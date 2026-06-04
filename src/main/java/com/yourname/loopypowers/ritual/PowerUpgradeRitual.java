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

public class PowerUpgradeRitual implements Ritual {

    private static final int STAGE_1_TICKS = 50;
    private static final int STAGE_2_TICKS = 45;
    private static final int STAGE_3_TICKS = 55;
    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS;

    private static final float STAGE_2_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_2_DAMAGE_INTERVAL = 12;
    private static final float STAGE_2_MIN_HEALTH      = 1.0f;

    private static final int   SHAKE_RADIUS           = 6;
    private static final int   SHAKE_STAGE_1_INTERVAL = 20;
    private static final int   SHAKE_STAGE_2_INTERVAL = 10;
    private static final int   SHAKE_STAGE_3_INTERVAL = 5;
    private static final float SHAKE_STAGE_1_BASE     = 0.08f;
    private static final float SHAKE_STAGE_2_BASE     = 0.16f;
    private static final float SHAKE_STAGE_3_BASE     = 0.22f;
    private static final float SHAKE_STAGE_3_PEAK     = 0.38f;

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerUpgradeRitual(PlayerEntity player, RitualManager.RitualType type) {
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
        return 3;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            default -> ticks;
        };
    }

    private float stageProgress() {
        int t = stageLocalTick();
        return switch (currentStage()) {
            case 1 -> (float) t / STAGE_1_TICKS;
            case 2 -> (float) t / STAGE_2_TICKS;
            case 3 -> (float) t / STAGE_3_TICKS;
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
    }

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.55f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.5f, 0.3f);
        }
        double progress = (double) t / STAGE_1_TICKS;
        if (t % SHAKE_STAGE_1_INTERVAL == 0)
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_1_BASE + (float) progress * 0.04f);
        if (t % 20 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.PLAYERS, 0.5f, 0.4f + (float) progress * 0.4f);
    }

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.7f, 0.65f);
        }
        double progress = (double) t / STAGE_2_TICKS;
        if (t % SHAKE_STAGE_2_INTERVAL == 0)
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8,
                    SHAKE_STAGE_2_BASE + (float) progress * (SHAKE_STAGE_3_BASE - SHAKE_STAGE_2_BASE));
        if (progress > 0.20f && progress < 0.88f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (t % STAGE_2_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_2_MIN_HEALTH + STAGE_2_DAMAGE_PER_TICK)
                sp.damage(world.getDamageSources().magic(), STAGE_2_DAMAGE_PER_TICK);
        }
        if (t == 20) world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.6f, 0.5f);
        if (t == 38) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 0.8f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0f, 0.9f);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_PEAK);
        }
        double progress = (double) t / STAGE_3_TICKS;
        int shakeInterval = (progress < 0.50) ? SHAKE_STAGE_3_INTERVAL : SHAKE_STAGE_2_INTERVAL;
        if (t % shakeInterval == 0) {
            float intensity = Math.max(SHAKE_STAGE_1_BASE, SHAKE_STAGE_3_BASE * (float)(1.0 - progress * 0.65));
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }
        if (progress < 0.60f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 15, 0, true, false, false));
        if (t % 6 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS, 0.5f + (float) progress * 0.4f, 0.7f + (float) progress * 0.8f);
    }

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        PowerManager.setLevel(sp, 2);
        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.heal(4.0f);
        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.20f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8f, 1.4f);
        sp.sendMessage(net.minecraft.text.Text.literal("§dYour bond has been strengthened to Level 2."), false);
        sendFx(world, sp, 0, 0, 1.0f);
    }

    private void sendFx(ServerWorld world, ServerPlayerEntity sp, int stage, int stageTick, float progress) {
        RitualFxPayload payload = new RitualFxPayload(
                RitualFxPayload.POWER_UPGRADE,
                sp.getX(), sp.getY(), sp.getZ(),
                stage, stageTick, progress, world.getTime());
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, sp.getBlockPos()).forEach(viewers::add);
        viewers.add(sp);
        viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
    }
}
