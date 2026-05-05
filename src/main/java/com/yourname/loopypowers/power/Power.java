package com.yourname.loopypowers.power;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.damage.DamageSource;

// This is an interface with mainly getter methods

public interface Power { // Basis for all powers

    // Called once when the player gets the power
    void onAssign(ServerPlayerEntity player);

    // Called when the player loses the power (switches classes)
    default void onRemove(ServerPlayerEntity player) {}

    // Called when the player dies
    default void onDeath(ServerPlayerEntity player) {}

    // Called every tick (20 times per second)
    void onTick(ServerPlayerEntity player);

    // try wrappers for abilities that can fail.
    // if they don't exist, the attempt should succeed.
    default boolean tryActivatePrimary(ServerPlayerEntity player) {
        activatePrimary(player);
        return true;
    }

    default boolean tryActivateSecondary(ServerPlayerEntity player) {
        activateSecondary(player);
        return true;
    }

    default boolean tryActivateUltimate(ServerPlayerEntity player) {
        activateUltimate(player);
        return true;
    }

    // Active abilities
    void activatePrimary(ServerPlayerEntity player);
    void activateSecondary(ServerPlayerEntity player);
    long getPrimaryCooldownMs();
    long getSecondaryCooldownMs();

    // Ultimate
    void activateUltimate(ServerPlayerEntity player);
    long getUltimateCooldownMs();

    // Ability Names
    default String getPassiveName() { return "Passive"; }
    default String getPrimaryName() { return "Primary"; }
    default String getSecondaryName() { return "Secondary"; }
    default String getUltimateName() { return "Ultimate"; }

    // passive on hit effects (Attacker side)
    default void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // default = nothing
    }

    // when dealing damage
    // Returns true to allow the damage, false to cancel it.
    default boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, net.minecraft.entity.damage.DamageSource source, float amount) {
        return true;
    }

    // defensive damage hook
    // Returns true to allow the damage to proceed, false to cancel the damage entirely.
    default boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        return true;
    }

    String getName();

    // HELP / DESCRIPTIONS - for help command
    default String getOverviewDescription() {
        return "No overview description set yet.";
    }

    default String getPassiveDescription() {
        return "No passive description set yet.";
    }

    default String getPrimaryDescription() {
        return "No primary description set yet.";
    }

    default String getSecondaryDescription() {
        return "No secondary description set yet.";
    }

    default String getUltimateDescription() {
        return "No ultimate description set yet.";
    }
}