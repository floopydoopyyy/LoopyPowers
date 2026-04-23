package com.yourname.loopypowers.sound;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {

    // IDs
    public static final Identifier THUNDERCLAP_ID     = new Identifier(Loopypowers.MOD_ID, "thunderclap");
    public static final Identifier TELEPORTCLAP_ID    = new Identifier(Loopypowers.MOD_ID, "teleportclap");
    public static final Identifier TELEPORTSNAP_ID    = new Identifier(Loopypowers.MOD_ID, "teleportsnap");
    public static final Identifier DASH_ID            = new Identifier(Loopypowers.MOD_ID, "dash");
    public static final Identifier RUSHSTART_ID       = new Identifier(Loopypowers.MOD_ID, "rushstart");
    public static final Identifier RUSHLOOP_ID        = new Identifier(Loopypowers.MOD_ID, "rushloop");
    public static final Identifier OVERDRIVESTART_ID  = new Identifier(Loopypowers.MOD_ID, "overdrivestart");
    public static final Identifier OVERDRIVELOOP_ID   = new Identifier(Loopypowers.MOD_ID, "overdriveloop");
    public static final Identifier ELECTRICITY_ID     = new Identifier(Loopypowers.MOD_ID, "electricity");
    public static final Identifier RAGE_ID  = new Identifier(Loopypowers.MOD_ID, "rage");
    public static final Identifier BASSDROP_ID        = new Identifier(Loopypowers.MOD_ID, "bassdrop");
    public static final Identifier BASSSINGLE_ID      = new Identifier(Loopypowers.MOD_ID, "basssingle");
    public static final Identifier BULLRUSH_ID        = new Identifier(Loopypowers.MOD_ID, "bullrush");
    public static final Identifier EARRING_ID         = new Identifier(Loopypowers.MOD_ID, "earring");
    public static final Identifier ENTITYSLAM_ID      = new Identifier(Loopypowers.MOD_ID, "entityslam");
    public static final Identifier RAILGUN_ID         = new Identifier(Loopypowers.MOD_ID, "railgun");
    public static final Identifier SLAM_ID            = new Identifier(Loopypowers.MOD_ID, "slam");
    public static final Identifier THUNDERCLAP2_ID    = new Identifier(Loopypowers.MOD_ID, "thunderclap2");
    public static final Identifier THUNDERCLAP3_ID    = new Identifier(Loopypowers.MOD_ID, "thunderclap3");
    public static final Identifier WALLSLAM_ID        = new Identifier(Loopypowers.MOD_ID, "wallslam");
    public static final Identifier POSSESSION_ID     = new Identifier(Loopypowers.MOD_ID, "possession");
    public static final Identifier BULLRUSHSTOP_ID   = new Identifier(Loopypowers.MOD_ID, "bullrushstop");
    public static final Identifier LUNGESTART_ID     = new Identifier(Loopypowers.MOD_ID, "lungestart");
    public static final Identifier SHATTER_ID        = new Identifier(Loopypowers.MOD_ID, "shatter");
    public static final Identifier SHOCK_ID          = new Identifier(Loopypowers.MOD_ID, "shock");

    // SoundEvents
    public static final SoundEvent THUNDERCLAP     = SoundEvent.of(THUNDERCLAP_ID);
    public static final SoundEvent TELEPORTCLAP    = SoundEvent.of(TELEPORTCLAP_ID);
    public static final SoundEvent TELEPORTSNAP    = SoundEvent.of(TELEPORTSNAP_ID);
    public static final SoundEvent DASH           = SoundEvent.of(DASH_ID);
    public static final SoundEvent RUSHSTART      = SoundEvent.of(RUSHSTART_ID);
    public static final SoundEvent RUSHLOOP       = SoundEvent.of(RUSHLOOP_ID);
    public static final SoundEvent OVERDRIVESTART = SoundEvent.of(OVERDRIVESTART_ID);
    public static final SoundEvent OVERDRIVELOOP  = SoundEvent.of(OVERDRIVELOOP_ID);
    public static final SoundEvent ELECTRICITY    = SoundEvent.of(ELECTRICITY_ID);
    public static final SoundEvent RAGE = SoundEvent.of(RAGE_ID);
    public static final SoundEvent BASSDROP        = SoundEvent.of(BASSDROP_ID);
    public static final SoundEvent BASSSINGLE      = SoundEvent.of(BASSSINGLE_ID);
    public static final SoundEvent BULLRUSH        = SoundEvent.of(BULLRUSH_ID);
    public static final SoundEvent EARRING         = SoundEvent.of(EARRING_ID);
    public static final SoundEvent ENTITYSLAM      = SoundEvent.of(ENTITYSLAM_ID);
    public static final SoundEvent RAILGUN         = SoundEvent.of(RAILGUN_ID);
    public static final SoundEvent SLAM            = SoundEvent.of(SLAM_ID);
    public static final SoundEvent WALLSLAM        = SoundEvent.of(WALLSLAM_ID);
    public static final SoundEvent POSSESSION     = SoundEvent.of(POSSESSION_ID);
    public static final SoundEvent BULLRUSHSTOP   = SoundEvent.of(BULLRUSHSTOP_ID);
    public static final SoundEvent LUNGESTART     = SoundEvent.of(LUNGESTART_ID);
    public static final SoundEvent SHATTER        = SoundEvent.of(SHATTER_ID);
    public static final SoundEvent SHOCK          = SoundEvent.of(SHOCK_ID);

    private ModSounds() {}

    public static void register() {
        Registry.register(Registries.SOUND_EVENT, THUNDERCLAP_ID, THUNDERCLAP);
        Registry.register(Registries.SOUND_EVENT, TELEPORTCLAP_ID, TELEPORTCLAP);
        Registry.register(Registries.SOUND_EVENT, TELEPORTSNAP_ID, TELEPORTSNAP);
        Registry.register(Registries.SOUND_EVENT, DASH_ID, DASH);
        Registry.register(Registries.SOUND_EVENT, RUSHSTART_ID, RUSHSTART);
        Registry.register(Registries.SOUND_EVENT, RUSHLOOP_ID, RUSHLOOP);
        Registry.register(Registries.SOUND_EVENT, OVERDRIVESTART_ID, OVERDRIVESTART);
        Registry.register(Registries.SOUND_EVENT, OVERDRIVELOOP_ID, OVERDRIVELOOP);
        Registry.register(Registries.SOUND_EVENT, ELECTRICITY_ID, ELECTRICITY);
        Registry.register(Registries.SOUND_EVENT, RAGE_ID, RAGE);
        Registry.register(Registries.SOUND_EVENT, BASSDROP_ID, BASSDROP);
        Registry.register(Registries.SOUND_EVENT, BASSSINGLE_ID, BASSSINGLE);
        Registry.register(Registries.SOUND_EVENT, BULLRUSH_ID, BULLRUSH);
        Registry.register(Registries.SOUND_EVENT, EARRING_ID, EARRING);
        Registry.register(Registries.SOUND_EVENT, ENTITYSLAM_ID, ENTITYSLAM);
        Registry.register(Registries.SOUND_EVENT, RAILGUN_ID, RAILGUN);
        Registry.register(Registries.SOUND_EVENT, SLAM_ID, SLAM);
        Registry.register(Registries.SOUND_EVENT, WALLSLAM_ID, WALLSLAM);
        Registry.register(Registries.SOUND_EVENT, POSSESSION_ID, POSSESSION);
        Registry.register(Registries.SOUND_EVENT, BULLRUSHSTOP_ID, BULLRUSHSTOP);
        Registry.register(Registries.SOUND_EVENT, LUNGESTART_ID, LUNGESTART);
        Registry.register(Registries.SOUND_EVENT, SHATTER_ID, SHATTER);
        Registry.register(Registries.SOUND_EVENT, SHOCK_ID, SHOCK);
    }
} // doing this sucks
