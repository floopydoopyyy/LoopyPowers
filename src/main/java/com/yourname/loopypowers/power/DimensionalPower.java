package com.yourname.loopypowers.power;

import com.yourname.loopypowers.entity.DisplaceEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.network.RenderPackets;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;

import java.util.Iterator;

public class DimensionalPower implements Power {

    /* ============================================================
       TAGS / CONSTANTS
       ============================================================ */

    private static final String PHASE_TAG        = "int_phase_";        // passive + primary
    private static final String DISPLACED_TAG    = "int_displaced_";    // projectile ability

    private static final int PASSIVE_PHASE_TICKS = 40;
    private static final int PASSIVE_COOLDOWN    = 50;

    // PASSIVE
    private static final double BASE_PHASE_CHANCE = 0.001; // chance per tick
    private static final double DAMAGE_CHANCE_GAIN = 0.02; // amount chance is increased per hit
    private static final double MAX_PHASE_CHANCE = 0.30;   // cap
    private static final int FLICKER_CYCLE = 8;     // total loop length
    private static final int FLICKER_ON_TIME = 3;   // how long invisible per cycle

    private static final String PHASE_CHANCE_TAG = "int_phase_chance_";
    private static final String IMMUNE_TAG = "int_immune_";
    private static final String PASSIVE_CD_TAG = "int_passive_cd_";

    // PRIMARY
    private static final int PHASE_SHIFT_DURATION = 45;

    private static final float ENTRY_SOUND_VOL = 0.7f;
    private static final float ENTRY_SOUND_PITCH = 1.3f;
    private static final float EXIT_SOUND_VOL = 0.9f;
    private static final float EXIT_SOUND_PITCH = 0.8f;

    private static final int TRAIL_INTERVAL = 3;
    private static final int EXIT_BURST_COUNT_MULT = 2;
    private static final double EXIT_BURST_SPREAD = 1.2;
    private static final double EXIT_BURST_SPEED = 0.08;

    private static final double EXIT_DAMAGE_RADIUS = 3.5;
    private static final float EXIT_DAMAGE = 5.0f;
    private static final double EXIT_PULL_STRENGTH = 0.45;
    private static final double EXIT_KNOCKBACK_STRENGTH = 0.8;
    private static final double EXIT_VERTICAL_BOOST = 0.15;

    private static final String PHASE_SHIFT_TAG = "int_phase_shift_";

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {}

    @Override
    public void onRemove(ServerPlayerEntity player) {}

    @Override
    public void onTick(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        handlePassive(player);
        handlePhaseShift(player);
        handleFracture(player); // ultimate tick

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(30),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            handleDisplace(e, world);
        }
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {

    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    public void onDamaged(ServerPlayerEntity player) {
        increasePhaseChance(player);
    }

    private void handlePassive(ServerPlayerEntity player) {
        if (hasTag(player, PHASE_SHIFT_TAG)) return;

        ServerWorld world = player.getServerWorld();

        // handle flicker first if phasing
        if (getTagTicks(player, PHASE_TAG) > 0) {
            handleFlicker(player, world);
            return;
        }

        // remove immunity
        if (getTagTicks(player, PHASE_TAG) <= 0) {
            removeTagPrefix(player, IMMUNE_TAG);
        }

        // Then tick cooldown
        if (hasTag(player, PASSIVE_CD_TAG)) {
            tickTag(player, PASSIVE_CD_TAG);
            return;
        }

        // Get current chance
        double chance = getPhaseChance(player);

        // Roll
        if (world.random.nextDouble() < chance) {
            startPassivePhase(player);

            // Reset chance after proc
            setPhaseChance(player, BASE_PHASE_CHANCE);

            // Start cooldown
            player.getCommandTags().add(PASSIVE_CD_TAG + PASSIVE_COOLDOWN);
        }
    }

    private void startPassivePhase(ServerPlayerEntity player) {
        removeTagPrefix(player, PHASE_TAG);
        player.getCommandTags().add(PHASE_TAG + PASSIVE_PHASE_TICKS);

        player.getServerWorld().playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                player.getSoundCategory(), 0.5f, 1.2f);
    }

