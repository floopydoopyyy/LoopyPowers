package com.yourname.loopypowers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.*;
import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.Power;
import com.yourname.loopypowers.manager.PassiveManager;

// now just displays cooldowns to the user. actual cooldowns handled by power manager.
public final class CooldownUI {

    private static final class Entry {
        long endMs;
        Text suffix; // CHANGED to Text

        Entry(long endMs, Text suffix) {
            this.endMs = endMs;
            this.suffix = suffix;
        }
    }

    // temporary surpress actionbar
    private static final class OverrideEntry {
        long endMs;
        Text message;

        OverrideEntry(long endMs, Text message) {
            this.endMs = endMs;
            this.message = message;
        }
    }

    private static final Map<UUID, OverrideEntry> OVERRIDES = new HashMap<>();

    // now should store charges
    private static final Map<UUID, Map<String, Entry>> COOLDOWNS = new HashMap<>();

    private CooldownUI() {
    } // no instances

    /* ============================================================
       PUBLIC API (used by ALL powers)
       ============================================================ */

    // actionbar surpresion
    public static void pushActionbarOverride(ServerPlayerEntity player, Text message, int ticks) {
        if (ticks <= 0) return;
        long end = System.currentTimeMillis() + (ticks * 50L);
        OVERRIDES.put(player.getUuid(), new OverrideEntry(end, message));
    }

    // read-only snapshot
    public static final class CooldownInfo {
        public final long remainingMs;
        public final String suffix;

        public CooldownInfo(long remainingMs, String suffix) {
            this.remainingMs = remainingMs;
            this.suffix = suffix;
        }
    }

    // query
    public static Map<String, CooldownInfo> getCooldownSnapshot(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        Map<String, Entry> map = COOLDOWNS.get(id);
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }

        long now = System.currentTimeMillis();
        Map<String, CooldownInfo> out = new LinkedHashMap<>();

        for (var e : map.entrySet()) {
            long remaining = e.getValue().endMs - now;
            if (remaining > 0) {
                // Fallback for snapshot using string
                String strSuffix = e.getValue().suffix == null ? null : e.getValue().suffix.getString();
                out.put(e.getKey(), new CooldownInfo(remaining, strSuffix));
            }
        }

