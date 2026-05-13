package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class BloodBoundEffect extends StatusEffect {

    public BloodBoundEffect() {
        super(StatusEffectCategory.HARMFUL, 0x5C1010 ); // red
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) { // CHANGED void to boolean
        // handled by blood power, this is purely visual
        return true; // ADDED
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}