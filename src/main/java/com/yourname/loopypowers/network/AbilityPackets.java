package com.yourname.loopypowers.network;

import com.yourname.loopypowers.Loopypowers;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.payload.*;
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

        // BloodPower fx
        PayloadTypeRegistry.playS2C().register(BloodBleedApplyPayload.ID, BloodBleedApplyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodBleedTickPayload.ID, BloodBleedTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodWhipBeamPayload.ID, BloodWhipBeamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodWhipHitPayload.ID, BloodWhipHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodClotCastPayload.ID, BloodClotCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodPopPayload.ID, BloodPopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodChainPayload.ID, BloodChainPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodBindTickPayload.ID, BloodBindTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BloodBindDamageFxPayload.ID, BloodBindDamageFxPayload.CODEC);

        // CosmicPower fx
        PayloadTypeRegistry.playS2C().register(CosmicFateAuraPayload.ID, CosmicFateAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicDetonateStartPayload.ID, CosmicDetonateStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicDetonateTickPayload.ID, CosmicDetonateTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicFateCapPayload.ID, CosmicFateCapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicRayPayload.ID, CosmicRayPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicRayHitPayload.ID, CosmicRayHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicRayImpactPayload.ID, CosmicRayImpactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicStarTrailPayload.ID, CosmicStarTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CosmicSlamPayload.ID, CosmicSlamPayload.CODEC);

        // DarknessPower fx
        PayloadTypeRegistry.playS2C().register(DarknessBackstabFxPayload.ID, DarknessBackstabFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessExposedFxPayload.ID, DarknessExposedFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessComboFxPayload.ID, DarknessComboFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessMistEnterPayload.ID, DarknessMistEnterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessMistTrailPayload.ID, DarknessMistTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessUltActivatePayload.ID, DarknessUltActivatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessBlackoutFxPayload.ID, DarknessBlackoutFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DarknessBlackoutEndPayload.ID, DarknessBlackoutEndPayload.CODEC);

        // DimensionalPower fx
        PayloadTypeRegistry.playS2C().register(DimensionalFlickerPayload.ID, DimensionalFlickerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalSituationalFxPayload.ID, DimensionalSituationalFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalPhaseEnterPayload.ID, DimensionalPhaseEnterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalPhaseTrailPayload.ID, DimensionalPhaseTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalPhaseExitPayload.ID, DimensionalPhaseExitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalDisplaceFxPayload.ID, DimensionalDisplaceFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalRiftOpenPayload.ID, DimensionalRiftOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalRiftTickPayload.ID, DimensionalRiftTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DimensionalRiftEndPayload.ID, DimensionalRiftEndPayload.CODEC);

        // ExplosionPower fx
        PayloadTypeRegistry.playS2C().register(ExplosionIgniteStartPayload.ID, ExplosionIgniteStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionIgniteTickPayload.ID, ExplosionIgniteTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionBurstPayload.ID, ExplosionBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionUltCancelPayload.ID, ExplosionUltCancelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionDropZonePayload.ID, ExplosionDropZonePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionUltAmbientPayload.ID, ExplosionUltAmbientPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ExplosionFinisherChargePayload.ID, ExplosionFinisherChargePayload.CODEC);

        // FirePower fx
        PayloadTypeRegistry.playS2C().register(FireHoverPayload.ID, FireHoverPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireUltChargePayload.ID, FireUltChargePayload.CODEC);

        // FlightPower fx
        PayloadTypeRegistry.playS2C().register(FlightKnockPayload.ID,       FlightKnockPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightGustPayload.ID,        FlightGustPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightGustTrailPayload.ID,   FlightGustTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightUpdraftPayload.ID,     FlightUpdraftPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightBoomStartPayload.ID,   FlightBoomStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightBoomWindupPayload.ID,  FlightBoomWindupPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightBoomTunnelPayload.ID,  FlightBoomTunnelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightBoomImpactPayload.ID,  FlightBoomImpactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FlightTrailPayload.ID,       FlightTrailPayload.CODEC);

        // LightningPower fx
        PayloadTypeRegistry.playS2C().register(LightningChargeReadyPayload.ID,       LightningChargeReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningHitBasicPayload.ID,           LightningHitBasicPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningHitTierPayload.ID,            LightningHitTierPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningChainTrailPayload.ID,         LightningChainTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningChainHitPayload.ID,           LightningChainHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningClapConePayload.ID,           LightningClapConePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningClapHitPayload.ID,            LightningClapHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningConfettiPayload.ID,           LightningConfettiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningTargetConfettiPayload.ID,     LightningTargetConfettiPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningSuperchargeAuraPayload.ID,    LightningSuperchargeAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningSuperchargeBurstPayload.ID,   LightningSuperchargeBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningMaelstromOpenPayload.ID,      LightningMaelstromOpenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningStormTargetAuraPayload.ID,    LightningStormTargetAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningStormCloudsPayload.ID,        LightningStormCloudsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LightningStormStrikePayload.ID,        LightningStormStrikePayload.CODEC);

        // Ritual fx (shared payload for all rituals)
        PayloadTypeRegistry.playS2C().register(RitualFxPayload.ID, RitualFxPayload.CODEC);

        // TeleportPower fx
        PayloadTypeRegistry.playS2C().register(TeleportDodgePayload.ID,        TeleportDodgePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportBlinkPayload.ID,        TeleportBlinkPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportAimBeamPayload.ID,      TeleportAimBeamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportSwapBurstPayload.ID,    TeleportSwapBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportFrenzyTickPayload.ID,   TeleportFrenzyTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TeleportFrenzyStrikePayload.ID, TeleportFrenzyStrikePayload.CODEC);

        // TelekinesisPower fx
        PayloadTypeRegistry.playS2C().register(TKBeamPayload.ID,              TKBeamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKPassiveHitPayload.ID,        TKPassiveHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKYankTargetPayload.ID,        TKYankTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKGrabTargetPayload.ID,        TKGrabTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKWallImpactPayload.ID,        TKWallImpactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKFloorImpactPayload.ID,       TKFloorImpactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKSuspendAuraPayload.ID,       TKSuspendAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKChokeAuraPayload.ID,         TKChokeAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKThrowPayload.ID,             TKThrowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisActivatePayload.ID,    TKDebrisActivatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisOrbitPayload.ID,       TKDebrisOrbitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisPullPayload.ID,        TKDebrisPullPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisAuraPayload.ID,        TKDebrisAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisThrowPayload.ID,       TKDebrisThrowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisExplosionPayload.ID,   TKDebrisExplosionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TKDebrisSpawnPayload.ID,       TKDebrisSpawnPayload.CODEC);

        // StrengthPower fx
        PayloadTypeRegistry.playS2C().register(StrengthOnePunchPayload.ID,      StrengthOnePunchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRageHitPayload.ID,       StrengthRageHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthSlamPayload.ID,          StrengthSlamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthSlamTargetPayload.ID,    StrengthSlamTargetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRushTrailPayload.ID,     StrengthRushTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRushEntityHitPayload.ID, StrengthRushEntityHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRushCancelPayload.ID,    StrengthRushCancelPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRushCrashPayload.ID,     StrengthRushCrashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRagePulsePayload.ID,     StrengthRagePulsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StrengthRageAuraPayload.ID,      StrengthRageAuraPayload.CODEC);

        // SpeedPower fx
        PayloadTypeRegistry.playS2C().register(SpeedLowHealthBurstPayload.ID,  SpeedLowHealthBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedRushTrailPayload.ID,       SpeedRushTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedDashPayload.ID,            SpeedDashPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedRushCastPayload.ID,        SpeedRushCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedOverdriveCastPayload.ID,   SpeedOverdriveCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedOverdriveTrailPayload.ID,  SpeedOverdriveTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedOverdriveHitPayload.ID,    SpeedOverdriveHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SpeedBlockImpactPayload.ID,     SpeedBlockImpactPayload.CODEC);

        // SoundPower fx
        PayloadTypeRegistry.playS2C().register(SoundResonanceBurstPayload.ID,  SoundResonanceBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBurstHitPayload.ID,        SoundBurstHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBoomCastPayload.ID,        SoundBoomCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassPullPayload.ID,        SoundBassPullPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassPullHitPayload.ID,     SoundBassPullHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassFinalPayload.ID,       SoundBassFinalPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassFinalRemotePayload.ID, SoundBassFinalRemotePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassPullVizPayload.ID,     SoundBassPullVizPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundBassBlastVizPayload.ID,    SoundBassBlastVizPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundUltWindupPayload.ID,       SoundUltWindupPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SoundUltBeamPayload.ID,         SoundUltBeamPayload.CODEC);

        // PsychicPower fx
        PayloadTypeRegistry.playS2C().register(PsychicLeechPayload.ID,        PsychicLeechPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PsychicCompelAuraPayload.ID,   PsychicCompelAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PsychicSpikeBeamPayload.ID,    PsychicSpikeBeamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PsychicSpikeImpactPayload.ID,  PsychicSpikeImpactPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PsychicSpikeAuraPayload.ID,    PsychicSpikeAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PsychicControlAuraPayload.ID,  PsychicControlAuraPayload.CODEC);

        // NaturePower fx
        PayloadTypeRegistry.playS2C().register(NatureGasTickPayload.ID,      NatureGasTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NatureVineCastPayload.ID,     NatureVineCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NatureVineBindPayload.ID,     NatureVineBindPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NatureVineStrikePayload.ID,   NatureVineStrikePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NatureVineTetherPayload.ID,   NatureVineTetherPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NatureBuffRingPayload.ID,     NatureBuffRingPayload.CODEC);

        // IcePower fx
        PayloadTypeRegistry.playS2C().register(IceShatterPayload.ID,          IceShatterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceFreezeStagePayload.ID,       IceFreezeStagePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceShatterReadyPayload.ID,      IceShatterReadyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceSpikeTrailPayload.ID,        IceSpikeTrailPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceWaterFreezePayload.ID,       IceWaterFreezePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceSpikePuffPayload.ID,         IceSpikePuffPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceBeamChargePayload.ID,        IceBeamChargePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceBeamLinePayload.ID,          IceBeamLinePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceSnowPayload.ID,              IceSnowPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceBlizzardPayload.ID,          IceBlizzardPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceWaveRingPayload.ID,          IceWaveRingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(IceSnowAroundEntityPayload.ID,  IceSnowAroundEntityPayload.CODEC);

        // HealingPower fx
        PayloadTypeRegistry.playS2C().register(HealingPassivePayload.ID,     HealingPassivePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingAbsorbHitPayload.ID,   HealingAbsorbHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingAbsorbTickPayload.ID,  HealingAbsorbTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingCleansePayload.ID,     HealingCleansePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingBurstPayload.ID,       HealingBurstPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingExpelledPayload.ID,    HealingExpelledPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingUltTickPayload.ID,     HealingUltTickPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HealingUltPhasePayload.ID,    HealingUltPhasePayload.CODEC);

        // FortunePower fx
        PayloadTypeRegistry.playS2C().register(FortuneProcEnemyPayload.ID,    FortuneProcEnemyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneProcSelfPayload.ID,     FortuneProcSelfPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneAllInPayload.ID,        FortuneAllInPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneJackpotPayload.ID,      FortuneJackpotPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneDuelBeamPayload.ID,     FortuneDuelBeamPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneDuelTetherPayload.ID,   FortuneDuelTetherPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneDuelAuraPayload.ID,     FortuneDuelAuraPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneUltCastPayload.ID,      FortuneUltCastPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneHouseBuiltPayload.ID,   FortuneHouseBuiltPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneHouseRoofPayload.ID,    FortuneHouseRoofPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneHouseDestroyPayload.ID, FortuneHouseDestroyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneCenterFxPayload.ID,     FortuneCenterFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneEntityFxPayload.ID,     FortuneEntityFxPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneHotSeatPassPayload.ID,  FortuneHotSeatPassPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortunePoofAtPayload.ID,       FortunePoofAtPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneJackpotHitPayload.ID,   FortuneJackpotHitPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FortuneCeilingBreakPayload.ID, FortuneCeilingBreakPayload.CODEC);
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