package com.yourname.loopypowers.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

public class PossessedEffect extends StatusEffect {
    public PossessedEffect() {
        super(StatusEffectCategory.HARMFUL, 0x800000); // Maroon
    }
}