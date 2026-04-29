package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class PowerRitual implements Ritual {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // timer
    private static final int STAGE_1_TICKS = 80;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    // final stage
    private static final float STAGE_4_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    // the above glowy thing
    private static final double SHINE_START_HEIGHT = 12.0;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleEffect RITUAL_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.4f, 0.8f), 1.3f); // pink
    private static final DustParticleEffect RITUAL_WHITE_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.9f), 1.1f); // pinkish white
    private static final DustParticleEffect RITUAL_WHITE =
            new DustParticleEffect(new Vector3f(1.0f, 1.0f, 1.0f), 1.2f); // white
    private static final DustParticleEffect RITUAL_PALE =
            new DustParticleEffect(new Vector3f(0.9f, 0.85f, 1.0f), 0.9f); // more purpely

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    /** return true when done */
    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) return true;
        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        int stage = currentStage();
        int stageTick = stageLocalTick();

        // Lock position
        if (stage < 4) {
            lockPosition(sp);
        }

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        // End
        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }

        return false;
    }

    /* ============================================================
       STAGE HELPERS
       ============================================================ */

    private int currentStage() {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    /** Ticks elapsed within the current stage. */
    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    /** stops all movement. */
    private void lockPosition(ServerPlayerEntity sp) {
        // Stop all motion
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.fallDistance = 0;

        // stop movement
        sp.setOnGround(true);

        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 10, true, false, false));
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        // On entry
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.9f, 0.7f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d pos = sp.getPos();
        long time = world.getTime();

        // Orbit radius expand
        double radius = 1.2 + progress * 0.8;
        int orbitPoints = 6;

        for (int i = 0; i < orbitPoints; i++) {
            double angle = (time * 0.07) + (i * Math.PI * 2.0 / orbitPoints);
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + 0.8 + Math.sin(time * 0.08 + i) * 0.3;

            world.spawnParticles(RITUAL_PINK,   x, y, z, 1, 0, 0.02, 0, 0.005);
            world.spawnParticles(RITUAL_PALE,   x, y + 0.2, z, 1, 0.03, 0.03, 0.03, 0.008);
        }

        if (t % 3 == 0) {
            int circlePoints = 16;
            for (int i = 0; i < circlePoints; i++) {
                double angle = Math.PI * 2.0 * i / circlePoints;
                double x = pos.x + Math.cos(angle) * 2.0;
                double z = pos.z + Math.sin(angle) * 2.0;
                world.spawnParticles(RITUAL_PINK, x, pos.y + 0.05, z, 1, 0, 0.01, 0, 0.003);
            }
        }

        // Tick sound
        if (t % 20 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.4f, 1.2f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT,
                    SoundCategory.PLAYERS, 0.5f, 1.4f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d pos = sp.getPos();
        long time = world.getTime();

        // keep spiral
        int spiralPoints = (int)(6 + progress * 10);
        double spiralRadius = 1.0 + progress * 0.5;
        for (int i = 0; i < spiralPoints; i++) {
            double angle = (time * 0.09) + (i * Math.PI * 2.0 / spiralPoints);
            double heightVar = (i / (double) spiralPoints) * 2.0;
            double x = pos.x + Math.cos(angle) * spiralRadius;
            double z = pos.z + Math.sin(angle) * spiralRadius;

            world.spawnParticles(RITUAL_PINK,   x, pos.y + heightVar, z, 1, 0, 0.03, 0, 0.006);
            world.spawnParticles(RITUAL_WHITE_PINK,  x, pos.y + heightVar + 0.15, z, 1, 0.02, 0.02, 0.02, 0.005);
        }

        // above particles
        double shineY = pos.y + SHINE_START_HEIGHT;
        int shineDensity = (int)(5 + progress * 20);
        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.spawnParticles(RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    shineY + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.z + Math.sin(angle) * r,
                    1, 0.05, 0.08, 0.05, 0.02);
        }

        if (t % 3 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    shineY,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0, 0.05, 0, 0.03);
        }

        if (t % 4 == 0) {
            for (int i = 0; i < 8; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 2.5;
                world.spawnParticles(RITUAL_WHITE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0, 0.04, 0, 0.015);
            }
        }
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                    SoundCategory.PLAYERS, 0.7f, 0.5f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d pos = sp.getPos();
        long time = world.getTime();

        // start lowering the clump
        double playerHeadY = pos.y + 2.5;
        double shineY = pos.y + SHINE_START_HEIGHT - (SHINE_START_HEIGHT - 2.5) * progress;

        // make it denser
        int shineDensity = 20 + (int)(progress * 15);
        double shineRadius = 1.5 - progress * 0.8; // shrinks

        for (int i = 0; i < shineDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * shineRadius;

            world.spawnParticles(RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    shineY + (world.random.nextDouble() - 0.3) * 1.2,
                    pos.z + Math.sin(angle) * r,
                    1, 0.04, 0.06, 0.04, 0.015 + progress * 0.02);
        }

        // Beam of particles connecting to player
        if (t % 2 == 0) {
            double beamSegments = 8;
            for (int i = 0; i < beamSegments; i++) {
                double beamT = i / beamSegments;
                double beamY = pos.y + 1.0 + (shineY - pos.y - 1.0) * beamT;
                world.spawnParticles(RITUAL_PINK,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.2,
                        beamY,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                        1, 0, 0.03, 0, 0.01);
            }
        }

        // when its close
        if (progress > 0.7f && t % 3 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.5,
                    shineY,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.5,
                    2, 0.1, 0.1, 0.1, 0.05);
        }

        // keep spiral
        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 8);
            world.spawnParticles(RITUAL_WHITE,
                    pos.x + Math.cos(angle) * 1.2,
                    pos.y + 1.0,
                    pos.z + Math.sin(angle) * 1.2,
                    1, 0, 0.02, 0, 0.005);
        }

        // contact
        if (progress > 0.95f) {
            world.spawnParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.5, pos.z,
                    10, 0.5, 0.5, 0.5, 0.06);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z,
                    1, 0, 0, 0, 0);

            if (t % 5 == 0) {
                world.playSound(null, sp.getBlockPos(),
                        SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                        SoundCategory.PLAYERS, 0.5f, 1.8f);
            }
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            // impact stuff
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    SoundCategory.PLAYERS, 1.2f, 0.6f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);

            Vec3d pos = sp.getPos();
            world.spawnParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z,
                    40, 1.2, 1.0, 1.2, 0.1);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.3, 0.3, 0.3, 0);

        }

        Vec3d pos = sp.getPos();

        // Sustained blindness
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, 15, 0, true, false, false));

        // damage without kill
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float currentHealth = sp.getHealth();
            if (currentHealth > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // fx around player
        double progress = (double) t / STAGE_4_TICKS;
        int burstCount = (int)(5 + (1.0 - progress) * 10); // more chaotic at start settles at end

        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.8;
            double h = world.random.nextDouble() * 2.5;

            world.spawnParticles(world.random.nextFloat() < 0.5f ? RITUAL_PINK : RITUAL_WHITE_PINK,
                    pos.x + Math.cos(angle) * r,
                    pos.y + h,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0.03, 0, 0.02);
        }

        // occasional other stuff
        if (t % 8 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 1.0, pos.z,
                    3, 0.4, 0.3, 0.4, 0.06);
        }

        // calm it down
        if (progress > 0.75f) {
            world.spawnParticles(RITUAL_PALE,
                    pos.x, pos.y + 1.0, pos.z,
                    4, 0.5, 0.4, 0.5, 0.01);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        // Grant the power
        PowerManager.assignRandomPower(sp);

        // remove effects
        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.heal(2.0f);

        // final particles
        Vec3d pos = sp.getPos();
        world.spawnParticles(RITUAL_PINK,  pos.x, pos.y + 1.0, pos.z, 30, 1.0, 0.8, 1.0, 0.08);
        world.spawnParticles(RITUAL_WHITE_PINK, pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.6, 0.8, 0.06);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
}