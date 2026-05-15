package com.yourname.loopypowers.power;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.fluid.Fluids;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;
import com.yourname.loopypowers.damage.ModDamageTypes;

import java.util.*;

public class IcePower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, IceCasterState> CASTER_STATES = new HashMap<>();
    private static final Map<UUID, IceVictimState> VICTIM_STATES = new HashMap<>();

    private static class SnowmanBuild {
        int step = 1;
        int delay = 10;
        BlockPos pos;
        SnowmanBuild(BlockPos pos) { this.pos = pos; }
    }

    private static class IceCasterState {
        int beamChargeTicks = 0;
        int beamFireTicks = 0;
        int ultActiveTicks = 0;
        int ultPulseTicks = 0;
        List<SnowmanBuild> snowmen = new ArrayList<>();
    }

    private static class IceVictimState {
        int freezePoints = 0;
        int freezeDecayTicks = 0;
        int freezeImmuneTicks = 0;
        int lastSpikeHitTick = -1;
        int lastWaveHitId = -1;
        RegistryKey<World> worldKey;

        IceVictimState(RegistryKey<World> worldKey) {
            this.worldKey = worldKey;
        }
    }

    private static IceCasterState getCasterState(ServerPlayerEntity player) {
        return CASTER_STATES.computeIfAbsent(player.getUuid(), k -> new IceCasterState());
    }

    private static IceVictimState getVictimState(LivingEntity victim) {
        ServerWorld w = (ServerWorld) victim.getWorld();
        return VICTIM_STATES.computeIfAbsent(victim.getUuid(), k -> new IceVictimState(w.getRegistryKey()));
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("ice_")); // Clean legacy tags
        CASTER_STATES.put(player.getUuid(), new IceCasterState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("ice_"));
        CASTER_STATES.remove(player.getUuid());

        // Remove lingering slow statuses
        player.removeStatusEffect(StatusEffects.SLOWNESS);

        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                // Thaw all entities
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(150), LivingEntity::isAlive)) {
                    IceVictimState state = VICTIM_STATES.get(e.getUuid());
                    if (state != null && state.freezePoints > 0) {
                        state.freezePoints = 0;
                        e.setFrozenTicks(0);
                        e.removeStatusEffect(ModEffects.DEEPFREEZE);
                    }
                }

                // Clear any pending global instances owned by this player
                SPIKE_CASTS.removeIf(sc -> sc.owner.equals(player.getUuid()));
                ULT_WAVES.removeIf(uw -> uw.owner.equals(player.getUuid()));
            }
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;
        tickFrozenWorld(player.getServerWorld());
        tickSpikesWorld(player.getServerWorld());
        tickBeam(player);
        tickUltimate(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        if (!tryShatter(attacker, target)) {
            applyFreezePoints(attacker, target, FRZ_POINTS_MELEE);
        }
    }

   /* ============================================================
   PASSIVE
   ============================================================ */

    // Point tuning
    private static final int FRZ_MAX_POINTS = 100;

    // Stage thresholds (0 = no freeze)
    private static final int FRZ_STAGE_1 = 10;    // light particles
    private static final int FRZ_STAGE_2 = 40;   // Slowness 1
    private static final int FRZ_STAGE_3 = 60;   // Slowness 2
    private static final int FRZ_STAGE_4 = 80;   // Slowness 2 + Mining Fatigue 1
    private static final int FRZ_STAGE_5 = 95;   // Fully frozen

    private static final int FRZ_DECAY_DELAY_TICKS = 20;  // how long after last hit before freeze decays
    private static final int FRZ_DECAY_STEP_TICKS  = 10;   // after delay, decay every this many ticks
    private static final int FRZ_DECAY_POINTS_STEP = 5;   // how many points decay
    // Shatter
    private static final int   FRZ_IMMUNE_TICKS = 100;     // time of ice immunity after shatter
    private static final float SHATTER_BONUS_DAMAGE = 12.0f; // damage on shatter

    // How many points abilities add
    private static final int FRZ_POINTS_MELEE = 7;  // melee hits

    // particles
    private static final DustParticleEffect FRZ_BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.25f, 0.65f, 1.00f), 0.75f);
    private static final DustParticleEffect FRZ_SHIMMER_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.95f, 1.00f), 0.45f);

    private static final Map<RegistryKey<World>, Long> FROZEN_LAST_TICK = new HashMap<>();

    private static int getFreezeStage(int points) {
        if (points < FRZ_STAGE_1) return 0;
        if (points < FRZ_STAGE_2) return 1;
        if (points < FRZ_STAGE_3) return 2;
        if (points < FRZ_STAGE_4) return 3;
        if (points < FRZ_STAGE_5) return 4;
        return 5;
    }

    /** Calculates the EXACT time until the target thaws and updates the UI timer. */
    private static void syncFreezeTimer(LivingEntity e, IceVictimState state) {
        if (state.freezePoints <= 0) {
            e.removeStatusEffect(ModEffects.DEEPFREEZE);
            return;
        }

        // Calculate exact ticks until thaw based on the decay intervals
        int steps = (int) Math.ceil((double) state.freezePoints / FRZ_DECAY_POINTS_STEP);
        int ticksRemaining = state.freezeDecayTicks + Math.max(0, (steps - 1) * FRZ_DECAY_STEP_TICKS);

        // Remove and reapply to force the UI to update the countdown safely
        e.removeStatusEffect(ModEffects.DEEPFREEZE);
        e.addStatusEffect(new StatusEffectInstance(ModEffects.DEEPFREEZE, ticksRemaining, 0, false, false, true));
    }

    private static void applyFreezePoints(ServerPlayerEntity caster, LivingEntity target, int addPoints) {
        if (addPoints <= 0) return;

        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return;

        state.freezePoints = MathHelper.clamp(state.freezePoints + addPoints, 0, FRZ_MAX_POINTS);
        state.freezeDecayTicks = FRZ_DECAY_DELAY_TICKS;

        // Force a UI update immediately when hit
        syncFreezeTimer(target, state);

        ServerWorld w = caster.getServerWorld();
        spawnFreezeStageParticles(w, target, getFreezeStage(state.freezePoints));
    }

    private static boolean tryShatter(ServerPlayerEntity caster, LivingEntity target) {
        IceVictimState state = getVictimState(target);
        if (state.freezeImmuneTicks > 0) return false;

        if (state.freezePoints < FRZ_STAGE_5) return false; // only when fully frozen

        ServerWorld w = caster.getServerWorld();

        // Bonus damage
        target.damage(ModDamageTypes.iceShatter(w, caster), SHATTER_BONUS_DAMAGE);

        // FX
        doShatterFX(w, target);

        // sound
        w.playSound(
                null, // null
                target.getBlockPos(),
                ModSounds.SHATTER,
                SoundCategory.PLAYERS,
                0.5f,   // volume
                0.8f    // pitch
        );

        // camerashake
        CameraShake.shakeNearby(caster,
                6,
                8,
                0.35f);

        // Clear freeze + grant immunity
        clearFreeze(target);
        state.freezeImmuneTicks = FRZ_IMMUNE_TICKS;

        return true;
    }

    private static void doShatterFX(ServerWorld w, LivingEntity target) {
        Vec3d p = target.getPos().add(0, target.getHeight() * 0.55, 0);

        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 60, 0.35, 0.35, 0.35, 0.00);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 18, 0.25, 0.20, 0.25, 0.00);
        w.spawnParticles(FRZ_BLUE_DUST, p.x, p.y, p.z, 24, 0.25, 0.20, 0.25, 0.00);
        w.spawnParticles(FRZ_SHIMMER_DUST, p.x, p.y, p.z, 16, 0.22, 0.18, 0.22, 0.00);

        w.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.9f, 1.15f);
        w.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 0.9f, 1.55f);
    }

    private static void clearFreeze(LivingEntity e) {
        IceVictimState state = VICTIM_STATES.get(e.getUuid());
        if (state != null) {
            state.freezePoints = 0;
            state.freezeDecayTicks = 0;
        }

        e.setFrozenTicks(0);
        e.removeStatusEffect(ModEffects.DEEPFREEZE); // clear visual indicator
    }

    private static void tickFrozenWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();
        Long last = FROZEN_LAST_TICK.get(key);
        if (last != null && last == now) return;
        FROZEN_LAST_TICK.put(key, now);

        if (VICTIM_STATES.isEmpty()) return;

        Iterator<Map.Entry<UUID, IceVictimState>> it = VICTIM_STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, IceVictimState> entry = it.next();
            IceVictimState state = entry.getValue();

            if (!state.worldKey.equals(key)) continue;

            Entity ent = w.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                it.remove();
                continue;
            }

            // Tick immunity
            if (state.freezeImmuneTicks > 0) state.freezeImmuneTicks--;

            int points = state.freezePoints;
            if (points <= 0) {
                // stop tracking if no freeze + no immunity
                if (state.freezeImmuneTicks <= 0) it.remove();
                continue;
            }

            // Sync the effect periodically in case they re-logged or the client desyncs
            if (now % 10 == 0 || !le.hasStatusEffect(ModEffects.DEEPFREEZE)) {
                syncFreezeTimer(le, state);
            }

            // Stage effects & particles
            int stage = getFreezeStage(points);
            applyFreezeStageEffects(w, le, stage);

            // Decay logic:
            if (state.freezeDecayTicks > 0) {
                state.freezeDecayTicks--;
                if (state.freezeDecayTicks == 0) {
                    state.freezePoints = Math.max(0, points - FRZ_DECAY_POINTS_STEP);
                    if (state.freezePoints > 0) {
                        state.freezeDecayTicks = FRZ_DECAY_STEP_TICKS;
                    } else {
                        clearFreeze(le);
                        if (state.freezeImmuneTicks <= 0) it.remove();
                    }
                }
            }
        }
    }

    private static void applyFreezeStageEffects(ServerWorld w, LivingEntity e, int stage) {
        // keep durations short so “clearing stages” feels instant (effects fall off quickly)
        final int T = 10;

        // particles scale with stage
        spawnFreezeStageParticles(w, e, stage);

        if (stage >= 2) {
            int slowAmp = (stage >= 3) ? 1 : 0; // stage2->0, stage3+->1
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, T, slowAmp, true, false));
        }
        if (stage >= 4) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, T, 0, true, false)); // MF1
        }
        if (stage >= 5) {
            // “can barely move” + mining fatigue stronger
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, T, 4, true, false));        // very slow
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, T, 2, true, false));  // heavy mining fatigue

            // extra movement clamp (feels frozen even if they have speed boosts etc.)
            Vec3d v = e.getVelocity();
            e.setVelocity(v.x * 0.25, MathHelper.clamp(v.y, -0.5, 0.5), v.z * 0.25);
            e.velocityModified = true;
            e.fallDistance = 0.0f;

            // strong frozen overlay (visual)
            e.setFrozenTicks(Math.max(e.getFrozenTicks(), 140));
        }
    }

    private static void spawnFreezeStageParticles(ServerWorld w, LivingEntity e, int stage) {
        if (stage <= 0) return;

        Vec3d p = e.getPos().add(0, e.getHeight() * 0.55, 0);

        // keep it cheap: stage 1/2 spawn less often
        if (stage <= 2 && (w.getTime() & 1) == 1) return;

        int snow = switch (stage) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            case 4 -> 7;
            default -> 12; // stage 5
        };

        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, snow, 0.18, 0.22, 0.18, 0.00);

        if (stage >= 2) {
            w.spawnParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 1, 0.15, 0.18, 0.15, 0.00);
        }
        if (stage >= 3 && (w.getTime() % 3) == 0) {
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0.00);
        }
        if (stage >= 4) {
            w.spawnParticles(FRZ_BLUE_DUST, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.00);
        }
        if (stage >= 5) {
            // IMPORTANT: very clear “ready to shatter” indicator
            spawnShatterReadyParticles(w, e);
        }
    }

    private static void spawnShatterReadyParticles(ServerWorld w, LivingEntity e) {
        // pulse every other tick
        if ((w.getTime() & 1) == 1) return;

        Vec3d c = e.getPos().add(0, e.getHeight() * 0.72, 0);

        // rotating halo ring around the upper body
        double baseAng = w.getTime() * 0.35;
        double pulse = 0.06 * Math.sin(w.getTime() * 0.45);
        double r = 0.55 + pulse;

        int points = 14;
        for (int i = 0; i < points; i++) {
            double a = baseAng + (i * (Math.PI * 2.0 / points));
            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + (Math.sin(a * 2.0) * 0.05);

            // bright “shatter ready” sparkles
            w.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.0);

            // shimmer + blue crackle mixed in
            if ((i % 2) == 0) {
                w.spawnParticles(FRZ_SHIMMER_DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i % 3) == 0) {
                w.spawnParticles(FRZ_BLUE_DUST, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i % 4) == 0) {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }

        if ((w.getTime() % 6) == 0) {
            w.spawnParticles(ParticleTypes.ENCHANT, c.x, c.y - 0.15, c.z, 6, 0.25, 0.22, 0.25, 0.0);
            w.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y - 0.15, c.z, 10, 0.28, 0.22, 0.28, 0.0);
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    private static final long PRIMARY_COOLDOWN_MS = 13_000;

    private static final double SPIKES_RANGE = 12.0;

    private static final int SPIKES_COUNT = 9;
    private static final int SPIKES_SPREAD_RADIUS = 1;

    private static final int SPIKES_TERRAIN_SEARCH = 3;

    private static final int SPIKES_MAX_HEIGHT = 3;
    private static final int SPIKES_RISE_INTERVAL = 2;

    private static final double SPIKES_MAX_Y_VEL = 1.65;
    private static final float SPIKES_DAMAGE = 8.5f;
    private static final double SPIKES_KNOCKUP_Y = 0.75;
    private static final int SPIKES_FREEZE_STACKS = 15;

    private static final double SPIKES_TRAVEL_SPEED = 0.9;
    private static final int SPIKES_TRAVEL_MIN_TICKS = 6;
    private static final int SPIKES_TRAVEL_MAX_TICKS = 16;

    private static final DustParticleEffect SPIKE_TRAIL_DUST =
            new DustParticleEffect(new Vector3f(0.55f, 0.85f, 1.00f), 1.10f);

    private static final DustParticleEffect SPIKE_PUFF_DUST =
            new DustParticleEffect(new Vector3f(0.35f, 0.80f, 1.00f), 1.35f);

    private static final List<SpikeCast> SPIKE_CASTS = new ArrayList<>();
    private static final Map<RegistryKey<World>, Long> SPIKES_LAST_TICK = new HashMap<>();

    private static final class SpikeBase {
        final BlockPos base;
        final int maxHeight;
        SpikeBase(BlockPos base, int maxHeight) {
            this.base = base;
            this.maxHeight = maxHeight;
        }
    }

    private static final class SpikeCast {
        final UUID owner;
        final RegistryKey<World> worldKey;

        final Vec3d startXZ;
        final Vec3d targetXZ;

        final int travelTicks;
        final int seed;

        final int yHint;

        int age;
        boolean spawned;
        int currentHeight;
        int riseTick;

        final List<SpikeBase> spikes = new ArrayList<>();

        SpikeCast(UUID owner, RegistryKey<World> worldKey, Vec3d startXZ, Vec3d targetXZ,
                  int travelTicks, int seed, int yHint) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.startXZ = startXZ;
            this.targetXZ = targetXZ;
            this.travelTicks = travelTicks;
            this.seed = seed;
            this.yHint = yHint;

            this.age = 0;
            this.spawned = false;
            this.currentHeight = 0;
            this.riseTick = 0;
        }
    }

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        Vec3d look = player.getRotationVec(1.0f);

        Vec3d dirXZ = new Vec3d(look.x, 0.0, look.z);
        if (dirXZ.lengthSquared() < 1.0e-6) {
            Direction f = player.getHorizontalFacing();
            dirXZ = new Vec3d(f.getOffsetX(), 0.0, f.getOffsetZ());
        }
        dirXZ = dirXZ.normalize();

        Vec3d maxXZ = new Vec3d(player.getX(), 0.0, player.getZ()).add(dirXZ.multiply(SPIKES_RANGE));

        BlockHitResult bhr = raycastBlock(player, SPIKES_RANGE);

        Vec3d hitPos;
        int yHint;

        if (bhr.getType() == HitResult.Type.BLOCK && bhr.getSide() != Direction.DOWN) {
            hitPos = bhr.getPos();
            yHint = MathHelper.floor(hitPos.y);
        } else {
            hitPos = new Vec3d(maxXZ.x, player.getY(), maxXZ.z);
            yHint = player.getBlockY();
        }

        int tx = MathHelper.floor(hitPos.x);
        int tz = MathHelper.floor(hitPos.z);

        Vec3d start = new Vec3d(player.getX(), 0.0, player.getZ());
        Vec3d target = new Vec3d(tx + 0.5, 0.0, tz + 0.5);

        double dist = Math.sqrt(start.squaredDistanceTo(target));
        int travel = (int) Math.ceil(dist / SPIKES_TRAVEL_SPEED);
        travel = MathHelper.clamp(travel, SPIKES_TRAVEL_MIN_TICKS, SPIKES_TRAVEL_MAX_TICKS);

        int seed = (int) (w.getTime() ^ player.getUuid().getLeastSignificantBits());
        SPIKE_CASTS.add(new SpikeCast(player.getUuid(), w.getRegistryKey(), start, target, travel, seed, yHint));

        w.playSound(null, player.getBlockPos(), ModSounds.SPIKECAST,
                player.getSoundCategory(), 0.7f, 1.00f);
        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickSpikesWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();
        Long last = SPIKES_LAST_TICK.get(key);
        if (last != null && last == now) return;
        SPIKES_LAST_TICK.put(key, now);

        if (SPIKE_CASTS.isEmpty()) return;

        Iterator<SpikeCast> it = SPIKE_CASTS.iterator();
        while (it.hasNext()) {
            SpikeCast sc = it.next();

            if (!sc.worldKey.equals(key)) continue;

            if (sc.age < sc.travelTicks) {
                float t = sc.age / (float) sc.travelTicks;
                spawnGroundTrail(w, sc.startXZ, sc.targetXZ, t, sc.seed, sc.yHint);
                sc.age++;
                continue;
            }

            if (!sc.spawned) {
                buildSpikeBases(w, sc, sc.yHint);

                sc.currentHeight = 1;
                growAllSpikesToHeight(w, sc, sc.currentHeight);

                w.playSound(null, BlockPos.ofFloored(sc.targetXZ.x, w.getBottomY(), sc.targetXZ.z),
                        SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS,
                        0.9f, 1.2f);

                sc.spawned = true;
                sc.riseTick = 0;
                continue;
            }

            if (sc.currentHeight >= SPIKES_MAX_HEIGHT) {
                it.remove();
                continue;
            }

            sc.riseTick++;
            if (sc.riseTick < SPIKES_RISE_INTERVAL) continue;
            sc.riseTick = 0;

            sc.currentHeight++;
            growAllSpikesToHeight(w, sc, sc.currentHeight);

            w.playSound(null, BlockPos.ofFloored(sc.targetXZ.x, w.getBottomY(), sc.targetXZ.z),
                    SoundEvents.BLOCK_GLASS_HIT, SoundCategory.BLOCKS,
                    0.75f, 1.35f);
        }
    }

    private static void spawnGroundTrail(ServerWorld w, Vec3d startXZ, Vec3d targetXZ,
                                         float progress, int seed, int yHint) {

        double sx = startXZ.x;
        double sz = startXZ.z;
        double ex = targetXZ.x;
        double ez = targetXZ.z;

        double fx = MathHelper.lerp(progress, sx, ex);
        double fz = MathHelper.lerp(progress, sz, ez);

        double back = 0.18;
        double px0 = MathHelper.lerp(MathHelper.clamp(progress - back, 0.0f, 1.0f), sx, ex);
        double pz0 = MathHelper.lerp(MathHelper.clamp(progress - back, 0.0f, 1.0f), sz, ez);

        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double a = i / (double) steps;
            double x = MathHelper.lerp(a, px0, fx);
            double z = MathHelper.lerp(a, pz0, fz);

            int bx = MathHelper.floor(x);
            int bz = MathHelper.floor(z);

            BlockPos surf = findSurfaceAirAboveSolid(w, bx, bz, yHint, false); // do NOT freeze water for trail
            int y = surf.getY();

            double jx = ((seed * 31L + i * 17L) % 100) / 100.0 - 0.5;
            double jz = ((seed * 13L + i * 29L) % 100) / 100.0 - 0.5;

            w.spawnParticles(
                    SPIKE_TRAIL_DUST,
                    x + (jx * 0.08),
                    y + 0.06,
                    z + (jz * 0.08),
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );

            if ((i & 1) == 0) {
                w.spawnParticles(ParticleTypes.SNOWFLAKE, x, y + 0.08, z, 1, 0.05, 0.01, 0.05, 0.0);
            }
        }
    }

    private static void buildSpikeBases(ServerWorld w, SpikeCast sc, int yHint) {
        Random rand = new Random(sc.seed);

        int cx = MathHelper.floor(sc.targetXZ.x);
        int cz = MathHelper.floor(sc.targetXZ.z);

        HashSet<Long> used = new HashSet<>();

        BlockPos centerBase = findBestSpikeBase(w, cx, cz, SPIKES_TERRAIN_SEARCH, yHint);
        if (centerBase != null) {
            sc.spikes.add(new SpikeBase(centerBase, pickSpikeHeight(rand)));
            used.add((((long) centerBase.getX()) << 32) ^ (centerBase.getZ() & 0xffffffffL));
        }

        int attempts = SPIKES_COUNT * 4;
        for (int a = 0; a < attempts && sc.spikes.size() < SPIKES_COUNT; a++) {
            int dx = (rand.nextInt(3) - 1) + (rand.nextInt(3) - 1);
            int dz = (rand.nextInt(3) - 1) + (rand.nextInt(3) - 1);

            dx = MathHelper.clamp(dx, -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);
            dz = MathHelper.clamp(dz, -SPIKES_SPREAD_RADIUS, SPIKES_SPREAD_RADIUS);

            int x = cx + dx;
            int z = cz + dz;

            BlockPos base = findBestSpikeBase(w, x, z, SPIKES_TERRAIN_SEARCH, yHint);
            if (base == null) continue;

            long key = (((long) base.getX()) << 32) ^ (base.getZ() & 0xffffffffL);
            if (!used.add(key)) continue;

            sc.spikes.add(new SpikeBase(base, pickSpikeHeight(rand)));
        }

        if (sc.spikes.isEmpty()) {
            BlockPos fallback = findSurfaceAirAboveSolid(w, cx, cz, yHint, true);
            sc.spikes.add(new SpikeBase(fallback, SPIKES_MAX_HEIGHT));
        }
    }

    private static int pickSpikeHeight(Random rand) {
        float r = rand.nextFloat();
        if (r < 0.60f) return 3;
        if (r < 0.88f) return 2;
        return 1;
    }

    private static BlockPos findBestSpikeBase(ServerWorld w, int x, int z, int searchR, int yHint) {
        BlockPos best = null;
        int bestD2 = Integer.MAX_VALUE;

        for (int dx = -searchR; dx <= searchR; dx++) {
            for (int dz = -searchR; dz <= searchR; dz++) {
                int xx = x + dx;
                int zz = z + dz;

                BlockPos candidate = findSurfaceAirAboveSolid(w, xx, zz, yHint, true); // freeze water surface for spike bases
                if (candidate == null) continue;

                int d2 = dx * dx + dz * dz;
                if (d2 < bestD2) {
                    bestD2 = d2;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private static BlockPos findSurfaceAirAboveSolid(ServerWorld w, int x, int z, int yHint) {
        return findSurfaceAirAboveSolid(w, x, z, yHint, false);
    }

    /**
     * Highly optimized ground-finder that prevents tunneling through the earth
     * when checking over blocks like slabs or snow layers.
     */
    private static BlockPos findSurfaceAirAboveSolid(ServerWorld w, int x, int z, int yHint, boolean freezeWaterSurface) {
        int startY = MathHelper.clamp(yHint + 3, w.getBottomY() + 2, w.getTopY() - 2);
        BlockPos.Mutable m = new BlockPos.Mutable(x, startY, z);

        boolean startedInSolid = false;
        BlockState initial = w.getBlockState(m);
        if (!initial.getFluidState().isEmpty() || (!initial.isAir() && !initial.getCollisionShape(w, m).isEmpty())) {
            startedInSolid = true;
        }

        boolean foundAir = !startedInSolid;

        for (int i = 0; i < 80 && m.getY() > w.getBottomY() + 2; i++) {
            BlockState hereState = w.getBlockState(m);
            BlockPos below = m.down();
            BlockState belowState = w.getBlockState(below);

            boolean hereIsFluid = !hereState.getFluidState().isEmpty();
            boolean hereHasCollision = !hereState.isAir() && !hereState.getCollisionShape(w, m).isEmpty();
            boolean hereOk = !hereIsFluid && !hereHasCollision;

            if (!foundAir) {
                // If we started inside a block (e.g. cave ceiling), wait until we pop out into the air
                if (hereOk) {
                    foundAir = true;
                }
            }

            if (foundAir) {
                if (!hereOk) {
                    // We hit a solid/fluid block after falling through the air. Stop immediately so we don't tunnel!
                    boolean groundWaterOk = freezeWaterSurface && isStillWater(hereState);
                    if (groundWaterOk) {
                        freezeStillWaterToFrostedIce(w, m);
                    }
                    return m.up().toImmutable();
                }

                // We are in air. Check if block below is a solid floor.
                boolean belowSolidOk = belowState.isSideSolidFullSquare(w, below, Direction.UP);
                boolean belowWaterOk = freezeWaterSurface && isStillWater(belowState);

                if (belowSolidOk || belowWaterOk) {
                    if (belowWaterOk) {
                        freezeStillWaterToFrostedIce(w, below);
                    }
                    return m.toImmutable();
                }
            }

            m.move(Direction.DOWN);
        }

        // Fallback if no valid air->ground transition was found in 80 blocks
        if (foundAir) {
            int top = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = Math.min(top, startY);
            BlockPos fallback = new BlockPos(x, y, z);
            if (freezeWaterSurface && isStillWater(w.getBlockState(fallback.down()))) {
                freezeStillWaterToFrostedIce(w, fallback.down());
            }
            return fallback;
        } else {
            // Trapped completely in solid rock
            return new BlockPos(x, yHint, z);
        }
    }

    private static boolean isStillWater(BlockState s) {
        return s.getFluidState().isOf(Fluids.WATER) && s.getFluidState().isStill();
    }

    private static void freezeStillWaterToFrostedIce(ServerWorld w, BlockPos pos) {
        // only freeze if it is water source right now
        BlockState s = w.getBlockState(pos);
        if (!isStillWater(s)) return;

        w.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState(), 2);
        // frosted ice melts naturally
        w.scheduleBlockTick(pos, Blocks.FROSTED_ICE, MathHelper.nextInt(w.random, 60, 120));

        w.spawnParticles(ParticleTypes.SNOWFLAKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                6, 0.22, 0.10, 0.22, 0.0);
    }

    private static void growAllSpikesToHeight(ServerWorld w, SpikeCast sc, int height) {
        for (SpikeBase sb : sc.spikes) {
            int desired = Math.min(height, sb.maxHeight);
            if (desired <= 0) continue;

            setSpikeColumnHeight(w, sb.base, desired);

            if (desired != height) continue;

            BlockPos top = sb.base.up(desired - 1);

            w.spawnParticles(
                    SPIKE_PUFF_DUST,
                    top.getX() + 0.5, top.getY() + 0.35, top.getZ() + 0.5,
                    18,
                    0.22, 0.25, 0.22,
                    0.02
            );
            w.spawnParticles(ParticleTypes.SNOWFLAKE,
                    top.getX() + 0.5, top.getY() + 0.45, top.getZ() + 0.5,
                    10, 0.25, 0.20, 0.25, 0.0
            );

            hitEntitiesForSpikeStep(w, sc.owner, top);
        }
    }

    private static void setSpikeColumnHeight(ServerWorld w, BlockPos base, int height) {
        for (int i = 0; i < height; i++) {
            BlockPos p = base.up(i);
            if (!w.getBlockState(p).getFluidState().isEmpty()) return;
        }

        for (int i = 0; i < height; i++) {
            BlockPos p = base.up(i);
            BlockState existing = w.getBlockState(p);

            boolean replaceable = existing.isAir()
                    || (existing.getCollisionShape(w, p).isEmpty() && existing.getFluidState().isEmpty());

            if (!replaceable && !existing.isOf(ModBlocks.ICE_SPIKE)) {
                return;
            }
        }

        if (height == 1) {
            setSpikeState(w, base, Thickness.TIP);
            return;
        }

        if (height == 2) {
            setSpikeState(w, base, Thickness.FRUSTUM);
            setSpikeState(w, base.up(1), Thickness.TIP);
            return;
        }

        setSpikeState(w, base, Thickness.BASE);
        setSpikeState(w, base.up(1), Thickness.FRUSTUM);
        setSpikeState(w, base.up(2), Thickness.TIP);
    }

    private static void setSpikeState(ServerWorld w, BlockPos pos, Thickness th) {
        BlockState s = ModBlocks.ICE_SPIKE.getDefaultState()
                .with(Properties.VERTICAL_DIRECTION, Direction.UP)
                .with(Properties.THICKNESS, th);

        w.setBlockState(pos, s, 2);
    }

    private static void hitEntitiesForSpikeStep(ServerWorld w, UUID owner, BlockPos top) {
        Entity ownerEnt = w.getEntity(owner);
        ServerPlayerEntity caster = (ownerEnt instanceof ServerPlayerEntity sp) ? sp : null;
        if (caster == null) return;

        Box box = new Box(top).expand(0.9, 1.6, 0.9);
        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        int nowTick = (int) (w.getTime() & 0x7fffffff);

        for (LivingEntity e : hits) {
            IceVictimState state = getVictimState(e);
            if (state.lastSpikeHitTick == nowTick) continue;
            state.lastSpikeHitTick = nowTick;

            // pass attribution
            e.damage(ModDamageTypes.iceSpike(w, caster), SPIKES_DAMAGE);

            Vec3d v = e.getVelocity();
            double newY = Math.min(SPIKES_MAX_Y_VEL, Math.max(v.y, SPIKES_KNOCKUP_Y));
            e.setVelocity(v.x, newY, v.z);
            e.velocityModified = true;
            e.fallDistance = 0.0f;

            if (!tryShatter(caster, e)) {
                applyFreezePoints(caster, e, SPIKES_FREEZE_STACKS);
            }
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final long SECONDARY_COOLDOWN_MS = 25_000;

    private static final int BEAM_CHARGE_TICKS = 22;
    private static final int BEAM_FIRE_TICKS   = 125;

    private static final double BEAM_RANGE = 34.0;
    private static final double BEAM_WIDTH = 0.75;

    private static final int BEAM_APPLY_EVERY = 2;
    private static final int BEAM_STACKS_PER_APPLY = 5;
    private static final int BEAM_SLOW_AMP = 1;
    private static final int BEAM_SLOW_TICKS = 12;

    private static final int BEAM_DAMAGE_EVERY = 2;
    private static final float BEAM_DAMAGE = 0.75f;

    private static final int BEAM_PARTICLE_DENSITY = 10;
    private static final double BEAM_SPIRAL_RADIUS = 0.15;

    // Beam collision + terrain effects
    private static final double BEAM_BLOCK_EPS = 0.12;     // pull beam endpoint out of the block a bit
    private static final int BEAM_SNOW_PLACE_EVERY = 2;    // how often we try to leave snow at floor impact
    private static final int BEAM_WATER_FREEZE_EVERY = 2;  // how often we try to freeze water along the beam
    private static final int BEAM_MAX_WATER_FREEZES_PER_TICK = 6;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        IceCasterState state = getCasterState(player);
        if (state.beamChargeTicks > 0) return;
        if (state.beamFireTicks > 0) return;

        state.beamChargeTicks = BEAM_CHARGE_TICKS;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                ModSounds.ICEBEAMCHARGE,
                player.getSoundCategory(),
                0.8f, 1.00f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBeam(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        IceCasterState state = getCasterState(player);

        // CHARGING
        if (state.beamChargeTicks > 0) {
            state.beamChargeTicks--;

            Vec3d v = player.getVelocity();
            player.setVelocity(0.0, v.y, 0.0);
            player.velocityModified = true;
            player.setSprinting(false);

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 4, true, false));
            spawnBeamChargeParticles(w, player);

            if (state.beamChargeTicks <= 0) {
                state.beamFireTicks = BEAM_FIRE_TICKS;

                w.playSound(null, player.getBlockPos(),
                        ModSounds.ICEBEAMLOOP,
                        player.getSoundCategory(),
                        0.9f, 1.55f);
            }
            return;
        }

        // FIRING
        if (state.beamFireTicks > 0) {
            state.beamFireTicks--;

            Vec3d v = player.getVelocity();
            player.setVelocity(0.0, v.y, 0.0);
            player.velocityModified = true;
            player.setSprinting(false);

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 4, 2, true, false));

            Vec3d dir = player.getRotationVec(1.0f).normalize();
            Vec3d muzzle = getBeamMuzzlePos(player, dir);
            Vec3d maxEnd = muzzle.add(dir.multiply(BEAM_RANGE));

            // block collision
            BlockHitResult blockHit = raycastBlocks(w, player, muzzle, maxEnd);
            Vec3d end = (blockHit.getType() == HitResult.Type.BLOCK)
                    ? blockHit.getPos().subtract(dir.multiply(BEAM_BLOCK_EPS))
                    : maxEnd;

            // particles
            spawnBeamLineParticles(w, muzzle, end);

            // leave snow
            if (blockHit.getType() == HitResult.Type.BLOCK && (player.age % BEAM_SNOW_PLACE_EVERY) == 0) {
                tryLeaveSnowAtBeamImpact(w, blockHit);
            }

            // freeze water
            if ((player.age % BEAM_WATER_FREEZE_EVERY) == 0) {
                freezeWaterAlongBeam(w, muzzle, end, BEAM_MAX_WATER_FREEZES_PER_TICK);
            }

            // apply effects
            if ((player.age % BEAM_APPLY_EVERY) == 0) {
                applyBeamToEntities(w, player, muzzle, end);
            }

            // play sounds
            if ((player.age % 15) == 0) {
                w.playSound(null, player.getBlockPos(),
                        ModSounds.ICEBEAMLOOP,
                        player.getSoundCategory(),
                        0.9f, 1.05f);
            }

            // DPS
            if ((player.age % BEAM_DAMAGE_EVERY) == 0) {
                applyBeamDamage(w, player, muzzle, end);
            }

            if (state.beamFireTicks <= 0) {
                w.playSound(null, player.getBlockPos(),
                        SoundEvents.BLOCK_GLASS_HIT,
                        player.getSoundCategory(),
                        0.65f, 1.8f);
            }
        }
    }

    private static BlockHitResult raycastBlocks(ServerWorld w, Entity caster, Vec3d start, Vec3d end) {
        return w.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                caster
        ));
    }

    private static void tryLeaveSnowAtBeamImpact(ServerWorld w, BlockHitResult hit) {
        // “colliding with floor” means we hit the UP face of a block (top surface).
        if (hit.getSide() != Direction.UP) return;

        BlockPos ground = hit.getBlockPos();
        BlockPos place = ground.up();

        // Only place snow if it's air and the block below can support it
        BlockState below = w.getBlockState(ground);
        BlockState at = w.getBlockState(place);

        boolean placeOk = at.isAir() && below.isSideSolidFullSquare(w, ground, Direction.UP);
        if (!placeOk) return;

        w.setBlockState(place, Blocks.SNOW.getDefaultState(), 2);

        // tiny puff so it feels responsive
        w.spawnParticles(ParticleTypes.SNOWFLAKE,
                place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5,
                8, 0.25, 0.03, 0.25, 0.0);
    }

    private static void freezeWaterAlongBeam(ServerWorld w, Vec3d start, Vec3d end, int max) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.25) return;

        Vec3d dir = delta.multiply(1.0 / len);

        // sample roughly every ~0.75 blocks
        double step = 0.75;
        int samples = MathHelper.clamp((int) (len / step), 3, 40);

        int froze = 0;
        BlockPos.Mutable m = new BlockPos.Mutable();

        for (int i = 0; i <= samples; i++) {
            if (froze >= max) break;

            Vec3d p = start.add(dir.multiply(i * step));
            m.set(MathHelper.floor(p.x), MathHelper.floor(p.y), MathHelper.floor(p.z));

            // check the block we are inside + a tiny neighborhood (helps “touching” water)
            for (int dx = -1; dx <= 1 && froze < max; dx++) {
                for (int dy = -1; dy <= 1 && froze < max; dy++) {
                    for (int dz = -1; dz <= 1 && froze < max; dz++) {
                        BlockPos pos = m.add(dx, dy, dz);
                        BlockState s = w.getBlockState(pos);

                        // “solid water blocks” = water source blocks (still)
                        if (s.getFluidState().isOf(Fluids.WATER) && s.getFluidState().isStill()) {
                            // use frosted ice (like frost walker) so it melts back naturally
                            w.setBlockState(pos, Blocks.FROSTED_ICE.getDefaultState(), 2);
                            w.scheduleBlockTick(pos, Blocks.FROSTED_ICE, MathHelper.nextInt(w.random, 60, 120));

                            w.spawnParticles(ParticleTypes.SNOWFLAKE,
                                    pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5,
                                    6, 0.25, 0.15, 0.25, 0.0);

                            froze++;
                        }
                    }
                }
            }
        }
    }

    private static void spawnBeamChargeParticles(ServerWorld w, ServerPlayerEntity player) {
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d muzzle = getBeamMuzzlePos(player, dir);

        int points = 14;
        double r = 0.22;

        for (int i = 0; i < points; i++) {
            double a = (player.age * 0.45) + (i * (Math.PI * 2.0 / points));
            double x = muzzle.x + Math.cos(a) * r;
            double z = muzzle.z + Math.sin(a) * r;
            double y = muzzle.y + (w.random.nextDouble() - 0.5) * 0.12;

            w.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0.0);

            if ((i % 3) == 0) {
                w.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if ((i & 1) == 0) {
                w.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void spawnBeamLineParticles(ServerWorld w, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.001) return;

        Vec3d dir = delta.multiply(1.0 / len);

        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = up.crossProduct(dir);
        if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        Vec3d up2 = dir.crossProduct(right).normalize();

        int steps = MathHelper.clamp((int) (len * BEAM_PARTICLE_DENSITY), 24, 140);

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Vec3d p = start.add(delta.multiply(t));

            // core shimmer line
            w.spawnParticles(ParticleTypes.WHITE_ASH, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);

            // spiral around beam
            double ang = (w.getTime() * 0.45) + (i * 0.65);
            double rx = Math.cos(ang) * BEAM_SPIRAL_RADIUS;
            double ry = Math.sin(ang) * BEAM_SPIRAL_RADIUS;

            Vec3d swirl = p.add(right.multiply(rx)).add(up2.multiply(ry));
            w.spawnParticles(ParticleTypes.SNOWFLAKE, swirl.x, swirl.y, swirl.z, 1, 0.0, 0.0, 0.0, 0.0);

            // sparkles
            if ((i % 10) == 0) {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            if ((i % 14) == 0) {
                w.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void applyBeamToEntities(ServerWorld w, ServerPlayerEntity caster, Vec3d start, Vec3d end) {
        Box scan = new Box(start, end).expand(BEAM_WIDTH, BEAM_WIDTH, BEAM_WIDTH);

        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, scan,
                e -> e.isAlive() && e != caster);

        double w2 = BEAM_WIDTH * BEAM_WIDTH;

        for (LivingEntity e : hits) {
            Vec3d p = e.getPos().add(0, e.getHeight() * 0.5, 0);
            double d2 = distSqPointToSegment(p, start, end);
            if (d2 > w2) continue;

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, BEAM_SLOW_TICKS, BEAM_SLOW_AMP, true, false));
            applyBeamDrag(e);

            applyFreezePoints(caster, e, BEAM_STACKS_PER_APPLY);
        }
    }

    private static void applyBeamDamage(ServerWorld w, ServerPlayerEntity caster, Vec3d start, Vec3d end) {
        Box scan = new Box(start, end).expand(BEAM_WIDTH, BEAM_WIDTH, BEAM_WIDTH);

        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, scan,
                e -> e.isAlive() && e != caster);

        double w2 = BEAM_WIDTH * BEAM_WIDTH;

        for (LivingEntity e : hits) {
            Vec3d p = e.getPos().add(0, e.getHeight() * 0.5, 0);
            double d2 = distSqPointToSegment(p, start, end);
            if (d2 > w2) continue;

            e.damage(ModDamageTypes.iceBeam(w, caster), BEAM_DAMAGE);
        }
    }

    private static void applyBeamDrag(LivingEntity e) {
        Vec3d v = e.getVelocity();

        double nx = v.x * 0.20;
        double nz = v.z * 0.20;

        double ny = v.y;
        if (ny > 0.12) ny = 0.12;
        ny -= 0.08;
        ny = Math.max(ny, -0.65);

        e.setVelocity(nx, ny, nz);
        e.velocityModified = true;
        e.fallDistance = 0.0f;
    }

    private static Vec3d getBeamMuzzlePos(ServerPlayerEntity player, Vec3d dir) {
        Vec3d eye = player.getEyePos();

        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = up.crossProduct(dir);
        if (right.lengthSquared() < 1.0e-6) right = new Vec3d(1, 0, 0);
        right = right.normalize();

        double side = (player.getMainArm() == net.minecraft.util.Arm.RIGHT) ? 0.24 : -0.24;

        return eye
                .add(dir.multiply(0.30))
                .add(0.0, -0.28, 0.0)
                .add(right.multiply(side));
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    private static final long ULT_COOLDOWN_MS = 340_000;

    private static final int ULT_DURATION_TICKS = 180;

    private static final double ULT_BLIZZARD_RADIUS = 11.0;
    private static final int ULT_SNOW_PER_TICK = 50;
    private static final int ULT_GUST_PER_TICK = 30;

    // Water freeze
    private static final int ULT_FROST_RADIUS = 4;
    private static final int ULT_FROST_EVERY = 2; // do it every 2 ticks to reduce cost
    private static final int ULT_FROST_MAX_PER_TICK = 10;

    // Ground snow
    private static final int ULT_SNOW_COVER_EVERY = 5;          // try every tick
    private static final int ULT_SNOW_COVER_ATTEMPTS = 45;      // how many random placements per tick
    private static final int ULT_SNOW_MAX_LAYERS = 2;           // vanilla max

    // Shockwave
    private static final int ULT_WAVE_INTERVAL = 16;
    private static final double ULT_WAVE_SPEED = 1.45;
    private static final double ULT_WAVE_MAX_RADIUS = 14.0;
    private static final double ULT_WAVE_THICKNESS = 0.80;

    private static final double ULT_WAVE_HEIGHT_OFFSET = 0.10;
    private static final double ULT_WAVE_JUMP_CLEARANCE = 0.55;

    private static final float ULT_WAVE_DAMAGE = 5.5f;
    private static final double ULT_WAVE_KB = 0.25;
    private static final double ULT_WAVE_UP = 0.07;
    private static final int ULT_WAVE_FREEZE_STACKS = 35;
    private static final int ULT_WAVE_SLOW_TICKS = 20;
    private static final int ULT_WAVE_SLOW_AMP = 1;

    // egg
    private static final double ULT_SNOWMAN_CHANCE = 0.002; // chance per tick

    // Extra FX
    private static final DustParticleEffect ULT_BLUE_DUST =
            new DustParticleEffect(new Vector3f(0.20f, 0.55f, 1.00f), 0.90f);

    private static final DustParticleEffect ULT_SHIMMER_DUST =
            new DustParticleEffect(new Vector3f(0.70f, 0.95f, 1.00f), 0.55f);

    private static final List<UltWave> ULT_WAVES = new ArrayList<>();
    private static final Map<RegistryKey<World>, Long> ULTW_LAST_TICK = new HashMap<>();
    private static int ULT_WAVE_ID_SEQ = 1;

    private static int nextUltWaveId() {
        int id = ULT_WAVE_ID_SEQ++;
        if (ULT_WAVE_ID_SEQ > 2_000_000_000) ULT_WAVE_ID_SEQ = 1;
        return id;
    }

    private static final class UltWave {
        final int id;
        final UUID owner;
        final RegistryKey<World> worldKey;

        final Vec3d centerXZ;
        final double waveY;
        double radius;
        final double maxRadius;

        UltWave(int id, UUID owner, RegistryKey<World> worldKey, Vec3d centerXZ, double waveY, double radius, double maxRadius) {
            this.id = id;
            this.owner = owner;
            this.worldKey = worldKey;
            this.centerXZ = centerXZ;
            this.waveY = waveY;
            this.radius = radius;
            this.maxRadius = maxRadius;
        }
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        IceCasterState state = getCasterState(player);
        state.ultActiveTicks = ULT_DURATION_TICKS;
        state.ultPulseTicks = 1;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE,
                player.getSoundCategory(),
                0.9f, 0.75f);
        playBlizzardLoop(w, player);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickUltimate(ServerPlayerEntity player) {
        IceCasterState state = getCasterState(player);
        if (state.ultActiveTicks <= 0) return;
        state.ultActiveTicks--;

        ServerWorld w = player.getServerWorld();

        // 1. Blizzard Global Effects
        spawnBlizzard(w, player);

        // 2. Entity effects
        spawnSnowAroundEntities(w, player);

        // 3. Ground snow accumulation
        if ((player.age % ULT_SNOW_COVER_EVERY) == 0) {
            spreadSnowCover(w, player, (int)Math.ceil(ULT_BLIZZARD_RADIUS), ULT_SNOW_COVER_ATTEMPTS);
        }

        // 4. Random Snowman Building
        if (w.random.nextDouble() < ULT_SNOWMAN_CHANCE) {
            tryStartSnowmanBuild(w, player, state);
        }
        tickActiveSnowmanBuilds(w, state);

        // 5. Water freeze around caster
        if ((player.age % ULT_FROST_EVERY) == 0) {
            freezeWaterAroundCaster(w, player, ULT_FROST_RADIUS, ULT_FROST_MAX_PER_TICK);
        }

        // 6. Pulse Waves & Loop Sound
        if (state.ultPulseTicks > 0) {
            state.ultPulseTicks--;
            if (state.ultPulseTicks <= 0) {
                state.ultPulseTicks = ULT_WAVE_INTERVAL;
                spawnUltWave(w, player);
                w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, player.getSoundCategory(), 0.8f, 0.85f);
            }
        }

        if (player.age % 30 == 0) {
            playBlizzardLoop(w, player);
        }
        tickUltWavesWorld(w);
    }

    private static void freezeWaterAroundCaster(ServerWorld w, ServerPlayerEntity caster, int radius, int maxPerTick) {
        BlockPos center = caster.getBlockPos();
        int froze = 0;

        // sample a square, but early-exit
        for (int dx = -radius; dx <= radius && froze < maxPerTick; dx++) {
            for (int dz = -radius; dz <= radius && froze < maxPerTick; dz++) {
                // circular-ish
                if ((dx * dx + dz * dz) > radius * radius) continue;

                BlockPos pos = center.add(dx, 0, dz);

                // Frost Walker targets water at feet level (usually just below)
                // Try y-1..y+1 to be forgiving on slopes
                for (int dy = -1; dy <= 1 && froze < maxPerTick; dy++) {
                    BlockPos p = pos.add(0, dy, 0);

                    BlockState s = w.getBlockState(p);
                    if (s.getFluidState().isOf(Fluids.WATER) && s.getFluidState().isStill()) {
                        w.setBlockState(p, Blocks.FROSTED_ICE.getDefaultState(), 2);
                        w.scheduleBlockTick(p, Blocks.FROSTED_ICE, MathHelper.nextInt(w.random, 60, 120));

                        // tiny sparkle
                        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                                4, 0.20, 0.10, 0.20, 0.0);
                        if ((froze & 1) == 0) {
                            w.spawnParticles(ParticleTypes.ENCHANT, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5,
                                    1, 0.02, 0.02, 0.02, 0.0);
                        }

                        froze++;
                    }
                }
            }
        }
    }

    private static void spawnBlizzard(ServerWorld w, ServerPlayerEntity caster) {
        Vec3d c = caster.getPos();

        for (int i = 0; i < ULT_SNOW_PER_TICK; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * ULT_BLIZZARD_RADIUS;
            double a = w.random.nextDouble() * Math.PI * 2.0;

            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 6.0 + w.random.nextDouble() * 3.0;

            w.spawnParticles(ParticleTypes.SNOWFLAKE,
                    x, y, z,
                    1,
                    0.25, 0.15, 0.25,
                    0.00);

            // extra shimmer sometimes
            if ((i % 12) == 0) {
                w.spawnParticles(ParticleTypes.END_ROD, x, y - 0.4, z, 1, 0, 0, 0, 0.0);
            }
        }

        for (int i = 0; i < ULT_GUST_PER_TICK; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * (ULT_BLIZZARD_RADIUS * 0.9);
            double a = w.random.nextDouble() * Math.PI * 2.0;

            double x = c.x + Math.cos(a) * r;
            double z = c.z + Math.sin(a) * r;
            double y = c.y + 1.0 + w.random.nextDouble() * 2.0;

            w.spawnParticles(ParticleTypes.WHITE_ASH, x, y, z, 1, 0.22, 0.18, 0.22, 0.00);

            // blue “cold” tint in the air
            if ((i & 3) == 0) {
                w.spawnParticles(ULT_BLUE_DUST, x, y, z, 1, 0.10, 0.08, 0.10, 0.0);
            }
        }
    }

    private static void spreadSnowCover(ServerWorld w, ServerPlayerEntity caster, int radius, int attempts) {
        Vec3d c = caster.getPos();

        for (int i = 0; i < attempts; i++) {
            double r = Math.sqrt(w.random.nextDouble()) * radius;
            double a = w.random.nextDouble() * (Math.PI * 2.0);

            int x = MathHelper.floor(c.x + Math.cos(a) * r);
            int z = MathHelper.floor(c.z + Math.sin(a) * r);

            BlockPos place = findSurfaceAirAboveSolid(w, x, z, caster.getBlockY(), true);

            if (!w.isInBuildLimit(place)) continue;

            BlockPos belowPos = place.down();
            BlockState below = w.getBlockState(belowPos);
            BlockState at = w.getBlockState(place);

            if (!below.isSideSolidFullSquare(w, belowPos, Direction.UP)) continue;

            if (at.isOf(Blocks.SNOW)) {
                int layers = at.get(SnowBlock.LAYERS);
                if (layers < ULT_SNOW_MAX_LAYERS) {
                    w.setBlockState(place, at.with(SnowBlock.LAYERS, layers + 1), 2);
                }
                continue;
            }

            // now only target air
            if (!at.isAir()) continue;

            w.setBlockState(place, Blocks.SNOW.getDefaultState(), 2);

            if ((i % 10) == 0) {
                w.spawnParticles(ParticleTypes.SNOWFLAKE,
                        place.getX() + 0.5, place.getY() + 0.05, place.getZ() + 0.5,
                        6, 0.22, 0.03, 0.22, 0.0);
            }
        }
    }

    private static void spawnUltWave(ServerWorld w, ServerPlayerEntity caster) {
        // anchored to ground
        BlockPos groundAir = findSurfaceAirAboveSolid(w, caster.getBlockX(), caster.getBlockZ(), caster.getBlockY(), true);
        double waveY = groundAir.getY() + ULT_WAVE_HEIGHT_OFFSET;

        UltWave wave = new UltWave(
                nextUltWaveId(),
                caster.getUuid(),
                w.getRegistryKey(),
                new Vec3d(caster.getX(), 0.0, caster.getZ()),
                waveY,
                0.8,
                ULT_WAVE_MAX_RADIUS
        );
        ULT_WAVES.add(wave);
    }

    private static void tickUltWavesWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();

        Long last = ULTW_LAST_TICK.get(key);
        if (last != null && last == now) return;
        ULTW_LAST_TICK.put(key, now);

        if (ULT_WAVES.isEmpty()) return;

        Iterator<UltWave> it = ULT_WAVES.iterator();
        while (it.hasNext()) {
            UltWave wave = it.next();
            if (!wave.worldKey.equals(key)) continue;

            wave.radius += ULT_WAVE_SPEED;

            spawnWaveRingParticles(w, wave);
            applyWaveHits(w, wave);

            if (wave.radius >= wave.maxRadius) {
                it.remove();
            }
        }
    }

    private static void spawnWaveRingParticles(ServerWorld w, UltWave wave) {
        double r = wave.radius;
        int points = MathHelper.clamp((int) (r * 14.0), 28, 180);

        double y = wave.waveY;

        for (int i = 0; i < points; i++) {
            double a = (i / (double) points) * (Math.PI * 2.0);

            double x = wave.centerXZ.x + Math.cos(a) * r;
            double z = wave.centerXZ.z + Math.sin(a) * r;

            w.spawnParticles(ParticleTypes.SNOWFLAKE, x, y, z, 1, 0.02, 0.01, 0.02, 0.0);

            // dust
            if ((i % 5) == 0) {
                w.spawnParticles(ULT_BLUE_DUST, x, y + 0.02, z, 1, 0.02, 0.01, 0.02, 0.0);
            }
            if ((i % 9) == 0) {
                w.spawnParticles(ULT_SHIMMER_DUST, x, y + 0.06, z, 1, 0.02, 0.01, 0.02, 0.0);
            }

            if ((i % 8) == 0) {
                w.spawnParticles(ParticleTypes.END_ROD, x, y + 0.05, z, 1, 0, 0, 0, 0.0);
            }
            if ((i % 11) == 0) {
                w.spawnParticles(ParticleTypes.ENCHANT, x, y + 0.08, z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void applyWaveHits(ServerWorld w, UltWave wave) {
        Vec3d c = wave.centerXZ;
        double scanR = wave.radius + ULT_WAVE_THICKNESS + 1.25;

        Box scan = new Box(c.x - scanR, wave.waveY - 3.0, c.z - scanR,
                c.x + scanR, wave.waveY + 4.0, c.z + scanR);

        // Added check to ignore Snow Golems in the scan filter
        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, scan,
                e -> e.isAlive() &&
                        !e.getUuid().equals(wave.owner) &&
                        !(e instanceof net.minecraft.entity.passive.SnowGolemEntity));

        for (LivingEntity e : hits) {
            double eTop = e.getY() + e.getHeight();
            if (eTop < wave.waveY - 0.10) continue;
            if (e.getY() > wave.waveY + ULT_WAVE_JUMP_CLEARANCE) continue;

            Vec3d p = e.getPos();
            double dx = p.x - c.x;
            double dz = p.z - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);

            if (Math.abs(dist - wave.radius) > ULT_WAVE_THICKNESS) continue;

            IceVictimState state = getVictimState(e);
            if (state.lastWaveHitId == wave.id) continue;
            state.lastWaveHitId = wave.id;

            Entity ownerEnt = w.getEntity(wave.owner);
            ServerPlayerEntity caster = (ownerEnt instanceof ServerPlayerEntity sp) ? sp : null;

            if (caster != null) {
                e.damage(ModDamageTypes.iceShockwave(w, caster), ULT_WAVE_DAMAGE);
                if (!tryShatter(caster, e)) {
                    applyFreezePoints(caster, e, ULT_WAVE_FREEZE_STACKS);
                }
            }

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ULT_WAVE_SLOW_TICKS, ULT_WAVE_SLOW_AMP, true, false));

            Vec3d v = e.getVelocity();
            double inv = (dist < 1.0e-4) ? 0.0 : (1.0 / dist);
            double pushX = dx * inv * ULT_WAVE_KB;
            double pushZ = dz * inv * ULT_WAVE_KB;
            double newY = Math.min(0.55, Math.max(v.y, ULT_WAVE_UP));
            e.setVelocity(v.x + pushX, newY, v.z + pushZ);
            e.velocityModified = true;
            e.fallDistance = 0.0f;
        }
    }

    private static void playBlizzardLoop(ServerWorld world, ServerPlayerEntity caster) {
        double radius = ULT_BLIZZARD_RADIUS;

        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(caster) <= radius * radius) {

                world.playSound(
                        null, // send to all nearby (including the player)
                        p.getBlockPos(),
                        ModSounds.BLIZZARDLOOP,
                        p.getSoundCategory(),
                        0.3f,
                        0.8f
                );
            }
        }
    }

    private static void spawnSnowAroundEntities(ServerWorld w, ServerPlayerEntity caster) {
        Box area = caster.getBoundingBox().expand(ULT_BLIZZARD_RADIUS);
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, area, e -> e.isAlive() && e != caster);

        for (LivingEntity e : targets) {
            Vec3d p = e.getPos();
            // Blowing gusts swirling around the entity
            for (int i = 0; i < 3; i++) {
                double ox = (w.random.nextDouble() - 0.5) * 1.5;
                double oz = (w.random.nextDouble() - 0.5) * 1.5;
                double oy = w.random.nextDouble() * e.getHeight();

                // Wind-blown particles with horizontal velocity
                w.spawnParticles(ParticleTypes.SNOWFLAKE, p.x + ox, p.y + oy, p.z + oz, 1, 0.5, 0.1, 0.5, 0.05);

                if (w.random.nextBoolean()) {
                    w.spawnParticles(ParticleTypes.WHITE_ASH, p.x + ox, p.y + oy, p.z + oz, 1, 0.2, 0.0, 0.2, 0.02);
                }
            }
        }
    }

    // EGG
    private static void tryStartSnowmanBuild(ServerWorld w, ServerPlayerEntity caster, IceCasterState state) {
        double r = w.random.nextDouble() * (ULT_BLIZZARD_RADIUS - 2);
        double a = w.random.nextDouble() * Math.PI * 2.0;
        int x = MathHelper.floor(caster.getX() + Math.cos(a) * r);
        int z = MathHelper.floor(caster.getZ() + Math.sin(a) * r);

        BlockPos pos = findSurfaceAirAboveSolid(w, x, z, caster.getBlockY(), true);
        if (pos != null && w.getBlockState(pos).isAir()) {
            state.snowmen.add(new SnowmanBuild(pos));
        }
    }

    private static void tickActiveSnowmanBuilds(ServerWorld w, IceCasterState state) {
        Iterator<SnowmanBuild> it = state.snowmen.iterator();

        while (it.hasNext()) {
            SnowmanBuild build = it.next();
            build.delay--;

            if (build.delay <= 0) {
                if (build.step == 1) { // Place bottom snow
                    w.setBlockState(build.pos, Blocks.SNOW_BLOCK.getDefaultState());
                    w.playSound(null, build.pos, SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.BLOCKS, 1f, 1f);
                    build.step = 2;
                    build.delay = 15;
                } else if (build.step == 2) { // Place top snow
                    w.setBlockState(build.pos.up(), Blocks.SNOW_BLOCK.getDefaultState());
                    w.playSound(null, build.pos.up(), SoundEvents.BLOCK_SNOW_PLACE, SoundCategory.BLOCKS, 1f, 1.2f);
                    build.step = 3;
                    build.delay = 15;
                } else if (build.step == 3) { // Place pumpkin (Vanilla triggers golem spawn)
                    w.setBlockState(build.pos.up(2), Blocks.CARVED_PUMPKIN.getDefaultState());
                    w.spawnParticles(ParticleTypes.SNOWFLAKE, build.pos.getX()+0.5, build.pos.getY()+2, build.pos.getZ()+0.5, 20, 0.5, 0.5, 0.5, 0.05);
                    it.remove();
                }
            }
        }
    }

    /* ============================================================
       RAYCAST + GEOMETRY
       ============================================================ */

    private static BlockHitResult raycastBlock(ServerPlayerEntity player, double range) {
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(look.multiply(range));

        return player.getWorld().raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
    }

    private static double distSqPointToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double abLen2 = ab.lengthSquared();
        if (abLen2 < 1.0e-9) return p.squaredDistanceTo(a);

        double t = (p.subtract(a)).dotProduct(ab) / abLen2;
        t = MathHelper.clamp(t, 0.0, 1.0);

        Vec3d proj = a.add(ab.multiply(t));
        return p.squaredDistanceTo(proj);
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return "Ice"; }
    @Override public String getPrimaryName() { return "Piercing Spikes"; }
    @Override public String getSecondaryName() { return "Flash Freeze"; }
    @Override public String getUltimateName() { return "Ice Age"; }

    @Override public long getPrimaryCooldownMs() { return PRIMARY_COOLDOWN_MS; }
    @Override public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }
    @Override public long getUltimateCooldownMs() { return ULT_COOLDOWN_MS; }

    @Override
    public String getOverviewDescription() {
        return "Ice is a close-medium range power that revolves around building up freeze and consuming this freeze for high damage." +
                "(see the passive for more info) this power's abilities can also effect the environment by freezing water, leaving snow or creating ice spikes.";
    }

    @Override
    public String getPassiveName() {
        return "Deep Freeze";
    }

    @Override
    public String getPassiveDescription() {
        return "Your melee and ability damage inflicts freeze on targets, (displayed by particles). Higher levels can apply increasing slowness and mining fatigue" +
                "At the highest level, the entity will be completedly frozen and have glowing particles to indicate that they can be shattered. When shattered, the freeze" +
                "will be consumed, dealing damage and making them immune to freeze briefly after. After a few seconds of not being attacked freeze will decay.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Cause a group of ice spikes to form where you are looking. These spikes deal damage and knock enemies upwards. If an enemy is fully frozen they will be shattered and if not a high amount" +
                "of freeze will be applied. This ability travels to your mouse location before forming, meaning there is a visual delay. Spikes will not destroy and blocks and will freeze nearby water if placed on water.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Briefly charge up and shoot out a constant beam of ice, you are slower while you do this. This beam does slight damage, slows and drags enemies downwards as well as" +
                "applying freeze. This ability will never shatter and cannot pierce blocks. This is intended to be the main freeze builder.";
    }

    @Override
    public String getUltimateDescription() {
        return "Cause a blizzard to form around you, freezing and snowing on your surroundings. There will also be shockwaves produced from your location that will damage," +
                "apply freeze and shatter anyone fully frozen. These shockwaves are visual and can be jumped over (although snow layers make it harder to see)";
    }
}