package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import com.yourname.loopypowers.sound.ModSounds;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SpeedPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SpeedState> ACTIVE_STATES = new HashMap<>();

    private static class SpeedState {
        int burstCdTicks = 0;
        int rushTicks = 0;
        int rushLoopCd = 0;
        int overdriveTicks = 0;
        int overdriveLoopCd = 0;
        int overdriveSlowTicks = 0;
        int blockDmgCd = 0;
    }

    private static SpeedState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new SpeedState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // PASSIVE
    private static final int    PASSIVE_SPEED_AMPLIFIER      = 1;
    private static final float  PASSIVE_LOW_HEALTH_THRESHOLD = 4.0f;
    private static final int    PASSIVE_BURST_AMPLIFIER      = 4;
    private static final int    PASSIVE_BURST_DURATION       = 80;
    private static final int    PASSIVE_BURST_COOLDOWN_TICKS = 500;

    // PRIMARY
    private static final double DASH_HORIZONTAL_STRENGTH = 2.6;
    private static final double DASH_VERTICAL_STRENGTH   = 1.3;
    private static final double DASH_MAX_DOWN            = -0.8;
    private static final double DASH_MAX_UP              = 0.8;
    private static final double DASH_GROUND_MIN_Y        = 0.18;

    // SECONDARY
    private static final int    RUSH_DURATION_TICKS        = 100;
    private static final float  RUSH_DAMAGE                = 12.5f;
    private static final double RUSH_KNOCKBACK_HORIZONTAL  = 1.4;
    private static final double RUSH_KNOCKBACK_VERTICAL    = 0.55;
    private static final double RUSH_KNOCKBACK_SCAN_RADIUS = 4.5;
    private static final double RUSH_VELOCITY_BONUS        = 1.5;
    private static final int    RUSH_HASTE_AMPLIFIER       = 0;

    // ULT
    private static final int    OVERDRIVE_DURATION_TICKS      = 200;
    private static final double OVERDRIVE_MIN_SPEED           = 1.4;
    private static final double OVERDRIVE_FORWARD_PUSH        = 0.38;
    private static final double OVERDRIVE_ENTITY_DAMAGE       = 19.5f;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK    = 1.8;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK_Y  = 0.5;
    private static final double OVERDRIVE_COLLISION_SLOW_X    = 0.15;
    private static final double OVERDRIVE_COLLISION_SLOW_Y    = 0.10;
    private static final int    OVERDRIVE_SLOW_TICKS          = 12;
    private static final float  OVERDRIVE_BLOCK_HARDNESS_MAX  = 3.0f;
    private static final float  OVERDRIVE_SELF_DAMAGE         = 1.5f;
    private static final int    OVERDRIVE_JUMP_AMPLIFIER      = 0;
    private static final int    OVERDRIVE_HASTE_AMPLIFIER     = 2;

    // EASTER EGG
    private static final int A_TRAIN_CHANCE = 250;

    // Sound
    private static final int RUSH_LOOP_INTERVAL_TICKS     = 25;
    private static final int OVERDRIVE_LOOP_INTERVAL_TICKS = 18;

    /* ============================================================
       PASSIVE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.put(player.getUuid(), new SpeedState());

        player.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false)
        );
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.remove(player.getUuid());

        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.HASTE);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        SpeedState state = getState(player);

        if (state.burstCdTicks > 0) state.burstCdTicks--;
        if (state.blockDmgCd > 0) state.blockDmgCd--;
        if (state.overdriveSlowTicks > 0) state.overdriveSlowTicks--;
        if (state.rushLoopCd > 0) state.rushLoopCd--;
        if (state.overdriveLoopCd > 0) state.overdriveLoopCd--;

        tickPassiveSpeed(player);
        tickLowHealthBurst(player, state);

        if (state.rushTicks > 0) {
            state.rushTicks--;
            tickRushEffects(player);

            if (state.rushLoopCd <= 0) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHLOOP, player.getSoundCategory(), 0.55f, 1.0f);
                state.rushLoopCd = RUSH_LOOP_INTERVAL_TICKS;
            }
        }

        if (state.overdriveTicks > 0) {
            state.overdriveTicks--;
            tickOverdrive(player, state);

            if (state.overdriveLoopCd <= 0) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHLOOP, player.getSoundCategory(), 0.55f, 1.3f);
                state.overdriveLoopCd = OVERDRIVE_LOOP_INTERVAL_TICKS;
            }
        }
    }

    /* ============================================================
       PRIMARY — DASH
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);

        double y = MathHelper.clamp(
                look.y * DASH_VERTICAL_STRENGTH,
                DASH_MAX_DOWN,
                DASH_MAX_UP
        );
        double x = look.x * DASH_HORIZONTAL_STRENGTH;
        double z = look.z * DASH_HORIZONTAL_STRENGTH;

        if (player.isOnGround()) {
            y = Math.max(y, DASH_GROUND_MIN_Y);
        }

        player.addVelocity(x, y, z);
        player.velocityModified = true;

        SpeedDashPayload fx = new SpeedDashPayload(
                player.getX(), player.getY(), player.getZ(),
                look.x, look.y, look.z);
        sendToViewers(player.getServerWorld(), player, fx);

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.DASH, player.getSoundCategory(), 1.0f, 1.0f);
        player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
    }

    /* ============================================================
       SECONDARY — RUSH
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        state.rushTicks = RUSH_DURATION_TICKS;
        state.rushLoopCd = 0;

        Vec3d look = player.getRotationVec(1.0F);
        player.setVelocity(look.x * RUSH_VELOCITY_BONUS, player.getVelocity().y, look.z * RUSH_VELOCITY_BONUS);
        player.velocityModified = true;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, RUSH_DURATION_TICKS, RUSH_HASTE_AMPLIFIER, true, false));

        player.getServerWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(RUSH_KNOCKBACK_SCAN_RADIUS),
                        e -> e instanceof LivingEntity)
                .forEach(e -> {
                    LivingEntity victim = (LivingEntity) e;
                    DamageSource rushSrc = ModDamageTypes.rush(player.getWorld(), player);

                    if (victim.damage(rushSrc, RUSH_DAMAGE)) {
                        double dx = victim.getX() - player.getX();
                        double dz = victim.getZ() - player.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        if (dist > 0.001) {
                            victim.addVelocity((dx / dist) * RUSH_KNOCKBACK_HORIZONTAL, RUSH_KNOCKBACK_VERTICAL, (dz / dist) * RUSH_KNOCKBACK_HORIZONTAL);
                            victim.velocityModified = true;
                        }

                        CameraShake.shakeNearby(player, 8.0, 5, 0.45f);
                    }
                });

        SpeedRushCastPayload fx = new SpeedRushCastPayload(player.getX(), player.getY(), player.getZ());
        sendToViewers(player.getServerWorld(), player, fx);

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 0.3f, 1.2f);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RUSHSTART, player.getSoundCategory(), 0.5f, 1.0f);
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        state.overdriveTicks = OVERDRIVE_DURATION_TICKS;
        state.overdriveLoopCd = 0;

        Vec3d look = player.getRotationVec(1.0F);
        player.setVelocity(
                look.x * (OVERDRIVE_MIN_SPEED * 1.8),
                player.getVelocity().y + 0.2,
                look.z * (OVERDRIVE_MIN_SPEED * 1.8)
        );
        player.velocityModified = true;

        SpeedOverdriveCastPayload fx = new SpeedOverdriveCastPayload(player.getX(), player.getY(), player.getZ());
        sendToViewers(player.getServerWorld(), player, fx);

        player.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
        player.playSound(ModSounds.OVERDRIVESTART, 0.2f, 1.5f);
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.0f, 0.6f);
    }

    /* ============================================================
       TICK HELPERS
       ============================================================ */

    private void tickPassiveSpeed(ServerPlayerEntity player) {
        if (!PassiveManager.isEnabled(player)) return;

        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed == null || speed.getDuration() < 5) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, PASSIVE_SPEED_AMPLIFIER, true, false));
        }
    }

    private void tickLowHealthBurst(ServerPlayerEntity player, SpeedState state) {
        if (state.burstCdTicks > 0) return;

        if (player.getHealth() <= PASSIVE_LOW_HEALTH_THRESHOLD) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, PASSIVE_BURST_DURATION, PASSIVE_BURST_AMPLIFIER, true, false));
            state.burstCdTicks = PASSIVE_BURST_COOLDOWN_TICKS;

            SpeedLowHealthBurstPayload fx = new SpeedLowHealthBurstPayload(player.getX(), player.getY() + 0.5, player.getZ());
            sendToViewers(player.getServerWorld(), player, fx);

            player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
        }
    }

    private void tickRushEffects(ServerPlayerEntity player) {
        Vec3d vel   = player.getVelocity();
        Vec3d horiz = new Vec3d(vel.x, 0, vel.z);
        if (horiz.length() < RUSH_VELOCITY_BONUS * 0.6) {
            Vec3d look = player.getRotationVec(1.0F);
            player.addVelocity(look.x * 0.18, 0, look.z * 0.18);
            player.velocityModified = true;
        }

        SpeedRushTrailPayload fx = new SpeedRushTrailPayload(player.getX(), player.getY() + 0.05, player.getZ());
        sendToViewers(player.getServerWorld(), player, fx);
    }

    private static void tickOverdrive(ServerPlayerEntity player, SpeedState state) {
        forceForward(player, state);
        breakBlocks(player, state);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20, OVERDRIVE_JUMP_AMPLIFIER, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 20, OVERDRIVE_HASTE_AMPLIFIER, true, false));

        if (player.isTouchingWater()) {
            Vec3d vel = player.getVelocity();
            if (vel.y < 0) {
                player.setVelocity(vel.x, 0.08, vel.z);
                player.velocityModified = true;
            }
            player.setOnGround(true);
        }

        ServerWorld w = player.getServerWorld();
        w.getOtherEntities(player, player.getBoundingBox().expand(1.2), e -> e instanceof LivingEntity)
                .forEach(entity -> {
                    DamageSource overdriveSrc = ModDamageTypes.overdrive(player.getWorld(), player);

                    if (entity.damage(overdriveSrc, (float) OVERDRIVE_ENTITY_DAMAGE)) {
                        // EASTER EGG
                        if (!entity.isAlive() && entity instanceof ServerPlayerEntity) {
                            if (player.getRandom().nextInt(A_TRAIN_CHANCE) == 0 && player.getServer() != null) {
                                player.getServer().getPlayerManager().broadcast(Text.literal("<" + player.getName().getString() + "> I can't stop. I can't stop. I can't stop. I can't stop."), false);
                            }
                        }

                        Vec3d dir = entity.getPos().subtract(player.getPos()).normalize();
                        entity.addVelocity(dir.x * OVERDRIVE_ENTITY_KNOCKBACK, OVERDRIVE_ENTITY_KNOCKBACK_Y, dir.z * OVERDRIVE_ENTITY_KNOCKBACK);
                        entity.velocityModified = true;

                        applyCollisionSlow(player, state);

                        SpeedOverdriveHitPayload fx = new SpeedOverdriveHitPayload(player.getX(), player.getY() + 1, player.getZ());
                        sendToViewers(w, player, fx);

                        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.0f, 0.8f);
                    }
                });

        Vec3d vel = player.getVelocity();
        boolean showRing = (int)(player.getWorld().getTime() % 4) == 0;
        SpeedOverdriveTrailPayload trail = new SpeedOverdriveTrailPayload(
                player.getX(), player.getY() + 0.8, player.getZ(),
                vel.x, vel.y, vel.z,
                showRing);
        sendToViewers(w, player, trail);
    }

    /* ============================================================
       OVERDRIVE BEHAVIOUR HELPERS
       ============================================================ */

    private static void applyCollisionSlow(ServerPlayerEntity player, SpeedState state) {
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * OVERDRIVE_COLLISION_SLOW_X, Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y), vel.z * OVERDRIVE_COLLISION_SLOW_X);
        player.velocityModified = true;

        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 2, true, false));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_SMALL_FALL, player.getSoundCategory(), 0.8f, 0.7f);
        CameraShake.shakeNearby(player, 10.0, 5, 15);
    }

    private static void applyCollisionSlowBlock(ServerPlayerEntity player, SpeedState state) {
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * OVERDRIVE_COLLISION_SLOW_X, Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y), vel.z * OVERDRIVE_COLLISION_SLOW_X);
        player.velocityModified = true;

        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 2, true, false));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, player.getSoundCategory(), 0.8f, 0.7f);
        CameraShake.shakeNearby(player, 10.0, 6, 17);
    }

    private static void breakBlocks(ServerPlayerEntity player, SpeedState state) {
        if (state.overdriveSlowTicks > 0) return;

        var world = player.getWorld();
        BlockPos base = player.getBlockPos();
        boolean impactedThisTick = false;

        for (BlockPos pos : BlockPos.iterate(base.add(-1, 0, -1), base.add(1, 2, 1))) {
            var blockState = world.getBlockState(pos);
            if (blockState.isAir() || blockState.isIn(BlockTags.FIRE)) continue;

            float hardness = blockState.getHardness(world, pos);

            if (hardness < 0) {
                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 2.0f);
                    impactedThisTick = true;
                }
                continue;
            }

            if (blockState.isReplaceable()
                    || blockState.isIn(BlockTags.LEAVES)
                    || blockState.isIn(BlockTags.FLOWERS)
                    || blockState.isIn(BlockTags.SMALL_FLOWERS)
                    || blockState.isIn(BlockTags.TALL_FLOWERS)) {
                world.breakBlock(pos, false);
                continue;
            }

            if (hardness <= OVERDRIVE_BLOCK_HARDNESS_MAX && !blockState.isReplaceable()) {
                world.breakBlock(pos, true, player);

                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 1.4f);
                    impactedThisTick = true;
                }
            }
        }
    }

    private static void handleBlockImpact(ServerPlayerEntity player, SpeedState state, float shakeStrength) {
        if (!player.horizontalCollision) return;
        if (state.overdriveSlowTicks > 0) return;

        applyCollisionSlowBlock(player, state);
        applyCollisionSelfDamage(player, state);

        CameraShake.shakeNearby(player, 12.0, 8, shakeStrength);

        SpeedBlockImpactPayload fx = new SpeedBlockImpactPayload(player.getX(), player.getY() + 1, player.getZ());
        sendToViewers(player.getServerWorld(), player, fx);

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, player.getSoundCategory(), 0.8f, 0.85f);
    }

    private static void forceForward(ServerPlayerEntity player, SpeedState state) {
        Vec3d look  = player.getRotationVec(1.0F);
        Vec3d vel   = player.getVelocity();
        Vec3d horiz = new Vec3d(vel.x, 0, vel.z);

        if (horiz.length() >= OVERDRIVE_MIN_SPEED) return;

        double slowFactor = (state.overdriveSlowTicks > 0)
                ? MathHelper.clamp(state.overdriveSlowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3d push = new Vec3d(look.x, 0, look.z).normalize().multiply(OVERDRIVE_FORWARD_PUSH * slowFactor);
        player.addVelocity(push.x, 0, push.z);
        player.velocityModified = true;
    }

    private static void applyCollisionSelfDamage(ServerPlayerEntity player, SpeedState state) {
        if (state.blockDmgCd > 0) return;

        player.damage(ModDamageTypes.wallCollision(player.getWorld()), OVERDRIVE_SELF_DAMAGE);
        state.blockDmgCd = 10;

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, player.getSoundCategory(), 0.8f, 1.0f);
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override public String getName()             { return Text.translatable("power.loopypowers.speed.name").getString(); }
    @Override public String getPassiveName()      { return Text.translatable("power.loopypowers.speed.passive_name").getString(); }
    @Override public String getPassiveDescription(){ return Text.translatable("power.loopypowers.speed.description.passive").getString(); }
    @Override public String getPrimaryName()      { return Text.translatable("power.loopypowers.speed.primary_name").getString(); }
    @Override public long   getPrimaryCooldownMs(){ return 6_000; }
    @Override public String getPrimaryDescription(){ return Text.translatable("power.loopypowers.speed.description.primary").getString(); }
    @Override public String getSecondaryName()    { return Text.translatable("power.loopypowers.speed.secondary_name").getString(); }
    @Override public long   getSecondaryCooldownMs(){ return 18_000; }
    @Override public String getSecondaryDescription(){ return Text.translatable("power.loopypowers.speed.description.secondary").getString(); }
    @Override public String getUltimateName()     { return Text.translatable("power.loopypowers.speed.ultimate_name").getString(); }
    @Override public long   getUltimateCooldownMs(){ return 400_000; }
    @Override public String getUltimateDescription(){ return Text.translatable("power.loopypowers.speed.description.ultimate").getString(); }
    @Override public String getOverviewDescription(){ return Text.translatable("power.loopypowers.speed.description.overview").getString(); }
}
