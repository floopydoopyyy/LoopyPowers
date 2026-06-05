package com.yourname.loopypowers.client.fx;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side particle FX for the Sonic Boom ultimate (FlightPower).
 *
 *  Windup  – a ring of cloud particles forms behind the player, drifting
 *            inward as if air is being "sucked" in for the launch.
 *  Dash    – an expanding cylindrical wake of cloud particles traces the
 *            tunnel the player punches through the air each tick.
 *  Impact  – a flat ground-level shockwave ring + an omnidirectional cloud
 *            burst at the collision point.
 */
@Environment(EnvType.CLIENT)
public final class FlightBoomFxClient {

    private FlightBoomFxClient() {}

    // ----------------------------------------------------------------
    // WINDUP  (called every 2 ticks while boomWindup > 0)
    // ----------------------------------------------------------------

    public static void onWindup(ClientWorld world, double cx, double cy, double cz,
                                double lookX, double lookY, double lookZ, boolean isStart) {
        Vec3d look = new Vec3d(lookX, lookY, lookZ).normalize();
        Vec3d back = look.multiply(-1.0).normalize();

        if (isStart) {
            // First frame: wide burst ring snapping into position
            spawnGatherRing(world, cx, cy, cz, back, 2.2, 20, 0.035);
            // A handful of loose poof wisps ejected outward so it "opens up"
            for (int i = 0; i < 6; i++) {
                double ox = (Math.random() - 0.5) * 2.5;
                double oy = (Math.random() - 0.5) * 1.0;
                double oz = (Math.random() - 0.5) * 2.5;
                world.addParticle(ParticleTypes.POOF,
                        cx + ox, cy + oy, cz + oz,
                        ox * 0.04, oy * 0.02, oz * 0.04);
            }
        } else {
            // Ongoing: tighter ring drifting straight toward player centre
            spawnGatherRing(world, cx, cy, cz, back, 1.4, 10, 0.025);
        }
    }

    /**
     * Spawns a ring of CLOUD particles in the plane perpendicular to {@code back},
     * offset one body-length behind the entity. Each particle drifts inward.
     */
    private static void spawnGatherRing(ClientWorld world,
                                        double cx, double cy, double cz,
                                        Vec3d back,
                                        double radius, int count, double inwardSpeed) {
        Vec3d p1 = perpendicular(back);
        Vec3d p2 = back.crossProduct(p1).normalize();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double rx = p1.x * cos + p2.x * sin;
            double ry = p1.y * cos + p2.y * sin;
            double rz = p1.z * cos + p2.z * sin;

            double sx = cx + back.x * 1.5 + rx * radius;
            double sy = cy + back.y * 1.5 + ry * radius;
            double sz = cz + back.z * 1.5 + rz * radius;

            double vx = -rx * inwardSpeed;
            double vy = -ry * inwardSpeed;
            double vz = -rz * inwardSpeed;

            world.addParticle(ParticleTypes.CLOUD, sx, sy, sz, vx, vy, vz);
        }
    }

    // ----------------------------------------------------------------
    // DASH  (called every tick while boomDash > 0)
    // ----------------------------------------------------------------

    public static void onDash(ClientWorld world, double cx, double cy, double cz,
                              double dirX, double dirY, double dirZ, boolean isStart) {
        Vec3d dir = new Vec3d(dirX, dirY, dirZ).normalize();

        if (isStart) {
            // Launch burst: a tight ring that explodes outward
            spawnWakeRing(world, cx, cy, cz, dir, 0.3, 20, 0.35);
            // A few enchanted-hit sparks at the leading edge for a "cutting" feel
            for (int i = 0; i < 8; i++) {
                double spread = 0.6;
                world.addParticle(ParticleTypes.ENCHANTED_HIT,
                        cx + dir.x * 0.5 + (Math.random() - 0.5) * spread,
                        cy + dir.y * 0.5 + (Math.random() - 0.5) * spread,
                        cz + dir.z * 0.5 + (Math.random() - 0.5) * spread,
                        dir.x * 0.06, dir.y * 0.06, dir.z * 0.06);
            }
        } else {
            // Ongoing: moderate expanding wake — the air tunnel
            spawnWakeRing(world, cx, cy, cz, dir, 0.35, 10, 0.18);
        }
    }

    /**
     * Spawns a ring of CLOUD particles centred slightly behind the entity,
     * perpendicular to travel direction. Each particle drifts outward and
     * backwards to carve the tunnel silhouette.
     */
    private static void spawnWakeRing(ClientWorld world,
                                      double cx, double cy, double cz,
                                      Vec3d dir,
                                      double radius, int count, double outSpeed) {
        Vec3d back = dir.multiply(-1.0);
        Vec3d p1 = perpendicular(dir);
        Vec3d p2 = dir.crossProduct(p1).normalize();

        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);

            double rx = p1.x * cos + p2.x * sin;
            double ry = p1.y * cos + p2.y * sin;
            double rz = p1.z * cos + p2.z * sin;

            double sx = cx + rx * radius;
            double sy = cy + ry * radius;
            double sz = cz + rz * radius;

            double vx = rx * outSpeed + back.x * 0.06;
            double vy = ry * outSpeed + back.y * 0.06;
            double vz = rz * outSpeed + back.z * 0.06;

            world.addParticle(ParticleTypes.CLOUD, sx, sy, sz, vx, vy, vz);
        }
    }

    // ----------------------------------------------------------------
    // IMPACT  (called once on terrain/entity collision)
    // ----------------------------------------------------------------

    public static void onImpact(ClientWorld world, double posX, double posY, double posZ) {
        double cy = posY + 0.4;

        // Two concentric horizontal shockwave rings expanding outward
        spawnShockwaveRing(world, posX, cy, posZ, 0.8, 24);
        spawnShockwaveRing(world, posX, cy, posZ, 1.8, 16);

        // Central cloud burst upward and outward
        for (int i = 0; i < 18; i++) {
            double vx = (Math.random() - 0.5) * 0.45;
            double vy = Math.random() * 0.35 + 0.05;
            double vz = (Math.random() - 0.5) * 0.45;
            world.addParticle(ParticleTypes.CLOUD,
                    posX + (Math.random() - 0.5) * 1.8,
                    posY + Math.random() * 1.2,
                    posZ + (Math.random() - 0.5) * 1.8,
                    vx, vy, vz);
        }

        // Crisp enchanted-hit sparkle for the impact flash
        for (int i = 0; i < 10; i++) {
            double spread = 1.2;
            world.addParticle(ParticleTypes.ENCHANTED_HIT,
                    posX + (Math.random() - 0.5) * spread,
                    posY + Math.random() * spread,
                    posZ + (Math.random() - 0.5) * spread,
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5,
                    (Math.random() - 0.5) * 0.5);
        }
    }

    /**
     * Flat horizontal ring of POOF particles expanding outward like a
     * ground-level sonic shockwave.
     */
    private static void spawnShockwaveRing(ClientWorld world,
                                           double cx, double cy, double cz,
                                           double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI / count) * i;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            world.addParticle(ParticleTypes.POOF,
                    cx + cos * radius, cy, cz + sin * radius,
                    cos * 0.22, 0.02, sin * 0.22);
        }
    }

    // ----------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------

    /** Returns an arbitrary unit vector perpendicular to {@code v}. */
    private static Vec3d perpendicular(Vec3d v) {
        Vec3d arbitrary = Math.abs(v.x) < 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        return v.crossProduct(arbitrary).normalize();
    }
}
