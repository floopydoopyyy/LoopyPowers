package com.yourname.loopypowers.effect;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier; // Added for 1.21.1
import org.joml.Vector3f;

public class FracturedEffect extends StatusEffect {
    public FracturedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4B0082); // Dark Violet

        // 1.21.1 Attribute Modifier change! No more UUIDs, and MULTIPLY_TOTAL was renamed.
        this.addAttributeModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                Identifier.of("loopypowers", "fractured_slowness"),
                -0.50f,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // tick every frame
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) { // Changed void to boolean
        if (entity.getWorld().isClient) return true; // Changed to return true

        // every 15 ticks
        if (entity.age % 15 == 0) {
            // damage
            entity.damage(ModDamageTypes.fracture(entity.getWorld(), null), 1.5f);

            // flicker
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 4, 0, true, false, false));
            if (entity instanceof ServerPlayerEntity p) {
                RenderPackets.hidePlayerFromOthers(p, 4);
            }

            // fx
            if (entity.getWorld() instanceof ServerWorld world) {
                DustParticleEffect darkBlue = new DustParticleEffect(new Vector3f(0.05f, 0.1f, 0.4f), 1.4f);
                DustParticleEffect midBlue = new DustParticleEffect(new Vector3f(0.2f, 0.4f, 1.0f), 1.2f);
                DustParticleEffect brightBlue = new DustParticleEffect(new Vector3f(0.6f, 0.8f, 1.0f), 0.9f);

                double x = entity.getX();
                double y = entity.getBodyY(0.5);
                double z = entity.getZ();

                world.spawnParticles(brightBlue, x, y, z, 8, 0.4, 0.6, 0.4, 0.02);
                world.spawnParticles(midBlue, x, y, z, 5, 0.3, 0.5, 0.3, 0.01);
                world.spawnParticles(darkBlue, x, y, z, 2, 0.2, 0.3, 0.2, 0.01);

                // Play random flicker sound
                net.minecraft.sound.SoundEvent flickerSound = switch (world.random.nextInt(3)) {
                    case 0 -> ModSounds.FLICKER;
                    case 1 -> ModSounds.FLICKER2;
                    default -> ModSounds.FLICKER3;
                };

                world.playSound(null, entity.getBlockPos(), flickerSound, SoundCategory.PLAYERS, 0.5f, 1.0f);
            }
        }
        return true; // MUST return true at the end
    }
}