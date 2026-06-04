package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class RuinRitualFxClient {

    private RuinRitualFxClient() {}

    private static final DustParticleEffect PURPLE      = new DustParticleEffect(new Vector3f(0.38f, 0.00f, 0.55f), 1.5f);
    private static final DustParticleEffect PURPLE_DARK = new DustParticleEffect(new Vector3f(0.18f, 0.00f, 0.28f), 1.6f);
    private static final DustParticleEffect PURPLE_MID  = new DustParticleEffect(new Vector3f(0.62f, 0.10f, 0.82f), 1.3f);
    private static final DustParticleEffect RED         = new DustParticleEffect(new Vector3f(0.62f, 0.00f, 0.08f), 1.4f);
    private static final DustParticleEffect RED_DARK    = new DustParticleEffect(new Vector3f(0.30f, 0.00f, 0.04f), 1.6f);

    private static final double[] CRACK_ANGLES = {
            0.0, Math.PI * 0.38, Math.PI * 0.72, Math.PI, Math.PI * 1.30, Math.PI * 1.75
    };

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress, time);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress, time);
            case 4 -> renderStage4(world, x, y, z, t, progress);
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double crackReach = 0.8 + progress * 3.7;
        if (t % 3 == 0) {
            for (double angle : CRACK_ANGLES) {
                int steps = (int)(crackReach / 0.45);
                for (int s = 0; s < steps; s++) {
                    double d      = 0.3 + s * 0.45;
                    double jitter = (w.random.nextDouble() - 0.5) * 0.18;
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE : PURPLE_DARK;
                    w.addParticle(col,
                            x + Math.cos(angle) * d + Math.cos(angle + Math.PI * 0.5) * jitter,
                            y + 0.04,
                            z + Math.sin(angle) * d + Math.sin(angle + Math.PI * 0.5) * jitter,
                            0, 0.002, 0);
                }
                if (t % 6 == 0)
                    w.addParticle(PURPLE_MID, x + Math.cos(angle) * crackReach, y + 0.1, z + Math.sin(angle) * crackReach, 0, 0.012, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                int    idx   = w.random.nextInt(CRACK_ANGLES.length);
                double d     = 0.5 + w.random.nextDouble() * crackReach * 0.8;
                w.addParticle(PURPLE_DARK, x + Math.cos(CRACK_ANGLES[idx]) * d, y + 0.05, z + Math.sin(CRACK_ANGLES[idx]) * d, 0, 0.008, 0);
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + w.random.nextDouble() * 3.5;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 2.5 + w.random.nextDouble() * 1.5, z + Math.sin(angle) * r, 0, -0.005, 0);
            }
        }
        if (progress > 0.55f && t % 6 == 0)
            w.addParticle(RED, x + (w.random.nextDouble() - 0.5) * 1.8, y + 0.05, z + (w.random.nextDouble() - 0.5) * 1.8, 0, 0.005, 0);
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double sigRotation = time * 0.025;
        double sigRadius   = 2.8 + Math.sin(time * 0.04) * 0.2;
        if (t % 2 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;
                int spokeSteps = 7;
                for (int s = 0; s < spokeSteps; s++) {
                    double d   = 0.4 + s * (sigRadius / spokeSteps);
                    DustParticleEffect col = (s < 3) ? PURPLE_DARK : PURPLE;
                    w.addParticle(col, x + Math.cos(angle) * d, y + 0.06, z + Math.sin(angle) * d, 0, 0.003, 0);
                }
                w.addParticle(PURPLE_MID, x + Math.cos(angle) * sigRadius, y + 0.08, z + Math.sin(angle) * sigRadius, 0, 0.008, 0);
            }
            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                w.addParticle(PURPLE, x + Math.cos(angle) * sigRadius, y + 0.06, z + Math.sin(angle) * sigRadius, 0, 0.002, 0);
            }
        }
        double columnHeight = 2.5 + progress * 5.5;
        if (t % 3 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx    = x + Math.cos(angle) * sigRadius;
                double cz    = z + Math.sin(angle) * sigRadius;
                int colSteps = (int)(columnHeight / 0.55) + 1;
                for (int s = 0; s < colSteps; s++) {
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                    w.addParticle(col,
                            cx + (w.random.nextDouble() - 0.5) * 0.3, y + s * 0.55, cz + (w.random.nextDouble() - 0.5) * 0.3,
                            0, 0.007, 0);
                }
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.2;
                w.addParticle(ParticleTypes.WARPED_SPORE, x + Math.cos(angle) * r, y + 0.1 + w.random.nextDouble() * columnHeight, z + Math.sin(angle) * r, 0, 0.006, 0);
            }
        }
        if (t % 4 == 0 && progress > 0.3f) {
            for (double baseAngle : CRACK_ANGLES) {
                if (!w.random.nextBoolean()) continue;
                double angle = baseAngle + sigRotation;
                w.addParticle(RED, x + Math.cos(angle) * sigRadius, y + columnHeight, z + Math.sin(angle) * sigRadius, 0, -0.04, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double sigRotation = time * 0.025;
        double sigRadius   = 2.8 * (1.0 - progress * 0.75);
        double sigHeight   = progress * 1.4;
        if (t % 3 == 0 && sigRadius > 0.3) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle = baseAngle + sigRotation;
                w.addParticle(PURPLE, x + Math.cos(angle) * sigRadius, y + sigHeight, z + Math.sin(angle) * sigRadius, 0, 0.005, 0);
            }
            int ringPoints = 24;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                w.addParticle(PURPLE_DARK, x + Math.cos(angle) * sigRadius, y + sigHeight, z + Math.sin(angle) * sigRadius, 0, 0.002, 0);
            }
        }
        if (t % 2 == 0) {
            int pullCount = (int)(6 + progress * 10);
            for (int i = 0; i < pullCount; i++) {
                double angle  = w.random.nextDouble() * Math.PI * 2;
                double elev   = (w.random.nextDouble() - 0.3) * Math.PI;
                double srcR   = 3.0 + w.random.nextDouble() * 2.0;
                double srcY   = y + 1.0 + Math.sin(elev) * 2.0;
                double fromX  = x + Math.cos(angle) * Math.cos(elev) * srcR;
                double fromZ  = z + Math.sin(angle) * Math.cos(elev) * srcR;
                Vec3d  toward = new Vec3d(x, y + 1, z).subtract(fromX, srcY, fromZ).normalize().multiply(0.08 + progress * 0.06).add(0, -0.03, 0);
                DustParticleEffect col = switch (w.random.nextInt(4)) {
                    case 0  -> PURPLE;
                    case 1  -> PURPLE_DARK;
                    case 2  -> RED;
                    default -> PURPLE_MID;
                };
                w.addParticle(col, fromX, srcY, fromZ, toward.x, toward.y, toward.z);
            }
        }
        if (t % 4 == 0) {
            for (double baseAngle : CRACK_ANGLES) {
                double angle        = baseAngle + sigRotation;
                double columnRadius = 2.8 * (1.0 - progress * 0.6);
                double dropHeight   = 7.0 * (1.0 - progress * 0.7);
                if (dropHeight < 0.5) continue;
                w.addParticle(PURPLE_DARK, x + Math.cos(angle) * columnRadius, y + dropHeight, z + Math.sin(angle) * columnRadius, 0, -0.05, 0);
            }
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 3.0;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 1.0 + w.random.nextDouble() * 3.0, z + Math.sin(angle) * r, 0, -0.02, 0);
            }
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                double srcX  = x + Math.cos(angle) * 3.0;
                double srcZ  = z + Math.sin(angle) * 3.0;
                Vec3d toward = new Vec3d(x, y + 1, z).subtract(srcX, y + 1.0, srcZ).normalize().multiply(0.15);
                for (int i = 0; i < 4; i++)
                    w.addParticle(PURPLE, srcX, y + 1.0, srcZ, toward.x, toward.y, toward.z);
            }
            scatter(w, RED, x, y + 1.0, z, 14, 1.3, 1.1, 1.3, 0.11);
        }
        if (t % 2 == 0) {
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    x + (w.random.nextDouble() - 0.5) * 1.6, y + w.random.nextDouble() * 2.2, z + (w.random.nextDouble() - 0.5) * 1.6,
                    0, 0.015, 0);
        }
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        if (t % 2 == 0) {
            for (int i = 0; i < burstCount; i++) {
                DustParticleEffect col = (i % 3 == 0) ? RED_DARK : PURPLE_DARK;
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.8;
                double h     = w.random.nextDouble() * 2.5;
                w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.016, 0);
            }
        }
        if (progress > 0.20f && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 3.5;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 4.0, z + Math.sin(angle) * r, 0, -0.02, 0);
            }
        }
        if (progress > 0.78f) {
            w.addParticle(PURPLE_MID, x, y + 1.0, z, 0, 0.005, 0);
            w.addParticle(RED,        x, y + 0.8, z, 0, 0.004, 0);
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        scatter(w, PURPLE,     x, y + 1.0, z, 14, 1.2, 1.0, 1.2, 0.09);
        scatter(w, RED,        x, y + 1.0, z, 10, 1.0, 0.8, 1.0, 0.08);
        scatter(w, PURPLE_DARK, x, y + 1.0, z,  8, 0.8, 0.6, 0.8, 0.06);
        scatter(w, ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 10, 0.8, 1.0, 0.8, 0.15);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
