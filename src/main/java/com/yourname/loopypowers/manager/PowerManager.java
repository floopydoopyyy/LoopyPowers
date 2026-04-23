package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.network.AbilityPackets;
import com.yourname.loopypowers.power.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Assigns powers to players and manages abilities
 * cooldowns managed by cooldownui.
 */
public class PowerManager {

    /* ============================================================
       POWER REGISTRY
       ============================================================ */

    private static final Power SPEED = new SpeedPower();
    private static final Power FIRE = new FirePower();
    private static final Power TELEPORT = new TeleportPower();
    private static final Power LIGHTNING = new LightningPower();
    private static final Power FLIGHT = new FlightPower();
    private static final Power BLOOD = new BloodPower();
    private static final Power SOUND = new SoundPower();
    private static final Power STRENGTH = new StrengthPower();
    private static final Power EXPLOSION = new ExplosionPower();
    private static final Power NATURE = new NaturePower();
    private static final Power ICE = new IcePower();
    private static final Power FORTUNE = new FortunePower();
    private static final Power DARKNESS = new DarknessPower();
    private static final Power HEALING_FACTOR = new HealingPower();
    private static final Power PSYCHIC = new PsychicPower();
    private static final Power COSMIC = new CosmicPower();
    private static final Power TELEKINESIS = new TelekinesisPower();
    private static final Power INTERDIMENSIONAL = new DimensionalPower();

    private static final List<Power> ALL_POWERS = List.of(
            SPEED, FIRE, TELEPORT, LIGHTNING, FLIGHT, BLOOD, SOUND, STRENGTH, EXPLOSION, NATURE, ICE, FORTUNE, DARKNESS, HEALING_FACTOR, PSYCHIC, COSMIC, TELEKINESIS, INTERDIMENSIONAL
    );

    private static final Map<UUID, Power> PLAYER_POWERS = new HashMap<>();

    /* ============================================================
       POWER ASSIGNMENT
       ============================================================ */

    public static void assignRandomPower(ServerPlayerEntity player) { // done on join
        Power power = ALL_POWERS.get(
                new Random().nextInt(ALL_POWERS.size())
        );

        PLAYER_POWERS.put(player.getUuid(), power);
        power.onAssign(player);

        syncClientFlags(player);

        player.sendMessage(
                Text.literal("§aYou gained: §e" + power.getName()),
                false
        );
    }

    public static void setPower(ServerPlayerEntity player, Power power) { // done with commands
        Power old = getPower(player); // gets power before swap
        if (old != null) old.onRemove(player); // any special cases should be removed by calling this.

        PLAYER_POWERS.put(player.getUuid(), power);
        power.onAssign(player);

        syncClientFlags(player);

        player.sendMessage(
                Text.literal("§aYou gained: §e" + power.getName()),
                false
        );
    }

    public static void removePower(ServerPlayerEntity player) {
        Power current = PLAYER_POWERS.remove(player.getUuid()); // gets power from map
        if (current != null) {
            current.onRemove(player); // call onremove for the corresponding power - should handle command tags
        }
        PowerManager.clearAllCooldowns(player);
        player.clearStatusEffects();
    }

    /* ============================================================
   COOLDOWNS
   ============================================================ */

    // abilityKey -> time (ms since epoch) when this cooldown ends
    private static final Map<UUID, Map<String, Long>> COOLDOWN_END_MS = new HashMap<>();

    // Key format: same as abilityKey(power, type) => "Ice:PRIMARY" etc.
    private static final Map<String, Long> COOLDOWN_OVERRIDE_MS = new HashMap<>();

    // multiplier for fun
    private static double GLOBAL_CD_MULT = 1.0;

    // player specific multiplier
    private static final Map<UUID, Double> PLAYER_CD_MULT = new HashMap<>();

    // debug cooldown disable
    private static boolean COOLDOWNS_DISABLED = false;



    private static long nowMs() {
        return System.currentTimeMillis();
    }

