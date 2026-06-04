package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class ElementalRitualFxClient {

    private ElementalRitualFxClient() {}

    private static final DustParticleEffect FIRE_ORANGE = new DustParticleEffect(new Vector3f(1.0f,  0.35f, 0.0f),  1.5f);
    private static final DustParticleEffect FIRE_RED    = new DustParticleEffect(new Vector3f(0.9f,  0.05f, 0.0f),  1.3f);
    private static final DustParticleEffect ICE_BLUE    = new DustParticleEffect(new Vector3f(0.35f, 0.8f,  1.0f),  1.4f);
    private static final DustParticleEffect ICE_WHITE   = new DustParticleEffect(new Vector3f(0.8f,  0.95f, 1.0f),  1.1f);
    private static final DustParticleEffect LIGHT_YELLOW= new DustParticleEffect(new Vector3f(1.0f,  0.95f, 0.1f),  1.5f);
    private static final DustParticleEffect LIGHT_WHITE = new DustParticleEffect(new Vector3f(0.9f,  0.95f, 1.0f),  1.2f);
    private static final DustParticleEffect EARTH_BROWN = new DustParticleEffect(new Vector3f(0.45f, 0.28f, 0.08f), 1.4f);
    private static final DustParticleEffect EARTH_GREEN = new DustParticleEffect(new Vector3f(0.2f,  0.55f, 0.1f),  1.2f);
    private static final DustParticleEffect WATER_TEAL  = new DustParticleEffect(new Vector3f(0.1f,  0.6f,  0.8f),  1.3f);
    private static final DustParticleEffect WATER_CYAN  = new DustParticleEffect(new Vector3f(0.4f,  0.9f,  0.9f),  1.0f);
    private static final DustParticleEffect AIR_PALE    = new DustParticleEffect(new Vector3f(0.85f, 0.95f, 1.0f),  0.9f);

    private static final DustParticleEffect[] COL_PRIMARY   = { FIRE_ORANGE, ICE_BLUE,  LIGHT_YELLOW, WATER_TEAL };
    private static final DustParticleEffect[] COL_SECONDARY = { FIRE_RED,    ICE_WHITE, LIGHT_WHITE,  WATER_CYAN };

    private static final double COLUMN_HEIGHT = 10.0;

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
        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2.0 * i / 6 + time * 0.02;
                double r = 2.5 + Math.sin(time * 0.05 + i) * 0.3;
                w.addParticle(EARTH_BROWN, x + Math.cos(angle) * r, y + 0.05, z + Math.sin(angle) * r, 0, 0.005, 0);
                w.addParticle(EARTH_GREEN, x + Math.cos(angle + 0.3) * r, y + 0.1, z + Math.sin(angle + 0.3) * r, 0, 0.003, 0);
            }
        }
        int waterPoints = (int)(4 + progress * 6);
        for (int i = 0; i < waterPoints; i++) {
            double angle = -(time * 0.06) + (i * Math.PI * 2.0 / waterPoints);
            double r = 1.6;
            double wy = y + 0.6 + Math.sin(time * 0.07 + i) * 0.25;
            w.addParticle(WATER_TEAL, x + Math.cos(angle) * r, wy, z + Math.sin(angle) * r, 0, 0.004, 0);
            if (t % 4 == 0) w.addParticle(WATER_CYAN, x + Math.cos(angle) * r, wy + 0.15, z + Math.sin(angle) * r, 0, 0.006, 0);
        }
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = w.random.nextDouble() * Math.PI * 2;
                double r = w.random.nextDouble() * 1.2;
                double h = 1.5 + w.random.nextDouble() * 2.5;
                w.addParticle(AIR_PALE, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.01, 0);
            }
        }
    }

    private static void renderStage2(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double columnRadius = 3.5;
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx = x + Math.cos(angle) * columnRadius;
            double cz = z + Math.sin(angle) * columnRadius;
            double columnHeight = COLUMN_HEIGHT * progress;
            int colSteps = (int)(columnHeight / 0.5) + 1;
            for (int s = 0; s < colSteps; s++) {
                double cy = y + s * 0.5;
                if (t % 3 == 0) w.addParticle(COL_PRIMARY[col], cx + (w.random.nextDouble() - 0.5) * 0.4, cy, cz + (w.random.nextDouble() - 0.5) * 0.4, 0, 0.008, 0);
                if (t % 5 == 0 && s % 2 == 0) w.addParticle(COL_SECONDARY[col], cx, cy + 0.15, cz, 0, 0.006, 0);
            }
            if (columnHeight > 2.0) scatter(w, COL_PRIMARY[col], cx, y + columnHeight, cz, 2, 0.3, 0.2, 0.3, 0.02);
        }
        if (t % 4 == 0) {
            double apexY = y + COLUMN_HEIGHT * progress;
            for (int i = 0; i < 5; i++) {
                double a = (time * 0.12) + (i * Math.PI * 2.0 / 5);
                w.addParticle(AIR_PALE, x + Math.cos(a) * (columnRadius * 0.6), apexY, z + Math.sin(a) * (columnRadius * 0.6), 0, 0.01, 0);
            }
        }
    }

    private static void renderStage3(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        double surgeRadius = 3.5 - progress * 2.5;
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx = x + Math.cos(angle) * surgeRadius;
            double cz = z + Math.sin(angle) * surgeRadius;
            int colSteps = (int)(COLUMN_HEIGHT * (0.6 + progress * 0.4));
            for (int s = 0; s < colSteps; s++) {
                if (t % 2 == 0) w.addParticle(COL_PRIMARY[col], cx + (w.random.nextDouble() - 0.5) * (0.4 + progress * 0.6), y + s * 0.45, cz + (w.random.nextDouble() - 0.5) * (0.4 + progress * 0.6), 0, 0.012, 0);
            }
            if (t % 5 == 0) {
                double toX = (x - cx) * (0.08 + progress * 0.06);
                double toY = (0.02 + progress * 0.02);
                double toZ = (z - cz) * (0.08 + progress * 0.06);
                w.addParticle(COL_SECONDARY[col], cx, y + COLUMN_HEIGHT * 0.5, cz, toX, toY, toZ);
            }
        }
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double a = (time * 0.1) + (col * Math.PI * 2.0 / COL_PRIMARY.length);
            double r = 1.4 - progress * 0.5;
            w.addParticle(COL_PRIMARY[col], x + Math.cos(a) * r, y + 1.0, z + Math.sin(a) * r, 0, 0.01, 0);
        }
    }

    private static void renderStage4(ClientWorld w, double x, double y, double z, int t, float progress, long time) {
        if (t == 1) {
            for (DustParticleEffect col : COL_PRIMARY) scatter(w, col, x, y + 1.0, z, 12, 1.2, 1.0, 1.2, 0.1);
            scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 3, 0.2, 0.2, 0.2, 0);
        }
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            DustParticleEffect col = COL_PRIMARY[i % COL_PRIMARY.length];
            double angle = w.random.nextDouble() * Math.PI * 2;
            double r = w.random.nextDouble() * 1.8;
            double h = w.random.nextDouble() * 2.5;
            w.addParticle(col, x + Math.cos(angle) * r, y + h, z + Math.sin(angle) * r, 0, 0.018, 0);
        }
        if (t % 4 == 0) {
            w.addParticle(ParticleTypes.FLAME, x + (w.random.nextDouble() - 0.5) * 1.5, y + w.random.nextDouble() * 2.0, z + (w.random.nextDouble() - 0.5) * 1.5, 0, 0.025, 0);
            w.addParticle(ParticleTypes.SNOWFLAKE, x + (w.random.nextDouble() - 0.5) * 2.0, y + 1.0 + w.random.nextDouble() * 1.5, z + (w.random.nextDouble() - 0.5) * 2.0, 0, 0.015, 0);
        }
        if (t % 5 == 0) {
            w.addParticle(ParticleTypes.END_ROD, x + (w.random.nextDouble() - 0.5) * 1.2, y + 0.5 + w.random.nextDouble() * 1.8, z + (w.random.nextDouble() - 0.5) * 1.2, 0, 0.03, 0);
        }
        if (progress > 0.8f) {
            for (DustParticleEffect sec : COL_SECONDARY) w.addParticle(sec, x, y + 1.2, z, 0, 0.006, 0);
        }
    }

    private static void renderComplete(ClientWorld w, double x, double y, double z) {
        for (DustParticleEffect col : COL_PRIMARY) scatter(w, col, x, y + 1.0, z, 10, 1.0, 0.8, 1.0, 0.09);
        scatter(w, ParticleTypes.FLASH, x, y + 1.0, z, 2, 0.2, 0.1, 0.2, 0);
    }

    static <T extends ParticleEffect> void scatter(ClientWorld w, T type, double cx, double cy, double cz, int n, double dx, double dy, double dz, double spd) {
        for (int i = 0; i < n; i++)
            w.addParticle(type, cx + (w.random.nextDouble() * 2 - 1) * dx, cy + (w.random.nextDouble() * 2 - 1) * dy, cz + (w.random.nextDouble() * 2 - 1) * dz, 0, spd, 0);
    }
}
