package com.yourname.loopypowers.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RenderPackets {
    private RenderPackets() {}

    public static void hidePlayerFromOthers(ServerPlayerEntity hidden, int ticks) {

        // Everyone tracking them
        for (ServerPlayerEntity p : PlayerLookup.tracking(hidden)) {
            var buf = PacketByteBufs.create(); // makes new buf
            buf.writeInt(hidden.getId());
            buf.writeInt(ticks);
            ServerPlayNetworking.send(p, AbilityPackets.HIDE_PLAYER, buf);
        }

        // And also send to themself (for 3rd person self-hide)
        {
            var buf = PacketByteBufs.create(); // makes new buf
            buf.writeInt(hidden.getId());
            buf.writeInt(ticks);
            ServerPlayNetworking.send(hidden, AbilityPackets.HIDE_PLAYER, buf);
        }
    }

    // client only trail
    public static void sendResonanceTrail(ServerPlayerEntity viewer, int targetEntityId, int count, float intensity) {
        var buf = PacketByteBufs.create();
        buf.writeInt(targetEntityId);
        buf.writeInt(count);
        buf.writeFloat(intensity);
        ServerPlayNetworking.send(viewer, AbilityPackets.RESONANCE_TRAIL, buf);
    }
    // small ring at a target. the things I do for a power no one will care about.
    public static void sendResonanceRing(ServerPlayerEntity viewer, int targetEntityId, float intensity) {
        var buf = PacketByteBufs.create();
        buf.writeInt(targetEntityId);
        buf.writeFloat(intensity);
        ServerPlayNetworking.send(viewer, AbilityPackets.RESONANCE_RING, buf);
    }
    // 1s line between viewer and target
    public static void sendResonanceLine(ServerPlayerEntity viewer, int targetEntityId, int ticks, float intensity) {
        var buf = PacketByteBufs.create();
        buf.writeInt(targetEntityId);
        buf.writeInt(ticks);
        buf.writeFloat(intensity);
        ServerPlayNetworking.send(viewer, AbilityPackets.RESONANCE_LINE, buf);
    }
    // stun fx
    public static void sendStunAudio(ServerPlayerEntity viewer, int ticks) {
        var buf = PacketByteBufs.create();
        buf.writeInt(ticks);
        ServerPlayNetworking.send(viewer, AbilityPackets.STUN_AUDIO, buf);
    }

}