    private static long getCooldownEndMs(ServerPlayerEntity player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        if (map == null) return 0L;
        return map.getOrDefault(key, 0L);
    }

    public static long getCooldownRemainingMs(ServerPlayerEntity player, String key) {
        long end = getCooldownEndMs(player, key);
        long rem = end - nowMs();
        return Math.max(0L, rem);
    }

    public static boolean isCooldownReady(ServerPlayerEntity player, String key) {
        if (COOLDOWNS_DISABLED) return true;
        return getCooldownRemainingMs(player, key) <= 0L;
    }

    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs) {
        if (COOLDOWNS_DISABLED) return;

        // Treat 0 or negative as “no cooldown” - set to this to remove a cooldown
        if (durationMs <= 0L) {
            clearCooldown(player, key);
            return;
        }

        long end = nowMs() + durationMs;
        COOLDOWN_END_MS.computeIfAbsent(player.getUuid(), u -> new HashMap<>()).put(key, end);

        // keep ui in sync
        CooldownUI.setCooldownEnd(player, key, end, null);
    }

    public static void clearCooldown(ServerPlayerEntity player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        if (map != null) map.remove(key);

        CooldownUI.clearCooldown(player, key);
    }

    public static void clearAllCooldowns(ServerPlayerEntity player) {
        COOLDOWN_END_MS.remove(player.getUuid());
        CooldownUI.clearAllCooldowns(player);
    }

    public static void clearAbilityCooldown(ServerPlayerEntity player, AbilityTypes type) {
        Power power = getPower(player);
        if (power == null) return;
        clearCooldown(player, abilityKey(power, type));
    }

    public static void clearAllAbilityCooldowns(ServerPlayerEntity player) {
        Power power = getPower(player);
        if (power == null) return;
        clearCooldown(player, abilityKey(power, AbilityTypes.PRIMARY));
        clearCooldown(player, abilityKey(power, AbilityTypes.SECONDARY));
        clearCooldown(player, abilityKey(power, AbilityTypes.ULTIMATE));
    }

    public static void reduceAllCooldowns(ServerPlayerEntity player, long amountMs) {
        if (amountMs <= 0) return;

        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        if (map == null || map.isEmpty()) return;

        long now = nowMs();

        for (Map.Entry<String, Long> entry : map.entrySet()) {
            long currentEnd = entry.getValue();

            // already ready → skip
            if (currentEnd <= now) continue;

            long newEnd = Math.max(now, currentEnd - amountMs);
            entry.setValue(newEnd);

            // sync UI
            CooldownUI.setCooldownEnd(player, entry.getKey(), newEnd, null);
        }
    }

    public static void reduceSingleCooldown(ServerPlayerEntity player, String key, long amountMs) {
        if (amountMs <= 0) return;

        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        if (map == null) return;

        Long currentEnd = map.get(key);
        if (currentEnd == null) return;

        long now = nowMs();

        // already ready → skip
        if (currentEnd <= now) return;

        long newEnd = Math.max(now, currentEnd - amountMs);
        map.put(key, newEnd);

        // sync UI
        CooldownUI.setCooldownEnd(player, key, newEnd, null);
    }

    // tuning for editing
    public static void setCooldownOverride(String key, long durationMs) {
        COOLDOWN_OVERRIDE_MS.put(key, durationMs);
    }

    public static void clearCooldownOverride(String key) {
        COOLDOWN_OVERRIDE_MS.remove(key);
    }

    public static void setGlobalCooldownMultiplier(double mult) {
        GLOBAL_CD_MULT = Math.max(0.0, mult);
    }

    public static void setPlayerCooldownMultiplier(ServerPlayerEntity player, double mult) {
        PLAYER_CD_MULT.put(player.getUuid(), Math.max(0.0, mult));
    }

    public static void clearPlayerCooldownMultiplier(ServerPlayerEntity player) {
        PLAYER_CD_MULT.remove(player.getUuid());
    }

    public static void setCooldownsDisabled(boolean disabled) {
        COOLDOWNS_DISABLED = disabled;
    }

    /**
     * Central place where ALL cooldown rules apply.
     */
    private static long computeFinalCooldownMs(ServerPlayerEntity player, Power power, AbilityTypes type, long baseMs) {
        long ms = baseMs;

        // special case for multi-charge abilities
        ms = modifyCooldown(player, power, ms);

        // ability override
        String key = abilityKey(power, type);
        Long override = COOLDOWN_OVERRIDE_MS.get(key);
        if (override != null) ms = override;

        // Multipliers
        ms = (long) Math.max(0L, ms * GLOBAL_CD_MULT);
        double pm = PLAYER_CD_MULT.getOrDefault(player.getUuid(), 1.0);
        ms = (long) Math.max(0L, ms * pm);

        return ms;
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    public static void usePrimary(ServerPlayerEntity player) {
        Power power = PLAYER_POWERS.get(player.getUuid());
        if (power == null) return;

        String key = abilityKey(power, AbilityTypes.PRIMARY);
        if (!isCooldownReady(player, key)) return; // <-- was CooldownUI.isReady

        // special cases
        if (power instanceof TeleportPower tp) {
            boolean finished = tp.activatePrimaryDoubleBlink(player);
            if (finished) {
                long cd = computeFinalCooldownMs(player, power, AbilityTypes.PRIMARY, tp.getPrimaryCooldownMs());
                startCooldown(player, key, cd); // <-- was CooldownUI.startCooldown
            }
            return;
        }

        // default behaviour - if no try exists it should pass
        if (power.tryActivatePrimary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.PRIMARY, power.getPrimaryCooldownMs());
            startCooldown(player, key, cd); // <-- was CooldownUI.startCooldown
        }
    }

    public static void useSecondary(ServerPlayerEntity player) {
        Power power = PLAYER_POWERS.get(player.getUuid());
        if (power == null) return;

        String key = abilityKey(power, AbilityTypes.SECONDARY);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivateSecondary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.SECONDARY, power.getSecondaryCooldownMs());
            startCooldown(player, key, cd); //
        }
    }

    public static void useUltimate(ServerPlayerEntity player) {
        Power power = PLAYER_POWERS.get(player.getUuid());
        if (power == null) return;

        String key = abilityKey(power, AbilityTypes.ULTIMATE);
        if (!isCooldownReady(player, key)) return; // <-- was CooldownUI.isReady

        if (power.tryActivateUltimate(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.ULTIMATE, power.getUltimateCooldownMs());
            startCooldown(player, key, cd); // <-- was CooldownUI.startCooldown
        }
    }

    private static void syncClientFlags(ServerPlayerEntity player) { // for clients to realise stuff exists
        Power p = PLAYER_POWERS.get(player.getUuid());
        boolean hasStrength = (p instanceof StrengthPower);

        var buf = PacketByteBufs.create();
        buf.writeBoolean(hasStrength);

        ServerPlayNetworking.send(player, AbilityPackets.SYNC_STRENGTH_POWER, buf);
    }

    private static long modifyCooldown(ServerPlayerEntity player, Power power, long baseMs) { // for certain powers where cooldowns may need to be halved
        // Strength ultimate halves cooldowns while raging
        if (power instanceof StrengthPower && player.getCommandTags().stream().anyMatch(t -> t.startsWith("st_raging_"))) {
            return Math.max(250L, (long)(baseMs * 0.20)); // baseMs - multiplier for cooldown reduction
        }
        return baseMs;
    }

    /* ============================================================
       QUERY
       ============================================================ */

    public static Power getPower(ServerPlayerEntity player) {
        return PLAYER_POWERS.get(player.getUuid());
    }
    public static String abilityKey(Power power, AbilityTypes type) {
        return power.getName() + ":" + type.name();
    }
}