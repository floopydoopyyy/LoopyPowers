package com.yourname.loopypowers.power;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class SpeedPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, SpeedState> ACTIVE_STATES = new HashMap<>();

    private static class SpeedState {
        int burstCdTicks = 0;

        // Primary — Dash Surge
        int dashCharges = DASH_MAX_CHARGES;
        int dashRechargeTicks = 0;
        int dashLockTicks = 0;
        int dashActiveTicks = 0;
        Vec3d dashDir = Vec3d.ZERO;

        // Secondary — Pinball Strike
        boolean isPinballing = false;
        int pinballHitsLeft = 0;
        int pinballTicksLeft = 0;
        Vec3d pinballAnchor = null;
        final Set<UUID> pinballHitTargets = new HashSet<>();
        UUID pinballCurrentTarget = null;

        // Ultimate — Overdrive
        int overdriveTicks = 0;
        int overdriveLoopCd = 0;
        int overdriveSlowTicks = 0;
        int blockDmgCd = 0;
    }

    private static SpeedState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new SpeedState());
    }

    public static boolean isOverdriveActive(ServerPlayerEntity player) {
        SpeedState state = ACTIVE_STATES.get(player.getUuid());
        return state != null && state.overdriveTicks > 0;
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // Passive
    private static final int   PASSIVE_SPEED_AMPLIFIER      = 1;
    private static final float PASSIVE_LOW_HEALTH_THRESHOLD = 4.0f;
    private static final int   PASSIVE_BURST_AMPLIFIER      = 4;
    private static final int   PASSIVE_BURST_DURATION       = 80;
    private static final int   PASSIVE_BURST_COOLDOWN_TICKS = 500;

    // Primary — Dash Surge
    private static final int    DASH_MAX_CHARGES   = 2;
    private static final int    DASH_RECHARGE_TICKS = 140;
    private static final int    DASH_LOCK_TICKS    = 8;
    private static final int    DASH_DURATION_TICKS = 5;
    private static final double DASH_SPEED         = 3.2;
    private static final double DASH_MAX_Y         = 0.45;
    private static final float  DASH_DAMAGE        = 8.5f;
    private static final double DASH_KNOCKBACK     = 1.35;
    private static final double DASH_KNOCKBACK_Y   = 0.35;

    // Secondary — Pinball Strike
    private static final int    PINBALL_MAX_HITS   = 15;
    private static final double PINBALL_RADIUS     = 12.0;
    private static final float  PINBALL_DAMAGE     = 12.5f;
    private static final double PINBALL_KNOCKBACK  = 0.9;
    private static final double PINBALL_KNOCKBACK_Y = 0.25;

    // Ultimate — Overdrive
    private static final int    OVERDRIVE_DURATION_TICKS    = 200;
    private static final double OVERDRIVE_MIN_SPEED         = 1.4;
    private static final double OVERDRIVE_ENTITY_DAMAGE     = 19.5f;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK  = 1.8;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK_Y = 0.5;
    private static final double OVERDRIVE_COLLISION_SLOW_X  = 0.15;
    private static final double OVERDRIVE_COLLISION_SLOW_Y  = 0.10;
    private static final int    OVERDRIVE_SLOW_TICKS        = 12;
    private static final float  OVERDRIVE_BLOCK_HARDNESS_MAX = 3.0f;
    private static final float  OVERDRIVE_SELF_DAMAGE       = 1.5f;
    private static final int    OVERDRIVE_JUMP_AMPLIFIER    = 0;
    private static final int    OVERDRIVE_HASTE_AMPLIFIER   = 2;

    // Easter egg
    private static final int A_TRAIN_CHANCE = 250;

    // Sound
    private static final int OVERDRIVE_LOOP_INTERVAL_TICKS = 18;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.put(player.getUuid(), new SpeedState());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false));
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
        if (state.overdriveLoopCd > 0) state.overdriveLoopCd--;

        tickPassiveSpeed(player);
        tickLowHealthBurst(player, state);

        // Dash
        if (state.dashLockTicks > 0) state.dashLockTicks--;
        tickDashRecharge(player, state);
        updateDashCooldownUI(player, state);

        if (state.dashActiveTicks > 0 && !state.isPinballing) {
            state.dashActiveTicks--;
            tickDashSurge(player, state);

            if (state.dashActiveTicks == 0) {
                Vec3d vel = player.getVelocity();
                player.setVelocity(vel.x * 0.15, Math.min(vel.y, 0.0), vel.z * 0.15);
                player.velocityModified = true;
                syncVelocity(player);
            }
        }

        // Pinball
        if (state.isPinballing) {
            tickPinballStrike(player, state);
        }

        // Overdrive
        if (state.overdriveTicks > 0 && !state.isPinballing) {
            state.overdriveTicks--;
            tickOverdrive(player, state);

            if (state.overdriveLoopCd <= 0) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.RUSHLOOP, player.getSoundCategory(), 0.55f, 1.3f);
                state.overdriveLoopCd = OVERDRIVE_LOOP_INTERVAL_TICKS;
            }
        }
    }

    /* ============================================================
       PRIMARY — DASH SURGE
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        SpeedState state = getState(player);

        if (state.isPinballing) return;
        if (state.dashLockTicks > 0) return;
        if (state.dashCharges <= 0) return;

        state.dashCharges--;
        state.dashLockTicks = DASH_LOCK_TICKS;
        if (state.dashRechargeTicks <= 0) {
            state.dashRechargeTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);
        }

        state.dashActiveTicks = DASH_DURATION_TICKS;
        Vec3d look = player.getRotationVec(1.0F);

        double y = look.y * DASH_SPEED;
        if (player.isOnGround()) {
            if (y > 0) y *= 0.3;
            if (y < 0.2) y = 0.2;
        }
        y = Math.min(y, DASH_MAX_Y);

        state.dashDir = new Vec3d(look.x * DASH_SPEED, y, look.z * DASH_SPEED);
        player.setVelocity(state.dashDir);
        player.velocityModified = true;
        syncVelocity(player);

        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.DASH, player.getSoundCategory(), 1.0f, 1.0f);
    }

    private void tickDashRecharge(ServerPlayerEntity player, SpeedState state) {
        if (PowerManager.areCooldownsDisabled()) {
            state.dashCharges = DASH_MAX_CHARGES;
            state.dashRechargeTicks = 0;
            return;
        }
        if (state.dashCharges >= DASH_MAX_CHARGES) {
            state.dashRechargeTicks = 0;
            return;
        }
        int maxTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);
        if (state.dashRechargeTicks <= 0) state.dashRechargeTicks = maxTicks;
        state.dashRechargeTicks--;
        if (state.dashRechargeTicks <= 0) {
            state.dashCharges++;
            if (state.dashCharges < DASH_MAX_CHARGES) {
                state.dashRechargeTicks = maxTicks;
            } else {
                state.dashRechargeTicks = 0;
            }
        }
    }

    private void updateDashCooldownUI(ServerPlayerEntity player, SpeedState state) {
        String key = "SpeedUI:PRIMARY";
        if (state.dashCharges >= DASH_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }
        int maxTicks = PowerManager.getModifiedCooldownTicks(player, DASH_RECHARGE_TICKS);
        long endMs = System.currentTimeMillis() + (state.dashRechargeTicks * 50L);
        Text suffix = CooldownUI.makeChargeSuffix(
                state.dashCharges, DASH_MAX_CHARGES, state.dashRechargeTicks, Math.max(1, maxTicks));
        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    private static void tickDashSurge(ServerPlayerEntity player, SpeedState state) {
        player.setVelocity(state.dashDir);
        player.velocityModified = true;
        player.fallDistance = 0;
        syncVelocity(player);

        spawnDashCastParticles(player, state.dashDir.normalize());
        spawnDashTrailParticles(player);

        player.getServerWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(1.2),
                        e -> e instanceof LivingEntity && e.isAlive())
                .forEach(e -> {
                    LivingEntity victim = (LivingEntity) e;
                    if (victim.damage(player.getDamageSources().playerAttack(player), DASH_DAMAGE)) {
                        Vec3d pushDir = state.dashDir.normalize();
                        victim.addVelocity(pushDir.x * DASH_KNOCKBACK, DASH_KNOCKBACK_Y, pushDir.z * DASH_KNOCKBACK);
                        victim.velocityModified = true;
                        if (victim instanceof ServerPlayerEntity sp) syncVelocity(sp);

                        player.getServerWorld().playSound(null, victim.getBlockPos(),
                                SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, player.getSoundCategory(), 1.0f, 1.3f);
                        CameraShake.shakeNearby(player, 6.0, 5, 0.25f);
                    }
                });
    }

    /* ============================================================
       SECONDARY — PINBALL STRIKE
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        if (state.isPinballing) return;

        state.isPinballing = true;
        state.pinballHitsLeft = PINBALL_MAX_HITS;
        state.pinballCurrentTarget = null;
        state.pinballAnchor = player.getPos();
        state.pinballHitTargets.clear();

        player.getServerWorld().playSound(null, player.getBlockPos(),
                ModSounds.RUSHSTART, player.getSoundCategory(), 0.2f, 1.5f);
    }

    private void tickPinballStrike(ServerPlayerEntity player, SpeedState state) {
        ServerWorld w = player.getServerWorld();

        if (w.getTime() % 3 == 0) {
            SpeedPinballAnchorPayload anchor = new SpeedPinballAnchorPayload(
                    state.pinballAnchor.x, state.pinballAnchor.y, state.pinballAnchor.z);
            sendToViewers(w, player, anchor);
        }

        player.fallDistance = 0;

        if (state.pinballCurrentTarget == null) {
            // Find next target within the anchor's radius
            Vec3d anchor = state.pinballAnchor;
            double r = PINBALL_RADIUS;
            net.minecraft.util.math.Box anchorBox = new net.minecraft.util.math.Box(
                    anchor.x - r, anchor.y - r, anchor.z - r,
                    anchor.x + r, anchor.y + r, anchor.z + r);

            List<LivingEntity> allTargets = w.getEntitiesByClass(LivingEntity.class, anchorBox,
                    e -> e.isAlive() && e != player
                            && e.getPos().squaredDistanceTo(anchor) <= (r * r)
                            && player.canSee(e));

            if (allTargets.isEmpty() || state.pinballHitsLeft <= 0) {
                endPinballStrike(player, state);
                return;
            }

            List<LivingEntity> unhitTargets = allTargets.stream()
                    .filter(e -> !state.pinballHitTargets.contains(e.getUuid()))
                    .toList();

            LivingEntity target;
            if (!unhitTargets.isEmpty()) {
                target = unhitTargets.get(w.random.nextInt(unhitTargets.size()));
            } else {
                target = allTargets.get(w.random.nextInt(allTargets.size()));
                state.pinballHitsLeft = 1;
            }

            state.pinballHitsLeft--;
            state.pinballHitTargets.add(target.getUuid());
            state.pinballCurrentTarget = target.getUuid();

            forceLookAt(player, target.getEyePos());

            Vec3d targetCenter = target.getPos().add(0, target.getHeight() / 2.0, 0);
            Vec3d playerCenter = player.getPos().add(0, player.getHeight() / 2.0, 0);
            Vec3d dir = targetCenter.subtract(playerCenter);
            double dist = dir.length();

            double speed = Math.max(2.5, Math.min(dist / 2.0, 5.0));
            Vec3d dashVel = dir.normalize().multiply(speed);

            player.setVelocity(dashVel);
            player.velocityModified = true;
            syncVelocity(player);

            w.playSound(null, player.getBlockPos(),
                    ModSounds.DARKNESSTELEPORT2, player.getSoundCategory(), 1.0f, 1.2f);
            spawnDashCastParticles(player, dir.normalize());

            state.pinballTicksLeft = Math.min(10, (int) Math.ceil(dist / speed) + 1);

        } else {
            // Travel toward target
            state.pinballTicksLeft--;

            Vec3d vel = player.getVelocity();
            if (vel.y < 0 && !player.isOnGround()) {
                player.setVelocity(vel.x, vel.y * 0.4, vel.z);
                player.velocityModified = true;
                syncVelocity(player);
            }

            spawnDashTrailParticles(player);

            net.minecraft.entity.Entity rawTarget = w.getEntity(state.pinballCurrentTarget);
            LivingEntity targetEntity = (rawTarget instanceof LivingEntity le) ? le : null;
            boolean close = targetEntity != null && player.squaredDistanceTo(targetEntity) < 16.0;

            if (state.pinballTicksLeft <= 0 || close) {
                if (targetEntity != null && targetEntity.isAlive() && close) {
                    player.swingHand(Hand.MAIN_HAND, true);

                    if (targetEntity.damage(ModDamageTypes.rush(w, player), PINBALL_DAMAGE)) {
                        Vec3d push = targetEntity.getPos().subtract(player.getPos()).normalize();
                        targetEntity.addVelocity(push.x * PINBALL_KNOCKBACK, PINBALL_KNOCKBACK_Y, push.z * PINBALL_KNOCKBACK);
                        targetEntity.velocityModified = true;
                        if (targetEntity instanceof ServerPlayerEntity sp) syncVelocity(sp);

                        CameraShake.shakeNearby(player, 6.0, 5, 0.4f);
                    }

                    w.playSound(null, targetEntity.getBlockPos(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, player.getSoundCategory(), 1.0f, 1.4f);
                    w.playSound(null, targetEntity.getBlockPos(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, player.getSoundCategory(), 0.8f, 1.1f);
                }

                player.setVelocity(0, 0, 0);
                player.velocityModified = true;
                syncVelocity(player);

                state.pinballCurrentTarget = null;
            }
        }
    }

    private static void endPinballStrike(ServerPlayerEntity player, SpeedState state) {
        state.isPinballing = false;
        state.pinballHitTargets.clear();
        player.getServerWorld().playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, player.getSoundCategory(), 1.0f, 0.8f);
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        if (state.isPinballing) return;

        state.overdriveTicks = OVERDRIVE_DURATION_TICKS;
        state.overdriveLoopCd = 0;

        Vec3d look = player.getRotationVec(1.0F);
        Vec3d flatLook = new Vec3d(look.x, 0, look.z);
        if (flatLook.lengthSquared() < 1.0e-6) {
            double yawRad = Math.toRadians(player.getYaw());
            flatLook = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        }
        flatLook = flatLook.normalize();

        player.setVelocity(
                flatLook.x * (OVERDRIVE_MIN_SPEED * 1.8),
                player.getVelocity().y + 0.2,
                flatLook.z * (OVERDRIVE_MIN_SPEED * 1.8)
        );
        player.velocityModified = true;
        syncVelocity(player);

        spawnOverdriveCastParticles(player);

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, player.getSoundCategory(), 0.6f, 1.8f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.OVERDRIVESTART, player.getSoundCategory(), 0.2f, 1.5f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.0f, 0.6f);
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
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, PASSIVE_BURST_DURATION, PASSIVE_BURST_AMPLIFIER, true, false));
            state.burstCdTicks = PASSIVE_BURST_COOLDOWN_TICKS;

            sendToViewers(player.getServerWorld(), player,
                    new SpeedLowHealthBurstPayload(player.getX(), player.getY(), player.getZ()));
            player.getServerWorld().playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, player.getSoundCategory(), 0.7f, 1.6f);
        }
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
        w.getOtherEntities(player, player.getBoundingBox().expand(1.2),
                e -> e instanceof LivingEntity && e.isAlive())
                .forEach(entity -> {
                    DamageSource overdriveSrc = ModDamageTypes.overdrive(player.getWorld(), player);
                    if (entity.damage(overdriveSrc, (float) OVERDRIVE_ENTITY_DAMAGE)) {
                        if (!entity.isAlive() && entity instanceof ServerPlayerEntity) {
                            if (player.getRandom().nextInt(A_TRAIN_CHANCE) == 0 && player.getServer() != null) {
                                player.getServer().getPlayerManager().broadcast(
                                        Text.literal("<" + player.getName().getString() + "> I can't stop. I can't stop. I can't stop. I can't stop."), false);
                            }
                        }

                        Vec3d dir = entity.getPos().subtract(player.getPos()).normalize();
                        entity.addVelocity(dir.x * OVERDRIVE_ENTITY_KNOCKBACK, OVERDRIVE_ENTITY_KNOCKBACK_Y, dir.z * OVERDRIVE_ENTITY_KNOCKBACK);
                        entity.velocityModified = true;

                        applyCollisionSlow(player, state);

                        sendToViewers(w, player, new SpeedExplosionFxPayload(player.getX(), player.getY(), player.getZ(), true));
                        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.0f, 0.8f);
                    }
                });

        spawnOverdriveTrailParticles(player);

        // Authoritative velocity sync so player cannot fight the forced forward speed with WASD
        player.velocityModified = true;
        syncVelocity(player);
    }

    /* ============================================================
       OVERDRIVE BEHAVIOUR HELPERS
       ============================================================ */

    private static void applyCollisionSlow(ServerPlayerEntity player, SpeedState state) {
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X);
        player.velocityModified = true;
        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 2, true, false));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_SMALL_FALL, player.getSoundCategory(), 0.8f, 0.7f);
        CameraShake.shakeNearby(player, 10.0, 5, 15);
    }

    private static void applyCollisionSlowBlock(ServerPlayerEntity player, SpeedState state) {
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X);
        player.velocityModified = true;
        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 2, true, false));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, player.getSoundCategory(), 0.8f, 0.7f);
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

        sendToViewers(player.getServerWorld(), player,
                new SpeedExplosionFxPayload(player.getX(), player.getY(), player.getZ(), false));
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, player.getSoundCategory(), 0.8f, 0.85f);
    }

    private static void forceForward(ServerPlayerEntity player, SpeedState state) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d currentVel = player.getVelocity();

        Vec3d flatLook = new Vec3d(look.x, 0, look.z);
        if (flatLook.lengthSquared() < 1.0e-6) {
            double yawRad = Math.toRadians(player.getYaw());
            flatLook = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        }

        double slowFactor = (state.overdriveSlowTicks > 0)
                ? MathHelper.clamp(state.overdriveSlowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3d targetHoriz = flatLook.normalize().multiply(OVERDRIVE_MIN_SPEED * slowFactor);
        player.setVelocity(targetHoriz.x, currentVel.y, targetHoriz.z);
        player.velocityModified = true;
    }

    private static void applyCollisionSelfDamage(ServerPlayerEntity player, SpeedState state) {
        if (state.blockDmgCd > 0) return;
        player.damage(ModDamageTypes.wallCollision(player.getWorld()), OVERDRIVE_SELF_DAMAGE);
        state.blockDmgCd = 10;
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT, player.getSoundCategory(), 0.8f, 1.0f);
    }

    /* ============================================================
       PARTICLE HELPERS
       ============================================================ */

    private static void spawnDashCastParticles(ServerPlayerEntity player, Vec3d look) {
        sendToViewers(player.getServerWorld(), player,
                new SpeedDashCastPayload(player.getX(), player.getY(), player.getZ(),
                        look.x, look.y, look.z));
    }

    private static void spawnDashTrailParticles(ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        if (vel.lengthSquared() < 0.1) return;
        sendToViewers(player.getServerWorld(), player,
                new SpeedDashTrailPayload(player.getX(), player.getY(), player.getZ(),
                        vel.x, vel.y, vel.z, player.getServerWorld().getTime()));
    }

    private static void spawnOverdriveCastParticles(ServerPlayerEntity player) {
        sendToViewers(player.getServerWorld(), player,
                new SpeedOverdriveCastPayload(player.getX(), player.getY(), player.getZ()));
    }

    private static void spawnOverdriveTrailParticles(ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        if (vel.lengthSquared() < 0.001) return;
        sendToViewers(player.getServerWorld(), player,
                new SpeedOverdriveTrailPayload(player.getX(), player.getY(), player.getZ(),
                        vel.x, vel.y, vel.z, player.getServerWorld().getTime()));
    }

    /* ============================================================
       UTIL
       ============================================================ */

    private static void forceLookAt(ServerPlayerEntity player, Vec3d targetPos) {
        Vec3d dir = targetPos.subtract(player.getEyePos());
        if (dir.lengthSquared() < 0.0001) return;

        float targetYaw   = (float)(Math.toDegrees(MathHelper.atan2(dir.z, dir.x))) - 90f;
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float targetPitch = (float)(-Math.toDegrees(MathHelper.atan2(dir.y, horizontal)));

        float yawDiff   = MathHelper.wrapDegrees(targetYaw - player.getYaw());
        float pitchDiff = targetPitch - player.getPitch();

        player.networkHandler.sendPacket(new PlayerPositionLookS2CPacket(
                0, 0, 0, yawDiff, pitchDiff,
                Set.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z, PositionFlag.Y_ROT, PositionFlag.X_ROT),
                0));
    }

    private static void syncVelocity(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));
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

    @Override public String getName()              { return Text.translatable("power.loopypowers.speed.name").getString(); }
    @Override public String getPassiveName()       { return Text.translatable("power.loopypowers.speed.passive_name").getString(); }
    @Override public String getPassiveDescription(){ return Text.translatable("power.loopypowers.speed.description.passive").getString(); }
    @Override public String getPrimaryName()       { return Text.translatable("power.loopypowers.speed.primary_name").getString(); }
    @Override public long   getPrimaryCooldownMs() { return 0; }
    @Override public String getPrimaryDescription(){ return Text.translatable("power.loopypowers.speed.description.primary").getString(); }
    @Override public String getSecondaryName()     { return Text.translatable("power.loopypowers.speed.secondary_name").getString(); }
    @Override public long   getSecondaryCooldownMs(){ return 27_000; }
    @Override public String getSecondaryDescription(){ return Text.translatable("power.loopypowers.speed.description.secondary").getString(); }
    @Override public String getUltimateName()      { return Text.translatable("power.loopypowers.speed.ultimate_name").getString(); }
    @Override public long   getUltimateCooldownMs(){ return 400_000; }
    @Override public String getUltimateDescription(){ return Text.translatable("power.loopypowers.speed.description.ultimate").getString(); }
    @Override public String getOverviewDescription(){ return Text.translatable("power.loopypowers.speed.description.overview").getString(); }
}
