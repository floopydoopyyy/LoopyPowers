package com.yourname.loopypowers;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.Power;


// now just displays cooldowns to the user. actual cooldowns handled by power manager.
public final class CooldownUI {

    private static final class Entry {
        long endMs;
        String suffix;

        Entry(long endMs, String suffix) {
            this.endMs = endMs;
            this.suffix = suffix;
        }
    }

    // temporary surpress actionbar
    private static final class OverrideEntry {
        long endMs;
        String message;

        OverrideEntry(long endMs, String message) {
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
    public static void pushActionbarOverride(ServerPlayerEntity player, String message, int ticks) {
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
        Map<String, Entry> map = COOLDOWNS.get(player.getUuid());
        if (map == null || map.isEmpty()) return Collections.emptyMap();

        long now = System.currentTimeMillis();
        Map<String, CooldownInfo> out = new LinkedHashMap<>();

        for (var e : map.entrySet()) {
            long remaining = e.getValue().endMs - now;
            if (remaining > 0) {
                out.put(e.getKey(), new CooldownInfo(remaining, e.getValue().suffix));
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

    // suffix for charges
    public static void startCooldown(ServerPlayerEntity player, String key, long durationMs, String suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(key, new Entry(System.currentTimeMillis() + durationMs, suffix));
    }

    // absolute end for cooldowns
    public static void setCooldownEnd(ServerPlayerEntity player, String key, long endMs, String suffix) {
        COOLDOWNS
                .computeIfAbsent(player.getUuid(), u -> new HashMap<>())
                .put(key, new Entry(endMs, suffix));
    }

    // clear specific entries when full.
    public static void clearCooldown(ServerPlayerEntity player, String key) {
        Map<String, Entry> map = COOLDOWNS.get(player.getUuid());
        if (map == null) return;
        map.remove(key);
    }

    public static void clearAllCooldowns(ServerPlayerEntity player) {
        COOLDOWNS.remove(player.getUuid());
    }

    // this uses a suffix for the amount of remaining charges:
    // missingCharges: how many charges are missing (maxCharges - currentCharges)
    // nextChargeTicks: ticks until next charge is restored
    // rechargeTicksPerCharge: ticks per charge restore
    public static String makeChargeSuffix(int currentCharges, int maxCharges, int nextChargeTicks, int rechargeTicksPerCharge) {
        if (maxCharges <= 0) return "";
        currentCharges = Math.max(0, Math.min(currentCharges, maxCharges));

        int missing = maxCharges - currentCharges;
        if (missing <= 0) return "(" + currentCharges + "/" + maxCharges + ")";

        nextChargeTicks = Math.max(0, nextChargeTicks);
        rechargeTicksPerCharge = Math.max(1, rechargeTicksPerCharge);

        // how long until fully recharged
        // (nextChargeTicks) + (missing-1) full recharge windows
        int fullTicks = nextChargeTicks + Math.max(0, missing - 1) * rechargeTicksPerCharge;

        long nextSec = (long) Math.ceil(nextChargeTicks / 20.0);
        long fullSec = (long) Math.ceil(fullTicks / 20.0);

        //
        if (missing == 1) {
            return "(" + currentCharges + "/" + maxCharges + ", " + nextSec + "s)";
        }
        return "(" + currentCharges + "/" + maxCharges + ", " + nextSec + "s, full " + fullSec + "s)";
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

        // 1) OVERRIDE wins, always (even if suppress_actionbar tag is present)
        OverrideEntry ov = OVERRIDES.get(id);
        if (ov != null && ov.endMs > now && ov.message != null && !ov.message.isBlank()) {
            player.sendMessage(Text.literal(ov.message), true);
            return;
        }

        // 2) allow global suppression of normal cooldown bar
        if (player.getCommandTags().contains("suppress_actionbar")) return;

        Map<String, Entry> map = COOLDOWNS.get(id);
        if (map == null || map.isEmpty()) return;

        StringBuilder bar = new StringBuilder();

        for (var entry : map.entrySet()) {
            Entry e = entry.getValue();
            long remaining = e.endMs - now;

            bar.append(formatAbility(player, entry.getKey()))
                    .append(" §7→ ");

            if (remaining <= 0) {
                bar.append("§aREADY");
            } else {
                bar.append("§c").append(remaining / 1000).append("s");
            }

            if (e.suffix != null && !e.suffix.isBlank()) {
                bar.append(" §8").append(e.suffix);
            }

            bar.append(" §8| ");
        }

        player.sendMessage(Text.literal(bar.toString()), true);
    }
}