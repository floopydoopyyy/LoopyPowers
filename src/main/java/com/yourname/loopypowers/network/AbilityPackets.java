package com.yourname.loopypowers.network;

import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.FlightPower;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class AbilityPackets {

    // ============================================================
    //  DEFINE PAYLOAD RECORDS
    // ============================================================
    // holy shit this suckeddddd

    // --- CLIENT TO SERVER ---
    public record PrimaryAbilityPayload() implements CustomPayload {
        public static final Id<PrimaryAbilityPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "primary_ability"));
        public static final PacketCodec<RegistryByteBuf, PrimaryAbilityPayload> CODEC = PacketCodec.unit(new PrimaryAbilityPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record SecondaryAbilityPayload() implements CustomPayload {
        public static final Id<SecondaryAbilityPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "secondary_ability"));
        public static final PacketCodec<RegistryByteBuf, SecondaryAbilityPayload> CODEC = PacketCodec.unit(new SecondaryAbilityPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record UltimateAbilityPayload() implements CustomPayload {
        public static final Id<UltimateAbilityPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "ultimate_ability"));
        public static final PacketCodec<RegistryByteBuf, UltimateAbilityPayload> CODEC = PacketCodec.unit(new UltimateAbilityPayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record FlightGlidePayload() implements CustomPayload {
        public static final Id<FlightGlidePayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "flight_glide_request"));
        public static final PacketCodec<RegistryByteBuf, FlightGlidePayload> CODEC = PacketCodec.unit(new FlightGlidePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record TogglePassivePayload() implements CustomPayload {
        public static final Id<TogglePassivePayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "toggle_passive"));
        public static final PacketCodec<RegistryByteBuf, TogglePassivePayload> CODEC = PacketCodec.unit(new TogglePassivePayload());
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // --- SERVER TO CLIENT ---
    public record CameraShakePayload(int ticks, float strength) implements CustomPayload {
        public static final Id<CameraShakePayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "camera_shake"));
        public static final PacketCodec<RegistryByteBuf, CameraShakePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, CameraShakePayload::ticks,
                PacketCodecs.FLOAT, CameraShakePayload::strength,
                CameraShakePayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record HidePlayerPayload(int entityId, int ticks) implements CustomPayload {
        public static final Id<HidePlayerPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "hide_player"));
        public static final PacketCodec<RegistryByteBuf, HidePlayerPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, HidePlayerPayload::entityId,
                PacketCodecs.INTEGER, HidePlayerPayload::ticks,
                HidePlayerPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record StunAudioPayload(int ticks) implements CustomPayload {
        public static final Id<StunAudioPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "stun_audio"));
        public static final PacketCodec<RegistryByteBuf, StunAudioPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, StunAudioPayload::ticks,
                StunAudioPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record SyncStrengthPayload(boolean hasStrength) implements CustomPayload {
        public static final Id<SyncStrengthPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "sync_strength_power"));
        public static final PacketCodec<RegistryByteBuf, SyncStrengthPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, SyncStrengthPayload::hasStrength,
                SyncStrengthPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ResonanceTrailPayload(int targetId, int count, float intensity) implements CustomPayload {
        public static final Id<ResonanceTrailPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "resonance_trail"));
        public static final PacketCodec<RegistryByteBuf, ResonanceTrailPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, ResonanceTrailPayload::targetId,
                PacketCodecs.INTEGER, ResonanceTrailPayload::count,
                PacketCodecs.FLOAT, ResonanceTrailPayload::intensity,
                ResonanceTrailPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ResonanceRingPayload(int targetId, float intensity) implements CustomPayload {
        public static final Id<ResonanceRingPayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "resonance_ring"));
        public static final PacketCodec<RegistryByteBuf, ResonanceRingPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, ResonanceRingPayload::targetId,
                PacketCodecs.FLOAT, ResonanceRingPayload::intensity,
                ResonanceRingPayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record ResonanceLinePayload(int targetId, int ticks, float intensity) implements CustomPayload {
        public static final Id<ResonanceLinePayload> ID = new Id<>(Identifier.of(Loopypowers.MOD_ID, "resonance_line"));
        public static final PacketCodec<RegistryByteBuf, ResonanceLinePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.INTEGER, ResonanceLinePayload::targetId,
                PacketCodecs.INTEGER, ResonanceLinePayload::ticks,
                PacketCodecs.FLOAT, ResonanceLinePayload::intensity,
                ResonanceLinePayload::new
        );
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ============================================================
    // 2. REGISTER PAYLOADS
    // ============================================================
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(PrimaryAbilityPayload.ID, PrimaryAbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SecondaryAbilityPayload.ID, SecondaryAbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UltimateAbilityPayload.ID, UltimateAbilityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FlightGlidePayload.ID, FlightGlidePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TogglePassivePayload.ID, TogglePassivePayload.CODEC);

        PayloadTypeRegistry.playS2C().register(CameraShakePayload.ID, CameraShakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HidePlayerPayload.ID, HidePlayerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StunAudioPayload.ID, StunAudioPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncStrengthPayload.ID, SyncStrengthPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResonanceTrailPayload.ID, ResonanceTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResonanceRingPayload.ID, ResonanceRingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResonanceLinePayload.ID, ResonanceLinePayload.CODEC);
    }

    // ============================================================
    // REGISTER SERVER RECEIVERS
    // ============================================================
    public static void registerServer() {
        registerPayloads();

        ServerPlayNetworking.registerGlobalReceiver(PrimaryAbilityPayload.ID, (payload, context) -> {
            context.server().execute(() -> PowerManager.usePrimary(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(SecondaryAbilityPayload.ID, (payload, context) -> {
            context.server().execute(() -> PowerManager.useSecondary(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(UltimateAbilityPayload.ID, (payload, context) -> {
            context.server().execute(() -> PowerManager.useUltimate(context.player()));
        });

        ServerPlayNetworking.registerGlobalReceiver(FlightGlidePayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                var p = PowerManager.getPower(context.player());
                if (!(p instanceof FlightPower)) return;
                context.player().getCommandTags().add("fl_glide_req");
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TogglePassivePayload.ID, (payload, context) -> {
            context.server().execute(() -> PassiveManager.toggle(context.player()));
        });
    }
}