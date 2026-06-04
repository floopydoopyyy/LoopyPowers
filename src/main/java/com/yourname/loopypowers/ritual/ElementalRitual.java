package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.payload.RitualFxPayload;
import com.yourname.loopypowers.power.*;
import com.yourname.loopypowers.sound.ModSounds;
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
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class ElementalRitual implements Ritual {

    private static final List<Supplier<Power>> ELEMENTAL_POWERS = List.of(
            FirePower::new, IcePower::new, LightningPower::new);

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;
    private static final int TOTAL_TICKS = STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public ElementalRitual(PlayerEntity player, RitualManager.RitualType type) {
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

        if (ticks == 1 || ticks % 28 == 0)
            world.playSound(null, sp.getBlockPos(), ModSounds.RITUALLOOP, SoundCategory.PLAYERS, 0.5f, 0.7f);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        if (stage < 4) lockPosition(sp);

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
    }

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), ModSounds.RITUALSTART, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_MOSS_PLACE, SoundCategory.PLAYERS, 1.0f, 0.7f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 1.0f);
        }
        double progress = (double) t / STAGE_1_TICKS;
        if (t % 30 == 0)
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_WATER_AMBIENT,
                    SoundCategory.PLAYERS, 0.3f, 0.9f + (float) progress * 0.2f);
    }

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1)  world.playSound(null, sp.getBlockPos(), ModSounds.DARKNESSTELEPORT2, SoundCategory.PLAYERS, 0.6f, 0.9f);
        if (t == 15) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, 0.4f, 1.1f);
        if (t == 30) world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_POWDER_SNOW_FALL, SoundCategory.PLAYERS, 0.5f, 0.8f);
        if (t == 45) world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.9f);
        double progress = (double) t / STAGE_3_TICKS;
        if (t % 18 == 0) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_BLAZE_AMBIENT, SoundCategory.PLAYERS, (float)(0.3 + progress * 0.4), 0.8f + (float) progress * 0.3f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.PLAYERS, (float)(0.3 + progress * 0.3), 1.0f);
        }
    }

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.3f, 0.6f);
            world.playSound(null, sp.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 1.0f);
        }
        double progress = (double) t / STAGE_4_TICKS;
        if (progress > 0.15f && progress < 0.88f)
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0, true, false, false));
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK)
                sp.damage(ModDamageTypes.ritual(world), STAGE_4_DAMAGE_PER_TICK);
        }
        if (t % 5 == 0)
            world.playSound(null, sp.getBlockPos(), ModSounds.DARKNESSLOOP, SoundCategory.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f);
    }

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory = ELEMENTAL_POWERS.get(sp.getRandom().nextInt(ELEMENTAL_POWERS.size()));
        PowerManager.setPower(sp, factory.get());
        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.extinguish();
        sp.heal(3.0f);
        world.playSound(null, sp.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        sendFx(world, sp, 0, 0, 1.0f);
    }

    private void sendFx(ServerWorld world, ServerPlayerEntity sp, int stage, int stageTick, float progress) {
        RitualFxPayload payload = new RitualFxPayload(
                RitualFxPayload.ELEMENTAL,
                sp.getX(), sp.getY(), sp.getZ(),
                stage, stageTick, progress, world.getTime());
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, sp.getBlockPos()).forEach(viewers::add);
        viewers.add(sp);
        viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
    }
}
