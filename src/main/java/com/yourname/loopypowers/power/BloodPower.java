package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Random;

public class BloodPower implements Power {

    private static final Random RNG = new Random();

    /* ============================================================
       CONSTANTS & TUNING
       ============================================================ */

    // -- PASSIVE --
    public static final float BLEED_FRACTION = 0.20f;       // 20% of melee damage
    public static final int BLEED_DURATION_TICKS = 60;      // how long the damage is stretched out over
    public static final int BLEED_TICK_INTERVAL = 15;       // how often it is applied

    // -- PRIMARY --
    public static final double WHIP_RANGE = 17.0;           // travel distance
    public static final float WHIP_SELF_DAMAGE = 2.0f;      // self damage on cast
    public static final float WHIP_BLEED_DAMAGE = 12.0f;     // total bleed on hit
    public static final int WHIP_BLEED_DURATION = 60;       // total duration
    public static final int WHIP_BLEED_INTERVAL = 15;       // bleed tick speed
    public static final double WHIP_YANK_XZ = 1.3;          // horizontal pull strength
    public static final double WHIP_YANK_Y = 0.4;           // vertical lift to clear friction
    private static final double WHIP_BEAM_STEP = 0.25;      // spacing between beam points
    private static final int WHIP_BEAM_DUST_PER_STEP = 1;   // particle density per step

    // -- SECONDARY --
    public static final double CLOT_SPEED = 0.75;
    public static final int CLOT_LIFETIME_TICKS = 60;
    public static final float CLOT_SELF_DAMAGE = 4.0f;      // self damage on cast
    public static final int CLOT_SLOW_TICKS = 80;           //
    public static final int CLOT_WEAK_TICKS = 80;           //
    public static final float CLOT_HIT_DAMAGE = 10.0f;       // direct projectile damage

    public static final float CLOT_BLEED_DAMAGE = 10.0f;       // bleed total if unpopped
    public static final int CLOT_BLEED_DURATION = 60;
    public static final int CLOT_BLEED_INTERVAL = 15;

    public static final float POP_DAMAGE_MULT = 6.0f;       // multiplies remaining bleed for burst
    public static final int POP_DURATION = 10;              // 2 seconds
    public static final int POP_INTERVAL = 5;              // interval of multiplied damage
    public static final float POP_HEAL_MULT = 0.75f;        // heals n% of pop damage immediately

    // -- ULTIMATE --
    private static final double BIND_CAST_RANGE = 24.0;
    private static final double BIND_MAX_RANGE = 20.0;      // leash range before snap
    private static final int BIND_DURATION_TICKS = 300;     // how long bound
    private static final float BIND_DAMAGE_REDUCTION = 0.60f; // amount users damage is reduced by
    private static final float BIND_DAMAGE_SHARE = 0.60f;   // amount of damage the target takes
    private static final int ENV_APPLY_INTERVAL_TICKS = 8;  // how fast lava/fire can tick across bond
    private static final float ENV_MAX_CHUNK = 8.0f;        // max burst from natural damage
    private static final int BIND_HIT_FX_COOLDOWN_TICKS = 6;

