package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.power.FlightPower;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
        Text msg = enabled
                ? Text.translatable("message.loopypowers.passive_enabled").formatted(Formatting.GREEN)
                : Text.translatable("message.loopypowers.passive_disabled").formatted(Formatting.RED);

        CooldownUI.pushActionbarOverride(player, msg, 20);
    }

    private static void sendBlockedFeedback(ServerPlayerEntity player) {
        Text msg = Text.translatable("message.loopypowers.passive_blocked").formatted(Formatting.RED);
        CooldownUI.pushActionbarOverride(player, msg, 20);
    }
}