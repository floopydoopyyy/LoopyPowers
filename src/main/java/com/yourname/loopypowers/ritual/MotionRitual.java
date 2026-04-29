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

public class MotionRitual implements Ritual {

       // POWERS

    private static final List<Supplier<Power>> MOTION_POWERS = List.of(
            SpeedPower::new,
            SoundPower::new,
            FlightPower::new
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

    // dark blue
    private static final DustParticleEffect BLUE_DEEP =
            new DustParticleEffect(new Vector3f(0.05f, 0.25f, 0.85f), 1.4f);
    // light blue
    private static final DustParticleEffect BLUE_LIGHT =
            new DustParticleEffect(new Vector3f(0.15f, 0.60f, 1.00f), 1.3f);
    // cyan
    private static final DustParticleEffect CYAN_BRIGHT =
            new DustParticleEffect(new Vector3f(0.20f, 0.90f, 1.00f), 1.1f);
    // bluey white
    private static final DustParticleEffect BLUE_PALE =
            new DustParticleEffect(new Vector3f(0.70f, 0.85f, 1.00f), 1.0f);
    // navy
    private static final DustParticleEffect NAVY =
            new DustParticleEffect(new Vector3f(0.00f, 0.08f, 0.35f), 1.5f);

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public MotionRitual(PlayerEntity player, RitualManager.RitualType type) {
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
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.7f, 0.6f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();

        // rings at feet that expand
        int    pulseTimer  = t % 12;
        double shockRadius = pulseTimer * 0.35;    // 0 to 4 blocks over 12 ticks
        if (shockRadius > 0.3 && t % 2 == 0) {
            int shockPoints = 14;
            for (int i = 0; i < shockPoints; i++) {
                double angle = Math.PI * 2.0 * i / shockPoints;
                DustParticleEffect col = (pulseTimer < 6) ? BLUE_LIGHT : BLUE_DEEP;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * shockRadius,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * shockRadius,
                        1, 0.04, 0.02, 0.04, 0.003);
            }
        }

        // rays from feet
        if (t % 5 == 0) {
            int    rays = 8;
            double maxR = 1.5 + progress * 1.5;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays;
                for (double d = 0.4; d <= maxR; d += 0.5) {
                    world.spawnParticles(BLUE_DEEP,
                            pos.x + Math.cos(angle) * d,
                            pos.y + 0.1,
                            pos.z + Math.sin(angle) * d,
                            1, 0.01, 0.02, 0.01, 0.015);
                }
            }
        }

