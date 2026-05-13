package com.yourname.loopypowers.sound;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = Identifier.of(Loopypowers.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    // Sound Events
    public static final SoundEvent THUNDERCLAP     = registerSoundEvent("thunderclap");
    public static final SoundEvent TELEPORTCLAP    = registerSoundEvent("teleportclap");
    public static final SoundEvent TELEPORTSNAP    = registerSoundEvent("teleportsnap");
    public static final SoundEvent DASH            = registerSoundEvent("dash");
    public static final SoundEvent RUSHSTART       = registerSoundEvent("rushstart");
    public static final SoundEvent RUSHLOOP        = registerSoundEvent("rushloop");
    public static final SoundEvent OVERDRIVESTART  = registerSoundEvent("overdrivestart");
    public static final SoundEvent OVERDRIVELOOP   = registerSoundEvent("overdriveloop");
    public static final SoundEvent ELECTRICITY     = registerSoundEvent("electricity");
    public static final SoundEvent RAGE            = registerSoundEvent("rage");
    public static final SoundEvent BASSDROP        = registerSoundEvent("bassdrop");
    public static final SoundEvent BASSSINGLE      = registerSoundEvent("basssingle");
    public static final SoundEvent BULLRUSH        = registerSoundEvent("bullrush");
    public static final SoundEvent EARRING         = registerSoundEvent("earring");
    public static final SoundEvent ENTITYSLAM      = registerSoundEvent("entityslam");
    public static final SoundEvent RAILGUN         = registerSoundEvent("railgun");
    public static final SoundEvent SLAM            = registerSoundEvent("slam");
    public static final SoundEvent WALLSLAM        = registerSoundEvent("wallslam");
    public static final SoundEvent POSSESSION      = registerSoundEvent("possession");
    public static final SoundEvent BULLRUSHSTOP    = registerSoundEvent("bullrushstop");
    public static final SoundEvent LUNGESTART      = registerSoundEvent("lungestart");
    public static final SoundEvent SHATTER         = registerSoundEvent("shatter");
    public static final SoundEvent SHOCK           = registerSoundEvent("shock");
    public static final SoundEvent BACKSTAB        = registerSoundEvent("backstab");
    public static final SoundEvent BIGSTAB         = registerSoundEvent("bigstab");
    public static final SoundEvent BASSBURST       = registerSoundEvent("bassburst");
    public static final SoundEvent ARENABUILD      = registerSoundEvent("arenabuild");
    public static final SoundEvent BOLT            = registerSoundEvent("bolt");
    public static final SoundEvent EXPLODEBIG      = registerSoundEvent("explodebig");
    public static final SoundEvent SPRAY           = registerSoundEvent("spray");
    public static final SoundEvent VINELASH        = registerSoundEvent("vinelash");
    public static final SoundEvent BLIZZARDLOOP    = registerSoundEvent("blizzardloop");
    public static final SoundEvent SPIKECAST       = registerSoundEvent("spikecast");
    public static final SoundEvent DARKNESSLOOP    = registerSoundEvent("darknessloop");
    public static final SoundEvent ICEBEAMLOOP     = registerSoundEvent("icebeamloop");
    public static final SoundEvent ICEBEAMCHARGE   = registerSoundEvent("icebeamcharge");
    public static final SoundEvent RAISESTAKES     = registerSoundEvent("raisestakes");
    public static final SoundEvent ALLIN           = registerSoundEvent("allin");
    public static final SoundEvent JACKPOT         = registerSoundEvent("jackpot");
    public static final SoundEvent JACKPOT2        = registerSoundEvent("jackpot2");
    public static final SoundEvent JACKPOT3        = registerSoundEvent("jackpot3");
    public static final SoundEvent NEWRULE         = registerSoundEvent("newrule");
    public static final SoundEvent FLICKER         = registerSoundEvent("flicker");
    public static final SoundEvent FLICKER2        = registerSoundEvent("flicker2");
    public static final SoundEvent FLICKER3        = registerSoundEvent("flicker3");
    public static final SoundEvent DARKNESSTELEPORT= registerSoundEvent("darknessteleport");
    public static final SoundEvent MISTENTER       = registerSoundEvent("mistenter");
    public static final SoundEvent MISTLOOP        = registerSoundEvent("mistloop");
    public static final SoundEvent DARKNESSTELEPORT2= registerSoundEvent("darknessteleport2");
    public static final SoundEvent SUSPEND         = registerSoundEvent("suspend");
    public static final SoundEvent BLACKHOLELOOP   = registerSoundEvent("blackholeloop");
    public static final SoundEvent YANK            = registerSoundEvent("yank");
    public static final SoundEvent GUST            = registerSoundEvent("gust");
    public static final SoundEvent UPDRAFT         = registerSoundEvent("updraft");
    public static final SoundEvent RITUALLOOP      = registerSoundEvent("ritualloop");
    public static final SoundEvent RITUALSTART     = registerSoundEvent("ritualstart");
    public static final SoundEvent COSMICRAY       = registerSoundEvent("cosmicray");
    public static final SoundEvent JACKPOTFUNNY    = registerSoundEvent("jackpotfunny");
    public static final SoundEvent PARTYPOPPER     = registerSoundEvent("partypopper");
    public static final SoundEvent FUNNYFNAF       = registerSoundEvent("funnyfnaf");
    public static final SoundEvent ONEPUNCH        = registerSoundEvent("onepunch");
    public static final SoundEvent HECANFLY        = registerSoundEvent("hecanfly");
    public static final SoundEvent PVZPOP          = registerSoundEvent("pvzpop");
    public static final SoundEvent MEDIC           = registerSoundEvent("medic");
    public static final SoundEvent STINK           = registerSoundEvent("stink");
    public static final SoundEvent WHY             = registerSoundEvent("why");

    private ModSounds() {}

    public static void register() {
    }
}