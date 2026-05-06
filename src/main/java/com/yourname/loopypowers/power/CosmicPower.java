package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.BlackHoleEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmicPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */
    // Map structure: Attacker UUID -> (Target UUID -> FateInstance)
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

    private static final int   FATE_TIMER_DEFAULT     = 200;
    private static final int   FATE_TIMER_MAX         = 300;
    private static final float FATE_DAMAGE_CAP        = 60.0f;
    private static final int   FATE_DETONATE_TICKS    = 30;
    private static final int   FATE_DETONATE_INTERVAL = 10;
    private static final float MELEE_FATE_RATIO       = 0.8f;
    private static final int   MELEE_TIMER_ADD        = 12;
    private static final int   MELEE_TIMER_CD         = 10;
    private static final int   FATE_IMMUNE_TICKS      = 200;

    // ── Primary tuning ────────────────────────────────────────────────────────

    private static final double RAY_RANGE         = 30.0;
    private static final float  RAY_DIRECT_DAMAGE = 3.5f;
    private static final float  RAY_FATE_STORE    = 9.0f;
    private static final int    RAY_TIMER_ADD     = 50;

    // ── Secondary tuning ─────────────────────────────────────────────────────

    private static final double STAR_SLAM_RADIUS     = 4.0;
    private static final float  STAR_FATE_STORE      = 14.0f;
    private static final float  STAR_TIMER_REDUCTION = 0.60f;
    private static final int    STAR_ARC_TICKS       = 40;

    // ── Ultimate tuning ───────────────────────────────────────────────────────

    private static final double BH_OUTER_RADIUS    = 20.0;
    private static final float  BH_TIMER_REDUCTION = 1.5f;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("cos_")); // Clean old tags
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("cos_"));
        player.removeStatusEffect(ModEffects.BRACED);
        player.removeStatusEffect(ModEffects.FATE);

        ACTIVE_STARS.remove(player.getUuid());

        // Instantly remove all targets owned by this player
        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.remove(player.getUuid());
        if (myTargets != null && player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                for (UUID targetId : myTargets.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) le.removeStatusEffect(ModEffects.FATE);
                }
            }
        }

        // Despawn active black holes
        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                for (BlackHoleEntity bh : w.getEntitiesByClass(BlackHoleEntity.class, player.getBoundingBox().expand(150), Entity::isAlive)) {
                    if (player.equals(bh.getOwner())) {
                        bh.discard();
                    }
                }
            }
        }

        // Despawn active black holes
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
        ServerWorld world = player.getServerWorld();
        UUID playerId = player.getUuid();

        // 1. Get ONLY the targets owned by this specific player
        Map<UUID, FateInstance> myTargets = ACTIVE_FATES.get(playerId);

        // If the player has targets, process them
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

                // Tick Immunity
                if (fate.immuneTicks > 0) {
                    fate.immuneTicks--;
                    if (fate.immuneTicks <= 0 && fate.storedDamage <= 0) it.remove(); // fully expired
                    continue;
                }

                // Tick Melee CD
                if (fate.meleeTimerCdTicks > 0) {
                    fate.meleeTimerCdTicks--;
                }

                // Tick Detonation
                if (fate.detonateTicks > 0) {
                    if (fate.storedDamage > 0 && target.age % FATE_DETONATE_INTERVAL == 0) {
                        int ticks = FATE_DETONATE_TICKS / FATE_DETONATE_INTERVAL;
                        float damagePerTick = fate.storedDamage / ticks;

                        target.damage(ModDamageTypes.fate(world, player), damagePerTick);
                        spawnDetonateParticles(world, target);
                    }

                    fate.detonateTicks--;
                    if (fate.detonateTicks <= 0) {
                        fate.storedDamage = 0;
                        fate.immuneTicks = FATE_IMMUNE_TICKS;
                        target.removeStatusEffect(ModEffects.FATE);
                    }
                    continue;
                }

                // Tick Timer
                if (fate.timerTicks > 0) {
                    fate.timerTicks--;
                    if (fate.timerTicks <= 0) {
                        fate.detonateTicks = FATE_DETONATE_TICKS;
                        spawnDetonateStartParticles(world, target);
                        target.removeStatusEffect(ModEffects.FATE);
                    } else {
                        spawnFateAuraParticles(world, target, fate);
                        syncFateEffect(target, fate.timerTicks);
                    }
                } else if (fate.storedDamage <= 0) {
                    // If it has no timer, no immunity, no detonation, and no damage, clean it up
                    it.remove();
                }
            }
        }

        // Shooting star logic
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
        addFate(target, fatePortion, 0, attacker.getUuid());

        // check if melee CD allows us to add timer
        Map<UUID, FateInstance> playerFates = ACTIVE_FATES.get(attacker.getUuid());
        if (playerFates != null) {
            FateInstance fate = playerFates.get(target.getUuid());
            if (fate != null && fate.meleeTimerCdTicks <= 0) {
                addFate(target, 0, MELEE_TIMER_ADD, attacker.getUuid());
                fate.meleeTimerCdTicks = MELEE_TIMER_CD;
            }
        }
    }

    private static void addFate(LivingEntity target, float fateDmg, int timerAdd, UUID attackerUuid) {
        UUID targetId = target.getUuid();
        FateInstance fate = null;
        UUID previousOwner = null;

        // Find if this target already has fate applied by ANY player
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

        // Handle ownership transfer (if a new attacker hits an already-afflicted target)
        if (attackerUuid != null && !attackerUuid.equals(previousOwner)) {
            if (previousOwner != null) {
                ACTIVE_FATES.get(previousOwner).remove(targetId); // Remove from old owner
            }
            // Add to new owner
            ACTIVE_FATES.computeIfAbsent(attackerUuid, k -> new HashMap<>()).put(targetId, fate);
            fate.ownerUuid = attackerUuid;
        }

        if (fate.immuneTicks > 0) return;

        float currentDmg = fate.storedDamage;
        float newDmg = Math.min(currentDmg + fateDmg, FATE_DAMAGE_CAP);

        boolean hitCapNow = currentDmg < FATE_DAMAGE_CAP && newDmg >= FATE_DAMAGE_CAP;
        boolean alreadyAtCap = currentDmg >= FATE_DAMAGE_CAP && fateDmg > 0;

        fate.storedDamage = newDmg;

        if ((hitCapNow || alreadyAtCap) && target.getWorld() instanceof ServerWorld world) {
            spawnFateCapParticles(world, target);
            playFateCapSound(world, target);
        }

        if (fate.timerTicks <= 0) {
            fate.timerTicks = FATE_TIMER_DEFAULT;
        } else {
            fate.timerTicks = Math.min(fate.timerTicks + timerAdd, FATE_TIMER_MAX);
        }

        syncFateEffect(target, fate.timerTicks);
    }

    static void reduceFateTimer(LivingEntity target, float reduction) {
        UUID targetId = target.getUuid();
        FateInstance fate = null;

        // Find fate instance regardless of who owns it
        for (Map<UUID, FateInstance> playerFates : ACTIVE_FATES.values()) {
            fate = playerFates.get(targetId);
            if (fate != null) break;
        }

        if (fate == null || fate.timerTicks <= 0 || fate.detonateTicks > 0 || fate.immuneTicks > 0) return;

        fate.timerTicks = Math.max(0, fate.timerTicks - Math.round(fate.timerTicks * reduction));
        syncFateEffect(target, fate.timerTicks);
    }

    // ── Status-effect sync ────────────────────────────────────────────────────
    private static void syncFateEffect(LivingEntity entity, int timerTicks) {
        entity.removeStatusEffect(ModEffects.FATE);
        if (timerTicks <= 0) return;

        entity.addStatusEffect(new StatusEffectInstance(
                ModEffects.FATE,
                timerTicks,
                0,
                false,
                false,      // hide particles
                true        // show icon in hotbar / inventory HUD
        ));
    }

    // ── Particle helpers ─────────────────────────────────────────────────────

    private void spawnFateAuraParticles(ServerWorld world, LivingEntity entity, FateInstance fate) {
        int starCount = MathHelper.clamp((int)fate.storedDamage, 0, 19);
        long time     = entity.getWorld().getTime();

        float  timerFraction = (float) fate.timerTicks / FATE_TIMER_MAX;
        double speed         = 0.05 + (1.0 - timerFraction) * 0.35;
        double angle         = time * speed;
        double orbitRadius   = 0.8 + 0.35;
        int    trailPoints   = (int)(3 + speed * 10);

        for (int t = 0; t < trailPoints; t++) {
            double trailAngle = angle - (t * 0.15);
            double cx   = entity.getX() + Math.cos(trailAngle) * orbitRadius;
            double cz   = entity.getZ() + Math.sin(trailAngle) * orbitRadius;
            double cy   = entity.getBodyY(0.55);
            float  size = 1.2f - (t * 0.08f);

            world.spawnParticles(
                    new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.1f), Math.max(0.4f, size)),
                    cx, cy, cz, 1, 0, 0, 0, 0);
        }

        if (timerFraction < 0.3f) {
            int flameCount = (int)((0.3f - timerFraction) * 20);
            for (int i = 0; i < flameCount; i++) {
                double randAngle = world.random.nextDouble() * Math.PI * 2;
                double r  = orbitRadius + 0.1 + world.random.nextDouble() * 0.2;
                double fx = entity.getX() + Math.cos(randAngle) * r;
                double fz = entity.getZ() + Math.sin(randAngle) * r;
                double fy = entity.getBodyY(0.55) + world.random.nextDouble() * 0.3;
                world.spawnParticles(ParticleTypes.FLAME, fx, fy, fz, 1,
                        0.01, 0.02, 0.01, 0.01);
            }
        }

        if (entity.age % 3 != 0) return;

        for (int i = 0; i < starCount; i++) {
            long seed      = entity.getId() * 341873128712L + i * 132897987541L;
            double offsetX = ((seed >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
            double offsetY = ((seed >> 8)  & 0xFF) / 255.0;
            double offsetZ = ((seed)       & 0xFF) / 255.0 * 2.0 - 1.0;
            world.spawnParticles(ParticleTypes.WHITE_ASH,
                    entity.getX() + offsetX * 0.8,
                    entity.getBodyY(offsetY * 1.2),
                    entity.getZ() + offsetZ * 0.8,
                    1, 0.01, 0.02, 0.01, 0.001);
        }
    }

    private void spawnDetonateStartParticles(ServerWorld world, LivingEntity entity) {
        Vec3d pos = entity.getPos();
        world.spawnParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z, 3, 0.2, 0.2, 0.2, 0);
        for (int i = 0; i < 25; i++) {
            double vx = (world.random.nextDouble() - 0.5) * 0.6;
            double vy = world.random.nextDouble() * 0.6;
            double vz = (world.random.nextDouble() - 0.5) * 0.6;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1, pos.z, 1, vx, vy, vz, 0.1);
        }
        world.playSound(null, entity.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                entity.getSoundCategory(), 0.8f, 0.6f);
    }

    private void spawnDetonateParticles(ServerWorld world, LivingEntity entity) {
        if (entity.age % 2 != 0) return;
        Vec3d pos = entity.getPos();
        for (int i = 0; i < 6; i++) {
            double a = world.getTime() * 0.3 + i;
            double x = pos.x + Math.cos(a) * 0.8;
            double z = pos.z + Math.sin(a) * 0.8;
            double y = pos.y + 0.8;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1,
                    (pos.x - x) * 0.2, 0.02, (pos.z - z) * 0.2, 0);
        }

        if (entity instanceof ServerPlayerEntity targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 5, 10, 0.06f);
        }
    }

    private static void spawnFateCapParticles(ServerWorld world, LivingEntity entity) {
        Vec3d pos = entity.getPos().add(0, entity.getHeight() * 0.6, 0);
        for (int i = 0; i < 40; i++) {
            double a     = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.4 + world.random.nextDouble() * 0.6;
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 1,
                    Math.cos(a) * speed,
                    (world.random.nextDouble() - 0.3) * 0.6,
                    Math.sin(a) * speed, 0.05);
        }
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

        spawnCosmicRayParticles(world, origin, end);

        LivingEntity firstTarget = getEntityOnBeam(world, player, origin, end);

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                new net.minecraft.util.math.Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);
            if (hit.isEmpty()) continue;

            e.damage(ModDamageTypes.cosmicRay(world, player), RAY_DIRECT_DAMAGE);
            addFate(e, RAY_FATE_STORE, RAY_TIMER_ADD, player.getUuid());

            Vec3d hitPos = hit.get();
            world.spawnParticles(ParticleTypes.FLASH,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.05, 0.05, 0.05, 0);
            world.spawnParticles(ParticleTypes.END_ROD,
                    hitPos.x, hitPos.y, hitPos.z, 3, 0.2, 0.2, 0.2, 0.02);
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                player.getSoundCategory(), 0.4f, 1.6f);
        world.playSound(null, player.getBlockPos(),
                ModSounds.COSMICRAY,
                player.getSoundCategory(), 0.4f, 1.6f);

        player.swingHand(Hand.MAIN_HAND, true);

        if (firstTarget != null) {
            Vec3d hitPos = firstTarget.getBoundingBox().getCenter();
            world.spawnParticles(ParticleTypes.FLASH,
                    hitPos.x, hitPos.y, hitPos.z, 2, 0.1, 0.1, 0.1, 0);
            for (int i = 0; i < 8; i++) {
                world.spawnParticles(ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z, 1,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.3,
                        (world.random.nextDouble() - 0.5) * 0.4, 0.05);
            }
        }
    }

    private void spawnCosmicRayParticles(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d dir  = to.subtract(from);
        double len = dir.length();
        Vec3d step = dir.normalize().multiply(0.25);
        int count  = (int)(len / 0.25);
        Vec3d pos  = from;

        for (int i = 0; i < count; i++) {
            double angle  = i * 0.4;
            double spiral = 0.08;
            double x = pos.x + Math.cos(angle) * spiral;
            double y = pos.y;
            double z = pos.z + Math.sin(angle) * spiral;

            world.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.4f, 0.0f, 0.6f), 1.1f),
                    x, y, z, 1, 0, 0, 0, 0);

            if (i % 2 == 0) {
                world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 1,
                        0.01, 0.01, 0.01, 0.01);
            }
            if (world.random.nextFloat() < 0.25f) {
                world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1,
                        0.02, 0.02, 0.02, 0.01);
            }

            pos = pos.add(step);
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
                ModEffects.BRACED, 100, 0, true, false, true));
    }

    private void handleShootingStar(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        spawnStarTrailParticles(world, player);

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

        spawnSlamParticles(world, slamPos);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 1.0f, 0.8f);

        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        ACTIVE_STARS.remove(player.getUuid());
    }

    private void spawnStarTrailParticles(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();
        for (int i = 0; i < 3; i++) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.5, pos.z, 1,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.05,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.02);
        }
    }

    private void spawnSlamParticles(ServerWorld world, Vec3d pos) {
        for (int i = 0; i < 30; i++) {
            double angle = (Math.PI * 2) * i / 30;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.1, pos.z, 1,
                    Math.cos(angle) * 0.4, 0.1, Math.sin(angle) * 0.4, 0.05);
        }
        world.spawnParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 0.5, pos.z, 3, 0.2, 0.2, 0.2, 0);
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
        reduceFateTimer(target, BH_TIMER_REDUCTION);
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return "Cosmic"; }
    @Override public String getPassiveName()   { return "Written in the Stars"; }
    @Override public String getPrimaryName()   { return "Pulsar"; }
    @Override public String getSecondaryName() { return "Starfall"; }
    @Override public String getUltimateName()  { return "Event Horizon"; }

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 22_000; }
    @Override public long getUltimateCooldownMs()  { return 250_000; }

    @Override
    public String getOverviewDescription() {
        return "Cosmic is a damage based power, the whole gimmick revolves around low damage being done initially, but having huge damage come in bursts later due" +
                " to the passive. All abilities revolve around this passive in different ways, and understanding how it works and how each ability interacts with it are" +
                " fundamental for doing well with this power. The explanation for the passive and each ability should help with this.";
    }

    @Override
    public String getPassiveDescription() {
        return "This is what your entire power revolves around. Your basic melee hits and some abilities apply a debuff called Fate, there are two elements to this debuff:" +
                " \n Fate damage: This is the amount of damage Fate currently stores, it increases with the hits you do and is indicated by the amount of dust coming off the" +
                " effected entity. This has a hard cap, when an entity has reached max damage, hitting it will play a sound and display star particles." +
                "\n Fate Timer: This is the time until the stored fate damage is quickly applied, it is indicated by the orbiting sun around the entity, with faster orbit speeds" +
                " meaning it is closer to detonation and extra fire particles will also appear when about to detonate." +
                " \n When the timer expires, the entity will explode and have the debuff removed, quickly taking all of the damage that was stored over a few seconds." +
                " They will then be immune to building fate for a long time (if they survive)." +
                "\n" +
                "\n But, your melee hits do significantly less damage and the lost damage is stored as Fate, melee hits also increase the countdown timer, giving you more time to build fate.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Fire a hitscan beam that deals minor direct damage but stores significant Fate damage on the target and extends their timer significantly." +
                " The beam pierces through entities but not blocks." +
                " This is a lot more reliable than extending it via melee hits.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Launch into the air and slam down towards your crosshair." +
                " The impact applies a small amount of fate damage to nearby enemies but significantly reduces their fate timer by a percentage." +
                " So this can be used to trigger detonations on groups of enemies.";
    }

    @Override
    public String getUltimateDescription() {
        return "Summon a slow-moving black hole a few blocks in front of you." +
                "\n This black hole travels slowly in the direction you cast it, having a large pull radius that gets stronger the closer entities are to the centre." +
                "\n Any entities in the centre of the black hole will take constant damage (this does not apply Fate damage) and their Fate timer will be quickly drained down." +
                "\n Essentially, this is a way to quickly drain fate and explode groups of entities over an area.";
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