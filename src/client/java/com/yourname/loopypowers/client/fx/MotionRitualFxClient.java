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
public final class MotionRitualFxClient {

    private MotionRitualFxClient() {}

    private static final DustParticleEffect BLUE_DEEP   = new DustParticleEffect(new Vector3f(0.05f, 0.25f, 0.85f), 1.4f);
    private static final DustParticleEffect BLUE_LIGHT  = new DustParticleEffect(new Vector3f(0.15f, 0.60f, 1.00f), 1.3f);
    private static final DustParticleEffect CYAN_BRIGHT = new DustParticleEffect(new Vector3f(0.20f, 0.90f, 1.00f), 1.1f);
    private static final DustParticleEffect BLUE_PALE   = new DustParticleEffect(new Vector3f(0.70f, 0.85f, 1.00f), 1.0f);
    private static final DustParticleEffect NAVY        = new DustParticleEffect(new Vector3f(0.00f, 0.08f, 0.35f), 1.5f);

    // ring data: { radius, heightOffset, angularSpeedPerTick, yWobbleAmplitude, wobbleFreqMultiplier }
    private static final double[][] RINGS = {
            { 2.5, 0.4,  0.08, 0.00, 0 },
            { 1.8, 1.0, -0.12, 0.55, 1 },
            { 2.1, 0.7,  0.06, 0.00, 0 },
            { 1.3, 1.5, -0.10, 0.70, 2 },
    };
    private static final DustParticleEffect[] RING_PALETTE = { BLUE_DEEP, BLUE_LIGHT, CYAN_BRIGHT, BLUE_PALE };

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
        int    pulseTimer  = t % 12;
        double shockRadius = pulseTimer * 0.35;
        if (shockRadius > 0.3 && t % 2 == 0) {
            int shockPoints = 14;
            for (int i = 0; i < shockPoints; i++) {
                double angle = Math.PI * 2.0 * i / shockPoints;
                DustParticleEffect col = (pulseTimer < 6) ? BLUE_LIGHT : BLUE_DEEP;
                w.addParticle(col, x + Math.cos(angle) * shockRadius, y + 0.05, z + Math.sin(angle) * shockRadius, 0, 0.003, 0);
            }
        }
        if (t % 5 == 0) {
            int    rays = 8;
            double maxR = 1.5 + progress * 1.5;
            for (int ri = 0; ri < rays; ri++) {
                double angle = ri * Math.PI * 2.0 / rays;
                for (double d = 0.4; d <= maxR; d += 0.5)
                    w.addParticle(BLUE_DEEP, x + Math.cos(angle) * d, y + 0.1, z + Math.sin(angle) * d, 0, 0.015, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double h = w.random.nextDouble() * (1.5 + progress * 1.5);
                w.addParticle(BLUE_LIGHT, x + (w.random.nextDouble() - 0.5) * 0.4, y + h, z + (w.random.nextDouble() - 0.5) * 0.4, 0, 0.010, 0);
            }
            w.addParticle(ParticleTypes.SWEEP_ATTACK,
                    x + (w.random.nextDouble() - 0.5) * 1.5, y + 0.5 + w.random.nextDouble(), z + (w.random.nextDouble() - 0.5) * 1.5,
                    0, 0, 0);
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        int arcPoints = (int)(8 + progress * 10);
        for (int ri = 0; ri < RINGS.length; ri++) {
            double[] ring    = RINGS[ri];
            double   r       = ring[0];
            double   baseY   = y + ring[1];
            double   speed   = ring[2];
            double   wobble  = ring[3];
            double   wobFreq = ring[4];
            DustParticleEffect col = RING_PALETTE[ri];
            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double yy    = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);
                if (t % 2 == 0 || i < 4)
                    w.addParticle(col, x + Math.cos(theta) * r, yy, z + Math.sin(theta) * r, 0, 0.005, 0);
                if (i == arcPoints - 1 && t % 3 == 0)
                    w.addParticle(NAVY, x + Math.cos(theta - 0.15) * r, yy, z + Math.sin(theta - 0.15) * r, 0, 0.003, 0);
            }
        }
        if (t % 8 == 0) {
            int    rays     = 6;
            double gyroTime = time * 0.03;
            for (int ri = 0; ri < rays; ri++) {
                double angle = ri * Math.PI * 2.0 / rays + gyroTime;
                w.addParticle(BLUE_LIGHT,
                        x + Math.cos(angle) * 1.2, y + 0.5, z + Math.sin(angle) * 1.2,
                        Math.cos(angle) * 0.05, 0.02, Math.sin(angle) * 0.05);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double shrink    = 1.0 - progress * 0.65;
        double speedMult = 1.0 + progress * 1.5;
        int arcPoints = (int)(12 + progress * 8);
        for (int ri = 0; ri < RINGS.length; ri++) {
            double[] ring   = RINGS[ri];
            double   r      = ring[0] * shrink;
            double   baseY  = y + ring[1];
            double   speed  = ring[2] * speedMult;
            double   wobble = ring[3];
            double   wobFreq = ring[4];
            DustParticleEffect col = RING_PALETTE[ri];
            for (int i = 0; i < arcPoints; i++) {
                double theta = (time * speed) - (i * Math.PI * 2.0 / (arcPoints * 1.8));
                double yy    = baseY + (wobble > 0 ? Math.sin(theta * (wobFreq + 1)) * wobble : 0);
                if (t % 2 == 0)
                    w.addParticle(col, x + Math.cos(theta) * r, yy, z + Math.sin(theta) * r, 0, 0.008, 0);
            }
        }
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle  = w.random.nextDouble() * Math.PI * 2;
                double srcR   = 3.5 + w.random.nextDouble() * 1.5;
                double srcY   = y + w.random.nextDouble() * 2.0;
                Vec3d  from   = new Vec3d(x + Math.cos(angle) * srcR, srcY, z + Math.sin(angle) * srcR);
                Vec3d  toward = new Vec3d(x, y + 1, z).subtract(from).normalize().multiply(0.10 + progress * 0.06);
                DustParticleEffect col = w.random.nextBoolean() ? BLUE_LIGHT : CYAN_BRIGHT;
                w.addParticle(col, from.x, from.y, from.z, toward.x, toward.y, toward.z);
            }
        }
        if (t % 10 == 0) {
            for (double shockY : new double[]{ 0.5, 1.3 }) {
                int shockPoints = 16;
                for (int i = 0; i < shockPoints; i++) {
                    double angle = Math.PI * 2.0 * i / shockPoints;
                    double vel   = 0.06 + progress * 0.04;
                    w.addParticle(CYAN_BRIGHT, x + Math.cos(angle) * 0.3, y + shockY, z + Math.sin(angle) * 0.3,
                            Math.cos(angle) * vel, 0.005, Math.sin(angle) * vel);
                }
            }
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            for (int d = 0; d < 8; d++) {
                double angle = d * Math.PI * 2.0 / 8;
                for (double r = 0.5; r <= 3.0; r += 0.5)
                    w.addParticle(BLUE_LIGHT, x + Math.cos(angle) * r, y + 0.5, z + Math.sin(angle) * r,
                            Math.cos(angle) * 0.08, 0.02, Math.sin(angle) * 0.08);
            }
            scatter(w, CYAN_BRIGHT, x, y + 1.0, z, 16, 1.5, 1.2, 1.5, 0.12);
            for (int i = 0; i < 4; i++)
                w.addParticle(ParticleTypes.SWEEP_ATTACK, x + (w.random.nextDouble() - 0.5) * 0.8, y + 1.0, z + (w.random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
        }
        int rayCount = (int)(12 + (1.0 - progress) * 12);
        if (t % 2 == 0) {
            for (int r = 0; r < rayCount; r++) {
                double angle = r * Math.PI * 2.0 / rayCount + (t * 0.07);
                double vel   = 0.08 + (1.0 - progress) * 0.05;
                double h     = 0.3 + w.random.nextDouble() * 2.0;
                DustParticleEffect col = switch (r % 3) {
                    case 0  -> BLUE_DEEP;
                    case 1  -> BLUE_LIGHT;
                    default -> CYAN_BRIGHT;
                };
                w.addParticle(col, x + Math.cos(angle) * 0.5, y + h, z + Math.sin(angle) * 0.5,
                        Math.cos(angle) * vel, 0.01, Math.sin(angle) * vel);
            }
        }
        if (t % 6 == 0)
            w.addParticle(ParticleTypes.SWEEP_ATTACK,
                    x + (w.random.nextDouble() - 0.5) * 1.5, y + 0.5 + w.random.nextDouble() * 1.5, z + (w.random.nextDouble() - 0.5) * 1.5,
                    0, 0, 0);
        if (progress > 0.80f) {
            for (int i = 0; i < 3; i++) {
                w.addParticle(BLUE_PALE, x, y + 1.0, z, 0, 0.004, 0);
                w.addParticle(NAVY,      x, y + 0.5, z, 0, 0.003, 0);
            }
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (int d = 0; d < 8; d++) {
            double angle = d * Math.PI * 2.0 / 8;
            scatter(w, BLUE_LIGHT, x + Math.cos(angle) * 0.5, y + 1.0, z + Math.sin(angle) * 0.5,
                    4, Math.cos(angle) * 0.10, 0.05, Math.sin(angle) * 0.10, 0);
        }
        scatter(w, CYAN_BRIGHT, x, y + 1.0, z, 12, 1.2, 1.0, 1.2, 0.09);
        scatter(w, BLUE_PALE,   x, y + 1.0, z,  8, 0.8, 0.7, 0.8, 0.05);
        for (int i = 0; i < 3; i++)
            w.addParticle(ParticleTypes.SWEEP_ATTACK, x + (w.random.nextDouble() - 0.5) * 0.5, y + 1.0, z + (w.random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
