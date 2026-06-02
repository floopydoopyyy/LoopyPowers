package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class HealingFxClient {

    private HealingFxClient() {}

    private static final DustParticleEffect HEAL_DUST = new DustParticleEffect(new Vector3f(0.9f, 0.2f, 0.4f), 1.2f);

    // ---- Passive ----

    public static void handlePassive(HealingPassivePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, HEAL_DUST, p.x(), p.y(), p.z(), 2, 0.3, 0.5, 0.3, 0.01);
        });
    }

    // ---- Secondary absorb ----

    public static void handleAbsorbHit(HealingAbsorbHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            float i = p.intensity();
            int count = 5 + (int)(i * 15);
            scatter(world, ParticleTypes.TOTEM_OF_UNDYING, p.x(), p.y(), p.z(),
                    count,
                    0.2 + i * 0.2, 0.3 + i * 0.2, 0.2 + i * 0.2,
                    0.01 + i * 0.05);
        });
    }

    public static void handleAbsorbTick(HealingAbsorbTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            float i = p.intensity();
            int sparkCount = 2 + (int)(i * 5);
            scatter(world, ParticleTypes.ELECTRIC_SPARK, p.x(), p.y(), p.z(),
                    sparkCount, 0.5, 0.6, 0.5, 0.02 + i * 0.05);
            if (p.endRod()) {
                scatter(world, ParticleTypes.END_ROD, p.x(), p.y(), p.z(), 1, 0.5, 0.6, 0.5, 0.01);
            }
        });
    }

    // ---- Primary cleanse ----

    public static void handleCleanse(HealingCleansePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.6, 0.8, 0.6, 0.05);
            scatter(world, ParticleTypes.FLASH,          x, y, z,  3, 0.7, 0.9, 0.7, 0.1);
            scatter(world, HEAL_DUST,                    x, y, z, 30, 0.6, 0.8, 0.6, 0.05);
        });
    }

    // ---- Burst explosion ----

    public static void handleBurst(HealingBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double px = p.x(), py = p.y(), pz = p.z();
            float capped = Math.min(p.stored(), 50.0f);

            int emitterCount = Math.min(1 + (int)(capped * 0.25f), 6);
            double spread = 0.5 + capped * 0.1;

            for (int i = 0; i < emitterCount; i++) {
                double ox = (world.random.nextDouble() * 2 - 1) * spread;
                double oy = (world.random.nextDouble() * 2 - 1) * spread * 0.6;
                double oz = (world.random.nextDouble() * 2 - 1) * spread;

                world.addParticle(ParticleTypes.EXPLOSION_EMITTER, px + ox, py + oy, pz + oz, 0, 0, 0);
                scatter(world, HEAL_DUST, px + ox, py + oy, pz + oz, 15, 0.4, 0.4, 0.4, 0.05);
            }

            if (capped >= 8.0f) {
                burst(world, ParticleTypes.END_ROD, px, py, pz, 40, 0.8, 0.25);
            }
            if (capped >= 16.0f) {
                burst(world, ParticleTypes.FLASH,            px, py, pz, 10, 0.8, 0.35);
                burst(world, ParticleTypes.TOTEM_OF_UNDYING, px, py, pz, 20, 0.5, 0.3);
            }
        });
    }

    // ---- Expelled per-effect ----

    public static void handleExpelled(HealingExpelledPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            double vx = p.dirX() * 0.4, vy = p.dirY() * 0.4, vz = p.dirZ() * 0.4;
            for (int i = 0; i < 6; i++) {
                world.addParticle(ParticleTypes.SMOKE, x, y, z, vx, vy, vz);
            }
            if (p.hasFire()) {
                scatter(world, ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.4, 0.3, 0.02);
            }
        });
    }

    // ---- Ultimate ----

    public static void handleUltTick(HealingUltTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            scatter(world, ParticleTypes.FIREWORK, x, y, z, 2, 0.4, 0.6, 0.4, 0.01);
            scatter(world, HEAL_DUST,              x, y, z, 3, 0.5, 0.6, 0.5, 0.02);
            if (p.endRod()) {
                scatter(world, ParticleTypes.END_ROD, x, y, z, 8, 0.6, 0.8, 0.6, 0.05);
            }
        });
    }

    public static void handleUltPhase(HealingUltPhasePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.FLASH, p.x(), p.y(), p.z(), 10, 0.5, 0.6, 0.5, 0.1);
        });
    }

    // ---- Helpers ----

    private static <T extends ParticleEffect> void scatter(
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

    // Mirrors the server's spawnBurst — gaussian outward burst with velocity
    private static <T extends ParticleEffect> void burst(
            ClientWorld world, T type,
            double x, double y, double z,
            int count, double speed, double spread) {
        for (int i = 0; i < count; i++) {
            double dx = world.random.nextGaussian();
            double dy = world.random.nextGaussian() * 0.6;
            double dz = world.random.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 0.0001) continue;
            dx /= len; dy /= len; dz /= len;
            double distance = spread * (0.5 + world.random.nextDouble());
            double velScale = speed * (0.8 + world.random.nextDouble() * 0.7);
            world.addParticle(type,
                    x + dx * distance, y + dy * distance, z + dz * distance,
                    dx * velScale, dy * velScale, dz * velScale);
        }
    }
}
