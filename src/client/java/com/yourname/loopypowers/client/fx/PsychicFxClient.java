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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Random;

@Environment(EnvType.CLIENT)
public final class PsychicFxClient {

    private PsychicFxClient() {}

    // Leech (passive)
    private static final DustParticleEffect LEECH_DUST_MAIN  = new DustParticleEffect(new Vector3f(0.80f, 0.00f, 0.90f), 0.8f);
    private static final DustParticleEffect LEECH_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.40f, 0.90f), 0.5f);
    private static final DustParticleEffect LEECH_DUST_END   = new DustParticleEffect(new Vector3f(1.00f, 0.20f, 0.80f), 1.0f);

    // Compel (primary)
    private static final DustParticleEffect COMPEL_DUST_MAIN  = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.7f);
    private static final DustParticleEffect COMPEL_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.5f);

    // Spike (secondary)
    private static final DustParticleEffect SPIKE_DUST_IMPACT_DARK  = new DustParticleEffect(new Vector3f(0.50f, 0.00f, 0.60f), 1.0f);
    private static final DustParticleEffect SPIKE_DUST_IMPACT_LIGHT = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.8f);
    private static final DustParticleEffect SPIKE_DUST_AURA_MAIN    = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleEffect SPIKE_DUST_AURA_LIGHT   = new DustParticleEffect(new Vector3f(0.50f, 0.00f, 0.60f), 0.5f);
    private static final DustParticleEffect SPIKE_BEAM_MAIN         = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleEffect SPIKE_BEAM_LIGHT        = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.4f);

    // Ultimate
    private static final DustParticleEffect ULT_DUST_MAIN  = new DustParticleEffect(new Vector3f(1.00f, 0.20f, 0.80f), 0.9f);
    private static final DustParticleEffect ULT_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.6f);
    private static final DustParticleEffect ULT_DUST_DARK  = new DustParticleEffect(new Vector3f(0.70f, 0.00f, 1.00f), 1.1f);

    // ---- Passive: leech drain line ----

    public static void handleLeech(PsychicLeechPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity ae = world.getEntityById(p.attackerId());
            Entity te = world.getEntityById(p.targetId());
            if (!(ae instanceof LivingEntity attacker) || !(te instanceof LivingEntity target)) return;

            Vec3d from    = new Vec3d(target.getX(),   target.getBodyY(0.7),   target.getZ());
            Vec3d to      = new Vec3d(attacker.getX(), attacker.getBodyY(0.7), attacker.getZ());
            Vec3d step    = to.subtract(from).multiply(1.0 / 10);
            Vec3d current = from;

            for (int i = 0; i < 10; i++) {
                double curve = Math.sin(i / 10.0 * Math.PI) * 0.15;
                scatter(world, LEECH_DUST_MAIN,  current.x, current.y - curve, current.z, 2, 0.05, 0.05, 0.05, 0.0);
                scatter(world, LEECH_DUST_LIGHT, current.x, current.y - curve, current.z, 1, 0.02, 0.02, 0.02, 0.0);
                current = current.add(step);
            }
            scatter(world, LEECH_DUST_END, attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        });
    }

    // ---- Primary: compel per-tick aura ----

    public static void handleCompelAura(PsychicCompelAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, COMPEL_DUST_MAIN,  le.getX(), le.getBodyY(0.6), le.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
            scatter(world, COMPEL_DUST_LIGHT, le.getX(), le.getBodyY(0.6), le.getZ(), 1, 0.3, 0.4, 0.3, 0.005);
        });
    }

    // ---- Secondary: jagged spike beam ----

    public static void handleSpikeBeam(PsychicSpikeBeamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d from = new Vec3d(p.fromX(), p.fromY(), p.fromZ());
            Vec3d to   = new Vec3d(p.toX(), p.toY(), p.toZ());
            int segments = p.segments();
            double offset = p.offset();

            Vec3d step    = to.subtract(from).multiply(1.0 / segments);
            Vec3d beamDir = to.subtract(from).normalize();
            Vec3d perp    = Math.abs(beamDir.y) < 0.9
                    ? new Vec3d(-beamDir.z, 0, beamDir.x).normalize()
                    : new Vec3d(1, 0, 0);
            Vec3d perp2 = beamDir.crossProduct(perp).normalize();

            Vec3d current = from;
            Random rng = new Random(from.hashCode());

            for (int i = 0; i < segments; i++) {
                Vec3d next = current.add(step);

                double jag  = (i % 2 == 0 ? 1 : -1) * offset * (0.5 + rng.nextDouble() * 0.5);
                double jag2 = (i % 3 == 0 ? 1 : -1) * offset * 0.4 * rng.nextDouble();
                Vec3d jaggedNext = next.add(perp.multiply(jag)).add(perp2.multiply(jag2));

                for (int j = 0; j <= 4; j++) {
                    Vec3d pos = current.lerp(jaggedNext, j / 4.0);
                    world.addParticle(SPIKE_BEAM_MAIN,  pos.x, pos.y, pos.z, 0, 0, 0);
                    world.addParticle(SPIKE_BEAM_LIGHT, pos.x, pos.y, pos.z, 0, 0, 0);
                }

                current = jaggedNext;
            }
        });
    }

    // ---- Secondary: spike impact burst ----

    public static void handleSpikeImpact(PsychicSpikeImpactPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, SPIKE_DUST_IMPACT_DARK,  le.getX(), le.getBodyY(0.5), le.getZ(), 20, 0.4, 0.5, 0.4, 0.03);
            scatter(world, SPIKE_DUST_IMPACT_LIGHT, le.getX(), le.getBodyY(0.5), le.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        });
    }

    // ---- Secondary: spike per-tick aura ----

    public static void handleSpikeAura(PsychicSpikeAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, SPIKE_DUST_AURA_MAIN,  le.getX(), le.getBodyY(0.7), le.getZ(), 2, 0.25, 0.30, 0.25, 0.01);
            scatter(world, SPIKE_DUST_AURA_LIGHT, le.getX(), le.getBodyY(0.4), le.getZ(), 1, 0.20, 0.20, 0.20, 0.005);
        });
    }

    // ---- Ultimate: possessed per-tick aura ----

    public static void handleControlAura(PsychicControlAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            scatter(world, ULT_DUST_MAIN,  le.getX(), le.getBodyY(0.7), le.getZ(), 4, 0.35, 0.45, 0.35, 0.02);
            scatter(world, ULT_DUST_LIGHT, le.getX(), le.getBodyY(0.5), le.getZ(), 2, 0.30, 0.30, 0.30, 0.01);
            scatter(world, ULT_DUST_DARK,  le.getX(), le.getBodyY(0.6), le.getZ(), 3, 0.30, 0.40, 0.30, 0.02);
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
