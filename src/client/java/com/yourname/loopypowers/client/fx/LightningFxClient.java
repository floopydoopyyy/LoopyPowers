package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class LightningFxClient {

    private LightningFxClient() {}

    private static final DustParticleEffect BOLT_YELLOW = new DustParticleEffect(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    private static final DustParticleEffect BOLT_GOLD   = new DustParticleEffect(new Vector3f(1.00f, 0.95f, 0.55f), 1.1f);
    private static final DustParticleEffect BOLT_WHITE  = new DustParticleEffect(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);
    private static final DustParticleEffect STORM_BLACK = new DustParticleEffect(new Vector3f(0.08f, 0.08f, 0.10f), 1.8f);
    private static final DustParticleEffect STORM_GREY  = new DustParticleEffect(new Vector3f(0.20f, 0.20f, 0.22f), 1.5f);

    private static final double CLAP_RANGE     = 7.0;
    private static final double CLAP_ANGLE_DEG = 65.0;
    private static final double STORM_RADIUS   = 15.0;

    // ---- Passive charge ready ----

    public static void handleChargeReady(LightningChargeReadyPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            for (double yOff : new double[]{ 0.15, 1.0 }) {
                int points = 24;
                for (int i = 0; i < points; i++) {
                    double ang = i * Math.PI * 2.0 / points;
                    double spd = 0.18;
                    world.addParticle(BOLT_YELLOW,
                            x + Math.cos(ang) * 0.4, y + yOff, z + Math.sin(ang) * 0.4,
                            Math.cos(ang) * spd, 0.01, Math.sin(ang) * spd);
                }
            }
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 50, 0.5, 0.8, 0.5, 0.10);
            scatter(world, BOLT_WHITE, x, y + 1.0, z, 14, 0.4, 0.5, 0.4, 0.06);
        });
    }

    // ---- Passive on-hit ----

    public static void handleHitBasic(LightningHitBasicPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 6,  0.25, 0.35, 0.25, 0.02);
            scatter(world, BOLT_GOLD,                    x, y, z, 4,  0.15, 0.20, 0.15, 0.015);
        });
    }

    public static void handleHitTier(LightningHitTierPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            int tier = p.tier();
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();

            int sparks = switch (tier) { case 1 -> 18; case 2 -> 28; case 3 -> 40; default -> 60; };
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, sparks,     0.45, 0.7,  0.45, 0.08);
            scatter(world, BOLT_YELLOW,                  x, y, z, sparks / 2, 0.35, 0.55, 0.35, 0.07);

            // hit ring
            int    points = 8 + tier * 4;
            double speed  = 0.12 + tier * 0.04;
            double h      = le.getY() + 0.8;
            for (int i = 0; i < points; i++) {
                double ang = i * Math.PI * 2.0 / points;
                DustParticleEffect col = (i % 2 == 0) ? BOLT_YELLOW : BOLT_WHITE;
                world.addParticle(col,
                        le.getX() + Math.cos(ang) * 0.3, h, le.getZ() + Math.sin(ang) * 0.3,
                        Math.cos(ang) * speed, 0.01, Math.sin(ang) * speed);
            }
        });
    }

    // ---- Chain ----

    public static void handleChainTrail(LightningChainTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d from = new Vec3d(p.fx(), p.fy(), p.fz());
            Vec3d to   = new Vec3d(p.tx(), p.ty(), p.tz());
            Vec3d delta = to.subtract(from);
            double len = delta.length();
            if (len < 0.01) return;
            int steps = MathHelper.clamp((int)(len * 10), 8, 60);
            Vec3d step = delta.multiply(1.0 / steps);
            Vec3d pos = from;
            for (int i = 0; i <= steps; i++) {
                if (i % 3 == 0) {
                    world.addParticle(BOLT_YELLOW, pos.x, pos.y, pos.z, 0.03, 0.08, 0.03);
                } else {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 0.04, 0.10, 0.8);
                }
                pos = pos.add(step);
            }
        });
    }

    public static void handleChainHit(LightningChainHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 14, 0.35, 0.5, 0.35, 0.06);
            scatter(world, BOLT_YELLOW,                  x, y, z,  8, 0.25, 0.35, 0.25, 0.05);
        });
    }

    // ---- Primary ----

    public static void handleClapCone(LightningClapConePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d center  = new Vec3d(p.x(), p.y(), p.z());
            Vec3d forward = new Vec3d(p.fx(), p.fy(), p.fz()).normalize();
            Vec3d right   = new Vec3d(-forward.z, 0, forward.x).normalize();
            double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);
            int depthSlices = 6, arcPoints = 20;
            for (int d = 1; d <= depthSlices; d++) {
                double depth   = CLAP_RANGE * ((double) d / depthSlices);
                double arcW    = depth * Math.tan(halfAngle);
                Vec3d slicePos = center.add(forward.multiply(depth));
                for (int i = 0; i <= arcPoints; i++) {
                    double lat = -arcW + 2.0 * arcW * ((double) i / arcPoints);
                    double jit = (world.random.nextDouble() - 0.5) * 0.6;
                    double x   = slicePos.x + right.x * (lat + jit);
                    double z   = slicePos.z + right.z * (lat + jit);
                    double y   = slicePos.y + (world.random.nextDouble() - 0.5) * 1.5;
                    if (world.random.nextFloat() > 0.65f) continue;
                    DustParticleEffect col = d <= 2 ? BOLT_WHITE : d <= 4 ? BOLT_YELLOW : BOLT_GOLD;
                    world.addParticle(col, x, y, z, 0, 0, 0);
                    if (world.random.nextFloat() < 0.20f) {
                        world.addParticle(ParticleTypes.END_ROD, x, y, z, forward.x * 0.12, 0.01, forward.z * 0.12);
                    }
                }
            }
            scatter(world, ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 18, 0.3, 0.3, 0.3, 0.08);
            scatter(world, BOLT_WHITE,                   center.x, center.y, center.z,  6, 0.15, 0.15, 0.15, 0.05);
        });
    }

    public static void handleClapHit(LightningClapHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            float t = p.t();
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, (int)(8 + 20 * t),  0.4, 0.6, 0.4, 0.06);
            scatter(world, BOLT_YELLOW,                  x, y, z, (int)(6 + 12 * t),  0.3, 0.4, 0.3, 0.05);
        });
    }

    // ---- EGG confetti ----

    public static void handleConfetti(LightningConfettiPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Vec3d center  = new Vec3d(p.x(), p.y(), p.z());
            Vec3d forward = new Vec3d(p.fx(), p.fy(), p.fz()).normalize();
            Vec3d right   = new Vec3d(-forward.z, 0, forward.x).normalize();
            double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);
            int depthSlices = 6, arcPoints = 20;
            for (int d = 1; d <= depthSlices; d++) {
                double depth   = CLAP_RANGE * ((double) d / depthSlices);
                double arcW    = depth * Math.tan(halfAngle);
                Vec3d slicePos = center.add(forward.multiply(depth));
                for (int i = 0; i <= arcPoints; i++) {
                    double lat = -arcW + 2.0 * arcW * ((double) i / arcPoints);
                    double jit = (world.random.nextDouble() - 0.5) * 0.6;
                    double x   = slicePos.x + right.x * (lat + jit);
                    double z   = slicePos.z + right.z * (lat + jit);
                    double y   = slicePos.y + (world.random.nextDouble() - 0.5) * 1.5;
                    if (world.random.nextFloat() > 0.50f) continue;
                    world.addParticle(new DustParticleEffect(new Vector3f(
                            world.random.nextFloat(), world.random.nextFloat(), world.random.nextFloat()), 1.1f),
                            x, y, z, 0, 0, 0);
                }
            }
        });
    }

    public static void handleTargetConfetti(LightningTargetConfettiPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            int count = (int)(25 + 30 * p.t());
            for (int i = 0; i < count; i++) {
                world.addParticle(new DustParticleEffect(new Vector3f(
                        world.random.nextFloat(), world.random.nextFloat(), world.random.nextFloat()), 0.85f),
                        le.getX(), le.getY() + 1.0, le.getZ(), 0.3, 0.4, 0.3);
            }
        });
    }

    // ---- Secondary ----

    public static void handleSuperchargeAura(LightningSuperchargeAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            int points = 16;
            for (int i = 0; i < points; i++) {
                double ang = i * Math.PI * 2.0 / points + (p.time() * 0.10);
                double r = 0.65;
                world.addParticle(BOLT_YELLOW,
                        x + Math.cos(ang) * r, y + 0.9, z + Math.sin(ang) * r,
                        0, 0.005, 0);
            }
            if (p.spark()) {
                double ang = world.random.nextDouble() * Math.PI * 2;
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x + Math.cos(ang) * 0.65, y + 0.9, z + Math.sin(ang) * 0.65,
                        Math.cos(ang) * 0.10, 0.04, Math.sin(ang) * 0.10);
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x + Math.cos(ang + 0.1) * 0.65, y + 0.9, z + Math.sin(ang + 0.1) * 0.65,
                        Math.cos(ang + 0.1) * 0.10, 0.04, Math.sin(ang + 0.1) * 0.10);
            }
            if (p.endRod()) {
                double ang = world.random.nextDouble() * Math.PI * 2;
                world.addParticle(ParticleTypes.END_ROD,
                        x + (world.random.nextDouble() - 0.5) * 0.4,
                        y + 0.6 + world.random.nextDouble() * 1.2,
                        z + (world.random.nextDouble() - 0.5) * 0.4,
                        Math.cos(ang) * 0.15, 0.06, Math.sin(ang) * 0.15);
            }
        });
    }

    public static void handleSuperchargeBurst(LightningSuperchargeBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();

            // charge ring at feet
            int ringPts = 40;
            for (int i = 0; i < ringPts; i++) {
                double ang = 2 * Math.PI * i / ringPts;
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        x + Math.cos(ang) * 1.2, y + 0.2, z + Math.sin(ang) * 1.2, 0, 0, 0);
            }

            // outer expanding ring
            int outerPts = 32;
            double outerSpd = 0.30;
            for (int i = 0; i < outerPts; i++) {
                double ang = i * Math.PI * 2.0 / outerPts;
                DustParticleEffect col = (i % 3 == 0) ? BOLT_WHITE : (i % 3 == 1) ? BOLT_YELLOW : BOLT_GOLD;
                world.addParticle(col,
                        x + Math.cos(ang) * 0.4, y + 0.3, z + Math.sin(ang) * 0.4,
                        Math.cos(ang) * outerSpd, 0.01, Math.sin(ang) * outerSpd);
            }

            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 80, 0.7, 1.2, 0.7, 0.12);
            scatter(world, BOLT_YELLOW,                  x, y + 1.0, z, 40, 0.5, 1.0, 0.5, 0.09);
            scatter(world, BOLT_WHITE,                   x, y + 1.0, z, 16, 0.3, 0.8, 0.3, 0.07);

            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI * 2.0 / 8;
                world.addParticle(ParticleTypes.END_ROD,
                        x + Math.cos(ang) * 0.4, y + 0.8 + world.random.nextDouble(), z + Math.sin(ang) * 0.4,
                        Math.cos(ang) * 0.22, 0.04, Math.sin(ang) * 0.22);
            }
        });
    }

    // ---- Ultimate ----

    public static void handleMaelstromOpen(LightningMaelstromOpenPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            for (double yOff : new double[]{ 0.1, 0.6, 1.2, 1.9 }) {
                int points = 28;
                double spd = 0.35;
                for (int i = 0; i < points; i++) {
                    double ang = i * Math.PI * 2.0 / points;
                    DustParticleEffect col = switch (i % 3) { case 0 -> BOLT_WHITE; case 1 -> BOLT_YELLOW; default -> BOLT_GOLD; };
                    world.addParticle(col,
                            x + Math.cos(ang) * 0.4, y + yOff, z + Math.sin(ang) * 0.4,
                            Math.cos(ang) * spd, 0.015, Math.sin(ang) * spd);
                }
            }
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1.0, z, 100, 1.0, 1.5, 1.0, 0.12);
            scatter(world, BOLT_YELLOW,                  x, y + 1.0, z,  60, 0.8, 1.2, 0.8, 0.10);
            scatter(world, BOLT_WHITE,                   x, y + 1.0, z,  20, 0.4, 0.8, 0.4, 0.07);
        });
    }

    public static void handleStormTargetAura(LightningStormTargetAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.25, 0.35, 0.25, 0.02);
            scatter(world, BOLT_YELLOW,                  x, y, z, 1, 0.15, 0.20, 0.15, 0.015);
        });
    }

    public static void handleStormClouds(LightningStormCloudsPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double cx = p.cx(), cy = p.cy(), cz = p.cz();
            int stormTicks = p.stormTicks();
            // Rotation offset — one full rotation every ~200 ticks
            double rotAngle = stormTicks * 0.031;

            if (p.isActivation()) {
                stormCloudActivationBurst(world, cx, cy, cz, rotAngle);
            } else {
                stormCloudCanopy(world, cx, cy, cz, rotAngle);
                stormCloudCrownEdge(world, cx, cy, cz, rotAngle);
                stormCloudBody(world, cx, cy, cz, rotAngle, stormTicks);
                stormCloudUnderbelly(world, cx, cy, cz, rotAngle, stormTicks);
            }
        });
    }

    // Layer 1 — CANOPY (y+8.5): light CLOUD wisps on very top
    private static void stormCloudCanopy(ClientWorld world, double cx, double cy, double cz, double rotAngle) {
        int points = 12;
        for (int i = 0; i < points; i++) {
            double a = rotAngle + (2 * Math.PI / points) * i;
            for (double r : new double[]{ STORM_RADIUS * 0.45, STORM_RADIUS * 0.80 }) {
                double x = cx + Math.cos(a) * r;
                double z = cz + Math.sin(a) * r;
                double y = cy + 8.5 + (world.random.nextDouble() - 0.5) * 0.6;
                world.addParticle(ParticleTypes.CLOUD, x, y, z,
                        (world.random.nextDouble() - 0.5) * 0.008, 0.002, (world.random.nextDouble() - 0.5) * 0.008);
            }
        }
    }

    // Layer 2 — CROWN EDGE (y+7–8.1): dense dark ring at STORM_RADIUS boundary
    private static void stormCloudCrownEdge(ClientWorld world, double cx, double cy, double cz, double rotAngle) {
        int outerPoints = 32;
        for (int i = 0; i < outerPoints; i++) {
            double a = rotAngle + (2 * Math.PI / outerPoints) * i;
            for (double yOff : new double[]{ 7.0, 7.6, 8.1 }) {
                double x = cx + Math.cos(a) * (STORM_RADIUS - 0.5 + world.random.nextDouble() * 1.2);
                double z = cz + Math.sin(a) * (STORM_RADIUS - 0.5 + world.random.nextDouble() * 1.2);
                double y = cy + yOff + (world.random.nextDouble() - 0.5) * 0.5;
                DustParticleEffect col = (world.random.nextFloat() < 0.65f) ? STORM_BLACK : STORM_GREY;
                world.addParticle(col, x, y, z, 0, (world.random.nextDouble() - 0.5) * 0.004, 0);
            }
            if (i % 2 == 0) {
                double x = cx + Math.cos(a) * (STORM_RADIUS * 0.95);
                double z = cz + Math.sin(a) * (STORM_RADIUS * 0.95);
                world.addParticle(ParticleTypes.CLOUD, x, cy + 7.4, z, 0, 0.003, 0);
            }
        }
        // counter-rotating inner ring for depth
        int innerPoints = 20;
        for (int i = 0; i < innerPoints; i++) {
            double a = -rotAngle + (2 * Math.PI / innerPoints) * i;
            double x = cx + Math.cos(a) * (STORM_RADIUS * 0.82);
            double z = cz + Math.sin(a) * (STORM_RADIUS * 0.82);
            world.addParticle(STORM_BLACK, x, cy + 6.8 + world.random.nextDouble() * 0.8, z, 0, 0.002, 0);
        }
    }

    // Layer 3 — BODY (y+6–7.5): thick dark disc interior
    private static void stormCloudBody(ClientWorld world, double cx, double cy, double cz,
                                        double rotAngle, int stormTicks) {
        double fastRot = rotAngle * 1.3;
        int pointsA = 18;
        for (int i = 0; i < pointsA; i++) {
            double a = fastRot + (2 * Math.PI / pointsA) * i;
            double r = STORM_RADIUS * 0.58 + (world.random.nextDouble() - 0.5) * 2.0;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            double y = cy + 6.5 + world.random.nextDouble() * 0.9;
            DustParticleEffect col = (world.random.nextFloat() < 0.5f) ? STORM_BLACK : STORM_GREY;
            world.addParticle(col, x, y, z, 0, 0.002, 0);
            if (world.random.nextFloat() < 0.25f)
                world.addParticle(ParticleTypes.CLOUD, x, y + 0.3, z, 0, 0.003, 0);
        }
        int pointsB = 12;
        for (int i = 0; i < pointsB; i++) {
            double a = -fastRot + (2 * Math.PI / pointsB) * i;
            double r = STORM_RADIUS * 0.30 + (world.random.nextDouble() - 0.5) * 1.5;
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            world.addParticle(STORM_BLACK, x, cy + 6.2 + world.random.nextDouble() * 1.2, z, 0, 0.003, 0);
        }
        if (stormTicks % 12 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = STORM_RADIUS * (0.2 + world.random.nextDouble() * 0.6);
                world.addParticle(STORM_GREY,
                        cx + Math.cos(a) * r, cy + 7.0 + world.random.nextDouble() * 1.0, cz + Math.sin(a) * r,
                        0, 0.004, 0);
            }
        }
    }

    // Layer 4 — UNDERBELLY (y+5–6.5): BOLT_YELLOW glow + ELECTRIC_SPARK drops
    private static void stormCloudUnderbelly(ClientWorld world, double cx, double cy, double cz,
                                              double rotAngle, int stormTicks) {
        int glowPoints = 14;
        for (int i = 0; i < glowPoints; i++) {
            double a = rotAngle * 0.7 + (2 * Math.PI / glowPoints) * i;
            double r = STORM_RADIUS * (0.20 + world.random.nextDouble() * 0.65);
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            double y = cy + 5.6 + world.random.nextDouble() * 0.9;
            world.addParticle(BOLT_YELLOW, x, y, z,
                    (world.random.nextDouble() - 0.5) * 0.015, -(world.random.nextDouble() * 0.008),
                    (world.random.nextDouble() - 0.5) * 0.015);
        }
        if (stormTicks % 6 == 0) {
            for (int i = 0; i < 4; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = STORM_RADIUS * (0.15 + world.random.nextDouble() * 0.5);
                world.addParticle(BOLT_WHITE,
                        cx + Math.cos(a) * r, cy + 5.8 + world.random.nextDouble() * 0.6, cz + Math.sin(a) * r,
                        0, -0.005, 0);
            }
        }
        // ELECTRIC_SPARK drops falling downward — visual threat signal
        for (int i = 0; i < 10; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = STORM_RADIUS * (0.1 + world.random.nextDouble() * world.random.nextDouble() * 0.75);
            double x = cx + Math.cos(a) * r, z = cz + Math.sin(a) * r;
            double y = cy + 5.2 + world.random.nextDouble() * 1.0;
            world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (world.random.nextDouble() - 0.5) * 0.02,
                    -(0.02 + world.random.nextDouble() * 0.04),
                    (world.random.nextDouble() - 0.5) * 0.02);
        }
    }

    // Activation burst — three expanding rings reveal the full zone boundary
    private static void stormCloudActivationBurst(ClientWorld world, double cx, double cy, double cz,
                                                   double rotAngle) {
        double[][] rings = {
                { STORM_RADIUS * 0.45, cy + 7.0, 20 },
                { STORM_RADIUS * 0.75, cy + 7.2, 28 },
                { STORM_RADIUS,        cy + 7.5, 36 },
        };
        for (double[] ring : rings) {
            double r = ring[0], y = ring[1];
            int points = (int) ring[2];
            for (int i = 0; i < points; i++) {
                double a = (2 * Math.PI / points) * i;
                double outSpeed = 0.22 * (r / STORM_RADIUS);
                double vx = Math.cos(a) * outSpeed, vz = Math.sin(a) * outSpeed;
                int mod = i % 3;
                if (mod == 0)       world.addParticle(ParticleTypes.CLOUD, cx + Math.cos(a) * 0.5, y, cz + Math.sin(a) * 0.5, vx, 0.005, vz);
                else if (mod == 1)  world.addParticle(STORM_BLACK,         cx + Math.cos(a) * 0.5, y, cz + Math.sin(a) * 0.5, vx, 0.003, vz);
                else                world.addParticle(BOLT_YELLOW,         cx + Math.cos(a) * 0.5, y, cz + Math.sin(a) * 0.5, vx, 0.008, vz);
            }
        }
        // central upward sparks
        for (int i = 0; i < 30; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 2.0;
            world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    cx + Math.cos(a) * r, cy + 0.5 + world.random.nextDouble() * 2.0, cz + Math.sin(a) * r,
                    (world.random.nextDouble() - 0.5) * 0.06, 0.12 + world.random.nextDouble() * 0.18,
                    (world.random.nextDouble() - 0.5) * 0.06);
        }
        // full-radius cloud band at boundary
        int edgePoints = 48;
        for (int i = 0; i < edgePoints; i++) {
            double a = rotAngle + (2 * Math.PI / edgePoints) * i;
            double x = cx + Math.cos(a) * (STORM_RADIUS - 0.8 + world.random.nextDouble() * 1.6);
            double z = cz + Math.sin(a) * (STORM_RADIUS - 0.8 + world.random.nextDouble() * 1.6);
            double y = cy + 6.5 + world.random.nextDouble() * 2.0;
            DustParticleEffect col = (world.random.nextFloat() < 0.6f) ? STORM_BLACK : STORM_GREY;
            world.addParticle(col, x, y, z, 0, 0.003, 0);
            if (i % 3 == 0) world.addParticle(ParticleTypes.CLOUD, x, y + 0.4, z, 0, 0.004, 0);
        }
    }

    public static void handleStormStrike(LightningStormStrikePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 22, 0.45, 0.7, 0.45, 0.08);
            scatter(world, BOLT_YELLOW,                  x, y, z, 14, 0.35, 0.55, 0.35, 0.07);
            scatter(world, BOLT_WHITE, x, le.getY() + 0.8, z,  6, 0.20, 0.25, 0.20, 0.05);
            for (int i = 0; i < 6; i++) {
                double ang = i * Math.PI * 2.0 / 6;
                world.addParticle(ParticleTypes.END_ROD,
                        le.getX() + Math.cos(ang) * 0.3, le.getY() + 0.5 + world.random.nextDouble() * 1.5, le.getZ() + Math.sin(ang) * 0.3,
                        Math.cos(ang) * 0.14, 0.06, Math.sin(ang) * 0.14);
            }
            // hit ring at tier 3
            int points = 8 + 3 * 4;
            double speed = 0.12 + 3 * 0.04;
            double h = le.getY() + 0.8;
            for (int i = 0; i < points; i++) {
                double ang = i * Math.PI * 2.0 / points;
                DustParticleEffect col = (i % 2 == 0) ? BOLT_YELLOW : BOLT_WHITE;
                world.addParticle(col, le.getX() + Math.cos(ang) * 0.3, h, le.getZ() + Math.sin(ang) * 0.3,
                        Math.cos(ang) * speed, 0.01, Math.sin(ang) * speed);
            }
        });
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
