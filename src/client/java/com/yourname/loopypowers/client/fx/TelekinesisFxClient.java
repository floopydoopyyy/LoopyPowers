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
public final class TelekinesisFxClient {

    private TelekinesisFxClient() {}

    private static final DustParticleEffect TK_PINK       = new DustParticleEffect(new Vector3f(1.0f, 0.2f, 0.7f), 1.2f);
    private static final DustParticleEffect TK_LIGHT_PINK = new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.85f), 0.9f);
    private static final DustParticleEffect TK_MAGENTA    = new DustParticleEffect(new Vector3f(0.85f, 0.0f, 0.5f), 1.5f);
    private static final DustParticleEffect TK_PINK_LARGE = new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.75f), 2.2f);
    private static final DustParticleEffect TK_DARK_PINK  = new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.35f), 1.8f);

    private static final double CHOKE_ORBIT_RADIUS_START = 0.5;
    private static final double CHOKE_ORBIT_RADIUS_END   = 0.2;

    // ---- Helpers ----

    private static void spawnImpactRing(ClientWorld world, double x, double y, double z, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            world.addParticle(TK_MAGENTA,
                    x, y + 0.1, z,
                    Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius);
        }
        scatter(world, TK_PINK, x, y + 0.5, z, 6, 0.3, 0.3, 0.3, 0.05);
    }

    // ---- Yank fall damage ----

    public static void handleYankFall(TKYankFallPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, TK_MAGENTA, p.x(), p.y(), p.z(), 15, 0.4, 0.2, 0.4, 0.05);
        });
    }

    // ---- TK beam (yank + secondary) ----

    public static void handleBeam(TKBeamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());
            Vec3d dir  = to.subtract(from);
            int steps  = (int)(dir.length() / 0.35);
            Vec3d step = dir.normalize().multiply(0.35);
            Vec3d pos  = from;

            for (int i = 0; i < steps; i++) {
                if (i % 2 == 0) world.addParticle(TK_PINK, pos.x, pos.y, pos.z, 0, 0, 0);
                if (world.random.nextFloat() < 0.2f)
                    world.addParticle(TK_LIGHT_PINK, pos.x, pos.y, pos.z, 0, 0, 0);
                pos = pos.add(step);
            }
        });
    }

    // ---- Passive hit ----

    public static void handlePassiveHit(TKPassiveHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            spawnImpactRing(world, le.getX(), le.getY(), le.getZ(), 10, 0.3);
            scatter(world, TK_LIGHT_PINK, le.getX(), le.getBodyY(0.7), le.getZ(), 4, 0.2, 0.3, 0.2, 0.03);
        });
    }

    // ---- Yank target ----

    public static void handleYankTarget(TKYankTargetPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            spawnImpactRing(world, le.getX(), le.getY(), le.getZ(), 8, 0.25);
        });
    }

    // ---- Grab target ----

    public static void handleGrabTarget(TKGrabTargetPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            spawnImpactRing(world, le.getX(), le.getY(), le.getZ(), 10, 0.3);
        });
    }

    // ---- Wall impact ----

    public static void handleWallImpact(TKWallImpactPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            double x = le.getX(), y = le.getY() + 0.8, z = le.getZ();
            spawnImpactRing(world, x, y - 0.7, z, 18, 0.4);
            scatter(world, TK_MAGENTA,    x, y, z, 10, 0.4, 0.4, 0.4, 0.07);
            scatter(world, TK_PINK_LARGE, x, y - 0.2, z, 5, 0.3, 0.3, 0.3, 0.04);

            for (int i = 0; i < 12; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double speed = 0.1 + world.random.nextDouble() * 0.2;
                world.addParticle(TK_DARK_PINK, x, y, z,
                        Math.cos(angle) * speed,
                        (world.random.nextDouble() - 0.3) * speed,
                        Math.sin(angle) * speed);
            }
        });
    }

    // ---- Floor impact ----

    public static void handleFloorImpact(TKFloorImpactPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            double x = le.getX(), y = le.getY(), z = le.getZ();
            spawnImpactRing(world, x, y, z, 12, 0.35);
            scatter(world, TK_PINK, x, y + 0.2, z, 5, 0.3, 0.1, 0.3, 0.04);
        });
    }

    // ---- Suspend aura (per tick) ----

    public static void handleSuspendAura(TKSuspendAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            double radius = 0.7;
            long time = p.time();
            for (int i = 0; i < 6; i++) {
                double angle = (time * 0.12) + (i * Math.PI * 2.0 / 6);
                world.addParticle(TK_PINK,
                        le.getX() + Math.cos(angle) * radius, le.getBodyY(0.6),
                        le.getZ() + Math.sin(angle) * radius, 0, 0.02, 0);
            }
            if (time % 3 == 0) {
                scatter(world, TK_LIGHT_PINK, le.getX(), le.getBodyY(0.5), le.getZ(), 2, 0.25, 0.1, 0.25, 0.01);
            }
        });
    }

    // ---- Choke aura (per tick) ----

    public static void handleChokeAura(TKChokeAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;

            float cp = p.chokeProgress();
            long time = p.time();
            double radius = CHOKE_ORBIT_RADIUS_START + (CHOKE_ORBIT_RADIUS_END - CHOKE_ORBIT_RADIUS_START) * cp;
            double spinSpeed = 0.12 + cp * 0.30;
            int points = 8;

            for (int i = 0; i < points; i++) {
                double angle = (time * spinSpeed) + (i * Math.PI * 2.0 / points);
                world.addParticle(TK_DARK_PINK,
                        le.getX() + Math.cos(angle) * radius,
                        le.getBodyY(0.3 + cp * 0.4),
                        le.getZ() + Math.sin(angle) * radius,
                        0, 0.01, 0);
            }

            if (cp > 0.5f) {
                double innerRadius = radius * 0.4;
                for (int i = 0; i < 4; i++) {
                    double angle = -(time * spinSpeed * 1.5) + (i * Math.PI / 2.0);
                    world.addParticle(TK_MAGENTA,
                            le.getX() + Math.cos(angle) * innerRadius, le.getBodyY(0.5),
                            le.getZ() + Math.sin(angle) * innerRadius, 0, 0.02, 0);
                }
            }

            if (time % 2 == 0) {
                scatter(world, TK_DARK_PINK, le.getX(), le.getBodyY(0.8), le.getZ(), 2, 0.15, 0.08, 0.15, 0.03);
            }
        });
    }

    // ---- Entity throw ----

    public static void handleThrow(TKThrowPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            spawnImpactRing(world, le.getX(), le.getY(), le.getZ(), 14, 0.4);
            scatter(world, TK_MAGENTA, le.getX(), le.getBodyY(0.5), le.getZ(), 8, 0.3, 0.3, 0.3, 0.06);
        });
    }

    // ---- Ult activate burst ----

    public static void handleDebrisActivate(TKDebrisActivatePayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = p.x(), cy = p.y(), cz = p.z();

            for (int ring = 0; ring < 3; ring++) {
                double ringRadius = 1.5 + ring * 2.5;
                int points = 12 + ring * 6;
                for (int i = 0; i < points; i++) {
                    double angle = Math.PI * 2.0 * i / points;
                    world.addParticle(ring % 2 == 0 ? TK_PINK_LARGE : TK_MAGENTA,
                            cx + Math.cos(angle) * ringRadius, cy,
                            cz + Math.sin(angle) * ringRadius,
                            0, 0.12, 0);
                }
            }

            for (int i = 0; i < 20; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 1.5;
                world.addParticle(TK_DARK_PINK,
                        cx + Math.cos(a) * r,
                        cy + world.random.nextDouble() * 2.0,
                        cz + Math.sin(a) * r,
                        0, 0.15, 0);
            }
        });
    }

    // ---- Orbit block tick ----

    public static void handleDebrisOrbit(TKDebrisOrbitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            world.addParticle(p.isInner() ? TK_MAGENTA : TK_DARK_PINK,
                    p.x() + world.random.nextGaussian() * 0.04,
                    p.y() + world.random.nextGaussian() * 0.04,
                    p.z() + world.random.nextGaussian() * 0.04,
                    world.random.nextGaussian() * 0.01,
                    world.random.nextGaussian() * 0.01,
                    world.random.nextGaussian() * 0.01);
        });
    }

    // ---- Entity being pulled ----

    public static void handleDebrisPull(TKDebrisPullPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            world.addParticle(TK_LIGHT_PINK,
                    le.getX(), le.getBodyY(0.5), le.getZ(),
                    p.pullX(), p.pullY(), p.pullZ());
        });
    }

    // ---- Debris field aura rings ----

    public static void handleDebrisAura(TKDebrisAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double cx = p.x(), cy = p.y(), cz = p.z();
            long time = p.time();

            if (time % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double angle = world.random.nextDouble() * Math.PI * 2;
                    double r = 2.5 + world.random.nextDouble() * 2.0;
                    scatter(world, TK_DARK_PINK,
                            cx + Math.cos(angle) * r,
                            cy + world.random.nextDouble() * 2.5,
                            cz + Math.sin(angle) * r,
                            1, 0.1, 0.1, 0.1, 0.05);
                }
            }

            double[] radii    = { 2.5, 3.3, 4.1 };
            double[] speeds   = { 0.06, 0.08, 0.10 };
            double[] dirs     = { 1, -1, 1 };
            int[]    pts      = { 4, 6, 8 };
            DustParticleEffect[] cols = { TK_MAGENTA, TK_PINK, TK_LIGHT_PINK };

            for (int ring = 0; ring < 3; ring++) {
                double ringR = radii[ring];
                double ringDir = dirs[ring];
                double ringSpeed = speeds[ring];

                for (int i = 0; i < pts[ring]; i++) {
                    double a = (time * ringSpeed * ringDir) + (i * Math.PI * 2.0 / pts[ring]);
                    world.addParticle(cols[ring],
                            cx + Math.cos(a) * ringR,
                            cy + Math.sin(a * 0.5) * 0.3,
                            cz + Math.sin(a) * ringR,
                            0, 0.015, 0);
                }
            }
        });
    }

    // ---- Orbit block thrown ----

    public static void handleDebrisThrow(TKDebrisThrowPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            spawnImpactRing(world, p.x(), p.y(), p.z(), 8, 0.25);
            scatter(world, TK_MAGENTA, p.x(), p.y(), p.z(), 4, 0.2, 0.2, 0.2, 0.06);
        });
    }

    // ---- Debris explosion (block impact) ----

    public static void handleDebrisExplosion(TKDebrisExplosionPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = p.x(), y = p.y(), z = p.z();

            // slam impact
            spawnImpactRing(world, x, y, z, 24, 0.5);
            for (int i = 0; i < 20; i++) {
                double angle = Math.PI * 2.0 * i / 20;
                world.addParticle(TK_PINK_LARGE,
                        x + Math.cos(angle) * 1.5, y + 0.1, z + Math.sin(angle) * 1.5,
                        Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15);
            }
            scatter(world, TK_MAGENTA, x, y + 0.3, z, 12, 0.5, 0.4, 0.5, 0.08);
            scatter(world, ParticleTypes.END_ROD, x, y + 0.5, z, 8, 0.4, 0.3, 0.4, 0.06);

            // extra explosion FX
            scatter(world, ParticleTypes.EXPLOSION, x, y + 0.5, z, 3, 0.5, 0.3, 0.5, 0.1);
            for (int i = 0; i < 15; i++) {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = world.random.nextDouble() * 0.8;
                world.addParticle(TK_DARK_PINK,
                        x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r,
                        Math.cos(a) * 0.3, 0.2, Math.sin(a) * 0.3);
            }
        });
    }

    // ---- New orbit block spawned (regen) ----

    public static void handleDebrisSpawn(TKDebrisSpawnPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, TK_MAGENTA, p.x(), p.y(), p.z(), 6, 0.2, 0.2, 0.2, 0.05);
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
