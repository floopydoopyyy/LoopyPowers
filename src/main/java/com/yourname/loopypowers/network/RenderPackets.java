package com.yourname.loopypowers.network;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public final class RenderPackets {
    private RenderPackets() {}

    public static void hidePlayerFromOthers(ServerPlayerEntity hidden, int ticks) {
        // Everyone tracking them
        for (ServerPlayerEntity p : PlayerLookup.tracking(hidden)) {
            ServerPlayNetworking.send(p, new AbilityPackets.HidePlayerPayload(hidden.getId(), ticks));
        }

        // And also send to themself (for 3rd person self-hide)
        ServerPlayNetworking.send(hidden, new AbilityPackets.HidePlayerPayload(hidden.getId(), ticks));
    }

    // client only trail
    public static void sendResonanceTrail(ServerPlayerEntity viewer, int targetEntityId, int count, float intensity) {
        ServerPlayNetworking.send(viewer, new AbilityPackets.ResonanceTrailPayload(targetEntityId, count, intensity));
    }

    // small ring at a target. the things I do for a power no one will care about.
    public static void sendResonanceRing(ServerPlayerEntity viewer, int targetEntityId, float intensity) {
        ServerPlayNetworking.send(viewer, new AbilityPackets.ResonanceRingPayload(targetEntityId, intensity));
    }

    // 1s line between viewer and target
    public static void sendResonanceLine(ServerPlayerEntity viewer, int targetEntityId, int ticks, float intensity) {
        ServerPlayNetworking.send(viewer, new AbilityPackets.ResonanceLinePayload(targetEntityId, ticks, intensity));
    }

    // stun fx
    public static void sendStunAudio(ServerPlayerEntity viewer, int ticks) {
        ServerPlayNetworking.send(viewer, new AbilityPackets.StunAudioPayload(ticks));
    }
}