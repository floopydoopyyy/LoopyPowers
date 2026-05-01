package com.yourname.loopypowers.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class BracedEffect extends StatusEffect {
    public BracedEffect() {
        // Beneficial effect, color: light gray/white
        super(StatusEffectCategory.BENEFICIAL, 0xDDDDDD);
    }
}