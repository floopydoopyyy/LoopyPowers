package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class PerfectedUpgradeRitual implements Ritual {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float DAMAGE_PER_TICK = 0.35f;
    private static final int   DAMAGE_INTERVAL = 10;
    private static final float MIN_HEALTH      = 0.5f;

    /* ============================================================
       CAMERA SHAKE
       ============================================================ */

    private static final int   SHAKE_RADIUS            = 8;   // wider than upgrade ritual
    private static final int   SHAKE_STAGE_1_INTERVAL  = 18;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 10;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final int   SHAKE_STAGE_4_INTERVAL  = 4;   // very frequent at peak
    private static final float SHAKE_STAGE_1_INTENSITY = 0.10f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.20f;
    private static final float SHAKE_STAGE_3_INTENSITY = 0.28f;
    private static final float SHAKE_STAGE_4_BURST     = 0.50f;  //
    private static final float SHAKE_STAGE_4_BASE      = 0.30f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // magenta
    private static final DustParticleEffect MAGENTA =
            new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    // white pink
    private static final DustParticleEffect PINK_WHITE =
            new DustParticleEffect(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    // purple
    private static final DustParticleEffect PURPLE =
            new DustParticleEffect(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    // violet
    private static final DustParticleEffect VIOLET =
            new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    // yellow white
    private static final DustParticleEffect WHITE_YELLOW =
            new DustParticleEffect(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);
    // white
    private static final DustParticleEffect WHITE =
            new DustParticleEffect(new Vector3f(1.00f, 0.98f, 1.00f), 1.8f);

    // SIGIL GEOMETRY

    private static final double[] SIGIL_ANGLES = {
            0.0,
            Math.PI * 0.28,
            Math.PI * 0.55,
            Math.PI * 0.82,
            Math.PI,
            Math.PI * 1.25,
            Math.PI * 1.55,
            Math.PI * 1.80
    };

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PerfectedUpgradeRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayerEntity sp) {
                cancelRitual(sp);
            }
            return true;
        }

        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

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

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private void lockPosition(ServerPlayerEntity sp) {
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.fallDistance = 0;
        sp.setOnGround(true);
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayerEntity sp) {
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
    }

    /* ============================================================
       SHARED HELPERS
       ============================================================ */

    private void spawnFallingColumn(ServerWorld world, Vec3d pos,
                                    int count, double radius, double minH, double maxH) {
        for (int i = 0; i < count; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * radius;
            DustParticleEffect col = world.random.nextBoolean() ? MAGENTA : WHITE_YELLOW;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r,
                    pos.y + minH + world.random.nextDouble() * (maxH - minH),
                    pos.z + Math.sin(angle) * r,
                    1, 0.02, -0.10, 0.02, 0.004);
        }
    }

    private void spawnHalo(ServerWorld world, Vec3d pos, double radius, double height,
                           double speed, int points,
                           DustParticleEffect primary, DustParticleEffect secondary) {
        for (int i = 0; i < points; i++) {
            double angle = speed + i * Math.PI * 2.0 / points;
            DustParticleEffect col = (i % 2 == 0) ? primary : secondary;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * radius,
                    pos.y + height,
                    pos.z + Math.sin(angle) * radius,
                    1, 0.01, 0.015, 0.01, 0.005);
        }
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_AMBIENT,     SoundCategory.PLAYERS, 1.0f, 0.45f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.6f, 0.25f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // sigil on ground
        double sigRotation = time * 0.020;
        double spokeReach  = 1.0 + progress * 4.0;

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * spokeReach;
                    // Inner spoke: deep purple; outer: violet then blush at the tip
                    DustParticleEffect col = (s < 4) ? PURPLE
                            : (s < 7) ? VIOLET
                            : PINK_WHITE;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.14;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.01, 0.01, 0.01, 0.003);
                }

                // tip
                if (t % 5 == 0 && spokeReach > 1.0) {
                    world.spawnParticles(MAGENTA,
                            pos.x + Math.cos(angle) * spokeReach,
                            pos.y + 0.10,
                            pos.z + Math.sin(angle) * spokeReach,
                            1, 0.04, 0.06, 0.04, 0.014);
                }
            }

            // ring joining
            if (spokeReach > 1.5) {
                int ringPoints = 32;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                    DustParticleEffect col = (i % 3 == 0) ? MAGENTA : PURPLE;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * spokeReach,
                            pos.y + 0.06,
                            pos.z + Math.sin(angle) * spokeReach,
                            1, 0.01, 0.01, 0.01, 0.003);
                }
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(3 + progress * 7), 1.4, 7.0, 13.0);
        }

        // fx rising
        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + world.random.nextDouble() * spokeReach * 0.7;
                world.spawnParticles(VIOLET,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * 2.5 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        // extra fx
        if (t % 14 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z,
                    12, 0.8, 0.8, 0.8, 1.6);
        }

        // glow at tips
        if (t % 7 == 0 && progress > 0.25f) {
            double spokeAngle = SIGIL_ANGLES[world.random.nextInt(SIGIL_ANGLES.length)] + sigRotation;
            world.spawnParticles(ParticleTypes.GLOW,
                    pos.x + Math.cos(spokeAngle) * spokeReach,
                    pos.y + 0.1,
                    pos.z + Math.sin(spokeAngle) * spokeReach,
                    2, 0.05, 0.05, 0.05, 0);
        }

        if (t % 18 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.PLAYERS, 0.6f, 0.35f + (float) progress * 0.45f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.9f, 0.65f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE,       SoundCategory.PLAYERS, 0.8f, 0.60f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // shake
        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_INTENSITY + (float) progress * (SHAKE_STAGE_3_INTENSITY - SHAKE_STAGE_2_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // rise and rotate sigil
        double sigRotation = time * 0.035;   // faster
        double sigilY      = pos.y + progress * 1.2;   // rises to chest

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 9;
                for (int s = 0; s < steps; s++) {
                    double d   = 0.3 + (double) s / steps * 5.0;
                    DustParticleEffect col = (s < 3) ? PURPLE
                            : (s < 6) ? VIOLET
                            : MAGENTA;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d,
                            sigilY,
                            pos.z + Math.sin(angle) * d,
                            1, 0.02, 0.015, 0.02, 0.003);
                }
            }

            // Outer ring of the floating sigil
            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                DustParticleEffect col = (i % 2 == 0) ? PURPLE : PINK_WHITE;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * 5.0,
                        sigilY,
                        pos.z + Math.sin(angle) * 5.0,
                        1, 0.01, 0.01, 0.01, 0.003);
            }
        }

        // three halos at varied sizes
        if (t % 2 == 0) {
            // Outer — pink, slow clockwise
            spawnHalo(world, pos, 2.4, 1.6, time * 0.07, 24, MAGENTA, VIOLET);
        }
        if (t % 2 == 0) {
            // Mid — purple, counter-clockwise
            spawnHalo(world, pos, 1.7, 1.9, -time * 0.10, 18, PURPLE, WHITE_YELLOW);
        }
        if (t % 3 == 0) {
            // Inner — white, fast clockwise
            spawnHalo(world, pos, 1.0, 2.2, time * 0.14, 12, WHITE_YELLOW, VIOLET);
        }

        // beams from each sigil spoke
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx    = pos.x + Math.cos(angle) * 0.7;
                double cz    = pos.z + Math.sin(angle) * 0.7;
                int    steps = (int)(5 + progress * 7);
                for (int s = 0; s < steps; s++) {
                    double h   = pos.y + 0.3 + s * (0.6 + progress * 0.4);
                    DustParticleEffect col = switch (s % 3) {
                        case 0  -> MAGENTA;
                        case 1  -> PURPLE;
                        default -> WHITE_YELLOW;
                    };
                    world.spawnParticles(col,
                            cx + (world.random.nextDouble() - 0.5) * 0.20,
                            h,
                            cz + (world.random.nextDouble() - 0.5) * 0.20,
                            1, 0.02, 0.06, 0.02, 0.006);
                }
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(6 + progress * 10), 1.0, 7.0, 14.0);
        }

        // blindness
        if (progress > 0.35f && progress < 0.92f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // damage
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), DAMAGE_PER_TICK);
            }
        }

        if (t == 22) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,  SoundCategory.PLAYERS, 0.7f, 0.45f);
        if (t == 46) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.75f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.9f, 0.8f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT,  SoundCategory.PLAYERS, 0.7f, 1.1f);
            // Shake spike as stage 3 opens
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_INTENSITY + 0.05f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // shake
        if (t % SHAKE_STAGE_3_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_3_INTENSITY
                    + (float) progress * (SHAKE_STAGE_4_BASE - SHAKE_STAGE_3_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // compress sigil
        double sigRotation = time * 0.055;   // fastest the sigil spins
        double shrink      = 1.0 - progress * 0.84;
        double sigR        = 5.0 * shrink + 0.8;
        double sigY        = pos.y + 1.2 - progress * 0.6;   // descends back toward feet

        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 8;
                for (int s = 0; s < steps; s++) {
                    double d   = 0.2 + (double) s / steps * sigR;
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE : MAGENTA;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d,
                            sigY,
                            pos.z + Math.sin(angle) * d,
                            1, 0.03, 0.02, 0.03, 0.005);
                }
            }
        }

        // halos shrinking
        double haloShrink = 1.0 - progress * 0.55;
        if (t % 2 == 0) {
            spawnHalo(world, pos, 2.4 * haloShrink, 1.6,  time * (0.07 + progress * 0.08), 24,
                    MAGENTA, VIOLET);
            spawnHalo(world, pos, 1.7 * haloShrink, 1.9, -time * (0.10 + progress * 0.08), 18,
                    PURPLE, WHITE_YELLOW);
        }
        if (t % 2 == 0) {
            spawnHalo(world, pos, 1.0 * haloShrink, 2.2,  time * (0.14 + progress * 0.12), 12,
                    WHITE_YELLOW, VIOLET);
        }

        // particle pull
        if (t % 2 == 0) {
            int pullCount = (int)(10 + progress * 18);
            for (int i = 0; i < pullCount; i++) {
                double theta  = world.random.nextDouble() * Math.PI * 2;
                double phi    = world.random.nextDouble() * Math.PI;
                double srcR   = 3.5 + world.random.nextDouble() * 5.0;
                double fromX  = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = pos.y + 1.0 + Math.cos(phi) * srcR * 0.45;
                double fromZ  = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3d  toward = pos.add(0, 1, 0)
                        .subtract(fromX, fromY, fromZ)
                        .normalize()
                        .multiply(0.10 + progress * 0.07);

                DustParticleEffect col = switch (i % 4) {
                    case 0  -> MAGENTA;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    default -> WHITE_YELLOW;
                };
                world.spawnParticles(col, fromX, fromY, fromZ,
                        1, toward.x, toward.y, toward.z, 0.010);
            }
        }

        // outward eruptions
        if (t % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.5;
                double speed = 0.10 + progress * 0.05;
                DustParticleEffect col = (i % 2 == 0) ? MAGENTA : WHITE_YELLOW;
                world.spawnParticles(col,
                        pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.5 + 0.04,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // column
        if (t % 2 == 0) {
            spawnFallingColumn(world, pos, (int)(10 + progress * 10), 0.7, 8.0, 15.0);
        }

        // effects
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (progress > 0.40f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.NAUSEA, 30, 0, true, false, false));
        }

        // Damage continues
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), DAMAGE_PER_TICK);
            }
        }

        if (t % 8 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.7, 0.6, 1.8);
        }

        if (t % 10 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.PLAYERS,
                    (float)(0.6 + progress * 0.4), 0.9f + (float) progress * 0.5f);
        }
        if (t == 30) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,      SoundCategory.PLAYERS, 1.3f, 0.65f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.9f, 0.75f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,     SoundCategory.PLAYERS, 1.0f, 0.80f);

            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_4_BURST);

            // shoot particles out
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 48; d++) {
                double theta = d * Math.PI * 2.0 / 48;
                double phi   = world.random.nextDouble() * Math.PI;
                double speed = 0.22 + world.random.nextDouble() * 0.16;
                DustParticleEffect col = switch (d % 5) {
                    case 0  -> MAGENTA;
                    case 1  -> WHITE_YELLOW;
                    case 2  -> PURPLE;
                    case 3  -> VIOLET;
                    default -> WHITE;
                };
                world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }

            // shockwave rings
            for (double ringScale : new double[]{ 0.5, 1.0, 1.6 }) {
                int ringPoints = 20;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = i * Math.PI * 2.0 / ringPoints;
                    world.spawnParticles(
                            ringScale < 1.0 ? WHITE : PURPLE,
                            pos.x + Math.cos(angle) * ringScale * 0.4,
                            pos.y + 0.15,
                            pos.z + Math.sin(angle) * ringScale * 0.4,
                            1,
                            Math.cos(angle) * 0.32 * ringScale,
                            0.01,
                            Math.sin(angle) * 0.32 * ringScale,
                            0.0);
                }
            }

            world.spawnParticles(ParticleTypes.FLASH,  pos.x, pos.y + 1.2, pos.z, 5, 0.4, 0.4, 0.4, 0);
            world.spawnParticles(ParticleTypes.GLOW,   pos.x, pos.y + 1.0, pos.z, 20, 0.8, 0.7, 0.8, 0);
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                    20, 1.0, 1.2, 1.0, 0.20);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;
        long   time     = world.getTime();

        // decay shake
        int shakeInterval = (progress < 0.60) ? SHAKE_STAGE_4_INTERVAL : SHAKE_STAGE_3_INTERVAL;
        if (t % shakeInterval == 0) {
            float intensity = SHAKE_STAGE_4_BASE * (float)(1.0 - progress * 0.70);
            intensity = Math.max(intensity, SHAKE_STAGE_1_INTENSITY);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }


        // blindness
        if (progress < 0.72f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // dense column
        if (t % 2 == 0 && progress < 0.70f) {
            spawnFallingColumn(world, pos,
                    (int)(14 + (1.0 - progress) * 10), 0.6, 8.0, 16.0);
        }

        // fast halos
        double finalShrink = 0.7 - progress * 0.25;
        if (t % 2 == 0 && progress < 0.80f) {
            spawnHalo(world, pos, Math.max(0.3, 2.4 * finalShrink), 1.6,
                    time * 0.18, 24, MAGENTA, WHITE);
            spawnHalo(world, pos, Math.max(0.3, 1.7 * finalShrink), 1.9,
                    -time * 0.22, 18, PURPLE, WHITE_YELLOW);
            spawnHalo(world, pos, Math.max(0.2, 1.0 * finalShrink), 2.2,
                    time * 0.28, 12, WHITE, VIOLET);
        }

        // more particles bursting out
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.7;
                double speed = 0.08 + (1.0 - progress) * 0.06;
                world.spawnParticles(WHITE,
                        pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // totem stuff
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.7,
                        pos.y + 0.3 + world.random.nextDouble() * 1.8,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.7,
                        1,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.10 + world.random.nextDouble() * 0.12,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.0);
            }
        }

        // calming
        if (progress > 0.75f) {
            world.spawnParticles(WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
            world.spawnParticles(PINK_WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
        }

        // Enchant throughout stage
        if (t % 8 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z, 10, 0.6, 0.8, 0.6, 2.0);
        }

        if (t % 7 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS,
                    0.6f + (float) progress * 0.4f, 0.65f + (float) progress * 1.0f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        PowerManager.setLevel(sp, 3);

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;

        // Heal
        sp.heal(6.0f);

        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.28f);

        Vec3d pos = sp.getPos();

        for (int d = 0; d < 40; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi   = world.random.nextDouble() * Math.PI;
            double speed = 0.18;
            DustParticleEffect col = switch (d % 4) {
                case 0  -> WHITE;
                case 1  -> MAGENTA;
                case 2  -> PURPLE;
                default -> WHITE_YELLOW;
            };
            world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                    1,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed,
                    0.0);
        }

        // Eight-point sigil star fired
        for (double angle : SIGIL_ANGLES) {
            for (double d = 0.5; d <= 4.0; d += 0.6) {
                world.spawnParticles(d < 2.0 ? WHITE : MAGENTA,
                        pos.x + Math.cos(angle) * d,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * d,
                        1,
                        Math.cos(angle) * 0.12, 0.01, Math.sin(angle) * 0.12,
                        0.0);
            }
        }

        world.spawnParticles(WHITE, pos.x, pos.y + 1.0, pos.z, 22, 1.5, 1.2, 1.5, 0.10);
        world.spawnParticles(MAGENTA,     pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        world.spawnParticles(PURPLE,     pos.x, pos.y + 1.0, pos.z, 16, 1.1, 1.0, 1.1, 0.08);
        world.spawnParticles(WHITE_YELLOW,      pos.x, pos.y + 1.0, pos.z, 14, 1.0, 0.9, 1.0, 0.07);
        world.spawnParticles(VIOLET,     pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                24, 1.2, 1.5, 1.2, 0.22);
        world.spawnParticles(ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z,
                16, 1.0, 0.8, 1.0, 0);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.4, pos.z,
                5, 0.3, 0.3, 0.3, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 1.0f, 1.6f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                SoundCategory.PLAYERS, 0.6f, 2.0f);

        sp.sendMessage(
                net.minecraft.text.Text.literal("§dYour bond has been perfected to Level 3."),
                false
        );
    }
}