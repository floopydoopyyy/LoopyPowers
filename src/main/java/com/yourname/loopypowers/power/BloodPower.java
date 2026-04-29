package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
       TAGS N TIMERS
       ============================================================ */
    // PASSIVE
    // store state in uuid
    protected static final Map<UUID, BleedInstance> ACTIVE_BLEEDS = new HashMap<>();

    private static class BleedInstance {
        public UUID attackerUuid;
        public int ticksLeft;
        public int nextTick;      // counts down to next bleed tick
        public float perTickDmg;  // how much damage each tick
    }

    // Passive bleed
    private static final float BLEED_FRACTION = 0.20f;       // 20% of melee damage
    private static final int BLEED_DURATION_TICKS = 60;  // how long the damage is stretched out over
    private static final int BLEED_TICK_INTERVAL = 15;       // how often it is applied

    public void tryApplyBleed(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        // do nothing to self
        if (target == attacker) return;

        // check if its a direct melee hit
        if (source.getSource() != attacker) return;

        // ignore if dead
        if (!target.isAlive()) return;

        // if damage is bleed (otherwise it spams)
        if (source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BLEED)
                || source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BIND)) {
            return;
        }

        target.addStatusEffect(new StatusEffectInstance(ModEffects.BLEED, BLEED_DURATION_TICKS, 0, true, false)); // visual, no logic attached to effect

        // take a percentage of damage
        float totalBleed = amount * BLEED_FRACTION;

        // space it out between ticks
        int intervals = Math.max(1, BLEED_DURATION_TICKS / BLEED_TICK_INTERVAL);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid(); // gets ID

        BleedInstance b = ACTIVE_BLEEDS.get(id);
        if (b == null) {
            b = new BleedInstance();
            ACTIVE_BLEEDS.put(id, b);
        }

        // refresh duration
        b.attackerUuid = attacker.getUuid();
        b.ticksLeft = BLEED_DURATION_TICKS;
        b.nextTick = BLEED_TICK_INTERVAL; // bleed on next tick
        b.perTickDmg = perTick;

        // particles
        ServerWorld w = attacker.getServerWorld();
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.25, 0.35, 0.25, 0.02);
        w.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                attacker.getSoundCategory(), 0.35f, 0.8f);
    }

    private void tickBleed(ServerPlayerEntity player) {

        // Prevent double-ticking if multiple Blood users exist.
        // tick ALL bleeds once per server tick
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.getOverworld().getTime();
        if (lastBleedTickTime == now) return;
        lastBleedTickTime = now;

        if (ACTIVE_BLEEDS.isEmpty()) return;

        // applies damage when due
        var it = ACTIVE_BLEEDS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID victimUuid = entry.getKey();
            BleedInstance b = entry.getValue();

            b.ticksLeft--;
            b.nextTick--;

            // expire
            if (b.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            // not time yet
            if (b.nextTick > 0) continue;
            b.nextTick = BLEED_TICK_INTERVAL;

            // resolve victim entity across all worlds
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

            // for death messages (damage is dealt by them)
            ServerPlayerEntity attacker = (b.attackerUuid != null)
                    ? server.getPlayerManager().getPlayer(b.attackerUuid)
                    : null;

            // apply bleed as no attacker so they don't just get sent into a knockback combo
            DamageSource bleedSrc = ModDamageTypes.bleed(victim.getWorld());
            victim.damage(bleedSrc, b.perTickDmg);

            // bleed particles
            ServerWorld w = (ServerWorld) victim.getWorld();
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0, victim.getZ(),
                    6, 0.30, 0.40, 0.30, 0.02);
        }
    }

    // global tick guard
    private static long lastBleedTickTime = Long.MIN_VALUE;

    // ---- PRIMARY ----

    // MARK STORAGE
    // wow this power is a lot of storing things.
    private static final Map<UUID, MarkInstance> ACTIVE_MARKS = new HashMap<>();

    private static class MarkInstance {
        public UUID attackerUuid;
        public int ticksLeft;
    }

    private static long lastMarkTickTime = Long.MIN_VALUE;

    // blood dust particle
    private static final DustParticleEffect BLOOD_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.25f);

    // beam constants
    private static final double STAIN_BEAM_STEP = 0.35; // spacing between beam
    private static final int STAIN_BEAM_DUST_PER_STEP = 2;

    // debuff traits
    private static final double STAIN_RANGE = 22.0; // travel distance
    private static final float STAIN_SELF_DAMAGE = 2.0f;     // self damage on cast
    private static final int STAIN_MARK_DURATION = 240;   // length of mark

    // finally actual logic
    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // damage self
        player.damage(player.getDamageSources().magic(), STAIN_SELF_DAMAGE);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(3.0f).normalize();
        Vec3d end = start.add(dir.multiply(STAIN_RANGE));

        // stop beam at blocks
        HitResult blockHit = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getPos() : end;

        // check if hit entity before collision
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

        // choose beam end point
        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // make beam along path
        spawnBloodBeam(world, start, beamEnd);

        // cast sound
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_SLIME_SQUISH_SMALL,
                player.getSoundCategory(),
                0.75f,
                0.65f
        );
        // animation
        player.swingHand(Hand.MAIN_HAND, true);

        // if hit entity
        if (hitEntity != null) {
            applyStainMark(player, hitEntity);

            // impact FX
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

    private void applyStainMark(ServerPlayerEntity attacker, LivingEntity target) {

        // maintain mark
        UUID id = target.getUuid();
        MarkInstance m = ACTIVE_MARKS.get(id);
        if (m == null) {
            m = new MarkInstance();
            ACTIVE_MARKS.put(id, m);
        }

        m.attackerUuid = attacker.getUuid();
        m.ticksLeft = STAIN_MARK_DURATION;

        // apply effects
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, true));

        // cacnel any regen
        // hunger is here to stop regeneration from actually happening
        target.removeStatusEffect(StatusEffects.REGENERATION); // this is probably overpowered.
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 40, 0, true, true));
    }

    private void tickStainMarks(ServerPlayerEntity player) {

        // ticks all marked players per tick
        MinecraftServer server = player.getServer();
        if (server == null) return;

        long now = server.getOverworld().getTime();
        if (lastMarkTickTime == now) return;
        lastMarkTickTime = now;

        if (ACTIVE_MARKS.isEmpty()) return;

        var it = ACTIVE_MARKS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            UUID victimUuid = entry.getKey();
            MarkInstance m = entry.getValue();

            m.ticksLeft--;

            if (m.ticksLeft <= 0) {
                it.remove();
                continue;
            }

            // find all marked across world
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

            // upkeep effects - regeneration CAN be reapplied otherwise it would make gaps useless.
            if (m.ticksLeft % 10 == 0) {
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20, 0, true, true));
                    victim.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 20, 0, true, true));
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 20, 0, true, true));
                //victim.removeStatusEffect(StatusEffects.REGENERATION);
            }

            // particles on marked people
            if (m.ticksLeft % 6 == 0) {
                ServerWorld w = (ServerWorld) victim.getWorld();
                w.spawnParticles(BLOOD_DUST,
                        victim.getX(), victim.getY() + 1.0, victim.getZ(),
                        2, 0.25, 0.35, 0.25, 0.01);
            }
        }
    }
    private void spawnBloodBeam(ServerWorld world, Vec3d start, Vec3d end) {

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);

        int steps = MathHelper.clamp((int) (len / STAIN_BEAM_STEP), 6, 140);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {

            // makes it not smooth (otherwise it just looked like a laser)
            double jx = (RNG.nextDouble() - 0.5) * 0.18;
            double jy = (RNG.nextDouble() - 0.5) * 0.18;
            double jz = (RNG.nextDouble() - 0.5) * 0.18;

            world.spawnParticles(
                    BLOOD_DUST,
                    p.x + jx, p.y + jy, p.z + jz,
                    STAIN_BEAM_DUST_PER_STEP,
                    0.02, 0.02, 0.02,
                    0.0
            );

            // occasional different effect
            // you need some variety in your diet after all
            // or at least I do.
            if (RNG.nextFloat() < 0.12f) {
                world.spawnParticles(
                        ParticleTypes.DAMAGE_INDICATOR,
                        p.x, p.y, p.z,
                        1,
                        0.05, 0.05, 0.05,
                        0.0
                );
            }

            p = p.add(dir.multiply(len / steps));
        }
    }
    // ---- SECONDARY ----
    // most logic is in the custom entity.
    private static final double CLOT_SPEED = 0.75;
    private static final int CLOT_LIFETIME_TICKS = 60;
    private static final int CLOT_SLOW_TICKS = 20 * 3;
    private static final int CLOT_WEAK_TICKS = 20 * 3;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();

        // spawn entity
        com.yourname.loopypowers.entity.BloodClotEntity clot =
                new com.yourname.loopypowers.entity.BloodClotEntity(com.yourname.loopypowers.entity.ModEntities.BLOOD_CLOT, world);

        clot.setOwner(player);

        // balancing
        clot.setTuning(CLOT_LIFETIME_TICKS, CLOT_SLOW_TICKS, CLOT_WEAK_TICKS, 2.0f); // damage on hit is last parameter

        // spawn it
        Vec3d start = player.getEyePos().add(player.getRotationVec(1.0f).multiply(0.6));
        clot.setPos(start.x, start.y, start.z);

        // set velocity
        clot.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, (float) CLOT_SPEED, 0.0f);
        clot.velocityModified = true;

        world.spawnEntity(clot);
        // animations
        player.swingHand(Hand.MAIN_HAND, true);
        // damage self
        player.damage(player.getDamageSources().magic(), 4.0f);

        // particles
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

        // start bleed
        int intervals = Math.max(1, durationTicks / intervalTicks);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid();

        BleedInstance b = ACTIVE_BLEEDS.get(id);
        if (b == null) {
            b = new BleedInstance();
            ACTIVE_BLEEDS.put(id, b);
        }

        // store attacker if it's a player (which it should be)
        b.attackerUuid = (attacker instanceof ServerPlayerEntity sp) ? sp.getUuid() : null;
        b.ticksLeft = durationTicks;
        b.nextTick = intervalTicks + attacker.getRandom().nextInt(8); // trying to vary the ticks because for some reason it was blocking entities from hitting
        b.perTickDmg = perTick;

        // particles
        if (attacker.getWorld() instanceof ServerWorld w) {
            w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    3, 0.20, 0.25, 0.20, 0.01);
            w.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundCategory(), 0.25f, 0.9f);
        }
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // no setup needed
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        // no clearance needed
    }

    @Override
    public void onTick(ServerPlayerEntity player) {

        // Passive bleed tick (server-side tracking)
        tickBleed(player);

        // Mark upkeep (glow + regen lock)
        tickStainMarks(player);

        // upkeep pact
        tickBind(player);
    }
