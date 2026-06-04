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
public final class MindRitualFxClient {

    private MindRitualFxClient() {}

    private static final DustParticleEffect PINK_HOT     = new DustParticleEffect(new Vector3f(1.0f,  0.08f, 0.58f), 1.4f);
    private static final DustParticleEffect PINK_PALE    = new DustParticleEffect(new Vector3f(1.0f,  0.60f, 0.85f), 1.1f);
    private static final DustParticleEffect PURPLE_DEEP  = new DustParticleEffect(new Vector3f(0.45f, 0.0f,  0.70f), 1.5f);
    private static final DustParticleEffect PURPLE_SOFT  = new DustParticleEffect(new Vector3f(0.70f, 0.30f, 1.0f),  1.2f);
    private static final DustParticleEffect PURPLE_BLACK = new DustParticleEffect(new Vector3f(0.15f, 0.0f,  0.25f), 1.6f);

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
        int ringCount = 2 + (int)(progress * 2);
        for (int ring = 0; ring < ringCount; ring++) {
            double phase  = (time * 0.04 + ring * 0.5) % (Math.PI * 2);
            double r      = 1.0 + ring * 0.9 + Math.sin(phase) * 0.2;
            int    points = 10 + ring * 4;
            if (t % 3 == 0) {
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    DustParticleEffect col = (ring % 2 == 0) ? PINK_HOT : PURPLE_DEEP;
                    w.addParticle(col,
                            x + Math.cos(angle) * r, y + 0.03, z + Math.sin(angle) * r,
                            0, 0.003, 0);
                }
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.5;
                double h     = 0.5 + w.random.nextDouble() * 3.5 * progress;
                DustParticleEffect wisp = w.random.nextBoolean() ? PINK_PALE : PURPLE_SOFT;
                w.addParticle(wisp, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.008, 0);
            }
        }
        if (t % 8 == 0) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = 0.5 + w.random.nextDouble() * 0.8;
            w.addParticle(PURPLE_BLACK, x + Math.cos(angle) * r, y + 0.5, z + Math.sin(angle) * r, 0, 0.005, 0);
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double maxHeight   = 10.0 * progress;
        double orbitRadius = 2.2;
        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int steps = (int)(maxHeight / 0.4) + 1;
            for (int s = 0; s < steps; s++) {
                double yy = y + s * 0.4;
                double outerAngle = baseAngle + s * 0.35 + time * 0.05;
                if (t % 2 == 0) {
                    DustParticleEffect col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    w.addParticle(col, x + Math.cos(outerAngle) * orbitRadius, yy, z + Math.sin(outerAngle) * orbitRadius, 0, 0.007, 0);
                }
                if (t % 3 == 0 && s % 2 == 0) {
                    double innerAngle = baseAngle - s * 0.30 - time * 0.04;
                    w.addParticle(PURPLE_SOFT, x + Math.cos(innerAngle) * (orbitRadius * 0.55), yy + 0.1, z + Math.sin(innerAngle) * (orbitRadius * 0.55), 0, 0.005, 0);
                }
            }
            if (maxHeight > 1.5 && t % 4 == 0)
                scatter(w, PINK_HOT, x, y + maxHeight, z, 2, 0.5, 0.15, 0.5, 0.02);
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + w.random.nextDouble() * 2.5;
                w.addParticle(PURPLE_BLACK, x + Math.cos(angle) * r, y + 0.1, z + Math.sin(angle) * r, 0, 0.01, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double orbitRadius = 2.2 - progress * 1.7;
        for (int h = 0; h < 3; h++) {
            double baseAngle = h * (Math.PI * 2.0 / 3);
            int    steps     = (int)(10.0 * (0.5 + progress * 0.5));
            for (int s = 0; s < steps; s++) {
                if (t % 2 == 0) {
                    double spin = baseAngle + s * (0.35 + progress * 0.25) + time * (0.06 + progress * 0.06);
                    double yy   = y + s * (0.4 - progress * 0.1);
                    DustParticleEffect col = switch (h) {
                        case 0  -> PINK_HOT;
                        case 1  -> PURPLE_DEEP;
                        default -> PINK_PALE;
                    };
                    w.addParticle(col, x + Math.cos(spin) * orbitRadius, yy, z + Math.sin(spin) * orbitRadius, 0, 0.01, 0);
                }
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = orbitRadius + 1.5;
                Vec3d from   = new Vec3d(x + Math.cos(angle) * r, y + 1.0, z + Math.sin(angle) * r);
                Vec3d toward = new Vec3d(x, y + 1, z).subtract(from).normalize().multiply(0.07 + progress * 0.05);
                DustParticleEffect col = w.random.nextBoolean() ? PINK_HOT : PURPLE_BLACK;
                w.addParticle(col, from.x, from.y, from.z, toward.x, toward.y, toward.z);
            }
        }
        if (t % 3 == 0) {
            double headRing = 0.8 - progress * 0.2;
            for (int i = 0; i < 8; i++) {
                double angle = time * 0.15 + i * Math.PI * 2.0 / 8;
                w.addParticle(PURPLE_SOFT, x + Math.cos(angle) * headRing, y + 1.6, z + Math.sin(angle) * headRing, 0, 0.008, 0);
            }
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            scatter(w, PINK_HOT,    x, y + 1.0, z, 20, 1.5, 1.2, 1.5, 0.12);
            scatter(w, PURPLE_DEEP, x, y + 1.0, z, 15, 1.2, 1.0, 1.2, 0.10);
            scatter(w, PURPLE_BLACK, x, y + 1.0, z, 10, 1.0, 0.8, 1.0, 0.08);
            scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 3, 0.3, 0.3, 0.3, 0);
        }
        int burstCount = (int)(10 + (1.0 - progress) * 16);
        for (int i = 0; i < burstCount; i++) {
            DustParticleEffect col = switch (i % 3) {
                case 0  -> PINK_HOT;
                case 1  -> PURPLE_DEEP;
                default -> PURPLE_BLACK;
            };
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = w.random.nextDouble() * 2.0;
            double h     = w.random.nextDouble() * 2.8;
            w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.018, 0);
        }
        if (t % 3 == 0)
            w.addParticle(ParticleTypes.END_ROD,
                    x + (w.random.nextDouble() - 0.5) * 1.8, y + w.random.nextDouble() * 2.5, z + (w.random.nextDouble() - 0.5) * 1.8,
                    0, 0.025, 0);
        if (t % 4 == 0) {
            w.addParticle(ParticleTypes.WITCH,
                    x + (w.random.nextDouble() - 0.5) * 1.2, y + 0.5 + w.random.nextDouble() * 2.0, z + (w.random.nextDouble() - 0.5) * 1.2,
                    0, 0.02, 0);
            w.addParticle(ParticleTypes.WITCH,
                    x + (w.random.nextDouble() - 0.5) * 1.2, y + 0.5 + w.random.nextDouble() * 2.0, z + (w.random.nextDouble() - 0.5) * 1.2,
                    0, 0.02, 0);
        }
        if (progress > 0.78f) {
            for (int i = 0; i < 3; i++) {
                w.addParticle(PINK_PALE,    x, y + 1.2, z, 0, 0.005, 0);
                w.addParticle(PURPLE_SOFT, x, y + 1.0, z, 0, 0.004, 0);
            }
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        scatter(w, PINK_HOT,   x, y + 1.0, z, 14, 1.2, 1.0, 1.2, 0.09);
        scatter(w, PURPLE_DEEP, x, y + 1.0, z, 10, 1.0, 0.8, 1.0, 0.07);
        scatter(w, PINK_PALE,  x, y + 1.0, z,  8, 0.8, 0.7, 0.8, 0.05);
        scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.2, 0.1, 0.2, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
