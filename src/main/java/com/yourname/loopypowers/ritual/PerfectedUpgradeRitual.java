package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.RitualFxPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class PerfectedUpgradeRitual implements Ritual {

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 65;
    private static final int STAGE_3_TICKS = 60;
    private static final int STAGE_4_TICKS = 80;
    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float DAMAGE_PER_TICK = 0.35f;
    private static final int   DAMAGE_INTERVAL = 10;
    private static final float MIN_HEALTH      = 0.5f;

    private static final int   SHAKE_RADIUS            = 8;
    private static final int   SHAKE_STAGE_1_INTERVAL  = 18;
    private static final int   SHAKE_STAGE_2_INTERVAL  = 10;
    private static final int   SHAKE_STAGE_3_INTERVAL  = 6;
    private static final int   SHAKE_STAGE_4_INTERVAL  = 4;
    private static final float SHAKE_STAGE_1_INTENSITY = 0.10f;
    private static final float SHAKE_STAGE_2_INTENSITY = 0.20f;
    private static final float SHAKE_STAGE_3_INTENSITY = 0.28f;
    private static final float SHAKE_STAGE_4_BURST     = 0.50f;
    private static final float SHAKE_STAGE_4_BASE      = 0.30f;

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PerfectedUpgradeRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    @Override
    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) {
            if (player instanceof ServerPlayerEntity sp) cancelRitual(sp);
            return true;
        }
        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        sendFx(world, sp, stage, stageTick, stageProgress());

        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }
        return false;
    }

    private int currentStage() {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private float stageProgress() {
        int t = stageLocalTick();
        return switch (currentStage()) {
            case 1 -> (float) t / STAGE_1_TICKS;
            case 2 -> (float) t / STAGE_2_TICKS;
            case 3 -> (float) t / STAGE_3_TICKS;
            case 4 -> (float) t / STAGE_4_TICKS;
            default -> 0f;
        };
    }

    private void lockPosition(ServerPlayerEntity sp) {
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.fallDistance = 0;
        sp.setOnGround(true);
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 10, true, false, false));
    }

    private void cancelRitual(ServerPlayerEntity sp) {
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
    }

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.0f, 0.45f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.6f, 0.25f);
        }
        double progress = (double) t / STAGE_1_TICKS;
        if (t % SHAKE_STAGE_1_INTERVAL == 0)
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_1_INTENSITY + (float) progress * 0.05f);
        if (t % 18 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.PLAYERS, 0.6f, 0.35f + (float) progress * 0.45f);
    }

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.9f, 0.65f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 0.60f);
        }
        double progress = (double) t / STAGE_2_TICKS;
        if (t % SHAKE_STAGE_2_INTERVAL == 0)
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8,
                    SHAKE_STAGE_2_INTENSITY + (float) progress * (SHAKE_STAGE_3_INTENSITY - SHAKE_STAGE_2_INTENSITY));
        if (progress > 0.35f && progress < 0.92f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK)
                sp.damage(world.getDamageSources().magic(), DAMAGE_PER_TICK);
        }
        if (t == 22) world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.7f, 0.45f);
        if (t == 46) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.75f);
    }

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.9f, 0.8f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 0.7f, 1.1f);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_INTENSITY + 0.05f);
        }
        double progress = (double) t / STAGE_3_TICKS;
        if (t % SHAKE_STAGE_3_INTERVAL == 0)
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8,
                    SHAKE_STAGE_3_INTENSITY + (float) progress * (SHAKE_STAGE_4_BASE - SHAKE_STAGE_3_INTENSITY));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (progress > 0.40f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 30, 0, true, false, false));
        if (t % DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > MIN_HEALTH + DAMAGE_PER_TICK)
                sp.damage(world.getDamageSources().magic(), DAMAGE_PER_TICK);
        }
        if (t % 10 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    SoundCategory.PLAYERS, (float)(0.6 + progress * 0.4), 0.9f + (float) progress * 0.5f);
        if (t == 30) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.3f, 0.65f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.9f, 0.75f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 0.80f);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_4_BURST);
        }
        double progress = (double) t / STAGE_4_TICKS;
        int shakeInterval = (progress < 0.60) ? SHAKE_STAGE_4_INTERVAL : SHAKE_STAGE_3_INTERVAL;
        if (t % shakeInterval == 0) {
            float intensity = Math.max(SHAKE_STAGE_1_INTENSITY, SHAKE_STAGE_4_BASE * (float)(1.0 - progress * 0.70));
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }
        if (progress < 0.72f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (t % 7 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS, 0.6f + (float) progress * 0.4f, 0.65f + (float) progress * 1.0f);
    }

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        PowerManager.setLevel(sp, 3);
        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.heal(6.0f);
        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.28f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.5f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 1.6f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.6f, 2.0f);
        sp.sendMessage(net.minecraft.text.Text.literal("§dYour bond has been perfected to Level 3."), false);
        sendFx(world, sp, 0, 0, 1.0f);
    }

    private void sendFx(ServerWorld world, ServerPlayerEntity sp, int stage, int stageTick, float progress) {
        RitualFxPayload payload = new RitualFxPayload(
                RitualFxPayload.PERFECTED,
                sp.getX(), sp.getY(), sp.getZ(),
                stage, stageTick, progress, world.getTime());
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, sp.getBlockPos()).forEach(viewers::add);
        viewers.add(sp);
        viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
    }
}
