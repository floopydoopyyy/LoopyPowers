package com.yourname.loopypowers.manager;

import com.yourname.loopypowers.Loopypowers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker; // Added for 1.21.1
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Handles reading and writing per-player power data to disk.
 */
public class PlayerDataStore {

    // ── Path helpers ──────────────────────────────────────────────────────────

    private static Path getPlayerDir(MinecraftServer server) {
        // 1.21.1 FIXED: getRunDirectory() now returns a Path directly, so .toPath() is removed
        return server.getRunDirectory()
                .resolve("loopypowers")
                .resolve("playerdata");
    }

    private static Path getPlayerFile(MinecraftServer server, UUID uuid) {
        return getPlayerDir(server).resolve(uuid + ".dat");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Serialises the player's power, level, and cooldowns to disc.
     */
    public static void save(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        NbtCompound nbt = new NbtCompound();
        PowerManager.saveToNbt(player, nbt);

        Path file = getPlayerFile(server, player.getUuid());

        try {
            Files.createDirectories(file.getParent());
            // 1.21.1 FIXED: writeCompressed now takes a Path directly instead of a File
            NbtIo.writeCompressed(nbt, file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to save player data for {} ({}): {}",
                    player.getName().getString(), player.getUuid(), e.getMessage()
            );
        }
    }

    /**
     * Deserialises and applies power, level, and cooldown data for a player.
     */
    /**
     * Deserialises and applies power, level, and cooldown data for a player.
     */
    public static void load(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = getPlayerFile(server, player.getUuid());
        if (!Files.exists(file)) return;

        try {
            // new yarn mappings yay
            NbtCompound nbt = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
            if (nbt != null) {
                PowerManager.loadFromNbt(player, nbt);
            }
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to load player data for {} ({}): {}",
                    player.getName().getString(), player.getUuid(), e.getMessage()
            );
        }
    }

    /**
     * Deletes the save file for a player.
     */
    public static void delete(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = getPlayerFile(server, player.getUuid());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Loopypowers.LOGGER.error(
                    "[Loopypowers] Failed to delete player data for {}: {}",
                    player.getUuid(), e.getMessage()
            );
        }
    }
}