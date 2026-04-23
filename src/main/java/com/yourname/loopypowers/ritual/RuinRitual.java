package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.*;
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

public class RuinRitual implements Ritual {

    /* ============================================================
       POWERS
       ============================================================ */

    private static final List<Supplier<Power>> RUIN_POWERS = List.of(
            ExplosionPower::new,
            FortunePower::new,
            BloodPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 80;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleEffect PURPLE =
            new DustParticleEffect(new Vector3f(0.38f, 0.00f, 0.55f), 1.5f);
    private static final DustParticleEffect PURPLE_DARK =
            new DustParticleEffect(new Vector3f(0.18f, 0.00f, 0.28f), 1.6f);
    private static final DustParticleEffect PURPLE_MID =
            new DustParticleEffect(new Vector3f(0.62f, 0.10f, 0.82f), 1.3f);
    private static final DustParticleEffect RED =
            new DustParticleEffect(new Vector3f(0.62f, 0.00f, 0.08f), 1.4f);
    private static final DustParticleEffect RED_DARK =
            new DustParticleEffect(new Vector3f(0.30f, 0.00f, 0.04f), 1.6f);

    // CRACK GEOMETRY
    private static final double[] CRACK_ANGLES = {
            0.0,
            Math.PI * 0.38,
            Math.PI * 0.72,
            Math.PI,
            Math.PI * 1.30,
            Math.PI * 1.75
    };

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public RuinRitual(PlayerEntity player, RitualManager.RitualType type) {
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
       HELPERS
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
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.6f, 0.5f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();

        // spawn lines from players feet
        double crackReach = 0.8 + progress * 3.7;
        if (t % 3 == 0) {
            for (double angle : CRACK_ANGLES) {
                int steps = (int)(crackReach / 0.45);
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + s * 0.45;
                    // different particles for each line
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE : PURPLE_DARK;
                    // jitter to line
                    double jitter = (world.random.nextDouble() - 0.5) * 0.18;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.04,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.02, 0.01, 0.02, 0.002);
                }
                // brighter at end
                if (t % 6 == 0) {
                    world.spawnParticles(PURPLE_MID,
                            pos.x + Math.cos(angle) * crackReach,
                            pos.y + 0.1,
                            pos.z + Math.sin(angle) * crackReach,
                            1, 0.05, 0.04, 0.05, 0.012);
                }
            }
        }

