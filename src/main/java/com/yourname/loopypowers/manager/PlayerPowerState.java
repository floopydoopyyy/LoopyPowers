package com.yourname.loopypowers.manager;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world-save player power data, stored via Minecraft's PersistentStateManager.
 *
 * The manager is attached to the overworld, so the file lives at:
 *   <worldSave>/data/loopypowers_player_data.dat
 *
 * Each world save has its own overworld and its own PersistentStateManager,
 * giving automatic per-world-save isolation that matches NeoForge attachment behaviour.
 */
public class PlayerPowerState extends PersistentState {

    private static final String ID = "loopypowers_player_data";

    private static final Type<PlayerPowerState> TYPE = new Type<>(
            PlayerPowerState::new,
            PlayerPowerState::fromNbt,
            null
    );

    private final Map<UUID, NbtCompound> playerData = new HashMap<>();

    private PlayerPowerState() {}

    // ── NBT ──────────────────────────────────────────────────────────────────

    private static PlayerPowerState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PlayerPowerState state = new PlayerPowerState();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                state.playerData.put(UUID.fromString(key), players.getCompound(key));
            } catch (IllegalArgumentException ignored) {}
        }
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players = new NbtCompound();
        playerData.forEach((uuid, data) -> players.put(uuid.toString(), data.copy()));
        nbt.put("players", players);
        return nbt;
    }

    // ── Per-player accessors ──────────────────────────────────────────────────

    public NbtCompound get(UUID uuid) {
        return playerData.get(uuid);
    }

    public void put(UUID uuid, NbtCompound data) {
        playerData.put(uuid, data);
        markDirty();
    }

    public void remove(UUID uuid) {
        if (playerData.remove(uuid) != null) markDirty();
    }

    // ── Static access ─────────────────────────────────────────────────────────

    public static PlayerPowerState get(MinecraftServer server) {
        PersistentStateManager psm = server.getOverworld().getPersistentStateManager();
        return psm.getOrCreate(TYPE, ID);
    }
}
