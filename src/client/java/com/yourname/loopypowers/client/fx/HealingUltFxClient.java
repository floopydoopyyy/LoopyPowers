package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

/**
 * Five distinct visual identities — one per HP phase — plus a burst
 * effect on every phase transition so the shift is unmistakeable.
 *
 *  Phase 5  OVERFLOW    — Double helix + rotating crown + golden stars
 *  Phase 4  EXCEPTIONAL — Orbiting ring + END_ROD scatter + rising wisps
 *  Phase 3  EQUILIBRIUM — Steady orbit + calm firework wisps
 *  Phase 2  INADEQUATE  — Urgent pulsing clusters + enchanted sparks
 *  Phase 1  EXPOSED     — Burst, sparks, ground stuff
 */
@Environment(EnvType.CLIENT)
public final class HealingUltFxClient {

    private HealingUltFxClient() {}

    private static final DustParticleEffect HEAL_DUST =
            new DustParticleEffect(new Vector3f(0.9f, 0.2f, 0.4f), 1.2f);

    private static final DustParticleEffect HEAL_DUST_BRIGHT =
            new DustParticleEffect(new Vector3f(1.0f, 0.45f, 0.65f), 1.8f);

    // ----------------------------------------------------------------
    // PUBLIC ENTRY POINT
    // ----------------------------------------------------------------

    public static void onUltTick(ClientWorld world, double cx, double cy, double cz,
                                 int phase, int ultTicks, boolean isPhaseChange) {
        // Angle seed advances every tick — drives all rotating patterns
        double angle = ultTicks * 0.22;

        // One-time burst on phase transition
        if (isPhaseChange) {
            spawnPhaseChangeBurst(world, cx, cy, cz, phase);
        }

        // Continuous per-tick FX
        switch (phase) {
            case 5 -> tickOverflow(world, cx, cy, cz, angle, ultTicks);
            case 4 -> tickExceptional(world, cx, cy, cz, angle, ultTicks);
            case 3 -> tickEquilibrium(world, cx, cy, cz, angle, ultTicks);
            case 2 -> tickInadequate(world, cx, cy, cz, angle, ultTicks);
            case 1 -> tickExposed(world, cx, cy, cz, angle, ultTicks);
        }
    }

    // ----------------------------------------------------------------
    // PER-PHASE TICK FX
    // ----------------------------------------------------------------

