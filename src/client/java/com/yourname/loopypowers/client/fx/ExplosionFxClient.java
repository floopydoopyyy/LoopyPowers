package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class ExplosionFxClient {

    private ExplosionFxClient() {}

    private static final int ULT_WARN_TICKS = 8;

    // ----------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------

    public static void handleIgniteStart(ExplosionIgniteStartPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.SMOKE, p.x(), p.y() + 1.0, p.z(), 18, 0.35, 0.35, 0.35, 0.02);
        });
    }

    public static void handleIgniteTick(ExplosionIgniteTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            float progress = p.progress();
            int smokeCount = 2 + (int)(progress * 10.0f);

            scatter(world, ParticleTypes.SMOKE, p.x(), p.y() + 0.9, p.z(),
                    smokeCount, 0.25, 0.25, 0.25, 0.01);

            if (progress > 0.55f) {
                int flameCount = 1 + (int)((progress - 0.55f) * 10.0f);
                scatter(world, ParticleTypes.FLAME, p.x(), p.y() + 0.9, p.z(),
                        flameCount, 0.18, 0.22, 0.18, 0.005);
            }

            if (p.largesmoke()) {
                scatter(world, ParticleTypes.LARGE_SMOKE, p.x(), p.y() + 1.0, p.z(),
                        2, 0.20, 0.25, 0.20, 0.01);
            }
        });
    }

    public static void handleBurst(ExplosionBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            float power = p.power();
            double x = p.x(), y = p.y(), z = p.z();

            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 0.0, 0.0, 0.0);

            int smokeCount = (int)(power * 15);
            double sp = power * 0.4;
            scatter(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, smokeCount, sp, sp, sp, 0.05);

            int lavaCount = (int)(power * 4);
            double lp = power * 0.2;
            scatter(world, ParticleTypes.LAVA, x, y, z, lavaCount, lp, lp, lp, 0.1);
        });
    }

    public static void handleUltCancel(ExplosionUltCancelPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.LARGE_SMOKE, p.x(), p.y() + 0.8, p.z(), 12, 0.35, 0.25, 0.35, 0.01);
        });
    }

    public static void handleDropZone(ExplosionDropZonePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double gx = p.groundX(), gy = p.groundY(), gz = p.groundZ();
            double radius = p.radius();
            double offset = p.offsetAngle();
            int points = 24;

            for (int i = 0; i < points; i++) {
                double angle = offset + (2 * Math.PI * i) / points;
                world.addParticle(ParticleTypes.FLAME,
                        gx + Math.cos(angle) * radius, gy + 0.1, gz + Math.sin(angle) * radius,
                        0.0, 0.0, 0.0);
            }
        });
    }

    public static void handleUltAmbient(ExplosionUltAmbientPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            world.addParticle(ParticleTypes.SMOKE,
                    p.x() + (world.random.nextDouble() * 2 - 1) * 0.18,
                    p.y() + 0.15,
                    p.z() + (world.random.nextDouble() * 2 - 1) * 0.18,
                    0.0, 0.002, 0.0);

            if (p.showFlame()) {
                world.addParticle(ParticleTypes.FLAME,
                        p.x() + (world.random.nextDouble() * 2 - 1) * 0.12,
                        p.y() + 0.25,
                        p.z() + (world.random.nextDouble() * 2 - 1) * 0.12,
                        0.0, 0.01, 0.0);
            }
        });
    }

    public static void handleFinisherCharge(ExplosionFinisherChargePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            float progress = MathHelper.clamp(1.0f - (p.warnLeft() / (float) ULT_WARN_TICKS), 0.0f, 1.0f);
            int smokeCount = 6  + (int)(progress * 16.0f);
            int flameCount = 2  + (int)(progress * 10.0f);

            scatter(world, ParticleTypes.SMOKE, p.x(), p.y() + 0.20, p.z(), smokeCount, 0.65, 0.05, 0.65, 0.02);
            scatter(world, ParticleTypes.FLAME, p.x(), p.y() + 0.35, p.z(), flameCount, 0.35, 0.12, 0.35, 0.02);

            if (p.showLava()) {
                world.addParticle(ParticleTypes.LAVA,
                        p.x() + (world.random.nextDouble() * 2 - 1) * 0.25,
                        p.y() + 0.25,
                        p.z() + (world.random.nextDouble() * 2 - 1) * 0.25,
                        0.0, 0.0, 0.0);
            }
        });
    }

    // ----------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------

    private static <T extends net.minecraft.particle.ParticleEffect> void scatter(
            ClientWorld world, T type,
            double cx, double cy, double cz,
            int count, double dx, double dy, double dz, double speed) {
        for (int i = 0; i < count; i++) {
            world.addParticle(type,
                    cx + (world.random.nextDouble() * 2 - 1) * dx,
                    cy + (world.random.nextDouble() * 2 - 1) * dy,
                    cz + (world.random.nextDouble() * 2 - 1) * dz,
                    0.0, speed, 0.0);
        }
    }
}
