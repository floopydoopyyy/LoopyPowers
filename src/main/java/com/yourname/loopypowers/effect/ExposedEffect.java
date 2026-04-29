package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class ExposedEffect extends StatusEffect {

    public ExposedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x000000 ); // black
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        // handled by darkness power, this is purely visual
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}
