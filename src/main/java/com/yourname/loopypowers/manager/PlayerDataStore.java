package com.yourname.loopypowers.manager;
import com.yourname.loopypowers.Loopypowers;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Handles reading and writing per-player power data to disk.
 *
 * Files are stored at:
 * <run_directory>/loopypowers/playerdata/<uuid>.dat
 *
 * The actual data layout is owned by PowerManager.saveToNbt / loadFromNbt.
 * This class only knows about the file system side.
 */
public class PlayerDataStore {

    // ── Path helpers ──────────────────────────────────────────────────────────

    private static Path getPlayerDir(MinecraftServer server) {
        // getRunDirectory() returns the server/game root as a File — stable across
        // Storing outside the world folder means data survives world resets.
        return server.getRunDirectory().toPath()
                .resolve("loopypowers")
                .resolve("playerdata");
    }

    private static Path getPlayerFile(MinecraftServer server, UUID uuid) {
        return getPlayerDir(server).resolve(uuid + ".dat");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Serialises the player's power, level, and cooldowns to disc.
     * Can be called if said player is on the server ONLY.
     */
    public static void save(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return; // shouldn't really happen server-side but is guarded

        NbtCompound nbt = new NbtCompound();
        PowerManager.saveToNbt(player, nbt);

        Path file = getPlayerFile(server, player.getUuid());

        try {
            Files.createDirectories(file.getParent());
            // accept path directly.
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
     * Does nothing and logs nothing if no save file exists yet.
     */
    public static void load(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        Path file = getPlayerFile(server, player.getUuid());
        if (!Files.exists(file)) return; // should be first time player — nothing to load

        try {
            // 1.20.4 FIX: Pass the Path directly, and provide an NbtSizeTracker
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
     * essentially just fully wipes a player
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