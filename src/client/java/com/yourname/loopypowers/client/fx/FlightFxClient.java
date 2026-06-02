package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class FlightFxClient {

    private FlightFxClient() {}

    public static void handleKnock(FlightKnockPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD, p.x(), p.y() + 1.0, p.z(), 8, 0.35, 0.35, 0.35, 0.02);
        });
    }

    public static void handleGust(FlightGustPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.EXPLOSION, p.x(), p.y() + 0.8, p.z(), 12, 0.25, 0.15, 0.25, 0.02);
        });
    }

    public static void handleGustTrail(FlightGustTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.FIREWORK, p.x(), p.y() + 0.6, p.z(), 2, 0.10, 0.06, 0.10, 0.005);
        });
    }

    public static void handleUpdraft(FlightUpdraftPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.EXPLOSION, p.x(), p.y() + 0.3, p.z(), 20, 0.35, 0.25, 0.35, 0.03);
        });
    }

    public static void handleBoomStart(FlightBoomStartPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.ENCHANTED_HIT, p.x(), p.y() + 1.0, p.z(), 0.0, 0.0, 0.0);
        });
    }

    public static void handleBoomWindup(FlightBoomWindupPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y() + 1.0, z = p.z();
            scatter(world, ParticleTypes.CLOUD,          x, y, z, 6, 0.35, 0.45, 0.35, 0.01);
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 8, 0.45, 0.55, 0.45, 0.02);
        });
    }

    public static void handleBoomTunnel(FlightBoomTunnelPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            // pos already has +1 Y from server; back = -dir
            Vec3d pos  = new Vec3d(p.x(), p.y(), p.z());
            Vec3d back = new Vec3d(-p.dirX(), -p.dirY(), -p.dirZ());

            for (int i = 0; i < 3; i++) {
                Vec3d pt = pos.add(back.multiply(i * 1.5));
                scatter(world, ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 3, 0.4, 0.4, 0.4, 0.02);
                if (world.random.nextFloat() < 0.25f) {
                    world.addParticle(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 0.0, 0.0, 0.0);
                }
            }
        });
    }

    public static void handleBoomImpact(FlightBoomImpactPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y() + 1.0, p.z(), 0.0, 0.0, 0.0);
        });
    }

    public static void handleTrail(FlightTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD, p.x(), p.y(), p.z(), 2, 0.08, 0.06, 0.08, 0.005);
        });
    }

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
