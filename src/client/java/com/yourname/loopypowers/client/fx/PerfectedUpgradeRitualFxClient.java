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
public final class PerfectedUpgradeRitualFxClient {

    private PerfectedUpgradeRitualFxClient() {}

    private static final DustParticleEffect MAGENTA    = new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    private static final DustParticleEffect PINK_WHITE = new DustParticleEffect(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    private static final DustParticleEffect PURPLE     = new DustParticleEffect(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    private static final DustParticleEffect VIOLET     = new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    private static final DustParticleEffect WHITE_YELLOW = new DustParticleEffect(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);
    private static final DustParticleEffect WHITE      = new DustParticleEffect(new Vector3f(1.00f, 0.98f, 1.00f), 1.8f);

    private static final double[] SIGIL_ANGLES = {
            0.0, Math.PI * 0.28, Math.PI * 0.55, Math.PI * 0.82,
            Math.PI, Math.PI * 1.25, Math.PI * 1.55, Math.PI * 1.80
    };

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress, time);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress, time);
            case 4 -> renderStage4(world, x, y, z, t, progress, time);
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double sigRotation = time * 0.020;
        double spokeReach  = 1.0 + progress * 4.0;
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 10;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * spokeReach;
                    DustParticleEffect col = (s < 4) ? PURPLE : (s < 7) ? VIOLET : PINK_WHITE;
                    double jitter = (w.random.nextDouble() - 0.5) * 0.14;
                    w.addParticle(col,
                            x + Math.cos(angle) * d + Math.cos(angle + Math.PI * 0.5) * jitter,
                            y + 0.05,
                            z + Math.sin(angle) * d + Math.sin(angle + Math.PI * 0.5) * jitter,
                            0, 0.003, 0);
                }
                if (t % 5 == 0 && spokeReach > 1.0)
                    w.addParticle(MAGENTA, x + Math.cos(angle) * spokeReach, y + 0.10, z + Math.sin(angle) * spokeReach, 0, 0.014, 0);
            }
            if (spokeReach > 1.5) {
                int ringPoints = 32;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                    DustParticleEffect col = (i % 3 == 0) ? MAGENTA : PURPLE;
                    w.addParticle(col, x + Math.cos(angle) * spokeReach, y + 0.06, z + Math.sin(angle) * spokeReach, 0, 0.003, 0);
                }
            }
        }
        if (t % 2 == 0) {
            int n = (int)(3 + progress * 7);
            for (int i = 0; i < n; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.4;
                w.addParticle(w.random.nextBoolean() ? MAGENTA : WHITE_YELLOW,
                        x + Math.cos(angle) * r, y + 7.0 + w.random.nextDouble() * 6.0, z + Math.sin(angle) * r,
                        0, -0.10, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + w.random.nextDouble() * spokeReach * 0.7;
                w.addParticle(VIOLET, x + Math.cos(angle) * r, y + 0.1 + w.random.nextDouble() * 2.5 * progress, z + Math.sin(angle) * r, 0, 0.008, 0);
            }
        }
        if (t % 14 == 0)
            scatter(w, ParticleTypes.ENCHANT, x, y + 1.0, z, 12, 0.8, 0.8, 0.8, 1.6);
        if (t % 7 == 0 && progress > 0.25f) {
            int idx = w.random.nextInt(SIGIL_ANGLES.length);
            double spokeAngle = SIGIL_ANGLES[idx] + sigRotation;
            scatter(w, ParticleTypes.GLOW, x + Math.cos(spokeAngle) * spokeReach, y + 0.1, z + Math.sin(spokeAngle) * spokeReach, 2, 0.05, 0.05, 0.05, 0);
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double sigRotation = time * 0.035;
        double sigilY      = y + progress * 1.2;
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 9;
                for (int s = 0; s < steps; s++) {
                    double d = 0.3 + (double) s / steps * 5.0;
                    DustParticleEffect col = (s < 3) ? PURPLE : (s < 6) ? VIOLET : MAGENTA;
                    w.addParticle(col, x + Math.cos(angle) * d, sigilY, z + Math.sin(angle) * d, 0, 0.003, 0);
                }
            }
            int ringPoints = 36;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + sigRotation;
                DustParticleEffect col = (i % 2 == 0) ? PURPLE : PINK_WHITE;
                w.addParticle(col, x + Math.cos(angle) * 5.0, sigilY, z + Math.sin(angle) * 5.0, 0, 0.003, 0);
            }
        }
        if (t % 2 == 0) spawnHalo(w, x, y, z, 2.4, 1.6, time * 0.07, 24, MAGENTA, VIOLET);
        if (t % 2 == 0) spawnHalo(w, x, y, z, 1.7, 1.9, -time * 0.10, 18, PURPLE, WHITE_YELLOW);
        if (t % 3 == 0) spawnHalo(w, x, y, z, 1.0, 2.2, time * 0.14, 12, WHITE_YELLOW, VIOLET);
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                double cx    = x + Math.cos(angle) * 0.7;
                double cz    = z + Math.sin(angle) * 0.7;
                int    steps = (int)(5 + progress * 7);
                for (int s = 0; s < steps; s++) {
                    double h = y + 0.3 + s * (0.6 + progress * 0.4);
                    DustParticleEffect col = switch (s % 3) {
                        case 0  -> MAGENTA;
                        case 1  -> PURPLE;
                        default -> WHITE_YELLOW;
                    };
                    w.addParticle(col, cx + (w.random.nextDouble() - 0.5) * 0.20, h, cz + (w.random.nextDouble() - 0.5) * 0.20, 0, 0.006, 0);
                }
            }
        }
        if (t % 2 == 0) {
            int n = (int)(6 + progress * 10);
            for (int i = 0; i < n; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.0;
                w.addParticle(w.random.nextBoolean() ? MAGENTA : WHITE_YELLOW,
                        x + Math.cos(angle) * r, y + 7.0 + w.random.nextDouble() * 7.0, z + Math.sin(angle) * r,
                        0, -0.10, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double sigRotation = time * 0.055;
        double shrink      = 1.0 - progress * 0.84;
        double sigR        = 5.0 * shrink + 0.8;
        double sigY        = y + 1.2 - progress * 0.6;
        if (t % 2 == 0) {
            for (double baseAngle : SIGIL_ANGLES) {
                double angle = baseAngle + sigRotation;
                int    steps = 8;
                for (int s = 0; s < steps; s++) {
                    double d = 0.2 + (double) s / steps * sigR;
                    DustParticleEffect col = (s % 2 == 0) ? PURPLE : MAGENTA;
                    w.addParticle(col, x + Math.cos(angle) * d, sigY, z + Math.sin(angle) * d, 0, 0.005, 0);
                }
            }
        }
        double haloShrink = 1.0 - progress * 0.55;
        if (t % 2 == 0) {
            spawnHalo(w, x, y, z, 2.4 * haloShrink, 1.6, time * (0.07 + progress * 0.08), 24, MAGENTA, VIOLET);
            spawnHalo(w, x, y, z, 1.7 * haloShrink, 1.9, -time * (0.10 + progress * 0.08), 18, PURPLE, WHITE_YELLOW);
            spawnHalo(w, x, y, z, 1.0 * haloShrink, 2.2, time * (0.14 + progress * 0.12), 12, WHITE_YELLOW, VIOLET);
        }
        if (t % 2 == 0) {
            int pullCount = (int)(10 + progress * 18);
            for (int i = 0; i < pullCount; i++) {
                double theta  = w.random.nextDouble() * Math.PI * 2;
                double phi    = w.random.nextDouble() * Math.PI;
                double srcR   = 3.5 + w.random.nextDouble() * 5.0;
                double fromX  = x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = y + 1.0 + Math.cos(phi) * srcR * 0.45;
                double fromZ  = z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3d  toward = new Vec3d(x, y + 1, z).subtract(fromX, fromY, fromZ).normalize().multiply(0.10 + progress * 0.07);
                DustParticleEffect col = switch (i % 4) {
                    case 0  -> MAGENTA;
                    case 1  -> PURPLE;
                    case 2  -> VIOLET;
                    default -> WHITE_YELLOW;
                };
                w.addParticle(col, fromX, fromY, fromZ, toward.x, toward.y, toward.z);
            }
        }
        if (t % 4 == 0) {
            for (int i = 0; i < 6; i++) {
                double theta = w.random.nextDouble() * Math.PI * 2;
                double phi   = w.random.nextDouble() * Math.PI * 0.5;
                double speed = 0.10 + progress * 0.05;
                DustParticleEffect col = (i % 2 == 0) ? MAGENTA : WHITE_YELLOW;
                w.addParticle(col, x, y + 1.0, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.5 + 0.04,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
        }
        if (t % 2 == 0) {
            int n = (int)(10 + progress * 10);
            for (int i = 0; i < n; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 0.7;
                w.addParticle(w.random.nextBoolean() ? MAGENTA : WHITE_YELLOW,
                        x + Math.cos(angle) * r, y + 8.0 + w.random.nextDouble() * 7.0, z + Math.sin(angle) * r,
                        0, -0.10, 0);
            }
        }
        if (t % 8 == 0) scatter(w, ParticleTypes.ENCHANT, x, y + 1.0, z, 10, 0.6, 0.7, 0.6, 1.8);
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t == 1) {
            for (int d = 0; d < 48; d++) {
                double theta = d * Math.PI * 2.0 / 48;
                double phi   = w.random.nextDouble() * Math.PI;
                double speed = 0.22 + w.random.nextDouble() * 0.16;
                DustParticleEffect col = switch (d % 5) {
                    case 0  -> MAGENTA;
                    case 1  -> WHITE_YELLOW;
                    case 2  -> PURPLE;
                    case 3  -> VIOLET;
                    default -> WHITE;
                };
                w.addParticle(col, x, y + 1.0, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
            for (double ringScale : new double[]{ 0.5, 1.0, 1.6 }) {
                int ringPoints = 20;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = i * Math.PI * 2.0 / ringPoints;
                    w.addParticle(ringScale < 1.0 ? WHITE : PURPLE,
                            x + Math.cos(angle) * ringScale * 0.4, y + 0.15, z + Math.sin(angle) * ringScale * 0.4,
                            Math.cos(angle) * 0.32 * ringScale, 0.01, Math.sin(angle) * 0.32 * ringScale);
                }
            }
            scatter(w, ParticleTypes.FLASH,            x, y + 1.2, z, 5, 0.4, 0.4, 0.4, 0);
            scatter(w, ParticleTypes.GLOW,             x, y + 1.0, z, 20, 0.8, 0.7, 0.8, 0);
            scatter(w, ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 20, 1.0, 1.2, 1.0, 0.20);
        }
        if (t % 2 == 0 && progress < 0.70f) {
            int n = (int)(14 + (1.0 - progress) * 10);
            for (int i = 0; i < n; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 0.6;
                w.addParticle(w.random.nextBoolean() ? MAGENTA : WHITE_YELLOW,
                        x + Math.cos(angle) * r, y + 8.0 + w.random.nextDouble() * 8.0, z + Math.sin(angle) * r,
                        0, -0.10, 0);
            }
        }
        double finalShrink = 0.7 - progress * 0.25;
        if (t % 2 == 0 && progress < 0.80f) {
            spawnHalo(w, x, y, z, Math.max(0.3, 2.4 * finalShrink), 1.6, time * 0.18, 24, MAGENTA, WHITE);
            spawnHalo(w, x, y, z, Math.max(0.3, 1.7 * finalShrink), 1.9, -time * 0.22, 18, PURPLE, WHITE_YELLOW);
            spawnHalo(w, x, y, z, Math.max(0.2, 1.0 * finalShrink), 2.2, time * 0.28, 12, WHITE, VIOLET);
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = w.random.nextDouble() * Math.PI * 2;
                double phi   = w.random.nextDouble() * Math.PI * 0.7;
                double speed = 0.08 + (1.0 - progress) * 0.06;
                w.addParticle(WHITE, x, y + 1.0, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++)
                w.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        x + (w.random.nextDouble() - 0.5) * 0.7, y + 0.3 + w.random.nextDouble() * 1.8, z + (w.random.nextDouble() - 0.5) * 0.7,
                        (w.random.nextDouble() - 0.5) * 0.04, 0.10 + w.random.nextDouble() * 0.12, (w.random.nextDouble() - 0.5) * 0.04);
        }
        if (progress > 0.75f) {
            w.addParticle(WHITE,      x, y + 1.0, z, 0, 0.004, 0);
            w.addParticle(PINK_WHITE, x, y + 1.0, z, 0, 0.003, 0);
        }
        if (t % 8 == 0) scatter(w, ParticleTypes.ENCHANT, x, y + 1.0, z, 10, 0.6, 0.8, 0.6, 2.0);
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (int d = 0; d < 40; d++) {
            double theta = w.random.nextDouble() * Math.PI * 2;
            double phi   = w.random.nextDouble() * Math.PI;
            double speed = 0.18;
            DustParticleEffect col = switch (d % 4) {
                case 0  -> WHITE;
                case 1  -> MAGENTA;
                case 2  -> PURPLE;
                default -> WHITE_YELLOW;
            };
            w.addParticle(col, x, y + 1.0, z,
                    Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed);
        }
        for (double angle : SIGIL_ANGLES) {
            for (double d = 0.5; d <= 4.0; d += 0.6)
                w.addParticle(d < 2.0 ? WHITE : MAGENTA,
                        x + Math.cos(angle) * d, y + 0.1, z + Math.sin(angle) * d,
                        Math.cos(angle) * 0.12, 0.01, Math.sin(angle) * 0.12);
        }
        scatter(w, WHITE,      x, y + 1.0, z, 22, 1.5, 1.2, 1.5, 0.10);
        scatter(w, MAGENTA,    x, y + 1.0, z, 18, 1.3, 1.1, 1.3, 0.09);
        scatter(w, PURPLE,     x, y + 1.0, z, 16, 1.1, 1.0, 1.1, 0.08);
        scatter(w, WHITE_YELLOW, x, y + 1.0, z, 14, 1.0, 0.9, 1.0, 0.07);
        scatter(w, VIOLET,     x, y + 1.0, z, 12, 0.9, 0.8, 0.9, 0.07);
        scatter(w, ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 24, 1.2, 1.5, 1.2, 0.22);
        scatter(w, ParticleTypes.GLOW,  x, y + 1.0, z, 16, 1.0, 0.8, 1.0, 0);
        scatter(w, ParticleTypes.FLASH, x, y + 1.4, z,  5, 0.3, 0.3, 0.3, 0);
    }

    private static void spawnHalo(ClientWorld w, double x, double y, double z,
                                  double radius, double height, double speed, int points,
                                  DustParticleEffect primary, DustParticleEffect secondary) {
        for (int i = 0; i < points; i++) {
            double angle = speed + i * Math.PI * 2.0 / points;
            DustParticleEffect col = (i % 2 == 0) ? primary : secondary;
            w.addParticle(col, x + Math.cos(angle) * radius, y + height, z + Math.sin(angle) * radius, 0, 0.005, 0);
        }
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
