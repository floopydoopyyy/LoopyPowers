package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class StrengthFxClient {

    private StrengthFxClient() {}

    private static final DustParticleEffect RAGE_RED = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.0f), 1.35f);

    // ---- Easter egg: one punch trail ----

    public static void handleOnePunch(StrengthOnePunchPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d pos = new Vec3d(p.x(), p.y() + 1.0, p.z());
            Vec3d dir = new Vec3d(p.dirX(), p.dirY(), p.dirZ()).normalize();

            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 0, 0, 0);
            scatter(world, ParticleTypes.FLASH, pos.x, pos.y, pos.z, 5, 1.0, 1.0, 1.0, 0);

            for (int i = 0; i < 35; i++) {
                double step = i * 3.5;
                double px = pos.x + dir.x * step;
                double py = pos.y + dir.y * step;
                double pz = pos.z + dir.z * step;

                scatter(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 5, 0.5, 0.5, 0.5, 0.1);
                scatter(world, ParticleTypes.CLOUD, px, py, pz, 10, 3.0, 3.0, 3.0, 0.3);

                if (i % 3 == 0) {
                    world.addParticle(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 0, 0, 0);
                    scatter(world, ParticleTypes.EXPLOSION, px, py, pz, 2, 4.0, 4.0, 4.0, 0);
                }
            }
        });
    }

    // ---- Rage on-hit cloud ----

    public static void handleRageHit(StrengthRageHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.CLOUD, le.getX(), le.getY() + le.getHeight() * 0.55, le.getZ(), 14, 0.18, 0.18, 0.18, 0.02);
        });
    }

    // ---- Ground slam ----

    public static void handleSlam(StrengthSlamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double sx = p.slamX(), sy = p.slamY(), sz = p.slamZ();
            BlockPos ground = new BlockPos(p.groundX(), p.groundY(), p.groundZ());
            BlockState groundState = world.getBlockState(ground);
            boolean grounded = p.casterGrounded();
            double groundTop = ground.getY() + 1.01;

            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, sx, sy + 0.10, sz, 0, 0, 0);

            if (grounded) {
                scatter(world, ParticleTypes.EXPLOSION, sx, sy + 0.15, sz, 6, 0.35, 0.15, 0.35, 0.02);
                scatter(world, ParticleTypes.POOF,      sx, sy + 0.10, sz, 12, 0.55, 0.15, 0.55, 0.03);
            }

            if (!groundState.isAir()) {
                BlockStateParticleEffect blockDust = new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState);

                int dustA = grounded ? 420 : 20;
                int dustB = grounded ? 220 : 8;

                scatter(world, blockDust, sx, groundTop, sz, dustA,
                        grounded ? 2.2 : 0.5, grounded ? 0.18 : 0.08, grounded ? 2.2 : 0.5, grounded ? 0.75 : 0.08);
                scatter(world, blockDust, sx, groundTop, sz, dustB,
                        grounded ? 0.65 : 0.20, grounded ? 1.10 : 0.25, grounded ? 0.65 : 0.20, grounded ? 1.15 : 0.12);

                if (grounded) {
                    double[] radii   = { 1.4, 2.8, 4.2 };
                    int[]    counts  = { 24, 32, 42 };
                    double[] spreads = { 0.25, 0.30, 0.38 };
                    double[] speeds  = { 0.35, 0.40, 0.45 };

                    for (int ri = 0; ri < radii.length; ri++) {
                        double r = radii[ri];
                        for (int i = 0; i < counts[ri]; i++) {
                            double a  = world.random.nextDouble() * (Math.PI * 2.0);
                            double jr = (world.random.nextDouble() - 0.5) * 0.35;
                            double x  = sx + Math.cos(a) * (r + jr);
                            double z  = sz + Math.sin(a) * (r + jr);
                            scatter(world, blockDust, x, groundTop, z, 1, spreads[ri], 0.08, spreads[ri], speeds[ri]);
                        }
                    }

                    scatter(world, ParticleTypes.CLOUD, sx, groundTop + 0.04, sz, 140, 1.1, 0.25, 1.1, 0.10);
                    scatter(world, ParticleTypes.CRIT,  sx, groundTop + 0.04, sz,  55, 0.7, 0.20, 0.7, 0.12);
                }
            }
        });
    }

    // ---- Per-target crit during slam ----

    public static void handleSlamTarget(StrengthSlamTargetPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.CRIT, le.getX(), le.getY() + le.getHeight() * 0.55, le.getZ(), 2, 0.12, 0.12, 0.12, 0);
        });
    }

    // ---- Per-tick rush trail ----

    public static void handleRushTrail(StrengthRushTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            if (p.hasGroundBlock()) {
                BlockPos ground = new BlockPos(p.groundX(), p.groundY(), p.groundZ());
                BlockState state = world.getBlockState(ground);
                if (!state.isAir()) {
                    scatter(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                            p.x(), ground.getY() + 1.01, p.z(), 14, 0.35, 0.04, 0.35, 0.10);
                } else {
                    scatter(world, ParticleTypes.CLOUD, p.x(), p.y() + 0.15, p.z(), 6, 0.18, 0.06, 0.18, 0.02);
                }
            } else {
                scatter(world, ParticleTypes.CLOUD, p.x(), p.y() + 0.15, p.z(), 6, 0.18, 0.06, 0.18, 0.02);
            }

            if (p.showCrit()) {
                Vec3d dir = new Vec3d(p.dirX(), 0, p.dirZ()).normalize();
                Vec3d front = new Vec3d(p.x(), p.y(), p.z()).add(dir.multiply(0.8)).add(0, 1.0, 0);
                scatter(world, ParticleTypes.CRIT, front.x, front.y, front.z, 2, 0.08, 0.08, 0.08, 0);
            }
        });
    }

    // ---- Rush entity collision ----

    public static void handleRushEntityHit(StrengthRushEntityHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ParticleTypes.CRIT,  le.getX(), le.getY() + le.getHeight() * 0.55, le.getZ(), 8, 0.16, 0.16, 0.16, 0.02);
            scatter(world, ParticleTypes.CLOUD, le.getX(), le.getY() + 0.15, le.getZ(), 14, 0.25, 0.10, 0.25, 0.04);
        });
    }

    // ---- Rush cancel ----

    public static void handleRushCancel(StrengthRushCancelPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD, p.x(), p.y(), p.z(), 10, 0.25, 0.10, 0.25, 0.02);
        });
    }

    // ---- Wall crash ----

    public static void handleRushCrash(StrengthRushCrashPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y(), p.z(), 0, 0, 0);
            scatter(world, ParticleTypes.CLOUD, p.x(), p.y(), p.z(), 120, 1.0, 0.35, 1.0, 0.12);
            scatter(world, ParticleTypes.CRIT,  p.x(), p.y(), p.z(),  50, 0.8, 0.25, 0.8, 0.18);
        });
    }

    // ---- Rage activation pulse ----

    public static void handleRagePulse(StrengthRagePulsePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = p.x(), cy = p.y() + 1.0, cz = p.z();

            scatter(world, RAGE_RED, cx, cy, cz, 28, 0.12, 0.18, 0.12, 0.06);

            int    points = 24;
            double radius = 3.35;
            double speed  = 0.45;
            double ringY  = p.y() + 0.15;

            for (int i = 0; i < points; i++) {
                double a  = (Math.PI * 2.0) * (i / (double) points);
                double dx = Math.cos(a);
                double dz = Math.sin(a);
                world.addParticle(RAGE_RED,
                        cx + dx * radius, ringY, cz + dz * radius,
                        dx * speed, 0.03, dz * speed);
            }

            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, cx, p.y() + 0.35, cz, 0, 0, 0);
            scatter(world, ParticleTypes.POOF, cx, p.y() + 0.10, cz, 10, 0.35, 0.05, 0.35, 0.05);
        });
    }

    // ---- Per-tick rage aura ----

    public static void handleRageAura(StrengthRageAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, RAGE_RED, p.x(), p.y(), p.z(), 1, 0.55, 0.55, 0.55, 0.02);
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
