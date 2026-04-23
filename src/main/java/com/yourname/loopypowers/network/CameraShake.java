package com.yourname.loopypowers.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class CameraShake {
    private CameraShake() {}

    /**
     * Shake nearby players' cameras.
     * @param source The player causing the shake (center point)
     * @param radius Blocks radius around source
     * @param ticks Duration in ticks (client-side)
     * @param strength Small number e.g. 0.4f - 1.2f (keep mild)
     */
    public static void shakeNearby(ServerPlayerEntity source, double radius, int ticks, float strength) {
        var world = source.getServerWorld();
        var origin = source.getPos();
        double r2 = radius * radius;

        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(origin) <= r2) {
                var buf = PacketByteBufs.create();
                buf.writeInt(ticks);
                buf.writeFloat(strength);
                ServerPlayNetworking.send(p, AbilityPackets.CAMERA_SHAKE, buf);
            }
        }
    }

    // shake one player
    public static void shake(ServerPlayerEntity target, int ticks, float strength) {
        var buf = PacketByteBufs.create();
        buf.writeInt(ticks);
        buf.writeFloat(strength);
        ServerPlayNetworking.send(target, AbilityPackets.CAMERA_SHAKE, buf);
    }
}