    private static void tickOverflow(ClientWorld world, double cx, double cy, double cz,
                                     double angle, int ticks) {
        for (int arm = 0; arm < 2; arm++) {
            double a = angle + arm * Math.PI;
            double heightOff = Math.sin(a * 1.6) * 0.55;
            double sx = cx + Math.cos(a) * 1.0;
            double sy = cy + heightOff;
            double sz = cz + Math.sin(a) * 1.0;
            world.addParticle(HEAL_DUST_BRIGHT, sx, sy, sz, 0, 0.04, 0);
        }

        for (int i = 0; i < 3; i++) {
            double a = -angle * 0.55 + (2 * Math.PI / 3.0) * i;
            double sx = cx + Math.cos(a) * 0.65;
            double sz = cz + Math.sin(a) * 0.65;
            world.addParticle(ParticleTypes.END_ROD, sx, cy + 1.25, sz, 0, 0.025, 0);
        }

        if (ticks % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                world.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        cx + (Math.random() - 0.5) * 0.5,
                        cy + (Math.random() - 0.5) * 0.5,
                        cz + (Math.random() - 0.5) * 0.5,
                        (Math.random() - 0.5) * 0.22,
                        Math.random() * 0.18 + 0.04,
                        (Math.random() - 0.5) * 0.22);
            }
        }
    }

    private static void tickExceptional(ClientWorld world, double cx, double cy, double cz,
                                        double angle, int ticks) {
        spawnOrbitRing(world, cx, cy, cz, 0.85, 5, angle, HEAL_DUST_BRIGHT, 0.012);

        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.7,
                    cy + (Math.random() - 0.5) * 0.6,
                    cz + (Math.random() - 0.5) * 0.7,
                    (Math.random() - 0.5) * 0.14,
                    Math.random() * 0.12 + 0.02,
                    (Math.random() - 0.5) * 0.14);
        }

        if (ticks % 4 == 0) {
            world.addParticle(ParticleTypes.FIREWORK,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy - 0.3,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.055, 0);
        }
    }

    private static void tickEquilibrium(ClientWorld world, double cx, double cy, double cz,
                                        double angle, int ticks) {
        spawnOrbitRing(world, cx, cy, cz, 0.72, 4, angle * 0.8, HEAL_DUST, 0.008);

        if (ticks % 3 == 0) {
            for (int i = 0; i < 2; i++) {
                world.addParticle(ParticleTypes.FIREWORK,
                        cx + (Math.random() - 0.5) * 0.55,
                        cy + (Math.random() - 0.5) * 0.55,
                        cz + (Math.random() - 0.5) * 0.55,
                        (Math.random() - 0.5) * 0.04, 0.03, (Math.random() - 0.5) * 0.04);
            }
        }

        if (ticks % 5 == 0) {
            world.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy + 0.35,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.035, 0);
        }
    }

    private static void tickInadequate(ClientWorld world, double cx, double cy, double cz,
                                       double angle, int ticks) {
        double pulse = 0.03 + Math.abs(Math.sin(ticks * 0.28)) * 0.07;

        for (int i = 0; i < 4; i++) {
            world.addParticle(HEAL_DUST,
                    cx + (Math.random() - 0.5) * 0.45,
                    cy + (Math.random() - 0.5) * 0.55,
                    cz + (Math.random() - 0.5) * 0.45,
                    (Math.random() - 0.5) * pulse * 3.5,
                    (Math.random() * 0.5 - 0.1) * pulse * 2.5,
                    (Math.random() - 0.5) * pulse * 3.5);
        }

        for (int i = 0; i < 2; i++) {
            world.addParticle(ParticleTypes.ENCHANTED_HIT,
                    cx + (Math.random() - 0.5) * 0.6,
                    cy + (Math.random() - 0.5) * 0.65,
                    cz + (Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.14,
                    (Math.random() - 0.5) * 0.14,
                    (Math.random() - 0.5) * 0.14);
        }

        if (ticks % 3 == 0) {
            world.addParticle(ParticleTypes.FIREWORK,
                    cx + (Math.random() - 0.5) * 0.45,
                    cy + (Math.random() - 0.5) * 0.45,
                    cz + (Math.random() - 0.5) * 0.45,
                    (Math.random() - 0.5) * 0.08, 0.04, (Math.random() - 0.5) * 0.08);
        }
    }

    private static void tickExposed(ClientWorld world, double cx, double cy, double cz,
                                    double angle, int ticks) {
        for (int i = 0; i < 6; i++) {
            world.addParticle(HEAL_DUST_BRIGHT,
                    cx + (Math.random() - 0.5) * 0.6,
                    cy + (Math.random() - 0.5) * 0.75,
                    cz + (Math.random() - 0.5) * 0.6,
                    (Math.random() - 0.5) * 0.22,
                    (Math.random() - 0.3) * 0.18,
                    (Math.random() - 0.5) * 0.22);
        }

        for (int i = 0; i < 4; i++) {
            world.addParticle(ParticleTypes.ENCHANTED_HIT,
                    cx + (Math.random() - 0.5) * 0.75,
                    cy + (Math.random() - 0.5) * 0.85,
                    cz + (Math.random() - 0.5) * 0.75,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2,
                    (Math.random() - 0.5) * 0.2);
        }

        if (ticks % 2 == 0) {
            world.addParticle(ParticleTypes.END_ROD,
                    cx + (Math.random() - 0.5) * 0.35,
                    cy + (Math.random() - 0.5) * 0.45,
                    cz + (Math.random() - 0.5) * 0.35,
                    (Math.random() - 0.5) * 0.14,
                    Math.random() * 0.1,
                    (Math.random() - 0.5) * 0.14);
        }

        if (ticks % 4 == 0) {
            world.addParticle(ParticleTypes.POOF,
                    cx + (Math.random() - 0.5) * 0.4,
                    cy - 0.75,
                    cz + (Math.random() - 0.5) * 0.4,
                    0, 0.03, 0);
        }
    }

    // ----------------------------------------------------------------
    // PHASE-CHANGE BURSTS
    // ----------------------------------------------------------------

    private static void spawnPhaseChangeBurst(ClientWorld world, double cx, double cy, double cz, int phase) {
        switch (phase) {
            case 5 -> {
                spawnRadialBurst(world, cx, cy, cz, 30, 0.32, HEAL_DUST_BRIGHT);
                spawnRadialBurst(world, cx, cy, cz, 14, 0.28, ParticleTypes.TOTEM_OF_UNDYING);
                spawnHorizontalRing(world, cx, cy - 0.3, cz, 2.0, 20, ParticleTypes.END_ROD, 0.18);
                for (int i = 0; i < 5; i++) {
                    world.addParticle(ParticleTypes.FLASH,
                            cx + (Math.random() - 0.5) * 1.4,
                            cy + (Math.random() - 0.5) * 1.0,
                            cz + (Math.random() - 0.5) * 1.4,
                            0, 0, 0);
                }
            }
            case 4 -> {
                spawnRadialBurst(world, cx, cy, cz, 22, 0.28, HEAL_DUST_BRIGHT);
                spawnRadialBurst(world, cx, cy, cz, 16, 0.24, ParticleTypes.END_ROD);
                spawnHorizontalRing(world, cx, cy - 0.3, cz, 1.5, 16, HEAL_DUST, 0.14);
            }
            case 3 -> {
                spawnHorizontalRing(world, cx, cy, cz, 1.2, 14, HEAL_DUST, 0.09);
                spawnRadialBurst(world, cx, cy, cz, 10, 0.18, ParticleTypes.FIREWORK);
            }
            case 2 -> {
                spawnHorizontalRing(world, cx, cy, cz, 1.0, 12, ParticleTypes.ENCHANTED_HIT, 0.11);
                spawnRadialBurst(world, cx, cy, cz, 16, 0.2, HEAL_DUST);
            }
            case 1 -> {
                spawnRadialBurst(world, cx, cy, cz, 26, 0.3, HEAL_DUST_BRIGHT);
                spawnRadialBurst(world, cx, cy, cz, 12, 0.25, ParticleTypes.ENCHANTED_HIT);
                spawnHorizontalRing(world, cx, cy - 0.3, cz, 1.8, 18, HEAL_DUST, 0.2);
                for (int i = 0; i < 8; i++) {
                    world.addParticle(ParticleTypes.FLASH,
                            cx + (Math.random() - 0.5) * 1.6,
                            cy + Math.random() * 1.0,
                            cz + (Math.random() - 0.5) * 1.6,
                            0, 0, 0);
                }
                world.addParticle(ParticleTypes.POOF,
                        cx, cy - 0.6, cz, 0, 0, 0);
            }
        }
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------

    private static void spawnOrbitRing(ClientWorld world,
                                       double cx, double cy, double cz,
                                       double radius, int count, double angleOffset,
                                       ParticleEffect particle, double vy) {
        for (int i = 0; i < count; i++) {
            double a = angleOffset + (2 * Math.PI / count) * i;
            world.addParticle(particle,
                    cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius,
                    0, vy, 0);
        }
    }

    private static void spawnHorizontalRing(ClientWorld world,
                                            double cx, double cy, double cz,
                                            double radius, int count,
                                            ParticleEffect particle, double outSpeed) {
        for (int i = 0; i < count; i++) {
            double a = (2 * Math.PI / count) * i;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            world.addParticle(particle,
                    cx + cos * radius, cy, cz + sin * radius,
                    cos * outSpeed, 0.02, sin * outSpeed);
        }
    }

    private static void spawnRadialBurst(ClientWorld world,
                                         double cx, double cy, double cz,
                                         int count, double speed,
                                         ParticleEffect particle) {
        for (int i = 0; i < count; i++) {
            double theta = Math.random() * 2 * Math.PI;
            double phi   = Math.acos(2 * Math.random() - 1);
            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.cos(phi) * speed * 0.65;
            double vz = Math.sin(phi) * Math.sin(theta) * speed;
            world.addParticle(particle, cx, cy, cz, vx, vy, vz);
        }
    }
}
