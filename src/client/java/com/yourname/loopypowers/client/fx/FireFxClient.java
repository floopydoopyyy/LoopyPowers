package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.FireHoverPayload;
import com.yourname.loopypowers.network.payload.FireUltChargePayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

@Environment(EnvType.CLIENT)
public final class FireFxClient {

    private FireFxClient() {}

    public static void handleHover(FireHoverPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y() - 0.4, z = p.z();
            scatter(world, ParticleTypes.FLAME, x, y, z, 8,  0.25, 0.10, 0.25, 0.02);
            scatter(world, ParticleTypes.SMOKE, x, y, z, 4,  0.20, 0.05, 0.20, 0.01);
        });
    }

    public static void handleUltCharge(FireUltChargePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = p.x(), y = p.y(), z = p.z();
            float progress = p.progress();
            double intensity = 0.5 + progress * 2.5;

            scatter(world, ParticleTypes.FLAME, x, y, z,
                    (int)(10 * intensity),
                    0.4 * intensity, 1.2 * intensity, 0.4 * intensity,
                    0.02 * intensity);

            scatter(world, ParticleTypes.LAVA, x, y + 0.5, z,
                    (int)(2 + progress * 10),
                    0.6, 0.8, 0.6, 0.03);

            scatter(world, ParticleTypes.LARGE_SMOKE, x, y, z,
                    (int)(5 * intensity),
                    0.5, 1.5, 0.5, 0.01);

            if (p.nearEnd()) {
                scatter(world, ParticleTypes.SOUL_FIRE_FLAME, x, y + 1.0, z,
                        40, 1.2, 1.5, 1.2, 0.08);
            }
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
