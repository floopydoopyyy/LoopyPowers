package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class TeleportFxClient {

    private TeleportFxClient() {}

    private static final DustParticleEffect FRENZY_DUST = new DustParticleEffect(new Vector3f(0.55f, 0.0f, 0.85f), 1.5f);
    private static final DustParticleEffect FRENZY_RING = new DustParticleEffect(new Vector3f(0.85f, 0.2f, 1.0f), 1.2f);

    private static final double ULTIMATE_AURA_RADIUS = 11.0;

    // ---- Passive dodge burst ----

    public static void handleDodge(TeleportDodgePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.PORTAL, p.x(), p.y(), p.z(), 40, 0.4, 0.8, 0.4, 0.08);
        });
    }

    // ---- Primary blink: trail + bursts at origin and destination ----

    public static void handleBlink(TeleportBlinkPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());

            spawnBlinkTrail(world, from, to);
            scatter(world, ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 30, 0.4, 0.7, 0.4, 0.1);
            scatter(world, ParticleTypes.PORTAL, to.x,   to.y + 1,   to.z,   30, 0.4, 0.7, 0.4, 0.1);
        });
    }

    // ---- Secondary aim beam (whiff + swap) ----

    public static void handleAimBeam(TeleportAimBeamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());

            spawnAimBeam(world, from, to);
            scatter(world, ParticleTypes.PORTAL, to.x, to.y, to.z, 18, 0.25, 0.25, 0.25, 0.06);
        });
    }

    // ---- Secondary swap: dual trails + large bursts ----

    public static void handleSwapBurst(TeleportSwapBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());

            spawnBlinkTrail(world, from, to);
            spawnBlinkTrail(world, to, from);
            scatter(world, ParticleTypes.PORTAL, from.x, from.y + 1, from.z, 50, 0.5, 0.8, 0.5, 0.1);
            scatter(world, ParticleTypes.PORTAL, to.x,   to.y + 1,   to.z,   50, 0.5, 0.8, 0.5, 0.1);
        });
    }

    // ---- Frenzy per-tick: radius ring + aura dust ----

    public static void handleFrenzyTick(TeleportFrenzyTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = p.x(), cy = p.y(), cz = p.z();

            // radius ring
            int points = 80;
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                world.addParticle(FRENZY_RING,
                        cx + Math.cos(angle) * ULTIMATE_AURA_RADIUS,
                        cy + 0.1,
                        cz + Math.sin(angle) * ULTIMATE_AURA_RADIUS,
                        0, 0, 0);
            }

            // aura dust
            scatter(world, FRENZY_DUST, cx, cy + 1.0, cz, 3, 0.6, 1.0, 0.6, 0.02);
            scatter(world, ParticleTypes.REVERSE_PORTAL, cx, cy + 0.5, cz, 20, 0.3, 0.6, 0.3, 0.01);
        });
    }

    // ---- Frenzy per-attack strike ----

    public static void handleFrenzyStrike(TeleportFrenzyStrikePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, FRENZY_DUST, p.x(), p.y(), p.z(), 20, 0.3, 0.6, 0.3, 0.08);
        });
    }

    // ---- Internal helpers ----

    private static void spawnBlinkTrail(ClientWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 12), 8, 80);
        Vec3d step = delta.multiply(1.0 / steps);
        Vec3d p = from;

        for (int i = 0; i <= steps; i++) {
            world.addParticle(ParticleTypes.PORTAL, p.x, p.y + 1.0, p.z, 0, 0, 0);
            p = p.add(step);
        }
    }

    private static void spawnAimBeam(ClientWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 18), 10, 140);
        Vec3d step = delta.multiply(1.0 / steps);
        Vec3d p = from;

        for (int i = 0; i <= steps; i++) {
            world.addParticle(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 0, 0, 0);
            p = p.add(step);
        }
    }

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
}
