package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class TelekinesisPower implements Power {

    /* ============================================================
       TAGS / CONSTANTS
       ============================================================ */

    private static final String TK_SUSPEND_TAG     = "tk_suspend_";
    private static final String TK_READY_THROW_TAG = "tk_ready_throw_";
    private static final String TK_CHOKE_TAG       = "tk_choke_";    // force choke after suspend expires

    private static final String TK_AIRBORNE_TAG = "tk_airborne_";    // force choke after suspend expires

    // ult
    private static final String DEBRIS_THROW_CD_TAG     = "tk_debris_throw_cd_";

    private static final Map<UUID, Vec3d> prevVelocity = new HashMap<>();

    // Maps a thrown/yanked entity's UUID to the UUID of the player who caused it.
    // Populated in markForImpactTracking, consumed in handleImpactDamage.
    private final Map<UUID, UUID> impactOwner = new HashMap<>();

    // Maps a suspended/choked entity's UUID to the UUID of the player who applied it.
    // Populated in activateSecondary, consumed in handleSuspendAndChoke.
    private final Map<UUID, UUID> suspendOwner = new HashMap<>();

    // ── Passive — Forceful Strikes ──────────────────────────────
    private static final double PASSIVE_KB_MULT     = 1.8;
    private static final double PASSIVE_KB_VERTICAL = 0.2;

    // ── Primary — Yank ──────────────────────────────────────────
    private static final double YANK_RANGE          = 12.0;
    private static final double YANK_CONE_DOT       = 0.6;
    private static final double YANK_STRENGTH       = 0.9;
    private static final double YANK_VERTICAL       = 0.3;

    // ── Secondary Suspend / throw phase ─────────────────────────────
    private static final int    SUSPEND_TICKS         = 40;
    private static final double SUSPEND_FLOAT_VEL     = 0.05;
    private static final int    THROW_READY_TICKS     = 60;
    private static final double THROW_SCAN_RANGE      = 14.0;
    private static final double THROW_SCAN_WIDTH      = 2.5;
    private static final double THROW_SPEED_H         = 1.5;
    private static final double THROW_SPEED_V         = 0.6;
    private static final double THROW_RELEASE_RANGE   = 12.0;

    // ── Choke phase ──────────────────────
    private static final int    CHOKE_TICKS           = 40;   // duration of choke
    private static final int    CHOKE_DAMAGE_INTERVAL = 18;   // ticks between damage pulses
    private static final float  CHOKE_DAMAGE_PER_TICK = 1.5f; // damage per pulse
    private static final double CHOKE_SQUEEZE_VEL     = -0.01; // upward squeeze velocity
    private static final double CHOKE_ORBIT_RADIUS_START = 1.0; // particle orbit start radius
    private static final double CHOKE_ORBIT_RADIUS_END   = 0.2; // tightens to this by end

    // ── Impact system ──
    private static final int    TK_AIRBORNE_TICKS   = 40;

    private static final double IMPACT_MIN_SPEED_H  = 0.6;
    private static final double IMPACT_MIN_SPEED_V  = 0.7;

    private static final float  IMPACT_WALL_DAMAGE  = 4.0f;
    private static final float  IMPACT_WALL_SCALE   = 2.5f;

    private static final float  IMPACT_FLOOR_DAMAGE = 2.5f;
    private static final float  IMPACT_FLOOR_SCALE  = 1.8f;

    // ── Ultimate ──────────────────────────────────
    private static final int    DEBRIS_MAX_BLOCKS            = 25;   // max orbiting blocks
    private static final double DEBRIS_HARVEST_RADIUS        = 10.0; // radius to harvest blocks from
    private static final int    DEBRIS_ORBIT_TICKS           = 260;  // how long ult lasts
    private static final double DEBRIS_ORBIT_RADIUS          = 4.5;  // orbit radius around player
    private static final double DEBRIS_ORBIT_RADIUS_INNER    = 2.5;  // inner ring radius (dual-ring)
    private static final double DEBRIS_ORBIT_SPEED           = 0.045;// outer ring radians per tick
    private static final double DEBRIS_ORBIT_SPEED_INNER     = 0.08; // inner ring — faster, opposite dir
    private static final double DEBRIS_PULL_RADIUS           = 12.0; // entity pull radius
    private static final double DEBRIS_PULL_STRENGTH         = 0.07;
    private static final float  DEBRIS_PULL_DAMAGE           = 0.1f;
    private static final int    DEBRIS_THROW_COOLDOWN        = 8;    // ticks between throws
    private static final int    DEBRIS_THROW_COUNT           = 5;    // blocks per throw
    private static final float  DEBRIS_THROW_DAMAGE          = 7.0f;
    private static final double DEBRIS_THROW_SPEED           = 2.4;
    private static final double DEBRIS_THROW_SPREAD          = 0.6; // shotgun spread per extra block
    private static final double DEBRIS_THROW_EXPLOSION_RADIUS = 4.0;
    // block regen
    private static final int    DEBRIS_REGEN_DELAY_TICKS = 25;  // no swing time before regen starts
    private static final int    DEBRIS_REGEN_INTERVAL    = 20;  // ticks per block regen
    private static final int    DEBRIS_REGEN_AMOUNT      = 5;   // blocks per regen tick

    private final Map<UUID, Double> orbitAngles = new HashMap<>();
    private int debrisFieldTicksRemaining = 0;
    private final Map<UUID, UUID> scheduledExplosions = new HashMap<>();
    private final Map<UUID, Vec3d> lastKnownBlockPos = new HashMap<>();
    private final Map<UUID, Long> lastSwingTime = new HashMap<>();

    /* ============================================================
       PARTICLE STUFF
       ============================================================ */
    // now up here cus idk felt like it

    private static final DustParticleEffect TK_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.2f, 0.7f), 1.2f);
    private static final DustParticleEffect TK_LIGHT_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.85f), 0.9f);
    private static final DustParticleEffect TK_MAGENTA =
            new DustParticleEffect(new Vector3f(0.85f, 0.0f, 0.5f), 1.5f);
    private static final DustParticleEffect TK_PINK_LARGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.75f), 2.2f);
    private static final DustParticleEffect TK_DARK_PINK =
            new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.35f), 1.8f);

    private static void spawnImpactRing(ServerWorld world, Vec3d pos, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            world.spawnParticles(TK_MAGENTA,
                    pos.x, pos.y + 0.1, pos.z,
                    1, Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius, 0.04);
        }
        world.spawnParticles(TK_PINK, pos.x, pos.y + 0.5, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
    }

    private static void spawnSuspendAura(ServerWorld world, LivingEntity entity, long time) {
        double radius = 0.7;
        for (int i = 0; i < 6; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 6);
            world.spawnParticles(TK_PINK,
                    entity.getX() + Math.cos(angle) * radius,
                    entity.getBodyY(0.6),
                    entity.getZ() + Math.sin(angle) * radius,
                    1, 0, 0.02, 0, 0);
        }
        if (time % 3 == 0) {
            world.spawnParticles(TK_LIGHT_PINK,
                    entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    2, 0.25, 0.1, 0.25, 0.01);
        }
    }

    private static void spawnChokeAura(ServerWorld world, LivingEntity entity, long time, float chokeProgress) {
        // Orbit tightens
        double radius = CHOKE_ORBIT_RADIUS_START
                + (CHOKE_ORBIT_RADIUS_END - CHOKE_ORBIT_RADIUS_START) * chokeProgress;

        // speed up
        double spinSpeed = 0.12 + chokeProgress * 0.30;
        int points = 8;

        for (int i = 0; i < points; i++) {
            double angle = (time * spinSpeed) + (i * Math.PI * 2.0 / points);
            double x = entity.getX() + Math.cos(angle) * radius;
            double z = entity.getZ() + Math.sin(angle) * radius;

            // Spiral upward
            double y = entity.getBodyY(0.3 + chokeProgress * 0.4);

            world.spawnParticles(TK_DARK_PINK, x, y, z, 1, 0, 0.01, 0, 0);
        }

        // final vortex
        if (chokeProgress > 0.5f) {
            double innerRadius = radius * 0.4;
            for (int i = 0; i < 4; i++) {
                double angle = -(time * spinSpeed * 1.5) + (i * Math.PI / 2.0);
                world.spawnParticles(TK_MAGENTA,
                        entity.getX() + Math.cos(angle) * innerRadius,
                        entity.getBodyY(0.5),
                        entity.getZ() + Math.sin(angle) * innerRadius,
                        1, 0, 0.02, 0, 0);
            }
        }

        if (time % 2 == 0) {
            world.spawnParticles(TK_DARK_PINK,
                    entity.getX(), entity.getBodyY(0.8), entity.getZ(),
                    2, 0.15, 0.08, 0.15, 0.03);
        }
    }

    private static void spawnBeam(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        int steps = (int)(dir.length() / 0.35);
        Vec3d step = dir.normalize().multiply(0.35);
        Vec3d pos = from;
        for (int i = 0; i < steps; i++) {
            if (i % 2 == 0) world.spawnParticles(TK_PINK, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0);
            if (world.random.nextFloat() < 0.2f) world.spawnParticles(TK_LIGHT_PINK, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            pos = pos.add(step);
        }
    }

    private static void spawnSlamImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 24, 0.5);
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2.0 * i / 20;
            world.spawnParticles(TK_PINK_LARGE,
                    pos.x + Math.cos(angle) * 1.5, pos.y + 0.1, pos.z + Math.sin(angle) * 1.5,
                    1, Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15, 0.02);
        }
        world.spawnParticles(TK_MAGENTA, pos.x, pos.y + 0.3, pos.z, 12, 0.5, 0.4, 0.5, 0.08);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 8, 0.4, 0.3, 0.4, 0.06);
    }

    private static void spawnWallImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 18, 0.4);
        world.spawnParticles(TK_MAGENTA, pos.x, pos.y + 0.5, pos.z, 10, 0.4, 0.4, 0.4, 0.07);
        world.spawnParticles(TK_PINK_LARGE, pos.x, pos.y + 0.3, pos.z, 5, 0.3, 0.3, 0.3, 0.04);

        for (int i = 0; i < 12; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.1 + world.random.nextDouble() * 0.2;
            world.spawnParticles(TK_DARK_PINK,
                    pos.x, pos.y + 0.5, pos.z,
                    1, Math.cos(angle) * speed, (world.random.nextDouble() - 0.3) * speed, Math.sin(angle) * speed, 0.01);
        }
    }

    private static void spawnFloorImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 12, 0.35);
        world.spawnParticles(TK_PINK, pos.x, pos.y + 0.2, pos.z, 5, 0.3, 0.1, 0.3, 0.04);
    }

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {}

    @Override
    public void onTick(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(30),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            handleSuspendAndChoke(e, world, time);
            handleImpactDamage(e, world);
        }

        tickTag(player, TK_READY_THROW_TAG);
        tickTag(player, "tk_throw_cd_");
        handleDebrisField(player);
        tickThrownBlocks(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        lastSwingTime.put(attacker.getUuid(), attacker.getServerWorld().getTime());

        Vec3d look = attacker.getRotationVec(1.0f);
        target.addVelocity(look.x * PASSIVE_KB_MULT, PASSIVE_KB_VERTICAL, look.z * PASSIVE_KB_MULT);
        target.velocityModified = true;

        markForImpactTracking(target, target.getVelocity(), attacker.getUuid());

        ServerWorld world = attacker.getServerWorld();
        spawnImpactRing(world, target.getPos(), 10, 0.3);
        world.spawnParticles(TK_LIGHT_PINK, target.getX(), target.getBodyY(0.7), target.getZ(), 4, 0.2, 0.3, 0.2, 0.03);
    }

    // IMPACT DAMAGE

    // attackerUuid is who caused the entity to become airborne, used for death messages
    private void markForImpactTracking(LivingEntity entity, Vec3d launchVelocity, UUID attackerUuid) {
        removeTagPrefix(entity, TK_AIRBORNE_TAG);
        entity.getCommandTags().add(TK_AIRBORNE_TAG + TK_AIRBORNE_TICKS);

        prevVelocity.put(entity.getUuid(), launchVelocity);
        impactOwner.put(entity.getUuid(), attackerUuid);
    }

    private void handleImpactDamage(LivingEntity entity, ServerWorld world) {
        if (!hasTag(entity, TK_AIRBORNE_TAG)) {
            prevVelocity.remove(entity.getUuid());
            impactOwner.remove(entity.getUuid());
            return;
        }

        tickTag(entity, TK_AIRBORNE_TAG);

        Vec3d prev = prevVelocity.get(entity.getUuid());
        Vec3d curr = entity.getVelocity();

        if (prev != null) {
            double prevH = Math.sqrt(prev.x * prev.x + prev.z * prev.z);
            double currH = Math.sqrt(curr.x * curr.x + curr.z * curr.z);

            boolean wallStopped =
                    prevH > IMPACT_MIN_SPEED_H &&
                            currH < prevH * 0.35 &&
                            entity.horizontalCollision;

            boolean floorHit =
                    prev.y < -IMPACT_MIN_SPEED_V &&
                            entity.isOnGround();

            // Resolve the player who caused this entity to become airborne
            Entity attacker = resolveOwner(impactOwner.get(entity.getUuid()), world);

            if (wallStopped) {
                float damage = IMPACT_WALL_DAMAGE +
                        (float)(prevH - IMPACT_MIN_SPEED_H) * IMPACT_WALL_SCALE;

                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);

                spawnWallImpact(world, entity.getPos().add(0, 0.8, 0));

                world.playSound(null, entity.getBlockPos(),
                        SoundEvents.BLOCK_STONE_HIT,
                        entity.getSoundCategory(), 0.9f, 0.7f);

                removeTagPrefix(entity, TK_AIRBORNE_TAG);
                prevVelocity.remove(entity.getUuid());
                impactOwner.remove(entity.getUuid());
                return;
            }

            if (floorHit) {
                float damage = IMPACT_FLOOR_DAMAGE +
                        (float)(-prev.y - IMPACT_MIN_SPEED_V) * IMPACT_FLOOR_SCALE;

                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);

                spawnFloorImpact(world, entity.getPos());

                world.playSound(null, entity.getBlockPos(),
                        SoundEvents.BLOCK_STONE_FALL,
                        entity.getSoundCategory(), 0.8f, 0.9f);

                removeTagPrefix(entity, TK_AIRBORNE_TAG);
                prevVelocity.remove(entity.getUuid());
                impactOwner.remove(entity.getUuid());
                return;
            }
        }

        prevVelocity.put(entity.getUuid(), curr);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);

        spawnBeam(world, origin, origin.add(look.multiply(YANK_RANGE)));

        boolean hitAnything = false;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(YANK_RANGE),
                en -> en.isAlive() && en != player)) {

            Vec3d toTarget = e.getPos().subtract(origin).normalize();
            if (look.dotProduct(toTarget) < YANK_CONE_DOT) continue;

            Vec3d pull = origin.subtract(e.getPos()).normalize().multiply(YANK_STRENGTH);
            Vec3d launchVel = e.getVelocity().add(pull.x, pull.y + YANK_VERTICAL, pull.z);
            e.setVelocity(launchVel);
            e.velocityModified = true;

            markForImpactTracking(e, launchVel, player.getUuid());
            spawnImpactRing(world, e.getPos(), 8, 0.25);
            hitAnything = true;
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 0.6f, 1.2f);
        if (hitAnything) world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, player.getSoundCategory(), 0.4f, 1.5f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(THROW_SCAN_RANGE));

        spawnBeam(world, origin, end);

        int grabbed = 0;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new Box(origin, end).expand(THROW_SCAN_WIDTH),
                en -> en.isAlive() && en != player)) {

            removeTagPrefix(e, TK_SUSPEND_TAG);
            removeTagPrefix(e, TK_CHOKE_TAG);
            e.getCommandTags().add(TK_SUSPEND_TAG + SUSPEND_TICKS);

            // Record who cast this suspend so choke damage is attributed correctly
            suspendOwner.put(e.getUuid(), player.getUuid());

            spawnImpactRing(world, e.getPos(), 10, 0.3);
            grabbed++;
        }

        if (grabbed > 0) {
            removeTagPrefix(player, TK_READY_THROW_TAG);
            player.getCommandTags().add(TK_READY_THROW_TAG + THROW_READY_TICKS);
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_SHULKER_SHOOT, player.getSoundCategory(), 0.7f, 0.9f);
    }

    private void handleSuspendAndChoke(LivingEntity e, ServerWorld world, long time) {
        boolean wasSuspended = hasTag(e, TK_SUSPEND_TAG); // check BEFORE ticking
        boolean stillSuspended = tickTag(e, TK_SUSPEND_TAG);

        if (stillSuspended) {
            // Normal suspend
            e.setVelocity(e.getVelocity().x * 0.3, SUSPEND_FLOAT_VEL, e.getVelocity().z * 0.3);
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 5, 4, true, false, false));
            spawnSuspendAura(world, e, time);
            return;
        }

        // Tag just expired this tick
        if (wasSuspended && !hasTag(e, TK_CHOKE_TAG)) {
            e.getCommandTags().add(TK_CHOKE_TAG + CHOKE_TICKS);
        }

        // ------ Choke phase ------------
        boolean choking = tickTag(e, TK_CHOKE_TAG);
        if (!choking) {
            // Choke fully expired — no further attribution needed
            suspendOwner.remove(e.getUuid());
            return;
        }

        int chokeRemaining = getTagValue(e, TK_CHOKE_TAG);
        float chokeProgress = 1.0f - ((float) chokeRemaining / CHOKE_TICKS);

        double squeezeVel = CHOKE_SQUEEZE_VEL + chokeProgress * 0.10;
        e.setVelocity(
                e.getVelocity().x * 0.2,
                squeezeVel,
                e.getVelocity().z * 0.2
        );
        e.velocityModified = true;

        int slownessLevel = (int)(chokeProgress * 5);
        e.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, slownessLevel, true, false, false));

        if (e.age % CHOKE_DAMAGE_INTERVAL == 0) {
            float damage = CHOKE_DAMAGE_PER_TICK * (0.5f + chokeProgress);

            // Attribute to whoever applied the original suspend
            Entity attacker = resolveOwner(suspendOwner.get(e.getUuid()), world);
            e.damage(ModDamageTypes.strangle(world, attacker), damage);

            world.playSound(null, e.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, e.getSoundCategory(),
                    0.4f + chokeProgress * 0.3f, 0.9f);
        }

        spawnChokeAura(world, e, time, chokeProgress);
    }

    public void performThrow(ServerPlayerEntity player) {
        if (!hasTag(player, TK_READY_THROW_TAG)) return;
        removeTagPrefix(player, TK_READY_THROW_TAG);

        Vec3d look = player.getRotationVec(1.0f);
        ServerWorld world = player.getServerWorld();

        List<LivingEntity> suspended = world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(THROW_RELEASE_RANGE),
                en -> hasTag(en, TK_SUSPEND_TAG) || hasTag(en, TK_CHOKE_TAG));

        for (LivingEntity e : suspended) {
            // cancel other states
            removeTagPrefix(e, TK_SUSPEND_TAG);
            removeTagPrefix(e, TK_CHOKE_TAG);

            Vec3d throwVel = new Vec3d(
                    look.x * THROW_SPEED_H,
                    THROW_SPEED_V,
                    look.z * THROW_SPEED_H
            );

            e.setVelocity(throwVel);
            e.velocityModified = true;

            markForImpactTracking(e, throwVel, player.getUuid());

            removeTagPrefix(e, TK_AIRBORNE_TAG);
            e.getCommandTags().add(TK_AIRBORNE_TAG + TK_AIRBORNE_TICKS);

            spawnImpactRing(world, e.getPos(), 14, 0.4);
            world.spawnParticles(TK_MAGENTA,
                    e.getX(), e.getBodyY(0.5), e.getZ(),
                    8, 0.3, 0.3, 0.3, 0.06);
        }

        if (!suspended.isEmpty()) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                    player.getSoundCategory(), 0.6f, 1.4f);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        clearDebrisField(world);

        int harvested = tryHarvestBlocks(player, world, DEBRIS_MAX_BLOCKS);

        if (harvested == 0) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_STONE_HIT, player.getSoundCategory(), 0.8f, 0.6f);
            return;
        }

        debrisFieldTicksRemaining = DEBRIS_ORBIT_TICKS;

        // rings
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = 1.5 + ring * 2.5;
            int points = 12 + ring * 6;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                world.spawnParticles(ring % 2 == 0 ? TK_PINK_LARGE : TK_MAGENTA,
                        player.getX() + Math.cos(angle) * ringRadius,
                        player.getBodyY(0.5),
                        player.getZ() + Math.sin(angle) * ringRadius,
                        1, 0, 0.12, 0, 0.03);
            }
        }

        // fx
        for (int i = 0; i < 20; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.spawnParticles(TK_DARK_PINK,
                    player.getX() + Math.cos(a) * r,
                    player.getBodyY(0.3) + world.random.nextDouble() * 2.0,
                    player.getZ() + Math.sin(a) * r,
                    1, 0, 0.15, 0, 0.04);
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 1.2f, 0.4f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_STONE_BREAK, player.getSoundCategory(), 1.5f, 0.6f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 0.6f, 0.5f);
    }

    private int tryHarvestBlocks(ServerPlayerEntity player, ServerWorld world, int maxCount) {
        Vec3d center = player.getPos();
        int radius = (int) Math.ceil(DEBRIS_HARVEST_RADIUS);
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = player.getBlockPos().add(dx, dy, dz);
                    if (pos.isWithinDistance(center, DEBRIS_HARVEST_RADIUS)
                            && isHarvestable(world, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }

        java.util.Collections.shuffle(candidates, new java.util.Random());

        int harvested = 0;
        for (BlockPos pos : candidates) {
            if (harvested >= maxCount) break;

            BlockState state = world.getBlockState(pos);
            world.removeBlock(pos, false);

            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, state);
            falling.setNoGravity(true);
            falling.dropItem = false;
            falling.timeFalling = -32768;

            // Assign to inner or outer ring based on index
            orbitAngles.put(falling.getUuid(), (Math.PI * 2.0 * harvested) / maxCount);
            harvested++;
        }

        return harvested;
    }

    /**
     * Returns true for nautural blocks, should ignore others
     */
    private boolean isHarvestable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.isOpaque()) return false;

        // list of valid blocks, should probably add more
        net.minecraft.block.Block block = state.getBlock();
        return block == Blocks.STONE
                || block == Blocks.COBBLESTONE
                || block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.GRAVEL
                || block == Blocks.SAND
                || block == Blocks.SANDSTONE
                || block == Blocks.DEEPSLATE
                || block == Blocks.COBBLED_DEEPSLATE
                || block == Blocks.ANDESITE
                || block == Blocks.DIORITE
                || block == Blocks.GRANITE
                || block == Blocks.TUFF
                || block == Blocks.NETHERRACK
                || block == Blocks.BLACKSTONE;
    }

    private void clearDebrisField(ServerWorld world) {
        for (UUID uuid : orbitAngles.keySet()) {
            net.minecraft.entity.Entity e = null;
            // Find and discard all tracked falling blocks
            for (net.minecraft.entity.Entity candidate : world.iterateEntities()) {
                if (candidate.getUuid().equals(uuid)) {
                    e = candidate;
                    break;
                }
            }
            if (e != null) e.discard();
        }
        orbitAngles.clear();
        debrisFieldTicksRemaining = 0;
    }

    /**
     * Called every tick while ult is active.
     * Moves each falling block to its position, handles pull/damage,
     * ticks cooldowns, and cleans up dead blocks.
     */
    private void handleDebrisField(ServerPlayerEntity player) {
        if (debrisFieldTicksRemaining <= 0) return;

        // regen blocks if possible
        handleDebrisRegen(player);

        ServerWorld world = player.getServerWorld();
        debrisFieldTicksRemaining--;

        if (debrisFieldTicksRemaining <= 0) {
            clearDebrisField(world);
            return;
        }

        Vec3d playerPos = player.getPos().add(0, 1.0, 0);
        tickTag(player, DEBRIS_THROW_CD_TAG);

        Set<UUID> toRemove = new HashSet<>();
        int total = orbitAngles.size();

        int index = 0;
        for (Map.Entry<UUID, Double> entry : orbitAngles.entrySet()) {
            UUID uuid = entry.getKey();

            Entity ent = world.getEntity(uuid);
            if (!(ent instanceof FallingBlockEntity block) || block.isRemoved()) {
                toRemove.add(uuid);
                index++;
                continue;
            }

            boolean isInner = (index % 2 == 1);
            double orbitRadius = isInner ? DEBRIS_ORBIT_RADIUS_INNER : DEBRIS_ORBIT_RADIUS;
            double orbitSpeed  = isInner ? -DEBRIS_ORBIT_SPEED_INNER : DEBRIS_ORBIT_SPEED;

            int ringIndex = index / 2;
            int ringTotal = Math.max(1, total / 2);
            double baseAngle = (Math.PI * 2.0 * ringIndex) / ringTotal;
            double timeOffset = world.getTime() * orbitSpeed;
            double angle = baseAngle + timeOffset;

            entry.setValue(angle);

            double x = playerPos.x + Math.cos(angle) * orbitRadius;
            double z = playerPos.z + Math.sin(angle) * orbitRadius;
            double y = playerPos.y
                    + Math.sin(angle * 1.5 + world.getTime() * 0.08) * 0.5
                    + (isInner ? 0.3 : 0.0);

            Vec3d target  = new Vec3d(x, y, z);
            Vec3d current = block.getPos();
            Vec3d delta   = target.subtract(current);

            Vec3d motion = delta.multiply(0.35);
            block.setVelocity(motion);
            block.velocityModified = true;

            if (delta.length() > 5.0) {
                block.setPos(target.x, target.y, target.z);
            }

            block.setNoGravity(true);
            block.timeFalling = -32768;

            world.spawnParticles(isInner ? TK_MAGENTA : TK_DARK_PINK,
                    target.x, target.y, target.z, 1, 0.04, 0.04, 0.04, 0.01);

            if (world.getTime() % 2 == 0) {
                world.spawnParticles(TK_PINK,
                        target.x, target.y, target.z, 1, 0.07, 0.07, 0.07, 0.015);
            }

            if (!isInner && world.getTime() % 6 == 0) {
                world.spawnParticles(TK_PINK_LARGE,
                        target.x, target.y + 0.2, target.z, 1, 0.1, 0.1, 0.1, 0.02);
            }

            index++;
        }

        toRemove.forEach(orbitAngles::remove);
        // below should not use blocks at all

        // pull and damage
        double contactRadius = DEBRIS_ORBIT_RADIUS_INNER * 1.2;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(DEBRIS_PULL_RADIUS),
                en -> en.isAlive() && en != player)) {

            Vec3d toPlayer = playerPos.subtract(e.getPos());
            double dist = toPlayer.length();
            if (dist < 0.5 || dist > DEBRIS_PULL_RADIUS) continue;

            // slight pull
            double strength = DEBRIS_PULL_STRENGTH * (1.0 - dist / DEBRIS_PULL_RADIUS);
            Vec3d pull = toPlayer.normalize().multiply(strength);

            e.addVelocity(pull.x, pull.y * 0.3, pull.z);
            e.velocityModified = true;

            // damage if close — attributed to the player running the ult
            if (dist <= contactRadius && world.getTime() % 10 == 0) {
                e.damage(ModDamageTypes.debrisOrbit(world, player), DEBRIS_PULL_DAMAGE);
            }

            if (world.getTime() % 4 == 0) {
                world.spawnParticles(TK_LIGHT_PINK,
                        e.getX(), e.getBodyY(0.5), e.getZ(),
                        1, pull.x * 2, pull.y * 2, pull.z * 2, 0.01);
            }
        }

        // player particles
        long time = world.getTime();
        for (int ring = 0; ring < 3; ring++) {
            double ringR = DEBRIS_ORBIT_RADIUS_INNER + ring * 0.8;
            double ringSpeed = 0.06 + ring * 0.02;
            double ringDir = (ring % 2 == 0) ? 1 : -1;
            int auraPoints = 4 + ring * 2;

            for (int i = 0; i < auraPoints; i++) {
                double a = (time * ringSpeed * ringDir) + (i * Math.PI * 2.0 / auraPoints);
                world.spawnParticles(ring == 0 ? TK_MAGENTA : ring == 1 ? TK_PINK : TK_LIGHT_PINK,
                        playerPos.x + Math.cos(a) * ringR,
                        playerPos.y + Math.sin(a * 0.5) * 0.3,
                        playerPos.z + Math.sin(a) * ringR,
                        1, 0, 0.015, 0, 0);
            }
        }

        for (int ring = 0; ring < 3; ring++) {
            double ringR = DEBRIS_ORBIT_RADIUS_INNER + ring * 0.8;
            double ringSpeed = 0.06 + ring * 0.02;
            double ringDir = (ring % 2 == 0) ? 1 : -1;
            int auraPoints = 4 + ring * 2;

            for (int i = 0; i < auraPoints; i++) {
                double a = (time * ringSpeed * ringDir) + (i * Math.PI * 2.0 / auraPoints);
                world.spawnParticles(ring == 0 ? TK_MAGENTA : ring == 1 ? TK_PINK : TK_LIGHT_PINK,
                        playerPos.x + Math.cos(a) * ringR,
                        playerPos.y + Math.sin(a * 0.5) * 0.3,
                        playerPos.z + Math.sin(a) * ringR,
                        1, 0, 0.015, 0, 0);
            }
        }
    }

    public void throwDebrisProjectile(ServerPlayerEntity player) {
        if (debrisFieldTicksRemaining <= 0) return;
        if (hasTag(player, DEBRIS_THROW_CD_TAG)) return;

        ServerWorld world = player.getServerWorld();

        // not enough blocks
        if (orbitAngles.size() < DEBRIS_THROW_COUNT) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_STONE_HIT, player.getSoundCategory(), 0.6f, 0.5f);
            return;
        }

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d right = new Vec3d(-look.z, 0, look.x).normalize();
        Vec3d up    = look.crossProduct(right).normalize();

        int thrown = 0;
        Iterator<Map.Entry<UUID, Double>> it = orbitAngles.entrySet().iterator();

        while (it.hasNext() && thrown < DEBRIS_THROW_COUNT) {
            Map.Entry<UUID, Double> entry = it.next();
            UUID uuid = entry.getKey();

            FallingBlockEntity block = null;
            for (Entity candidate : world.iterateEntities()) {
                if (candidate.getUuid().equals(uuid)
                        && candidate instanceof FallingBlockEntity fb) {
                    block = fb;
                    break;
                }
            }

            it.remove();

            if (block != null && !block.isRemoved()) {
                block.setNoGravity(false);
                block.timeFalling = 0;
                block.dropItem = false;

                double spreadH = (thrown == 0) ? 0 : (thrown % 2 == 0 ? 1 : -1)
                        * DEBRIS_THROW_SPREAD * ((thrown + 1) / 2);
                double spreadV = (world.random.nextDouble() - 0.5) * DEBRIS_THROW_SPREAD * 0.5;

                Vec3d vel = look.multiply(DEBRIS_THROW_SPEED)
                        .add(right.multiply(spreadH))
                        .add(up.multiply(spreadV));

                block.setVelocity(vel);
                block.velocityModified = true;

                // Store the throwing player so triggerDebrisExplosion can attribute correctly
                scheduledExplosions.put(uuid, player.getUuid());

                spawnImpactRing(world, block.getPos(), 8, 0.25);
                world.spawnParticles(TK_MAGENTA,
                        block.getX(), block.getY(), block.getZ(),
                        4, 0.2, 0.2, 0.2, 0.06);
            }

            thrown++;
        }

        if (thrown > 0) {
            removeTagPrefix(player, DEBRIS_THROW_CD_TAG);
            player.getCommandTags().add(DEBRIS_THROW_CD_TAG + DEBRIS_THROW_COOLDOWN);

            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 0.8f, 1.1f);
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_STONE_BREAK, player.getSoundCategory(), 1.2f, 1.2f);

            for (int i = 0; i < 12; i++) {
                double spread = (world.random.nextDouble() - 0.5) * 0.4;
                world.spawnParticles(TK_PINK_LARGE,
                        player.getEyePos().x, player.getEyePos().y, player.getEyePos().z,
                        1,
                        look.x * 0.6 + spread,
                        look.y * 0.6 + (world.random.nextDouble() - 0.5) * 0.2,
                        look.z * 0.6 + spread,
                        0.08);
            }
        }
    }

    private void tickThrownBlocks(ServerPlayerEntity player) {
        if (scheduledExplosions.isEmpty()) return;

        ServerWorld world = player.getServerWorld();
        Set<UUID> toExplode = new HashSet<>();

        for (UUID blockUuid : scheduledExplosions.keySet()) {
            FallingBlockEntity block = null;
            for (net.minecraft.entity.Entity candidate : world.iterateEntities()) {
                if (candidate.getUuid().equals(blockUuid)
                        && candidate instanceof FallingBlockEntity fb) {
                    block = fb;
                    break;
                }
            }

            if (block == null || block.isRemoved()) {
                Vec3d pos = lastKnownBlockPos.get(blockUuid);
                if (pos != null) {
                    triggerDebrisExplosion(player, world, pos);
                }
                toExplode.add(blockUuid);
                continue;
            }
        }

        for (UUID blockUuid : toExplode) {
            scheduledExplosions.remove(blockUuid);
        }

        // store last known position
        for (Map.Entry<UUID, UUID> entry : scheduledExplosions.entrySet()) {
            UUID blockUuid = entry.getKey();

            for (net.minecraft.entity.Entity candidate : world.iterateEntities()) {
                if (!candidate.getUuid().equals(blockUuid)
                        || !(candidate instanceof FallingBlockEntity block)) continue;

                lastKnownBlockPos.put(blockUuid, block.getPos());

                // Check for block collision by checking if velocity zeroed out horizontally
                boolean hitSomething = block.horizontalCollision || block.isOnGround();

                if (hitSomething) {
                    Vec3d explodePos = block.getPos();
                    block.discard();
                    triggerDebrisExplosion(player, world, explodePos);
                    toExplode.add(blockUuid);
                }
                break;
            }
        }

        toExplode.forEach(uuid -> {
            scheduledExplosions.remove(uuid);
            lastKnownBlockPos.remove(uuid);
        });
    }

    private void triggerDebrisExplosion(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        // damage
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(pos, pos).expand(DEBRIS_THROW_EXPLOSION_RADIUS),
                en -> en.isAlive() && en != player)) {

            double dist = e.getPos().distanceTo(pos);
            if (dist > DEBRIS_THROW_EXPLOSION_RADIUS) continue;

            // damage falloff
            float damage = DEBRIS_THROW_DAMAGE * (float)(1.0 - dist / DEBRIS_THROW_EXPLOSION_RADIUS);
            e.damage(ModDamageTypes.blockThrow(world, player), damage);

            // knockback
            Vec3d knockback = e.getPos().subtract(pos).normalize().multiply(0.8);
            e.addVelocity(knockback.x, 0.4, knockback.z);
            e.velocityModified = true;

            markForImpactTracking(e, e.getVelocity(), player.getUuid());
        }

        // fx
        spawnSlamImpact(world, pos);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.5, pos.z, 3, 0.5, 0.3, 0.5, 0.1);
        for (int i = 0; i < 15; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 0.8;
            world.spawnParticles(TK_DARK_PINK,
                    pos.x + Math.cos(a) * r, pos.y + 0.3, pos.z + Math.sin(a) * r,
                    1, Math.cos(a) * 0.3, 0.2, Math.sin(a) * 0.3, 0.05);
        }

        world.playSound(null,
                new BlockPos((int)pos.x, (int)pos.y, (int)pos.z),
                SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS,
                1.0f, 0.8f);
    }

    private void handleDebrisRegen(ServerPlayerEntity player) {
        if (debrisFieldTicksRemaining <= 0) return;

        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        long lastSwing = lastSwingTime.getOrDefault(player.getUuid(), 0L);
        long idleTime = time - lastSwing;

        // hit too recently
        if (idleTime < DEBRIS_REGEN_DELAY_TICKS) return;

        // Only regen at intervals
        if (time % DEBRIS_REGEN_INTERVAL != 0) return;

        // if full
        if (orbitAngles.size() >= DEBRIS_MAX_BLOCKS) return;

        int toRegen = Math.min(DEBRIS_REGEN_AMOUNT,
                DEBRIS_MAX_BLOCKS - orbitAngles.size());

        for (int i = 0; i < toRegen; i++) {
            spawnOrbitBlock(player, world);
        }
    }

    private void spawnOrbitBlock(ServerPlayerEntity player, ServerWorld world) {
        BlockPos pos = player.getBlockPos().down(); // simple source (can randomise later)
        BlockState state = Blocks.STONE.getDefaultState();

        FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, pos, state);
        block.setNoGravity(true);
        block.dropItem = false;
        block.timeFalling = -32768;

        // Add with random angle
        orbitAngles.put(block.getUuid(), world.random.nextDouble() * Math.PI * 2);

        // fx
        world.spawnParticles(TK_MAGENTA,
                block.getX(), block.getY(), block.getZ(),
                6, 0.2, 0.2, 0.2, 0.05);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_STONE_PLACE,
                player.getSoundCategory(), 0.4f, 1.2f);
    }

    /* ============================================================
       YAP
       ============================================================ */

    @Override public String getName()          { return "Telekinesis"; }
    @Override public String getPassiveName()   { return "Forceful Strikes"; }
    @Override public String getPrimaryName()   { return "Yank"; }
    @Override public String getSecondaryName() { return "Suspend"; }
    @Override public String getUltimateName()  { return "Debris Orbit"; }

    @Override public long getPrimaryCooldownMs()   { return 3000; }
    @Override public long getSecondaryCooldownMs() { return 7000; }
    @Override public long getUltimateCooldownMs()  { return 15000; }

    @Override
    public String getOverviewDescription() {
        return "Telekinesis is a control power focused on moving entities around and keeping them away from you." +
                " Where most abilities can vary on power dependent on the current environment, meaning this power may be weaker in some areas, such as wide open spaces, but stronger" +
                " in others, like enclosed spaces.";
    }

    @Override
    public String getPassiveDescription() {
        return "Melee hits have increases knockback, this stacks with knockback enchantments.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Pull all entities in front of you towards you, this pull will also bring entities upwards.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Suspend a group of entities in front of you, disabling their movement and floating them into the air. You then have two choices for actions to take:" +
                " /nSwing your hand in any direction and launch the suspended entities in the direction you were looking, entities will take increased fall damage or bonus damage when colliding" +
                " with a wall." +
                " /nDo not swing your hand while they are suspended and will begin to choke them, losing the ability to throw them and dealing damage over time.";
    }

    @Override
    public String getUltimateDescription() {
        return "Surround yourself in telekinetic energy, pulling in nearby entities and damaging them if they are very close." +
                "/nNearby natural blocks are pulled from the world and orbit you, swinging will launch some of these blocks in the direction you are looking, dealing AOE damage and" +
                " knockback." +
                "/nYou must have a certain number of blocks to launch them and you replenish blocks slowly after not swinging for a few seconds." +
                "/nThere are no changes to your movement during this, but your vision may be obscured. ";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    // Resolve the player entity from a stored UUID — returns null if they've logged off
    private static Entity resolveOwner(UUID ownerUuid, ServerWorld world) {
        if (ownerUuid == null) return null;
        return world.getServer().getPlayerManager().getPlayer(ownerUuid);
    }

    public static boolean hasTag(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) return true;
        }
        return false;
    }

    private static int getTagValue(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                try { return Integer.parseInt(tag.substring(prefix.length())); }
                catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static boolean tickTag(LivingEntity entity, String prefix) {
        Iterator<String> it = entity.getCommandTags().iterator();
        String newTag = null;
        boolean active = false;

        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                try {
                    int ticks = Integer.parseInt(tag.substring(prefix.length())) - 1;
                    it.remove();
                    if (ticks > 0) { newTag = prefix + ticks; active = true; }
                } catch (NumberFormatException ignored) { it.remove(); }
                break;
            }
        }

        if (newTag != null) entity.getCommandTags().add(newTag);
        return active;
    }

    public static void removeTagPrefix(Entity e, String prefix) {
        e.getCommandTags().removeIf(s -> s.startsWith(prefix));
    }
}