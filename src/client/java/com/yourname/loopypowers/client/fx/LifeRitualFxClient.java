package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class LifeRitualFxClient {

    private LifeRitualFxClient() {}

    private static final DustParticleEffect DARK_GREEN  = new DustParticleEffect(new Vector3f(0.08f, 0.55f, 0.12f), 1.5f);
    private static final DustParticleEffect LIGHT_GREEN = new DustParticleEffect(new Vector3f(0.35f, 0.90f, 0.22f), 1.3f);
    private static final DustParticleEffect ROOT        = new DustParticleEffect(new Vector3f(0.22f, 0.32f, 0.08f), 1.6f);
    private static final DustParticleEffect GOLD        = new DustParticleEffect(new Vector3f(0.88f, 0.82f, 0.10f), 1.2f);
    private static final DustParticleEffect PINK        = new DustParticleEffect(new Vector3f(0.98f, 0.92f, 0.90f), 1.0f);

    private static final double CANOPY_HEIGHT = 11.0;
    private static final double ROOT_REACH    = 5.5;
    private static final double BRANCH_START  = 0.45;

    private static final double[] ROOT_ANGLES = { 0.0, Math.PI * 0.35, Math.PI * 0.72, Math.PI, Math.PI * 1.32, Math.PI * 1.74 };
    private static final double[][] ROOT_BRANCHES = { { +0.40, -0.28 }, { -0.35, +0.30 }, { +0.32, -0.38 }, { -0.30, +0.42 }, { +0.38, -0.25 }, { -0.42, +0.32 } };

    public static void render(ClientWorld world, double x, double y, double z, int stage, int t, float progress, long time) {
        if (stage == 0) { renderComplete(world, x, y, z); return; }
        switch (stage) {
            case 1 -> renderStage1(world, x, y, z, t, progress);
            case 2 -> renderStage2(world, x, y, z, t, progress, time);
            case 3 -> renderStage3(world, x, y, z, t, progress);
            case 4 -> renderStage4(world, x, y, z, t, progress);
        }
    }

    private static void spawnRoots(ClientWorld w, double x, double y, double z, double reach, boolean glow) {
        for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
            double angle = ROOT_ANGLES[ri];
            int steps = Math.max(3, (int)(reach / 0.5));
            for (int s = 0; s < steps; s++) {
                double d = 0.3 + (double) s / steps * reach;
                double jitter = (w.random.nextDouble() - 0.5) * 0.20;
                double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;
                DustParticleEffect col = (s < steps / 3) ? ROOT : DARK_GREEN;
                w.addParticle(col, x + Math.cos(angle) * d + perpX, y + 0.04, z + Math.sin(angle) * d + perpZ, 0, 0.002, 0);
            }
            if (glow && reach > 1.0) w.addParticle(LIGHT_GREEN, x + Math.cos(angle) * reach, y + 0.09, z + Math.sin(angle) * reach, 0, 0.012, 0);
            double bStartD = reach * BRANCH_START;
            double[] branches = ROOT_BRANCHES[ri];
            double bLen = reach * (1.0 - BRANCH_START) * 0.75;
            if (bStartD > 0.4 && bLen > 0.3) {
                for (double branchOffset : branches) {
                    double bAngle = angle + branchOffset;
                    int bSteps = Math.max(2, (int)(bLen / 0.45));
                    for (int s = 0; s < bSteps; s++) {
                        double d = bStartD + (double) s / bSteps * bLen;
                        double jitter = (w.random.nextDouble() - 0.5) * 0.16;
                        w.addParticle(DARK_GREEN, x + Math.cos(bAngle) * d + Math.cos(bAngle + Math.PI * 0.5) * jitter, y + 0.04, z + Math.sin(bAngle) * d + Math.sin(bAngle + Math.PI * 0.5) * jitter, 0, 0.002, 0);
                    }
                    if (glow) w.addParticle(LIGHT_GREEN, x + Math.cos(bAngle) * (bStartD + bLen), y + 0.08, z + Math.sin(bAngle) * (bStartD + bLen), 0, 0.010, 0);
                }
            }
        }
    }

    private static void renderStage1(ClientWorld w, double x, double y, double z, int t, float progress) {
        double reach = 0.6 + progress * (ROOT_REACH - 0.6);
        if (t % 3 == 0) spawnRoots(w, x, y, z, reach, t % 8 == 0);
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = ROOT_ANGLES[w.random.nextInt(ROOT_ANGLES.length)];
                double d = 0.6 + w.random.nextDouble() * reach * 0.8;
                w.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + Math.cos(a) * d, y + 0.1, z + Math.sin(a) * d, 0, 0.015, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = 0.5 + w.random.nextDouble() * 4.0;
                w.addParticle(ParticleTypes.FALLING_SPORE_BLOSSOM, x + Math.cos(angle) * r, y + 5.0 + w.random.nextDouble() * 4.0, z + Math.sin(angle) * r, 0, -0.04, 0);
            }
        }
        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 0.5;
                w.addParticle(GOLD, x + Math.cos(angle) * r, y + 0.05 + w.random.nextDouble() * 1.2 * progress, z + Math.sin(angle) * r, 0, 0.006, 0);
            }
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t % 3 == 0) spawnRoots(w, x, y, z, ROOT_REACH, t % 6 == 0);
        double vineHeight = CANOPY_HEIGHT * progress;
        if (t % 2 == 0) {
            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double baseAngle = ROOT_ANGLES[ri];
                double cx = x + Math.cos(baseAngle) * 0.8;
                double cz = z + Math.sin(baseAngle) * 0.8;
                int vineSteps = Math.max(4, (int)(vineHeight / 0.45));
                for (int s = 0; s < vineSteps; s++) {
                    double fraction = (double) s / vineSteps;
                    double vy = y + fraction * vineHeight;
                    double vR = 0.55 * (1.0 - fraction * 0.5);
                    double twist = baseAngle + fraction * Math.PI * 3.0 + time * 0.025;
                    DustParticleEffect col = (fraction < 0.4) ? ROOT : (fraction < 0.75) ? DARK_GREEN : LIGHT_GREEN;
                    w.addParticle(col, cx + Math.cos(twist) * vR, vy, cz + Math.sin(twist) * vR, 0, 0.004, 0);
                }
                if (vineHeight > 1.5 && t % 4 == 0) w.addParticle(LIGHT_GREEN, cx + (w.random.nextDouble() - 0.5) * 0.3, y + vineHeight, cz + (w.random.nextDouble() - 0.5) * 0.3, 0, 0.015, 0);
            }
        }
        double canopyY = y + CANOPY_HEIGHT;
        double canopyR = 3.5 - progress * 1.8;
        int canopyDensity = (int)(6 + progress * 20);
        if (t % 2 == 0) {
            for (int i = 0; i < canopyDensity; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * canopyR;
                double h = (w.random.nextDouble() - 0.3) * 1.8;
                w.addParticle(w.random.nextFloat() < 0.55f ? PINK : GOLD, x + Math.cos(angle) * r, canopyY + h, z + Math.sin(angle) * r, 0, 0.010, 0);
            }
        }
        if (t % 5 == 0) w.addParticle(ParticleTypes.END_ROD, x + (w.random.nextDouble() - 0.5) * canopyR * 1.4, canopyY, z + (w.random.nextDouble() - 0.5) * canopyR * 1.4, 0, 0.02, 0);
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 2.5;
                double h = 2.0 + w.random.nextDouble() * (CANOPY_HEIGHT - 3.0);
                w.addParticle(ParticleTypes.CHERRY_LEAVES, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, -0.03, 0);
            }
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 1.5;
                w.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + Math.cos(angle) * r, y + 0.2 + w.random.nextDouble() * 3.0, z + Math.sin(angle) * r, 0, 0.01, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress) {
        double canopyR = 1.7 * (1.0 - progress);
        double beamTop = y + CANOPY_HEIGHT;
        if (canopyR > 0.15 && t % 2 == 0) {
            int density = (int)(20 + (1.0 - progress) * 15);
            for (int i = 0; i < density; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * canopyR;
                w.addParticle(w.random.nextFloat() < 0.5f ? PINK : GOLD, x + Math.cos(angle) * r, beamTop + (w.random.nextDouble() - 0.3), z + Math.sin(angle) * r, 0, 0.012, 0);
            }
        }
        if (t % 2 == 0) {
            int beamSteps = (int)(8 + progress * 10);
            for (int s = 0; s < beamSteps; s++) {
                double fraction = (double) s / beamSteps;
                double by = beamTop - fraction * (CANOPY_HEIGHT - 1.8);
                double jitter = 0.55 * (1.0 - fraction) + 0.04;
                DustParticleEffect col = (fraction < 0.4) ? PINK : (fraction < 0.75) ? LIGHT_GREEN : GOLD;
                w.addParticle(col, x + (w.random.nextDouble() - 0.5) * jitter, by, z + (w.random.nextDouble() - 0.5) * jitter, 0, -0.04, 0);
            }
        }
        if (t % 3 == 0) w.addParticle(ParticleTypes.END_ROD, x + (w.random.nextDouble() - 0.5) * 0.3, beamTop, z + (w.random.nextDouble() - 0.5) * 0.3, 0, -0.18, 0);
        if (t % 4 == 0) spawnRoots(w, x, y, z, ROOT_REACH, true);
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = 0.8 + w.random.nextDouble() * 2.2;
                double h = 1.0 + w.random.nextDouble() * (CANOPY_HEIGHT - 2.0);
                w.addParticle(ParticleTypes.CHERRY_LEAVES, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, -0.04, 0);
            }
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 1.5;
                double h = 1.5 + w.random.nextDouble() * 5.0;
                w.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, -0.02, 0);
            }
        }
        if (progress > 0.88f && t % 4 == 0) {
            scatter(w, PINK, x, y + 1.5, z, 6, 0.4, 0.3, 0.4, 0.04);
            w.addParticle(ParticleTypes.FLASH, x, y + 1.5, z, 0, 0, 0);
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int ri = 0; ri < ROOT_ANGLES.length; ri++) {
                double angle = ROOT_ANGLES[ri];
                for (double d = 0.3; d <= ROOT_REACH; d += 0.5)
                    w.addParticle(DARK_GREEN, x + Math.cos(angle) * d, y + 0.05, z + Math.sin(angle) * d, Math.cos(angle) * 0.04, 0.14, Math.sin(angle) * 0.04);
                double[] branches = ROOT_BRANCHES[ri];
                for (double bOff : branches) {
                    double bAngle = angle + bOff;
                    double bStart = ROOT_REACH * BRANCH_START;
                    double bLen = ROOT_REACH * (1.0 - BRANCH_START) * 0.75;
                    for (double d = bStart; d <= bStart + bLen; d += 0.5)
                        w.addParticle(LIGHT_GREEN, x + Math.cos(bAngle) * d, y + 0.05, z + Math.sin(bAngle) * d, Math.cos(bAngle) * 0.04, 0.12, Math.sin(bAngle) * 0.04);
                }
            }
            for (int d = 0; d < 24; d++) {
                double theta = d * Math.PI * 2.0 / 24;
                double phi = Math.PI * 0.35 + w.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.18 + w.random.nextDouble() * 0.10;
                DustParticleEffect col = (d % 2 == 0) ? PINK : GOLD;
                w.addParticle(col, x, y + 1.0, z, Math.sin(phi) * Math.cos(theta) * speed, Math.cos(phi) * speed, Math.sin(phi) * Math.sin(theta) * speed);
            }
            scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 3, 0.2, 0.2, 0.2, 0);
        }
        int burstCount = (int)(10 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r = w.random.nextDouble() * 1.8;
            double h = w.random.nextDouble() * 2.6;
            DustParticleEffect col = switch (i % 4) { case 0 -> DARK_GREEN; case 1 -> LIGHT_GREEN; case 2 -> GOLD; default -> PINK; };
            w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.015, 0);
        }
        if (t % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 3.0;
                w.addParticle(ParticleTypes.CHERRY_LEAVES, x + Math.cos(angle) * r, y + 0.3 + w.random.nextDouble() * 3.5, z + Math.sin(angle) * r, 0, 0.01, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 1.5;
                w.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, x + Math.cos(angle) * r, y + 0.2 + w.random.nextDouble() * 2.5, z + Math.sin(angle) * r, 0, 0.015, 0);
            }
        }
        if (t % 5 == 0) w.addParticle(ParticleTypes.END_ROD, x + (w.random.nextDouble() - 0.5) * 1.6, y + 0.5 + w.random.nextDouble() * 2.2, z + (w.random.nextDouble() - 0.5) * 1.6, 0, 0.025, 0);
        if (progress > 0.78f) {
            scatter(w, PINK, x, y + 1.3, z, 2, 0.5, 0.3, 0.5, 0.005);
            w.addParticle(GOLD, x, y + 1.5, z, 0, 0.004, 0);
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (double angle : ROOT_ANGLES)
            scatter(w, LIGHT_GREEN, x + Math.cos(angle) * 2.0, y + 0.05, z + Math.sin(angle) * 2.0, 3, 0.03, 0.10, 0.03, 0);
        scatter(w, DARK_GREEN,  x, y + 1.0, z, 22, 1.1, 0.9, 1.1, 0.08);
        scatter(w, LIGHT_GREEN, x, y + 1.0, z, 18, 0.9, 0.8, 0.9, 0.07);
        scatter(w, PINK,        x, y + 1.0, z, 16, 0.8, 0.7, 0.8, 0.07);
        scatter(w, GOLD,        x, y + 1.2, z, 12, 0.6, 0.5, 0.6, 0.06);
        scatter(w, ParticleTypes.CHERRY_LEAVES, x, y + 1.0, z, 20, 1.2, 1.0, 1.2, 0.06);
        scatter(w, ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.5, z, 10, 0.8, 0.6, 0.8, 0.04);
        scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.2, 0.1, 0.2, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