        // darker dust from particles
        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double crackAngle = CRACK_ANGLES[world.random.nextInt(CRACK_ANGLES.length)];
                double d          = 0.5 + world.random.nextDouble() * crackReach * 0.8;
                world.spawnParticles(PURPLE_DARK,
                        pos.x + Math.cos(crackAngle) * d,
                        pos.y + 0.05,
                        pos.z + Math.sin(crackAngle) * d,
                        1, 0.01, 0.06, 0.01, 0.008);
            }
        }

        // ash
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + world.random.nextDouble() * 3.5;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 2.5 + world.random.nextDouble() * 1.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.01, 0.02, 0.005);
            }
        }

        // blood around
        if (progress > 0.55f && t % 6 == 0) {
            world.spawnParticles(RED,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.8,
                    pos.y + 0.05,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.8,
                    1, 0.02, 0.02, 0.02, 0.005);
        }

        if (t % 22 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                    SoundCategory.PLAYERS, 0.4f, 0.4f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.7f, 0.6f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // make a sigil and spin it
        double sigRotation = time * 0.025;    // slow clockwise drift
        double sigRadius   = 2.8 + Math.sin(time * 0.04) * 0.2;   // slight breathing pulse

        if (t % 2 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;

                int spokeSteps = 7;
                for (int s = 0; s < spokeSteps; s++) {
                    double d   = 0.4 + s * (sigRadius / spokeSteps);
                    DustParticleEffect col = (s < 3) ? PURPLE_DARK : PURPLE;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d,
                            pos.y + 0.06,
                            pos.z + Math.sin(angle) * d,
                            1, 0.02, 0.01, 0.02, 0.003);
                }
                // line tip
                world.spawnParticles(PURPLE_MID,
                        pos.x + Math.cos(angle) * sigRadius,
                        pos.y + 0.08,
                        pos.z + Math.sin(angle) * sigRadius,
                        1, 0.04, 0.02, 0.04, 0.008);
            }

            // connect tips in ring
            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                world.spawnParticles(PURPLE,
                        pos.x + Math.cos(angle) * sigRadius,
                        pos.y + 0.06,
                        pos.z + Math.sin(angle) * sigRadius,
                        1, 0.01, 0.01, 0.01, 0.002);
            }
        }

        // pillars from each tip
        double columnHeight = 2.5 + progress * 5.5;
        if (t % 3 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx    = pos.x + Math.cos(angle) * sigRadius;
                double cz    = pos.z + Math.sin(angle) * sigRadius;

                int colSteps = (int)(columnHeight / 0.55) + 1;
                for (int s = 0; s < colSteps; s++) {
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                    world.spawnParticles(col,
                            cx + (world.random.nextDouble() - 0.5) * 0.3,
                            pos.y + s * 0.55,
                            cz + (world.random.nextDouble() - 0.5) * 0.3,
                            1, 0.02, 0.03, 0.02, 0.007);
                }
            }
        }

        // spores emitted
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.2;
                world.spawnParticles(ParticleTypes.WARPED_SPORE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * columnHeight,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.006);
            }
        }

        // more blood
        if (t % 4 == 0 && progress > 0.3f) {
            for (double baseAngle : CRACK_ANGLES) {
                if (!world.random.nextBoolean()) continue;
                double angle = baseAngle + sigRotation;
                world.spawnParticles(RED,
                        pos.x + Math.cos(angle) * sigRadius,
                        pos.y + columnHeight,
                        pos.z + Math.sin(angle) * sigRadius,
                        1, 0.05, -0.04, 0.05, 0.01);
            }
        }

        if (t == 18) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_WITHER_SHOOT,      SoundCategory.PLAYERS, 0.4f, 0.7f);
        if (t == 40) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.5f, 0.7f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 0.6f, 0.8f);
        }

        double progress  = (double) t / STAGE_3_TICKS;
        Vec3d  pos       = sp.getPos();
        long   time      = world.getTime();

        // shrink and lift sigil
        double sigRotation = time * 0.025;
        double sigRadius   = 2.8 * (1.0 - progress * 0.75);
        double sigHeight   = progress * 1.4;    // go towards players chest

        if (t % 3 == 0 && sigRadius > 0.3) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;
                world.spawnParticles(PURPLE,
                        pos.x + Math.cos(angle) * sigRadius,
                        pos.y + sigHeight,
                        pos.z + Math.sin(angle) * sigRadius,
                        1, 0.03, 0.02, 0.03, 0.005);
            }
            int ringPoints = 24;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                world.spawnParticles(PURPLE_DARK,
                        pos.x + Math.cos(angle) * sigRadius,
                        pos.y + sigHeight,
                        pos.z + Math.sin(angle) * sigRadius,
                        1, 0.01, 0.01, 0.01, 0.002);
            }
        }

        // pull particles towards player
        if (t % 2 == 0) {
            int pullCount = (int)(6 + progress * 10);
            for (int i = 0; i < pullCount; i++) {
                double angle  = world.random.nextDouble() * Math.PI * 2;
                double elev   = (world.random.nextDouble() - 0.3) * Math.PI;
                double srcR   = 3.0 + world.random.nextDouble() * 2.0;
                double srcY   = pos.y + 1.0 + Math.sin(elev) * 2.0;
                Vec3d  from   = new Vec3d(
                        pos.x + Math.cos(angle) * Math.cos(elev) * srcR,
                        srcY,
                        pos.z + Math.sin(angle) * Math.cos(elev) * srcR);
                Vec3d  toward = pos.add(0, 1, 0)
                        .subtract(from)
                        .normalize()
                        .multiply(0.08 + progress * 0.06);
                // downward bias
                toward = toward.add(0, -0.03, 0);

                DustParticleEffect col;
                int choice = world.random.nextInt(4);
                col = switch (choice) {
                    case 0  -> PURPLE;
                    case 1  -> PURPLE_DARK;
                    case 2  -> RED;
                    default -> PURPLE_MID;
                };
                world.spawnParticles(col,
                        from.x, from.y, from.z,
                        1, toward.x, toward.y, toward.z, 0.012);
            }
        }

        // remove columns
        if (t % 4 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle        = baseAngle + sigRotation;
                double columnRadius = 2.8 * (1.0 - progress * 0.6);
                double dropHeight   = 7.0 * (1.0 - progress * 0.7);
                if (dropHeight < 0.5) continue;
                world.spawnParticles(PURPLE_DARK,
                        pos.x + Math.cos(angle) * columnRadius,
                        pos.y + dropHeight,
                        pos.z + Math.sin(angle) * columnRadius,
                        1, 0.02, -0.05, 0.02, 0.008);
            }
        }

        // more ash
        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 3.0;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 1.0 + world.random.nextDouble() * 3.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.02, 0.01, 0.004);
            }
        }

        if (t % 14 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS,
                    (float)(0.4 + progress * 0.4), 0.6f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_SPAWN,       SoundCategory.PLAYERS, 1.0f, 0.7f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ITEM_TOTEM_USE,            SoundCategory.PLAYERS, 1.0f, 0.8f);

            // fire particles inward
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                double srcX  = pos.x + Math.cos(angle) * 3.0;
                double srcZ  = pos.z + Math.sin(angle) * 3.0;
                Vec3d toward = pos.add(0, 1, 0)
                        .subtract(srcX, pos.y + 1.0, srcZ)
                        .normalize().multiply(0.15);
                world.spawnParticles(PURPLE,
                        srcX, pos.y + 1.0, srcZ,
                        4, toward.x, toward.y, toward.z, 0.0);
            }
            world.spawnParticles(RED, pos.x, pos.y + 1.0, pos.z,
                    14, 1.3, 1.1, 1.3, 0.11);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // Blindness
        if (progress > 0.20f && progress < 0.80f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 25, 0, true, false, false));
        }

        // Damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // soul fire
        if (t % 2 == 0) {
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.6,
                    pos.y + world.random.nextDouble() * 2.2,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.6,
                    1, 0.04, 0.06, 0.04, 0.015);
        }

        // cloud burst
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        if (t % 2 == 0) {
            for (int i = 0; i < burstCount; i++) {
                DustParticleEffect col = (i % 3 == 0) ? RED_DARK : PURPLE_DARK;
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                double h     = world.random.nextDouble() * 2.5;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                        1, 0, 0.02, 0, 0.016);
            }
        }

        // ash
        if (progress > 0.20f && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 3.5;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 4.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.02, 0.01, 0.003);
            }
        }

        // calming
        if (progress > 0.78f) {
            world.spawnParticles(PURPLE_MID,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
            world.spawnParticles(RED,
                    pos.x, pos.y + 0.8, pos.z, 1, 0.4, 0.2, 0.4, 0.004);
        }

        if (t % 6 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS,
                    0.3f + (float) progress * 0.2f, 0.5f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory =
                RUIN_POWERS.get(sp.getRandom().nextInt(RUIN_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
        sp.removeStatusEffect(StatusEffects.WITHER);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.extinguish();
        sp.heal(3.0f);

        Vec3d pos = sp.getPos();
        world.spawnParticles(PURPLE,  pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        world.spawnParticles(RED, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
        world.spawnParticles(PURPLE_DARK,  pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.6, 0.8, 0.06);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                10, 0.8, 1.0, 0.8, 0.15);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 0.8f, 0.7f);
    }
}