package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3f;

public class BleedEffect extends StatusEffect {

    public BleedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x5C1010); // red
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // only blood fx
        if (!entity.getWorld().isClient() && entity.getWorld().getTime() % 10 == 0) {
            ServerWorld sw = (ServerWorld) entity.getWorld();
            sw.spawnParticles(new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.0f), 0.8f),
                    entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    1, 0.2, 0.4, 0.2, 0.01);
        }
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}