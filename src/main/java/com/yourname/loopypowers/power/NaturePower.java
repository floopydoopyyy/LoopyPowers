package com.yourname.loopypowers.power;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import com.yourname.loopypowers.damage.ModDamageTypes;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Nature power skeleton (1.20.1 / Fabric -> 1.21.1)
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
       EASTER EGGS
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
                        le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.TETHERED));
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
        if (!player.isAlive()) return;

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
    private static final int GAS_POISON_AMP   = 2;          // amp
    private static final int GAS_APPLY_INTERVAL_TICKS = 10;  // checks
    private static final int GAS_REAPPLY_THRESHOLD = 35;    // only refresh when low

    // fog
    private static final int GAS_PARTICLES_MIN = 30;
    private static final int GAS_PARTICLES_MAX = 110;

    // stinky
    private static final float STINK_CHANCE = 0.02f;

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
            float halfH = h * 0.5f;

            int count = MathHelper.lerp(t, GAS_PARTICLES_MIN, GAS_PARTICLES_MAX);

            NatureGasTickPayload payload = new NatureGasTickPayload(
                    center.x, center.y, center.z,
                    r, halfH,
                    count, g.seed
            );
            BlockPos centerPos = BlockPos.ofFloored(center);
            PlayerLookup.tracking(w, centerPos).forEach(sp -> ServerPlayNetworking.send(sp, payload));

            // POISON
            double minY = center.y - halfH;
            double maxY = center.y + halfH;

            if (((g.age + g.seed) % GAS_APPLY_INTERVAL_TICKS) != 0) continue;

            // Small vertical padding so it feels like "fog volume" not a razor slab
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
    private static final int CAGE_BUILD_INTERVAL_TICKS = 2; // build 1 layer every 2 ticks

    private static final int CAGE_RADIUS = 12;
    private static final int CAGE_POINTS = 68; // ring density
    private static final int CAGE_HEIGHT = 6;
    private static final int CAGE_THICKNESS = 2;

    private static final class CageState {
        int ticksLeft = CAGE_LIFETIME_TICKS;
        RegistryKey<World> worldKey;
        final List<BlockPos> bases;
        final List<BlockPos> placed = new ArrayList<>();

        int buildLayer = 0;
        int buildWait = 0;
        boolean built = false;

        CageState(RegistryKey<World> worldKey, List<BlockPos> bases) {
            this.worldKey = worldKey;
            this.bases = bases;
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        NatureState state = getState(player);

        w.playSound(null, player.getBlockPos(), ModSounds.ARENABUILD, net.minecraft.sound.SoundCategory.BLOCKS, 0.8f, 1.0f);

        removeCageNow(player, state);

        int cx = player.getBlockPos().getX();
        int cz = player.getBlockPos().getZ();
        int aroundY = player.getBlockPos().getY();

        // Calculate all ground bases (do not place blocks yet)
        List<BlockPos> bases = new ArrayList<>();
        for (int i = 0; i < CAGE_POINTS; i++) {
            double a = (Math.PI * 2.0) * (i / (double) CAGE_POINTS);
            for (int t = 0; t < CAGE_THICKNESS; t++) {
                int rNow = Math.max(1, CAGE_RADIUS - t);
                int x = cx + (int) Math.round(Math.cos(a) * rNow);
                int z = cz + (int) Math.round(Math.sin(a) * rNow);
                int baseAirY = findLocalFloorAirY(w, x, z, aroundY);
                bases.add(new BlockPos(x, baseAirY, z));
            }
        }

        state.cage = new CageState(w.getRegistryKey(), bases);

        NatureCageFxPayload cageFx = new NatureCageFxPayload(cx, aroundY, cz, CAGE_RADIUS, CAGE_THICKNESS);
        BlockPos playerPos = player.getBlockPos();
        PlayerLookup.tracking(w, playerPos).forEach(sp -> ServerPlayNetworking.send(sp, cageFx));

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

        ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(state.cage.worldKey);
        if (w == null) return;

        if (!state.cage.built) {
            state.cage.buildWait--;
            if (state.cage.buildWait <= 0) {
                int yOffset = state.cage.buildLayer;
                BlockState vine = ModBlocks.THORN_VINE.getDefaultState();

                for (BlockPos base : state.cage.bases) {
                    BlockPos pos = base.up(yOffset);

                    BlockState existing = w.getBlockState(pos);
                    if (!existing.getFluidState().isEmpty()) continue;
                    if (!existing.getCollisionShape(w, pos).isEmpty() && !existing.isAir()) continue;

                    w.setBlockState(pos, vine, 2);
                    state.cage.placed.add(pos);
                }

                state.cage.buildLayer++;
                state.cage.buildWait = CAGE_BUILD_INTERVAL_TICKS;

                if (state.cage.buildLayer >= CAGE_HEIGHT) {
                    state.cage.built = true;
                }
            }
            return; // Pause lifetime decay while building
        }

        state.cage.ticksLeft--;
        if (state.cage.ticksLeft > 0) return;

        removeCageNow(player, state);
    }

    private static void removeCageNow(ServerPlayerEntity player, NatureState state) {
        if (state.cage == null) return;

        ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(state.cage.worldKey);
        if (w != null) {
            for (int i = state.cage.placed.size() - 1; i >= 0; i--) {
                BlockPos pos = state.cage.placed.get(i);
                if (w.getBlockState(pos).isOf(ModBlocks.THORN_VINE)) {
                    w.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                }
            }
            // Play a single unified crumbling sound instead of per-block sounds
            w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CROP_BREAK, SoundCategory.BLOCKS, 1.0f, 0.7f);
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
    private static final int VINE_STRIKE_EXTRA_RANDOM = 6;      // extra "miss" lashes forward

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
        NatureVineCastPayload castPayload = new NatureVineCastPayload(player.getX(), player.getY() + 1.0, player.getZ());
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, castPayload));

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
            NatureVineCastPayload anchorPayload = new NatureVineCastPayload(anchor.x, anchor.y + 0.2, anchor.z);
            PlayerLookup.tracking(w, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, anchorPayload));

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

            state.vines.put(e.getUuid(), new VineBind(e.getUuid(), w.getRegistryKey(), anchor, seed));

            // strike + lashes + bind FX + anchor puff — all handled client-side
            NatureVineBindPayload bindPayload = new NatureVineBindPayload(
                    lashFrom.x, lashFrom.y, lashFrom.z,
                    e.getId(),
                    anchor.x, anchor.y, anchor.z,
                    seed
            );
            PlayerLookup.tracking(w, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, bindPayload));

            taken++;
        }

        // extra random lashes
        Random rr = new Random(seedBase);
        for (int i = 0; i < VINE_STRIKE_EXTRA_RANDOM; i++) {
            double d = 6.0 + rr.nextDouble() * (VINE_RANGE - 6.0);
            Vec3d to = lashFrom.add(look.multiply(d));

            // spread around crosshair
            double ox = (rr.nextDouble() - 0.5) * 3.5;
            double oy = (rr.nextDouble() - 0.5) * 2.0;
            double oz = (rr.nextDouble() - 0.5) * 3.5;

            Vec3d lashTo = to.add(ox, oy, oz);
            int lashSeed = seedBase ^ (i * 991);
            NatureVineStrikePayload strikePayload = new NatureVineStrikePayload(
                    lashFrom.x, lashFrom.y, lashFrom.z,
                    lashTo.x, lashTo.y, lashTo.z,
                    lashSeed
            );
            BlockPos midPos = BlockPos.ofFloored((lashFrom.x + lashTo.x) * 0.5, (lashFrom.y + lashTo.y) * 0.5, (lashFrom.z + lashTo.z) * 0.5);
            PlayerLookup.tracking(w, midPos).forEach(sp -> ServerPlayNetworking.send(sp, strikePayload));
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
            NatureBuffRingPayload ringPayload = new NatureBuffRingPayload(player.getX(), player.getY(), player.getZ(), w.getTime());
            PlayerLookup.tracking(w, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, ringPayload));
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
                Vec3d from = b.anchor.add(0, 0.2, 0);
                Vec3d to   = le.getPos().add(0, le.getHeight() * 0.55, 0);
                NatureVineTetherPayload tetherPayload = new NatureVineTetherPayload(
                        from.x, from.y, from.z,
                        to.x, to.y, to.z,
                        b.seed
                );
                BlockPos midPos = BlockPos.ofFloored((from.x + to.x) * 0.5, (from.y + to.y) * 0.5, (from.z + to.z) * 0.5);
                PlayerLookup.tracking(w, midPos).forEach(sp -> ServerPlayNetworking.send(sp, tetherPayload));
            }

            // effects
            if (((b.age + b.seed) % VINE_SLOW_REFRESH) == 0) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, VINE_SLOW_TICKS, VINE_SLOW_AMP, true, false));
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 15, 0, true, false));
                le.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.TETHERED), 5, 0, true, false));
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

    @Override public String getName() { return Text.translatable("power.loopypowers.nature.name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.nature.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.nature.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.nature.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs() { return 18_000; }
    @Override public long getSecondaryCooldownMs() { return 38_000; }
    @Override public long getUltimateCooldownMs() { return 350_000; }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.nature.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Text.translatable("power.loopypowers.nature.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.nature.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.nature.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.nature.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.nature.description.ultimate").getString();
    }
}
