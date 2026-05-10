package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

public class DisplacedEffect extends StatusEffect {

    public DisplacedEffect() {
        //harmful category
        super(StatusEffectCategory.HARMFUL, 0x00FFFF); // cyan
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // Apply every tick
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) return;
        ServerWorld world = (ServerWorld) entity.getWorld();

        // set velocity to 0
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
        entity.fallDistance = 0; // stops fall damage accumulating when frozen

        // Lock position using packets (no more camera lock)
        if (entity instanceof ServerPlayerEntity displacedPlayer) {
            displacedPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            0f, // 0 with relative flags means no camera snapping!
                            0f,
                            java.util.Set.of(
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT
                            ),
                            0
                    )
            );

            // stop mining
            displacedPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 255, true, false, false));

            // stop actions
            if (!displacedPlayer.getMainHandStack().isEmpty()) {
                displacedPlayer.getItemCooldownManager().set(displacedPlayer.getMainHandStack().getItem(), 5);
            }
            displacedPlayer.stopUsingItem();
        }

        // invisibility
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 5, 0, true, false, false));

        // stop damage
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 5, 255, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));

        // disable mob ai
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }

        // fx
        DustParticleEffect darkBlue  = new DustParticleEffect(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleEffect midBlue   = new DustParticleEffect(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleEffect brightBlue = new DustParticleEffect(new Vector3f(0.6f, 0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getBodyY(0.5);
        double z = entity.getZ();

        world.spawnParticles(brightBlue,  x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        world.spawnParticles(midBlue,     x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (world.random.nextFloat() < 0.4f) {
            world.spawnParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }
    }

    @Override
    public void onRemoved(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onRemoved(entity, attributes, amplifier);

        // Restore AI when the effect naturally expires or is cleansed
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(false);
        }
    }
}