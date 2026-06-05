package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.network.AbilityPackets;
import com.yourname.loopypowers.power.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting; // Added for translations

import java.util.*;

/**
 * Assigns powers to players and manages abilities.
 * Cooldowns are managed by CooldownUI; persistence is handled by PlayerDataStore.
 */
public class PowerManager {

    /* ============================================================
       POWER REGISTRY
       ============================================================ */

    private static final Power SPEED           = new SpeedPower();
    private static final Power FIRE            = new FirePower();
    private static final Power TELEPORT        = new TeleportPower();
    private static final Power LIGHTNING       = new LightningPower();
    private static final Power FLIGHT          = new FlightPower();
    private static final Power BLOOD           = new BloodPower();
    private static final Power SOUND           = new SoundPower();
    private static final Power STRENGTH        = new StrengthPower();
    private static final Power EXPLOSION       = new ExplosionPower();
    private static final Power NATURE          = new NaturePower();
    private static final Power ICE             = new IcePower();
    private static final Power FORTUNE         = new FortunePower();
    private static final Power DARKNESS        = new DarknessPower();
    private static final Power HEALING_FACTOR  = new HealingPower();
    private static final Power PSYCHIC         = new PsychicPower();
    private static final Power COSMIC          = new CosmicPower();
    private static final Power TELEKINESIS     = new TelekinesisPower();
    private static final Power INTERDIMENSIONAL = new DimensionalPower();

    private static final List<Power> ALL_POWERS = List.of(
            SPEED, FIRE, TELEPORT, LIGHTNING, FLIGHT, BLOOD, SOUND, STRENGTH,
            EXPLOSION, NATURE, ICE, FORTUNE, DARKNESS, HEALING_FACTOR,
            PSYCHIC, COSMIC, TELEKINESIS, INTERDIMENSIONAL
    );

    /** UUID → assigned power */
    private static final Map<UUID, Power> PLAYER_POWERS = new HashMap<>();

    /** UUID → power level (1–3) */
    private static final Map<UUID, Integer> PLAYER_LEVELS = new HashMap<>();

    /* ============================================================
       POWER ASSIGNMENT
       ============================================================ */

    public static void assignRandomPower(ServerPlayerEntity player) {
        Power power = ALL_POWERS.get(new Random().nextInt(ALL_POWERS.size()));
        setPower(player, power);
    }

    /**
     * Default alias for setting a power. Triggers the chat announcements.
     */
    public static void setPower(ServerPlayerEntity player, Power power) {
        setPower(player, power, false);
    }

    /**
     * Sets a player's power, calling onRemove on the old one and onAssign on
     * the new one, then persisting immediately so the change survives a crash.
     * * @param silent If true, suppresses the "You gained the power" chat messages.
     * Used during logins and respawns to avoid spam.
     */
    public static void setPower(ServerPlayerEntity player, Power power, boolean silent) {
        Power old = getPower(player);
        if (old != null) old.onRemove(player);

        PLAYER_POWERS.put(player.getUuid(), power);
        power.onAssign(player);

        syncClientFlags(player);

        // Persist immediately so admin commands survive crashes
        PlayerDataStore.save(player);

        if (!silent) {
            // TRANSLATED CHAT ANNOUNCEMENTS
            player.sendMessage(Text.translatable("message.loopypowers.power_gained", power.getName()).formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.translatable("message.loopypowers.power_help").formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.translatable("message.loopypowers.power_keybinds").formatted(Formatting.YELLOW), false);
        }
    }

