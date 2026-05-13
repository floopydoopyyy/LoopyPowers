package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class DeepFreezeEffect extends StatusEffect {

    public DeepFreezeEffect() {
        super(StatusEffectCategory.HARMFUL, 0x00BFFF ); // light blue
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) { // CHANGED void to boolean
        // handled by ice power, this is purely visual
        return true; // ADDED
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}