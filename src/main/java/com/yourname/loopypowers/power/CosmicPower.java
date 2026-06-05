package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.BlackHoleEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CosmicPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */
    private static final Map<UUID, Map<UUID, FateInstance>> ACTIVE_FATES = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_STARS = new HashMap<>();

    private static class FateInstance {
        public UUID ownerUuid;
        public float storedDamage;
        public int timerTicks;
        public int detonateTicks;
        public int immuneTicks;
        public int meleeTimerCdTicks;
    }

    private boolean applyingReducedDamage = false;

    // ── Passive tuning ────────────────────────────────────────────────────────

    private static final int   FATE_TIMER_MAX         = 300; // 15 seconds
    private static final float FATE_DAMAGE_CAP        = 55.0f;
    private static final int   FATE_DETONATE_TICKS    = 40;
    private static final int   FATE_DETONATE_INTERVAL = 10;
    private static final float MELEE_FATE_RATIO       = 0.6f;
    private static final int   MELEE_TIMER_REDUCE     = 25; // ticks subtracted per hit
    private static final int   MELEE_TIMER_CD         = 10;
    private static final int   FATE_IMMUNE_TICKS      = 200;

    // Distance Decay
    private static final double FATE_DECAY_DISTANCE   = 9.0;
    private static final float  FATE_DECAY_PER_TICK   = 0.5f;

    // ── Primary tuning ────────────────────────────────────────────────────────

    private static final double RAY_RANGE         = 30.0;
    private static final float  RAY_DIRECT_DAMAGE = 3.5f;
    private static final float  RAY_FATE_STORE    = 7.0f;
    private static final int    RAY_TIMER_ADD     = 40; // Increases timer to stall detonation

    // ── Secondary tuning ─────────────────────────────────────────────────────

    private static final double STAR_SLAM_RADIUS     = 4.0;
    private static final float  STAR_FATE_STORE      = 9.0f;
    private static final float  STAR_TIMER_REDUCTION = 0.50f; // halves the timer
    private static final int    STAR_ARC_TICKS       = 40;

    // ── Ultimate tuning ───────────────────────────────────────────────────────

    private static final int    BH_TIMER_REDUCTION = 5;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("cos_"));
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("cos_"));

        player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.BRACED));
        player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));

        ACTIVE_STARS.remove(player.getUuid());

        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.remove(player.getUuid());
        if (myTargets != null && player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                for (UUID targetId : myTargets.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) {
                        le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));
                        // Tell clients to stop rendering
                        CosmicFateAuraPayload clearPayload = new CosmicFateAuraPayload(le.getId(), 0, 0);
                        PlayerLookup.tracking(w, le.getBlockPos()).forEach(p -> ServerPlayNetworking.send(p, clearPayload));
                    }
                }
            }
        }

        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                for (BlackHoleEntity bh : w.getEntitiesByClass(BlackHoleEntity.class, player.getBoundingBox().expand(150), Entity::isAlive)) {
                    if (player.equals(bh.getOwner())) {
                        bh.discard();
                    }
                }
            }
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        ServerWorld world = player.getServerWorld();
        UUID playerId = player.getUuid();

        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.get(playerId);

        if (myTargets != null && !myTargets.isEmpty()) {
            Iterator<Map.Entry<UUID, FateInstance>> it = myTargets.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<UUID, FateInstance> entry = it.next();
                UUID targetId = entry.getKey();
                FateInstance fate = entry.getValue();

                Entity ent = world.getEntity(targetId);
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
                    it.remove();
                    continue;
                }

                if (fate.immuneTicks > 0) {
                    fate.immuneTicks--;
                    if (fate.immuneTicks <= 0 && fate.storedDamage <= 0) it.remove();
                    continue;
                }

                // decay
                double distSq = target.squaredDistanceTo(player);
                if (distSq > FATE_DECAY_DISTANCE * FATE_DECAY_DISTANCE && fate.detonateTicks <= 0) {
                    fate.storedDamage -= FATE_DECAY_PER_TICK;

                    if (fate.storedDamage <= 0) {
                        CosmicFateAuraPayload clearPayload = new CosmicFateAuraPayload(target.getId(), 0, 0);
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(p -> ServerPlayNetworking.send(p, clearPayload));
                        syncFateEffect(target, 0);

                        world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, target.getSoundCategory(), 1.0f, 0.5f);
                        world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.4, 0.4, 0.4, 0.1);

                        it.remove();
                        continue;
                    } else if (target.age % 10 == 0) {
                        float sd = fate.storedDamage;
                        int tt = fate.timerTicks;
                        CosmicFateAuraPayload syncPayload = new CosmicFateAuraPayload(target.getId(), sd, tt);
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(p -> ServerPlayNetworking.send(p, syncPayload));
                    }
                }

                if (fate.meleeTimerCdTicks > 0) {
                    fate.meleeTimerCdTicks--;
                }

                if (fate.detonateTicks > 0) {
                    if (fate.storedDamage > 0 && target.age % FATE_DETONATE_INTERVAL == 0) {
                        int ticks = FATE_DETONATE_TICKS / FATE_DETONATE_INTERVAL;
                        float damagePerTick = fate.storedDamage / ticks;

                        target.damage(ModDamageTypes.fate(world, player), damagePerTick);

                        // detonate tick visuals → client
                        CosmicDetonateTickPayload dtPayload = new CosmicDetonateTickPayload(target.getId());
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(p ->
                                ServerPlayNetworking.send(p, dtPayload));

                        if (target instanceof ServerPlayerEntity targetPlayer) {
                            CameraShake.shakeNearby(targetPlayer, 5, 10, 0.06f);
                        }
                    }

                    fate.detonateTicks--;
                    if (fate.detonateTicks <= 0) {
                        fate.storedDamage = 0;
                        fate.immuneTicks = FATE_IMMUNE_TICKS;
                        target.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));
                    }
                    continue;
                }

                if (fate.timerTicks > 0) {
                    fate.timerTicks--;
                    if (fate.timerTicks <= 0) {
                        // Tell clients to clear the aura so the explosion looks clean
                        CosmicFateAuraPayload clearAura = new CosmicFateAuraPayload(target.getId(), 0, 0);
                        Set<ServerPlayerEntity> clearViewers = new HashSet<>();
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(clearViewers::add);
                        clearViewers.add(player);
                        clearViewers.forEach(p -> ServerPlayNetworking.send(p, clearAura));

                        fate.detonateTicks = FATE_DETONATE_TICKS;

                        // detonate start visuals → client
                        double tx = target.getX(), ty = target.getY(), tz = target.getZ();
                        CosmicDetonateStartPayload startPayload = new CosmicDetonateStartPayload(tx, ty, tz);
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(p ->
                                ServerPlayNetworking.send(p, startPayload));

                        world.playSound(null, target.getBlockPos(),
                                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                                target.getSoundCategory(), 0.8f, 0.6f);

                        target.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));
                    } else {
                        // fate aura visuals → client
                        CosmicFateAuraPayload auraPayload = new CosmicFateAuraPayload(
                                target.getId(), fate.storedDamage, fate.timerTicks);
                        Set<ServerPlayerEntity> auraViewers = new HashSet<>();
                        PlayerLookup.tracking(world, target.getBlockPos()).forEach(auraViewers::add);
                        auraViewers.add(player);
                        auraViewers.forEach(p -> ServerPlayNetworking.send(p, auraPayload));

                        syncFateEffect(target, fate.timerTicks);
                    }
                } else if (fate.storedDamage <= 0) {
                    it.remove();
                }
            }
        }

        if (ACTIVE_STARS.containsKey(playerId)) {
            int ticks = ACTIVE_STARS.get(playerId) - 1;
            if (ticks <= 0) {
                ACTIVE_STARS.remove(playerId);
            } else {
                ACTIVE_STARS.put(playerId, ticks);
                handleShootingStar(player);
            }
        }
    }

    /* ============================================================
       PASSIVE (ATTACK HOOK)
       ============================================================ */

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;
        if (source.getSource() != attacker) return true;
        if (this.applyingReducedDamage) return true;

        if (source.isOf(ModDamageTypes.FATE) || source.isOf(ModDamageTypes.BLACK_HOLE)) return true;

        applyMeleeFate(attacker, target, amount);

        this.applyingReducedDamage = true;
        target.damage(source, amount * 0.4f);
        this.applyingReducedDamage = false;

        return false;
    }

    public static void applyMeleeFate(ServerPlayerEntity attacker, LivingEntity target, float damageDealt) {
        float fatePortion = damageDealt * MELEE_FATE_RATIO;

        Map<UUID, FateInstance> playerFates = ACTIVE_FATES.get(attacker.getUuid());
        FateInstance fate = playerFates != null ? playerFates.get(target.getUuid()) : null;

        if (fate == null) {
            // First hit: starts timer at FATE_TIMER_MAX
            addFate(target, fatePortion, 0, attacker.getUuid());
        } else {
            // Subsequent hits: reduce timer to accelerate detonation
            int timerMod = 0;
            if (fate.meleeTimerCdTicks <= 0) {
                timerMod = -MELEE_TIMER_REDUCE;
                fate.meleeTimerCdTicks = MELEE_TIMER_CD;
            }
            addFate(target, fatePortion, timerMod, attacker.getUuid());
        }
    }

    private static void addFate(LivingEntity target, float fateDmg, int timerChange, UUID attackerUuid) {
        UUID targetId = target.getUuid();
        FateInstance fate = null;
        UUID previousOwner = null;

        for (Map.Entry<UUID, Map<UUID, FateInstance>> entry : ACTIVE_FATES.entrySet()) {
            if (entry.getValue().containsKey(targetId)) {
                fate = entry.getValue().get(targetId);
                previousOwner = entry.getKey();
                break;
            }
        }

        if (fate == null) {
            fate = new FateInstance();
        }

        if (attackerUuid != null && !attackerUuid.equals(previousOwner)) {
            if (previousOwner != null) {
                ACTIVE_FATES.get(previousOwner).remove(targetId);
            }
            ACTIVE_FATES.computeIfAbsent(attackerUuid, k -> new HashMap<>()).put(targetId, fate);
            fate.ownerUuid = attackerUuid;
        }

        if (fate.immuneTicks > 0) return;

        float currentDmg = fate.storedDamage;
        float newDmg = Math.min(currentDmg + fateDmg, FATE_DAMAGE_CAP);

        boolean hitCapNow   = currentDmg < FATE_DAMAGE_CAP && newDmg >= FATE_DAMAGE_CAP;
        boolean alreadyAtCap = currentDmg >= FATE_DAMAGE_CAP && fateDmg > 0;

        fate.storedDamage = newDmg;

        if ((hitCapNow || alreadyAtCap) && target.getWorld() instanceof ServerWorld world) {
            // fate cap burst → client (position already includes height offset)
            double ex = target.getX();
            double ey = target.getY() + target.getHeight() * 0.6;
            double ez = target.getZ();
            PlayerLookup.tracking(world, target.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new CosmicFateCapPayload(ex, ey, ez)));
            playFateCapSound(world, target);
        }

        if (fate.timerTicks <= 0) {
            fate.timerTicks = FATE_TIMER_MAX;
        } else {
            fate.timerTicks = MathHelper.clamp(fate.timerTicks + timerChange, 1, FATE_TIMER_MAX);
        }

        // Send payload with the updated stored damage and ticks to start/refresh the visual
        if (target.getWorld() instanceof ServerWorld sw) {
            float sd = fate.storedDamage;
            int tt = fate.timerTicks;
            CosmicFateAuraPayload auraPayload = new CosmicFateAuraPayload(target.getId(), sd, tt);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(sw, target.getBlockPos()).forEach(viewers::add);
            if (target instanceof ServerPlayerEntity sp) viewers.add(sp);
            viewers.forEach(p -> ServerPlayNetworking.send(p, auraPayload));
        }
        syncFateEffect(target, fate.timerTicks);
    }

    static void reduceFateTimer(LivingEntity target, float reduction) {
        UUID targetId = target.getUuid();
        FateInstance fate = null;

        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        fate.timerTicks = Math.max(1, fate.timerTicks - Math.round(fate.timerTicks * reduction));

        // Notify clients of the timer deduction so the particles stay perfectly synced
        if (target.getWorld() instanceof ServerWorld sw) {
            float sd = fate.storedDamage;
            int tt = fate.timerTicks;
            CosmicFateAuraPayload payload = new CosmicFateAuraPayload(target.getId(), sd, tt);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(sw, target.getBlockPos()).forEach(viewers::add);
            if (target instanceof ServerPlayerEntity sp) viewers.add(sp);
            viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
        }
        syncFateEffect(target, fate.timerTicks);
    }

    private static void syncFateEffect(LivingEntity entity, int timerTicks) {
        entity.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));
        if (timerTicks <= 0) return;
        entity.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.FATE),
                timerTicks, 0, false, false, true));
    }

    private static void playFateCapSound(ServerWorld world, LivingEntity entity) {
        world.playSound(null, entity.getBlockPos(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE,
                entity.getSoundCategory(), 0.8f, 1.8f);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(RAY_RANGE));

        var blockHit = world.raycast(new net.minecraft.world.RaycastContext(
                origin, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player));
        if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        // ray beam → client
        {
            double sx = origin.x, sy = origin.y, sz = origin.z;
            double ex = end.x, ey = end.y, ez = end.z;
            CosmicRayPayload rayPayload = new CosmicRayPayload(sx, sy, sz, ex, ey, ez);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, rayPayload));
        }

        LivingEntity firstTarget = getEntityOnBeam(world, player, origin, end);

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                new net.minecraft.util.math.Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);
            if (hit.isEmpty()) continue;

            e.damage(ModDamageTypes.cosmicRay(world, player), RAY_DIRECT_DAMAGE);
            addFate(e, RAY_FATE_STORE, RAY_TIMER_ADD, player.getUuid());

            // per-entity hit spark → client
            Vec3d hitPos = hit.get();
            double hx = hitPos.x, hy = hitPos.y, hz = hitPos.z;
            PlayerLookup.tracking(world, e.getBlockPos()).forEach(p ->
                    ServerPlayNetworking.send(p, new CosmicRayHitPayload(hx, hy, hz)));
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                player.getSoundCategory(), 0.4f, 1.6f);
        world.playSound(null, player.getBlockPos(),
                ModSounds.COSMICRAY,
                player.getSoundCategory(), 0.4f, 1.6f);

        player.swingHand(Hand.MAIN_HAND, true);

        if (firstTarget != null) {
            // first-target impact → client
            Vec3d hitPos = firstTarget.getBoundingBox().getCenter();
            double hx = hitPos.x, hy = hitPos.y, hz = hitPos.z;
            CosmicRayImpactPayload impactPayload = new CosmicRayImpactPayload(hx, hy, hz);
            Set<ServerPlayerEntity> impactViewers = new HashSet<>();
            PlayerLookup.tracking(world, firstTarget.getBlockPos()).forEach(impactViewers::add);
            impactViewers.add(player);
            impactViewers.forEach(p -> ServerPlayNetworking.send(p, impactPayload));
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        player.setVelocity(0, 1.2, 0);
        player.velocityModified = true;

        ACTIVE_STARS.put(player.getUuid(), STAR_ARC_TICKS);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                player.getSoundCategory(), 0.9f, 0.8f);

        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.BRACED), 100, 0, true, false, true));
    }

    private void handleShootingStar(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // trail → client
        {
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            CosmicStarTrailPayload trailPayload = new CosmicStarTrailPayload(px, py, pz);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, trailPayload));
        }

        Vec3d velocity = player.getVelocity();

        if (velocity.y > 0) {
            Vec3d look = player.getRotationVec(1.0f);
            player.addVelocity(look.x * 0.02, 0, look.z * 0.02);
            return;
        }

        Vec3d look = player.getRotationVec(1.0f);
        player.setVelocity(look.x * 1.2, -1.5, look.z * 1.2);
        player.velocityModified = true;

        boolean hitGround = player.isOnGround() || player.verticalCollision;
        if (!hitGround) return;

        Vec3d slamPos = player.getPos();

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(STAR_SLAM_RADIUS),
                en -> en.isAlive() && en != player)) {

            if (e.getPos().distanceTo(slamPos) > STAR_SLAM_RADIUS) continue;

            e.damage(ModDamageTypes.shootingStar(world, player), 4.0f);
            addFate(e, STAR_FATE_STORE, 0, player.getUuid());
            reduceFateTimer(e, STAR_TIMER_REDUCTION);
        }

        // slam impact → client
        {
            double sx = slamPos.x, sy = slamPos.y, sz = slamPos.z;
            CosmicSlamPayload slamPayload = new CosmicSlamPayload(sx, sy, sz);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, slamPayload));
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                player.getSoundCategory(), 1.0f, 0.8f);

        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        ACTIVE_STARS.remove(player.getUuid());
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d lookDir = player.getRotationVec(1.0f).normalize();
        Vec3d spawnPos = player.getEyePos().add(lookDir.multiply(3.0));

        BlackHoleEntity bh = new BlackHoleEntity(ModEntities.BLACK_HOLE_ENTITY, world);
        bh.setOwner(player);
        bh.setPos(spawnPos.x, spawnPos.y - 1.0, spawnPos.z);
        bh.setTravelDirection(lookDir);
        world.spawnEntity(bh);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_PORTAL_AMBIENT,
                player.getSoundCategory(), 1.2f, 0.4f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), 0.6f, 0.5f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    public static void drainFateTimer(LivingEntity target) {
        UUID targetId = target.getUuid();
        FateInstance fate = null;

        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        fate.timerTicks = Math.max(1, fate.timerTicks - BH_TIMER_REDUCTION);

        if (target.getWorld() instanceof ServerWorld sw) {
            float sd = fate.storedDamage;
            int tt = fate.timerTicks;
            CosmicFateAuraPayload payload = new CosmicFateAuraPayload(target.getId(), sd, tt);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(sw, target.getBlockPos()).forEach(viewers::add);
            if (target instanceof ServerPlayerEntity sp) viewers.add(sp);
            viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
        }
        syncFateEffect(target, fate.timerTicks);
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 22_000; }
    @Override public long getUltimateCooldownMs()  { return 250_000; }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Text.translatable("power.loopypowers.cosmic.name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.cosmic.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.cosmic.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.cosmic.ultimate_name").getString(); }
    @Override public String getPassiveName() { return Text.translatable("power.loopypowers.cosmic.passive_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.cosmic.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.cosmic.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.cosmic.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.cosmic.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.cosmic.description.ultimate").getString();
    }

    // OTHER HELPERS

    public static boolean cleanseFate(LivingEntity target) {
        boolean cleansed = false;
        UUID targetId = target.getUuid();

        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            if (playerFates.remove(targetId) != null) {
                cleansed = true;
            }
        }

        if (cleansed) {
            target.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FATE));
            // Tell clients to immediately stop rendering the cosmic aura
            if (target.getWorld() instanceof ServerWorld sw) {
                CosmicFateAuraPayload clearPayload = new CosmicFateAuraPayload(target.getId(), 0, 0);
                Set<ServerPlayerEntity> viewers = new HashSet<>();
                PlayerLookup.tracking(sw, target.getBlockPos()).forEach(viewers::add);
                if (target instanceof ServerPlayerEntity sp) viewers.add(sp);
                viewers.forEach(p -> ServerPlayNetworking.send(p, clearPayload));
            }
            return true;
        }

        return false;
    }

    private LivingEntity getEntityOnBeam(ServerWorld world, ServerPlayerEntity player, Vec3d origin, Vec3d end) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {
            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);
            if (hit.isPresent()) {
                double dist = origin.squaredDistanceTo(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }
}
