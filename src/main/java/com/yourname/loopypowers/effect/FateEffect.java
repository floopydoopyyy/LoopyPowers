package com.yourname.loopypowers.effect;

import com.yourname.loopypowers.power.CosmicPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.server.world.ServerWorld;

public class FateEffect extends StatusEffect {

    public FateEffect() {
        super(StatusEffectCategory.HARMFUL, 0xFFFFFF); // white
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
       // handled by cosmic power, this is purely visual
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true; // runs every tick
    }
}
