package com.yourname.loopypowers.effect;

import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

public class StunEffect extends StatusEffect {

    // Bright yellow dust for the "cartoon stars" effect
    private static final DustParticleEffect STUN_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.1f), 1.0f);

    public StunEffect() {
        super(StatusEffectCategory.HARMFUL, 0xF7CB15); // grey yellow

        // reduce movement speed
        this.addAttributeModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                Identifier.of("loopypowers", "stun_slow"),
                -0.80f,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL //
        );

        // reduce weapon swing speed
        this.addAttributeModifier(
                EntityAttributes.GENERIC_ATTACK_SPEED,
                Identifier.of("loopypowers", "stun_attack_speed"),
                -0.50f,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL //
        );

        // reduce attack damage
        this.addAttributeModifier(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                Identifier.of("loopypowers", "stun_damage"),
                -4.0f,
                EntityAttributeModifier.Operation.ADD_VALUE //
        );
    }

    @Override
    public void onApplied(LivingEntity entity, int amplifier) {
        super.onApplied(entity, amplifier);

        // Play the ringing sound exactly when effect applied
        if (!entity.getWorld().isClient && entity instanceof ServerPlayerEntity player) {
            // Route sound through the world
            player.getWorld().playSound(null, player.getBlockPos(), ModSounds.EARRING, SoundCategory.PLAYERS, 1.5f, 1.0f);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // Forces the effect to tick every single frame
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return true; // Changed to return true

        // STARS
        if (entity.getWorld() instanceof ServerWorld world) {
            double radius = 0.55; // Distance from the center of the head
            double height = entity.getY() + entity.getHeight() + 0.35; // Just above the head

            // Use the entity's age to rotate the particles
            double angle1 = (entity.age * 0.25) % (2 * Math.PI); // 0.25 controls the rotation speed
            double angle2 = angle1 + Math.PI; // Offset the second star by 180 degrees

            double x1 = entity.getX() + Math.cos(angle1) * radius;
            double z1 = entity.getZ() + Math.sin(angle1) * radius;

            double x2 = entity.getX() + Math.cos(angle2) * radius;
            double z2 = entity.getZ() + Math.sin(angle2) * radius;

            // Spawn the particles with 0 velocity so they form a  orbit
            world.spawnParticles(STUN_DUST, x1, height, z1, 1, 0, 0, 0, 0);
            world.spawnParticles(STUN_DUST, x2, height, z2, 1, 0, 0, 0, 0);
        }

        if (entity instanceof ServerPlayerEntity p) {
            // Player Stun Logic
            if (p.age % 10 == 0) { // throttles audio packets

                int duration = 0;
                for (StatusEffectInstance instance : p.getStatusEffects()) {
                    if (instance.getEffectType().value() == this) {
                        duration = instance.getDuration();
                        break;
                    }
                }

                if (duration > 0) {
                    RenderPackets.sendStunAudio(p, duration);
                }
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

        return true;
    }
}