    private void handleFlicker(ServerPlayerEntity player, ServerWorld world) {

        long time = world.getTime();

        int cyclePos = (int)(time % FLICKER_CYCLE);
        boolean flickerActive = cyclePos < FLICKER_ON_TIME;

        // particles
        if (cyclePos == 0) {

            DustParticleEffect darkBlue = new DustParticleEffect(
                    new Vector3f(0.05f, 0.1f, 0.4f),
                    1.4f
            );

            DustParticleEffect midBlue = new DustParticleEffect(
                    new Vector3f(0.2f, 0.4f, 1.0f),
                    1.2f
            );

            DustParticleEffect brightBlue = new DustParticleEffect(
                    new Vector3f(0.6f, 0.8f, 1.0f),
                    0.9f
            );

            double x = player.getX();
            double y = player.getBodyY(0.5);
            double z = player.getZ();

            world.spawnParticles(brightBlue, x, y, z, 12, 0.5, 0.7, 0.5, 0.03);
            world.spawnParticles(midBlue, x, y, z, 8, 0.35, 0.55, 0.35, 0.02);

            if (world.random.nextFloat() < 0.4f) {
                world.spawnParticles(darkBlue, x, y, z, 3, 0.3, 0.4, 0.3, 0.015);
            }

            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    player.getSoundCategory(), 0.25f, 1.8f);
        }

