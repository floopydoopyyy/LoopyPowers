package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class DarknessFxClient {

    private DarknessFxClient() {}

    private static final DustParticleEffect DARK_DUST =
            new DustParticleEffect(new Vec3d(0.05, 0.05, 0.05).toVector3f(), 1.4f);
    private static final DustParticleEffect BLACK_DUST =
            new DustParticleEffect(new Vec3d(0.01, 0.01, 0.01).toVector3f(), 1.8f);

    // Blackout sphere constants (mirror server-side values)
    private static final int    BLACKOUT_RADIUS          = 16;
    private static final double BLACKOUT_HEIGHT          = 13.0;
    private static final int    BLACKOUT_RING_POINTS     = 48;
    private static final int    BLACKOUT_VERTICAL_LAYERS = 20;
    private static final int    BLACKOUT_INNER_PARTICLES = 100;
    private static final double BLACKOUT_INNER_SPREAD    = BLACKOUT_RADIUS * 0.9;

    // ----------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------

    public static void handleBackstabFx(DarknessBackstabFxPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y(), z = payload.z();

            scatter(world, ParticleTypes.SMOKE,       x, y, z, 12, 0.3, 0.4, 0.3, 0.02);
            scatter(world, ParticleTypes.LARGE_SMOKE, x, y, z, 6,  0.2, 0.3, 0.2, 0.01);

            // directional streak from behind
            double sx = x + payload.dirX() * 0.5;
            double sz = z + payload.dirZ() * 0.5;
            scatter(world, ParticleTypes.SMOKE, sx, y, sz, 6, 0.1, 0.1, 0.1, 0.01);
        });
    }

    public static void handleExposedFx(DarknessExposedFxPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y(), z = payload.z();
            double dirX = payload.dirX(), dirZ = payload.dirZ();

            scatter(world, ParticleTypes.SMOKE,       x, y, z, 20, 0.4, 0.5, 0.4, 0.04);
            scatter(world, ParticleTypes.LARGE_SMOKE, x, y, z, 10, 0.3, 0.4, 0.3, 0.02);
            scatter(world, BLACK_DUST,                x, y, z, 12, 0.25, 0.3, 0.25, 0.0);

            // directional ring: count=0 in original means direct-velocity spawn
            for (int i = 0; i < 8; i++) {
                double angle  = world.random.nextDouble() * Math.PI * 2;
                double radius = 0.6;
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                world.addParticle(BLACK_DUST, px, y, pz, dirX, 0.05, dirZ);
            }
        });
    }

    public static void handleComboFx(DarknessComboFxPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CRIT, payload.x(), payload.y(), payload.z(), 15, 0.3, 0.3, 0.3, 0.1);
        });
    }

    public static void handleMistEnter(DarknessMistEnterPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, DARK_DUST, payload.x(), payload.y(), payload.z(), 35, 0.8, 1.0, 0.8, 0.02);
        });
    }

    public static void handleMistTrail(DarknessMistTrailPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, DARK_DUST, payload.x(), payload.y(), payload.z(), 12, 0.6, 0.8, 0.6, 0.01);
        });
    }

    public static void handleUltActivate(DarknessUltActivatePayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y() + 1.0, z = payload.z();
            scatter(world, ParticleTypes.LARGE_SMOKE, x, y, z, 40, 1.0, 0.7, 1.0, 0.03);
            scatter(world, ParticleTypes.SMOKE,       x, y, z, 55, 2.0, 1.0, 2.0, 0.01);
        });
    }

    public static void handleBlackoutFx(DarknessBlackoutFxPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = payload.x(), cy = payload.y(), cz = payload.z();

            // Center column
            scatter(world, ParticleTypes.SMOKE,       cx, cy + 1.0, cz, 12, 2.0, 1.5, 2.0, 0.01);
            scatter(world, ParticleTypes.LARGE_SMOKE, cx, cy + 1.0, cz, 6,  1.5, 1.0, 1.5, 0.01);

            // Interior fill
            for (int i = 0; i < BLACKOUT_INNER_PARTICLES; i++) {
                double angle  = world.random.nextDouble() * Math.PI * 2;
                double radius = Math.sqrt(world.random.nextDouble()) * BLACKOUT_INNER_SPREAD;
                double x = cx + Math.cos(angle) * radius;
                double z = cz + Math.sin(angle) * radius;
                double y = cy + (world.random.nextDouble() * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT);

                world.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.005, 0.0);
                if (world.random.nextFloat() < 0.35f) {
                    world.addParticle(BLACK_DUST,
                            x + (world.random.nextDouble() * 2 - 1) * 0.02,
                            y + (world.random.nextDouble() * 2 - 1) * 0.02,
                            z + (world.random.nextDouble() * 2 - 1) * 0.02,
                            0.0, 0.0, 0.0);
                }
            }

            // Outer sphere shell
            for (int ly = 0; ly < BLACKOUT_VERTICAL_LAYERS; ly++) {
                double heightOffset = ((double) ly / (BLACKOUT_VERTICAL_LAYERS - 1)) * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT;
                double layerRadius  = BLACKOUT_RADIUS * Math.sqrt(1 - Math.pow(heightOffset / BLACKOUT_HEIGHT, 2));

                for (int i = 0; i < BLACKOUT_RING_POINTS; i++) {
                    double angle = (Math.PI * 2.0 * i) / BLACKOUT_RING_POINTS;
                    double x     = cx + Math.cos(angle) * layerRadius;
                    double z     = cz + Math.sin(angle) * layerRadius;
                    double yPos  = cy + heightOffset;

                    world.addParticle(ParticleTypes.SMOKE, x, yPos, z, 0.0, 0.0, 0.0);
                    if (i % 4 == 0) {
                        world.addParticle(ParticleTypes.LARGE_SMOKE,
                                x + (world.random.nextDouble() * 2 - 1) * 0.05,
                                yPos + (world.random.nextDouble() * 2 - 1) * 0.05,
                                z + (world.random.nextDouble() * 2 - 1) * 0.05,
                                0.0, 0.0, 0.0);
                    }
                }
            }
        });
    }

    public static void handleBlackoutEnd(DarknessBlackoutEndPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD,
                    payload.x(), payload.y() + 1.0, payload.z(),
                    25, 1.0, 0.6, 1.0, 0.03);
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
