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
public final class SpaceRitualFxClient {

    private SpaceRitualFxClient() {}

    private static final DustParticleEffect PURPLE      = new DustParticleEffect(new Vector3f(0.52f, 0.08f, 0.80f), 1.4f);
    private static final DustParticleEffect PURPLE_DARK = new DustParticleEffect(new Vector3f(0.20f, 0.02f, 0.38f), 1.6f);
    private static final DustParticleEffect PINK        = new DustParticleEffect(new Vector3f(0.88f, 0.40f, 0.94f), 1.2f);
    private static final DustParticleEffect GOLD        = new DustParticleEffect(new Vector3f(1.00f, 0.92f, 0.58f), 0.9f);
    private static final DustParticleEffect WHITE       = new DustParticleEffect(new Vector3f(0.92f, 0.96f, 1.00f), 0.7f);
    private static final DustParticleEffect BLUE        = new DustParticleEffect(new Vector3f(0.22f, 0.82f, 0.90f), 1.1f);
    private static final DustParticleEffect BLUE_lIGHT  = new DustParticleEffect(new Vector3f(0.78f, 0.92f, 1.00f), 1.3f);

    private static final double ARM_A     = 0.35;
    private static final double ARM_B     = 0.50;
    private static final double ARM_T_MAX = 2.7 * Math.PI;
    private static final int    ARM_STEPS = 30;
    private static final double SINGULARITY_HEIGHT = 3.8;

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress, time);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress, time);
            case 4 -> renderStage4(world, x, y, z, t, progress);
        }
    }

    private static void spawnStarField(ClientWorld w, double x, double y, double z, int starsPerTick) {
        for (int i = 0; i < starsPerTick; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = 5.5 + w.random.nextDouble() * 6.5;
            double h     = -0.5 + w.random.nextDouble() * 13.0;
            double sx = x + Math.cos(angle) * r;
            double sy = y + h;
            double sz = z + Math.sin(angle) * r;
            if (w.random.nextInt(5) == 0)
                w.addParticle(ParticleTypes.END_ROD, sx, sy, sz, 0, 0, 0);
            else {
                DustParticleEffect star = (w.random.nextInt(3) == 0) ? GOLD : WHITE;
                w.addParticle(star, sx, sy, sz, 0, 0.001, 0);
            }
        }
    }

    private static void spawnArmsFlat(ClientWorld w, double x, double y, double z,
                                      double tMax, double armY, double cloudWidth, double rotation, boolean dim) {
        int steps = Math.max(3, (int)(ARM_STEPS * (tMax / ARM_T_MAX)));
        for (int arm = 0; arm < 2; arm++) {
            double armOffset = arm * Math.PI;
            for (int s = 0; s < steps; s++) {
                double theta = (double) s / steps * tMax;
                double r     = ARM_A + ARM_B * theta;
                double angle = theta + armOffset + rotation;
                double cloud = cloudWidth * (0.25 + (r / 4.6) * 0.75);
                double px = x + Math.cos(angle) * r + (w.random.nextDouble() - 0.5) * cloud;
                double pz = z + Math.sin(angle) * r + (w.random.nextDouble() - 0.5) * cloud;
                DustParticleEffect col;
                if (dim) {
                    col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                } else {
                    double frac = (double) s / steps;
                    col = (frac < 0.35) ? PURPLE_DARK : (frac < 0.70) ? PURPLE : (s % 2 == 0) ? PINK : BLUE;
                }
                w.addParticle(col, px, armY, pz, 0, 0.002, 0);
            }
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        spawnStarField(w, x, y, z, (int)(4 + progress * 8));
        double armTMax = ARM_T_MAX * progress;
        if (armTMax > 0.4 && t % 2 == 0)
            spawnArmsFlat(w, x, y, z, armTMax, y + 0.08, 0.35, time * 0.018, false);
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + w.random.nextDouble() * 1.5;
                w.addParticle(PURPLE_DARK, x + Math.cos(angle) * r, y + 0.1 + w.random.nextDouble() * 2.5 * progress, z + Math.sin(angle) * r, 0, 0.006, 0);
            }
        }
        if (t % 14 == 0)
            scatter(w, ParticleTypes.ENCHANT, x, y + 1.0, z, 6, 0.5, 0.5, 0.5, 1.2);
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        spawnStarField(w, x, y, z, 12);
        if (t % 2 == 0) {
            for (int arm = 0; arm < 2; arm++) {
                double armOffset = arm * Math.PI;
                for (int s = 0; s < ARM_STEPS; s++) {
                    double theta  = (double) s / ARM_STEPS * ARM_T_MAX;
                    double r      = ARM_A + ARM_B * theta;
                    double angle  = theta + armOffset + time * 0.018;
                    double armY   = y + 0.1 + theta * (0.48 * progress);
                    double cloudW = 0.25 + (r / 4.6) * 0.85;
                    double px = x + Math.cos(angle) * r + (w.random.nextDouble() - 0.5) * cloudW;
                    double py = armY + (w.random.nextDouble() - 0.5) * cloudW * 0.45;
                    double pz = z + Math.sin(angle) * r + (w.random.nextDouble() - 0.5) * cloudW;
                    double frac = (double) s / ARM_STEPS;
                    DustParticleEffect col;
                    if (frac < 0.30)       col = (s % 2 == 0) ? PURPLE_DARK : PURPLE;
                    else if (frac < 0.65)  col = (s % 2 == 0) ? PURPLE : PINK;
                    else                   col = (s % 2 == 0) ? PINK : BLUE;
                    w.addParticle(col, px, py, pz, 0, 0.003, 0);
                }
            }
        }
        double orbitR     = 2.7;
        double orbitSpeed = time * 0.09;
        double orbitTilt  = 0.32;
        int    orbitPts   = 18;
        if (t % 2 == 0) {
            for (int i = 0; i < orbitPts; i++) {
                double theta = orbitSpeed + i * Math.PI * 2.0 / orbitPts;
                double ox    = x + Math.cos(theta) * orbitR;
                double oy    = y + 1.6 + Math.sin(theta) * orbitR * Math.sin(orbitTilt);
                double oz    = z + Math.sin(theta) * orbitR * Math.cos(orbitTilt);
                DustParticleEffect col = (i % 3 == 0) ? GOLD : BLUE_lIGHT;
                w.addParticle(col, ox, oy, oz, 0, 0.004, 0);
            }
        }
        if (t % 6 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + w.random.nextDouble() * 3.5;
                double h     = 0.4 + w.random.nextDouble() * (3.0 + progress * 2.0);
                w.addParticle(ParticleTypes.DRAGON_BREATH, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.005, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double singX = x;
        double singY = y + SINGULARITY_HEIGHT;
        double singZ = z;
        spawnStarField(w, x, y, z, 16);
        double armShrink = 1.0 - progress * 0.50;
        if (t % 3 == 0)
            spawnArmsFlat(w, x, y, z, ARM_T_MAX * armShrink, y + 0.08, 0.55, time * 0.018, true);
        double discR     = 1.9 - progress * 0.5;
        double discSpeed = time * (0.14 + progress * 0.18);
        int    discPts   = 24;
        if (t % 2 == 0) {
            for (int i = 0; i < discPts; i++) {
                double theta = discSpeed + i * Math.PI * 2.0 / discPts;
                double dx    = x + Math.cos(theta) * discR;
                double dz    = z + Math.sin(theta) * discR;
                DustParticleEffect col = switch (i % 4) {
                    case 0  -> BLUE_lIGHT;
                    case 1  -> WHITE;
                    case 2  -> PURPLE;
                    default -> BLUE;
                };
                w.addParticle(col, dx, singY - 0.1, dz, 0, 0.006, 0);
                if (i % 3 == 0)
                    w.addParticle(BLUE_lIGHT, x + Math.cos(theta + 0.2) * (discR * 0.58), singY + 0.12, z + Math.sin(theta + 0.2) * (discR * 0.58), 0, 0.005, 0);
            }
        }
        if (t % 2 == 0)
            scatter(w, ParticleTypes.PORTAL, singX, singY, singZ, 8, 0.28, 0.18, 0.28, 0.06);
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++)
                w.addParticle(ParticleTypes.REVERSE_PORTAL,
                        singX + (w.random.nextDouble() - 0.5) * 0.2, singY, singZ + (w.random.nextDouble() - 0.5) * 0.2,
                        0, 0.04, 0);
        }
        int pullCount = (int)(8 + progress * 16);
        if (t % 2 == 0) {
            for (int i = 0; i < pullCount; i++) {
                double theta  = w.random.nextDouble() * Math.PI * 2;
                double phi    = w.random.nextDouble() * Math.PI;
                double srcR   = 4.5 + w.random.nextDouble() * 5.5;
                double fromX  = x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = y + 1.0 + Math.cos(phi) * srcR * 0.5 + 2.0;
                double fromZ  = z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3d  toward = new Vec3d(singX, singY, singZ).subtract(fromX, fromY, fromZ).normalize().multiply(0.09 + progress * 0.07);
                DustParticleEffect col = switch (w.random.nextInt(4)) {
                    case 0  -> WHITE;
                    case 1  -> PURPLE;
                    case 2  -> BLUE_lIGHT;
                    default -> GOLD;
                };
                w.addParticle(col, fromX, fromY, fromZ, toward.x, toward.y, toward.z);
            }
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int d = 0; d < 36; d++) {
                double theta = w.random.nextDouble() * Math.PI * 2;
                double phi   = w.random.nextDouble() * Math.PI;
                double speed = 0.18 + w.random.nextDouble() * 0.14;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> PURPLE;
                    case 1  -> WHITE;
                    case 2  -> GOLD;
                    default -> BLUE_lIGHT;
                };
                w.addParticle(col, x, y + 1.0, z,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed);
            }
            scatter(w, ParticleTypes.PORTAL, x, y + 1.0, z, 22, 1.0, 0.8, 1.0, 0.16);
            scatter(w, ParticleTypes.FLASH,  x, y + 1.0, z,  4, 0.4, 0.3, 0.4, 0);
        }
        spawnStarField(w, x, y, z, 22);
        if (t % 2 == 0) {
            int streamCount = 8;
            for (int s = 0; s < streamCount; s++) {
                double angle = s * Math.PI * 2.0 / streamCount + (t * 0.07);
                for (int step = 0; step < 8; step++) {
                    double d     = 0.5 + step * 0.75;
                    double h     = 1.0 + step * 0.10;
                    double speed = 0.06 + step * 0.006;
                    DustParticleEffect col = switch (s % 4) {
                        case 0  -> PURPLE;
                        case 1  -> WHITE;
                        case 2  -> BLUE_lIGHT;
                        default -> PINK;
                    };
                    w.addParticle(col,
                            x + Math.cos(angle) * d, y + h, z + Math.sin(angle) * d,
                            Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed);
                }
            }
        }
        if (t % 3 == 0)
            scatter(w, ParticleTypes.PORTAL, x, y + w.random.nextDouble() * 3.0, z, 2, 1.4, 0.06, 1.4, 0.04);
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double theta = w.random.nextDouble() * Math.PI * 2;
                double phi   = w.random.nextDouble() * Math.PI;
                double speed = 0.10 + w.random.nextDouble() * 0.09;
                w.addParticle(ParticleTypes.END_ROD,
                        x + (w.random.nextDouble() - 0.5) * 0.6, y + 0.5 + w.random.nextDouble() * 1.8, z + (w.random.nextDouble() - 0.5) * 0.6,
                        Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed * 0.5, Math.sin(phi) * Math.sin(theta) * speed);
            }
        }
        if (t % 5 == 0 && progress < 0.70f)
            scatter(w, ParticleTypes.DRAGON_BREATH, x, y + 1.2, z, 3, 0.8, 0.5, 0.8, 0.04);
        if (progress > 0.78f) {
            for (int i = 0; i < 4; i++) {
                w.addParticle(PURPLE,      x, y + 1.0, z, 0, 0.005, 0);
                w.addParticle(PURPLE_DARK, x, y + 0.5, z, 0, 0.004, 0);
            }
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (int d = 0; d < 24; d++) {
            double theta = w.random.nextDouble() * Math.PI * 2;
            double phi   = w.random.nextDouble() * Math.PI;
            double speed = 0.14;
            w.addParticle(WHITE, x, y + 1.0, z,
                    Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed);
        }
        scatter(w, PURPLE,    x, y + 1.0, z, 16, 1.4, 1.1, 1.4, 0.09);
        scatter(w, PINK,      x, y + 1.0, z, 12, 1.2, 1.0, 1.2, 0.08);
        scatter(w, BLUE,      x, y + 1.0, z, 10, 1.0, 0.9, 1.0, 0.07);
        scatter(w, ParticleTypes.END_ROD, x, y + 1.0, z, 8, 0.9, 0.8, 0.9, 0.12);
        scatter(w, ParticleTypes.FLASH,   x, y + 1.0, z, 3, 0.3, 0.2, 0.3, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
