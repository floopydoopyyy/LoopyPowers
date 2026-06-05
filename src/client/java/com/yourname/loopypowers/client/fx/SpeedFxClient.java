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
            double oy = p.y() + 0.5;
            world.addParticle(WHITE_BRIGHT, p.x(), oy, p.z(), 0.3, 0.4, 0.3);
            world.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), oy, p.z(), 0.4, 0.2, 0.4);
        });
    }

    // ---- Primary: dash cast and trail ----

    public static void handleDashCast(SpeedDashCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = p.x(), y = p.y(), z = p.z();
            double lx = p.lookX(), ly = p.lookY(), lz = p.lookZ();

            for (int i = 0; i < 20; i++) {
                world.addParticle(WHITE_PALE, x, y, z,
                        (Math.random() - 0.5) * 0.8, Math.random() * 0.5, (Math.random() - 0.5) * 0.8);
            }
            for (int i = 0; i < 10; i++) {
                world.addParticle(WHITE_BRIGHT, x, y, z,
                        -lx * 0.5 + (Math.random() - 0.5) * 0.2,
                        -ly * 0.5 + Math.random() * 0.2,
                        -lz * 0.5 + (Math.random() - 0.5) * 0.2);
            }
        });
    }

    public static void handleDashTrail(SpeedDashTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d vel = new Vec3d(p.vx(), p.vy(), p.vz());
            if (vel.lengthSquared() < 0.001) return;
            Vec3d back = vel.normalize().negate();
            double py = p.y() + 1.0;

            for (int i = 0; i < 2; i++) {
                world.addParticle(WHITE_STREAK,
                        p.x() + (Math.random() - 0.5) * 0.3,
                        py  + (Math.random() - 0.5) * 0.3,
                        p.z() + (Math.random() - 0.5) * 0.3,
                        0, 0, 0);
            }
            if (Math.random() < 0.3) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        p.x() + (Math.random() - 0.5) * 0.5,
                        py  + (Math.random() - 0.5) * 0.5,
                        p.z() + (Math.random() - 0.5) * 0.5,
                        back.x * 0.2, back.y * 0.2, back.z * 0.2);
            }
            if (p.gameTime() % 2 == 0) {
                world.addParticle(ParticleTypes.SWEEP_ATTACK, p.x(), py, p.z(), 0, 0, 0);
            }
        });
    }

    // ---- Secondary: pinball anchor ring ----

    public static void handlePinballAnchor(SpeedPinballAnchorPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double radius = 10.0;
            int points = 36;
            for (int i = 0; i < points; i++) {
                double angle = 2 * Math.PI * i / points;
                double px = p.anchorX() + Math.cos(angle) * radius;
                double pz = p.anchorZ() + Math.sin(angle) * radius;
                world.addParticle(WHITE_PALE, px, p.anchorY() + 0.1, pz, 0, 0.02, 0);
                if (i % 4 == 0) {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, px, p.anchorY() + 0.2, pz, 0, 0.05, 0);
                }
            }
        });
    }

    // ---- Ultimate: overdrive cast ----

    public static void handleOverdriveCast(SpeedOverdriveCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = p.x(), oy = p.y() + 1.0, z = p.z();

            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy,     z, 1.5, 0.5, 1.5);
            world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy + 1, z, 1.5, 0.5, 1.5);

            for (int i = 0; i < 80; i++) {
                world.addParticle(WHITE_PALE,   x, oy, z, (Math.random()-0.5)*1.8, Math.random()*1.5, (Math.random()-0.5)*1.8);
            }
            for (int i = 0; i < 30; i++) {
                world.addParticle(WHITE_BRIGHT,  x, oy, z, (Math.random()-0.5)*1.5, Math.random()*1.0, (Math.random()-0.5)*1.5);
            }
            for (int i = 0; i < 40; i++) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, oy, z, (Math.random()-0.5)*2.5, Math.random()*2.0, (Math.random()-0.5)*2.5);
            }
            for (int i = 0; i < 24; i++) {
                world.addParticle(ParticleTypes.END_ROD, x, oy, z, (Math.random()-0.5)*2.5, (Math.random()-0.5)*2.5, (Math.random()-0.5)*2.5);
            }
            for (int i = 0; i < 4; i++) {
                world.addParticle(ParticleTypes.FLASH, x, oy + Math.random(), z, 0, 0, 0);
            }
        });
    }

    // ---- Ultimate tick: overdrive trail ----

    public static void handleOverdriveTrail(SpeedOverdriveTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d pos  = new Vec3d(p.x(), p.y() + 1.0, p.z());
            Vec3d vel  = new Vec3d(p.velX(), p.velY(), p.velZ());
            if (vel.lengthSquared() < 0.001) return;

            Vec3d back = vel.normalize().negate();
            Vec3d arbitrary = Math.abs(back.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
            Vec3d right = back.crossProduct(arbitrary).normalize();
            Vec3d up    = right.crossProduct(back).normalize();

            // Far behind: thick pale dust
            Vec3d far = pos.add(back.multiply(1.2));
            for (int i = 0; i < 8; i++) {
                world.addParticle(WHITE_STREAK,
                        far.x + (Math.random()-0.5)*0.8, far.y + (Math.random()-0.5)*0.8, far.z + (Math.random()-0.5)*0.8,
                        0, 0, 0);
            }

            // Closer: bright core
            Vec3d close = pos.add(back.multiply(0.5));
            for (int i = 0; i < 6; i++) {
                world.addParticle(WHITE_BRIGHT,
                        close.x + (Math.random()-0.5)*0.5, close.y + (Math.random()-0.5)*0.5, close.z + (Math.random()-0.5)*0.5,
                        0, 0, 0);
            }

            // Sparks
            for (int i = 0; i < 8; i++) {
                world.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x + (Math.random()-0.5)*1.5, pos.y + (Math.random()-0.5)*1.5, pos.z + (Math.random()-0.5)*1.5,
                        back.x * 0.4, back.y * 0.4, back.z * 0.4);
            }

            // Speed lines
            if (Math.random() < 0.4) {
                world.addParticle(ParticleTypes.END_ROD,
                        pos.x + (Math.random()-0.5)*1.2, pos.y + (Math.random()-0.5)*1.2, pos.z + (Math.random()-0.5)*1.2,
                        back.x * 0.8, back.y * 0.8, back.z * 0.8);
            }

            // Shockwave ring every 4 ticks
            if (p.gameTime() % 4 == 0) {
                int points = 32;
                double ringRadius = 1.2;
                for (int i = 0; i < points; i++) {
                    double angle = i * Math.PI * 2.0 / points;
                    Vec3d pt = pos.add(right.multiply(Math.cos(angle) * ringRadius))
                                  .add(up.multiply(Math.sin(angle) * ringRadius));
                    world.addParticle(WHITE_PALE, pt.x, pt.y, pt.z, back.x * 0.1, back.y * 0.1, back.z * 0.1);
                }
                for (int i = 0; i < 10; i++) {
                    double angle = Math.random() * Math.PI * 2.0;
                    Vec3d pt = pos.add(right.multiply(Math.cos(angle) * (ringRadius + 0.3)))
                                  .add(up.multiply(Math.sin(angle) * (ringRadius + 0.3)));
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, back.x * 0.2, back.y * 0.2, back.z * 0.2);
                }
            }
        });
    }

    // ---- Overdrive: collision explosions ----

    public static void handleExplosionFx(SpeedExplosionFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            double x = p.x(), oy = p.y() + 1.0, z = p.z();
            if (p.heavy()) {
                for (int i = 0; i < 4; i++) {
                    world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy, z, 0.6, 0.2, 0.6);
                }
                world.addParticle(ParticleTypes.FLASH, x, oy, z, 0, 0, 0);
                for (int i = 0; i < 15; i++) {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, oy, z,
                            (Math.random()-0.5)*1.5, Math.random()*1.0, (Math.random()-0.5)*1.5);
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    world.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, oy, z, 0.15, 0.10, 0.15);
                }
                for (int i = 0; i < 8; i++) {
                    world.addParticle(ParticleTypes.ELECTRIC_SPARK, x, oy, z,
                            (Math.random()-0.5)*0.8, Math.random()*0.5, (Math.random()-0.5)*0.8);
                }
            }
        });
    }

    // ---- Util ----

    @SuppressWarnings("unused")
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
