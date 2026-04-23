package com.yourname.loopypowers.ritual;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
        MIND_VESTIGE
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
            default                -> new PowerRitual(player, type);
        };

        ACTIVE.put(player.getUuid(), ritual);
    }

    public static boolean isActive(PlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    public static void tick(ServerWorld world) {
        Iterator<Map.Entry<UUID, Ritual>> it = ACTIVE.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, Ritual> entry = it.next();
            Ritual ritual = entry.getValue();

            boolean done = ritual.tick(world);

            if (done) {
                it.remove();
            }
        }
    }
}