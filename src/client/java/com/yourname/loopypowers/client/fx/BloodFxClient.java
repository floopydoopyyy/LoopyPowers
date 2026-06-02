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
public final class BloodFxClient {

    private BloodFxClient() {}

    private static final DustParticleEffect BLOOD_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.25f);
    private static final DustParticleEffect POP_RED_DUST =
            new DustParticleEffect(new Vector3f(0.8f, 0.0f, 0.0f), 2.5f);

    // ----------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------

    public static void handleBleedApply(BloodBleedApplyPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnScattered(world, ParticleTypes.DAMAGE_INDICATOR,
                    payload.x(), payload.y() + 1.0, payload.z(),
                    payload.count(), 0.25, 0.35, 0.25, 0.02);
        });
    }

    public static void handleBleedTick(BloodBleedTickPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnScattered(world, ParticleTypes.DAMAGE_INDICATOR,
                    payload.x(), payload.y() + 1.0, payload.z(),
                    6, 0.30, 0.40, 0.30, 0.02);
        });
    }

    public static void handleWhipBeam(BloodWhipBeamPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnBloodBeam(world,
                    payload.startX(), payload.startY(), payload.startZ(),
                    payload.endX(), payload.endY(), payload.endZ());
        });
    }

    public static void handleWhipHit(BloodWhipHitPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnScattered(world, ParticleTypes.DAMAGE_INDICATOR,
                    payload.x(), payload.y() + 1.0, payload.z(),
                    10, 0.35, 0.45, 0.35, 0.02);
        });
    }

    public static void handleClotCast(BloodClotCastPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnScattered(world, BLOOD_DUST,
                    payload.x(), payload.y() + 1.0, payload.z(),
                    10, 0.25, 0.30, 0.25, 0.02);
        });
    }

    public static void handlePop(BloodPopPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = payload.x(), y = payload.y() + 1.0, z = payload.z();
            spawnScattered(world, POP_RED_DUST, x, y, z, 80, 0.4, 0.6, 0.4, 0.15);
            spawnScattered(world, ParticleTypes.CRIT, x, y, z, 40, 0.5, 0.5, 0.5, 0.1);
            world.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0.0, 0.0, 0.0);
        });
    }

    public static void handleChain(BloodChainPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnBloodChain(world,
                    payload.startX(), payload.startY(), payload.startZ(),
                    payload.endX(), payload.endY(), payload.endZ());
        });
    }

    public static void handleBindTick(BloodBindTickPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity ce = world.getEntityById(payload.casterId());
            Entity te = world.getEntityById(payload.targetId());
            if (!(ce instanceof LivingEntity caster) || !(te instanceof LivingEntity target)) return;

            float strain = payload.strain();

            spawnBindAura(world, caster, strain);
            spawnBindAura(world, target, strain);
            spawnTetherParticles(world, caster, target);

            if (strain > 0.55f) {
                Vec3d cp = caster.getPos().add(0, caster.getHeight() * 0.65, 0);
                Vec3d tp = target.getPos().add(0, target.getHeight() * 0.65, 0);
                world.addParticle(ParticleTypes.CRIT,
                        cp.x + (world.random.nextDouble() * 2 - 1) * 0.15,
                        cp.y + (world.random.nextDouble() * 2 - 1) * 0.15,
                        cp.z + (world.random.nextDouble() * 2 - 1) * 0.15,
                        0.0, 0.0, 0.0);
                world.addParticle(ParticleTypes.CRIT,
                        tp.x + (world.random.nextDouble() * 2 - 1) * 0.15,
                        tp.y + (world.random.nextDouble() * 2 - 1) * 0.15,
                        tp.z + (world.random.nextDouble() * 2 - 1) * 0.15,
                        0.0, 0.0, 0.0);
            }
        });
    }

    public static void handleBindDamageFx(BloodBindDamageFxPayload payload, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity ce = world.getEntityById(payload.casterId());
            Entity te = world.getEntityById(payload.targetId());
            if (!(ce instanceof LivingEntity caster) || !(te instanceof LivingEntity target)) return;

            spawnBindDamageFx(world, caster, target, payload.intensity());
        });
    }

    // ----------------------------------------------------------------
    // PRIVATE HELPERS  (mirror server-side logic, now runs on client)
    // ----------------------------------------------------------------

    private static void spawnBloodBeam(ClientWorld world,
            double startX, double startY, double startZ,
            double endX, double endY, double endZ) {

        Vec3d start = new Vec3d(startX, startY, startZ);
        Vec3d end   = new Vec3d(endX, endY, endZ);
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir   = delta.multiply(1.0 / len);
        Vec3d up    = new Vec3d(0, 1, 0);
        Vec3d right = dir.crossProduct(up).normalize();
        if (right.lengthSquared() < 0.01) right = new Vec3d(1, 0, 0);
        Vec3d perp  = dir.crossProduct(right).normalize();

        int steps = MathHelper.clamp((int)(len / 0.25), 6, 140);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double wave    = Math.sin(t * Math.PI);
            double spiralX = Math.cos(t * 12.0) * 0.5 * wave;
            double spiralY = Math.sin(t * 12.0) * 0.5 * wave;
            Vec3d offset = right.multiply(spiralX).add(perp.multiply(spiralY));

            world.addParticle(BLOOD_DUST,
                    p.x + offset.x, p.y + offset.y, p.z + offset.z,
                    0.0, 0.0, 0.0);

            if (world.random.nextFloat() < 0.1f) {
                world.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                        p.x + offset.x, p.y + offset.y, p.z + offset.z,
                        0.0, 0.0, 0.0);
            }

            p = p.add(dir.multiply(len / steps));
        }
    }

    private static void spawnBloodChain(ClientWorld world,
            double startX, double startY, double startZ,
            double endX, double endY, double endZ) {

        Vec3d start = new Vec3d(startX, startY, startZ);
        Vec3d end   = new Vec3d(endX, endY, endZ);
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir   = delta.multiply(1.0 / len);
        int steps = MathHelper.clamp((int)(len / 0.8), 6, 70);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double jx = (world.random.nextDouble() - 0.5) * 0.12;
            double jy = (world.random.nextDouble() - 0.5) * 0.12;
            double jz = (world.random.nextDouble() - 0.5) * 0.12;

            world.addParticle(BLOOD_DUST, p.x + jx, p.y + jy, p.z + jz, 0.0, 0.0, 0.0);

            if (world.random.nextFloat() < 0.10f) {
                world.addParticle(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 0.0, 0.0, 0.0);
            }

            p = p.add(dir.multiply(len / steps));
        }
    }

    private static void spawnBindAura(ClientWorld world, LivingEntity e, float intensity) {
        Vec3d p     = e.getPos().add(0, e.getHeight() * 0.65, 0);
        int count   = MathHelper.clamp((int)(1 + intensity * 4), 1, 6);
        double spread = 0.25 + intensity * 0.35;

        for (int i = 0; i < count; i++) {
            world.addParticle(BLOOD_DUST,
                    p.x + (world.random.nextDouble() * 2 - 1) * spread,
                    p.y + (world.random.nextDouble() * 2 - 1) * 0.30,
                    p.z + (world.random.nextDouble() * 2 - 1) * spread,
                    0.0, 0.0, 0.0);
        }

        if (world.random.nextFloat() < (0.10f + intensity * 0.35f)) {
            world.addParticle(ParticleTypes.DAMAGE_INDICATOR,
                    p.x + (world.random.nextDouble() * 2 - 1) * 0.08,
                    p.y + (world.random.nextDouble() * 2 - 1) * 0.12,
                    p.z + (world.random.nextDouble() * 2 - 1) * 0.08,
                    0.0, 0.0, 0.0);
        }
    }

    private static void spawnTetherParticles(ClientWorld world, LivingEntity a, LivingEntity b) {
        Vec3d start = a.getPos().add(0, a.getHeight() * 0.6, 0);
        Vec3d end   = b.getPos().add(0, b.getHeight() * 0.6, 0);

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir   = delta.multiply(1.0 / len);
        int steps   = MathHelper.clamp((int)(len / 0.8), 5, 40);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            double t      = i / (double) steps;
            double pulse  = 0.08 + 0.06 * Math.sin((t * 8.0) + (world.getTime() * 0.35));
            double jitter = 0.06;

            world.addParticle(BLOOD_DUST,
                    p.x + (world.random.nextDouble() - 0.5) * (jitter + pulse),
                    p.y + (world.random.nextDouble() - 0.5) * (jitter + pulse),
                    p.z + (world.random.nextDouble() - 0.5) * (jitter + pulse),
                    0.0, 0.0, 0.0);

            p = p.add(dir.multiply(len / steps));
        }

        if (world.random.nextFloat() < 0.85f) {
            for (int i = 0; i < 2; i++) {
                world.addParticle(BLOOD_DUST,
                        start.x + (world.random.nextDouble() * 2 - 1) * 0.15,
                        start.y + (world.random.nextDouble() * 2 - 1) * 0.20,
                        start.z + (world.random.nextDouble() * 2 - 1) * 0.15,
                        0.0, 0.0, 0.0);
                world.addParticle(BLOOD_DUST,
                        end.x + (world.random.nextDouble() * 2 - 1) * 0.15,
                        end.y + (world.random.nextDouble() * 2 - 1) * 0.20,
                        end.z + (world.random.nextDouble() * 2 - 1) * 0.15,
                        0.0, 0.0, 0.0);
            }
        }
    }

    private static void spawnBindDamageFx(ClientWorld world, LivingEntity caster, LivingEntity target, float intensity) {
        Vec3d a = caster.getPos().add(0, caster.getHeight() * 0.65, 0);
        Vec3d b = target.getPos().add(0, target.getHeight() * 0.65, 0);

        int indicatorCount = 3 + (int)(intensity * 4);
        int dustCount      = 4 + (int)(intensity * 6);

        spawnScattered(world, ParticleTypes.DAMAGE_INDICATOR, a.x, a.y, a.z, indicatorCount, 0.25, 0.30, 0.25, 0.02);
        spawnScattered(world, ParticleTypes.DAMAGE_INDICATOR, b.x, b.y, b.z, indicatorCount, 0.25, 0.30, 0.25, 0.02);
        spawnScattered(world, BLOOD_DUST, a.x, a.y, a.z, dustCount, 0.35, 0.40, 0.35, 0.0);
        spawnScattered(world, BLOOD_DUST, b.x, b.y, b.z, dustCount, 0.35, 0.40, 0.35, 0.0);

        Vec3d delta = b.subtract(a);
        double len = delta.length();
        if (len > 0.01) {
            Vec3d dir   = delta.multiply(1.0 / len);
            int steps   = MathHelper.clamp((int)(len / 1.2), 3, 14);
            Vec3d p     = a;
            for (int i = 0; i <= steps; i++) {
                world.addParticle(BLOOD_DUST, p.x, p.y, p.z, 0.0, 0.0, 0.0);
                p = p.add(dir.multiply(len / steps));
            }
        }
    }

    // ----------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------

    private static <T extends net.minecraft.particle.ParticleEffect> void spawnScattered(
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
