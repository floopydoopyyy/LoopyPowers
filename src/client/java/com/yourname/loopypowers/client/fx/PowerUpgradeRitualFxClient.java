package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PowerUpgradeRitualFxClient {

    private PowerUpgradeRitualFxClient() {}

    private static final DustParticleEffect MAGENTA = new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    private static final DustParticleEffect PINK    = new DustParticleEffect(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    private static final DustParticleEffect PURPLE  = new DustParticleEffect(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    private static final DustParticleEffect VIOLET  = new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    private static final DustParticleEffect WHITE   = new DustParticleEffect(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress, time);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress);
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t % 2 == 0) {
            int columnCount = (int)(4 + progress * 8);
            for (int i = 0; i < columnCount; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.0;
                double h     = 8.0 + w.random.nextDouble() * 6.0;
                DustParticleEffect col = w.random.nextBoolean() ? MAGENTA : PINK;
                w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, -0.06, 0);
            }
        }
        int    pulseTimer = t % 15;
        double ringR      = pulseTimer * 0.32;
        if (ringR > 0.3 && t % 2 == 0) {
            int ringPoints = 16;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                DustParticleEffect col = (pulseTimer < 8) ? PURPLE : PINK;
                w.addParticle(col, x + Math.cos(angle) * ringR, y + 0.05, z + Math.sin(angle) * ringR, 0, 0.004, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + w.random.nextDouble() * 1.2;
                w.addParticle(VIOLET, x + Math.cos(angle) * r, y + 0.1 + w.random.nextDouble() * 2.0 * progress, z + Math.sin(angle) * r, 0, 0.007, 0);
            }
        }
        if (t % 16 == 0)
            w.addParticle(ParticleTypes.ENCHANT, x, y + 1.0, z, 0, 1.4, 0);
        if (t % 6 == 0 && progress > 0.3f) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + w.random.nextDouble() * 2.0;
                w.addParticle(ParticleTypes.GLOW, x + Math.cos(angle) * r, y + 0.1, z + Math.sin(angle) * r, 0, 0, 0);
            }
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t % 2 == 0) {
            int streamCount = 6;
            for (int s = 0; s < streamCount; s++) {
                double baseAngle = s * Math.PI * 2.0 / streamCount;
                double spin      = baseAngle + time * 0.06;
                double r         = 0.5 + progress * 0.4;
                int steps = 8;
                for (int i = 0; i < steps; i++) {
                    double h     = y + 0.3 + i * (0.8 + progress * 0.5);
                    double angle = spin + i * 0.08;
                    DustParticleEffect col = switch (s % 3) {
                        case 0  -> MAGENTA;
                        case 1  -> WHITE;
                        default -> PURPLE;
                    };
                    if (i < steps - 1)
                        w.addParticle(col, x + Math.cos(angle) * r, h, z + Math.sin(angle) * r, 0, 0.005, 0);
                    else
                        w.addParticle(WHITE, x + Math.cos(angle) * r, h, z + Math.sin(angle) * r, 0, 0.02, 0);
                }
            }
        }
        if (t % 2 == 0) {
            int rainCount = (int)(6 + progress * 10);
            for (int i = 0; i < rainCount; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 1.8;
                DustParticleEffect col = w.random.nextBoolean() ? MAGENTA : VIOLET;
                w.addParticle(col, x + Math.cos(angle) * r, y + 6.0 + w.random.nextDouble() * 4.0, z + Math.sin(angle) * r, 0, -0.08, 0);
            }
        }
        if (t % 8 == 0) {
            int ringPoints = 14;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                double speed = 0.12 + progress * 0.06;
                w.addParticle(PURPLE,
                        x + Math.cos(angle) * 0.4, y + 1.0, z + Math.sin(angle) * 0.4,
                        Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed);
            }
        }
        if (t % 8 == 0)
            scatter(w, ParticleTypes.GLOW, x, y + 1.0, z, 4, 0.4, 0.5, 0.4, 0);
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int d = 0; d < 36; d++) {
                double theta = d * Math.PI * 2.0 / 36;
                double phi   = Math.PI * 0.3 + w.random.nextDouble() * Math.PI * 0.4;
                double speed = 0.20 + w.random.nextDouble() * 0.12;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> MAGENTA;
                    case 1  -> WHITE;
                    case 2  -> PURPLE;
                    default -> VIOLET;
                };
                w.addParticle(col, x, y + 1.0, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
            for (int i = 0; i < 18; i++) {
                double angle = i * Math.PI * 2.0 / 18;
                w.addParticle(PURPLE, x + Math.cos(angle) * 0.4, y + 0.15, z + Math.sin(angle) * 0.4,
                        Math.cos(angle) * 0.30, 0.01, Math.sin(angle) * 0.30);
            }
            scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 4, 0.3, 0.3, 0.3, 0);
            scatter(w, ParticleTypes.GLOW,  x, y + 1.0, z, 12, 0.6, 0.5, 0.6, 0);
        }
        if (t % 2 == 0) {
            int columnCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < columnCount; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 0.8;
                DustParticleEffect col = w.random.nextBoolean() ? MAGENTA : WHITE;
                w.addParticle(col, x + Math.cos(angle) * r, y + 7.0 + w.random.nextDouble() * 5.0, z + Math.sin(angle) * r, 0, -0.12, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                w.addParticle(ParticleTypes.TOTEM_OF_UNDYING,
                        x + (w.random.nextDouble() - 0.5) * 0.6,
                        y + 0.3 + w.random.nextDouble() * 1.5,
                        z + (w.random.nextDouble() - 0.5) * 0.6,
                        (w.random.nextDouble() - 0.5) * 0.04, 0.08 + w.random.nextDouble() * 0.10, (w.random.nextDouble() - 0.5) * 0.04);
            }
        }
        if (progress > 0.72f) {
            w.addParticle(PINK,  x, y + 1.0, z, 0, 0.005, 0);
            w.addParticle(WHITE, x, y + 1.0, z, 0, 0.004, 0);
        }
        if (t % 10 == 0)
            scatter(w, ParticleTypes.ENCHANT, x, y + 1.0, z, 5, 0.4, 0.5, 0.4, 1.2);
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (int d = 0; d < 32; d++) {
            double theta = w.random.nextDouble() * Math.PI * 2;
            double phi   = w.random.nextDouble() * Math.PI;
            double speed = 0.16;
            DustParticleEffect col = switch (d % 3) {
                case 0  -> MAGENTA;
                case 1  -> WHITE;
                default -> PURPLE;
            };
            w.addParticle(col, x, y + 1.0, z,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed);
        }
        scatter(w, MAGENTA,  x, y + 1.0, z, 18, 1.3, 1.1, 1.3, 0.09);
        scatter(w, PURPLE,   x, y + 1.0, z, 14, 1.1, 1.0, 1.1, 0.08);
        scatter(w, WHITE,    x, y + 1.0, z, 12, 0.9, 0.8, 0.9, 0.07);
        scatter(w, VIOLET,   x, y + 1.0, z, 10, 0.8, 0.8, 0.8, 0.07);
        scatter(w, PINK,     x, y + 1.0, z,  8, 0.7, 0.7, 0.7, 0.06);
        scatter(w, ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 16, 1.0, 1.2, 1.0, 0.18);
        scatter(w, ParticleTypes.GLOW,  x, y + 1.0, z, 10, 0.8, 0.6, 0.8, 0);
        scatter(w, ParticleTypes.FLASH, x, y + 1.2, z,  3, 0.2, 0.2, 0.2, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
