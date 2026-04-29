package com.yourname.loopypowers.effect;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.network.RenderPackets;
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
import org.joml.Vector3f;

public class FracturedEffect extends StatusEffect {
    public FracturedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x4B0082); // Dark Violet

        // Slowness
        this.addAttributeModifier(
                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                "7101b44b-4b2a-4a2a-8b1b-1b2b3b4b5b6b",
                -0.90f,
                EntityAttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // tick every frame
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;

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
            }
        }
    }
}