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
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public final class SoundFxClient {

    private SoundFxClient() {}

    // Bass Drop constants (match server)
    private static final double BD_PULL_RADIUS  = 10.0;
    private static final double BD_FINAL_RADIUS = 7.0;
    private static final int    PULL_VIZ_STEPS  = 8;
    private static final int    BLAST_VIZ_STEPS = 8;
    private static final int    PULL_POINTS     = 90;
    private static final int    BLAST_POINTS    = 120;
    private static final double ULT_PARTICLE_STEP = 0.55;

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

    // ---- Bass pull pulse (contracting sphere) ----

    public static void handleBassPull(SoundBassPullPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            world.addParticle(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 0, 0, 0);
            spawnContractingSphere(world, c, p.seed());
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
            spawnExpandingSphere(world, c, world.random.nextLong());
            spawnSphereShell(world, c, BD_FINAL_RADIUS * 0.85, 18, ParticleTypes.SCULK_SOUL, 0.9, world.random.nextLong());
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
            int step = p.step();
            long seed = p.seed();

            double t = (PULL_VIZ_STEPS <= 1) ? 1.0 : (step / (double)(PULL_VIZ_STEPS - 1));
            t = t * t;
            double r = MathHelper.lerp(t, BD_PULL_RADIUS, 2.6);

            spawnSphereShell(world, c, r, PULL_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0, seed + step);
            if ((step & 1) == 0) {
                spawnSphereShell(world, c, r * 0.55, 22, ParticleTypes.SCULK_SOUL, 0.0, seed + 500 + step);
            }
        });
    }

    // ---- Per-tick blast sphere animation frame ----

    public static void handleBassBlastViz(SoundBassBlastVizPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d c = new Vec3d(p.x(), p.y(), p.z());
            int step = p.step();
            long seed = p.seed();

            double t = (BLAST_VIZ_STEPS <= 1) ? 1.0 : (step / (double)(BLAST_VIZ_STEPS - 1));
            double ease = 1.0 - Math.pow(1.0 - t, 2.0);
            double r = MathHelper.lerp(ease, 1.0, BD_FINAL_RADIUS * 1.5);

            spawnSphereShell(world, c, r, BLAST_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0, seed + step);
            if ((step & 1) == 0) {
                spawnGroundRing(world, new Vec3d(p.x(), p.y() - 0.9, p.z()), r, seed + 1000 + step);
            }
        });
    }

    // ---- Ult windup tick — SONIC_BOOM pops matching NeoForge ----

    public static void handleUltWindup(SoundUltWindupPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.SONIC_BOOM, p.x(), p.y(), p.z(), 0, 0, 0);
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

            int steps = MathHelper.clamp((int)(len / ULT_PARTICLE_STEP), 10, 220);
            Vec3d step = delta.multiply(1.0 / steps);
            Random random = Random.create(p.seed());

            Vec3d pos = start;
            for (int i = 0; i <= steps; i++) {
                world.addParticle(ParticleTypes.SONIC_BOOM, pos.x, pos.y, pos.z, 0, 0, 0);
                if ((i & 1) == 0) {
                    for (int j = 0; j < 3; j++) {
                        world.addParticle(ParticleTypes.SCULK_CHARGE_POP,
                                pos.x + (random.nextDouble() - 0.5) * 0.18,
                                pos.y + (random.nextDouble() - 0.5) * 0.18,
                                pos.z + (random.nextDouble() - 0.5) * 0.18,
                                0, 0, 0);
                    }
                } else {
                    world.addParticle(ParticleTypes.SCULK_SOUL,
                            pos.x + (random.nextDouble() - 0.5) * 0.10,
                            pos.y + (random.nextDouble() - 0.5) * 0.10,
                            pos.z + (random.nextDouble() - 0.5) * 0.10,
                            0, 0, 0);
                }
                pos = pos.add(step);
            }
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 0, 0, 0);
        });
    }

    // ---- Internal helpers ----

    private static void spawnSphereShell(ClientWorld world, Vec3d center, double radius, int points,
                                          ParticleEffect particle, double yOffset, long seed) {
        Random random = Random.create(seed);
        for (int i = 0; i < points; i++) {
            double u = random.nextDouble();
            double v = random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double j = 0.12;
            double px = center.x + sx * radius + (random.nextDouble() - 0.5) * j;
            double py = center.y + yOffset + sy * radius + (random.nextDouble() - 0.5) * j;
            double pz = center.z + sz * radius + (random.nextDouble() - 0.5) * j;

            world.addParticle(particle, px, py, pz, 0, 0, 0);
        }
    }

    private static void spawnContractingSphere(ClientWorld world, Vec3d center, long seed) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = s / (double)(shells - 1);
            double r = MathHelper.lerp(t, BD_PULL_RADIUS, 5.2);
            spawnSphereShell(world, center, r, 120, ParticleTypes.SCULK_CHARGE_POP, 0.9, seed + s);
        }
    }

    private static void spawnExpandingSphere(ClientWorld world, Vec3d center, long seed) {
        int shells = 10;
        for (int s = 0; s < shells; s++) {
            double t = s / (double)(shells - 1);
            double r = MathHelper.lerp(t, 1.0, BD_FINAL_RADIUS);
            spawnSphereShell(world, center, r, 150, ParticleTypes.SCULK_CHARGE_POP, 0.9, seed + s);
        }
    }

    private static void spawnGroundRing(ClientWorld world, Vec3d center, double radius, long seed) {
        Random random = Random.create(seed);
        double y = center.y + 0.10;
        for (int i = 0; i < 36; i++) {
            double a = (Math.PI * 2.0) * (i / 36.0);
            double x = center.x + Math.cos(a) * radius + (random.nextDouble() - 0.5) * 0.06;
            double z = center.z + Math.sin(a) * radius + (random.nextDouble() - 0.5) * 0.06;
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
