package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.CooldownUI;
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
        return PASSIVES.getOrDefault(player.getUuid(), true);
    }

    public static void setPassiveState(ServerPlayerEntity player, boolean state) {
        PASSIVES.put(player.getUuid(), state);
    }

    public static void clearPassiveState(ServerPlayerEntity player) {
        PASSIVES.remove(player.getUuid());
    }

    public static void toggle(ServerPlayerEntity player) {
        boolean newState = !isEnabled(player);
        PASSIVES.put(player.getUuid(), newState);

        sendFeedback(player, newState);

        PlayerDataStore.save(player);
    }

    private static void sendFeedback(ServerPlayerEntity player, boolean enabled) {
        Text msg = enabled
                ? Text.translatable("message.loopypowers.passive_enabled").formatted(Formatting.GREEN)
                : Text.translatable("message.loopypowers.passive_disabled").formatted(Formatting.RED);

        CooldownUI.pushActionbarOverride(player, msg, 20);
    }
}