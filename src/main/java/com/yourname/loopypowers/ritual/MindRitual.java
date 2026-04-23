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

public class MindRitual implements Ritual {

    /* ============================================================
       MIND POWER POOL
       Add new mind powers here — nothing else needs changing.
       ============================================================ */

    private static final List<Supplier<Power>> MIND_POWERS = List.of(
            PsychicPower::new,
            DarknessPower::new,
            TelekinesisPower::new
            // TelepathyPower::new, etc.
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // Hot pink
    private static final DustParticleEffect PINK_HOT =
            new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.58f), 1.4f);
    private static final DustParticleEffect PINK_PALE =
            new DustParticleEffect(new Vector3f(1.0f, 0.60f, 0.85f), 1.1f);

    // purple
    private static final DustParticleEffect PURPLE_DEEP =
            new DustParticleEffect(new Vector3f(0.45f, 0.0f, 0.70f), 1.5f);
    private static final DustParticleEffect PURPLE_SOFT =
            new DustParticleEffect(new Vector3f(0.70f, 0.30f, 1.0f),  1.2f);

    // purple black
    private static final DustParticleEffect PURPLE_BLACK =
            new DustParticleEffect(new Vector3f(0.15f, 0.0f, 0.25f), 1.6f);

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public MindRitual(PlayerEntity player, RitualManager.RitualType type) {
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
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDERMAN_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.6f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // rings on ground
        int ringCount = 2 + (int)(progress * 2);   // grows over stages
        for (int ring = 0; ring < ringCount; ring++) {
            double phase  = (time * 0.04 + ring * 0.5) % (Math.PI * 2);
            double r      = 1.0 + ring * 0.9 + Math.sin(phase) * 0.2;
            int    points = 10 + ring * 4;
            if (t % 3 == 0) {
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    DustParticleEffect col = (ring % 2 == 0) ? PINK_HOT : PURPLE_DEEP;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * r,
                            pos.y + 0.03,
                            pos.z + Math.sin(angle) * r,
                            1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        // stuff rising
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.5;
                double h     = 0.5 + world.random.nextDouble() * 3.5 * progress;
                DustParticleEffect wisp = world.random.nextBoolean() ? PINK_PALE : PURPLE_SOFT;
                world.spawnParticles(wisp,
                        pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.05, 0.02, 0.008);
            }
        }

        // darker stuff
        if (t % 8 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = 0.5 + world.random.nextDouble() * 0.8;
            world.spawnParticles(PURPLE_BLACK,
                    pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r,
                    1, 0.01, 0.02, 0.01, 0.005);
        }

        if (t % 25 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.PLAYERS, 0.3f, 0.5f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8f, 0.7f);
        }

        double progress     = (double) t / STAGE_2_TICKS;
        Vec3d  pos          = sp.getPos();
        long   time         = world.getTime();
        double maxHeight    = 10.0 * progress;
        double orbitRadius  = 2.2;

        // three helix strands, meant to be DNA but not really
        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);

            int steps = (int)(maxHeight / 0.4) + 1;
            for (int s = 0; s < steps; s++) {
                double y = pos.y + s * 0.4;

                // Outer helix
                double outerAngle = baseAngle + s * 0.35 + time * 0.05;
                if (t % 2 == 0) {
                    DustParticleEffect col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    world.spawnParticles(col,
                            pos.x + Math.cos(outerAngle) * orbitRadius,
                            y,
                            pos.z + Math.sin(outerAngle) * orbitRadius,
                            1, 0.05, 0.03, 0.05, 0.007);
                }

                // Inner -helix
                if (t % 3 == 0 && s % 2 == 0) {
                    double innerAngle = baseAngle - s * 0.30 - time * 0.04;
                    world.spawnParticles(PURPLE_SOFT,
                            pos.x + Math.cos(innerAngle) * (orbitRadius * 0.55),
                            y + 0.1,
                            pos.z + Math.sin(innerAngle) * (orbitRadius * 0.55),
                            1, 0.03, 0.02, 0.03, 0.005);
                }
            }

            if (maxHeight > 1.5 && t % 4 == 0) {
                world.spawnParticles(PINK_HOT,
                        pos.x, pos.y + maxHeight, pos.z,
                        2, 0.5, 0.15, 0.5, 0.02);
            }
        }

        // darker from the ground
        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + world.random.nextDouble() * 2.5;
                world.spawnParticles(PURPLE_BLACK,
                        pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        if (t == 20) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,      SoundCategory.PLAYERS, 0.4f, 1.3f);
        if (t == 40) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,  SoundCategory.PLAYERS, 0.5f, 1.6f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // shrink radius, accelerate speed
        double orbitRadius = 2.2 - progress * 1.7;

        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int    steps     = (int)(10.0 * (0.5 + progress * 0.5));

            for (int s = 0; s < steps; s++) {
                if (t % 2 == 0) {
                    double spin = baseAngle + s * (0.35 + progress * 0.25)
                            + time * (0.06 + progress * 0.06);
                    double y = pos.y + s * (0.4 - progress * 0.1);

                    DustParticleEffect col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    world.spawnParticles(col,
                            pos.x + Math.cos(spin) * orbitRadius,
                            y,
                            pos.z + Math.sin(spin) * orbitRadius,
                            1, 0.04 + progress * 0.04, 0.03, 0.04 + progress * 0.04, 0.01);
                }
            }
        }

        // inner particles going towards player
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = orbitRadius + 1.5;
                Vec3d  from  = new Vec3d(
                        pos.x + Math.cos(angle) * r, pos.y + 1.0, pos.z + Math.sin(angle) * r);
                Vec3d toward = pos.add(0, 1, 0).subtract(from)
                        .normalize().multiply(0.07 + progress * 0.05);
                DustParticleEffect col = world.random.nextBoolean() ? PINK_HOT : PURPLE_BLACK;
                world.spawnParticles(col,
                        from.x, from.y, from.z,
                        1, toward.x, toward.y, toward.z, 0.012);
            }
        }

        // tightening ring
        if (t % 3 == 0) {
            double headRing = 0.8 - progress * 0.2;
            for (int i = 0; i < 8; i++) {
                double angle = time * 0.15 + i * Math.PI * 2.0 / 8;
                world.spawnParticles(PURPLE_SOFT,
                        pos.x + Math.cos(angle) * headRing, pos.y + 1.6,
                        pos.z + Math.sin(angle) * headRing,
                        1, 0.02, 0.02, 0.02, 0.008);
            }
        }

        if (t % 15 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDERMAN_AMBIENT,
                    SoundCategory.PLAYERS,
                    (float)(0.3 + progress * 0.4), 1.2f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,  SoundCategory.PLAYERS, 1.2f, 1.4f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDERMAN_SCREAM,    SoundCategory.PLAYERS, 0.8f, 0.8f);

            Vec3d pos = sp.getPos();
            world.spawnParticles(PINK_HOT,    pos.x, pos.y + 1.0, pos.z, 20, 1.5, 1.2, 1.5, 0.12);
            world.spawnParticles(PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 15, 1.2, 1.0, 1.2, 0.10);
            world.spawnParticles(PURPLE_BLACK, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.08);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.3, 0.3, 0.3, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // blindness
        if (progress > 0.10f && progress < 0.85f) {
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

        // burst
        int burstCount = (int)(10 + (1.0 - progress) * 16);
        for (int i = 0; i < burstCount; i++) {
            DustParticleEffect col = switch (i % 3) {
                case 0  -> PINK_HOT;
                case 1  -> PURPLE_DEEP;
                default -> PURPLE_BLACK;
            };
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 2.0;
            double h     = world.random.nextDouble() * 2.8;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                    1, 0, 0.02, 0, 0.018);
        }

        // emit particles
        if (t % 3 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.8,
                    pos.y + world.random.nextDouble() * 2.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.8,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        // doesnt add much
        if (t % 4 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.2,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.2,
                    2, 0.10, 0.10, 0.10, 0.02);
        }

        // calm it down
        if (progress > 0.78f) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticles(PINK_PALE,
                        pos.x, pos.y + 1.2, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
                world.spawnParticles(PURPLE_SOFT,
                        pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.004);
            }
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.25f + (float) progress * 0.15f, 1.8f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory =
                MIND_POWERS.get(sp.getRandom().nextInt(MIND_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.DARKNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.extinguish();
        sp.heal(3.0f);

        Vec3d pos = sp.getPos();
        world.spawnParticles(PINK_HOT,    pos.x, pos.y + 1.0, pos.z, 14, 1.2, 1.0, 1.2, 0.09);
        world.spawnParticles(PURPLE_DEEP, pos.x, pos.y + 1.0, pos.z, 10, 1.0, 0.8, 1.0, 0.07);
        world.spawnParticles(PINK_PALE,   pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.7, 0.8, 0.05);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.2f);
    }
}