        // trail
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double h = world.random.nextDouble() * (1.5 + progress * 1.5);
                world.spawnParticles(BLUE_LIGHT,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                        pos.y + h,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                        1, 0.02, 0.04, 0.02, 0.010);
            }
        }

        // this looked better in my head
        // rarer sweep attacks
        if (t % 3 == 0) {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + 0.5 + world.random.nextDouble(),
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 1);
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ITEM_ELYTRA_FLYING,
                    SoundCategory.PLAYERS, 0.3f, 0.5f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 0.7f, 0.5f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // Four orbital rings orbiting at different radii, heights, and speeds.
        // using the orbit angle as input — this tilts the ring out of the horizontal
        // plane and makes it appear as a 3D angled orbit rather than a flat disc.
        // Columns: { radius, heightOffset, angularSpeedPerTick, yWobbleAmplitude, wobbleFreqMultiplier }
        double[][] rings = {
                { 2.5, 0.4,  0.08, 0.00, 0 },   // straight outer ring
                { 1.8, 1.0, -0.12, 0.55, 1 },   // tilted mid ring
                { 2.1, 0.7,  0.06, 0.00, 0 },   // flat medium ring
                { 1.3, 1.5, -0.10, 0.70, 2 },   // steeply tilted head ring
        };
        DustParticleEffect[] palette = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };

        // Arc trail - spawn only a swept arc (not complete circle)
        int arcPoints = (int)(8 + progress * 10);

        for (int ri = 0; ri < rings.length; ri++) {
            double[] ring     = rings[ri];
            double   r        = ring[0];
            double   baseY    = pos.y + ring[1];
            double   speed    = ring[2];
            double   wobble   = ring[3];
            double   wobFreq  = ring[4];
            DustParticleEffect col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                // Head of the arc at i=0, tail at i=arcPoints-1.
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y     = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);

                if (t % 2 == 0 || i < 4) {
                    world.spawnParticles(col,
                            pos.x + Math.cos(theta) * r,
                            y,
                            pos.z + Math.sin(theta) * r,
                            1, 0.03, 0.02, 0.03, 0.005);
                }

                // put a darker shadow behind the arc
                if (i == arcPoints - 1 && t % 3 == 0) {
                    world.spawnParticles(NAVY,
                            pos.x + Math.cos(theta - 0.15) * r,
                            y,
                            pos.z + Math.sin(theta - 0.15) * r,
                            1, 0.04, 0.02, 0.04, 0.003);
                }
            }
        }

        // little burst between passes - not really noticable
        if (t % 8 == 0) {
            int    rays = 6;
            double gyroTime = time * 0.03;
            for (int r = 0; r < rays; r++) {
                double angle = r * Math.PI * 2.0 / rays + gyroTime;
                world.spawnParticles(BLUE_LIGHT,
                        pos.x + Math.cos(angle) * 1.2,
                        pos.y + 0.5,
                        pos.z + Math.sin(angle) * 1.2,
                        1, Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05, 0.02);
            }
        }

        if (t == 20) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_ARROW_SHOOT,  SoundCategory.PLAYERS, 0.5f, 1.4f);
        if (t == 40) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ITEM_ELYTRA_FLYING,  SoundCategory.PLAYERS, 0.5f, 1.2f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 1.5f);
        }

        double progress  = (double) t / STAGE_3_TICKS;
        Vec3d  pos       = sp.getPos();
        long   time      = world.getTime();
        double shrink    = 1.0 - progress * 0.65;
        double speedMult = 1.0 + progress * 1.5;

        double[][] rings = {
                { 2.5 * shrink, 0.4,  0.08 * speedMult, 0.00, 0 },
                { 1.8 * shrink, 1.0, -0.12 * speedMult, 0.55, 1 },
                { 2.1 * shrink, 0.7,  0.06 * speedMult, 0.00, 0 },
                { 1.3 * shrink, 1.5, -0.10 * speedMult, 0.70, 2 },
        };
        DustParticleEffect[] palette = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };

        int arcPoints = (int)(12 + progress * 8);

        for (int ri = 0; ri < rings.length; ri++) {
            double[] ring   = rings[ri];
            double   r      = ring[0];
            double   baseY  = pos.y + ring[1];
            double   speed  = ring[2];
            double   wobble = ring[3];
            double   wobFreq = ring[4];
            DustParticleEffect col = palette[ri];

            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double y     = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);
                if (t % 2 == 0) {
                    world.spawnParticles(col,
                            pos.x + Math.cos(theta) * r,
                            y,
                            pos.z + Math.sin(theta) * r,
                            1, 0.04, 0.03, 0.04, 0.008);
                }
            }
        }

        // tighten it to shrink towards player
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle  = world.random.nextDouble() * Math.PI * 2;
                double srcR   = 3.5 + world.random.nextDouble() * 1.5;
                double srcY   = pos.y + world.random.nextDouble() * 2.0;
                Vec3d  from   = new Vec3d(
                        pos.x + Math.cos(angle) * srcR, srcY, pos.z + Math.sin(angle) * srcR);
                Vec3d toward  = pos.add(0, 1, 0).subtract(from)
                        .normalize().multiply(0.10 + progress * 0.06);
                DustParticleEffect col = world.random.nextBoolean() ? BLUE_LIGHT : CYAN_BRIGHT;
                world.spawnParticles(col,
                        from.x, from.y, from.z,
                        1, toward.x, toward.y, toward.z, 0.014);
            }
        }

        // Horizontal shockwave rings at waist and knee
        if (t % 10 == 0) {
            for (double shockY : new double[]{ 0.5, 1.3 }) {
                int shockPoints = 16;
                for (int i = 0; i < shockPoints; i++) {
                    double angle = Math.PI * 2.0 * i / shockPoints;
                    double vel   = 0.06 + progress * 0.04;
                    world.spawnParticles(CYAN_BRIGHT,
                            pos.x + Math.cos(angle) * 0.3,
                            pos.y + shockY,
                            pos.z + Math.sin(angle) * 0.3,
                            1, Math.cos(angle) * vel, 0.005, Math.sin(angle) * vel, 0.0);
                }
            }
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS,
                    (float)(0.4 + progress * 0.3), 1.3f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,      SoundCategory.PLAYERS, 0.8f, 1.6f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.3f);

            // particles fired in rays from the player
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                for (double r = 0.5; r <= 3.0; r += 0.5) {
                    world.spawnParticles(BLUE_LIGHT,
                            pos.x + Math.cos(angle) * r, pos.y + 0.5, pos.z + Math.sin(angle) * r,
                            1, Math.cos(angle) * 0.08, 0.02, Math.sin(angle) * 0.08, 0.0);
                }
            }
            world.spawnParticles(CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z,
                    16, 1.5, 1.2, 1.5, 0.12);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z,
                    4, 0.8, 0.5, 0.8, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // effects
        if (progress > 0.15f && progress < 0.82f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 25, 0, true, false, false));
        }
        // damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // rotate rays around the player
        int rayCount = (int)(12 + (1.0 - progress) * 12);
        if (t % 2 == 0) {
            for (int r = 0; r < rayCount; r++) {
                double angle = r * Math.PI * 2.0 / rayCount + (ticks * 0.07);
                double vel   = 0.08 + (1.0 - progress) * 0.05;
                double h     = 0.3 + world.random.nextDouble() * 2.0;
                DustParticleEffect col = switch (r % 3) {
                    case 0  -> BLUE_DEEP;
                    case 1  -> BLUE_LIGHT;
                    default -> CYAN_BRIGHT;
                };
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * 0.5,
                        pos.y + h,
                        pos.z + Math.sin(angle) * 0.5,
                        1, Math.cos(angle) * vel, 0.01, Math.sin(angle) * vel, 0.0);
            }
        }

        // more sweeps
        if (t % 6 == 0) {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + 0.5 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0, 0, 0, 1);
        }

        // calm it
        if (progress > 0.80f) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticles(BLUE_PALE,
                        pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.004);
                world.spawnParticles(NAVY,
                        pos.x, pos.y + 0.5, pos.z, 1, 0.4, 0.2, 0.4, 0.003);
            }
        }

        if (t % 5 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ITEM_ELYTRA_FLYING,
                    SoundCategory.PLAYERS,
                    0.5f + (float) progress * 0.3f, 1.5f + (float) progress * 0.5f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory =
                MOTION_POWERS.get(sp.getRandom().nextInt(MOTION_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.extinguish();
        sp.heal(3.0f);

        Vec3d pos = sp.getPos();

        // final fx
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI * 2.0 / 8;
            world.spawnParticles(BLUE_LIGHT,
                    pos.x + Math.cos(angle) * 0.5, pos.y + 1.0, pos.z + Math.sin(angle) * 0.5,
                    4, Math.cos(angle) * 0.10, 0.05, Math.sin(angle) * 0.10, 0.0);
        }
        world.spawnParticles(CYAN_BRIGHT, pos.x, pos.y + 1.0, pos.z, 12, 1.2, 1.0, 1.2, 0.09);
        world.spawnParticles(BLUE_PALE,   pos.x, pos.y + 1.0, pos.z,  8, 0.8, 0.7, 0.8, 0.05);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + 1.0, pos.z,
                3, 0.5, 0.3, 0.5, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 0.9f);
    }
}