package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class SoundFxClient {

    private SoundFxClient() {}

    // Bass Drop constants (match server)
    private static final double BD_PULL_RADIUS  = 10.0;
    private static final double BD_FINAL_RADIUS = 7.0;

    // ---- Passive: resonance burst on entity ----

    public static void handleResonanceBurst(SoundResonanceBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.SCULK_SOUL, le.getX(), le.getY() + 1.0, le.getZ(), 6, 0.35, 0.45, 0.35, 0.02);
        });
    }

    // ---- Resonated ability hit burst ----

    public static void handleBurstHit(SoundBurstHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.6, z = le.getZ();
            world.addParticle(ParticleTypes.SONIC_BOOM, x, y, z, 0, 0, 0);
            scatter(world, ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.2, le.getZ(), 6, 0.35, 0.25, 0.35, 0.02);
        });
    }

    // ---- Single SONIC_BOOM cast effect (bass drop cast + ult activate) ----

    public static void handleBoomCast(SoundBoomCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.SONIC_BOOM, p.x(), p.y(), p.z(), 0, 0, 0);
        });
    }

    // ---- Bass pull pulse (contracting sphere + soul shell + sonic boom) ----

    public static void handleBassPull(SoundBassPullPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            world.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 0, 0, 0);
            spawnContractingSphere(world, c);
            spawnSphereShell(world, c, BD_PULL_RADIUS * 0.65, 12, ParticleTypes.SCULK_SOUL, 0.9);
        });
    }

    // ---- Bass pull hit — SCULK_CHARGE_POP per pulled target ----

    public static void handleBassPullHit(SoundBassPullHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.SCULK_CHARGE_POP, le.getX(), le.getY() + le.getHeight() * 0.55, le.getZ(), 2, 0.12, 0.10, 0.12, 0.01);
        });
    }

    // ---- Bass final burst ----

    public static void handleBassFinal(SoundBassFinalPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.25, c.z, 0, 0, 0);
            world.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 0, 0, 0);
            spawnExpandingSphere(world, c);
            spawnSphereShell(world, c, BD_FINAL_RADIUS * 0.85, 18, ParticleTypes.SCULK_SOUL, 0.9);
        });
    }

    // ---- Bass final remote target explosion ----

    public static void handleBassFinalRemote(SoundBassFinalRemotePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.2, le.getZ(), 6, 0.35, 0.20, 0.35, 0.02);
        });
    }

    // ---- Per-tick pull sphere animation frame ----

    public static void handleBassPullViz(SoundBassPullVizPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            double r = p.radius();
            spawnSphereShell(world, c, r, 90, ParticleTypes.SCULK_CHARGE_POP, 0.0);
            if (p.innerShell()) {
                spawnSphereShell(world, c, r * 0.55, 22, ParticleTypes.SCULK_SOUL, 0.0);
            }
        });
    }

    // ---- Per-tick blast sphere animation frame ----

    public static void handleBassBlastViz(SoundBassBlastVizPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            double r = p.radius();
            spawnSphereShell(world, c, r, 120, ParticleTypes.SCULK_CHARGE_POP, 0.0);
            if (p.groundRing()) {
                spawnGroundRing(world, new Vec3d(p.x(), p.y() - 0.9, p.z()), r);
            }
        });
    }

    // ---- Ult windup tick ----

    public static void handleUltWindup(SoundUltWindupPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.SCULK_CHARGE_POP, p.x(), p.y(), p.z(), 6, 0.25, 0.35, 0.25, 0.01);
        });
    }

    // ---- Ult beam ----

    public static void handleUltBeam(SoundUltBeamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d start = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d end   = new Vec3d(p.toX(), p.toY(), p.toZ());
            Vec3d delta = end.subtract(start);
            double len = delta.length();
            if (len < 0.01) return;

            double particleStep = 0.55;
            int steps = MathHelper.clamp((int)(len / particleStep), 10, 220);
            Vec3d step = delta.multiply(1.0 / steps);

            Vec3d pos = start;
            for (int i = 0; i <= steps; i++) {
                world.addParticle(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 0, 0, 0);
                if ((i & 1) == 0) {
                    scatter(world, ParticleTypes.SCULK_CHARGE_POP, pos.x, pos.y, pos.z, 3, 0.18, 0.18, 0.18, 0.01);
                } else {
                    scatter(world, ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z, 1, 0.10, 0.10, 0.10, 0.0);
                }
                pos = pos.add(step);
            }
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 0, 0, 0);
        });
    }

    // ---- Internal helpers ----

    private static void spawnSphereShell(ClientWorld world, Vec3d center, double radius, int points,
                                          ParticleEffect particle, double yOffset) {
        for (int i = 0; i < points; i++) {
            double u = world.random.nextDouble();
            double v = world.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double j = 0.12;
            double px = center.x + sx * radius + (world.random.nextDouble() - 0.5) * j;
            double py = center.y + yOffset + sy * radius + (world.random.nextDouble() - 0.5) * j;
            double pz = center.z + sz * radius + (world.random.nextDouble() - 0.5) * j;

            world.addParticle(particle, px, py, pz, 0, 0, 0);
        }
    }

    private static void spawnContractingSphere(ClientWorld world, Vec3d center) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, BD_PULL_RADIUS, 5.2);
            spawnSphereShell(world, center, r, 120, ParticleTypes.SCULK_CHARGE_POP, 0.9);
        }
    }

    private static void spawnExpandingSphere(ClientWorld world, Vec3d center) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, 1.0, BD_FINAL_RADIUS);
            spawnSphereShell(world, center, r, 150, ParticleTypes.SCULK_CHARGE_POP, 0.9);
        }
    }

    private static void spawnGroundRing(ClientWorld world, Vec3d center, double radius) {
        double y = center.y + 0.10;
        for (int i = 0; i < 36; i++) {
            double a = (Math.PI * 2.0) * (i / 36.0);
            double x = center.x + Math.cos(a) * radius + (world.random.nextDouble() - 0.5) * 0.06;
            double z = center.z + Math.sin(a) * radius + (world.random.nextDouble() - 0.5) * 0.06;
            world.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 0, 0, 0);
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
