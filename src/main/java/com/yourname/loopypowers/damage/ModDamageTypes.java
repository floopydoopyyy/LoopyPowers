package com.yourname.loopypowers.damage;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
// registers custom damage types for silly death messages

public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final RegistryKey<DamageType> BLEED =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "bleed"));
    public static final RegistryKey<DamageType> BIND =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "bind"));
    public static final RegistryKey<DamageType> OVERDRIVE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "overdrive"));
    public static final RegistryKey<DamageType> RUSH =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "rush"));
    public static final RegistryKey<DamageType> FRENZY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "frenzy"));
    public static final RegistryKey<DamageType> THUNDERCLAP =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "thunderclap"));
    public static final RegistryKey<DamageType> SMITE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "smite"));
    public static final RegistryKey<DamageType> SONIC = // note: this is the flight one, not sound one
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "sonic"));
    public static final RegistryKey<DamageType> SOUND =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "sound"));
    // Explosion
    public static final RegistryKey<DamageType> SUPER_EXPLOSION =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "super_explosion"));
    // Ice
    public static final RegistryKey<DamageType> ICE_SPIKE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "ice_spike"));
    public static final RegistryKey<DamageType> ICE_SHATTER =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "ice_shatter"));
    public static final RegistryKey<DamageType> ICE_SHOCKWAVE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "ice_shockwave"));
    public static final RegistryKey<DamageType> ICE_BEAM =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "ice_beam"));
    // Nature
    public static final RegistryKey<DamageType> THORN =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "thorn"));
    public static final RegistryKey<DamageType> VINE_BIND =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "vine_bind"));
    // Strength
    public static final RegistryKey<DamageType> SLAM =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "slam"));
    public static final RegistryKey<DamageType> RUSH_COLLISION =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "rush_collision"));
    // Healing
    public static final RegistryKey<DamageType> ABSORB =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "absorb"));

    // BLOOD
    // bleed
    public static DamageSource bleed(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEED);
        return new DamageSource(entry, attacker, attacker);
    }
    //
    public static DamageSource bleed(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEED);
        return new DamageSource(entry);
    }
    // pact
    public static DamageSource bind(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BIND);
        return new DamageSource(entry, attacker, attacker);
    }
    // speed
    public static DamageSource overdrive(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(OVERDRIVE);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource rush(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH);
        return new DamageSource(entry, attacker, attacker);
    }
    // teleport
    public static DamageSource frenzy(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRENZY);
        return new DamageSource(entry, attacker, attacker);
    }
    // lightning
    public static DamageSource thunderclap(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THUNDERCLAP);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource smite(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMITE);
        return new DamageSource(entry, attacker, attacker);
    }
    // flight
    public static DamageSource sonic(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SONIC);
        return new DamageSource(entry, attacker, attacker);
    }
    // sound
    public static DamageSource sound(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SOUND);
        return new DamageSource(entry, attacker, attacker);
    }

    // explosion
    public static DamageSource superExplosion(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SUPER_EXPLOSION);
        return new DamageSource(entry, attacker, attacker);
    }

    // ice
    public static DamageSource iceSpike(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SPIKE);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource iceShatter(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHATTER);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource iceShockwave(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHOCKWAVE);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource iceBeam(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_BEAM);
        return new DamageSource(entry, attacker, attacker);
    }
    // nature
    public static DamageSource thorn(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THORN);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource vineBind(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(VINE_BIND);
        return new DamageSource(entry, attacker, attacker);
    }
    // nature - thorns (block damage, no attacker)
    public static DamageSource thorn(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THORN);
        return new DamageSource(entry);
    }

    // nature - vine bind tick damage (no attacker)
    public static DamageSource vineBind(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(VINE_BIND);
        return new DamageSource(entry);
    }

    // strength
    public static DamageSource slam(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SLAM);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource rushCollision(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH_COLLISION);
        return new DamageSource(entry, attacker, attacker);
    }

    // healing
    public static DamageSource absorb(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ABSORB);
        return new DamageSource(entry);
    }

    public static void init() {
        // are these even needed?
    }
}