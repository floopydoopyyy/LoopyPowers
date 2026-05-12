package com.yourname.loopypowers.ritual;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class RitualManager {

    public enum RitualType {
        POWER_VESTIGE,
        ELEMENTAL_VESTIGE,
        LIFE_VESTIGE,
        RUIN_VESTIGE,
        MOTION_VESTIGE,
        SPACE_VESTIGE,
        MIND_VESTIGE,
        SEVERANCE_RITUAL,
        REFINED_UPGRADE,
        PERFECTED_UPGRADE
    }

    private static final Map<UUID, Ritual> ACTIVE = new HashMap<>();

    public static void startRitual(PlayerEntity player, RitualType type) {
        ACTIVE.remove(player.getUuid());

        Ritual ritual = switch (type) {
            case ELEMENTAL_VESTIGE -> new ElementalRitual(player, type);
            case LIFE_VESTIGE      -> new LifeRitual(player, type);
            case MIND_VESTIGE      -> new MindRitual(player, type);
            case MOTION_VESTIGE      -> new MotionRitual(player, type);
            case RUIN_VESTIGE      -> new RuinRitual(player, type);
            case SPACE_VESTIGE      -> new SpaceRitual(player, type);
            case SEVERANCE_RITUAL -> new SeveranceRitual(player, type);
            case REFINED_UPGRADE -> new PowerUpgradeRitual(player, type);
            case PERFECTED_UPGRADE    -> new PerfectedUpgradeRitual(player, type);
            default                -> new PowerRitual(player, type);
        };

        ACTIVE.put(player.getUuid(), ritual);
    }

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    // once per server
    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Ritual>> it = ACTIVE.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Ritual> entry = it.next();
            UUID playerId = entry.getKey();
            Ritual ritual = entry.getValue();

            // Find the exact player on the server
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);

            if (player != null) {
                // Tick the ritual exactly once, using the player's current dimension
                boolean done = ritual.tick(player.getWorld());
                if (done) {
                    it.remove();
                }
            } else {
                // player gone, cancel and remove the ritual
                it.remove();
            }
        }
    }
}