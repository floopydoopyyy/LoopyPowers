package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
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

public class SeveranceRitual implements Ritual {

    private static final int STAGE_1_TICKS = 65;
    private static final int STAGE_2_TICKS = 55;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 75;
    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.5f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 8;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public SeveranceRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

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
        sp.removeStatusEffect(StatusEffects.DARKNESS);
        sp.removeStatusEffect(StatusEffects.WEAKNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
    }

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.7f, 0.4f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_GRAVEL_PLACE, SoundCategory.PLAYERS, 0.5f, 0.5f);
        }
        double progress = (double) t / STAGE_1_TICKS;
        if (t % 22 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                    SoundCategory.PLAYERS, 0.5f, 0.35f + (float) progress * 0.2f);
    }

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.5f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_SOUL_SAND_HIT, SoundCategory.PLAYERS, 0.8f, 0.6f);
        }
        double progress = (double) t / STAGE_2_TICKS;
        if (progress > 0.4f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 0, true, false, false));
        if (t == 20) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 0.4f, 0.6f);
        if (t == 42) world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.5f, 0.55f);
    }

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.9f, 0.7f);
        }
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 30, 0, true, false, false));
        double progress = (double) t / STAGE_3_TICKS;
        if (t % 14 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS, (float)(0.5 + progress * 0.4), 0.55f + (float) progress * 0.35f);
        if (t == 28)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
    }

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.1f, 0.5f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 0.7f);
        }
        double progress = (double) t / STAGE_4_TICKS;
        if (progress > 0.10f && progress < 0.80f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0, true, false, false));
        if (progress > 0.05f && progress < 0.70f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 25, 0, true, false, false));
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 1, true, false, false));
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK)
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
        }
        if (t % 8 == 0 && progress < 0.60f)
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS, (float)(0.4 + (1.0 - progress) * 0.3), 0.45f + (float) progress * 0.3f);
    }

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        PowerManager.removePower(sp);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.DARKNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
        sp.removeStatusEffect(StatusEffects.WITHER);
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 0, false, true, true));
        world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WITHER_DEATH, SoundCategory.PLAYERS, 0.8f, 1.2f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_SOUL_SAND_HIT, SoundCategory.PLAYERS, 0.6f, 0.5f);
        sp.sendMessage(net.minecraft.text.Text.literal("§8Your power has been stripped."), true);
        sendFx(world, sp, 0, 0, 1.0f);
    }

    private void sendFx(ServerWorld world, ServerPlayerEntity sp, int stage, int stageTick, float progress) {
        RitualFxPayload payload = new RitualFxPayload(
                RitualFxPayload.SEVERANCE,
                sp.getX(), sp.getY(), sp.getZ(),
                stage, stageTick, progress, world.getTime());
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, sp.getBlockPos()).forEach(viewers::add);
        viewers.add(sp);
        viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
    }
}
