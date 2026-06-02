package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class BloodPower implements Power {

    /* ============================================================
       CONSTANTS & TUNING
       ============================================================ */

    // -- PASSIVE --
    public static final float BLEED_FRACTION = 0.20f;
    public static final int BLEED_DURATION_TICKS = 60;
    public static final int BLEED_TICK_INTERVAL = 15;

    // -- PRIMARY --
    public static final double WHIP_RANGE = 17.0;
    public static final float WHIP_SELF_DAMAGE = 2.0f;
    public static final float WHIP_BLEED_DAMAGE = 8.0f;
    public static final int WHIP_BLEED_DURATION = 60;
    public static final int WHIP_BLEED_INTERVAL = 15;
    public static final double WHIP_YANK_XZ = 1.3;
    public static final double WHIP_YANK_Y = 0.4;

    // -- SECONDARY --
    public static final double CLOT_SPEED = 0.75;
    public static final int CLOT_LIFETIME_TICKS = 60;
    public static final float CLOT_SELF_DAMAGE = 4.0f;
    public static final int CLOT_SLOW_TICKS = 80;
    public static final int CLOT_WEAK_TICKS = 80;
    public static final float CLOT_HIT_DAMAGE = 10.0f;

    public static final float CLOT_BLEED_DAMAGE = 10.0f;
    public static final int CLOT_BLEED_DURATION = 60;
    public static final int CLOT_BLEED_INTERVAL = 15;

    public static final float POP_DAMAGE_MULT = 6.0f;
    public static final int POP_DURATION = 10;
    public static final int POP_INTERVAL = 5;
    public static final float POP_HEAL_MULT = 0.75f;

    // -- ULTIMATE --
    private static final double BIND_CAST_RANGE = 24.0;
    private static final double BIND_MAX_RANGE = 20.0;
    private static final int BIND_DURATION_TICKS = 300;
    private static final float BIND_DAMAGE_REDUCTION = 0.60f;
    private static final float BIND_DAMAGE_SHARE = 0.60f;
    private static final int ENV_APPLY_INTERVAL_TICKS = 8;
    private static final float ENV_MAX_CHUNK = 8.0f;
    private static final int BIND_HIT_FX_COOLDOWN_TICKS = 6;

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
        if (!player.isAlive()) return;
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
        public float totalDmgLeft;
    }

    private static long lastBleedTickTime = Long.MIN_VALUE;
    private static final String BIND_GUARD = "bl_bind_guard";

    /* ============================================================
       PASSIVE (ATTACK HOOK)
       ============================================================ */

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;
        if (target == attacker || !target.isAlive() || amount <= 0) return true;
        if (source.getSource() != attacker) return true;

        if (source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BLEED)
                || source.getTypeRegistryEntry().matchesKey(ModDamageTypes.BIND)) {
            return true;
        }

        target.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.BLEED), BLEED_DURATION_TICKS, 0, true, false));

        float totalBleed = amount * BLEED_FRACTION;
        int intervals = Math.max(1, BLEED_DURATION_TICKS / BLEED_TICK_INTERVAL);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid();
        BleedInstance b = ACTIVE_BLEEDS.computeIfAbsent(id, k -> new BleedInstance());
        b.attackerUuid = attacker.getUuid();
        b.ticksLeft = BLEED_DURATION_TICKS;
        b.nextTick = BLEED_TICK_INTERVAL;
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        if (attacker.getWorld() instanceof ServerWorld w) {
            double tx = target.getX(), ty = target.getY(), tz = target.getZ();
            PlayerLookup.tracking(w, target.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new BloodBleedApplyPayload(tx, ty, tz, 4)));
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

            if (b.ticksLeft <= 0) { it.remove(); continue; }
            if (b.nextTick > 0) continue;
            b.nextTick = BLEED_TICK_INTERVAL;

            LivingEntity victim = null;
            for (ServerWorld w : server.getWorlds()) {
                Entity e = w.getEntity(victimUuid);
                if (e instanceof LivingEntity le) { victim = le; break; }
            }
            if (victim == null || !victim.isAlive()) { it.remove(); continue; }

            ServerPlayerEntity attacker = (b.attackerUuid != null)
                    ? server.getPlayerManager().getPlayer(b.attackerUuid)
                    : null;

            DamageSource bleedSrc = ModDamageTypes.bleed(victim.getWorld());
            victim.damage(bleedSrc, b.perTickDmg);
            b.totalDmgLeft -= b.perTickDmg;

            if (attacker != null && attacker.isAlive()) {
                attacker.heal(b.perTickDmg);
            }

            ServerWorld w = (ServerWorld) victim.getWorld();
            double vx = victim.getX(), vy = victim.getY(), vz = victim.getZ();
            PlayerLookup.tracking(w, victim.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new BloodBleedTickPayload(vx, vy, vz)));
        }
    }


    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        player.damage(ModDamageTypes.bloodself(world, player), WHIP_SELF_DAMAGE);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(WHIP_RANGE));

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
        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class, searchBox, e -> e.isAlive() && e != player);

        for (LivingEntity e : candidates) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.25).raycast(start, blockEnd);
            if (hit.isEmpty()) continue;
            Vec3d p = hit.get();
            double d = start.squaredDistanceTo(p);
            if (d < bestDistSq) { bestDistSq = d; hitEntity = e; hitPos = p; }
        }

        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // beam visuals → client
        {
            double sx = start.x, sy = start.y, sz = start.z;
            double ex = beamEnd.x, ey = beamEnd.y, ez = beamEnd.z;
            BloodWhipBeamPayload beamPayload = new BloodWhipBeamPayload(sx, sy, sz, ex, ey, ez);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, beamPayload));
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_IRON_GOLEM_DAMAGE,
                player.getSoundCategory(), 0.75f, 1.5f);
        player.swingHand(Hand.MAIN_HAND, true);

        if (hitEntity != null) {
            applyBleedFromProjectile(player, hitEntity, WHIP_BLEED_DAMAGE, WHIP_BLEED_DURATION, WHIP_BLEED_INTERVAL);

            Vec3d pullDir = player.getPos().subtract(hitEntity.getPos()).normalize();
            hitEntity.addVelocity(pullDir.x * WHIP_YANK_XZ, WHIP_YANK_Y, pullDir.z * WHIP_YANK_XZ);
            hitEntity.velocityModified = true;

            // hit impact visuals → client
            double hx = hitEntity.getX(), hy = hitEntity.getY(), hz = hitEntity.getZ();
            BloodWhipHitPayload hitPayload = new BloodWhipHitPayload(hx, hy, hz);
            Set<ServerPlayerEntity> hitViewers = new HashSet<>();
            PlayerLookup.tracking(world, hitEntity.getBlockPos()).forEach(hitViewers::add);
            hitViewers.add(player);
            hitViewers.forEach(p -> ServerPlayNetworking.send(p, hitPayload));

            world.playSound(null, hitEntity.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    player.getSoundCategory(), 0.7f, 0.9f);
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
        clot.setPos(player.getX(), player.getEyeY(), player.getZ());
        clot.setVelocity(player, player.getPitch(), player.getYaw(), 0.0f, (float) CLOT_SPEED, 0.0f);
        clot.velocityModified = true;

        world.spawnEntity(clot);

        player.swingHand(Hand.MAIN_HAND, true);
        player.damage(ModDamageTypes.bloodself(world, player), CLOT_SELF_DAMAGE);

        // cast burst → client
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        BloodClotCastPayload castPayload = new BloodClotCastPayload(px, py, pz);
        Set<ServerPlayerEntity> castViewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(castViewers::add);
        castViewers.add(player);
        castViewers.forEach(p -> ServerPlayNetworking.send(p, castPayload));

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_SLIME_SQUISH,
                player.getSoundCategory(), 0.65f, 0.6f);
    }

    public static void applyBleedFromProjectile(LivingEntity attacker, LivingEntity target, float totalBleed, int durationTicks, int intervalTicks) {
        if (attacker == null || target == null) return;
        if (!target.isAlive()) return;
        if (target == attacker) return;

        int intervals = Math.max(1, durationTicks / intervalTicks);
        float perTick = totalBleed / (float) intervals;

        UUID id = target.getUuid();
        BleedInstance b = ACTIVE_BLEEDS.computeIfAbsent(id, k -> new BleedInstance());
        b.attackerUuid = (attacker instanceof ServerPlayerEntity sp) ? sp.getUuid() : null;
        b.ticksLeft = durationTicks;
        b.nextTick = intervalTicks + attacker.getRandom().nextInt(8);
        b.perTickDmg = perTick;
        b.totalDmgLeft = totalBleed;

        target.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.BLEED), durationTicks, 0, true, false));

        if (attacker.getWorld() instanceof ServerWorld w) {
            double tx = target.getX(), ty = target.getY(), tz = target.getZ();
            PlayerLookup.tracking(w, target.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new BloodBleedApplyPayload(tx, ty, tz, 3)));
            w.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                    attacker.getSoundCategory(), 0.25f, 0.9f);
        }
    }

    public static void popBleed(LivingEntity target, ServerPlayerEntity attacker) {
        if (target == null) return;

        UUID id = target.getUuid();
        BleedInstance b = ACTIVE_BLEEDS.remove(id);

        target.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.BLEED));

        if (b == null || b.totalDmgLeft <= 0) return;

        float totalPopDamage = b.totalDmgLeft * POP_DAMAGE_MULT;
        applyBleedFromProjectile(attacker, target, totalPopDamage, POP_DURATION, POP_INTERVAL);

        attacker.heal(totalPopDamage * POP_HEAL_MULT);

        if (attacker.getWorld() instanceof ServerWorld w) {
            w.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, attacker.getSoundCategory(), 1.0f, 1.2f);

            // pop burst → client
            double tx = target.getX(), ty = target.getY(), tz = target.getZ();
            PlayerLookup.tracking(w, target.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new BloodPopPayload(tx, ty, tz)));

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
            if (d < bestDistSq) { bestDistSq = d; hitEntity = e; hitPos = p; }
        }

        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // chain cast visuals → client
        {
            double sx = start.x, sy = start.y, sz = start.z;
            double ex = beamEnd.x, ey = beamEnd.y, ez = beamEnd.z;
            BloodChainPayload chainPayload = new BloodChainPayload(sx, sy, sz, ex, ey, ez);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, chainPayload));
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_HIT, player.getSoundCategory(), 0.9f, 0.9f);

        if (hitEntity == null) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_SLIME_SQUISH_SMALL, player.getSoundCategory(), 0.6f, 0.6f);
            return;
        }

        startBind(player, hitEntity);

        world.playSound(null, hitEntity.getBlockPos(),
                SoundEvents.BLOCK_CHAIN_PLACE, player.getSoundCategory(), 1.0f, 1.0f);
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

        if (target == null || !target.isAlive()
                || victim.squaredDistanceTo(target) > (BIND_MAX_RANGE * BIND_MAX_RANGE)) {
            ACTIVE_BINDS.remove(victim.getUuid());
            return true;
        }

        if (source.getAttacker() == null && victim.getWorld() instanceof ServerWorld sw) {
            long now = sw.getTime();
            float pending = PENDING_ENV_DAMAGE.getOrDefault(victim.getUuid(), 0.0f) + amount;
            if (pending > ENV_MAX_CHUNK) pending = ENV_MAX_CHUNK;
            PENDING_ENV_DAMAGE.put(victim.getUuid(), pending);

            long last = LAST_ENV_APPLY.getOrDefault(victim.getUuid(), Long.MIN_VALUE);
            if ((now - last) < ENV_APPLY_INTERVAL_TICKS) return false;

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
                    float intensity = MathHelper.clamp(amount / 10.0f, 0.15f, 1.0f);
                    BloodBindDamageFxPayload fxPayload = new BloodBindDamageFxPayload(victim.getId(), target.getId(), intensity);
                    Set<ServerPlayerEntity> fxViewers = new HashSet<>();
                    PlayerLookup.tracking(victim).forEach(fxViewers::add);
                    fxViewers.add(victim);
                    PlayerLookup.tracking(sw, target.getBlockPos()).forEach(fxViewers::add);
                    fxViewers.forEach(p -> ServerPlayNetworking.send(p, fxPayload));
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
            if (b.ticksLeft <= 0) { it.remove(); continue; }

            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterUuid);
            if (caster == null || !caster.isAlive()) {
                it.remove();
                PENDING_ENV_DAMAGE.remove(casterUuid);
                LAST_ENV_APPLY.remove(casterUuid);
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
                        SoundEvents.BLOCK_CHAIN_BREAK, caster.getSoundCategory(), 0.9f, 1.0f);
                it.remove();
                PENDING_ENV_DAMAGE.remove(caster.getUuid());
                LAST_ENV_APPLY.remove(caster.getUuid());
                continue;
            }

            double dist = Math.sqrt(caster.squaredDistanceTo(target));
            float strain = (float) MathHelper.clamp((dist - 10.0) / (BIND_MAX_RANGE - 10.0), 0.0, 1.0);

            if (strain > 0.55f) {
                if ((b.ticksLeft % 10) == 0) {
                    caster.getServerWorld().playSound(null, caster.getBlockPos(),
                            SoundEvents.BLOCK_CHAIN_HIT,
                            caster.getSoundCategory(),
                            0.35f,
                            1.2f + (strain * 0.4f));
                }
            }

            if ((b.ticksLeft % 2) == 0) {
                // keep the bloodbound status effect server-side (controls HUD indicator)
                target.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(ModEffects.BLOODBOUND), 5, 0, true, false));

                BloodBindTickPayload bindPayload = new BloodBindTickPayload(caster.getId(), target.getId(), strain);
                Set<ServerPlayerEntity> bindViewers = new HashSet<>();
                PlayerLookup.tracking(caster).forEach(bindViewers::add);
                bindViewers.add(caster);
                PlayerLookup.tracking(caster.getServerWorld(), target.getBlockPos()).forEach(bindViewers::add);
                bindViewers.forEach(p -> ServerPlayNetworking.send(p, bindPayload));
            }
        }
    }

    private static boolean isBound(ServerPlayerEntity caster) {
        return ACTIVE_BINDS.containsKey(caster.getUuid());
    }

    private static void startBind(ServerPlayerEntity caster, LivingEntity target) {
        if (caster == null || target == null) return;
        BindInstance b = ACTIVE_BINDS.computeIfAbsent(caster.getUuid(), k -> new BindInstance());
        b.targetUuid = target.getUuid();
        b.ticksLeft = BIND_DURATION_TICKS;
    }

    private static void breakBind(ServerPlayerEntity caster) {
        ACTIVE_BINDS.remove(caster.getUuid());
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

    @Override public String getName() { return Text.translatable("power.loopypowers.blood.name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.blood.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.blood.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.blood.ultimate_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.blood.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Text.translatable("power.loopypowers.blood.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.blood.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.blood.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.blood.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.blood.description.ultimate").getString();
    }
}
