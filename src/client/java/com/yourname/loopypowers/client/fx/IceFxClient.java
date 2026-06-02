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
public final class IceFxClient {

    private IceFxClient() {}

    private static final DustParticleEffect FRZ_BLUE_DUST     = new DustParticleEffect(new Vector3f(0.25f, 0.65f, 1.00f), 0.75f);
    private static final DustParticleEffect FRZ_SHIMMER_DUST  = new DustParticleEffect(new Vector3f(0.75f, 0.95f, 1.00f), 0.45f);
    private static final DustParticleEffect SPIKE_TRAIL_DUST  = new DustParticleEffect(new Vector3f(0.55f, 0.85f, 1.00f), 1.10f);
    private static final DustParticleEffect SPIKE_PUFF_DUST   = new DustParticleEffect(new Vector3f(0.35f, 0.80f, 1.00f), 1.35f);
    private static final DustParticleEffect ULT_BLUE_DUST     = new DustParticleEffect(new Vector3f(0.20f, 0.55f, 1.00f), 0.90f);
    private static final DustParticleEffect ULT_SHIMMER_DUST  = new DustParticleEffect(new Vector3f(0.70f, 0.95f, 1.00f), 0.55f);

    // ---- Passive / freeze ----

    public static void handleShatter(IceShatterPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.55, z = le.getZ();
            scatter(world, ParticleTypes.SNOWFLAKE,    x, y, z, 60, 0.35, 0.35, 0.35, 0.0);
            scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 18, 0.25, 0.20, 0.25, 0.0);
            scatter(world, FRZ_BLUE_DUST,              x, y, z, 24, 0.25, 0.20, 0.25, 0.0);
            scatter(world, FRZ_SHIMMER_DUST,           x, y, z, 16, 0.22, 0.18, 0.22, 0.0);
        });
    }

    public static void handleFreezeStage(IceFreezeStagePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            int stage = p.stage();
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.55, z = le.getZ();

            int snow = switch (stage) {
                case 1 -> 1; case 2 -> 3; case 3 -> 5; case 4 -> 7; default -> 12;
            };
            scatter(world, ParticleTypes.SNOWFLAKE, x, y, z, snow, 0.18, 0.22, 0.18, 0.0);
            if (stage >= 2) scatter(world, ParticleTypes.WHITE_ASH,  x, y, z, 1, 0.15, 0.18, 0.15, 0.0);
            if (p.spark())  scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.12, 0.12, 0.12, 0.0);
            if (stage >= 4) scatter(world, FRZ_BLUE_DUST, x, y, z, 1, 0.10, 0.10, 0.10, 0.0);
        });
    }

    public static void handleShatterReady(IceShatterReadyPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            Vec3d c = le.getPos().add(0, le.getHeight() * 0.72, 0);
            double baseAng = p.baseAng();
            double pulse = 0.06 * Math.sin(baseAng / 0.35 * 0.45);
            double r = 0.55 + pulse;
            int points = 14;

            for (int i = 0; i < points; i++) {
                double a = baseAng + (i * (Math.PI * 2.0 / points));
                double x = c.x + Math.cos(a) * r;
                double z = c.z + Math.sin(a) * r;
                double y = c.y + (Math.sin(a * 2.0) * 0.05);

                world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
                if ((i % 2) == 0) scatter(world, FRZ_SHIMMER_DUST,   x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                if ((i % 3) == 0) scatter(world, FRZ_BLUE_DUST,       x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                if ((i % 4) == 0) scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }

            if (p.enchant()) {
                scatter(world, ParticleTypes.ENCHANT,   c.x, c.y - 0.15, c.z, 6,  0.25, 0.22, 0.25, 0.0);
                scatter(world, ParticleTypes.SNOWFLAKE, c.x, c.y - 0.15, c.z, 10, 0.28, 0.22, 0.28, 0.0);
            }
        });
    }

    // ---- Primary / spikes ----

    public static void handleSpikeTrail(IceSpikeTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            float progress = p.progress();
            double back = 0.18;
            double px0 = MathHelper.lerp(MathHelper.clamp(progress - back, 0.0f, 1.0f), p.sx(), p.ex());
            double pz0 = MathHelper.lerp(MathHelper.clamp(progress - back, 0.0f, 1.0f), p.sz(), p.ez());
            double fx  = MathHelper.lerp(progress, p.sx(), p.ex());
            double fz  = MathHelper.lerp(progress, p.sz(), p.ez());
            double y   = p.yHint() + 0.06;

            int steps = 10;
            int seed = p.seed();
            for (int i = 0; i <= steps; i++) {
                double a  = i / (double) steps;
                double x  = MathHelper.lerp(a, px0, fx);
                double z  = MathHelper.lerp(a, pz0, fz);
                double jx = ((seed * 31L + i * 17L) % 100) / 100.0 - 0.5;
                double jz = ((seed * 13L + i * 29L) % 100) / 100.0 - 0.5;

                world.addParticle(SPIKE_TRAIL_DUST, x + jx * 0.08, y, z + jz * 0.08, 0, 0, 0);
                if ((i & 1) == 0) scatter(world, ParticleTypes.SNOWFLAKE, x, y + 0.02, z, 1, 0.05, 0.01, 0.05, 0.0);
            }
        });
    }

    public static void handleWaterFreeze(IceWaterFreezePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.SNOWFLAKE, p.x(), p.y(), p.z(), p.count(), 0.22, 0.10, 0.22, 0.0);
            if (p.enchant()) scatter(world, ParticleTypes.ENCHANT, p.x(), p.y() + 0.05, p.z(), 1, 0.02, 0.02, 0.02, 0.0);
        });
    }

    public static void handleSpikePuff(IceSpikePuffPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, SPIKE_PUFF_DUST,          p.x(), p.y(), p.z(), 18, 0.22, 0.25, 0.22, 0.02);
            scatter(world, ParticleTypes.SNOWFLAKE,  p.x(), p.y() + 0.10, p.z(), 10, 0.25, 0.20, 0.25, 0.0);
        });
    }

    // ---- Secondary / beam ----

    public static void handleBeamCharge(IceBeamChargePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            int points = 14;
            double r = 0.22;
            for (int i = 0; i < points; i++) {
                double a = (p.age() * 0.45) + (i * (Math.PI * 2.0 / points));
                double x = p.mx() + Math.cos(a) * r;
                double z = p.mz() + Math.sin(a) * r;
                double y = p.my() + (world.random.nextDouble() - 0.5) * 0.12;
                world.addParticle(ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
                if ((i % 3) == 0) scatter(world, ParticleTypes.ENCHANT,   x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                if ((i & 1) == 0) scatter(world, ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        });
    }

    public static void handleBeamLine(IceBeamLinePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d start = new Vec3d(p.sx(), p.sy(), p.sz());
            Vec3d end   = new Vec3d(p.ex(), p.ey(), p.ez());
            Vec3d delta = end.subtract(start);
            double len  = delta.length();
            if (len < 0.001) return;

            Vec3d dir   = delta.multiply(1.0 / len);
            Vec3d up    = new Vec3d(0, 1, 0);
            Vec3d right = up.crossProduct(dir);
            if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
            right = right.normalize();
            Vec3d up2 = dir.crossProduct(right).normalize();

            int steps = MathHelper.clamp((int)(len * 10), 24, 140);
            double SPIRAL_R = 0.15;

            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3d pt = start.add(delta.multiply(t));

                world.addParticle(ParticleTypes.WHITE_ASH, pt.x, pt.y, pt.z, 0, 0, 0);

                double ang = (p.time() * 0.45) + (i * 0.65);
                double rx = Math.cos(ang) * SPIRAL_R;
                double ry = Math.sin(ang) * SPIRAL_R;
                Vec3d swirl = pt.add(right.multiply(rx)).add(up2.multiply(ry));
                world.addParticle(ParticleTypes.SNOWFLAKE, swirl.x, swirl.y, swirl.z, 0, 0, 0);

                if ((i % 10) == 0) scatter(world, ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.0);
                if ((i % 14) == 0) world.addParticle(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 0, 0, 0);
            }
        });
    }

    // ---- Misc snow/freeze ----

    public static void handleSnow(IceSnowPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.SNOWFLAKE, p.x(), p.y(), p.z(), p.count(), 0.35, 0.25, 0.35, 0.02);
        });
    }

    // ---- Ultimate ----

    public static void handleBlizzard(IceBlizzardPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double RADIUS = 11.0;
            for (int i = 0; i < 50; i++) {
                double r = Math.sqrt(world.random.nextDouble()) * RADIUS;
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double x = p.cx() + Math.cos(a) * r;
                double z = p.cz() + Math.sin(a) * r;
                double y = p.cy() + 6.0 + world.random.nextDouble() * 3.0;
                scatter(world, ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.25, 0.15, 0.25, 0.0);
                if ((i % 12) == 0) world.addParticle(ParticleTypes.END_ROD, x, y - 0.4, z, 0, 0, 0);
            }
            for (int i = 0; i < 30; i++) {
                double r = Math.sqrt(world.random.nextDouble()) * (RADIUS * 0.9);
                double a = world.random.nextDouble() * Math.PI * 2.0;
                double x = p.cx() + Math.cos(a) * r;
                double z = p.cz() + Math.sin(a) * r;
                double y = p.cy() + 1.0 + world.random.nextDouble() * 2.0;
                scatter(world, ParticleTypes.WHITE_ASH, x, y, z, 1, 0.22, 0.18, 0.22, 0.0);
                if ((i & 3) == 0) scatter(world, ULT_BLUE_DUST, x, y, z, 1, 0.10, 0.08, 0.10, 0.0);
            }
        });
    }

    public static void handleWaveRing(IceWaveRingPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double r = p.radius();
            int points = MathHelper.clamp((int)(r * 14.0), 28, 180);
            double y = p.y();
            for (int i = 0; i < points; i++) {
                double a = (i / (double) points) * (Math.PI * 2.0);
                double x = p.cx() + Math.cos(a) * r;
                double z = p.cz() + Math.sin(a) * r;
                scatter(world, ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.01, 0.02, 0.0);
                if ((i % 5) == 0)  scatter(world, ULT_BLUE_DUST,    x, y + 0.02, z, 1, 0.02, 0.01, 0.02, 0.0);
                if ((i % 9) == 0)  scatter(world, ULT_SHIMMER_DUST, x, y + 0.06, z, 1, 0.02, 0.01, 0.02, 0.0);
                if ((i % 8) == 0)  world.addParticle(ParticleTypes.END_ROD, x, y + 0.05, z, 0, 0, 0);
                if ((i % 11) == 0) scatter(world, ParticleTypes.ENCHANT, x, y + 0.08, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        });
    }

    public static void handleSnowAroundEntity(IceSnowAroundEntityPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            Vec3d pos = le.getPos();
            for (int i = 0; i < 3; i++) {
                double ox = (world.random.nextDouble() - 0.5) * 1.5;
                double oz = (world.random.nextDouble() - 0.5) * 1.5;
                double oy = world.random.nextDouble() * le.getHeight();
                scatter(world, ParticleTypes.SNOWFLAKE, pos.x + ox, pos.y + oy, pos.z + oz, 1, 0.5, 0.1, 0.5, 0.05);
                if (world.random.nextBoolean()) {
                    scatter(world, ParticleTypes.WHITE_ASH, pos.x + ox, pos.y + oy, pos.z + oz, 1, 0.2, 0.0, 0.2, 0.02);
                }
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
