package com.yourname.loopypowers.power;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import com.yourname.loopypowers.damage.ModDamageTypes;
import net.minecraft.block.Blocks;

import java.util.*;

/**
 * Nature power skeleton (1.20.1 / Fabric)
 * Focus: area denial + trapping + "hunt" marking.
 */
public class NaturePower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, NatureState> ACTIVE_STATES = new HashMap<>();

    private static class NatureState {
        final List<GasInstance> gases = new ArrayList<>();
        CageState cage = null;
        final Map<UUID, VineBind> vines = new HashMap<>();
    }

    private static NatureState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new NatureState());
    }

    /* ============================================================
       EASTER EGGS (MIGRATED FROM LOOPYPOWERS)
       ============================================================ */

    private static final float FLOWER_CORPSE_CHANCE = 0.15f;
    private static final float PVZ_KILL_CHANCE = 0.03f;
    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY};

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        NatureState state = getState(player);
        removeCageNow(player, state);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        NatureState state = ACTIVE_STATES.remove(player.getUuid());

        // Cleanup personal buffs
        player.removeStatusEffect(StatusEffects.REGENERATION);
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.HASTE);

        if (state == null) return;

        // Cleanup cage
        removeCageNow(player, state);

        // Cleanup vines
        if (player.getServer() != null) {
            for (VineBind b : state.vines.values()) {
                ServerWorld w = player.getServer().getWorld(b.worldKey);
                if (w != null) {
                    Entity ent = w.getEntity(b.target);
                    if (ent instanceof LivingEntity le) {
                        le.removeStatusEffect(StatusEffects.SLOWNESS);
                        le.removeStatusEffect(StatusEffects.GLOWING);
                        le.removeStatusEffect(ModEffects.TETHERED);
                    }
                }
            }
        }
        state.vines.clear();
        state.gases.clear();
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (amount >= target.getHealth()) {
            // PVZ Zombie Pop
            if (target instanceof ZombieEntity) {
                if (source.isOf(ModDamageTypes.THORN) || source.isOf(ModDamageTypes.VINE_BIND)) {
                    if (target.getWorld().random.nextFloat() < PVZ_KILL_CHANCE) {
                        target.getWorld().playSound(null, target.getBlockPos(), ModSounds.PVZPOP, SoundCategory.HOSTILE, 0.7f, 1.0f);
                    }
                }
            }

            // Flower Planting
            if (target.getWorld() instanceof ServerWorld sw) {
                BlockPos under = target.getBlockPos().down();
                if (sw.getBlockState(under).isOf(Blocks.GRASS_BLOCK) && sw.getBlockState(target.getBlockPos()).isAir()) {
                    if (sw.random.nextFloat() < FLOWER_CORPSE_CHANCE) {
                        Block flower = FLOWERS[sw.random.nextInt(FLOWERS.length)];
                        sw.setBlockState(target.getBlockPos(), flower.getDefaultState());
                        sw.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.6f, 1.0f);
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        NatureState state = getState(player);

        // PASSIVE
        if (PassiveManager.isEnabled(player)) {
            tickPhotosynthesis(player);
        }

        // PRIMARY
        tickExpandingGas(player, state);

        // SECONDARY
        tickCage(player, state);

        // ULT
        tickHunt(player, state);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int PASSIVE_REFRESH_TICKS = 20; // every time it is refreshed
    private static final int PASSIVE_REGEN_AMP = 0;      // regen provided
    private static final int PASSIVE_REGEN_TICKS = 40;   // how long given for

    private static void tickPhotosynthesis(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // refresh rhythm
        if (player.age % PASSIVE_REFRESH_TICKS != 0) return;

        boolean inWater = player.isTouchingWater();
        boolean inSun = w.isDay() && w.isSkyVisible(player.getBlockPos()) && !w.isRaining();

        if (inWater || inSun) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION,
                    PASSIVE_REGEN_TICKS,
                    PASSIVE_REGEN_AMP,
                    true,
                    false
            ));
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */
    // duration
    private static final int GAS_DURATION_TICKS = 280;

    // range
    private static final float GAS_RADIUS_START = 1.8f;
    private static final float GAS_RADIUS_MAX   = 6.6f;

    // vertical volume
    private static final float GAS_HEIGHT_START = 1.8f;
    private static final float GAS_HEIGHT_MAX   = 3.6f;

    // Poison
    private static final int GAS_POISON_TICKS = 90;         // time
    private static final int GAS_POISON_AMP   = 3;          // amp
    private static final int GAS_APPLY_INTERVAL_TICKS = 10;  // checks
    private static final int GAS_REAPPLY_THRESHOLD = 35;    // only refresh when low

    // fog
    private static final int GAS_PARTICLES_MIN = 30;
    private static final int GAS_PARTICLES_MAX = 110;

    // stinky
    private static final float STINK_CHANCE = 0.02f;

    // make green dust
    private static final DustParticleEffect GAS_DUST =
            new DustParticleEffect(new Vector3f(0.12f, 0.95f, 0.18f), 1.75f);

    private static final class GasInstance {
        final RegistryKey<World> worldKey;
        final Vec3d center;
        final int seed;
        int age;

        GasInstance(RegistryKey<World> worldKey, Vec3d center, int seed) {
            this.worldKey = worldKey;
            this.center = center;
            this.seed = seed;
            this.age = 0;
        }
    }

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        NatureState state = getState(player);

        // Spawn in front of player
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d spawn = player.getPos().add(look.multiply(1.2)).add(0, 0.6, 0);

        // Create a deterministic gas instance
        int seed = (int)(w.getTime() ^ player.getUuid().getLeastSignificantBits());
        state.gases.add(new GasInstance(w.getRegistryKey(), spawn, seed));

        // The stink easter egg
        net.minecraft.sound.SoundEvent sound = w.random.nextFloat() < STINK_CHANCE ? ModSounds.STINK : ModSounds.SPRAY;

        w.playSound(null, player.getBlockPos(),
                sound,
                player.getSoundCategory(),
                0.8f, 0.9f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickExpandingGas(ServerPlayerEntity player, NatureState state) {
        if (state.gases.isEmpty()) return;

        long now = player.getServerWorld().getTime();

        Iterator<GasInstance> it = state.gases.iterator();
        while (it.hasNext()) {
            GasInstance g = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(g.worldKey);
            if (w == null) continue;

            g.age++;
            if (g.age >= GAS_DURATION_TICKS) {
                it.remove();
                continue;
            }

            // progress 0..1
            float t = g.age / (float) GAS_DURATION_TICKS;
            t = MathHelper.clamp(t, 0.0f, 1.0f);

            float r = MathHelper.lerp(t, GAS_RADIUS_START, GAS_RADIUS_MAX);
            float h = MathHelper.lerp(t, GAS_HEIGHT_START, GAS_HEIGHT_MAX);

            Vec3d center = g.center;

            // centered vertical volume
            double halfH = h * 0.5;
            double minY = center.y - halfH;
            double maxY = center.y + halfH;

            // VISUALS
            int count = (int) MathHelper.lerp(t, GAS_PARTICLES_MIN, GAS_PARTICLES_MAX);

            // thick core
            w.spawnParticles(
                    GAS_DUST,
                    center.x, center.y, center.z,
                    count,
                    r * 0.90, halfH * 0.90, r * 0.90,
                    0.02
            );

            // boundary wisps (readable edge)
            if (((now + g.seed) & 1L) == 0L) {
                w.spawnParticles(
                        GAS_DUST,
                        center.x, center.y, center.z,
                        Math.max(20, count / 3),
                        r * 1.10, halfH * 0.55, r * 1.10,
                        0.03
                );
            }

            // occasional particles
            if (((now + g.seed) % 10L) == 0L) {
                w.spawnParticles(
                        GAS_DUST,
                        center.x, center.y, center.z,
                        120,
                        r * 0.70, halfH * 0.70, r * 0.70,
                        0.04
                );
            }

            // POISON
            if (((g.age + g.seed) % GAS_APPLY_INTERVAL_TICKS) != 0) continue;

            // Small vertical padding so it feels like “fog volume” not a razor slab
            double yPad = 0.35;

            Box scan = new Box(
                    center.x - r, (minY - yPad), center.z - r,
                    center.x + r, (maxY + yPad), center.z + r
            );

            List<LivingEntity> hits = w.getEntitiesByClass(
                    LivingEntity.class,
                    scan,
                    e -> e.isAlive() && !e.getUuid().equals(player.getUuid())
            );

            double r2 = r * r;

            for (LivingEntity e : hits) {
                var bb = e.getBoundingBox();

                // must overlap vertically with the gas volume
                if (bb.maxY < (minY - yPad) || bb.minY > (maxY + yPad)) continue;

                // XZ cylinder check using closest point on entity AABB
                double closestX = MathHelper.clamp(center.x, bb.minX, bb.maxX);
                double closestZ = MathHelper.clamp(center.z, bb.minZ, bb.maxZ);

                double dx = closestX - center.x;
                double dz = closestZ - center.z;

                if ((dx * dx + dz * dz) > r2) continue;

                // apply poison
                StatusEffectInstance cur = e.getStatusEffect(StatusEffects.POISON);
                if (cur == null || cur.getDuration() <= GAS_REAPPLY_THRESHOLD) {
                    e.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.POISON,
                            GAS_POISON_TICKS,
                            GAS_POISON_AMP,
                            true,
                            false
                    ));
                }
            }
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int CAGE_LIFETIME_TICKS = 240; // time up

    private static final int CAGE_RADIUS = 12;
    private static final int CAGE_POINTS = 68; // ring density
    private static final int CAGE_HEIGHT = 6;
    private static final int CAGE_THICKNESS = 2;

    private static final class CageState {
        int ticksLeft = CAGE_LIFETIME_TICKS;
        RegistryKey<World> worldKey;
        final List<BlockPos> placed = new ArrayList<>();

        CageState(RegistryKey<World> worldKey) {
            this.worldKey = worldKey;
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        NatureState state = getState(player);

        w.playSound(null, player.getBlockPos(), ModSounds.ARENABUILD, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 1.0f);

        removeCageNow(player, state);

        state.cage = new CageState(w.getRegistryKey());

        int cx = player.getBlockPos().getX();
        int cz = player.getBlockPos().getZ();
        int aroundY = player.getBlockPos().getY();

        // build a ring on the ground
        for (int i = 0; i < CAGE_POINTS; i++) {
            double a = (Math.PI * 2.0) * (i / (double) CAGE_POINTS);

            for (int t = 0; t < CAGE_THICKNESS; t++) {
                int rNow = Math.max(1, CAGE_RADIUS - t);

                int x = cx + (int) Math.round(Math.cos(a) * rNow);
                int z = cz + (int) Math.round(Math.sin(a) * rNow);

                // find ground
                int baseAirY = findLocalFloorAirY(w, x, z, aroundY);

                // Build column
                for (int y = 0; y < CAGE_HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, baseAirY + y, z);

                    // only replace air
                    BlockState existing = w.getBlockState(pos);
                    if (!existing.getFluidState().isEmpty()) continue;
                    if (!existing.getCollisionShape(w, pos).isEmpty() && !existing.isAir()) continue;

                    BlockState vine = ModBlocks.THORN_VINE.getDefaultState();
                    w.setBlockState(pos, vine);
                    state.cage.placed.add(pos);
                }
            }
        }
        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static int findLocalFloorAirY(ServerWorld w, int x, int z, int aroundY) {
        int startY = MathHelper.clamp(aroundY + 1, w.getBottomY() + 2, w.getTopY() - 2);

        BlockPos.Mutable m = new BlockPos.Mutable(x, startY, z);

        // search downward for air above
        for (int i = 0; i < 18 && m.getY() > w.getBottomY() + 2; i++) {
            BlockPos belowPos = m.down();

            BlockState here = w.getBlockState(m);
            BlockState below = w.getBlockState(belowPos);

            boolean hereEmpty = here.getCollisionShape(w, m).isEmpty() && here.getFluidState().isEmpty();
            boolean belowSolid = !below.getCollisionShape(w, belowPos).isEmpty() && below.getFluidState().isEmpty();

            if (hereEmpty && belowSolid) {
                return m.getY(); // air block directly above ground
            }

            m.move(Direction.DOWN);
        }

        // fallback - find surface
        return w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static void tickCage(ServerPlayerEntity player, NatureState state) {
        if (state.cage == null) return;

        state.cage.ticksLeft--;
        if (state.cage.ticksLeft > 0) return;

        removeCageNow(player, state);
    }

    private static void removeCageNow(ServerPlayerEntity player, NatureState state) {
        if (state.cage == null) return;

        ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(state.cage.worldKey);
        if (w != null) {
            for (BlockPos pos : state.cage.placed) {
                if (w.getBlockState(pos).isOf(ModBlocks.THORN_VINE)) {
                    w.breakBlock(pos, false);
                }
            }
        }
        state.cage = null;
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */
    // tuning
    private static final int VINE_DURATION_TICKS = 220; // time stuck
    private static final double VINE_RANGE = 22.0;
    private static final int VINE_MAX_TARGETS = 8;
    private static final double VINE_CONE_DOT = 0.80; // cone degrees
    // tether behavior
    private static final double VINE_TETHER_RADIUS = 5.5;     // how far they can roam from the anchor
    private static final double VINE_PULL_MIN = 0.06;         // smallest pull when just outside
    private static final double VINE_PULL_MAX = 0.45;         // strongest pull when far outside
    private static final double VINE_OVER_PULL_SCALE = 0.18;  // pull strength scale vs "over distance"
    // debuff + tick damage
    private static final int VINE_SLOW_REFRESH = 10;
    private static final int VINE_SLOW_TICKS = 20;
    private static final int VINE_SLOW_AMP = 1;
    private static final int VINE_DAMAGE_INTERVAL = 20;
    private static final float VINE_DAMAGE = 1.0f;
    // caster buffs near a vined enemy
    private static final double VINE_BUFF_RANGE = 12.0;
    private static final int VINE_BUFF_REFRESH = 10;
    private static final int VINE_BUFF_TICKS = 30;
    //lash visuals
    private static final int VINE_STRIKE_LASHES_PER_TARGET = 3; // extra vine lines per target
    private static final int VINE_STRIKE_EXTRA_RANDOM = 6;      // extra “miss” lashes forward

    // visuals
    private static final DustParticleEffect VINE_DUST =
            new DustParticleEffect(new Vector3f(0.10f, 0.85f, 0.12f), 1.35f);

    // pink
    private static final DustParticleEffect VINE_PINK_DUST =
            new DustParticleEffect(new Vector3f(0.95f, 0.35f, 0.85f), 1.05f);

    private static final float VINE_PINK_SPECK_CHANCE = 0.12f; // ~12% of particles become pink

    private static final class VineBind {
        final UUID target;                // bound entity
        final RegistryKey<World> worldKey;
        final Vec3d anchor;               // where they were hit by ult
        final int seed;
        int age;

        VineBind(UUID target, RegistryKey<World> worldKey, Vec3d anchor, int seed) {
            this.target = target;
            this.worldKey = worldKey;
            this.anchor = anchor;
            this.seed = seed;
            this.age = 0;
        }
    }
    // debug
    private static final String DEBUG_VINE_SELF = "nature_debug_vine_self";

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        NatureState state = getState(player);

        // cast FX
        w.spawnParticles(VINE_DUST,
                player.getX(), player.getY() + 1.0, player.getZ(),
                80,
                0.75, 0.95, 0.75,
                0.02
        );
        w.playSound(null, player.getBlockPos(),
                ModSounds.VINELASH,
                player.getSoundCategory(),
                1.0f, 0.8f);

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();

        // spawn vines from a hand
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = up.crossProduct(look);
        if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        Vec3d lashFrom = eye
                .add(look.multiply(0.35))     // a bit forward
                .add(right.multiply(0.35))    // to the side
                .add(0.0, -0.35, 0.0);        // slightly down

        Box box = new Box(player.getPos(), player.getPos()).expand(VINE_RANGE, 10, VINE_RANGE);

        List<LivingEntity> candidates = w.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        int taken = 0;
        int seedBase = (int)(w.getTime() ^ player.getUuid().getLeastSignificantBits());

        // DEBUG to try tether self
        if (player.getCommandTags().contains(DEBUG_VINE_SELF)) {
            player.getCommandTags().remove(DEBUG_VINE_SELF);

            int seed = seedBase ^ 0xBEEF;
            Vec3d anchor = player.getPos();

            state.vines.put(player.getUuid(), new VineBind(player.getUuid(), w.getRegistryKey(), anchor, seed));

            // anchor particles
            w.spawnParticles(VINE_DUST, anchor.x, anchor.y + 0.2, anchor.z, 35, 0.35, 0.15, 0.35, 0.02);

            player.swingHand(Hand.MAIN_HAND, true);
            return;
        }

        for (LivingEntity e : candidates) {
            if (taken >= VINE_MAX_TARGETS) break;

            // cone range check
            Vec3d targetPoint = e.getPos().add(0, e.getHeight() * 0.65, 0);
            Vec3d toTarget = targetPoint.subtract(eye);

            double distSq = toTarget.lengthSquared();
            if (distSq < 1.0e-6) continue;
            if (distSq > (VINE_RANGE * VINE_RANGE)) continue;

            Vec3d dir = toTarget.normalize();
            double dot = look.dotProduct(dir);
            if (dot < VINE_CONE_DOT) continue;

            // LOS check
            if (!hasLineOfSight(w, player, e, eye)) continue;

            // anchor point is where they were when hit
            Vec3d anchor = e.getPos();

            // store bind
            int seed = seedBase ^ e.getId();

            // main strike line
            spawnVineStrike(w, lashFrom, e.getPos().add(0, e.getHeight() * 0.65, 0), seed);

            // extra lash lines around the target
            spawnVineLashes(w, lashFrom, e, seed);

            state.vines.put(e.getUuid(), new VineBind(e.getUuid(), w.getRegistryKey(), anchor, seed));

            // bind FX on target
            w.spawnParticles(VINE_DUST,
                    e.getX(), e.getY() + (e.getHeight() * 0.55), e.getZ(),
                    40,
                    0.45, 0.45, 0.45,
                    0.02
            );
            // pink
            w.spawnParticles(VINE_PINK_DUST,
                    e.getX(), e.getY() + (e.getHeight() * 0.55), e.getZ(),
                    6,
                    0.35, 0.35, 0.35,
                    0.01
            );

            w.spawnParticles(VINE_DUST,
                    anchor.x, anchor.y + 0.2, anchor.z,
                    25,
                    0.35, 0.15, 0.35,
                    0.02
            );

            taken++;
        }

        // extra lashes
        Random rr = new Random(seedBase);
        for (int i = 0; i < VINE_STRIKE_EXTRA_RANDOM; i++) {
            double d = 6.0 + rr.nextDouble() * (VINE_RANGE - 6.0);
            Vec3d to = lashFrom.add(look.multiply(d));

            // spread around crosshair
            double ox = (rr.nextDouble() - 0.5) * 3.5;
            double oy = (rr.nextDouble() - 0.5) * 2.0;
            double oz = (rr.nextDouble() - 0.5) * 3.5;

            spawnVineStrike(w, lashFrom, to.add(ox, oy, oz), seedBase ^ (i * 991));
        }

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickHunt(ServerPlayerEntity player, NatureState state) {
        tickVines(player, state);

        // buffs for the caster near vined
        if (player.age % VINE_BUFF_REFRESH != 0) return;

        ServerWorld w = player.getServerWorld();
        RegistryKey<World> wk = w.getRegistryKey();

        boolean nearAny = false;
        double r2 = VINE_BUFF_RANGE * VINE_BUFF_RANGE;

        for (VineBind b : state.vines.values()) {
            if (!b.worldKey.equals(wk)) continue;

            Entity ent = w.getEntity(b.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;

            if (le.squaredDistanceTo(player) <= r2) {
                nearAny = true;
                break;
            }
        }

        if (nearAny) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, VINE_BUFF_TICKS, 0, true, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, VINE_BUFF_TICKS, 0, true, false));

            // visuals while buffed
            spawnBuffRing(w, player);
        }
    }

    private static void tickVines(ServerPlayerEntity player, NatureState state) {
        if (state.vines.isEmpty()) return;
        long now = player.getServerWorld().getTime();

        Iterator<VineBind> it = state.vines.values().iterator();
        while (it.hasNext()) {
            VineBind b = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(b.worldKey);
            if (w == null) continue;

            b.age++;
            if (b.age >= VINE_DURATION_TICKS) {
                it.remove();
                continue;
            }

            Entity ent = w.getEntity(b.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }

            // === tether particles ===
            if (((now + b.seed) % 3L) == 0L) {
                Vec3d a = b.anchor.add(0, 0.2, 0);
                Vec3d t = le.getPos().add(0, le.getHeight() * 0.55, 0);
                spawnVineTether(w, a, t, b.seed);
            }

            // effects
            if (((b.age + b.seed) % VINE_SLOW_REFRESH) == 0) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, VINE_SLOW_TICKS, VINE_SLOW_AMP, true, false));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 15, 0, true, false));
                le.addStatusEffect(new StatusEffectInstance(ModEffects.TETHERED, 5, 0, true, false));
            }

            //soft tether
            double dx = b.anchor.x - le.getX();
            double dz = b.anchor.z - le.getZ();
            double distSq = dx * dx + dz * dz;

            double rad = VINE_TETHER_RADIUS;
            double radSq = rad * rad;

            if (distSq > radSq) {
                double dist = Math.sqrt(distSq);
                double over = dist - rad;

                // pull strength scales with how far outside they are
                double pull = MathHelper.clamp(over * VINE_OVER_PULL_SCALE, VINE_PULL_MIN, VINE_PULL_MAX);

                double nx = dx / dist;
                double nz = dz / dist;

                le.addVelocity(nx * pull, 0.0, nz * pull);
                le.velocityModified = true;

                // for players, force sync so it feels consistent
                if (le instanceof ServerPlayerEntity sp) {
                    sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
                    sp.setSprinting(false);
                }
            }

            // tick damage
            if (((b.age + b.seed) % VINE_DAMAGE_INTERVAL) == 0) {
                // don't attribute caster to stop annoying knockback
                le.damage(ModDamageTypes.vineBind(w), VINE_DAMAGE);
            }
        }
    }

    private static boolean hasLineOfSight(ServerWorld w, ServerPlayerEntity caster, LivingEntity target, Vec3d casterEye) {
        Vec3d targetPoint = target.getPos().add(0, target.getHeight() * 0.85, 0);

        HitResult hr = w.raycast(new RaycastContext(
                casterEye,
                targetPoint,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                caster
        ));

        if (hr.getType() == HitResult.Type.MISS) return true;

        // if the hit is basically at the target point, treat it as LOS
        double hitSq = hr.getPos().squaredDistanceTo(casterEye);
        double endSq = targetPoint.squaredDistanceTo(casterEye);
        return hitSq >= (endSq - 0.05);
    }

    private static void spawnVineTether(ServerWorld w, Vec3d from, Vec3d to, int seed) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 10), 10, 60);
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) {
            DustParticleEffect eff = ((w.getTime() + seed + i) % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;

            w.spawnParticles(
                    eff,
                    p.x, p.y, p.z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
            p = p.add(step);
        }

        // “anchor” puff
        if ((w.getTime() & 3L) == 0L) {
            w.spawnParticles(
                    VINE_DUST,
                    from.x, from.y + 0.15, from.z,
                    8,
                    0.20, 0.10, 0.20,
                    0.01
            );
            w.spawnParticles(
                    VINE_PINK_DUST,
                    from.x, from.y + 0.15, from.z,
                    2,
                    0.20, 0.10, 0.20,
                    0.01
            );
        }
    }

    private static void spawnVineStrike(ServerWorld w, Vec3d from, Vec3d to, int seed) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 14), 12, 90);
        Vec3d step = delta.multiply(1.0 / steps);

        Random r = new Random(seed);
        Vec3d p = from;

        for (int i = 0; i <= steps; i++) {
            // jitter
            double j = 0.05 + (r.nextDouble() * 0.04);
            double jx = (r.nextDouble() - 0.5) * j;
            double jy = (r.nextDouble() - 0.5) * j;
            double jz = (r.nextDouble() - 0.5) * j;

            DustParticleEffect eff = (r.nextFloat() < VINE_PINK_SPECK_CHANCE) ? VINE_PINK_DUST : VINE_DUST;

            w.spawnParticles(
                    eff,
                    p.x + jx, p.y + jy, p.z + jz,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );

            p = p.add(step);
        }

        // hit stuff
        w.spawnParticles(
                VINE_DUST,
                to.x, to.y, to.z,
                8,
                0.20, 0.20, 0.20,
                0.02
        );
        w.spawnParticles(
                VINE_PINK_DUST,
                to.x, to.y, to.z,
                2,
                0.20, 0.20, 0.20,
                0.02
        );
    }

    private static void spawnVineLashes(ServerWorld w, Vec3d lashFrom, LivingEntity target, int seed) {
        Random r = new Random(seed ^ 0x52A1B);

        Vec3d base = target.getPos().add(0, target.getHeight() * 0.65, 0);

        for (int i = 0; i < VINE_STRIKE_LASHES_PER_TARGET; i++) {
            double ox = (r.nextDouble() - 0.5) * 1.8;
            double oy = (r.nextDouble() - 0.5) * 1.2;
            double oz = (r.nextDouble() - 0.5) * 1.8;

            spawnVineStrike(w, lashFrom, base.add(ox, oy, oz), seed ^ (i * 1337));
        }
    }

    private static void spawnBuffRing(ServerWorld w, ServerPlayerEntity player) {
        Vec3d c = player.getPos();
        double y = c.y + 0.15;

        int points = 24;
        double radius1 = 0.85;
        double radius2 = 1.15;

        // rotate over time
        double spin = (w.getTime() * 0.22);

        for (int i = 0; i < points; i++) {
            double a = spin + (Math.PI * 2.0) * (i / (double) points);

            double x1 = c.x + Math.cos(a) * radius1;
            double z1 = c.z + Math.sin(a) * radius1;

            double x2 = c.x + Math.cos(a + 0.35) * radius2;
            double z2 = c.z + Math.sin(a + 0.35) * radius2;

            DustParticleEffect eff1 = (w.getTime() % 9L == 0L) ? VINE_PINK_DUST : VINE_DUST;
            w.spawnParticles(eff1, x1, y, z1, 1, 0.0, 0.0, 0.0, 0.0);

            if ((i & 1) == 0) {
                DustParticleEffect eff2 = ((w.getTime() + i) % 11L == 0L) ? VINE_PINK_DUST : VINE_DUST;
                w.spawnParticles(eff2, x2, y + 0.10, z2, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        w.spawnParticles(
                VINE_DUST,
                c.x, c.y + 0.9, c.z,
                6,
                0.20, 0.35, 0.20,
                0.01
        );
        w.spawnParticles(
                VINE_PINK_DUST,
                c.x, c.y + 0.9, c.z,
                2,
                0.20, 0.35, 0.20,
                0.01
        );
    }

    // Called by HealingPower's purge ability to cleanse the player
    public static boolean cleanseVines(ServerPlayerEntity player) {
        boolean removed = false;
        for (NatureState state : ACTIVE_STATES.values()) {
            if (state.vines.remove(player.getUuid()) != null) {
                removed = true;
            }
        }
        return removed;
    }

    @Override public String getName() { return "Nature"; }
    @Override public String getPrimaryName() { return "Toxic Spores"; }
    @Override public String getSecondaryName() { return "Verdant Prison"; }
    @Override public String getUltimateName() { return "Wild Hunt"; }

    @Override public long getPrimaryCooldownMs() { return 18_000; }
    @Override public long getSecondaryCooldownMs() { return 38_000; }
    @Override public long getUltimateCooldownMs() { return 350_000; }

    @Override
    public String getOverviewDescription() {
        return "Nature is a power centered around area denial, the abilities can lock off areas and entrap entities. " +
                "Users get buffs in sunlight and water, making you especially deadly in certain zones (particularly in enclosed spaces)";
    }

    @Override
    public String getPassiveName() {
        return "Photosynthesis";
    }

    @Override
    public String getPassiveDescription() {
        return "You gain regeneration in sunlight and in water.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Create a cloud of expanding poison gas. Anything inside the cloud will be inflicted with poison. You are immune to this gas.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Conjure a circular cage of thorned vines around you for a long time. Anyone who touches these vines will be slowed and take damage." +
                " People with the nature power do not take damage from the vines but are still slowed, the vines are also difficult to break.";
    }

    @Override
    public String getUltimateDescription() {
        return "Shoot many vines in front of you. Any enemies close and in direct line of site will be hit by these vines and tethered to their current location, which" +
                " will keep them trapped in that location, dealing periodic damage and slowing them. When near a tethered energy, the caster becomes empowered with speed and haste.";
    }
}