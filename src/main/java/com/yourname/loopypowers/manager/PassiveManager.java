package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.power.FlightPower;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class PassiveManager {

    // stores passive state per player
    private static final Map<UUID, Boolean> PASSIVES = new HashMap<>();

    public static boolean isEnabled(ServerPlayerEntity player) {
        // default = true
        return PASSIVES.getOrDefault(player.getUuid(), true);
    }

    public static void toggle(ServerPlayerEntity player) {
        // block for flight power
        if (PowerManager.getPower(player) instanceof FlightPower) {
            sendBlockedFeedback(player);
            return;
        }

        boolean newState = !isEnabled(player);
        PASSIVES.put(player.getUuid(), newState);

        sendFeedback(player, newState);
    }

    private static void sendFeedback(ServerPlayerEntity player, boolean enabled) {
        String msg = enabled
                ? "§aPassive Enabled"
                : "§cPassive Disabled";

        CooldownUI.pushActionbarOverride(player, msg, 20);
    }

    private static void sendBlockedFeedback(ServerPlayerEntity player) {
        String msg = "§cThis passive cannot be disabled.";
        CooldownUI.pushActionbarOverride(player, msg, 20);
    }
}