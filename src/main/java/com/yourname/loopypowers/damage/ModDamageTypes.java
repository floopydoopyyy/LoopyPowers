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
    public static final RegistryKey<DamageType> SMOOTHING =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "smoothing"));

    // cosmic
    public static final RegistryKey<DamageType> FATE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "fate"));
    public static final RegistryKey<DamageType> COSMIC_RAY =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "cosmic_ray"));
    public static final RegistryKey<DamageType> SHOOTING_STAR =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "shooting_star"));
    public static final RegistryKey<DamageType> BLACK_HOLE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "black_hole"));

    // darkness
    public static final RegistryKey<DamageType> BACKSTAB =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "backstab"));
    public static final RegistryKey<DamageType> ULTIMATE_STAB =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "darkult"));

    // dimensional
    public static final RegistryKey<DamageType> PHASE_BURST =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "phase_burst"));
    public static final RegistryKey<DamageType> FRACTURE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "fracture"));

    // fortune
    public static final RegistryKey<DamageType> BET =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "bet"));
    public static final RegistryKey<DamageType> DUEL =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "duel"));
    public static final RegistryKey<DamageType> HOUSE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "house"));

    // telekinesis
    public static final RegistryKey<DamageType> WALL_COLLISION =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "wall_collision"));
    public static final RegistryKey<DamageType> STRANGLE =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "strangle"));
    public static final RegistryKey<DamageType> BLOCK_THROW =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "block_throw"));
    public static final RegistryKey<DamageType> DEBRIS_ORBIT =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "debris_orbit"));

    // misc
    public static final RegistryKey<DamageType> RITUAL =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier(Loopypowers.MOD_ID, "ritual"));

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
    public static DamageSource smoothing(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMOOTHING);
        return new DamageSource(entry);
    }

    // cosmic
    public static DamageSource fate(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FATE);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource cosmicRay(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(COSMIC_RAY);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource shootingStar(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHOOTING_STAR);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource blackHole(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLACK_HOLE);
        return new DamageSource(entry, attacker, attacker);
    }

    // darkness
    public static DamageSource darknessBackstab(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BACKSTAB);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource darkUlt(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ULTIMATE_STAB);
        return new DamageSource(entry, attacker, attacker);
    }

    // dimensional
    public static DamageSource phaseBurst(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(PHASE_BURST);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource fracture(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRACTURE);
        return new DamageSource(entry, attacker, attacker);
    }

    // fortune
    public static DamageSource bet(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BET);
        return new DamageSource(entry);
    }
    public static DamageSource duel(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DUEL);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource house(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(HOUSE);
        return new DamageSource(entry, attacker, attacker);
    }

    // telekinesis
    public static DamageSource wallCollision(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(WALL_COLLISION);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource strangle(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(STRANGLE);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource blockThrow(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLOCK_THROW);
        return new DamageSource(entry, attacker, attacker);
    }
    public static DamageSource debrisOrbit(World world, Entity attacker) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DEBRIS_ORBIT);
        return new DamageSource(entry, attacker, attacker);
    }

    // misc
    public static DamageSource ritual(World world) {
        var entry = world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RITUAL);
        return new DamageSource(entry);
    }

    public static void init() {
        // are these even needed?
    }
}