        // flicker
        if (flickerActive) {

            // yeah
            RenderPackets.hidePlayerFromOthers(player, 1);

            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INVISIBILITY,
                    2, //
                    0,
                    true,
                    false
            ));
        }

        // damage immunity
        if (!hasTag(player, IMMUNE_TAG)) {
            player.getCommandTags().add(IMMUNE_TAG + getTagTicks(player, PHASE_TAG));
        }

        tickTag(player, PHASE_TAG);
    }

    private double getPhaseChance(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(PHASE_CHANCE_TAG)) {
                try {
                    return Double.parseDouble(tag.substring(PHASE_CHANCE_TAG.length()));
                } catch (Exception ignored) {}
            }
        }
        return BASE_PHASE_CHANCE;
    }

    private void setPhaseChance(ServerPlayerEntity player, double value) {
        removeTagPrefix(player, PHASE_CHANCE_TAG);
        player.getCommandTags().add(PHASE_CHANCE_TAG + value);
    }

    private void increasePhaseChance(ServerPlayerEntity player) {
        double current = getPhaseChance(player);
        current = Math.min(MAX_PHASE_CHANCE, current + DAMAGE_CHANCE_GAIN);
        setPhaseChance(player, current);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // clear passive
        removeTagPrefix(player, PHASE_TAG);
        removeTagPrefix(player, IMMUNE_TAG);

        removeTagPrefix(player, PHASE_SHIFT_TAG);
        player.getCommandTags().add(PHASE_SHIFT_TAG + PHASE_SHIFT_DURATION);

        // switch to spectator
        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);

        spawnPhaseParticles(world, player);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(), ENTRY_SOUND_VOL, ENTRY_SOUND_PITCH);
    }

    private void handlePhaseShift(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        boolean active = tickTag(player, PHASE_SHIFT_TAG);

        if (!active) {
            // if ended, exit
            if (player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
                exitPhaseShift(player, world);
            }
            return;
        }

        // particles
        if (world.getTime() % TRAIL_INTERVAL == 0) {

            DustParticleEffect midBlue = new DustParticleEffect(
                    new Vector3f(0.2f, 0.4f, 1.0f),
                    1.0f
            );

            world.spawnParticles(midBlue,
                    player.getX(),
                    player.getBodyY(0.5),
                    player.getZ(),
                    2,
                    0.15, 0.2, 0.15,
                    0.01
            );
        }
    }

    private void exitPhaseShift(ServerPlayerEntity player, ServerWorld world) {

        // back to survival
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), EXIT_SOUND_VOL, EXIT_SOUND_PITCH);

        // damage nearby entities
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(EXIT_DAMAGE_RADIUS),
                en -> en.isAlive() && en != player)) {

            // damage
            e.damage(world.getDamageSources().magic(), EXIT_DAMAGE);

            // direction vectors
            Vec3d fromPlayer = e.getPos().subtract(player.getPos());
            double distance = fromPlayer.length();

            if (distance < 0.001) continue;

            Vec3d dir = fromPlayer.normalize();

            // slight pull
            Vec3d pull = dir.multiply(-EXIT_PULL_STRENGTH);

            Vec3d knockback = dir.multiply(EXIT_KNOCKBACK_STRENGTH);

            // knockback push
            Vec3d finalVelocity = pull.add(knockback).add(0, EXIT_VERTICAL_BOOST, 0);

            e.addVelocity(finalVelocity.x, finalVelocity.y, finalVelocity.z);
            e.velocityModified = true;
        }

        // exit particles
        DustParticleEffect darkBlue = new DustParticleEffect(
                new Vector3f(0.05f, 0.1f, 0.4f),
                1.4f
        );

        DustParticleEffect midBlue = new DustParticleEffect(
                new Vector3f(0.2f, 0.4f, 1.0f),
                1.2f
        );

        DustParticleEffect brightBlue = new DustParticleEffect(
                new Vector3f(0.6f, 0.8f, 1.0f),
                1.1f
        );

        double x = player.getX();
        double y = player.getBodyY(0.5);
        double z = player.getZ();

        world.spawnParticles(brightBlue,
                x, y, z,
                25 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD, EXIT_BURST_SPREAD, EXIT_BURST_SPREAD,
                EXIT_BURST_SPEED
        );

        world.spawnParticles(midBlue,
                x, y, z,
                18 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8,
                EXIT_BURST_SPEED * 0.8
        );

        world.spawnParticles(darkBlue,
                x, y, z,
                8 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6,
                EXIT_BURST_SPEED * 0.6
        );
    }

    private void spawnPhaseParticles(ServerWorld world, ServerPlayerEntity player) {

        DustParticleEffect darkBlue = new DustParticleEffect(
                new Vector3f(0.05f, 0.1f, 0.4f),
                1.4f
        );

        DustParticleEffect midBlue = new DustParticleEffect(
                new Vector3f(0.2f, 0.4f, 1.0f),
                1.2f
        );

        DustParticleEffect brightBlue = new DustParticleEffect(
                new Vector3f(0.6f, 0.8f, 1.0f),
                1.0f
        );

        double x = player.getX();
        double y = player.getBodyY(0.5);
        double z = player.getZ();

        world.spawnParticles(brightBlue, x, y, z, 15, 0.6, 0.8, 0.6, 0.04);
        world.spawnParticles(midBlue, x, y, z, 10, 0.4, 0.6, 0.4, 0.03);

        if (world.random.nextFloat() < 0.5f) {
            world.spawnParticles(darkBlue, x, y, z, 4, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // direction
        Vec3d look = player.getRotationVec(1.0f);

        // spawn projectile
        DisplaceEntity bolt = new DisplaceEntity(
                ModEntities.DISPLACE_ENTITY, world);

        bolt.setOwner(player);

        bolt.setPos(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        // slow moving
        bolt.setVelocity(look.multiply(0.6));

        world.spawnEntity(bolt);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(), 0.6f, 1.4f);
    }

    private void handleDisplace(LivingEntity entity, ServerWorld world) {
        boolean active = tickTag(entity, DISPLACED_TAG);

        // restore ai if unaffected
        if (!active) {
            if (entity instanceof MobEntity mob) {
                mob.setAiDisabled(false);
            }
            return;
        }

        if (!active) return;

        // set velocity to 0
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
        entity.fallDistance = 0; // stops fall damage accumulating when froze

        // Lock position using packets
        if (entity instanceof ServerPlayerEntity displacedPlayer) {
            displacedPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            displacedPlayer.getYaw(),
                            displacedPlayer.getPitch(),
                            java.util.Set.of(
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT
                            ),
                            0
                    )
            );

            // stop mining
            displacedPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 255, true, false, false));

            // stop actions
            if (!displacedPlayer.getMainHandStack().isEmpty()) { // set cooldowns on held item
                displacedPlayer.getItemCooldownManager().set(displacedPlayer.getMainHandStack().getItem(), 5);
            }
            displacedPlayer.stopUsingItem(); // force usables to not be used
        }

        // Invisibility
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 5, 0, true, false, false));
        // stop damage
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 5, 255, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));

        // disable mob ai
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }

        // fx
        DustParticleEffect darkBlue  = new DustParticleEffect(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleEffect midBlue   = new DustParticleEffect(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleEffect brightBlue = new DustParticleEffect(new Vector3f(0.6f, 0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getBodyY(0.5);
        double z = entity.getZ();

        world.spawnParticles(brightBlue,  x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        world.spawnParticles(midBlue,     x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (world.random.nextFloat() < 0.4f) {
            world.spawnParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    // ── Ultimate — Reality Fracture ─────────────────────────────
    private static final int    ULT_DURATION             = 300;  // ticks cracks last
    private static final double ULT_RIFT_RADIUS          = 26.0; // how far cracks spread from rift
    private static final int    ULT_CRACK_COUNT          = 15;   // number of crack lines
    private static final int    ULT_CRACK_SEGMENTS       = 8;    // jagged segments per crack line
    private static final double ULT_CRACK_SEGMENT_LENGTH = 2.3;  // length of each segment
    private static final double ULT_CRACK_JAGGED_ANGLE   = 0.6;  // max angle deviation (radians)
    private static final double ULT_CRACK_WIDTH          = 0.9;  // proximity to crack that triggers damage
    private static final float  ULT_CRACK_DAMAGE         = 2.5f; // damage on contact
    private static final int    ULT_CRACK_SLOW_TICKS     = 35;   // ticks of slowness on contact
    private static final int    ULT_CRACK_SLOW_AMP       = 2;    // slowness amplifier
    private static final int    ULT_CRACK_DMG_COOLDOWN   = 15;   // ticks between damage hits per entity
    private static final double ULT_WALL_PARTICLE_HEIGHT = 3.0;  // how tall the particle wall rises
    private static final int    ULT_WALL_PARTICLE_DENSITY = 1;   // particles per crack point per interval
    private static final int    ULT_PARTICLE_INTERVAL    = 8;    // ticks between aura particle spawns
    private static final int    ULT_RING_COUNT        = 2; // amount of rings
    private static final double ULT_RING_SPACING      = 9.0; // space between them
    private static final int    ULT_RING_POINTS       = 20; // points in each circle
    private static final double ULT_RING_WIDTH        = 1.0; // yeah
    private static final double MAX_STEP_UP = 1.25;   // how much it can climb
    private static final double MAX_STEP_DOWN = 2.5;  // how much it can drop
    private static final int SEARCH_DOWN = 6;         // how far down to search
    private static final int SEARCH_UP = 2;           // how upward it can correct
    private static final String ULT_CRACK_DMG_CD_TAG     = "int_crack_cd_";

    // Stores crack line points — built on cast, read every tick
    // Each crack is a list of Vec3d points; stored as instance field since this is per-player
    // this is stupid
    private final java.util.List<java.util.List<Vec3d>> activeCracks = new java.util.ArrayList<>();
    private int crackTicksRemaining = 0;
    private Vec3d riftOrigin = null;
    private final java.util.List<java.util.List<Vec3d>> activeRings = new java.util.ArrayList<>();

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Reset everything
        activeCracks.clear();
        activeRings.clear();
        crackTicksRemaining = 0;
        riftOrigin = null;

        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d origin = player.getPos().add(look.x * 3.0, 0, look.z * 3.0);

        riftOrigin = origin;
        crackTicksRemaining = ULT_DURATION;

        java.util.Random rng = new java.util.Random();
        double angleStep = (Math.PI * 2.0) / ULT_CRACK_COUNT;

        // ── Build radial cracks ─────────────────────────────
        for (int i = 0; i < ULT_CRACK_COUNT; i++) {
            double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
            activeCracks.add(buildCrackLine(origin, baseAngle, rng, world));
        }

        // visuals
        spawnRiftOpeningParticles(world, origin);

        for (int i = 1; i <= ULT_RING_COUNT; i++) {
            double radius = i * ULT_RING_SPACING;
            activeRings.add(buildRing(origin, radius, world)); // pass world
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), 1.2f, 0.4f);
    }

    private java.util.List<Vec3d> buildRing(Vec3d center, double radius, ServerWorld world) {
        java.util.List<Vec3d> points = new java.util.ArrayList<>();

        int smoothPoints = ULT_RING_POINTS * 3; // hopefully its smoother

        for (int i = 0; i < smoothPoints; i++) {
            double angle = (Math.PI * 2.0 * i) / smoothPoints;

            Vec3d p = new Vec3d(
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius
            );

            points.add(snapToGround(p, world));
        }

        return points;
    }

    private java.util.List<Vec3d> buildCrackLine(Vec3d origin, double baseAngle,
                                                 java.util.Random rng, ServerWorld world) {

        java.util.List<Vec3d> points = new java.util.ArrayList<>();

        Vec3d current = origin;
        points.add(snapToGround(current, world));

        double currentAngle = baseAngle;

        for (int seg = 0; seg < ULT_CRACK_SEGMENTS; seg++) {

            currentAngle += (rng.nextDouble() - 0.5) * ULT_CRACK_JAGGED_ANGLE;

            double segLen = ULT_CRACK_SEGMENT_LENGTH * (0.7 + rng.nextDouble() * 0.6);

            Vec3d nextFlat = current.add(
                    Math.cos(currentAngle) * segLen,
                    0,
                    Math.sin(currentAngle) * segLen
            );

            // interpolate between points
            int steps = 3 + rng.nextInt(3); // 3 sub-steps

            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;

                Vec3d interp = new Vec3d(
                        current.x + (nextFlat.x - current.x) * t,
                        current.y,
                        current.z + (nextFlat.z - current.z) * t
                );

                points.add(snapToGround(interp, world));
            }

            current = nextFlat;

            if (current.distanceTo(origin) > ULT_RIFT_RADIUS) break;
        }

        return points;
    }

    private void handleFracture(ServerPlayerEntity player) {
        if (crackTicksRemaining <= 0) return;

        crackTicksRemaining--;
        if (crackTicksRemaining <= 0) {
            activeCracks.clear();
            activeRings.clear();
            riftOrigin = null;
            return;
        }

        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        // WALLS
        if (time % ULT_PARTICLE_INTERVAL == 0) {

            for (java.util.List<Vec3d> crack : activeCracks) {
                spawnCrackWallParticles(world, crack, time);
            }

            for (java.util.List<Vec3d> ring : activeRings) {
                spawnCrackWallParticles(world, ring, time);
            }

            if (riftOrigin != null) {
                spawnRiftAuraParticles(world, riftOrigin, time);
            }
        }

        // -- COLLISION --
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(ULT_RIFT_RADIUS + 2),
                en -> en.isAlive() && en != player)) {

            if (hasTag(e, ULT_CRACK_DMG_CD_TAG)) {
                tickTag(e, ULT_CRACK_DMG_CD_TAG);
                continue;
            }

            if (isNearAnyCrack(e.getPos()) || isNearAnyRing(e.getPos())) {

                e.damage(player.getDamageSources().magic(), ULT_CRACK_DAMAGE);

                e.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS,
                        ULT_CRACK_SLOW_TICKS,
                        ULT_CRACK_SLOW_AMP,
                        false, true, true
                ));

                removeTagPrefix(e, ULT_CRACK_DMG_CD_TAG);
                e.getCommandTags().add(ULT_CRACK_DMG_CD_TAG + ULT_CRACK_DMG_COOLDOWN);

                world.spawnParticles(
                        CRACK_BRIGHT,
                        e.getX(), e.getBodyY(0.5), e.getZ(),
                        6, 0.3, 0.4, 0.3, 0.04
                );
            }
        }
    }

    private boolean isNearAnyCrack(Vec3d pos) {
        for (java.util.List<Vec3d> crack : activeCracks) {
            for (int i = 0; i < crack.size() - 1; i++) {
                if (distanceToSegment(pos, crack.get(i), crack.get(i + 1)) < ULT_CRACK_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        // Flatten to XZ
        Vec3d pFlat = new Vec3d(p.x, 0, p.z);
        Vec3d aFlat = new Vec3d(a.x, 0, a.z);
        Vec3d bFlat = new Vec3d(b.x, 0, b.z);

        Vec3d ab = bFlat.subtract(aFlat);
        double len2 = ab.lengthSquared();
        if (len2 < 0.0001) return pFlat.distanceTo(aFlat);

        double t = net.minecraft.util.math.MathHelper.clamp(
                pFlat.subtract(aFlat).dotProduct(ab) / len2, 0, 1);
        Vec3d closest = aFlat.add(ab.multiply(t));
        return pFlat.distanceTo(closest);
    }

    // particles
    private static final DustParticleEffect CRACK_DARK =
            new DustParticleEffect(new Vector3f(0.05f, 0.15f, 0.55f), 1.8f);  // deep blue
    private static final DustParticleEffect CRACK_MID =
            new DustParticleEffect(new Vector3f(0.25f, 0.55f, 1.0f), 1.4f);   // sky blue
    private static final DustParticleEffect CRACK_BRIGHT =
            new DustParticleEffect(new Vector3f(0.75f, 0.92f, 1.0f), 1.1f);   // icy white-blue
    private static final DustParticleEffect RIFT_PURPLE =
            new DustParticleEffect(new Vector3f(0.3f, 0.6f, 1.0f), 2.0f);     // bright blue replacing purple
    private static final DustParticleEffect RIFT_WHITE =
            new DustParticleEffect(new Vector3f(0.9f, 0.97f, 1.0f), 1.2f);    // pure white-blue

    private void spawnCrackWallParticles(ServerWorld world, java.util.List<Vec3d> crack, long time) {
        for (int i = 0; i < crack.size() - 1; i++) {
            Vec3d a = crack.get(i);
            Vec3d b = crack.get(i + 1);
            int steps = Math.max(1, (int)(a.distanceTo(b) / 0.55));

            for (int s = 0; s <= steps; s++) {
                if (world.random.nextInt(4) != 0) continue;

                double t = (double) s / steps;
                double baseX = a.x + (b.x - a.x) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseZ = a.z + (b.z - a.z) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseY = a.y + (b.y - a.y) * t; // interpolate Y along terrain

                double wallHeight = ULT_WALL_PARTICLE_HEIGHT * (0.5 + world.random.nextDouble() * 0.5);

                // bottom crack
                world.spawnParticles(CRACK_DARK,
                        baseX, baseY + 0.05, baseZ,
                        1, 0.03, 0.01, 0.03, 0.003);
                world.spawnParticles(CRACK_MID,
                        baseX, baseY + 0.12, baseZ,
                        1, 0.04, 0.02, 0.04, 0.005);

                // make them rise slightly
                for (int h = 1; h < ULT_WALL_PARTICLE_DENSITY + 2; h++) {
                    double y = baseY + (wallHeight * h / (ULT_WALL_PARTICLE_DENSITY + 2));

                    float heightFraction = (float) h / (ULT_WALL_PARTICLE_DENSITY + 2);
                    DustParticleEffect color = heightFraction < 0.35f ? CRACK_DARK
                            : heightFraction < 0.65f ? CRACK_MID
                            : CRACK_BRIGHT;

                    double driftX = (world.random.nextDouble() - 0.5) * 0.015 * h;
                    double driftZ = (world.random.nextDouble() - 0.5) * 0.015 * h;

                    world.spawnParticles(color,
                            baseX + driftX, y, baseZ + driftZ,
                            1, 0.01, 0.02 + heightFraction * 0.03, 0.01,
                            0.003 + heightFraction * 0.006);
                }

                if (world.random.nextFloat() < 0.06f) {
                    world.spawnParticles(RIFT_WHITE,
                            baseX, baseY + wallHeight * 0.4, baseZ,
                            1, 0.06, 0.08, 0.06, 0.02);
                }

                if (world.random.nextFloat() < 0.04f) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            baseX, baseY + 0.1, baseZ,
                            1, (world.random.nextDouble() - 0.5) * 0.04,
                            0.06 + world.random.nextDouble() * 0.06,
                            (world.random.nextDouble() - 0.5) * 0.04,
                            0.01);
                }
            }
        }
    }

    private void spawnRiftOpeningParticles(ServerWorld world, Vec3d pos) {
        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 1.8;
            int ringPoints = 16 + ring * 8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                world.spawnParticles(ring == 0 ? RIFT_WHITE : ring == 1 ? CRACK_BRIGHT : CRACK_MID,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1,
                        -Math.cos(angle) * (0.2 + ring * 0.05),
                        0.04,
                        -Math.sin(angle) * (0.2 + ring * 0.05),
                        0.015);
            }
        }

        world.spawnParticles(RIFT_WHITE,  pos.x, pos.y + 0.2, pos.z, 25, 0.4, 0.6, 0.4, 0.09);
        world.spawnParticles(CRACK_BRIGHT, pos.x, pos.y + 0.2, pos.z, 18, 0.5, 0.7, 0.5, 0.07);
        world.spawnParticles(CRACK_MID,   pos.x, pos.y + 0.1, pos.z, 12, 0.6, 0.4, 0.6, 0.05);
        world.spawnParticles(CRACK_DARK,  pos.x, pos.y + 0.1, pos.z, 8,  0.7, 0.3, 0.7, 0.04);

        // beam upward
        for (int h = 0; h < 14; h++) {
            double heightFraction = (double) h / 14;
            DustParticleEffect col = heightFraction < 0.3 ? CRACK_DARK
                    : heightFraction < 0.6 ? CRACK_MID
                    : heightFraction < 0.85 ? CRACK_BRIGHT
                    : RIFT_WHITE;

            world.spawnParticles(col,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    pos.y + h * 0.45,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        for (int i = 0; i < 16; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * r,
                    pos.y + 0.2,
                    pos.z + Math.sin(a) * r,
                    1,
                    Math.cos(a) * 0.04, 0.12 + world.random.nextDouble() * 0.1,
                    Math.sin(a) * 0.04, 0.02);
        }

        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 3, 0.15, 0.1, 0.15, 0);
    }

    private void spawnRiftAuraParticles(ServerWorld world, Vec3d pos, long time) {
        // 2 rings around centre
        for (int ring = 0; ring < 2; ring++) {
            double speed  = ring == 0 ? 0.09 : -0.06;
            double radius = ring == 0 ? 0.7 : 1.1;
            double angle  = time * speed;
            int points    = ring == 0 ? 3 : 5;

            for (int i = 0; i < points; i++) {
                double a = angle + (i * Math.PI * 2.0 / points);
                double r = radius + Math.sin(time * 0.07 + i) * 0.15;

                world.spawnParticles(ring == 0 ? RIFT_WHITE : CRACK_BRIGHT,
                        pos.x + Math.cos(a) * r,
                        pos.y + 0.2 + Math.sin(time * 0.05 + i) * 0.12,
                        pos.z + Math.sin(a) * r,
                        1, 0, 0.02, 0, 0.008);
            }
        }

        // occasional star
        if (time % 5 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.2,
                    pos.y + 0.15,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                    1, 0, 0.07 + world.random.nextDouble() * 0.05, 0, 0.01);
        }

        if (time % 8 == 0) {
            world.spawnParticles(RIFT_WHITE, pos.x, pos.y + 0.4, pos.z,
                    1, 0.12, 0.12, 0.12, 0.025);
        }
    }

    private boolean isNearAnyRing(Vec3d pos) {
        for (java.util.List<Vec3d> ring : activeRings) {
            for (int i = 0; i < ring.size(); i++) {
                Vec3d a = ring.get(i);
                Vec3d b = ring.get((i + 1) % ring.size());

                if (distanceToSegment(pos, a, b) < ULT_RING_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vec3d snapToGround(Vec3d pos, ServerWorld world) {
        if (world == null) return pos;

        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int baseY = (int) Math.floor(pos.y);

        int bestY = baseY;
        boolean found = false;

        for (int dy = 0; dy <= SEARCH_DOWN; dy++) {
            int y = baseY - dy;

            if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                bestY = y + 1;
                found = true;
                break;
            }
        }

        if (!found) {
            for (int dy = 1; dy <= SEARCH_UP; dy++) {
                int y = baseY + dy;

                if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                    if ((y - baseY) <= MAX_STEP_UP) {
                        bestY = y + 1;
                        found = true;
                    }
                    break;
                }
            }
        }

        if (!found) return pos;

        double newY = bestY + 0.05;

        // 🚫 Prevent massive jumps (this is the KEY part)
        double delta = newY - pos.y;

        if (delta > MAX_STEP_UP || delta < -MAX_STEP_DOWN) {
            return pos;
        }

        return new Vec3d(pos.x, newY, pos.z);
    }

    private boolean isSolidGround(ServerWorld world, int x, int y, int z) {
        return world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z))
                .isSolidBlock(world, new net.minecraft.util.math.BlockPos(x, y, z));
    }

    private boolean isAirAbove(ServerWorld world, int x, int y, int z) {
        return world.getBlockState(new net.minecraft.util.math.BlockPos(x, y + 1, z)).isAir();
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override public String getName() { return "Interdimensional"; }

    @Override public String getPassiveName()   { return "Instability"; }
    @Override public String getPrimaryName()   { return "Phase Shift"; }
    @Override public String getSecondaryName() { return "Displacement"; }
    @Override public String getUltimateName()  { return "Fracture"; }

    @Override public long getPrimaryCooldownMs()   { return 6000; }
    @Override public long getSecondaryCooldownMs() { return 9000; }
    @Override public long getUltimateCooldownMs()  { return 20000; }

    @Override
    public String getOverviewDescription() {
        return "Interdimensional is a power that is centered around making and taking opportunities, able to separate out entities and take duels easily. The power" +
                "also has some defensive utility, able to quickly escape or get brief damage immunity when under pressure.";
    }

    @Override
    public String getPassiveDescription() {
        return "You have a constant chance to start 'flickering', when flickering you cannot receive any damage from any source. This chance increases when taking damage" +
                " and goes on cooldown briefly after flickering.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Enter spectator mode briefly, being able to pass through blocks, fly and travel faster, leaving a visible trail of particles. When exiting spectator mode," +
                " do a burst of damage to nearby entities.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Shoot a slow, piercing projectile that banishes hit entities. Banished entities cannot move, attack, interact, or receive damage from any sources for a few seconds." +
                " Their location is indicated by particles an they can still see during banishment.";
    }

    @Override
    public String getUltimateDescription() {
        return "Create a 'fracture' at your location, this creates a series of cracks along the ground that create isolated 'sections' that entities must remain in." +
                " Any entities touching these cracks will be significantly slowed and take constant damage, the caster does not receive damage or slowness from these cracks." +
                " The intention of this is to force groups to become isolated, making it easier to take duels and kill groups.";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    public static boolean hasTag(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) return true;
        }
        return false;
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
                    if (ticks > 0) {
                        newTag = prefix + ticks;
                        active = true;
                    }
                } catch (NumberFormatException ignored) {
                    it.remove();
                }
                break;
            }
        }

        if (newTag != null) entity.getCommandTags().add(newTag);
        return active;
    }

    public static void removeTagPrefix(Entity e, String prefix) {
        e.getCommandTags().removeIf(s -> s.startsWith(prefix));
    }

    private int getTagTicks(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                try {
                    return Integer.parseInt(tag.substring(prefix.length()));
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }
}