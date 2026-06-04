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
public final class SeveranceRitualFxClient {

    private SeveranceRitualFxClient() {}

    private static final DustParticleEffect BLACK  = new DustParticleEffect(new Vector3f(0.06f, 0.01f, 0.10f), 1.6f);
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.35f, 0.00f, 0.52f), 1.5f);
    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.60f, 0.18f, 0.82f), 1.3f);
    private static final DustParticleEffect RED    = new DustParticleEffect(new Vector3f(0.28f, 0.01f, 0.03f), 1.5f);
    private static final DustParticleEffect GRAY   = new DustParticleEffect(new Vector3f(0.72f, 0.70f, 0.68f), 0.9f);

    private static final double[] TENDRIL_ANGLES = {
            0.0, Math.PI * 0.42, Math.PI * 0.81, Math.PI, Math.PI * 1.35, Math.PI * 1.78
    };

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress, time);
            case 4 -> renderStage4(world, x, y, z, t, progress);
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress) {
        double maxReach  = 5.0;
        double innerEdge = maxReach * (1.0 - progress);
        if (t % 2 == 0) {
            for (double angle : TENDRIL_ANGLES) {
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = innerEdge + (maxReach - innerEdge) * ((double) s / steps);
                    double jitter = (w.random.nextDouble() - 0.5) * 0.22;
                    DustParticleEffect col = (s < 4) ? BLACK : (s < 7) ? PURPLE : VIOLET;
                    w.addParticle(col,
                            x + Math.cos(angle) * d + Math.cos(angle + Math.PI * 0.5) * jitter,
                            y + 0.05,
                            z + Math.sin(angle) * d + Math.sin(angle + Math.PI * 0.5) * jitter,
                            0, 0.002, 0);
                }
                if (t % 4 == 0 && innerEdge > 0.4) {
                    scatter(w, VIOLET, x + Math.cos(angle) * innerEdge, y + 0.12, z + Math.sin(angle) * innerEdge, 2, 0.05, 0.08, 0.05, 0.018);
                    w.addParticle(PURPLE, x + Math.cos(angle) * innerEdge, y + 0.1, z + Math.sin(angle) * innerEdge, 0, 0.010, 0);
                }
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.8 + w.random.nextDouble() * 4.0;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 2.0 + w.random.nextDouble() * 2.0, z + Math.sin(angle) * r, 0, -0.008, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 0.6;
                w.addParticle(BLACK, x + Math.cos(angle) * r, y + 0.05 + w.random.nextDouble() * 1.5 * progress, z + Math.sin(angle) * r, 0, 0.005, 0);
            }
        }
        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + w.random.nextDouble() * 2.5;
                w.addParticle(PURPLE, x + Math.cos(angle) * r, y + 0.1, z + Math.sin(angle) * r, 0, 0.008, 0);
            }
        }
        if (t % 8 == 0 && progress > 0.3f) {
            int    idx   = w.random.nextInt(TENDRIL_ANGLES.length);
            double angle = TENDRIL_ANGLES[idx];
            double r     = innerEdge + w.random.nextDouble() * 2.0;
            scatter(w, ParticleTypes.WITCH, x + Math.cos(angle) * r, y + 0.5 + w.random.nextDouble() * 0.8, z + Math.sin(angle) * r, 2, 0.06, 0.06, 0.06, 0.02);
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double coilRotation = time * 0.03;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                int    steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d      = 0.6 + (double) s / steps * 3.9;
                    double jitter = (w.random.nextDouble() - 0.5) * 0.18;
                    DustParticleEffect col = (s < 3) ? BLACK : (s < 6) ? PURPLE : VIOLET;
                    w.addParticle(col,
                            x + Math.cos(angle) * d + Math.cos(angle + Math.PI * 0.5) * jitter,
                            y + 0.05,
                            z + Math.sin(angle) * d + Math.sin(angle + Math.PI * 0.5) * jitter,
                            0, 0.003, 0);
                }
                double riseHeight = 0.5 + progress * 3.5;
                if (t % 3 == 0) {
                    w.addParticle(PURPLE, x + Math.cos(angle) * 0.7, y + w.random.nextDouble() * riseHeight, z + Math.sin(angle) * 0.7, 0, 0.008, 0);
                    if (w.random.nextFloat() < 0.4f)
                        w.addParticle(VIOLET, x + Math.cos(angle) * 0.55, y + riseHeight, z + Math.sin(angle) * 0.55, 0, 0.012, 0);
                }
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.2 + w.random.nextDouble() * 0.9;
                w.addParticle(ParticleTypes.WARPED_SPORE, x + Math.cos(angle) * r, y + 0.5 + w.random.nextDouble() * 2.0, z + Math.sin(angle) * r, 0, 0.012, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + w.random.nextDouble() * 0.8;
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(angle) * r, y + 0.3 + w.random.nextDouble() * 2.2, z + Math.sin(angle) * r, 0, 0.010, 0);
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + w.random.nextDouble() * 1.6;
                w.addParticle(PURPLE, x + Math.cos(angle) * r, y + 0.3 + w.random.nextDouble() * 0.8, z + Math.sin(angle) * r, 0, 0.005, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 5.0;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 1.5 + w.random.nextDouble() * 3.0, z + Math.sin(angle) * r, 0, -0.01, 0);
            }
        }
        if (t % 5 == 0)
            scatter(w, ParticleTypes.WITCH, x, y + 0.5 + w.random.nextDouble() * 1.5, z, 3, 0.8, 0.08, 0.8, 0.025);
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t % 2 == 0) {
            int burstCount = (int)(8 + progress * 14);
            for (int i = 0; i < burstCount; i++) {
                double theta = w.random.nextDouble() * Math.PI * 2;
                double phi   = w.random.nextDouble() * Math.PI * 0.6;
                double speed = 0.08 + progress * 0.07;
                DustParticleEffect col = switch (i % 5) {
                    case 0  -> BLACK;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    case 3  -> RED;
                    default -> GRAY;
                };
                w.addParticle(col, x, y + 1.1, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
        }
        double coilRotation = time * 0.05;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                double r     = 0.65 * (1.0 - progress * 0.35);
                if (r > 0.2) {
                    DustParticleEffect col = (t % 4 == 0) ? VIOLET : BLACK;
                    w.addParticle(col, x + Math.cos(angle) * r, y + 0.3, z + Math.sin(angle) * r, 0, 0.006, 0);
                }
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.8;
                w.addParticle(RED, x + Math.cos(angle) * r, y + 0.05, z + Math.sin(angle) * r, 0, 0.003, 0);
            }
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.0;
                w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(angle) * r, y + 0.2 + w.random.nextDouble() * 2.5, z + Math.sin(angle) * r, 0, 0.016, 0);
            }
        }
        if (t % 3 == 0) {
            int    coronaPoints = 12;
            double coronaR      = 0.9 - progress * 0.2;
            for (int i = 0; i < coronaPoints; i++) {
                double angle = time * 0.08 + i * Math.PI * 2.0 / coronaPoints;
                w.addParticle(VIOLET, x + Math.cos(angle) * coronaR, y + 1.1, z + Math.sin(angle) * coronaR, 0, 0.007, 0);
            }
        }
        if (t % 3 == 0)
            scatter(w, ParticleTypes.WITCH, x, y + 0.5 + w.random.nextDouble() * 2.0, z, 4, 0.7, 0.10, 0.7, 0.03);
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int d = 0; d < 40; d++) {
                double theta = d * Math.PI * 2.0 / 40;
                double phi   = Math.PI * 0.35 + w.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.22 + w.random.nextDouble() * 0.14;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> BLACK;
                    case 2  -> RED;
                    default -> VIOLET;
                };
                w.addParticle(col, x, y + 1.1, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
            scatter(w, ParticleTypes.SOUL_FIRE_FLAME, x, y + 1.0, z, 24, 1.1, 0.9, 1.1, 0.14);
            scatter(w, ParticleTypes.WITCH, x, y + 1.0, z, 16, 0.8, 0.6, 0.8, 0.10);
            scatter(w, ParticleTypes.ASH,   x, y + 1.0, z, 35, 1.3, 0.9, 1.3, 0.11);
            int ringPoints = 18;
            for (int i = 0; i < ringPoints; i++) {
                double angle = i * Math.PI * 2.0 / ringPoints;
                w.addParticle(PURPLE, x + Math.cos(angle) * 0.4, y + 0.2, z + Math.sin(angle) * 0.4,
                        Math.cos(angle) * 0.28, 0.01, Math.sin(angle) * 0.28);
            }
        }
        if (t > 8 && t % 2 == 0) {
            int pullCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < pullCount; i++) {
                double theta  = w.random.nextDouble() * Math.PI * 2;
                double phi    = w.random.nextDouble() * Math.PI;
                double srcR   = 2.5 + w.random.nextDouble() * 4.5;
                double fromX  = x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = y + 1.0 + Math.cos(phi) * srcR * 0.4;
                double fromZ  = z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3d  toward = new Vec3d(x, y + 1, z).subtract(fromX, fromY, fromZ).normalize().multiply(0.10 + (1.0 - progress) * 0.06);
                DustParticleEffect col = switch (i % 4) {
                    case 0  -> BLACK;
                    case 1  -> GRAY;
                    case 2  -> PURPLE;
                    default -> RED;
                };
                w.addParticle(col, fromX, fromY, fromZ, toward.x, toward.y, toward.z);
            }
        }
        if (progress < 0.65f && t % 3 == 0) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = w.random.nextDouble() * 0.7;
            w.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(angle) * r, y + 0.2 + w.random.nextDouble() * 2.0, z + Math.sin(angle) * r, 0, 0.008, 0);
        }
        if (progress > 0.15f && progress < 0.65f && t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + w.random.nextDouble() * 1.2;
                w.addParticle(PURPLE, x + Math.cos(angle) * r, y + 0.2 + w.random.nextDouble() * 2.2, z + Math.sin(angle) * r, 0, 0.006, 0);
            }
        }
        if (t % 3 == 0 && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 4.5;
                w.addParticle(ParticleTypes.ASH, x + Math.cos(angle) * r, y + 3.0 + w.random.nextDouble() * 2.0, z + Math.sin(angle) * r, 0, -0.015, 0);
            }
        }
        if (progress > 0.72f) {
            w.addParticle(BLACK,  x, y + 1.0, z, 0, 0.003, 0);
            w.addParticle(GRAY,   x, y + 0.8, z, 0, 0.002, 0);
            w.addParticle(PURPLE, x, y + 1.0, z, 0, 0.002, 0);
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        scatter(w, BLACK,  x, y + 1.0, z, 20, 1.3, 1.0, 1.3, 0.07);
        scatter(w, PURPLE, x, y + 1.0, z, 16, 1.1, 0.9, 1.1, 0.06);
        scatter(w, VIOLET, x, y + 1.0, z, 10, 0.9, 0.8, 0.9, 0.05);
        scatter(w, GRAY,   x, y + 1.0, z, 12, 1.0, 0.8, 1.0, 0.05);
        scatter(w, ParticleTypes.ASH,             x, y + 1.0, z, 22, 1.5, 1.0, 1.5, 0.06);
        scatter(w, ParticleTypes.SOUL_FIRE_FLAME, x, y + 1.0, z,  8, 0.6, 0.5, 0.6, 0.05);
        scatter(w, ParticleTypes.WITCH,            x, y + 1.0, z,  6, 0.5, 0.4, 0.5, 0.06);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
