package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public final class SpeedFxClient {

    private SpeedFxClient() {}

    private static final DustParticleEffect WHITE_BRIGHT  = new DustParticleEffect(new Vector3f(1.00f, 1.00f, 1.00f), 1.3f);
    private static final DustParticleEffect WHITE_PALE    = new DustParticleEffect(new Vector3f(0.85f, 0.85f, 0.85f), 1.0f);
    private static final DustParticleEffect WHITE_STREAK  = new DustParticleEffect(new Vector3f(0.95f, 0.95f, 0.95f), 0.8f);

    // ---- Passive: low-health burst ----

    public static void handleLowHealthBurst(SpeedLowHealthBurstPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, WHITE_BRIGHT, p.x(), p.y(), p.z(), 8, 0.3, 0.4, 0.3, 0.06);
            scatter(world, ParticleTypes.SWEEP_ATTACK, p.x(), p.y(), p.z(), 3, 0.4, 0.2, 0.4, 0);
        });
    }

    // ---- Secondary tick: rush cloud trail ----

    public static void handleRushTrail(SpeedRushTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD, p.x(), p.y(), p.z(), 3, 0.15, 0.05, 0.15, 0.005);
        });
    }

    // ---- Primary: dash ----

    public static void handleDash(SpeedDashPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double ox = p.x(), oy = p.y() + 0.8, oz = p.z();
            double lx = p.lookX(), ly = p.lookY(), lz = p.lookZ();

            // central burst
            scatter(world, WHITE_BRIGHT, ox, oy, oz, 12, 0.3, 0.25, 0.3, 0.06);

            // trail streaks behind the look direction
            for (int i = 1; i <= 4; i++) {
                double d = i * 0.70;
                world.addParticle((i % 2 == 0) ? WHITE_BRIGHT : WHITE_STREAK,
                        ox - lx * d, oy - ly * d * 0.5, oz - lz * d, 0, 0, 0);
            }

            // ring
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI * 2.0 / 6;
                world.addParticle(WHITE_STREAK,
                        ox + Math.cos(angle) * 0.4, oy, oz + Math.sin(angle) * 0.4, 0, 0, 0);
            }

            scatter(world, ParticleTypes.SWEEP_ATTACK, ox, p.y() + 0.3, oz, 3, 0.5, 0.15, 0.5, 0);
        });
    }

    // ---- Secondary: rush cast ----

    public static void handleRushCast(SpeedRushCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double ox = p.x(), oy = p.y() + 1.0, oz = p.z();

            // outward ring at feet
            for (int i = 0; i < 12; i++) {
                double angle = i * Math.PI * 2.0 / 12;
                world.addParticle(WHITE_BRIGHT,
                        ox + Math.cos(angle) * 0.5, p.y() + 0.1, oz + Math.sin(angle) * 0.5,
                        Math.cos(angle) * 0.22, 0.01, Math.sin(angle) * 0.22);
            }

            scatter(world, WHITE_BRIGHT, ox, oy, oz, 10, 0.5, 0.5, 0.5, 0.10);
            scatter(world, WHITE_PALE,   ox, oy, oz,  6, 0.6, 0.6, 0.6, 0.08);
            scatter(world, ParticleTypes.EXPLOSION_EMITTER, ox, oy, oz, 3, 0.6, 0.2, 0.6, 0.1);
            scatter(world, ParticleTypes.SWEEP_ATTACK, ox, p.y() + 0.5, oz, 4, 0.6, 0.25, 0.6, 0);
        });
    }

    // ---- Ultimate: overdrive cast ----

    public static void handleOverdriveCast(SpeedOverdriveCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double ox = p.x(), oy = p.y() + 1.0, oz = p.z();

            // upward sphere burst
            for (int d = 0; d < 12; d++) {
                double theta = d * Math.PI * 2.0 / 12;
                double phi   = Math.PI / 4;
                double speed = 0.20;
                world.addParticle(WHITE_BRIGHT, ox, oy, oz,
                        Math.cos(theta) * Math.cos(phi) * speed,
                        Math.sin(phi) * speed,
                        Math.sin(theta) * Math.cos(phi) * speed);
            }

            // ring at feet
            for (int i = 0; i < 10; i++) {
                double angle = i * Math.PI * 2.0 / 10;
                world.addParticle(WHITE_STREAK,
                        ox + Math.cos(angle) * 0.4, p.y() + 0.1, oz + Math.sin(angle) * 0.4,
                        Math.cos(angle) * 0.28, 0.01, Math.sin(angle) * 0.28);
            }

            scatter(world, ParticleTypes.FIREWORK,      ox, oy, oz, 8, 0.5, 0.6, 0.5, 0.08);
            scatter(world, ParticleTypes.FLASH,         ox, oy, oz, 1, 0.4, 0.2, 0.4, 0);
            scatter(world, ParticleTypes.SWEEP_ATTACK,  ox, oy, oz, 5, 0.7, 0.3, 0.7, 0);
        });
    }

    // ---- Ultimate tick: overdrive trail ----

    public static void handleOverdriveTrail(SpeedOverdriveTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double ox = p.x(), oy = p.y(), oz = p.z();
            Vec3d vel  = new Vec3d(p.velX(), p.velY(), p.velZ());
            double len = vel.length();
            if (len < 1.0e-5) return;

            Vec3d back = vel.normalize().negate();

            // streaks behind player
            for (int i = 0; i < 3; i++) {
                double d = 0.5 + i * 1.1;
                world.addParticle(
                        (i % 2 == 0) ? WHITE_BRIGHT : WHITE_PALE,
                        ox + back.x * d, oy + back.y * d * 0.3, oz + back.z * d,
                        0, 0, 0);
            }

            scatter(world, ParticleTypes.CLOUD,   ox + back.x * 1.5, oy - 0.3, oz + back.z * 1.5, 3, 0.25, 0.20, 0.25, 0.04);
            scatter(world, ParticleTypes.SMOKE,   ox + back.x,       oy - 0.1, oz + back.z,        2, 0.15, 0.15, 0.15, 0.03);
            scatter(world, ParticleTypes.END_ROD, ox, oy, oz, 4, 0.3, 0.6, 0.3, 0.06);
            scatter(world, ParticleTypes.FIREWORK, ox, oy, oz, 2, 0.35, 0.55, 0.25, 0.05);

            if (p.showRing()) {
                Vec3d right = new Vec3d(-vel.z, 0, vel.x).normalize();
                for (int i = 0; i < 4; i++) {
                    double angle = i * Math.PI * 2.0 / 4;
                    double rx = right.x * Math.cos(angle) * 0.6;
                    double rz = right.z * Math.cos(angle) * 0.6;
                    world.addParticle(WHITE_STREAK, ox + rx, oy, oz + rz, 0, 0, 0);
                }
            }
        });
    }

    // ---- Overdrive entity hit ----

    public static void handleOverdriveHit(SpeedOverdriveHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y(), p.z(), 4, 0.6, 0.2, 0.6, 0.1);
        });
    }

    // ---- Overdrive block collision ----

    public static void handleBlockImpact(SpeedBlockImpactPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.EXPLOSION_EMITTER, p.x(), p.y(), p.z(), 3, 0.15, 0.10, 0.15, 0.02);
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
