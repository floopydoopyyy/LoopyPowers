package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.*;
import com.yourname.loopypowers.sound.ModSounds;
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

import java.util.List;
import java.util.function.Supplier;

public class LifeRitual implements Ritual {

    /* ============================================================
       POWERS
       ============================================================ */

    private static final List<Supplier<Power>> LIFE_POWERS = List.of(
            HealingPower::new,
            NaturePower::new,
            StrengthPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 75;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.25f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 12;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private static final double CANOPY_HEIGHT = 11.0;  //
    private static final double ROOT_REACH    = 5.5;   //

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleEffect DARK_GREEN =
            new DustParticleEffect(new Vector3f(0.08f, 0.55f, 0.12f), 1.5f);
    private static final DustParticleEffect LIGHT_GREEN =
            new DustParticleEffect(new Vector3f(0.35f, 0.90f, 0.22f), 1.3f);
    private static final DustParticleEffect ROOT =
            new DustParticleEffect(new Vector3f(0.22f, 0.32f, 0.08f), 1.6f);
    private static final DustParticleEffect GOLD =
            new DustParticleEffect(new Vector3f(0.88f, 0.82f, 0.10f), 1.2f);
    private static final DustParticleEffect PINK =
            new DustParticleEffect(new Vector3f(0.98f, 0.92f, 0.90f), 1.0f);

    /* ============================================================
       ROOT GEOMETRY
       ============================================================ */

    private static final double[] ROOT_ANGLES = {
            0.0,
            Math.PI * 0.35,
            Math.PI * 0.72,
            Math.PI,
            Math.PI * 1.32,
            Math.PI * 1.74
    };

    // Each root: two branch angles offset by radians from the root angle
    // and a branch start fraction - how far along the root before branching
    private static final double[][] ROOT_BRANCHES = {
            { +0.40, -0.28 },  // root 0 branches
            { -0.35, +0.30 },  // root 1
            { +0.32, -0.38 },  // root 2
            { -0.30, +0.42 },  // root 3
            { +0.38, -0.25 },  // root 4
            { -0.42, +0.32 },  // root 5
    };
    private static final double BRANCH_START = 0.45; // where the branch begins at what point of the root

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public LifeRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) return true;
        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        // ambient loop plays evenly throughout the entire ritual
        if (ticks == 1 || ticks % 28 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    ModSounds.RITUALLOOP, SoundCategory.PLAYERS, 0.5f, 0.7f);
        }

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        if (stage < 4) lockPosition(sp);

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
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));
    }

    /* ============================================================
       SHARED HELPERS
       ============================================================ */

    private void spawnRoots(ServerWorld world, Vec3d pos,
                            double reach, boolean glow) {
        for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
            double angle = ROOT_ANGLES[ri];

            // Main root stem
            int steps = Math.max(3, (int)(reach / 0.5));
            for (int s = 0; s < steps; s++) {
                double d = 0.3 + (double) s / steps * reach;

                double jitter = (world.random.nextDouble() - 0.5) * 0.20;
                double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                DustParticleEffect col = (s < steps / 3) ? ROOT : DARK_GREEN;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * d + perpX,
                        pos.y + 0.04,
                        pos.z + Math.sin(angle) * d + perpZ,
                        1, 0.01, 0.01, 0.01, 0.002);
            }

            // tip fx
            if (glow && reach > 1.0) {
                world.spawnParticles(LIGHT_GREEN,
                        pos.x + Math.cos(angle) * reach,
                        pos.y + 0.09,
                        pos.z + Math.sin(angle) * reach,
                        1, 0.04, 0.05, 0.04, 0.012);
            }

            // Two branch offshoots — begin at BRANCH_START fraction of reach
            double branchStartD = reach * BRANCH_START;
            double[] branches   = ROOT_BRANCHES[ri];
            double   branchLen  = reach * (1.0 - BRANCH_START) * 0.75;

            if (branchStartD > 0.4 && branchLen > 0.3) {
                for (double branchOffset : branches) {
                    double branchAngle = angle + branchOffset;
                    int    bSteps      = Math.max(2, (int)(branchLen / 0.45));

                    for (int s = 0; s < bSteps; s++) {
                        double d      = branchStartD + (double) s / bSteps * branchLen;
                        double jitter = (world.random.nextDouble() - 0.5) * 0.16;
                        double bPerpX = Math.cos(branchAngle + Math.PI * 0.5) * jitter;
                        double bPerpZ = Math.sin(branchAngle + Math.PI * 0.5) * jitter;

                        world.spawnParticles(DARK_GREEN,
                                pos.x + Math.cos(branchAngle) * d + bPerpX,
                                pos.y + 0.04,
                                pos.z + Math.sin(branchAngle) * d + bPerpZ,
                                1, 0.01, 0.01, 0.01, 0.002);
                    }

                    // Branch tip glow
                    if (glow) {
                        world.spawnParticles(LIGHT_GREEN,
                                pos.x + Math.cos(branchAngle) * (branchStartD + branchLen),
                                pos.y + 0.08,
                                pos.z + Math.sin(branchAngle) * (branchStartD + branchLen),
                                1, 0.03, 0.04, 0.03, 0.010);
                    }
                }
            }
        }
    }

    /* ============================================================
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    ModSounds.RITUALSTART, SoundCategory.PLAYERS, 1.0f, 1.0f);

            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_MOSS_PLACE,    SoundCategory.PLAYERS, 0.9f, 0.75f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_GRASS_PLACE,   SoundCategory.PLAYERS, 0.6f, 0.85f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();

        // roots growing
        double reach = 0.6 + progress * (ROOT_REACH - 0.6);
        if (t % 3 == 0) {
            spawnRoots(world, pos, reach, t % 8 == 0);
        }

        // rising "spores"
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double rAngle = ROOT_ANGLES[world.random.nextInt(ROOT_ANGLES.length)];
                double d      = 0.6 + world.random.nextDouble() * reach * 0.8;
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        pos.x + Math.cos(rAngle) * d,
                        pos.y + 0.1,
                        pos.z + Math.sin(rAngle) * d,
                        1, 0.04, 0.06, 0.04, 0.015);
            }
        }

        // "falling spores"
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + world.random.nextDouble() * 4.0;
                world.spawnParticles(ParticleTypes.FALLING_SPORE_BLOSSOM,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 5.0 + world.random.nextDouble() * 4.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.04, 0.02, 0.005);
            }
        }

        // gold from the player
        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 0.5;
                world.spawnParticles(GOLD,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05 + world.random.nextDouble() * 1.2 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.006);
            }
        }

        if (t % 24 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AZALEA_LEAVES_PLACE,
                    SoundCategory.PLAYERS, 0.5f, 0.8f + (float) progress * 0.25f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    ModSounds.DARKNESSTELEPORT2, SoundCategory.PLAYERS, 0.8f, 0.75f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AZALEA_LEAVES_PLACE,    SoundCategory.PLAYERS, 0.6f, 0.65f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // keep roots
        if (t % 3 == 0) {
            spawnRoots(world, pos, ROOT_REACH, t % 6 == 0);
        }

        // Vine spirals
        double vineHeight = CANOPY_HEIGHT * progress;
        if (t % 2 == 0) {
            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double baseAngle = ROOT_ANGLES[ri];
                double cx        = pos.x + Math.cos(baseAngle) * 0.8;
                double cz        = pos.z + Math.sin(baseAngle) * 0.8;

                int vineSteps = Math.max(4, (int)(vineHeight / 0.45));
                for (int s = 0; s < vineSteps; s++) {
                    double fraction = (double) s / vineSteps;
                    double y        = pos.y + fraction * vineHeight;
                    // The vine coils tighter as it rises — radius shrinks with height
                    double vR    = 0.55 * (1.0 - fraction * 0.5);
                    double twist = baseAngle + fraction * Math.PI * 3.0 + time * 0.025;

                    DustParticleEffect col = (fraction < 0.4) ? ROOT
                            : (fraction < 0.75) ? DARK_GREEN
                            : LIGHT_GREEN;
                    world.spawnParticles(col,
                            cx + Math.cos(twist) * vR,
                            y,
                            cz + Math.sin(twist) * vR,
                            1, 0.02, 0.02, 0.02, 0.004);
                }

                // vine tip
                if (vineHeight > 1.5 && t % 4 == 0) {
                    world.spawnParticles(LIGHT_GREEN,
                            cx + (world.random.nextDouble() - 0.5) * 0.3,
                            pos.y + vineHeight,
                            cz + (world.random.nextDouble() - 0.5) * 0.3,
                            1, 0.04, 0.06, 0.04, 0.015);
                }
            }
        }

        // canopy above
        double canopyY = pos.y + CANOPY_HEIGHT;
        double canopyR = 3.5 - progress * 1.8;  // tightens
        int    canopyDensity = (int)(6 + progress * 20);

        if (t % 2 == 0) {
            for (int i = 0; i < canopyDensity; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * canopyR;
                double h     = (world.random.nextDouble() - 0.3) * 1.8;
                DustParticleEffect col = world.random.nextFloat() < 0.55f
                        ? PINK : GOLD;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        canopyY + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.03, 0.04, 0.03, 0.010);
            }
        }

        // the usual
        if (t % 5 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * canopyR * 1.4,
                    canopyY,
                    pos.z + (world.random.nextDouble() - 0.5) * canopyR * 1.4,
                    1, 0, 0.03, 0, 0.02);
        }

        // particles drifting towards player
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 2.5;
                double h     = 2.0 + world.random.nextDouble() * (CANOPY_HEIGHT - 3.0);
                world.spawnParticles(ParticleTypes.CHERRY_LEAVES,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.03, 0.02, 0.008);
            }
        }

        // "spores"
        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.5;
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 3.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        // why not
        if (t % 22 == 0) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, 25, 0, true, false, false));
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE,
                    SoundCategory.PLAYERS, 0.5f, 0.85f + (float) progress * 0.35f);
        }
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.6f, 0.65f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.5f, 0.55f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d  pos      = sp.getPos();

        // tighten the canopy and move it down
        double canopyR = 1.7 * (1.0 - progress);
        double beamTop = pos.y + CANOPY_HEIGHT;

        if (canopyR > 0.15 && t % 2 == 0) {
            int density = (int)(20 + (1.0 - progress) * 15);
            for (int i = 0; i < density; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * canopyR;
                DustParticleEffect col = world.random.nextFloat() < 0.5f
                        ? PINK : GOLD;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        beamTop + (world.random.nextDouble() - 0.3),
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.012);
            }
        }

        // beam partcles
        if (t % 2 == 0) {
            int beamSteps = (int)(8 + progress * 10);
            for (int s = 0; s < beamSteps; s++) {
                double fraction = (double) s / beamSteps;
                double y        = beamTop - fraction * (CANOPY_HEIGHT - 1.8);
                // Jitter narrows as the beam approaches the player — funnel shape
                double jitter   = 0.55 * (1.0 - fraction) + 0.04;

                DustParticleEffect col = (fraction < 0.4) ? PINK
                        : (fraction < 0.75) ? LIGHT_GREEN
                        : GOLD;
                world.spawnParticles(col,
                        pos.x + (world.random.nextDouble() - 0.5) * jitter,
                        y,
                        pos.z + (world.random.nextDouble() - 0.5) * jitter,
                        1, 0, -0.04, 0, 0.008);
            }
        }

        // again
        if (t % 3 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.3,
                    beamTop,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.3,
                    1, 0, -0.18, 0, 0.0);
        }

        // pulse roots
        if (t % 4 == 0) {
            spawnRoots(world, pos, ROOT_REACH, true);
        }

        // petals around beam
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.8 + world.random.nextDouble() * 2.2;
                double h     = 1.0 + world.random.nextDouble() * (CANOPY_HEIGHT - 2.0);
                world.spawnParticles(ParticleTypes.CHERRY_LEAVES,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.04, 0.02, 0.010);
            }
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.5;
                double h     = 1.5 + world.random.nextDouble() * 5.0;
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.02, 0.02, 0.008);
            }
        }

        // contact
        if (progress > 0.88f && t % 4 == 0) {
            world.spawnParticles(PINK, pos.x, pos.y + 1.5, pos.z,
                    6, 0.4, 0.3, 0.4, 0.04);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z,
                    1, 0, 0, 0, 0);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.6f, 1.3f);
        }

        if (t % 18 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AZALEA_LEAVES_PLACE,
                    SoundCategory.PLAYERS, 0.6f, 0.9f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,     SoundCategory.PLAYERS, 1.0f, 0.70f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE, SoundCategory.PLAYERS, 1.0f, 0.55f);

            Vec3d pos = sp.getPos();

            // roots surge out
            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double angle = ROOT_ANGLES[ri];
                for (double d = 0.3; d <= ROOT_REACH; d += 0.5) {
                    world.spawnParticles(DARK_GREEN,
                            pos.x + Math.cos(angle) * d,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d,
                            1,
                            Math.cos(angle) * 0.04, 0.14, Math.sin(angle) * 0.04,
                            0.0);
                }
                // Branches erupt too
                double[] branches = ROOT_BRANCHES[ri];
                for (double branchOff : branches) {
                    double bAngle = angle + branchOff;
                    double bStart = ROOT_REACH * BRANCH_START;
                    double bLen   = ROOT_REACH * (1.0 - BRANCH_START) * 0.75;
                    for (double d = bStart; d <= bStart + bLen; d += 0.5) {
                        world.spawnParticles(LIGHT_GREEN,
                                pos.x + Math.cos(bAngle) * d,
                                pos.y + 0.05,
                                pos.z + Math.sin(bAngle) * d,
                                1,
                                Math.cos(bAngle) * 0.04, 0.12, Math.sin(bAngle) * 0.04,
                                0.0);
                    }
                }
            }

            // Blossom and gold burst
            for (int d = 0; d < 24; d++) {
                double theta = d * Math.PI * 2.0 / 24;
                double phi   = Math.PI * 0.35 + world.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.18 + world.random.nextDouble() * 0.10;
                DustParticleEffect col = (d % 2 == 0) ? PINK : GOLD;
                world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }

            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.2, 0.2, 0.2, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // effects
        if (progress > 0.10f && progress < 0.80f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 25, 0, true, false, false));
        }

        // Regeneration
        if (t % 15 == 0) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, 20, 1, true, false, false));
        }

        // Damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                // Apply Custom Ritual Damage Type
                sp.damage(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // usual storm
        int burstCount = (int)(10 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 1.8;
            double h     = world.random.nextDouble() * 2.6;
            DustParticleEffect col = switch (i % 4) {
                case 0  -> DARK_GREEN;
                case 1  -> LIGHT_GREEN;
                case 2  -> GOLD;
                default -> PINK;
            };
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0.025, 0, 0.015);
        }

        // cherry blossoms
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 3.0;
                world.spawnParticles(ParticleTypes.CHERRY_LEAVES,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.3 + world.random.nextDouble() * 3.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.03, 0.02, 0.01);
            }
        }

        // spores rising
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.5;
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 2.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.05, 0.02, 0.015);
            }
        }

        // end rods
        if (t % 5 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.6,
                    pos.y + 0.5 + world.random.nextDouble() * 2.2,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.6,
                    1, 0.03, 0.07, 0.03, 0.025);
        }

        // calming stage
        if (progress > 0.78f) {
            world.spawnParticles(PINK, pos.x, pos.y + 1.3, pos.z,
                    2, 0.5, 0.3, 0.5, 0.005);
            world.spawnParticles(GOLD,   pos.x, pos.y + 1.5, pos.z,
                    1, 0.4, 0.2, 0.4, 0.004);
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    ModSounds.DARKNESSLOOP,
                    SoundCategory.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory =
                LIFE_POWERS.get(sp.getRandom().nextInt(LIFE_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.REGENERATION);
        sp.heal(4.0f);

        Vec3d pos = sp.getPos();

        // final root fx
        for (double angle : ROOT_ANGLES) {
            world.spawnParticles(LIGHT_GREEN,
                    pos.x + Math.cos(angle) * 2.0,
                    pos.y + 0.05,
                    pos.z + Math.sin(angle) * 2.0,
                    3, Math.cos(angle) * 0.03, 0.10, Math.sin(angle) * 0.03, 0.0);
        }

        world.spawnParticles(DARK_GREEN,   pos.x, pos.y + 1.0, pos.z, 22, 1.1, 0.9, 1.1, 0.08);
        world.spawnParticles(LIGHT_GREEN,   pos.x, pos.y + 1.0, pos.z, 18, 0.9, 0.8, 0.9, 0.07);
        world.spawnParticles(PINK,  pos.x, pos.y + 1.0, pos.z, 16, 0.8, 0.7, 0.8, 0.07);
        world.spawnParticles(GOLD,    pos.x, pos.y + 1.2, pos.z, 12, 0.6, 0.5, 0.6, 0.06);
        world.spawnParticles(ParticleTypes.CHERRY_LEAVES, pos.x, pos.y + 1.0, pos.z,
                20, 1.2, 1.0, 1.2, 0.06);
        world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.x, pos.y + 0.5, pos.z,
                10, 0.8, 0.6, 0.8, 0.04);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 0.9f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE,
                SoundCategory.PLAYERS, 0.8f, 1.1f);
    }
}