package com.yourname.loopypowers.manager;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Reads and writes per-player power data using the world-scoped PlayerPowerState.
 *
 * Data is stored in <worldSave>/data/loopypowers_player_data.dat via Minecraft's
 * PersistentStateManager, giving automatic per-world-save isolation.
 */
public class PlayerDataStore {

    public static void save(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        NbtCompound nbt = new NbtCompound();
        PowerManager.saveToNbt(player, nbt);
        PlayerPowerState.get(server).put(player.getUuid(), nbt);
    }

    public static void load(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        NbtCompound nbt = PlayerPowerState.get(server).get(player.getUuid());
        if (nbt != null) {
            PowerManager.loadFromNbt(player, nbt);
        }
    }

    public static void delete(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerPowerState.get(server).remove(player.getUuid());
    }
}