// ULT
    // I HATE CODING THIS POWER!!!
    private static final Map<UUID, BindInstance> ACTIVE_BINDS = new HashMap<>();

    private static class BindInstance {
        public UUID targetUuid;
        public int ticksLeft;
    }

    private static long lastBindTickTime = Long.MIN_VALUE;
    // limits so hit damage doesn't spam
    private static final Map<UUID, Long> LAST_BIND_HIT_FX = new HashMap<>();
    private static final int BIND_HIT_FX_COOLDOWN_TICKS = 6; // 0.3s

    // bind constants
    private static final double BIND_CAST_RANGE = 24.0;
    private static final double BIND_MAX_RANGE = 20.0; // leash range
    private static final int BIND_DURATION_TICKS = 300; // how long bound
    private static final float BIND_DAMAGE_REDUCTION = 0.60f; // amount damage users damage reduced by
    private static final float BIND_DAMAGE_SHARE = 0.60f;     // amount of damage the attacker takes
    private static final int ENV_APPLY_INTERVAL_TICKS = 8; // how much stuff like lava can hurt
    private static final float ENV_MAX_CHUNK = 8.0f;       // max burst from natural damage. my attempt at stopping annoying plays.

    private static final Map<UUID, Float> PENDING_ENV_DAMAGE = new HashMap<>();
    private static final Map<UUID, Long> LAST_ENV_APPLY = new HashMap<>();

    private static final String BIND_GUARD = "bl_bind_guard"; // prevents recursion

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // if already bound, cancel
        if (isBound(player)) {
            breakBind(player);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, player.getSoundCategory(), 0.8f, 1.0f);
            return;
        }
        player.swingHand(Hand.MAIN_HAND, true);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(BIND_CAST_RANGE));

        // stop at blocks
        HitResult blockHit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getPos() : end;

        // find nearest living entity along the segment
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

        // spawn chain
        spawnBloodChain(world, start, beamEnd);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_HIT,
                player.getSoundCategory(),
                0.9f,
                0.9f
        );

        if (hitEntity == null) {
            // miss feedback
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, player.getSoundCategory(), 0.6f, 0.6f);
            return;
        }

        // bind it
        startBind(player, hitEntity);

        world.playSound(null, hitEntity.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_PLACE,
                player.getSoundCategory(),
                1.0f,
                1.0f
        );
    }

    public boolean tryBindDamage(ServerPlayerEntity caster, DamageSource source, float amount) {
        if (amount <= 0) return false; // too little to care

        BindInstance b = ACTIVE_BINDS.get(caster.getUuid()); // dodge method if no bind active for player
        if (b == null) return false;

        // stops recursion
        if (caster.getCommandTags().contains(BIND_GUARD)) return false;

        MinecraftServer server = caster.getServer();
        if (server == null) return false;

        LivingEntity target = null;
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(b.targetUuid);
            if (e instanceof LivingEntity le) { target = le; break; }
        }
        if (target == null || !target.isAlive()) {
            ACTIVE_BINDS.remove(caster.getUuid());
            return false;
        }

        // range check
        if (caster.squaredDistanceTo(target) > (BIND_MAX_RANGE * BIND_MAX_RANGE)) {
            ACTIVE_BINDS.remove(caster.getUuid());
            return false;
        }
        // ENVIRONMENTAL DAMAGE
        // If there's no attacker, it's probably environmental
        // it will still transfer, but not all the time
        if (source.getAttacker() == null && caster.getWorld() instanceof ServerWorld sw) {
            long now = sw.getTime();

            float pending = PENDING_ENV_DAMAGE.getOrDefault(caster.getUuid(), 0.0f);
            pending += amount;

            // cap chunk so it doesn't become stupid if they sit in lava for ages
            if (pending > ENV_MAX_CHUNK) pending = ENV_MAX_CHUNK;

            PENDING_ENV_DAMAGE.put(caster.getUuid(), pending);

            long last = LAST_ENV_APPLY.getOrDefault(caster.getUuid(), Long.MIN_VALUE);

            // keep waiting if not ready
            if ((now - last) < ENV_APPLY_INTERVAL_TICKS) {
                return true; // caller should return false to cancel the original damage
            }

            // time to apply a chunk
            LAST_ENV_APPLY.put(caster.getUuid(), now);
            amount = pending;
            PENDING_ENV_DAMAGE.remove(caster.getUuid());
        }

        float reduced = amount * (1.0f - BIND_DAMAGE_REDUCTION); //
        float shared  = amount * BIND_DAMAGE_SHARE;             //

        caster.getCommandTags().add(BIND_GUARD);
        if (target instanceof ServerPlayerEntity spTarget) spTarget.getCommandTags().add(BIND_GUARD);

        try {
            // take damage, cancel it and apply new damage
            if (reduced > 0.0f) caster.damage(source, reduced); // reduce player damage
            DamageSource bindSrc = ModDamageTypes.bind(target.getWorld(), caster); // apply new damage with the other source
            if (shared > 0.0f) target.damage(bindSrc, shared);

            // transfer particles with a tick limit
            if (caster.getWorld() instanceof ServerWorld sw) {
                long now = sw.getTime();
                long last = LAST_BIND_HIT_FX.getOrDefault(caster.getUuid(), Long.MIN_VALUE);

                if ((now - last) >= BIND_HIT_FX_COOLDOWN_TICKS) {
                    LAST_BIND_HIT_FX.put(caster.getUuid(), now);
                    spawnBindDamageFx(sw, caster, target, amount);
                }
            }

        } finally {
            caster.getCommandTags().remove(BIND_GUARD);
            if (target instanceof ServerPlayerEntity spTarget) spTarget.getCommandTags().remove(BIND_GUARD);
        }
        return true;
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
                PENDING_ENV_DAMAGE.remove(caster.getUuid()); // stops transfers
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

            // if it is about to snap
            double dist = Math.sqrt(caster.squaredDistanceTo(target));
            float strain = (float) MathHelper.clamp((dist - 10.0) / (BIND_MAX_RANGE - 10.0), 0.0, 1.0); // this is the distance between entites
            // particles when close to breaking
            if (strain > 0.55f) {

                // make thicker and other effects
                if ((b.ticksLeft % 2) == 0) {
                    caster.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                            caster.getX(), caster.getY() + 1.0, caster.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);

                    caster.getServerWorld().spawnParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY() + 1.0, target.getZ(),
                            1, 0.15, 0.15, 0.15, 0.0);
                }

                // warning sound as it nears snap
                if ((b.ticksLeft % 10) == 0) {
                    caster.getServerWorld().playSound(null, caster.getBlockPos(),
                            SoundEvents.BLOCK_CHAIN_HIT,
                            caster.getSoundCategory(),
                            0.35f,
                            1.2f + (strain * 0.4f));
                }
            }

            // particles on both players
            if ((b.ticksLeft % 2) == 0) {
                spawnBindAura(caster.getServerWorld(), caster, strain);
                spawnBindAura(caster.getServerWorld(), target, strain);
            }

            // make constant fx
            if ((b.ticksLeft % 2) == 0) {
                spawnTetherParticles(caster.getServerWorld(), caster, target);
            }
        }
    }

    // HELPERS FOR ULT
    private static boolean isBound(ServerPlayerEntity caster) { // checks if entity is already in blood pact
        return ACTIVE_BINDS.containsKey(caster.getUuid());
    }

    private static void startBind(ServerPlayerEntity caster, LivingEntity target) { // initiates bind
        BindInstance b = ACTIVE_BINDS.get(caster.getUuid());
        if (b == null) {
            b = new BindInstance();
            ACTIVE_BINDS.put(caster.getUuid(), b);
        }
        b.targetUuid = target.getUuid();
        b.ticksLeft = BIND_DURATION_TICKS;
    }

    private static void breakBind(ServerPlayerEntity caster) { // take a guess
        ACTIVE_BINDS.remove(caster.getUuid());
    }

    private void spawnBloodChain(ServerWorld world, Vec3d start, Vec3d end) { // this is the chain on cast, not the tether
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);
        int steps = MathHelper.clamp((int)(len / 0.35), 6, 140);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double jx = (RNG.nextDouble() - 0.5) * 0.12;
            double jy = (RNG.nextDouble() - 0.5) * 0.12;
            double jz = (RNG.nextDouble() - 0.5) * 0.12;

            world.spawnParticles(BLOOD_DUST, p.x + jx, p.y + jy, p.z + jz,
                    2, 0.02, 0.02, 0.02, 0.0);

            if (RNG.nextFloat() < 0.10f) {
                world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z,
                        1, 0.05, 0.05, 0.05, 0.0);
            }

            p = p.add(dir.multiply(len / steps));
        }
    }

    private void spawnTetherParticles(ServerWorld w, LivingEntity a, LivingEntity b) { // tether
        //  visual effect
        b.addStatusEffect(new StatusEffectInstance(ModEffects.BLOODBOUND, 5, 0, true, false));

        Vec3d start = a.getPos().add(0, a.getHeight() * 0.6, 0);
        Vec3d end   = b.getPos().add(0, b.getHeight() * 0.6, 0);

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);

        // fewer points than your original, but each point is "heavier"
        int steps = MathHelper.clamp((int)(len / 0.45), 8, 80);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {

            double t = i / (double) steps;

            // pulse thickness over time and along the chain
            double pulse = 0.08 + 0.06 * Math.sin((t * 8.0) + (w.getTime() * 0.35));
            double jitter = 0.06;

            double jx = (RNG.nextDouble() - 0.5) * jitter;
            double jy = (RNG.nextDouble() - 0.5) * jitter;
            double jz = (RNG.nextDouble() - 0.5) * jitter;

            // main blood particles
            w.spawnParticles(BLOOD_DUST,
                    p.x + jx, p.y + jy, p.z + jz,
                    2,
                    pulse, pulse, pulse,
                    0.0);

            p = p.add(dir.multiply(len / steps));
        }

        // endpoints
        if (RNG.nextFloat() < 0.85f) {
            w.spawnParticles(BLOOD_DUST,
                    start.x, start.y, start.z,
                    4, 0.15, 0.20, 0.15, 0.0);
            w.spawnParticles(BLOOD_DUST,
                    end.x, end.y, end.z,
                    4, 0.15, 0.20, 0.15, 0.0);
        }
    } // i hate particles

    private void spawnBindAura(ServerWorld w, LivingEntity e, float intensity) { // particles if close to snapping
        Vec3d p = e.getPos().add(0, e.getHeight() * 0.65, 0);

        int count = MathHelper.clamp((int)(3 + intensity * 10), 3, 14);
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

    private void spawnBindDamageFx(ServerWorld w, LivingEntity caster, LivingEntity target, float amount) { // particles when damaged
        // makes it scale slightly with damage
        float intensity = MathHelper.clamp(amount / 10.0f, 0.15f, 1.0f);

        Vec3d a = caster.getPos().add(0, caster.getHeight() * 0.65, 0);
        Vec3d b = target.getPos().add(0, target.getHeight() * 0.65, 0);

        // this is on both people
        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                a.x, a.y, a.z,
                6 + (int)(intensity * 10),
                0.25, 0.30, 0.25,
                0.02);

        w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                b.x, b.y, b.z,
                6 + (int)(intensity * 10),
                0.25, 0.30, 0.25,
                0.02);

        w.spawnParticles(BLOOD_DUST,
                a.x, a.y, a.z,
                8 + (int)(intensity * 14),
                0.35, 0.40, 0.35,
                0.0);

        w.spawnParticles(BLOOD_DUST,
                b.x, b.y, b.z,
                8 + (int)(intensity * 14),
                0.35, 0.40, 0.35,
                0.0);

        // my attempt at a tether pulse
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

        // sound
        w.playSound(null, caster.getBlockPos(),
                SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                caster.getSoundCategory(),
                0.45f,
                0.85f + (intensity * 0.3f));
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 6_000; }
    @Override public long getSecondaryCooldownMs() { return 5_000; }
    @Override public long getUltimateCooldownMs() { return 5_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return "Blood"; }
    @Override public String getPrimaryName() { return "Bloodstain"; }
    @Override public String getSecondaryName() { return "Blood Clot"; }
    @Override public String getUltimateName() { return "Blood Pact"; }

    @Override
    public String getOverviewDescription() {
        return "Blood mainly revolves around damaging and debuffing single targets extremely well, making it very strong in 1v1 scenarios";
    }

    @Override
    public String getPassiveName() {
        return "Leech";
    }

    @Override
    public String getPassiveDescription() {
        return "Your melee damage also inflicts a bleed that does additional damage over time, which is based on the damage dealt, meaning stronger weapons result in more bleed.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Shoot a beam of blood where you are looking, on hit enemies are marked for a long time, making them blinded, glowing and inflicting them with hunger, while also removing any regeneration effects.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Shoot a slow moving projectile that inflicts enemies with bleed, slowness and weakness.";
    }

    @Override
    public String getUltimateDescription() {
        return "Shoot out a blood chain. If it hits a target they will now be bound to you. When bound they will take some of the damage you take instead of you. Going too far from each other will break the pact." +
                "Constant environmental damage is capped (so there will be a point where lava damage stops being applied)";
    }
}