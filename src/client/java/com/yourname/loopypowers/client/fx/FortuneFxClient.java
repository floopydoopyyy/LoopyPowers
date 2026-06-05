package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class FortuneFxClient {

    private FortuneFxClient() {}

    // ---- Passive ----

    public static void handleProcEnemy(FortuneProcEnemyPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.60, z = le.getZ();
            scatter(world, ParticleTypes.ENCHANT, x, y, z, 14, 0.25, 0.30, 0.25, 0.0);
            scatter(world, ParticleTypes.CRIT,    x, le.getY() + le.getHeight() * 0.55, z, 10, 0.20, 0.20, 0.20, 0.04);
        });
    }

    public static void handleProcSelf(FortuneProcSelfPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + 1.0, z = le.getZ();
            scatter(world, ParticleTypes.ENCHANT, x, y, z, 18, 0.35, 0.45, 0.35, 0.0);
            world.addParticle(ParticleTypes.FIREWORK, x, y, z, 0.0, 0.0, 0.0);
        });
    }

    // ---- Primary ----

    public static void handleAllIn(FortuneAllInPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.ENCHANT, p.x(), p.y() + 1.0, p.z(), 18, 0.35, 0.45, 0.35, 0.0);
        });
    }

    public static void handleJackpot(FortuneJackpotPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y() + 1.0, z = p.z();
            world.addParticle(ParticleTypes.FIREWORK, x, y, z, 0.0, 0.0, 0.0);
            scatter(world, ParticleTypes.ENCHANT, x, y + 0.1, z, 60, 0.65, 0.55, 0.65, 0.0);
        });
    }

    // ---- Secondary (duel) ----

    public static void handleDuelBeam(FortuneDuelBeamPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d start = new Vec3d(p.startX(), p.startY(), p.startZ());
            Vec3d end   = new Vec3d(p.endX(),   p.endY(),   p.endZ());
            Vec3d delta = end.subtract(start);
            double len = delta.length();
            if (len < 0.01) return;

            Vec3d dir = delta.multiply(1.0 / len);
            int steps = MathHelper.clamp((int)(len / 0.35), 8, 120);

            Vec3d pos = start;
            for (int i = 0; i <= steps; i++) {
                scatter(world, ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.0);
                if ((i & 3) == 0) {
                    scatter(world, ParticleTypes.CRIT, pos.x, pos.y, pos.z, 1, 0.02, 0.02, 0.02, 0.02);
                }
                pos = pos.add(dir.multiply(len / steps));
            }
        });
    }

    public static void handleDuelTether(FortuneDuelTetherPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Entity ea = world.getEntityById(p.entityIdA());
            Entity eb = world.getEntityById(p.entityIdB());
            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b)) return;

            Vec3d start = a.getPos().add(0, a.getHeight() * 0.65, 0);
            Vec3d end   = b.getPos().add(0, b.getHeight() * 0.65, 0);
            Vec3d delta = end.subtract(start);
            double len = delta.length();
            if (len < 0.01) return;

            Vec3d dir = delta.multiply(1.0 / len);
            int steps = MathHelper.clamp((int)(len / 0.45), 10, 90);

            Vec3d pos = start;
            for (int i = 0; i <= steps; i++) {
                scatter(world, ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0.0);
                pos = pos.add(dir.multiply(len / steps));
            }
            scatter(world, ParticleTypes.ENCHANT, start.x, start.y, start.z, 3, 0.15, 0.15, 0.15, 0.0);
            scatter(world, ParticleTypes.ENCHANT, end.x,   end.y,   end.z,   3, 0.15, 0.15, 0.15, 0.0);
        });
    }

    public static void handleDuelAura(FortuneDuelAuraPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.65, z = le.getZ();
            scatter(world, ParticleTypes.ENCHANT, x, y, z, 2, 0.18, 0.25, 0.18, 0.0);
        });
    }

    // ---- Ultimate (house) ----

    public static void handleUltCast(FortuneUltCastPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.ENCHANT, p.x() + 0.5, p.y() + 1.2, p.z() + 0.5, 35, 0.9, 0.4, 0.9, 0.0);
        });
    }

    public static void handleHouseBuilt(FortuneHouseBuiltPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.POOF, p.x() + 0.5, p.y() + 0.2, p.z() + 0.5, 16, 0.6, 0.2, 0.6, 0.04);
        });
    }

    public static void handleHouseRoof(FortuneHouseRoofPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            for (int i = 0; i < 28; i++) {
                double x = p.centerX() + (world.random.nextDouble() * 2 - 1) * p.radius();
                double z = p.centerZ() + (world.random.nextDouble() * 2 - 1) * p.radius();
                world.addParticle(ParticleTypes.ENCHANT, x, p.y(), z, 0.0, 0.0, 0.0);
            }
        });
    }

    public static void handleHouseDestroy(FortuneHouseDestroyPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.CLOUD, p.x() + 0.5, p.y() + 1.0, p.z() + 0.5, 40, 0.9, 0.45, 0.9, 0.05);
        });
    }

    public static void handleCenterFx(FortuneCenterFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x() + 0.5, y = p.y(), z = p.z() + 0.5;
            switch (p.type()) {
                case 0 -> scatter(world, ParticleTypes.ENCHANT,        x, y + 1.2, z, 18, 0.9,  0.35, 0.9,  0.0);  // DOUBLE_OR_NOTHING
                case 1 -> scatter(world, ParticleTypes.LARGE_SMOKE,    x, y + 1.2, z, 25, 1.2,  0.6,  1.2,  0.02); // SMOKE_MACHINE_START
                case 2 -> scatter(world, ParticleTypes.ENCHANT,        x, y + 1.2, z, 18, 1.0,  0.35, 1.0,  0.0);  // CHIP_TOSS_CENTER
                case 3 -> scatter(world, ParticleTypes.ENCHANT,        x, y + 1.2, z, 22, 0.8,  0.35, 0.8,  0.0);  // JACKPOT_ARM
                case 4 -> scatter(world, ParticleTypes.ELECTRIC_SPARK, x, y + 1.2, z, 30, 1.0,  0.5,  1.0,  0.1);  // BOUNCER
            }
        });
    }

    public static void handleEntityFx(FortuneEntityFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), z = le.getZ();
            double h = le.getHeight();
            switch (p.type()) {
                case 0  -> scatter(world, ParticleTypes.ENCHANT,       x, le.getY() + h * 0.6, z, 8,  0.25, 0.25, 0.25, 0.0);  // INSIDE_JOIN
                case 1  -> scatter(world, ParticleTypes.ENCHANT,       x, le.getY() + h * 0.7, z, 18, 0.35, 0.35, 0.35, 0.0);  // HOT_SEAT_START
                case 2  -> {                                                                                                      // HOT_SEAT_TICK
                    scatter(world, ParticleTypes.ENCHANT, x, le.getY() + h * 0.6, z, 10, 0.25, 0.35, 0.25, 0.0);
                    scatter(world, ParticleTypes.CRIT,    x, le.getY() + h * 0.6, z,  2, 0.15, 0.15, 0.15, 0.02);
                }
                case 3  -> scatter(world, ParticleTypes.CLOUD,         x, le.getY() + h * 0.5, z, 18, 0.35, 0.25, 0.35, 0.03); // HOT_SEAT_DETONATE
                case 4  -> scatter(world, ParticleTypes.CRIT,          x, le.getY() + h * 0.6, z, 16, 0.35, 0.35, 0.35, 0.08); // ROULETTE_PICK
                case 5  -> scatter(world, ParticleTypes.ENCHANT,       x, le.getY() + h * 0.7, z, 18, 0.35, 0.35, 0.35, 0.0);  // SPOTLIGHT_START
                case 6  -> scatter(world, ParticleTypes.CRIT,          x, le.getY() + h * 0.8, z,  2, 0.25, 0.25, 0.25, 0.02); // SPOTLIGHT_TICK
                case 7  -> scatter(world, ParticleTypes.ELECTRIC_SPARK,x, le.getY() + 1.0,     z, 10, 0.2,  0.4,  0.2,  0.05); // PRISON_PUSH
                case 8  -> scatter(world, ParticleTypes.ENCHANTED_HIT, x, le.getY() + 1.0,     z, 30, 0.5,  0.5,  0.5,  0.05); // PRISON_YANK
                case 9  -> scatter(world, ParticleTypes.ENCHANT,       x, le.getY() + 1.0,     z, 22, 0.45, 0.55, 0.45, 0.0);  // CARD_COUNTER
                case 10 -> scatter(world, ParticleTypes.SMOKE,         x, le.getY() + h * 0.6, z,  8, 0.45, 0.35, 0.45, 0.01); // SMOKE_MACHINE
                case 11 -> scatter(world, ParticleTypes.CRIT,           x, le.getY() + h * 0.5, z, 10, 0.25, 0.35, 0.25, 0.10); // CHIP_TOSS_ENTITY
                case 12 -> scatter(world, ParticleTypes.ELECTRIC_SPARK, x, le.getY() + h * 0.6, z,  6, 0.25, 0.25, 0.25, 0.0);  // LIGHTNING_TICK
                case 13 -> scatter(world, ParticleTypes.ELECTRIC_SPARK, x, le.getY() + 1.0,     z, 25, 0.2,  0.4,  0.2,  0.15); // BOUNCER_PUSH
            }
        });
    }

    public static void handleHotSeatPass(FortuneHotSeatPassPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity ef = world.getEntityById(p.fromId());
            Entity et = world.getEntityById(p.toId());
            if (ef instanceof LivingEntity from) {
                scatter(world, ParticleTypes.ENCHANT, from.getX(), from.getY() + from.getHeight() * 0.6, from.getZ(), 10, 0.25, 0.25, 0.25, 0.0);
            }
            if (et instanceof LivingEntity to) {
                scatter(world, ParticleTypes.ENCHANT, to.getX(), to.getY() + to.getHeight() * 0.6, to.getZ(), 14, 0.25, 0.25, 0.25, 0.0);
            }
        });
    }

    public static void handlePoofAt(FortunePoofAtPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.POOF, p.x(), p.y(), p.z(), p.count(), 0.25, 0.25, 0.25, 0.02);
        });
    }

    public static void handleJackpotHit(FortuneJackpotHitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            Entity e = world.getEntityById(p.entityId());
            if (!(e instanceof LivingEntity le)) return;
            double x = le.getX(), y = le.getY() + le.getHeight() * 0.6, z = le.getZ();
            world.addParticle(ParticleTypes.FIREWORK, x, y, z, 0.0, 0.0, 0.0);
            scatter(world, ParticleTypes.CRIT, x, y, z, 18, 0.35, 0.35, 0.35, 0.12);
        });
    }

    public static void handleCeilingBreak(FortuneCeilingBreakPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            scatter(world, ParticleTypes.ENCHANT, p.x(), p.y(), p.z(), 10, 0.25, 0.25, 0.25, 0.0);
        });
    }

    private static <T extends net.minecraft.particle.ParticleEffect> void scatter(
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
