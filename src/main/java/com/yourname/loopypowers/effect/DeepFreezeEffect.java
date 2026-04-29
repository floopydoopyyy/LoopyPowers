package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class DeepFreezeEffect extends StatusEffect {

    public DeepFreezeEffect() {
        super(StatusEffectCategory.HARMFUL, 0x00BFFF ); // light blue
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // handled by ice power, this is purely visual
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}
