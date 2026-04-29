package com.yourname.loopypowers.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEffects {

    public static final StatusEffect FATE = new FateEffect();
    public static final StatusEffect BLOODBOUND = new BloodBoundEffect();
    public static final StatusEffect BLEED = new BloodBoundEffect();
    public static final StatusEffect EXPOSED = new ExposedEffect();
    public static final StatusEffect TETHERED = new TetheredEffect();
    public static final StatusEffect STUN = new StunEffect();
    public static final StatusEffect DEEPFREEZE = new DeepFreezeEffect();
    public static final StatusEffect FRACTURED = new FracturedEffect();
    public static final StatusEffect COMPELLED = new CompelledEffect();
    public static final StatusEffect POSSESSED = new PossessedEffect();
    public static final StatusEffect GROUNDED = new GroundedEffect();

    public static void register() {
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "fate"), FATE);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "bloodbound"), BLOODBOUND);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "bleed"), BLEED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "exposed"), EXPOSED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "tethered"), TETHERED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "stunned"), STUN);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "deepfreeze"), DEEPFREEZE);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "fractured"), FRACTURED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "compelled"), COMPELLED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "possessed"), POSSESSED);
        Registry.register(Registries.STATUS_EFFECT, new Identifier("loopypowers", "grounded"), GROUNDED);
    }
}