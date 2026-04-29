package com.yourname.loopypowers.effect;

import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;

public class StunEffect extends StatusEffect {
    public StunEffect() {
        super(StatusEffectCategory.HARMFUL, 0xF7CB15); // grey yellow

        // reduce movement speed
        this.addAttributeModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "c51ceae4-f860-4b53-8356-9a2cddc48c66",
                -0.80f,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );

        // reduce weapon swing speed
        this.addAttributeModifier(
                EntityAttributes.GENERIC_ATTACK_SPEED,
                "5bfd003b-d3eb-4601-8b2b-0ffc06df9a56",
                -0.50f,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );

        // reduce attack damage
        this.addAttributeModifier(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                "b888eb1a-fbf2-4fb3-81b0-2f3b9c7b949b",
                -4.0f,
                EntityAttributeModifier.Operation.ADDITION
        );
    }

    @Override
    public void onApplied(LivingEntity entity, AttributeContainer attributes, int amplifier) {
        super.onApplied(entity, attributes, amplifier);

        // Play the ringing sound exactly when effect applied
        if (!entity.getWorld().isClient && entity instanceof ServerPlayerEntity player) {
            player.playSound(ModSounds.EARRING, SoundCategory.PLAYERS, 1.5f, 1.0f);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // Forces the effect to tick every single frame
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

        if (entity instanceof ServerPlayerEntity p) {
            // Player Stun Logic
            if (p.age % 10 == 0) { // throttles audio packets
                RenderPackets.sendStunAudio(p, p.getStatusEffect(ModEffects.STUN).getDuration());
            }

            p.setSprinting(false);
            p.fallDistance = 0.0f; // prevents fall damage accumulating
            CameraShake.shake(p, 2, 1.9f);

        } else {
            // Mob Stun Logic
            if (entity instanceof MobEntity mob) {
                mob.getNavigation().stop();
                mob.getMoveControl().strafeTo(0.0f, 0.0f);
                mob.setTarget(null);
            }

            // Lock look direction
            entity.setYaw(entity.prevYaw);
            entity.setPitch(entity.prevPitch);
        }
    }
}