    public static void removePower(ServerPlayerEntity player) {
        Power current = PLAYER_POWERS.remove(player.getUuid());
        if (current != null) current.onRemove(player);

        clearAllCooldowns(player);
        player.clearStatusEffects();

        // Save the now-empty state so the file also has this
        PlayerDataStore.save(player);
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    /** UUID → (abilityKey → epoch-ms when cooldown expires) */
    private static final Map<UUID, Map<String, Long>> COOLDOWN_END_MS = new HashMap<>();

    private static final Map<String, Long> COOLDOWN_OVERRIDE_MS = new HashMap<>();

    private static double  GLOBAL_CD_MULT    = 1.0;
    private static final Map<UUID, Double> PLAYER_CD_MULT = new HashMap<>();
    private static boolean COOLDOWNS_DISABLED = false;

    private static long nowMs() { return System.currentTimeMillis(); }

    private static long getCooldownEndMs(ServerPlayerEntity player, String key) {
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        return (map == null) ? 0L : map.getOrDefault(key, 0L);
    }

    public static long getCooldownRemainingMs(ServerPlayerEntity player, String key) {
        return Math.max(0L, getCooldownEndMs(player, key) - nowMs());
    }

    public static boolean isCooldownReady(ServerPlayerEntity player, String key) {
        if (COOLDOWNS_DISABLED) return true;
        return getCooldownRemainingMs(player, key) <= 0L;
    }

    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs) {
        if (COOLDOWNS_DISABLED) return;
        if (durationMs <= 0L) { clearCooldown(player, key); return; }

        long end = nowMs() + durationMs;
        COOLDOWN_END_MS.computeIfAbsent(player.getUuid(), u -> new HashMap<>()).put(key, end);
        CooldownUI.setCooldownEnd(player, key, end, (Text) null);
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
            if (currentEnd <= now) continue;

            long newEnd = Math.max(now, currentEnd - amountMs);
            entry.setValue(newEnd);
            CooldownUI.setCooldownEnd(player, entry.getKey(), newEnd, (Text) null);
        }
    }

    public static void reduceSingleCooldown(ServerPlayerEntity player, String key, long amountMs) {
        if (amountMs <= 0) return;
        Map<String, Long> map = COOLDOWN_END_MS.get(player.getUuid());
        if (map == null) return;

        Long currentEnd = map.get(key);
        if (currentEnd == null) return;

        long now = nowMs();
        if (currentEnd <= now) return;

        long newEnd = Math.max(now, currentEnd - amountMs);
        map.put(key, newEnd);
        CooldownUI.setCooldownEnd(player, key, newEnd, (Text) null);
    }

    public static void setCooldownOverride(String key, long durationMs) { COOLDOWN_OVERRIDE_MS.put(key, durationMs); }
    public static void clearCooldownOverride(String key)                 { COOLDOWN_OVERRIDE_MS.remove(key); }
    public static void setGlobalCooldownMultiplier(double mult)          { GLOBAL_CD_MULT = Math.max(0.0, mult); }

    public static double getPlayerCooldownMultiplier(ServerPlayerEntity player) {
        return PLAYER_CD_MULT.getOrDefault(player.getUuid(), 1.0);
    }

    public static void setPlayerCooldownMultiplier(ServerPlayerEntity player, double mult) {
        PLAYER_CD_MULT.put(player.getUuid(), Math.max(0.0, mult));
    }

    public static void clearPlayerCooldownMultiplier(ServerPlayerEntity player) {
        PLAYER_CD_MULT.remove(player.getUuid());
    }

    public static void setCooldownsDisabled(boolean disabled) { COOLDOWNS_DISABLED = disabled; }

    public static boolean areCooldownsDisabled() { return COOLDOWNS_DISABLED; }

    public static int getModifiedCooldownTicks(ServerPlayerEntity player, int baseTicks) {
        if (COOLDOWNS_DISABLED) return 0;
        double mult = GLOBAL_CD_MULT * PLAYER_CD_MULT.getOrDefault(player.getUuid(), 1.0);
        return (int) Math.max(0, Math.round(baseTicks * mult));
    }

    /** Applies overrides and multipliers to produce the final cooldown duration. */
    private static long computeFinalCooldownMs(ServerPlayerEntity player, Power power, AbilityTypes type, long baseMs) {
        long ms = modifyCooldown(player, power, type, baseMs);

        Long override = COOLDOWN_OVERRIDE_MS.get(abilityKey(power, type));
        if (override != null) ms = override;

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

        // TRANSLATED: No power check
        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.no_power").formatted(Formatting.RED), 40);
            return;
        }

        // TRANSLATED: Displaced lock EXCEPTIONS using RegistryEntry
        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED)) && !(power instanceof HealingPower)) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.displaced").formatted(Formatting.RED), 20);
            return;
        }

        String key = abilityKey(power, AbilityTypes.PRIMARY);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivatePrimary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.PRIMARY, power.getPrimaryCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    public static void useSecondary(ServerPlayerEntity player) {
        Power power = PLAYER_POWERS.get(player.getUuid());

        // No power check
        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.no_power").formatted(Formatting.RED), 40);
            return;
        }

        // Using RegistryEntry
        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED))) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.displaced").formatted(Formatting.RED), 20);
            return;
        }

        // Level check
        if (getLevel(player) < 2) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.secondary_level_req").formatted(Formatting.RED), 30);
            return;
        }

        String key = abilityKey(power, AbilityTypes.SECONDARY);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivateSecondary(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.SECONDARY, power.getSecondaryCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    public static void useUltimate(ServerPlayerEntity player) {
        Power power = PLAYER_POWERS.get(player.getUuid());

        // No power check
        if (power == null) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.no_power").formatted(Formatting.RED), 40);
            return;
        }

        // Using RegistryEntry
        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED))) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.displaced").formatted(Formatting.RED), 20);
            return;
        }

        // Level check
        if (getLevel(player) < 3) {
            CooldownUI.pushActionbarOverride(player, Text.translatable("message.loopypowers.ultimate_level_req").formatted(Formatting.RED), 30);
            return;
        }

        String key = abilityKey(power, AbilityTypes.ULTIMATE);
        if (!isCooldownReady(player, key)) return;

        if (power.tryActivateUltimate(player)) {
            long cd = computeFinalCooldownMs(player, power, AbilityTypes.ULTIMATE, power.getUltimateCooldownMs());
            startCooldown(player, key, cd);
        }
    }

    private static void syncClientFlags(ServerPlayerEntity player) {
        Power p = PLAYER_POWERS.get(player.getUuid());
        boolean hasStrength = (p instanceof StrengthPower);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                player,
                new AbilityPackets.SyncStrengthPayload(hasStrength)
        );
    }

    private static long modifyCooldown(ServerPlayerEntity player, Power power, AbilityTypes type, long baseMs) {
        if (power instanceof StrengthPower && type != AbilityTypes.ULTIMATE && StrengthPower.isRaging(player)) {
            return Math.max(250L, (long)(baseMs * 0.15));
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

    /* ============================================================
       LEVELS
       ============================================================ */

    public static int getLevel(ServerPlayerEntity player) {
        return PLAYER_LEVELS.getOrDefault(player.getUuid(), 1);
    }

    public static void setLevel(ServerPlayerEntity player, int level) {
        PLAYER_LEVELS.put(player.getUuid(), Math.max(1, Math.min(3, level)));
    }

    public static void levelUp(ServerPlayerEntity player) {
        int current = getLevel(player);
        if (current < 3) {
            setLevel(player, current + 1);
            // TRANSLATED: Level up
            player.sendMessage(Text.translatable("message.loopypowers.level_up", current + 1).formatted(Formatting.AQUA), false);
        }
    }

    /* ============================================================
       PERSISTENCE  (called by PlayerDataStore)
       ============================================================ */

    public static void saveToNbt(ServerPlayerEntity player, net.minecraft.nbt.NbtCompound nbt) {
        Power power = getPower(player);
        if (power != null) nbt.putString("lp_power", power.getName());

        nbt.putInt("lp_level", getLevel(player));
        nbt.putDouble("lp_cd_mult", getPlayerCooldownMultiplier(player));
        nbt.putBoolean("lp_passive", PassiveManager.isEnabled(player));

        Map<String, Long> cds = COOLDOWN_END_MS.get(player.getUuid());
        if (cds != null && !cds.isEmpty()) {
            net.minecraft.nbt.NbtCompound cdTag = new net.minecraft.nbt.NbtCompound();
            long now = nowMs();
            for (Map.Entry<String, Long> entry : cds.entrySet()) {
                if (entry.getValue() > now) cdTag.putLong(entry.getKey(), entry.getValue());
            }
            if (!cdTag.isEmpty()) nbt.put("lp_cooldowns", cdTag);
        }
    }

    public static void loadFromNbt(ServerPlayerEntity player, net.minecraft.nbt.NbtCompound nbt) {
        if (nbt.contains("lp_power")) {
            String name = nbt.getString("lp_power");
            for (Power p : ALL_POWERS) {
                if (p.getName().equals(name)) {
                    PLAYER_POWERS.put(player.getUuid(), p);
                    p.onAssign(player);
                    syncClientFlags(player);
                    break;
                }
            }
        }

        if (nbt.contains("lp_level")) {
            setLevel(player, nbt.getInt("lp_level"));
        }

        if (nbt.contains("lp_cd_mult")) {
            double mult = nbt.getDouble("lp_cd_mult");
            if (mult != 1.0) PLAYER_CD_MULT.put(player.getUuid(), Math.max(0.0, mult));
        }

        boolean passive = !nbt.contains("lp_passive") || nbt.getBoolean("lp_passive");
        PassiveManager.setPassiveState(player, passive);

        if (nbt.contains("lp_cooldowns")) {
            net.minecraft.nbt.NbtCompound cdTag = nbt.getCompound("lp_cooldowns");

            Map<String, Long> map = new HashMap<>();
            long now = nowMs();

            for (String key : cdTag.getKeys()) {
                long endMs = cdTag.getLong(key);
                if (endMs > now) {
                    map.put(key, endMs);
                    CooldownUI.setCooldownEnd(player, key, endMs, (Text) null);
                }
            }
            COOLDOWN_END_MS.put(player.getUuid(), map);
        }
    }

    public static void copyCooldowns(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        Map<String, Long> oldMap = COOLDOWN_END_MS.get(oldPlayer.getUuid());
        if (oldMap == null) return;
        COOLDOWN_END_MS.put(newPlayer.getUuid(), new HashMap<>(oldMap));
    }

    public static void clearPlayerState(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        PLAYER_POWERS.remove(id);
        PLAYER_LEVELS.remove(id);
        COOLDOWN_END_MS.remove(id);
        PLAYER_CD_MULT.remove(id);
        CooldownUI.clearAllCooldowns(player);
        PassiveManager.clearPassiveState(player);
    }
}