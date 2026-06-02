package com.yourname.loopypowers.client.fx;

import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Environment(EnvType.CLIENT)
public final class DimensionalFxClient {

    private DimensionalFxClient() {}

    // ── Colour palette ────────────────────────────────────────────
    private static final Vector3f COL_DARK_BLUE    = new Vector3f(0.05f, 0.10f, 0.40f);
    private static final Vector3f COL_MID_BLUE     = new Vector3f(0.20f, 0.40f, 1.00f);
    private static final Vector3f COL_BRIGHT_BLUE  = new Vector3f(0.60f, 0.80f, 1.00f);
    private static final Vector3f COL_CRACK_DARK   = new Vector3f(0.05f, 0.15f, 0.55f);
    private static final Vector3f COL_CRACK_MID    = new Vector3f(0.25f, 0.55f, 1.00f);
    private static final Vector3f COL_CRACK_BRIGHT = new Vector3f(0.75f, 0.92f, 1.00f);
    private static final Vector3f COL_RIFT_WHITE   = new Vector3f(0.90f, 0.97f, 1.00f);

    private static final DustParticleEffect CRACK_DARK   = new DustParticleEffect(COL_CRACK_DARK,   1.8f);
    private static final DustParticleEffect CRACK_MID    = new DustParticleEffect(COL_CRACK_MID,    1.4f);
    private static final DustParticleEffect CRACK_BRIGHT = new DustParticleEffect(COL_CRACK_BRIGHT, 1.1f);
    private static final DustParticleEffect RIFT_WHITE   = new DustParticleEffect(COL_RIFT_WHITE,   1.2f);

    // ── Rift geometry constants (must mirror server values) ───────
    private static final int    ULT_CRACK_COUNT          = 15;
    private static final int    ULT_CRACK_SEGMENTS       = 8;
    private static final double ULT_CRACK_SEGMENT_LENGTH = 2.3;
    private static final double ULT_CRACK_JAGGED_ANGLE   = 0.6;
    private static final double ULT_RIFT_RADIUS          = 26.0;
    private static final int    ULT_RING_COUNT           = 2;
    private static final double ULT_RING_SPACING         = 9.0;
    private static final int    ULT_RING_POINTS          = 20;
    private static final double ULT_WALL_PARTICLE_HEIGHT = 3.0;
    private static final int    ULT_WALL_PARTICLE_DENSITY = 1;
    private static final double MAX_STEP_UP  = 1.25;
    private static final double MAX_STEP_DOWN = 2.5;
    private static final int    SEARCH_DOWN  = 6;
    private static final int    SEARCH_UP    = 2;

    // ── Stored rift geometry, keyed by caster network entity ID ──
    private static final Map<Integer, ClientRiftState> ACTIVE_RIFTS = new HashMap<>();

    private static final class ClientRiftState {
        List<List<Vec3d>> cracks;
        List<List<Vec3d>> rings;
        Vec3d origin;
    }

    // ----------------------------------------------------------------
    // HANDLERS
    // ----------------------------------------------------------------

