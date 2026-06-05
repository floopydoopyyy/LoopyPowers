package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

@Environment(EnvType.CLIENT)
public final class NatureFxClient {

    private NatureFxClient() {}

    private static final DustParticleEffect GAS_DUST =
            new DustParticleEffect(new Vector3f(0.12f, 0.95f, 0.18f), 1.75f);
    private static final DustParticleEffect VINE_DUST =
            new DustParticleEffect(new Vector3f(0.10f, 0.85f, 0.12f), 1.35f);
    private static final DustParticleEffect VINE_PINK_DUST =
            new DustParticleEffect(new Vector3f(0.95f, 0.35f, 0.85f), 1.05f);
    private static final DustParticleEffect CAGE_VINE_DUST =
            new DustParticleEffect(new Vector3f(0.10f, 0.85f, 0.12f), 1.5f);

    private static final float VINE_PINK_SPECK_CHANCE = 0.12f;
    private static final int VINE_STRIKE_LASHES_PER_TARGET = 3;

    // ---- Secondary: cage eruption burst ----

    public static void handleCageFx(NatureCageFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            int cx = p.cx(), cy = p.cy(), cz = p.cz();
            int radius = p.radius(), thickness = p.thickness();
            int points = 80;

            BlockStateParticleEffect dirt = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DIRT.getDefaultState());

            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0) * (i / (double) points);
                for (int t = 0; t < thickness; t++) {
                    double rNow = radius - t + (world.random.nextDouble() * 0.5 - 0.25);
                    double x = cx + Math.cos(a) * rNow;
                    double z = cz + Math.sin(a) * rNow;

                    // find ground level visually
                    int y = cy + 2;
                    while (y > cy - 10 && world.getBlockState(new BlockPos((int) x, y, (int) z)).isAir()) {
                        y--;
                    }

                    world.addParticle(dirt,          x, y + 1.0, z, 0, 0.2 + world.random.nextDouble() * 0.3, 0);
                    world.addParticle(CAGE_VINE_DUST, x, y + 1.0, z, 0, 0.3 + world.random.nextDouble() * 0.4, 0);
                    if (world.random.nextFloat() < 0.3f) {
                        world.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y + 1.0, z, 0, 0.1, 0);
                    }
                }
            }
        });
    }

    // ---- Primary: gas cloud ----

    public static void handleGasTick(NatureGasTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            float r = p.radius();
            float halfH = p.halfH();
            long now = world.getTime();
            int seed = p.seed();

            // Thick core — Gaussian for rounded, cylindrical shape
            for (int i = 0; i < p.count(); i++) {
                world.addParticle(GAS_DUST,
                        p.x() + world.random.nextGaussian() * (r * 0.9),
                        p.y() + world.random.nextGaussian() * (halfH * 0.9),
                        p.z() + world.random.nextGaussian() * (r * 0.9),
                        0, 0, 0);
            }

            // Boundary wisps
            if (((now + seed) & 1L) == 0L) {
                int wispCount = Math.max(20, p.count() / 3);
                for (int i = 0; i < wispCount; i++) {
                    world.addParticle(GAS_DUST,
                            p.x() + world.random.nextGaussian() * (r * 1.1),
                            p.y() + world.random.nextGaussian() * (halfH * 0.55),
                            p.z() + world.random.nextGaussian() * (r * 1.1),
                            0, 0, 0);
                }
            }

            // Occasional full volume
            if (((now + seed) % 10L) == 0L) {
                for (int i = 0; i < 120; i++) {
                    world.addParticle(GAS_DUST,
                            p.x() + world.random.nextGaussian() * (r * 0.7),
                            p.y() + world.random.nextGaussian() * (halfH * 0.7),
                            p.z() + world.random.nextGaussian() * (r * 0.7),
                            0, 0, 0);
                }
            }
        });
    }

    // ---- Ultimate: vine cast burst ----

    public static void handleVineCast(NatureVineCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, VINE_DUST, p.x(), p.y(), p.z(), 80, 0.75, 0.95, 0.75, 0.02);
        });
    }

    // ---- Ultimate: vine bind (strike + lashes + bind FX + anchor puff) ----

    public static void handleVineBind(NatureVineBindPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());

            // main strike line
            Vec3d targetCenter = le.getPos().add(0, le.getHeight() * 0.65, 0);
            spawnVineStrikeLine(world, from, targetCenter, p.seed());

            // extra lashes around target
            Random r = new Random(p.seed() ^ 0x52A1B);
            Vec3d base = targetCenter;
            for (int i = 0; i < VINE_STRIKE_LASHES_PER_TARGET; i++) {
                double ox = (r.nextDouble() - 0.5) * 1.8;
                double oy = (r.nextDouble() - 0.5) * 1.2;
                double oz = (r.nextDouble() - 0.5) * 1.8;
                spawnVineStrikeLine(world, from, base.add(ox, oy, oz), p.seed() ^ (i * 1337));
            }

            // bind burst at entity
            double ex = le.getX(), ey = le.getY() + le.getHeight() * 0.55, ez = le.getZ();
            scatter(world, VINE_DUST,      ex, ey, ez, 40, 0.45, 0.45, 0.45, 0.02);
            scatter(world, VINE_PINK_DUST, ex, ey, ez,  6, 0.35, 0.35, 0.35, 0.01);

            // anchor puff
            scatter(world, VINE_DUST, p.anchorX(), p.anchorY() + 0.2, p.anchorZ(), 25, 0.35, 0.15, 0.35, 0.02);
        });
    }

    // ---- Ultimate: extra random lash line ----

    public static void handleVineStrike(NatureVineStrikePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnVineStrikeLine(world,
                    new Vec3d(p.fromX(), p.fromY(), p.fromZ()),
                    new Vec3d(p.toX(), p.toY(), p.toZ()),
                    p.seed());
        });
    }

    // ---- Tether line (per tick while vines active) ----

    public static void handleVineTether(NatureVineTetherPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());
            Vec3d delta = to.subtract(from);
            double len = delta.length();
            if (len < 0.001) return;

            int steps = MathHelper.clamp((int)(len * 10), 10, 60);
            Vec3d step = delta.multiply(1.0 / steps);
            long now = world.getTime();

            Vec3d pos = from;
            for (int i = 0; i <= steps; i++) {
                DustParticleEffect eff = ((now + p.seed() + i) % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;
                world.addParticle(eff,
                        pos.x + world.random.nextGaussian() * 0.02,
                        pos.y + world.random.nextGaussian() * 0.02,
                        pos.z + world.random.nextGaussian() * 0.02,
                        0.0, 0.0, 0.0);
                pos = pos.add(step);
            }

            // Anchor puff — computed client-side matching NeoForge
            if ((now & 3L) == 0L) {
                for (int i = 0; i < 8; i++) {
                    world.addParticle(VINE_DUST,
                            from.x + world.random.nextGaussian() * 0.20,
                            from.y + 0.15 + world.random.nextGaussian() * 0.10,
                            from.z + world.random.nextGaussian() * 0.20,
                            0, 0, 0);
                }
                for (int i = 0; i < 2; i++) {
                    world.addParticle(VINE_PINK_DUST,
                            from.x + world.random.nextGaussian() * 0.20,
                            from.y + 0.15 + world.random.nextGaussian() * 0.10,
                            from.z + world.random.nextGaussian() * 0.20,
                            0, 0, 0);
                }
            }
        });
    }

    // ---- Buff ring (caster buffed near vined enemy) ----

    public static void handleBuffRing(NatureBuffRingPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = p.x(), cy = p.y(), cz = p.z();
            double y = cy + 0.15;
            int points = 24;
            double radius1 = 0.85, radius2 = 1.15;
            double spin = p.time() * 0.22;

            for (int i = 0; i < points; i++) {
                double a = spin + (Math.PI * 2.0) * (i / (double) points);

                double x1 = cx + Math.cos(a) * radius1;
                double z1 = cz + Math.sin(a) * radius1;
                double x2 = cx + Math.cos(a + 0.35) * radius2;
                double z2 = cz + Math.sin(a + 0.35) * radius2;

                DustParticleEffect eff1 = (p.time() % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;
                world.addParticle(eff1, x1, y, z1, 0.0, 0.0, 0.0);

                if ((i & 1) == 0) {
                    DustParticleEffect eff2 = ((p.time() + i) % 11L == 0L) ? VINE_PINK_DUST : VINE_DUST;
                    world.addParticle(eff2, x2, y + 0.10, z2, 0.0, 0.0, 0.0);
                }
            }

            scatter(world, VINE_DUST,      cx, cy + 0.9, cz, 6, 0.20, 0.35, 0.20, 0.01);
            scatter(world, VINE_PINK_DUST, cx, cy + 0.9, cz, 2, 0.20, 0.35, 0.20, 0.01);
        });
    }

    // ---- Internal helpers ----

    private static void spawnVineStrikeLine(ClientWorld world, Vec3d from, Vec3d to, int seed) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 14), 12, 90);
        Vec3d step = delta.multiply(1.0 / steps);

        Random r = new Random(seed);
        Vec3d p = from;

        for (int i = 0; i <= steps; i++) {
            double j = 0.05 + (r.nextDouble() * 0.04);
            double jx = (r.nextDouble() - 0.5) * j;
            double jy = (r.nextDouble() - 0.5) * j;
            double jz = (r.nextDouble() - 0.5) * j;

            DustParticleEffect eff = (r.nextFloat() < VINE_PINK_SPECK_CHANCE) ? VINE_PINK_DUST : VINE_DUST;
            world.addParticle(eff, p.x + jx, p.y + jy, p.z + jz, 0.0, 0.0, 0.0);
            p = p.add(step);
        }

        // hit burst
        scatter(world, VINE_DUST,      to.x, to.y, to.z, 8, 0.20, 0.20, 0.20, 0.02);
        scatter(world, VINE_PINK_DUST, to.x, to.y, to.z, 2, 0.20, 0.20, 0.20, 0.02);
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
