package com.yourname.loopypowers.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class TetheredEffect extends StatusEffect {

    public TetheredEffect() {
        super(StatusEffectCategory.HARMFUL, 0x013220 ); // dark green
    }

    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        // handled by nature power, this is purely visual
        return true;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}
