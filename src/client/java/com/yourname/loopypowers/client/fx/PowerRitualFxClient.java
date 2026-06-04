package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class PowerRitualFxClient {

    private PowerRitualFxClient() {}

    private static final DustParticleEffect RITUAL_PINK       = new DustParticleEffect(new Vector3f(1.0f, 0.4f,  0.8f),  1.3f);
    private static final DustParticleEffect RITUAL_WHITE_PINK = new DustParticleEffect(new Vector3f(1.0f, 0.7f,  0.9f),  1.1f);
    private static final DustParticleEffect RITUAL_WHITE      = new DustParticleEffect(new Vector3f(1.0f, 1.0f,  1.0f),  1.2f);
    private static final DustParticleEffect RITUAL_PALE       = new DustParticleEffect(new Vector3f(0.9f, 0.85f, 1.0f),  0.9f);

    private static final double SHINE_START_HEIGHT = 12.0;

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
        double radius      = 1.2 + progress * 0.8;
        int    orbitPoints = 6;
        for (int i = 0; i < orbitPoints; i++) {
            double angle = (time * 0.07) + (i * Math.PI * 2.0 / orbitPoints);
            double px    = x + Math.cos(angle) * radius;
            double pz    = z + Math.sin(angle) * radius;
            double py    = y + 0.8 + Math.sin(time * 0.08 + i) * 0.3;
            w.addParticle(RITUAL_PINK, px, py, pz, 0, 0.005, 0);
            w.addParticle(RITUAL_PALE, px, py + 0.2, pz, 0, 0.008, 0);
        }
        if (t % 3 == 0) {
            int circlePoints = 16;
            for (int i = 0; i < circlePoints; i++) {
                double angle = Math.PI * 2.0 * i / circlePoints;
                w.addParticle(RITUAL_PINK, x + Math.cos(angle) * 2.0, y + 0.05, z + Math.sin(angle) * 2.0, 0, 0.003, 0);
            }
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        int    spiralPoints  = (int)(6 + progress * 10);
        double spiralRadius  = 1.0 + progress * 0.5;
        for (int i = 0; i < spiralPoints; i++) {
            double angle     = (time * 0.09) + (i * Math.PI * 2.0 / spiralPoints);
            double heightVar = (i / (double) spiralPoints) * 2.0;
            double px = x + Math.cos(angle) * spiralRadius;
            double pz = z + Math.sin(angle) * spiralRadius;
            w.addParticle(RITUAL_PINK,       px, y + heightVar,       pz, 0, 0.006, 0);
            w.addParticle(RITUAL_WHITE_PINK, px, y + heightVar + 0.15, pz, 0, 0.005, 0);
        }
        double shineY       = y + SHINE_START_HEIGHT;
        int    shineDensity = (int)(5 + progress * 20);
        for (int i = 0; i < shineDensity; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = w.random.nextDouble() * 1.5;
            w.addParticle(RITUAL_WHITE_PINK,
                    x + Math.cos(angle) * r,
                    shineY + (w.random.nextDouble() - 0.5) * 1.5,
                    z + Math.sin(angle) * r,
                    0, 0.02, 0);
        }
        if (t % 3 == 0)
            w.addParticle(ParticleTypes.END_ROD,
                    x + (w.random.nextDouble() - 0.5) * 2.0, shineY, z + (w.random.nextDouble() - 0.5) * 2.0,
                    0, 0.03, 0);
        if (t % 4 == 0) {
            for (int i = 0; i < 8; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r     = w.random.nextDouble() * 2.5;
                w.addParticle(RITUAL_WHITE, x + Math.cos(angle) * r, y + 0.05, z + Math.sin(angle) * r, 0, 0.015, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double shineY      = y + SHINE_START_HEIGHT - (SHINE_START_HEIGHT - 2.5) * progress;
        int    shineDensity = 20 + (int)(progress * 15);
        double shineRadius = 1.5 - progress * 0.8;
        for (int i = 0; i < shineDensity; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = w.random.nextDouble() * shineRadius;
            w.addParticle(RITUAL_WHITE_PINK,
                    x + Math.cos(angle) * r,
                    shineY + (w.random.nextDouble() - 0.3) * 1.2,
                    z + Math.sin(angle) * r,
                    0, 0.015 + progress * 0.02, 0);
        }
        if (t % 2 == 0) {
            double beamSegments = 8;
            for (int i = 0; i < beamSegments; i++) {
                double beamT = i / beamSegments;
                double beamY = y + 1.0 + (shineY - y - 1.0) * beamT;
                w.addParticle(RITUAL_PINK,
                        x + (w.random.nextDouble() - 0.5) * 0.2, beamY, z + (w.random.nextDouble() - 0.5) * 0.2,
                        0, 0.01, 0);
            }
        }
        if (progress > 0.7f && t % 3 == 0)
            scatter(w, ParticleTypes.END_ROD, x, shineY, z, 2, 0.1, 0.1, 0.1, 0.05);
        for (int i = 0; i < 8; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 8);
            w.addParticle(RITUAL_WHITE, x + Math.cos(angle) * 1.2, y + 1.0, z + Math.sin(angle) * 1.2, 0, 0.005, 0);
        }
        if (progress > 0.95f) {
            scatter(w, RITUAL_WHITE_PINK, x, y + 1.5, z, 10, 0.5, 0.5, 0.5, 0.06);
            w.addParticle(ParticleTypes.FLASH, x, y + 1.5, z, 0, 0, 0);
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress) {
        if (t == 1) {
            scatter(w, RITUAL_WHITE_PINK, x, y + 1.0, z, 40, 1.2, 1.0, 1.2, 0.1);
            scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 3, 0.3, 0.3, 0.3, 0);
        }
        int burstCount = (int)(5 + (1.0 - progress) * 10);
        for (int i = 0; i < burstCount; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r     = w.random.nextDouble() * 1.8;
            double h     = w.random.nextDouble() * 2.5;
            DustParticleEffect col = w.random.nextFloat() < 0.5f ? RITUAL_PINK : RITUAL_WHITE_PINK;
            w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.02, 0);
        }
        if (t % 8 == 0)
            scatter(w, ParticleTypes.END_ROD, x, y + 1.0, z, 3, 0.4, 0.3, 0.4, 0.06);
        if (progress > 0.75f)
            scatter(w, RITUAL_PALE, x, y + 1.0, z, 4, 0.5, 0.4, 0.5, 0.01);
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        scatter(w, RITUAL_PINK,       x, y + 1.0, z, 30, 1.0, 0.8, 1.0, 0.08);
        scatter(w, RITUAL_WHITE_PINK, x, y + 1.0, z, 20, 0.8, 0.6, 0.8, 0.06);
        scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.2, 0.1, 0.2, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