    public static void handleFlicker(DimensionalFlickerPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            DustParticleEffect brightBlue = new DustParticleEffect(COL_BRIGHT_BLUE, 0.9f);
            DustParticleEffect midBlue    = new DustParticleEffect(COL_MID_BLUE,    1.2f);
            DustParticleEffect darkBlue   = new DustParticleEffect(COL_DARK_BLUE,   1.4f);

            scatter(world, brightBlue, x, y, z, 12, 0.5, 0.7, 0.5, 0.03);
            scatter(world, midBlue,    x, y, z, 8,  0.35, 0.55, 0.35, 0.02);
            if (world.random.nextFloat() < 0.4f)
                scatter(world, darkBlue, x, y, z, 3, 0.3, 0.4, 0.3, 0.015);
        });
    }

    public static void handleSituationalFx(DimensionalSituationalFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            switch (p.type()) {
                case 0 -> { // fall / void
                    scatter(world, ParticleTypes.SQUID_INK, x, y, z, 30, 0.4, 0.6, 0.4, 0.1);
                    scatter(world, ParticleTypes.POOF,      x, y, z, 20, 0.4, 0.6, 0.4, 0.05);
                }
                case 1 -> { // drown
                    scatter(world, ParticleTypes.BUBBLE_POP, x, y, z, 40, 0.4, 0.4, 0.4, 0.1);
                    scatter(world, ParticleTypes.CLOUD,      x, y, z, 20, 0.4, 0.4, 0.4, 0.05);
                }
                case 2 -> { // freeze
                    scatter(world, ParticleTypes.LAVA,               x, y, z, 15, 0.4, 0.6, 0.4, 0.1);
                    scatter(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 30, 0.4, 0.6, 0.4, 0.05);
                }
                case 3 -> // levitate
                    scatter(world, ParticleTypes.ASH, x, y, z, 40, 0.4, 0.6, 0.4, 0.1);
                case 4 -> // blind
                    scatter(world, ParticleTypes.FLASH, x, y, z, 2, 0.1, 0.1, 0.1, 0.0);
                case 5 -> { // poison/wither
                    scatter(world, ParticleTypes.GLOW,   x, y, z, 40, 0.4, 0.6, 0.4, 0.1);
                    scatter(world, ParticleTypes.SCRAPE,  x, y, z, 20, 0.4, 0.6, 0.4, 0.1);
                }
                case 6 -> // hunger
                    scatter(world, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 15, 0.2, 0.2, 0.2, 0.05);
                case 7 -> { // fire / water
                    scatter(world, ParticleTypes.SPLASH,       x, y, z, 50, 0.4, 0.6, 0.4, 0.1);
                    scatter(world, ParticleTypes.FALLING_WATER, x, y, z, 30, 0.4, 0.6, 0.4, 0.1);
                }
                case 8 -> // slowness → speed boost
                    scatter(world, ParticleTypes.SOUL, x, y, z, 25, 0.3, 0.1, 0.3, 0.1);
                case 9 -> // easter egg: lava (strider)
                    scatter(world, ParticleTypes.LAVA,  x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
                case 10 -> // easter egg: poof (cat/pig/sheep)
                    scatter(world, ParticleTypes.POOF,  x, y, z, 5, 0.2, 0.2, 0.2, 0.02);
                case 11 -> // easter egg: splash (cod)
                    scatter(world, ParticleTypes.SPLASH, x, y, z, 15, 0.3, 0.3, 0.3, 0.05);
            }
        });
    }

    public static void handlePhaseEnter(DimensionalPhaseEnterPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            DustParticleEffect brightBlue = new DustParticleEffect(COL_BRIGHT_BLUE, 1.0f);
            DustParticleEffect midBlue    = new DustParticleEffect(COL_MID_BLUE,    1.2f);
            DustParticleEffect darkBlue   = new DustParticleEffect(COL_DARK_BLUE,   1.4f);

            scatter(world, brightBlue, x, y, z, 15, 0.6, 0.8, 0.6, 0.04);
            scatter(world, midBlue,    x, y, z, 10, 0.4, 0.6, 0.4, 0.03);
            if (world.random.nextFloat() < 0.5f)
                scatter(world, darkBlue, x, y, z, 4, 0.3, 0.4, 0.3, 0.02);
        });
    }

    public static void handlePhaseTrail(DimensionalPhaseTrailPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            DustParticleEffect midBlue = new DustParticleEffect(COL_MID_BLUE, 1.0f);
            scatter(world, midBlue, p.x(), p.y(), p.z(), 2, 0.15, 0.2, 0.15, 0.01);
        });
    }

    public static void handlePhaseExit(DimensionalPhaseExitPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            DustParticleEffect brightBlue = new DustParticleEffect(COL_BRIGHT_BLUE, 1.1f);
            DustParticleEffect midBlue    = new DustParticleEffect(COL_MID_BLUE,    1.2f);
            DustParticleEffect darkBlue   = new DustParticleEffect(COL_DARK_BLUE,   1.4f);

            scatter(world, brightBlue, x, y, z, 50, 1.2, 1.2, 1.2, 0.08);
            scatter(world, midBlue,    x, y, z, 36, 0.96, 0.96, 0.96, 0.064);
            scatter(world, darkBlue,   x, y, z, 16, 0.72, 0.72, 0.72, 0.048);
        });
    }

    public static void handleDisplaceFx(DimensionalDisplaceFxPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;
            double x = p.x(), y = p.y(), z = p.z();
            DustParticleEffect brightBlue = new DustParticleEffect(COL_BRIGHT_BLUE, 0.9f);
            DustParticleEffect midBlue    = new DustParticleEffect(COL_MID_BLUE,    1.2f);
            DustParticleEffect darkBlue   = new DustParticleEffect(COL_DARK_BLUE,   1.4f);

            scatter(world, brightBlue, x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
            scatter(world, midBlue,    x, y, z, 4, 0.35, 0.55, 0.35, 0.02);
            if (world.random.nextFloat() < 0.4f)
                scatter(world, darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        });
    }

    public static void handleRiftOpen(DimensionalRiftOpenPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            Vec3d origin = new Vec3d(p.ox(), p.oy(), p.oz());
            Random rng = new Random(p.rngSeed());

            ClientRiftState state = new ClientRiftState();
            state.origin = origin;
            state.cracks = new ArrayList<>();
            state.rings  = new ArrayList<>();

            double angleStep = (Math.PI * 2.0) / ULT_CRACK_COUNT;
            for (int i = 0; i < ULT_CRACK_COUNT; i++) {
                double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
                state.cracks.add(buildCrackLine(origin, baseAngle, rng, world));
            }
            for (int i = 1; i <= ULT_RING_COUNT; i++) {
                state.rings.add(buildRing(origin, i * ULT_RING_SPACING, world));
            }

            ACTIVE_RIFTS.put(p.casterId(), state);

            spawnRiftOpeningParticles(world, origin);
        });
    }

    public static void handleRiftTick(DimensionalRiftTickPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            ClientWorld world = ctx.client().world;
            if (world == null) return;

            ClientRiftState state = ACTIVE_RIFTS.get(p.casterId());
            if (state == null) return;

            for (List<Vec3d> crack : state.cracks) spawnCrackWallParticles(world, crack);
            for (List<Vec3d> ring  : state.rings)  spawnCrackWallParticles(world, ring);
            spawnRiftAuraParticles(world, state.origin, world.getTime());
        });
    }

    public static void handleRiftEnd(DimensionalRiftEndPayload p, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> ACTIVE_RIFTS.remove(p.casterId()));
    }

    // ----------------------------------------------------------------
    // RIFT GEOMETRY (mirrors server-side logic, uses ClientWorld)
    // ----------------------------------------------------------------

    private static List<Vec3d> buildRing(Vec3d center, double radius, ClientWorld world) {
        List<Vec3d> points = new ArrayList<>();
        int smooth = ULT_RING_POINTS * 3;
        for (int i = 0; i < smooth; i++) {
            double angle = (Math.PI * 2.0 * i) / smooth;
            Vec3d p = new Vec3d(center.x + Math.cos(angle) * radius, center.y,
                                center.z + Math.sin(angle) * radius);
            points.add(snapToGround(p, world));
        }
        return points;
    }

    private static List<Vec3d> buildCrackLine(Vec3d origin, double baseAngle,
                                              Random rng, ClientWorld world) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d current = origin;
        points.add(snapToGround(current, world));

        double currentAngle = baseAngle;
        for (int seg = 0; seg < ULT_CRACK_SEGMENTS; seg++) {
            currentAngle += (rng.nextDouble() - 0.5) * ULT_CRACK_JAGGED_ANGLE;
            double segLen = ULT_CRACK_SEGMENT_LENGTH * (0.7 + rng.nextDouble() * 0.6);
            Vec3d nextFlat = current.add(Math.cos(currentAngle) * segLen, 0,
                                         Math.sin(currentAngle) * segLen);
            int steps = 3 + rng.nextInt(3);
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3d interp = new Vec3d(current.x + (nextFlat.x - current.x) * t, current.y,
                                         current.z + (nextFlat.z - current.z) * t);
                points.add(snapToGround(interp, world));
            }
            current = nextFlat;
            if (current.distanceTo(origin) > ULT_RIFT_RADIUS) break;
        }
        return points;
    }

    private static Vec3d snapToGround(Vec3d pos, ClientWorld world) {
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int baseY = (int) Math.floor(pos.y);

        int bestY = baseY;
        boolean found = false;

        for (int dy = 0; dy <= SEARCH_DOWN; dy++) {
            int y = baseY - dy;
            if (isSolid(world, x, y, z) && isAir(world, x, y, z)) {
                bestY = y + 1; found = true; break;
            }
        }
        if (!found) {
            for (int dy = 1; dy <= SEARCH_UP; dy++) {
                int y = baseY + dy;
                if (isSolid(world, x, y, z) && isAir(world, x, y, z)) {
                    if ((y - baseY) <= MAX_STEP_UP) { bestY = y + 1; found = true; }
                    break;
                }
            }
        }
        if (!found) return pos;

        double newY = bestY + 0.05;
        double delta = newY - pos.y;
        if (delta > MAX_STEP_UP || delta < -MAX_STEP_DOWN) return pos;
        return new Vec3d(pos.x, newY, pos.z);
    }

    private static boolean isSolid(ClientWorld world, int x, int y, int z) {
        BlockPos bp = new BlockPos(x, y, z);
        return world.getBlockState(bp).isSolidBlock(world, bp);
    }
    private static boolean isAir(ClientWorld world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y + 1, z)).isAir();
    }

    // ----------------------------------------------------------------
    // PARTICLE HELPERS
    // ----------------------------------------------------------------

    private static void spawnRiftOpeningParticles(ClientWorld world, Vec3d pos) {
        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 1.8;
            int ringPoints = 16 + ring * 8;
            DustParticleEffect col = ring == 0 ? RIFT_WHITE : ring == 1 ? CRACK_BRIGHT : CRACK_MID;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                world.addParticle(col,
                        pos.x + Math.cos(angle) * r, pos.y + 0.1, pos.z + Math.sin(angle) * r,
                        -Math.cos(angle) * (0.2 + ring * 0.05), 0.04,
                        -Math.sin(angle) * (0.2 + ring * 0.05));
            }
        }

        scatter(world, RIFT_WHITE,   pos.x, pos.y + 0.2, pos.z, 25, 0.4, 0.6, 0.4, 0.09);
        scatter(world, CRACK_BRIGHT, pos.x, pos.y + 0.2, pos.z, 18, 0.5, 0.7, 0.5, 0.07);
        scatter(world, CRACK_MID,    pos.x, pos.y + 0.1, pos.z, 12, 0.6, 0.4, 0.6, 0.05);
        scatter(world, CRACK_DARK,   pos.x, pos.y + 0.1, pos.z, 8,  0.7, 0.3, 0.7, 0.04);

        for (int h = 0; h < 14; h++) {
            double hf = (double) h / 14;
            DustParticleEffect col = hf < 0.3 ? CRACK_DARK : hf < 0.6 ? CRACK_MID
                    : hf < 0.85 ? CRACK_BRIGHT : RIFT_WHITE;
            world.addParticle(col,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    pos.y + h * 0.45,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    0.0, 0.025, 0.0);
        }

        for (int i = 0; i < 16; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.addParticle(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * r, pos.y + 0.2, pos.z + Math.sin(a) * r,
                    Math.cos(a) * 0.04, 0.12 + world.random.nextDouble() * 0.1, Math.sin(a) * 0.04);
        }

        scatter(world, ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 3, 0.15, 0.1, 0.15, 0.0);
    }

    private static void spawnCrackWallParticles(ClientWorld world, List<Vec3d> crack) {
        for (int i = 0; i < crack.size() - 1; i++) {
            Vec3d a = crack.get(i);
            Vec3d b = crack.get(i + 1);
            int steps = Math.max(1, (int)(a.distanceTo(b) / 0.55));

            for (int s = 0; s <= steps; s++) {
                if (world.random.nextInt(4) != 0) continue;

                double t     = (double) s / steps;
                double baseX = a.x + (b.x - a.x) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseZ = a.z + (b.z - a.z) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseY = a.y + (b.y - a.y) * t;

                double wallHeight = ULT_WALL_PARTICLE_HEIGHT * (0.5 + world.random.nextDouble() * 0.5);

                world.addParticle(CRACK_DARK, baseX, baseY + 0.05, baseZ, 0.0, 0.003, 0.0);
                world.addParticle(CRACK_MID,  baseX, baseY + 0.12, baseZ, 0.0, 0.005, 0.0);

                for (int h = 1; h < ULT_WALL_PARTICLE_DENSITY + 2; h++) {
                    double y = baseY + (wallHeight * h / (ULT_WALL_PARTICLE_DENSITY + 2));
                    float hf = (float) h / (ULT_WALL_PARTICLE_DENSITY + 2);
                    DustParticleEffect col = hf < 0.35f ? CRACK_DARK : CRACK_BRIGHT;
                    world.addParticle(col,
                            baseX + (world.random.nextDouble() - 0.5) * 0.015 * h,
                            y,
                            baseZ + (world.random.nextDouble() - 0.5) * 0.015 * h,
                            0.0, 0.003 + hf * 0.006, 0.0);
                }

                if (world.random.nextFloat() < 0.06f)
                    world.addParticle(RIFT_WHITE, baseX, baseY + wallHeight * 0.4, baseZ, 0.0, 0.02, 0.0);

                if (world.random.nextFloat() < 0.04f)
                    world.addParticle(ParticleTypes.END_ROD, baseX, baseY + 0.1, baseZ,
                            (world.random.nextDouble() - 0.5) * 0.04,
                            0.06 + world.random.nextDouble() * 0.06,
                            (world.random.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    private static void spawnRiftAuraParticles(ClientWorld world, Vec3d pos, long time) {
        for (int ring = 0; ring < 2; ring++) {
            double speed  = ring == 0 ? 0.09 : -0.06;
            double radius = ring == 0 ? 0.7  : 1.1;
            double angle  = time * speed;
            int points    = ring == 0 ? 3 : 5;

            for (int i = 0; i < points; i++) {
                double a = angle + (i * Math.PI * 2.0 / points);
                double r = radius + Math.sin(time * 0.07 + i) * 0.15;
                DustParticleEffect col = ring == 0 ? RIFT_WHITE : CRACK_BRIGHT;
                world.addParticle(col,
                        pos.x + Math.cos(a) * r,
                        pos.y + 0.2 + Math.sin(time * 0.05 + i) * 0.12,
                        pos.z + Math.sin(a) * r,
                        0.0, 0.008, 0.0);
            }
        }

        if (time % 5 == 0)
            world.addParticle(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.2, pos.y + 0.15,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                    0.0, 0.07 + world.random.nextDouble() * 0.05, 0.0);

        if (time % 8 == 0)
            world.addParticle(RIFT_WHITE, pos.x, pos.y + 0.4, pos.z, 0.0, 0.025, 0.0);
    }

    // ----------------------------------------------------------------
    // UTIL
    // ----------------------------------------------------------------

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