    // shared visual element
    private static final DustParticleEffect BLOOD_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.25f);

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        breakBind(player);
        PENDING_ENV_DAMAGE.remove(player.getUuid());
        LAST_ENV_APPLY.remove(player.getUuid());
        LAST_BIND_HIT_FX.remove(player.getUuid());
        player.getCommandTags().remove(BIND_GUARD);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        tickBleed(player);
        tickBind(player);
    }

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    protected static final Map<UUID, BleedInstance> ACTIVE_BLEEDS = new HashMap<>();

    public static class BleedInstance {
        public UUID attackerUuid;
        public int ticksLeft;
        public int nextTick;
        public float perTickDmg;
        public float totalDmgLeft; // keeps track for the pop mechanic
    }

    private static long lastBleedTickTime = Long.MIN_VALUE;
    private static final String BIND_GUARD = "bl_bind_guard"; // prevents recursion

    /* ============================================================
       PASSIVE (ATTACK HOOK)
       ============================================================ */

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return true;
        if (target == attacker || !target.isAlive() || amount <= 0) return true;
        if (source.getSource() != attacker) return true;

        // if damage is bleed (otherwise it spams)
        if (source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BLEED)
                || source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BIND)) {
            return true;
        }

        target.addStatusEffect(new StatusEffectInstance(ModEffects.BLEED, BLEED_DURATION_TICKS, 0, true, false));

        float totalBleed = amount * BLEED_FRACTION;
        int intervals = Math.max(1, BLEED_DURATION_TICKS / BLEED_TICK_INTERVAL);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid();

        BleedInstance b = ACTIVE_BLEEDS.get(id);
        if (b == null) {
            b = new BleedInstance();
            ACTIVE_BLEEDS.put(id, b);
        }

        b.attackerUuid = attacker.getUuid();
        b.ticksLeft = BLEED_DURATION_TICKS;
        b.nextTick = BLEED_TICK_INTERVAL;
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        // particles
        if (attacker.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    4, 0.25, 0.35, 0.25, 0.02);
            w.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundCategory(), 0.35f, 0.8f);
        }

        return true;
    }

    private void tickBleed(ServerPlayerEntity player) {

        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.getOverworld().getTime();
        if (lastBleedTickTime == now) return;
        lastBleedTickTime = now;

        if (ACTIVE_BLEEDS.isEmpty()) return;

        var it = ACTIVE_BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID victimUuid = entry.getKey();
            BleedInstance b = entry.getValue();

            b.ticksLeft--;
            b.nextTick--;

            if (b.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            if (b.nextTick > 0) continue;
            b.nextTick = BLEED_TICK_INTERVAL;

            LivingEntity victim = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(victimUuid);
                if (e instanceof LivingEntity le) {
                    victim = le;
                    break;
                }
            }
            if (victim == null || !victim.isAlive()) {
                it.remove();
                continue;
            }

            ServerPlayerEntity attacker = (b.attackerUuid != null)
                    ? server.getPlayerManager().getPlayer(b.attackerUuid)
                    : null;

            DamageSource bleedSrc = ModDamageTypes.bleed(victim.getWorld());
            victim.damage(bleedSrc, b.perTickDmg);
            b.totalDmgLeft -= b.perTickDmg;

            // lifesteal for the attacker
            if (attacker != null && attacker.isAlive()) {
                attacker.heal(b.perTickDmg);
            }

            ServerWorld w = (ServerWorld) victim.getWorld();
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    6, 0.30, 0.40, 0.30, 0.02);
        }
    }


    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        player.damage(player.getDamageSources().magic(), WHIP_SELF_DAMAGE);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(WHIP_RANGE));

        HitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getPos() : end;

        LivingEntity hitEntity = null;
        Vec3d hitPos = null;
        double bestDistSq = start.squaredDistanceTo(blockEnd);

        Box searchBox = new Box(start, blockEnd).expand(1.2);

        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity e : candidates) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.25).raycast(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3d p = hit.get();
            double d = start.squaredDistanceTo(p);

            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        spawnBloodBeam(world, start, beamEnd, dir);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_IRON_GOLEM_DAMAGE,
                player.getSoundCategory(),
                0.75f,
                1.5f
        );
        player.swingHand(Hand.MAIN_HAND, true);

        if (hitEntity != null) {

            applyBleedFromProjectile(player, hitEntity, WHIP_BLEED_DAMAGE, WHIP_BLEED_DURATION, WHIP_BLEED_INTERVAL);

            Vec3d pullDir = player.getPos().subtract(hitEntity.getPos()).normalize();

            hitEntity.addVelocity(pullDir.x * WHIP_YANK_XZ, WHIP_YANK_Y, pullDir.z * WHIP_YANK_XZ);
            hitEntity.velocityModified = true;

            world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    hitEntity.getX(), hitEntity.getY() + 1.0, hitEntity.getZ(),
                    10, 0.35, 0.45, 0.35, 0.02);

            world.playSound(null, hitEntity.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    player.getSoundCategory(),
                    0.7f,
                    0.9f
            );
        }
    }

    private void spawnBloodBeam(ServerWorld world, Vec3d start, Vec3d end, Vec3d dir) {

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        // calculate perpendicular vectors for the whip spiral wave
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) right = new Vec3d(1, 0, 0); // fallback if pointing straight up/down
        Vec3d perp = dir.crossProduct(right).normalize();

        int steps = MathHelper.clamp((int) (len / WHIP_BEAM_STEP), 6, 140);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;

            // sine wave that peaks in the middle of the whip to give it a belly
            double wave = Math.sin(t * Math.PI);
            double spiralX = Math.cos(t * 12.0) * 0.5 * wave;
            double spiralY = Math.sin(t * 12.0) * 0.5 * wave;

            Vec3d offset = right.multiply(spiralX).add(perp.multiply(spiralY));

            world.spawnParticles(
                    BLOOD_DUST,
                    p.x + offset.x, p.y + offset.y, p.z + offset.z,
                    WHIP_BEAM_DUST_PER_STEP,
                    0.02, 0.02, 0.02,
                    0.0
            );

            // occasional extra drip
            if (RNG.nextFloat() < 0.1f) {
                world.spawnParticles(
                        ParticleTypes.DAMAGE_INDICATOR,
                        p.x + offset.x, p.y + offset.y, p.z + offset.z,
                        1,
                        0.05, 0.05, 0.05,
                        0.0
                );
            }

            p = p.add(dir.multiply(len / steps));
        }
    }


    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();

        com.yourname.loopypowers.entity.BloodClotEntity clot =
                new com.yourname.loopypowers.entity.BloodClotEntity(com.yourname.loopypowers.entity.ModEntities.BLOOD_CLOT, world);

        clot.setOwner(player);

        clot.setTuning(CLOT_LIFETIME_TICKS, CLOT_SLOW_TICKS, CLOT_WEAK_TICKS, CLOT_HIT_DAMAGE);

        // spawn directly at eye level to prevent passing through point-blank targets
        clot.setPos(player.getX(), player.getEyeY(), player.getZ());

        // use the vanilla velocity setter to sync pitch/yaw to the client properly!
        clot.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, (float) CLOT_SPEED, 0.0f);
        clot.velocityModified = true;

        world.spawnEntity(clot);

        player.swingHand(Hand.MAIN_HAND, true);
        player.damage(player.getDamageSources().magic(), CLOT_SELF_DAMAGE);

        world.spawnParticles(BLOOD_DUST,
                player.getX(), player.getY() + 1.0, player.getZ(),
                10, 0.25, 0.30, 0.25, 0.02);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_SLIME_SQUISH,
                player.getSoundCategory(),
                0.65f,
                0.6f
        );
    }

    public static void applyBleedFromProjectile(LivingEntity attacker, LivingEntity target, float totalBleed, int durationTicks, int intervalTicks) {
        if (attacker == null || target == null) return;
        if (!target.isAlive()) return;
        if (target == attacker) return;

        int intervals = Math.max(1, durationTicks / intervalTicks);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid();

        BleedInstance b = ACTIVE_BLEEDS.get(id);
        if (b == null) {
            b = new BleedInstance();
            ACTIVE_BLEEDS.put(id, b);
        }

        b.attackerUuid = (attacker instanceof ServerPlayerEntity sp) ? sp.getUuid() : null;
        b.ticksLeft = durationTicks;
        b.nextTick = intervalTicks + attacker.getRandom().nextInt(8);
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        target.addStatusEffect(new StatusEffectInstance(ModEffects.BLEED, durationTicks, 0, true, false));

        if (attacker.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    3, 0.20, 0.25, 0.20, 0.01);
            w.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundCategory(), 0.25f, 0.9f);
        }
    }

    public static void popBleed(LivingEntity target, ServerPlayerEntity attacker) {
        UUID id = target.getUuid();
        BleedInstance b = ACTIVE_BLEEDS.remove(id);

        target.removeStatusEffect(ModEffects.BLEED);

        if (b == null || b.totalDmgLeft <= 0) return;

        float totalPopDamage = b.totalDmgLeft * POP_DAMAGE_MULT;

        applyBleedFromProjectile(attacker, target, totalPopDamage, POP_DURATION, POP_INTERVAL);

        // lifesteal
        attacker.heal(totalPopDamage * POP_HEAL_MULT);

        if (attacker.getWorld() instanceof ServerWorld w) {
            w.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, attacker.getSoundCategory(), 1.0f, 1.2f);

            // red burst
            w.spawnParticles(new DustParticleEffect(new Vector3f(0.8f, 0.0f, 0.0f), 2.5f),
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    80, 0.4, 0.6, 0.4, 0.15);

            w.spawnParticles(ParticleTypes.CRIT,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    40, 0.5, 0.5, 0.5, 0.1);

            w.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.0, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);

            CameraShake.shakeNearby(attacker, 3.0, 10, 0.6f);
        }
    }

    public static boolean isBleeding(LivingEntity target) {
        return ACTIVE_BLEEDS.containsKey(target.getUuid());
    }

    /* ============================================================
       ULTIMATE (VICTIM HOOK)
       ============================================================ */

    private static final Map<UUID, BindInstance> ACTIVE_BINDS = new HashMap<>();

    private static class BindInstance {
        public UUID targetUuid;
        public int ticksLeft;
    }

    private static long lastBindTickTime = Long.MIN_VALUE;
    private static final Map<UUID, Long> LAST_BIND_HIT_FX = new HashMap<>();
    private static final Map<UUID, Float> PENDING_ENV_DAMAGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ENV_APPLY = new HashMap<>();

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        if (isBound(player)) {
            breakBind(player);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, player.getSoundCategory(), 0.8f, 1.0f);
            return;
        }
        player.swingHand(Hand.MAIN_HAND, true);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(BIND_CAST_RANGE));

        HitResult blockHit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getPos() : end;

        LivingEntity hitEntity = null;
        Vec3d hitPos = null;
        double bestDistSq = start.squaredDistanceTo(blockEnd);

        Box searchBox = new Box(start, blockEnd).expand(1.2);
        List<LivingEntity> candidates = world.getEntitiesByClass(LivingEntity.class, searchBox,
                e -> e.isAlive() && e != player);

        for (LivingEntity e : candidates) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.25).raycast(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3d p = hit.get();
            double d = start.squaredDistanceTo(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        spawnBloodChain(world, start, beamEnd);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_HIT,
                player.getSoundCategory(),
                0.9f,
                0.9f
        );

        if (hitEntity == null) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, player.getSoundCategory(), 0.6f, 0.6f);
            return;
        }

        startBind(player, hitEntity);

        world.playSound(null, hitEntity.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_PLACE,
                player.getSoundCategory(),
                1.0f,
                1.0f
        );
    }

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        if (amount <= 0) return true;

        BindInstance b = ACTIVE_BINDS.get(victim.getUuid());
        if (b == null) return true;

        if (victim.getCommandTags().contains(BIND_GUARD)) return true;

        MinecraftServer server = victim.getServer();
        if (server == null) return true;

        LivingEntity target = null;
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(b.targetUuid);
            if (e instanceof LivingEntity le) { target = le; break; }
        }

        if (target == null || !target.isAlive() || victim.squaredDistanceTo(target) > (BIND_MAX_RANGE * BIND_MAX_RANGE)) {
            ACTIVE_BINDS.remove(victim.getUuid());
            return true;
        }

        if (source.getAttacker() == null && victim.getWorld() instanceof ServerWorld sw) {
            long now = sw.getTime();

            float pending = PENDING_ENV_DAMAGE.getOrDefault(victim.getUuid(), 0.0f) + amount;

            if (pending > ENV_MAX_CHUNK) pending = ENV_MAX_CHUNK;

            PENDING_ENV_DAMAGE.put(victim.getUuid(), pending);

            long last = LAST_ENV_APPLY.getOrDefault(victim.getUuid(), Long.MIN_VALUE);

            if ((now - last) < ENV_APPLY_INTERVAL_TICKS) {
                return false;
            }

            LAST_ENV_APPLY.put(victim.getUuid(), now);
            amount = pending;
            PENDING_ENV_DAMAGE.remove(victim.getUuid());
        }

        float reduced = amount * (1.0f - BIND_DAMAGE_REDUCTION);
        float shared  = amount * BIND_DAMAGE_SHARE;

        victim.getCommandTags().add(BIND_GUARD);
        if (target instanceof ServerPlayerEntity spTarget) spTarget.getCommandTags().add(BIND_GUARD);

        try {
            if (reduced > 0.0f) victim.damage(source, reduced);
            DamageSource bindSrc = ModDamageTypes.bind(target.getWorld(), victim);
            if (shared > 0.0f) target.damage(bindSrc, shared);

            if (victim.getWorld() instanceof ServerWorld sw) {
                long now = sw.getTime();
                long last = LAST_BIND_HIT_FX.getOrDefault(victim.getUuid(), Long.MIN_VALUE);

                if ((now - last) >= BIND_HIT_FX_COOLDOWN_TICKS) {
                    LAST_BIND_HIT_FX.put(victim.getUuid(), now);
                    spawnBindDamageFx(sw, victim, target, amount);
                }
            }

        } finally {
            victim.getCommandTags().remove(BIND_GUARD);
            if (target instanceof ServerPlayerEntity spTarget) spTarget.getCommandTags().remove(BIND_GUARD);
        }

        return false;
    }

    private void tickBind(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.getOverworld().getTime();
        if (lastBindTickTime == now) return;
        lastBindTickTime = now;

        if (ACTIVE_BINDS.isEmpty()) return;

        var it = ACTIVE_BINDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID casterUuid = entry.getKey();
            BindInstance b = entry.getValue();

            b.ticksLeft--;
            if (b.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterUuid);
            if (caster == null || !caster.isAlive()) {
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUuid());
                LAST_ENV_APPLY.remove(caster.getUuid());
                continue;
            }

            LivingEntity target = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(b.targetUuid);
                if (e instanceof LivingEntity le) { target = le; break; }
            }

            if (target == null || !target.isAlive()) {
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUuid());
                LAST_ENV_APPLY.remove(caster.getUuid());
                continue;
            }

            double max = BIND_MAX_RANGE * BIND_MAX_RANGE;
            if (caster.squaredDistanceTo(target) > max) {
                caster.getServerWorld().playSound(null, caster.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_BREAK,
                        caster.getSoundCategory(),
                        0.9f, 1.0f
                );
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUuid());
                LAST_ENV_APPLY.remove(caster.getUuid());
                continue;
            }

            double dist = Math.sqrt(caster.squaredDistanceTo(target));
            float strain = (float) MathHelper.clamp((dist - 10.0) / (BIND_MAX_RANGE - 10.0), 0.0, 1.0);

            if (strain > 0.55f) {
                if ((b.ticksLeft % 2) == 0) {
                    caster.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                            caster.getX(), caster.getY() + 1.0, caster.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);

                    caster.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);
                }

                if ((b.ticksLeft % 10) == 0) {
                    caster.getServerWorld().playSound(null, caster.getBlockPos(),
                            SoundEvents.BLOCK_CHAIN_HIT,
                            caster.getSoundCategory(),
                            0.35f,
                            1.2f + (strain * 0.4f));
                }
            }

            if ((b.ticksLeft % 2) == 0) {
                spawnBindAura(caster.getServerWorld(), caster, strain);
                spawnBindAura(caster.getServerWorld(), target, strain);
            }

            if ((b.ticksLeft % 2) == 0) {
                spawnTetherParticles(caster.getServerWorld(), caster, target);
            }
        }
    }

    private static boolean isBound(ServerPlayerEntity caster) {
        return ACTIVE_BINDS.containsKey(caster.getUuid());
    }

    private static void startBind(ServerPlayerEntity caster, LivingEntity target) {
        BindInstance b = ACTIVE_BINDS.get(caster.getUuid());
        if (b == null) {
            b = new BindInstance();
            ACTIVE_BINDS.put(caster.getUuid(), b);
        }
        b.targetUuid = target.getUuid();
        b.ticksLeft = BIND_DURATION_TICKS;
    }

    private static void breakBind(ServerPlayerEntity caster) {
        ACTIVE_BINDS.remove(caster.getUuid());
    }

    private void spawnBloodChain(ServerWorld world, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);

        // toned down spacing
        int steps = MathHelper.clamp((int)(len / 0.8), 6, 70);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double jx = (RNG.nextDouble() - 0.5) * 0.12;
            double jy = (RNG.nextDouble() - 0.5) * 0.12;
            double jz = (RNG.nextDouble() - 0.5) * 0.12;

            world.spawnParticles(BLOOD_DUST, p.x + jx, p.y + jy, p.z + jz,
                    1, 0.02, 0.02, 0.02, 0.0);

            if (RNG.nextFloat() < 0.10f) {
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z,
                        1, 0.05, 0.05, 0.05, 0.0);
            }

            p = p.add(dir.multiply(len / steps));
        }
    }

    private void spawnTetherParticles(ServerWorld w, LivingEntity a, LivingEntity b) {
        b.addStatusEffect(new StatusEffectInstance(ModEffects.BLOODBOUND, 5, 0, true, false));

        Vec3d start = a.getPos().add(0, a.getHeight() * 0.6, 0);
        Vec3d end   = b.getPos().add(0, b.getHeight() * 0.6, 0);

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);

        // toned down spacing
        int steps = MathHelper.clamp((int)(len / 0.8), 5, 40);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {

            double t = i / (double) steps;

            double pulse = 0.08 + 0.06 * Math.sin((t * 8.0) + (w.getTime() * 0.35));
            double jitter = 0.06;

            double jx = (RNG.nextDouble() - 0.5) * jitter;
            double jy = (RNG.nextDouble() - 0.5) * jitter;
            double jz = (RNG.nextDouble() - 0.5) * jitter;

            w.spawnParticles(BLOOD_DUST,
                    p.x + jx, p.y + jy, p.z + jz,
                    1,
                    pulse, pulse, pulse,
                    0.0);

            p = p.add(dir.multiply(len / steps));
        }

        if (RNG.nextFloat() < 0.85f) {
            w.spawnParticles(BLOOD_DUST,
                    start.x, start.y, start.z,
                    2, 0.15, 0.20, 0.15, 0.0);
            w.spawnParticles(BLOOD_DUST,
                    end.x, end.y, end.z,
                    2, 0.15, 0.20, 0.15, 0.0);
        }
    }

    private void spawnBindAura(ServerWorld w, LivingEntity e, float intensity) {
        Vec3d p = e.getPos().add(0, e.getHeight() * 0.65, 0);

        int count = MathHelper.clamp((int)(1 + intensity * 4), 1, 6);
        double spread = 0.25 + intensity * 0.35;

        w.spawnParticles(BLOOD_DUST,
                p.x, p.y, p.z,
                count,
                spread, 0.30, spread,
                0.0);

        if (RNG.nextFloat() < (0.10f + intensity * 0.35f)) {
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    p.x, p.y, p.z,
                    1,
                    0.08, 0.12, 0.08,
                    0.0);
        }
    }

    private void spawnBindDamageFx(ServerWorld w, LivingEntity caster, LivingEntity target, float amount) {
        float intensity = MathHelper.clamp(amount / 10.0f, 0.15f, 1.0f);

        Vec3d a = caster.getPos().add(0, caster.getHeight() * 0.65, 0);
        Vec3d b = target.getPos().add(0, target.getHeight() * 0.65, 0);

        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                a.x, a.y, a.z,
                3 + (int)(intensity * 4),
                0.25, 0.30, 0.25,
                0.02);

        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                b.x, b.y, b.z,
                3 + (int)(intensity * 4),
                0.25, 0.30, 0.25,
                0.02);

        w.spawnParticles(BLOOD_DUST,
                a.x, a.y, a.z,
                4 + (int)(intensity * 6),
                0.35, 0.40, 0.35,
                0.0);

        w.spawnParticles(BLOOD_DUST,
                b.x, b.y, b.z,
                4 + (int)(intensity * 6),
                0.35, 0.40, 0.35,
                0.0);

        Vec3d delta = b.subtract(a);
        double len = delta.length();
        if (len > 0.01) {
            Vec3d dir = delta.multiply(1.0 / len);
            int steps = MathHelper.clamp((int)(len / 1.2), 3, 14);

            Vec3d p = a;
            for (int i = 0; i <= steps; i++) {
                w.spawnParticles(BLOOD_DUST,
                        p.x, p.y, p.z,
                        1,
                        0.05, 0.05, 0.05,
                        0.0);
                p = p.add(dir.multiply(len / steps));
            }
        }

        w.playSound(null, caster.getBlockPos(),
                SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                caster.getSoundCategory(),
                0.45f,
                0.85f + (intensity * 0.3f));
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 9_000; }
    @Override public long getSecondaryCooldownMs() { return 13_500; }
    @Override public long getUltimateCooldownMs() { return 260_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return "Blood"; }
    @Override public String getPrimaryName() { return "Hemochord"; }
    @Override public String getSecondaryName() { return "Coagulate"; }
    @Override public String getUltimateName() { return "Blood Pact"; }

    @Override
    public String getOverviewDescription() {
        return "Blood is a power that revolves around 1v1s, able to keep people in fights and deal high damage quickly to single targets, but may struggle against groups." +
                " It is intended to be a more simplistic power that expands upon your close-combat ability.";
    }

    @Override
    public String getPassiveName() {
        return "Sanguine Siphon";
    }

    @Override
    public String getPassiveDescription() {
        return "Your melee damage also inflicts a bleed that does additional damage over time, which is based on the damage dealt, meaning stronger weapons result in more bleed. Any bleed damage dealt heals you.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Damage yourself to cast a fast-moving whip that embeds into a target, bleeding them and pulling them to you.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Shoot a slow moving projectile that can hit a single target, the projectile has two effects dependiing on if the target is bleeding or not:\n" +
                "Target is not bleeding - Apply slowness, weakness and bleed.\n" +
                "Target is bleeding - Pop the bleed, multiplying the remaining damage and applying it instantly, this is a burst tool.";
    }

    @Override
    public String getUltimateDescription() {
        return "Shoot out a blood chain. If it hits a target they will now be bound to you. When bound they will take some of the damage you take instead of you. Going too far from each other will break the pact." +
                "Constant environmental damage is capped (so there will be a point where lava damage stops being applied)";
    }
}