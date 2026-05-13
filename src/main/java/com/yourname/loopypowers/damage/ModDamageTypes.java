package com.yourname.loopypowers.damage;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class ModDamageTypes {
    private ModDamageTypes() {}

    // ============================================================
    // REGISTRY KEYS (1.21.1 - Identifier.of)
    // ============================================================

    public static final RegistryKey<DamageType> BLEED = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "bleed"));
    public static final RegistryKey<DamageType> BIND = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "bind"));
    public static final RegistryKey<DamageType> OVERDRIVE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "overdrive"));
    public static final RegistryKey<DamageType> RUSH = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "rush"));
    public static final RegistryKey<DamageType> FRENZY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "frenzy"));
    public static final RegistryKey<DamageType> THUNDERCLAP = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "thunderclap"));
    public static final RegistryKey<DamageType> SMITE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "smite"));
    public static final RegistryKey<DamageType> SONIC = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "sonic"));
    public static final RegistryKey<DamageType> SOUND = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "sound"));
    public static final RegistryKey<DamageType> SUPER_EXPLOSION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "super_explosion"));
    public static final RegistryKey<DamageType> EXPLOSION_NORMAL = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "explosion_normal"));
    public static final RegistryKey<DamageType> ICE_SPIKE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "ice_spike"));
    public static final RegistryKey<DamageType> ICE_SHATTER = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "ice_shatter"));
    public static final RegistryKey<DamageType> ICE_SHOCKWAVE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "ice_shockwave"));
    public static final RegistryKey<DamageType> ICE_BEAM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "ice_beam"));
    public static final RegistryKey<DamageType> THORN = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "thorn"));
    public static final RegistryKey<DamageType> VINE_BIND = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "vine_bind"));
    public static final RegistryKey<DamageType> SLAM = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "slam"));
    public static final RegistryKey<DamageType> RUSH_COLLISION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "rush_collision"));
    public static final RegistryKey<DamageType> ABSORB = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "absorb"));
    public static final RegistryKey<DamageType> SMOOTHING = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "smoothing"));
    public static final RegistryKey<DamageType> FATE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "fate"));
    public static final RegistryKey<DamageType> COSMIC_RAY = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "cosmic_ray"));
    public static final RegistryKey<DamageType> SHOOTING_STAR = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "shooting_star"));
    public static final RegistryKey<DamageType> BLACK_HOLE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "black_hole"));
    public static final RegistryKey<DamageType> BACKSTAB = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "backstab"));
    public static final RegistryKey<DamageType> ULTIMATE_STAB = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "darkult"));
    public static final RegistryKey<DamageType> PHASE_BURST = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "phase_burst"));
    public static final RegistryKey<DamageType> FRACTURE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "fracture"));
    public static final RegistryKey<DamageType> BET = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "bet"));
    public static final RegistryKey<DamageType> DUEL = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "duel"));
    public static final RegistryKey<DamageType> HOUSE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "house"));
    public static final RegistryKey<DamageType> WALL_COLLISION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "wall_collision"));
    public static final RegistryKey<DamageType> STRANGLE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "strangle"));
    public static final RegistryKey<DamageType> BLOCK_THROW = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "block_throw"));
    public static final RegistryKey<DamageType> DEBRIS_ORBIT = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "debris_orbit"));
    public static final RegistryKey<DamageType> FIRE_POWER = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "fire_power"));
    public static final RegistryKey<DamageType> FIRE_EXPLOSION = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "fire_explosion"));
    public static final RegistryKey<DamageType> RITUAL = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "ritual"));
    public static final RegistryKey<DamageType> ONEPUNCH = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "onepunch"));
    public static final RegistryKey<DamageType> ABSORBPULSE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "absorbpulse"));
    public static final RegistryKey<DamageType> BLOODSELF = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Loopypowers.MOD_ID, "bloodself"));

    // ============================================================
    // DAMAGE SOURCE GENERATORS
    // ============================================================

    // BLOOD
    public static DamageSource bleed(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEED), attacker, attacker); }
    public static DamageSource bleed(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLEED)); }

    public static DamageSource bind(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BIND), attacker, attacker); }
    public static DamageSource bind(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BIND)); }

    public static DamageSource bloodself(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLOODSELF), attacker, attacker); }
    public static DamageSource bloodself(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLOODSELF)); }

    // SPEED / COMBAT
    public static DamageSource overdrive(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(OVERDRIVE), attacker, attacker); }
    public static DamageSource overdrive(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(OVERDRIVE)); }

    public static DamageSource rush(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH), attacker, attacker); }
    public static DamageSource rush(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH)); }

    public static DamageSource frenzy(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRENZY), attacker, attacker); }
    public static DamageSource frenzy(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRENZY)); }

    // LIGHTNING / SOUND
    public static DamageSource thunderclap(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THUNDERCLAP), attacker, attacker); }
    public static DamageSource thunderclap(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THUNDERCLAP)); }

    public static DamageSource smite(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMITE), attacker, attacker); }
    public static DamageSource smite(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMITE)); }

    public static DamageSource sonic(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SONIC), attacker, attacker); }
    public static DamageSource sonic(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SONIC)); }

    public static DamageSource sound(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SOUND), attacker, attacker); }
    public static DamageSource sound(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SOUND)); }

    // EXPLOSION
    public static DamageSource superExplosion(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SUPER_EXPLOSION), attacker, attacker); }
    public static DamageSource superExplosion(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SUPER_EXPLOSION)); }

    public static DamageSource explosionNormal(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(EXPLOSION_NORMAL), attacker, attacker); }
    public static DamageSource explosionNormal(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(EXPLOSION_NORMAL)); }

    // ICE
    public static DamageSource iceSpike(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SPIKE), attacker, attacker); }
    public static DamageSource iceSpike(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SPIKE)); }

    public static DamageSource iceShatter(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHATTER), attacker, attacker); }
    public static DamageSource iceShatter(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHATTER)); }

    public static DamageSource iceShockwave(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHOCKWAVE), attacker, attacker); }
    public static DamageSource iceShockwave(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_SHOCKWAVE)); }

    public static DamageSource iceBeam(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_BEAM), attacker, attacker); }
    public static DamageSource iceBeam(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ICE_BEAM)); }

    // NATURE
    public static DamageSource thorn(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THORN), attacker, attacker); }
    public static DamageSource thorn(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(THORN)); }

    public static DamageSource vineBind(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(VINE_BIND), attacker, attacker); }
    public static DamageSource vineBind(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(VINE_BIND)); }

    // STRENGTH
    public static DamageSource slam(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SLAM), attacker, attacker); }
    public static DamageSource slam(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SLAM)); }

    public static DamageSource rushCollision(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH_COLLISION), attacker, attacker); }
    public static DamageSource rushCollision(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RUSH_COLLISION)); }

    public static DamageSource onePunch(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ONEPUNCH), attacker, attacker); }
    public static DamageSource onePunch(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ONEPUNCH)); }

    // HEALING
    public static DamageSource absorb(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ABSORB), attacker, attacker); }
    public static DamageSource absorb(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ABSORB)); }

    public static DamageSource smoothing(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMOOTHING), attacker, attacker); }
    public static DamageSource smoothing(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SMOOTHING)); }

    public static DamageSource absorbpulse(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ABSORBPULSE), attacker, attacker); }
    public static DamageSource absorbpulse(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ABSORBPULSE)); }

    // COSMIC
    public static DamageSource fate(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FATE), attacker, attacker); }
    public static DamageSource fate(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FATE)); }

    public static DamageSource cosmicRay(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(COSMIC_RAY), attacker, attacker); }
    public static DamageSource cosmicRay(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(COSMIC_RAY)); }

    public static DamageSource shootingStar(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHOOTING_STAR), attacker, attacker); }
    public static DamageSource shootingStar(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHOOTING_STAR)); }

    public static DamageSource blackHole(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLACK_HOLE), attacker, attacker); }
    public static DamageSource blackHole(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLACK_HOLE)); }

    // DARKNESS
    public static DamageSource darknessBackstab(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BACKSTAB), attacker, attacker); }
    public static DamageSource darknessBackstab(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BACKSTAB)); }

    public static DamageSource darkUlt(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ULTIMATE_STAB), attacker, attacker); }
    public static DamageSource darkUlt(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(ULTIMATE_STAB)); }

    // DIMENSIONAL
    public static DamageSource phaseBurst(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(PHASE_BURST), attacker, attacker); }
    public static DamageSource phaseBurst(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(PHASE_BURST)); }

    public static DamageSource fracture(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRACTURE), attacker, attacker); }
    public static DamageSource fracture(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FRACTURE)); }

    // FORTUNE
    public static DamageSource bet(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BET), attacker, attacker); }
    public static DamageSource bet(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BET)); }

    public static DamageSource duel(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DUEL), attacker, attacker); }
    public static DamageSource duel(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DUEL)); }

    public static DamageSource house(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(HOUSE), attacker, attacker); }
    public static DamageSource house(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(HOUSE)); }

    // TELEKINESIS
    public static DamageSource wallCollision(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(WALL_COLLISION), attacker, attacker); }
    public static DamageSource wallCollision(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(WALL_COLLISION)); }

    public static DamageSource strangle(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(STRANGLE), attacker, attacker); }
    public static DamageSource strangle(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(STRANGLE)); }

    public static DamageSource blockThrow(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLOCK_THROW), attacker, attacker); }
    public static DamageSource blockThrow(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BLOCK_THROW)); }

    public static DamageSource debrisOrbit(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DEBRIS_ORBIT), attacker, attacker); }
    public static DamageSource debrisOrbit(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DEBRIS_ORBIT)); }

    // FIRE
    public static DamageSource firePower(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FIRE_POWER), attacker, attacker); }
    public static DamageSource firePower(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FIRE_POWER)); }

    public static DamageSource fireExplosion(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FIRE_EXPLOSION), attacker, attacker); }
    public static DamageSource fireExplosion(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(FIRE_EXPLOSION)); }

    // MISC
    public static DamageSource ritual(World world, Entity attacker) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RITUAL), attacker, attacker); }
    public static DamageSource ritual(World world) { return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(RITUAL)); }

    public static void init() {
        // Class load hook
    }
}