        return out;
    }

    // same signature
    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs) {
        COOLDOWNS
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(key, new Entry(System.currentTimeMillis() + durationMs, null));
    }

    // suffix for charges (Text version)
    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs, Text suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(key, new Entry(System.currentTimeMillis() + durationMs, suffix));
    }

    // Legacy String fallback for older power classes
    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs, String suffix) {
        startCooldown(player, key, durationMs, suffix == null ? null : Text.literal(suffix));
    }

    // absolute end for cooldowns (Text version)
    public static void setCooldownEnd(ServerPlayerEntity player, String key, long endMs, Text suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(key, new Entry(endMs, suffix));
    }

    // Legacy String fallback
    public static void setCooldownEnd(ServerPlayerEntity player, String key, long endMs, String suffix) {
        setCooldownEnd(player, key, endMs, suffix == null ? null : Text.literal(suffix));
    }

    // clear specific entries when full.
    public static void clearCooldown(ServerPlayerEntity player, String key) {
        Map<String, Entry> map = COOLDOWNS.get(player.getUuid());
        if (map == null) return;
        map.remove(key);
    }

    public static void clearAllCooldowns(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        COOLDOWNS.remove(id);
        OVERRIDES.remove(id);
    }

    // CHANGED: Now returns a translatable Text object instead of a String
    public static Text makeChargeSuffix(int currentCharges, int maxCharges, int nextChargeTicks, int rechargeTicksPerCharge) {
        if (maxCharges <= 0) return Text.empty();
        currentCharges = Math.max(0, Math.min(currentCharges, maxCharges));

        int missing = maxCharges - currentCharges;
        if (missing <= 0) {
            return Text.translatable("hud.loopypowers.charges_simple", currentCharges, maxCharges);
        }

        nextChargeTicks = Math.max(0, nextChargeTicks);
        rechargeTicksPerCharge = Math.max(1, rechargeTicksPerCharge);

        // how long until fully recharged
        int fullTicks = nextChargeTicks + Math.max(0, missing - 1) * rechargeTicksPerCharge;

        long nextSec = (long) Math.ceil(nextChargeTicks / 20.0);
        long fullSec = (long) Math.ceil(fullTicks / 20.0);

        if (missing == 1) {
            return Text.translatable("hud.loopypowers.charges_next", currentCharges, maxCharges, nextSec);
        }
        return Text.translatable("hud.loopypowers.charges_full", currentCharges, maxCharges, nextSec, fullSec);
    }

    public static boolean isReady(ServerPlayerEntity player, String key) {
        Map<String, Entry> map = COOLDOWNS.get(player.getUuid());
        if (map == null) return true;

        Entry e = map.get(key);
        return e == null || e.endMs <= System.currentTimeMillis();
    }

    private static String formatAbility(ServerPlayerEntity player, String key) {
        String[] parts = key.split(":");
        if (parts.length != 2) return key;

        String powerName = parts[0];
        AbilityTypes type = AbilityTypes.valueOf(parts[1]);

        Power power = PowerManager.getPower(player);
        if (power == null) return key;

        String abilityName = switch (type) {
            case PRIMARY -> power.getPrimaryName();
            case SECONDARY -> power.getSecondaryName();
            case ULTIMATE -> power.getUltimateName();
        };

        return type.color + abilityName;
    }

    /* ============================================================
       TICK HANDLER (called once per server tick per player)
       ============================================================ */

    public static void tick(ServerPlayerEntity player) {
        if (player.getWorld().getTime() % 10 != 0) return;
        cleanup(player);
        renderActionBar(player);
    }

    /* ============================================================
       INTERNALS
       ============================================================ */

    private static void cleanup(ServerPlayerEntity player) {
        UUID id = player.getUuid();

        // cleanup cooldowns
        Map<String, Entry> map = COOLDOWNS.get(id);
        if (map != null) {
            Iterator<Map.Entry<String, Entry>> it = map.entrySet().iterator();
            long now = System.currentTimeMillis();
            while (it.hasNext()) {
                if (it.next().getValue().endMs <= now) {
                    it.remove();
                }
            }
            if (map.isEmpty()) COOLDOWNS.remove(id);
        }

        // cleanup override
        OverrideEntry ov = OVERRIDES.get(id);
        if (ov != null && ov.endMs <= System.currentTimeMillis()) {
            OVERRIDES.remove(id);
        }
    }

    private static void renderActionBar(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        long now = System.currentTimeMillis();

        // 1) OVERRIDE wins
        OverrideEntry ov = OVERRIDES.get(id);
        if (ov != null && ov.endMs > now && ov.message != null) {
            player.sendMessage(ov.message, true);
            return;
        }

        // 2) suppression
        if (player.getCommandTags().contains("suppress_actionbar")) return;

        Map<String, Entry> map = COOLDOWNS.get(id);
        boolean hasCooldowns = map != null && !map.isEmpty();

        // passive state
        boolean passiveOff = !PassiveManager.isEnabled(player);

        // only show if cooldowns exist
        if (!hasCooldowns) return;

        // CHANGED: We now stitch Text objects together instead of a StringBuilder
        MutableText bar = Text.empty();

        // passive indicator when off
        if (passiveOff) {
            bar.append(Text.translatable("hud.loopypowers.passive_off").formatted(Formatting.DARK_GRAY));
            bar.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
        }

        for (var entry : map.entrySet()) {
            Entry e = entry.getValue();
            long remaining = e.endMs - now;

            bar.append(Text.literal(formatAbility(player, entry.getKey())))
                    .append(Text.literal(" → ").formatted(Formatting.GRAY));

            if (remaining <= 0) {
                bar.append(Text.translatable("hud.loopypowers.ready").formatted(Formatting.GREEN));
            } else {
                bar.append(Text.translatable("hud.loopypowers.cooldown_seconds", remaining / 1000).formatted(Formatting.RED));
            }

            if (e.suffix != null) {
                bar.append(Text.literal(" "))
                        .append(e.suffix.copy().formatted(Formatting.DARK_GRAY));
            }

            bar.append(Text.literal(" | ").formatted(Formatting.DARK_GRAY));
        }

        player.sendMessage(bar, true);
    }
}