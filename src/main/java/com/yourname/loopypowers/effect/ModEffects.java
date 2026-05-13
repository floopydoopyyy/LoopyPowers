package com.yourname.loopypowers.effect;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEffects {

    public static final StatusEffect FATE = new FateEffect();
    public static final StatusEffect BLOODBOUND = new BloodBoundEffect();
    public static final StatusEffect BLEED = new BleedEffect(); // remember to fix this in the other branches please
    public static final StatusEffect EXPOSED = new ExposedEffect();
    public static final StatusEffect TETHERED = new TetheredEffect();
    public static final StatusEffect STUN = new StunEffect();
    public static final StatusEffect DEEPFREEZE = new DeepFreezeEffect();
    public static final StatusEffect FRACTURED = new FracturedEffect();
    public static final StatusEffect COMPELLED = new CompelledEffect();
    public static final StatusEffect POSSESSED = new PossessedEffect();
    public static final StatusEffect GROUNDED = new GroundedEffect();
    public static final StatusEffect BRACED = new BracedEffect();
    public static final StatusEffect DISPLACED = new DisplacedEffect();

    public static void register() {
        // 1.21.1 - Identifier.of
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "fate"), FATE);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "bloodbound"), BLOODBOUND);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "bleed"), BLEED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "exposed"), EXPOSED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "tethered"), TETHERED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "stunned"), STUN);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "deepfreeze"), DEEPFREEZE);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "fractured"), FRACTURED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "compelled"), COMPELLED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "possessed"), POSSESSED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "grounded"), GROUNDED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "braced"), BRACED);
        Registry.register(Registries.STATUS_EFFECT, Identifier.of("loopypowers", "displaced"), DISPLACED);
    }
}