package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class CosmicFxClient {

    private CosmicFxClient() {}

    private static final DustParticleEffect COSMIC_PURPLE =
            new DustParticleEffect(new Vector3f(0.4f, 0.0f, 0.6f), 1.1f);

    private static final int FATE_TIMER_MAX = 300;

    // ----------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------

    public static void handleFateAura(CosmicFateAuraPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity ent = world.getEntityById(payload.entityId());
            if (!(ent instanceof LivingEntity target)) return;

            float storedDamage = payload.storedDamage();
            int timerTicks    = payload.timerTicks();
            int starCount     = MathHelper.clamp((int) storedDamage, 0, 19);
            long time         = world.getTime();

            float  timerFraction = (float) timerTicks / FATE_TIMER_MAX;
            double speed         = 0.05 + (1.0 - timerFraction) * 0.35;
            double angle         = time * speed;
            double orbitRadius   = 0.8 + 0.35;
            int    trailPoints   = (int)(3 + speed * 10);

            for (int t = 0; t < trailPoints; t++) {
                double trailAngle = angle - (t * 0.15);
                double cx   = target.getX() + Math.cos(trailAngle) * orbitRadius;
                double cz   = target.getZ() + Math.sin(trailAngle) * orbitRadius;
                double cy   = target.getBodyY(0.55);
                float  size = 1.2f - (t * 0.08f);

                world.addParticle(
                        new DustParticleEffect(new Vector3f(1.0f, 0.5f, 0.1f), Math.max(0.4f, size)),
                        cx, cy, cz, 0.0, 0.0, 0.0);
            }

            if (timerFraction < 0.3f) {
                int flameCount = (int)((0.3f - timerFraction) * 20);
                for (int i = 0; i < flameCount; i++) {
                    double randAngle = world.random.nextDouble() * Math.PI * 2;
                    double r  = orbitRadius + 0.1 + world.random.nextDouble() * 0.2;
                    double fx = target.getX() + Math.cos(randAngle) * r;
                    double fz = target.getZ() + Math.sin(randAngle) * r;
                    double fy = target.getBodyY(0.55) + world.random.nextDouble() * 0.3;
                    world.addParticle(ParticleTypes.FLAME, fx, fy, fz, 0.01, 0.02, 0.01);
                }
            }

            if (target.age % 3 != 0) return;

            for (int i = 0; i < starCount; i++) {
                long seed    = (long) target.getId() * 341873128712L + (long) i * 132897987541L;
                double offX  = ((seed >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
                double offY  = ((seed >> 8)  & 0xFF) / 255.0;
                double offZ  = ((seed)        & 0xFF) / 255.0 * 2.0 - 1.0;
                world.addParticle(ParticleTypes.WHITE_ASH,
                        target.getX() + offX * 0.8,
                        target.getBodyY(offY * 1.2),
                        target.getZ() + offZ * 0.8,
                        0.01, 0.02, 0.01);
            }
        });
    }

    public static void handleDetonateStart(CosmicDetonateStartPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y() + 1.0, z = payload.z();

            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.FLASH,
                        x + (world.random.nextDouble() * 2 - 1) * 0.2,
                        y + (world.random.nextDouble() * 2 - 1) * 0.2,
                        z + (world.random.nextDouble() * 2 - 1) * 0.2,
                        0.0, 0.0, 0.0);
            }

            for (int i = 0; i < 25; i++) {
                double vx = (world.random.nextDouble() - 0.5) * 0.6;
                double vy = world.random.nextDouble() * 0.6;
                double vz = (world.random.nextDouble() - 0.5) * 0.6;
                world.addParticle(ParticleTypes.END_ROD, x, y, z, vx, vy, vz);
            }
        });
    }

    public static void handleDetonateTick(CosmicDetonateTickPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity ent = world.getEntityById(payload.entityId());
            if (!(ent instanceof LivingEntity target)) return;

            Vec3d pos = target.getPos();
            for (int i = 0; i < 6; i++) {
                double a = world.getTime() * 0.3 + i;
                double x = pos.x + Math.cos(a) * 0.8;
                double z = pos.z + Math.sin(a) * 0.8;
                double y = pos.y + 0.8;
                world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                        (pos.x - x) * 0.2, 0.02, (pos.z - z) * 0.2);
            }
        });
    }

    public static void handleFateCap(CosmicFateCapPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double px = payload.x(), py = payload.y(), pz = payload.z();

            for (int i = 0; i < 40; i++) {
                double a     = world.random.nextDouble() * Math.PI * 2;
                double speed = 0.4 + world.random.nextDouble() * 0.6;
                world.addParticle(ParticleTypes.END_ROD, px, py, pz,
                        Math.cos(a) * speed,
                        (world.random.nextDouble() - 0.3) * 0.6,
                        Math.sin(a) * speed);
            }
        });
    }

    public static void handleRay(CosmicRayPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(payload.startX(), payload.startY(), payload.startZ());
            Vec3d to   = new Vec3d(payload.endX(),   payload.endY(),   payload.endZ());
            Vec3d dir  = to.subtract(from);
            double len = dir.length();
            Vec3d step = dir.normalize().multiply(0.25);
            int count  = (int)(len / 0.25);
            Vec3d pos  = from;

            for (int i = 0; i < count; i++) {
                double a      = i * 0.4;
                double spiral = 0.08;
                double x = pos.x + Math.cos(a) * spiral;
                double z = pos.z + Math.sin(a) * spiral;

                world.addParticle(COSMIC_PURPLE, x, pos.y, z, 0.0, 0.0, 0.0);

                if (i % 2 == 0) {
                    world.addParticle(ParticleTypes.SMOKE, x, pos.y, z, 0.01, 0.01, 0.01);
                }
                if (world.random.nextFloat() < 0.25f) {
                    world.addParticle(ParticleTypes.END_ROD, x, pos.y, z, 0.02, 0.02, 0.02);
                }

                pos = pos.add(step);
            }
        });
    }

    public static void handleRayHit(CosmicRayHitPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y(), z = payload.z();

            world.addParticle(ParticleTypes.FLASH,
                    x + (world.random.nextDouble() * 2 - 1) * 0.05,
                    y + (world.random.nextDouble() * 2 - 1) * 0.05,
                    z + (world.random.nextDouble() * 2 - 1) * 0.05,
                    0.0, 0.0, 0.0);

            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.END_ROD,
                        x + (world.random.nextDouble() * 2 - 1) * 0.2,
                        y + (world.random.nextDouble() * 2 - 1) * 0.2,
                        z + (world.random.nextDouble() * 2 - 1) * 0.2,
                        0.0, 0.02, 0.0);
            }
        });
    }

    public static void handleRayImpact(CosmicRayImpactPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y(), z = payload.z();

            for (int i = 0; i < 2; i++) {
                world.addParticle(ParticleTypes.FLASH,
                        x + (world.random.nextDouble() * 2 - 1) * 0.1,
                        y + (world.random.nextDouble() * 2 - 1) * 0.1,
                        z + (world.random.nextDouble() * 2 - 1) * 0.1,
                        0.0, 0.0, 0.0);
            }

            for (int i = 0; i < 8; i++) {
                world.addParticle(ParticleTypes.END_ROD, x, y, z,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.3,
                        (world.random.nextDouble() - 0.5) * 0.4);
            }
        });
    }

    public static void handleStarTrail(CosmicStarTrailPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y() + 0.5, z = payload.z();

            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.END_ROD, x, y, z,
                        (world.random.nextDouble() - 0.5) * 0.2,
                        0.05,
                        (world.random.nextDouble() - 0.5) * 0.2);
            }
        });
    }

    public static void handleSlam(CosmicSlamPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = payload.x(), y = payload.y(), z = payload.z();

            for (int i = 0; i < 30; i++) {
                double angle = (Math.PI * 2) * i / 30;
                world.addParticle(ParticleTypes.END_ROD,
                        x, y + 0.1, z,
                        Math.cos(angle) * 0.4, 0.1, Math.sin(angle) * 0.4);
            }

            for (int i = 0; i < 3; i++) {
                world.addParticle(ParticleTypes.FLASH,
                        x + (world.random.nextDouble() * 2 - 1) * 0.2,
                        y + 0.5 + (world.random.nextDouble() * 2 - 1) * 0.2,
                        z + (world.random.nextDouble() * 2 - 1) * 0.2,
                        0.0, 0.0, 0.0);
            }
        });
    }
}
