package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.client.HiddenPlayersClient;
import com.yourname.loopypowers.client.fx.BloodFxClient;
import com.yourname.loopypowers.client.fx.CosmicFxClient;
import com.yourname.loopypowers.client.fx.DarknessFxClient;
import com.yourname.loopypowers.client.fx.DimensionalFxClient;
import com.yourname.loopypowers.client.fx.ExplosionFxClient;
import com.yourname.loopypowers.client.fx.FireFxClient;
import com.yourname.loopypowers.client.fx.FlightFxClient;
import com.yourname.loopypowers.client.fx.NatureFxClient;
import com.yourname.loopypowers.client.fx.PsychicFxClient;
import com.yourname.loopypowers.client.fx.SoundFxClient;
import com.yourname.loopypowers.client.fx.SpeedFxClient;
import com.yourname.loopypowers.client.fx.StrengthFxClient;
import com.yourname.loopypowers.client.fx.TelekinesisFxClient;
import com.yourname.loopypowers.client.fx.TeleportFxClient;
import com.yourname.loopypowers.client.fx.FortuneFxClient;
import com.yourname.loopypowers.client.fx.HealingFxClient;
import com.yourname.loopypowers.client.fx.IceFxClient;
import com.yourname.loopypowers.client.fx.LightningFxClient;
import com.yourname.loopypowers.client.fx.ElementalRitualFxClient;
import com.yourname.loopypowers.client.fx.LifeRitualFxClient;
import com.yourname.loopypowers.client.fx.MindRitualFxClient;
import com.yourname.loopypowers.client.fx.MotionRitualFxClient;
import com.yourname.loopypowers.client.fx.PerfectedUpgradeRitualFxClient;
import com.yourname.loopypowers.client.fx.PowerRitualFxClient;
import com.yourname.loopypowers.client.fx.PowerUpgradeRitualFxClient;
import com.yourname.loopypowers.client.fx.RuinRitualFxClient;
import com.yourname.loopypowers.client.fx.SeveranceRitualFxClient;
import com.yourname.loopypowers.client.fx.SpaceRitualFxClient;
import com.yourname.loopypowers.network.payload.RitualFxPayload;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import com.yourname.loopypowers.network.AbilityPackets;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class LoopypowersClient implements ClientModInitializer {

	public static KeyBinding PRIMARY_ABILITY_KEY;
	public static KeyBinding SECONDARY_ABILITY_KEY;
	public static KeyBinding ULTIMATE_ABILITY_KEY;
	public static KeyBinding TOGGLE_PASSIVE_KEY;

	@Override
	public void onInitializeClient() {
		// entity rendering
		EntityRendererRegistry.register(ModEntities.POWER_FIREBALL, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.BLOOD_CLOT, com.yourname.loopypowers.client.BloodClotRenderer::new);
		EntityRendererRegistry.register(ModEntities.SONIC_BOLT, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.SHADOW_STEP, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.COMPEL_ENTITY, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.PUPPETRY_ENTITY, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.BLACK_HOLE_ENTITY, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.DISPLACE_ENTITY, EmptyEntityRenderer::new);
		// block rendering
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.THORN_VINE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ICE_SPIKE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CASINO_BARS, RenderLayer.getCutout());

		System.out.println("Loopypowers client loaded");

		PRIMARY_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.loopypowers.primary", GLFW.GLFW_KEY_G, "category.loopypowers"));
		SECONDARY_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.loopypowers.secondary", GLFW.GLFW_KEY_F, "category.loopypowers"));
		ULTIMATE_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.loopypowers.ultimate", GLFW.GLFW_KEY_Q, "category.loopypowers"));
		TOGGLE_PASSIVE_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.loopypowers.toggle_passive", GLFW.GLFW_KEY_APOSTROPHE, "category.loopypowers"));

		// 1.21.1 FIXED: Client Receivers using CustomPayloads
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.SyncStrengthPayload.ID,
				(payload, context) -> context.client().execute(() ->
						com.yourname.loopypowers.network.ClientPowerState.setStrengthPower(payload.hasStrength()))
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.HidePlayerPayload.ID,
				(payload, context) -> context.client().execute(() ->
						HiddenPlayersClient.hide(payload.entityId(), payload.ticks()))
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.CameraShakePayload.ID,
				(payload, context) -> context.client().execute(() ->
						CameraShakeClient.start(payload.ticks(), payload.strength()))
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.StunAudioPayload.ID,
				(payload, context) -> context.client().execute(() ->
						StunAudioClient.setStun(payload.ticks()))
		);

		// BloodPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(BloodBleedApplyPayload.ID, BloodFxClient::handleBleedApply);
		ClientPlayNetworking.registerGlobalReceiver(BloodBleedTickPayload.ID, BloodFxClient::handleBleedTick);
		ClientPlayNetworking.registerGlobalReceiver(BloodWhipBeamPayload.ID, BloodFxClient::handleWhipBeam);
		ClientPlayNetworking.registerGlobalReceiver(BloodWhipHitPayload.ID, BloodFxClient::handleWhipHit);
		ClientPlayNetworking.registerGlobalReceiver(BloodClotCastPayload.ID, BloodFxClient::handleClotCast);
		ClientPlayNetworking.registerGlobalReceiver(BloodPopPayload.ID, BloodFxClient::handlePop);
		ClientPlayNetworking.registerGlobalReceiver(BloodChainPayload.ID, BloodFxClient::handleChain);
		ClientPlayNetworking.registerGlobalReceiver(BloodBindTickPayload.ID, BloodFxClient::handleBindTick);
		ClientPlayNetworking.registerGlobalReceiver(BloodBindDamageFxPayload.ID, BloodFxClient::handleBindDamageFx);

		// CosmicPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(BlackHoleParticlePayload.ID, CosmicFxClient::handleBlackHoleParticles);
		ClientPlayNetworking.registerGlobalReceiver(CosmicFateAuraPayload.ID, CosmicFxClient::handleFateAura);
		ClientPlayNetworking.registerGlobalReceiver(CosmicDetonateStartPayload.ID, CosmicFxClient::handleDetonateStart);
		ClientPlayNetworking.registerGlobalReceiver(CosmicDetonateTickPayload.ID, CosmicFxClient::handleDetonateTick);
		ClientPlayNetworking.registerGlobalReceiver(CosmicFateCapPayload.ID, CosmicFxClient::handleFateCap);
		ClientPlayNetworking.registerGlobalReceiver(CosmicRayPayload.ID, CosmicFxClient::handleRay);
		ClientPlayNetworking.registerGlobalReceiver(CosmicRayHitPayload.ID, CosmicFxClient::handleRayHit);
		ClientPlayNetworking.registerGlobalReceiver(CosmicRayImpactPayload.ID, CosmicFxClient::handleRayImpact);
		ClientPlayNetworking.registerGlobalReceiver(CosmicStarTrailPayload.ID, CosmicFxClient::handleStarTrail);
		ClientPlayNetworking.registerGlobalReceiver(CosmicSlamPayload.ID, CosmicFxClient::handleSlam);

		// DarknessPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(DarknessBackstabFxPayload.ID, DarknessFxClient::handleBackstabFx);
		ClientPlayNetworking.registerGlobalReceiver(DarknessExposedFxPayload.ID,  DarknessFxClient::handleExposedFx);
		ClientPlayNetworking.registerGlobalReceiver(DarknessComboFxPayload.ID,    DarknessFxClient::handleComboFx);
		ClientPlayNetworking.registerGlobalReceiver(DarknessMistEnterPayload.ID,  DarknessFxClient::handleMistEnter);
		ClientPlayNetworking.registerGlobalReceiver(DarknessMistTrailPayload.ID,  DarknessFxClient::handleMistTrail);
		ClientPlayNetworking.registerGlobalReceiver(DarknessUltActivatePayload.ID, DarknessFxClient::handleUltActivate);
		ClientPlayNetworking.registerGlobalReceiver(DarknessBlackoutFxPayload.ID,  DarknessFxClient::handleBlackoutFx);
		ClientPlayNetworking.registerGlobalReceiver(DarknessBlackoutEndPayload.ID, DarknessFxClient::handleBlackoutEnd);

		// DimensionalPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(DimensionalFlickerPayload.ID,       DimensionalFxClient::handleFlicker);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalSituationalFxPayload.ID,  DimensionalFxClient::handleSituationalFx);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalPhaseEnterPayload.ID,     DimensionalFxClient::handlePhaseEnter);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalPhaseTrailPayload.ID,     DimensionalFxClient::handlePhaseTrail);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalPhaseExitPayload.ID,      DimensionalFxClient::handlePhaseExit);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalDisplaceFxPayload.ID,     DimensionalFxClient::handleDisplaceFx);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalRiftOpenPayload.ID,       DimensionalFxClient::handleRiftOpen);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalRiftTickPayload.ID,       DimensionalFxClient::handleRiftTick);
		ClientPlayNetworking.registerGlobalReceiver(DimensionalRiftEndPayload.ID,        DimensionalFxClient::handleRiftEnd);

		// ExplosionPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(ExplosionIgniteStartPayload.ID,     ExplosionFxClient::handleIgniteStart);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionIgniteTickPayload.ID,      ExplosionFxClient::handleIgniteTick);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionBurstPayload.ID,           ExplosionFxClient::handleBurst);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionUltCancelPayload.ID,       ExplosionFxClient::handleUltCancel);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionDropZonePayload.ID,        ExplosionFxClient::handleDropZone);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionUltAmbientPayload.ID,      ExplosionFxClient::handleUltAmbient);
		ClientPlayNetworking.registerGlobalReceiver(ExplosionFinisherChargePayload.ID,  ExplosionFxClient::handleFinisherCharge);

		// FirePower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(FireHoverPayload.ID,     FireFxClient::handleHover);
		ClientPlayNetworking.registerGlobalReceiver(FireUltChargePayload.ID, FireFxClient::handleUltCharge);

		// FlightPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(FlightKnockPayload.ID,       FlightFxClient::handleKnock);
		ClientPlayNetworking.registerGlobalReceiver(FlightGustPayload.ID,        FlightFxClient::handleGust);
		ClientPlayNetworking.registerGlobalReceiver(FlightGustTrailPayload.ID,   FlightFxClient::handleGustTrail);
		ClientPlayNetworking.registerGlobalReceiver(FlightUpdraftPayload.ID,     FlightFxClient::handleUpdraft);
		ClientPlayNetworking.registerGlobalReceiver(FlightBoomStartPayload.ID,   FlightFxClient::handleBoomStart);
		ClientPlayNetworking.registerGlobalReceiver(FlightBoomWindupPayload.ID,  FlightFxClient::handleBoomWindup);
		ClientPlayNetworking.registerGlobalReceiver(FlightBoomDashPayload.ID,    FlightFxClient::handleBoomDash);
		ClientPlayNetworking.registerGlobalReceiver(FlightBoomImpactPayload.ID,  FlightFxClient::handleBoomImpact);
		ClientPlayNetworking.registerGlobalReceiver(FlightTrailPayload.ID,       FlightFxClient::handleTrail);

		// LightningPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(LightningChargeReadyPayload.ID,       LightningFxClient::handleChargeReady);
		ClientPlayNetworking.registerGlobalReceiver(LightningHitBasicPayload.ID,           LightningFxClient::handleHitBasic);
		ClientPlayNetworking.registerGlobalReceiver(LightningHitTierPayload.ID,            LightningFxClient::handleHitTier);
		ClientPlayNetworking.registerGlobalReceiver(LightningChainTrailPayload.ID,         LightningFxClient::handleChainTrail);
		ClientPlayNetworking.registerGlobalReceiver(LightningChainHitPayload.ID,           LightningFxClient::handleChainHit);
		ClientPlayNetworking.registerGlobalReceiver(LightningClapConePayload.ID,           LightningFxClient::handleClapCone);
		ClientPlayNetworking.registerGlobalReceiver(LightningClapHitPayload.ID,            LightningFxClient::handleClapHit);
		ClientPlayNetworking.registerGlobalReceiver(LightningConfettiPayload.ID,           LightningFxClient::handleConfetti);
		ClientPlayNetworking.registerGlobalReceiver(LightningTargetConfettiPayload.ID,     LightningFxClient::handleTargetConfetti);
		ClientPlayNetworking.registerGlobalReceiver(LightningSuperchargeAuraPayload.ID,    LightningFxClient::handleSuperchargeAura);
		ClientPlayNetworking.registerGlobalReceiver(LightningSuperchargeBurstPayload.ID,   LightningFxClient::handleSuperchargeBurst);
		ClientPlayNetworking.registerGlobalReceiver(LightningMaelstromOpenPayload.ID,      LightningFxClient::handleMaelstromOpen);
		ClientPlayNetworking.registerGlobalReceiver(LightningStormTargetAuraPayload.ID,    LightningFxClient::handleStormTargetAura);
		ClientPlayNetworking.registerGlobalReceiver(LightningStormCloudsPayload.ID,        LightningFxClient::handleStormClouds);
		ClientPlayNetworking.registerGlobalReceiver(LightningStormStrikePayload.ID,        LightningFxClient::handleStormStrike);

		// IcePower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(IceShatterPayload.ID,          IceFxClient::handleShatter);
		ClientPlayNetworking.registerGlobalReceiver(IceFreezeStagePayload.ID,       IceFxClient::handleFreezeStage);
		ClientPlayNetworking.registerGlobalReceiver(IceShatterReadyPayload.ID,      IceFxClient::handleShatterReady);
		ClientPlayNetworking.registerGlobalReceiver(IceSpikeTrailPayload.ID,        IceFxClient::handleSpikeTrail);
		ClientPlayNetworking.registerGlobalReceiver(IceWaterFreezePayload.ID,       IceFxClient::handleWaterFreeze);
		ClientPlayNetworking.registerGlobalReceiver(IceSpikePuffPayload.ID,         IceFxClient::handleSpikePuff);
		ClientPlayNetworking.registerGlobalReceiver(IceBeamChargePayload.ID,        IceFxClient::handleBeamCharge);
		ClientPlayNetworking.registerGlobalReceiver(IceBeamLinePayload.ID,          IceFxClient::handleBeamLine);
		ClientPlayNetworking.registerGlobalReceiver(IceSnowPayload.ID,              IceFxClient::handleSnow);
		ClientPlayNetworking.registerGlobalReceiver(IceBlizzardPayload.ID,          IceFxClient::handleBlizzard);
		ClientPlayNetworking.registerGlobalReceiver(IceWaveRingPayload.ID,          IceFxClient::handleWaveRing);
		ClientPlayNetworking.registerGlobalReceiver(IceSnowAroundEntityPayload.ID,  IceFxClient::handleSnowAroundEntity);

		// HealingPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(HealingPassivePayload.ID,     HealingFxClient::handlePassive);
		ClientPlayNetworking.registerGlobalReceiver(HealingAbsorbHitPayload.ID,   HealingFxClient::handleAbsorbHit);
		ClientPlayNetworking.registerGlobalReceiver(HealingAbsorbTickPayload.ID,  HealingFxClient::handleAbsorbTick);
		ClientPlayNetworking.registerGlobalReceiver(HealingCleansePayload.ID,     HealingFxClient::handleCleanse);
		ClientPlayNetworking.registerGlobalReceiver(HealingBurstPayload.ID,       HealingFxClient::handleBurst);
		ClientPlayNetworking.registerGlobalReceiver(HealingExpelledPayload.ID,    HealingFxClient::handleExpelled);
		ClientPlayNetworking.registerGlobalReceiver(HealingUltTickPayload.ID,     HealingFxClient::handleUltTick);

		// TeleportPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(TeleportDodgePayload.ID,        TeleportFxClient::handleDodge);
		ClientPlayNetworking.registerGlobalReceiver(TeleportBlinkPayload.ID,        TeleportFxClient::handleBlink);
		ClientPlayNetworking.registerGlobalReceiver(TeleportAimBeamPayload.ID,      TeleportFxClient::handleAimBeam);
		ClientPlayNetworking.registerGlobalReceiver(TeleportSwapBurstPayload.ID,    TeleportFxClient::handleSwapBurst);
		ClientPlayNetworking.registerGlobalReceiver(TeleportFrenzyTickPayload.ID,   TeleportFxClient::handleFrenzyTick);
		ClientPlayNetworking.registerGlobalReceiver(TeleportFrenzyStrikePayload.ID, TeleportFxClient::handleFrenzyStrike);

		// TelekinesisPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(TKYankFallPayload.ID,          TelekinesisFxClient::handleYankFall);
		ClientPlayNetworking.registerGlobalReceiver(TKBeamPayload.ID,              TelekinesisFxClient::handleBeam);
		ClientPlayNetworking.registerGlobalReceiver(TKPassiveHitPayload.ID,        TelekinesisFxClient::handlePassiveHit);
		ClientPlayNetworking.registerGlobalReceiver(TKYankTargetPayload.ID,        TelekinesisFxClient::handleYankTarget);
		ClientPlayNetworking.registerGlobalReceiver(TKGrabTargetPayload.ID,        TelekinesisFxClient::handleGrabTarget);
		ClientPlayNetworking.registerGlobalReceiver(TKWallImpactPayload.ID,        TelekinesisFxClient::handleWallImpact);
		ClientPlayNetworking.registerGlobalReceiver(TKFloorImpactPayload.ID,       TelekinesisFxClient::handleFloorImpact);
		ClientPlayNetworking.registerGlobalReceiver(TKSuspendAuraPayload.ID,       TelekinesisFxClient::handleSuspendAura);
		ClientPlayNetworking.registerGlobalReceiver(TKChokeAuraPayload.ID,         TelekinesisFxClient::handleChokeAura);
		ClientPlayNetworking.registerGlobalReceiver(TKThrowPayload.ID,             TelekinesisFxClient::handleThrow);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisActivatePayload.ID,    TelekinesisFxClient::handleDebrisActivate);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisOrbitPayload.ID,       TelekinesisFxClient::handleDebrisOrbit);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisPullPayload.ID,        TelekinesisFxClient::handleDebrisPull);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisAuraPayload.ID,        TelekinesisFxClient::handleDebrisAura);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisThrowPayload.ID,       TelekinesisFxClient::handleDebrisThrow);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisExplosionPayload.ID,   TelekinesisFxClient::handleDebrisExplosion);
		ClientPlayNetworking.registerGlobalReceiver(TKDebrisSpawnPayload.ID,       TelekinesisFxClient::handleDebrisSpawn);

		// StrengthPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(StrengthOnePunchPayload.ID,      StrengthFxClient::handleOnePunch);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRageHitPayload.ID,       StrengthFxClient::handleRageHit);
		ClientPlayNetworking.registerGlobalReceiver(StrengthSlamPayload.ID,          StrengthFxClient::handleSlam);
		ClientPlayNetworking.registerGlobalReceiver(StrengthSlamTargetPayload.ID,    StrengthFxClient::handleSlamTarget);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRushTrailPayload.ID,     StrengthFxClient::handleRushTrail);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRushEntityHitPayload.ID, StrengthFxClient::handleRushEntityHit);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRushCancelPayload.ID,    StrengthFxClient::handleRushCancel);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRushCrashPayload.ID,     StrengthFxClient::handleRushCrash);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRagePulsePayload.ID,     StrengthFxClient::handleRagePulse);
		ClientPlayNetworking.registerGlobalReceiver(StrengthRageAuraPayload.ID,      StrengthFxClient::handleRageAura);

		// SpeedPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(SpeedLowHealthBurstPayload.ID,  SpeedFxClient::handleLowHealthBurst);
		ClientPlayNetworking.registerGlobalReceiver(SpeedDashCastPayload.ID,        SpeedFxClient::handleDashCast);
		ClientPlayNetworking.registerGlobalReceiver(SpeedDashTrailPayload.ID,       SpeedFxClient::handleDashTrail);
		ClientPlayNetworking.registerGlobalReceiver(SpeedPinballAnchorPayload.ID,   SpeedFxClient::handlePinballAnchor);
		ClientPlayNetworking.registerGlobalReceiver(SpeedOverdriveCastPayload.ID,   SpeedFxClient::handleOverdriveCast);
		ClientPlayNetworking.registerGlobalReceiver(SpeedOverdriveTrailPayload.ID,  SpeedFxClient::handleOverdriveTrail);
		ClientPlayNetworking.registerGlobalReceiver(SpeedExplosionFxPayload.ID,     SpeedFxClient::handleExplosionFx);

		// SoundPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(SoundResonanceBurstPayload.ID,  SoundFxClient::handleResonanceBurst);
		ClientPlayNetworking.registerGlobalReceiver(SoundBurstHitPayload.ID,        SoundFxClient::handleBurstHit);
		ClientPlayNetworking.registerGlobalReceiver(SoundBoomCastPayload.ID,        SoundFxClient::handleBoomCast);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassPullPayload.ID,        SoundFxClient::handleBassPull);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassPullHitPayload.ID,     SoundFxClient::handleBassPullHit);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassFinalPayload.ID,       SoundFxClient::handleBassFinal);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassFinalRemotePayload.ID, SoundFxClient::handleBassFinalRemote);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassPullVizPayload.ID,     SoundFxClient::handleBassPullViz);
		ClientPlayNetworking.registerGlobalReceiver(SoundBassBlastVizPayload.ID,    SoundFxClient::handleBassBlastViz);
		ClientPlayNetworking.registerGlobalReceiver(SoundUltWindupPayload.ID,       SoundFxClient::handleUltWindup);
		ClientPlayNetworking.registerGlobalReceiver(SoundUltBeamPayload.ID,         SoundFxClient::handleUltBeam);

		// PsychicPower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(PsychicLeechPayload.ID,       PsychicFxClient::handleLeech);
		ClientPlayNetworking.registerGlobalReceiver(PsychicCompelAuraPayload.ID,  PsychicFxClient::handleCompelAura);
		ClientPlayNetworking.registerGlobalReceiver(PsychicSpikeBeamPayload.ID,   PsychicFxClient::handleSpikeBeam);
		ClientPlayNetworking.registerGlobalReceiver(PsychicSpikeImpactPayload.ID, PsychicFxClient::handleSpikeImpact);
		ClientPlayNetworking.registerGlobalReceiver(PsychicSpikeAuraPayload.ID,   PsychicFxClient::handleSpikeAura);
		ClientPlayNetworking.registerGlobalReceiver(PsychicControlAuraPayload.ID, PsychicFxClient::handleControlAura);

		// NaturePower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(NatureCageFxPayload.ID,       NatureFxClient::handleCageFx);
		ClientPlayNetworking.registerGlobalReceiver(NatureGasTickPayload.ID,      NatureFxClient::handleGasTick);
		ClientPlayNetworking.registerGlobalReceiver(NatureVineCastPayload.ID,     NatureFxClient::handleVineCast);
		ClientPlayNetworking.registerGlobalReceiver(NatureVineBindPayload.ID,     NatureFxClient::handleVineBind);
		ClientPlayNetworking.registerGlobalReceiver(NatureVineStrikePayload.ID,   NatureFxClient::handleVineStrike);
		ClientPlayNetworking.registerGlobalReceiver(NatureVineTetherPayload.ID,   NatureFxClient::handleVineTether);
		ClientPlayNetworking.registerGlobalReceiver(NatureBuffRingPayload.ID,     NatureFxClient::handleBuffRing);

		// FortunePower fx receivers
		ClientPlayNetworking.registerGlobalReceiver(FortuneProcEnemyPayload.ID,    FortuneFxClient::handleProcEnemy);
		ClientPlayNetworking.registerGlobalReceiver(FortuneProcSelfPayload.ID,     FortuneFxClient::handleProcSelf);
		ClientPlayNetworking.registerGlobalReceiver(FortuneAllInPayload.ID,        FortuneFxClient::handleAllIn);
		ClientPlayNetworking.registerGlobalReceiver(FortuneJackpotPayload.ID,      FortuneFxClient::handleJackpot);
		ClientPlayNetworking.registerGlobalReceiver(FortuneDuelBeamPayload.ID,     FortuneFxClient::handleDuelBeam);
		ClientPlayNetworking.registerGlobalReceiver(FortuneDuelTetherPayload.ID,   FortuneFxClient::handleDuelTether);
		ClientPlayNetworking.registerGlobalReceiver(FortuneDuelAuraPayload.ID,     FortuneFxClient::handleDuelAura);
		ClientPlayNetworking.registerGlobalReceiver(FortuneUltCastPayload.ID,      FortuneFxClient::handleUltCast);
		ClientPlayNetworking.registerGlobalReceiver(FortuneHouseBuiltPayload.ID,   FortuneFxClient::handleHouseBuilt);
		ClientPlayNetworking.registerGlobalReceiver(FortuneHouseRoofPayload.ID,    FortuneFxClient::handleHouseRoof);
		ClientPlayNetworking.registerGlobalReceiver(FortuneHouseDestroyPayload.ID, FortuneFxClient::handleHouseDestroy);
		ClientPlayNetworking.registerGlobalReceiver(FortuneCenterFxPayload.ID,     FortuneFxClient::handleCenterFx);
		ClientPlayNetworking.registerGlobalReceiver(FortuneEntityFxPayload.ID,     FortuneFxClient::handleEntityFx);
		ClientPlayNetworking.registerGlobalReceiver(FortuneHotSeatPassPayload.ID,  FortuneFxClient::handleHotSeatPass);
		ClientPlayNetworking.registerGlobalReceiver(FortunePoofAtPayload.ID,       FortuneFxClient::handlePoofAt);
		ClientPlayNetworking.registerGlobalReceiver(FortuneJackpotHitPayload.ID,   FortuneFxClient::handleJackpotHit);
		ClientPlayNetworking.registerGlobalReceiver(FortuneCeilingBreakPayload.ID, FortuneFxClient::handleCeilingBreak);

		// Ritual fx receiver (shared payload, dispatches by ritualId)
		ClientPlayNetworking.registerGlobalReceiver(RitualFxPayload.ID, (payload, ctx) -> {
			ctx.client().execute(() -> {
				net.minecraft.client.world.ClientWorld world = ctx.client().world;
				if (world == null) return;
				double x = payload.x(), y = payload.y(), z = payload.z();
				int stage = payload.stage(), t = payload.stageTick();
				float progress = payload.progress();
				long time = payload.time();
				switch (payload.ritualId()) {
					case RitualFxPayload.ELEMENTAL     -> ElementalRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.LIFE          -> LifeRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.MIND          -> MindRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.MOTION        -> MotionRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.PERFECTED     -> PerfectedUpgradeRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.POWER         -> PowerRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.POWER_UPGRADE -> PowerUpgradeRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.RUIN          -> RuinRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.SEVERANCE     -> SeveranceRitualFxClient.render(world, x, y, z, stage, t, progress, time);
					case RitualFxPayload.SPACE         -> SpaceRitualFxClient.render(world, x, y, z, stage, t, progress, time);
				}
			});
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			if (!client.options.jumpKey.wasPressed()) return;
			if (client.player.isOnGround()) return;
			if (client.player.isFallFlying()) return;
			if (client.player.getVelocity().y > -0.08) return;

			// 1.21.1 FIXED: Sending Payload
			ClientPlayNetworking.send(new AbilityPackets.FlightGlidePayload());
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			while (PRIMARY_ABILITY_KEY.wasPressed()) sendPrimaryAbility();
			while (SECONDARY_ABILITY_KEY.wasPressed()) sendSecondaryAbility();
			while (ULTIMATE_ABILITY_KEY.wasPressed()) sendUltimateAbility();
			while (TOGGLE_PASSIVE_KEY.wasPressed()) sendTogglePassive();

			CameraShakeClient.tick(client);
			HiddenPlayersClient.tick();
			tickResonanceTrails(client);
			tickResonanceLines(client);
			StunAudioClient.tick();
		});

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.ResonanceTrailPayload.ID,
				(payload, context) -> context.client().execute(() -> {
					if (context.client().world == null) return;
					var e = Objects.requireNonNull(context.client().world).getEntityById(payload.targetId());
					if (!(e instanceof net.minecraft.entity.LivingEntity le)) return;

					Vec3d vel = le.getVelocity();
					Vec3d back = vel.lengthSquared() > 1.0e-4 ? vel.normalize().multiply(-0.35) : new Vec3d(0, 0, 0);
					int n = Math.max(1, Math.min(10, (int) (payload.count() * MathHelper.clamp(payload.intensity(), 0.6f, 1.6f))));
					ArrayDeque<ResTrailPoint> dq = RES_TRAILS.computeIfAbsent(payload.targetId(), k -> new ArrayDeque<>());

					for (int i = 0; i < n; i++) {
						double jx = (Objects.requireNonNull(context.client().world).random.nextDouble() - 0.5) * 0.45;
						double jy = Objects.requireNonNull(context.client().world).random.nextDouble() * (le.getHeight() * 0.9);
						double jz = (Objects.requireNonNull(context.client().world).random.nextDouble() - 0.5) * 0.45;

						double px = le.getX() + back.x + jx;
						double py = le.getY() + 0.10 + jy;
						double pz = le.getZ() + back.z + jz;

						dq.addLast(new ResTrailPoint(px, py, pz, payload.intensity()));
					}
					while (dq.size() > RES_TRAIL_MAX_POINTS) dq.removeFirst();
				})
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.ResonanceRingPayload.ID,
				(payload, context) -> context.client().execute(() -> {
					if (context.client().world == null) return;
					var e = Objects.requireNonNull(context.client().world).getEntityById(payload.targetId());
					if (!(e instanceof net.minecraft.entity.LivingEntity le)) return;

					double cx = le.getX();
					double cy = le.getY() + le.getHeight() * 0.55;
					double cz = le.getZ();

					int points = 18;
					double radius = 0.9 + (MathHelper.clamp(payload.intensity(), 0.6f, 1.6f) - 1.0) * 0.25;

					for (int i = 0; i < points; i++) {
						double a = (Math.PI * 2.0) * (i / (double) points);
						double x = cx + Math.cos(a) * radius;
						double z = cz + Math.sin(a) * radius;

						Objects.requireNonNull(context.client().world).addParticle(ParticleTypes.SCULK_CHARGE_POP, x, cy, z, 0.0, 0.0, 0.0);
						if (Objects.requireNonNull(context.client().world).random.nextFloat() < 0.35f) {
							Objects.requireNonNull(context.client().world).addParticle(ParticleTypes.SCULK_SOUL, x, cy + (Objects.requireNonNull(context.client().world).random.nextDouble() - 0.5) * 0.35, z, 0.0, 0.0, 0.0);
						}
					}
				})
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.ResonanceLinePayload.ID,
				(payload, context) -> context.client().execute(() ->
						RES_LINES.put(payload.targetId(), Math.max(RES_LINES.getOrDefault(payload.targetId(), 0), payload.ticks()))
				)
		);
	}
	// RESONANCE TRAIL BUFFER (client only)

	private static final Map<Integer, ArrayDeque<ResTrailPoint>> RES_TRAILS = new HashMap<>();
	private static final int RES_TRAIL_LIFETIME_TICKS = 150; // 3 seconds
	private static final int RES_TRAIL_MAX_POINTS = 90;     // cap per entity so it doesn't explode

	private static final DustParticleEffect RES_BLUE_DUST =
			new DustParticleEffect(new Vector3f(0.15f, 0.55f, 1.00f), 0.75f);

	private static final class ResTrailPoint {
		final double x, y, z;
		int age;
		float intensity;
		ResTrailPoint(double x, double y, double z, float intensity) {
			this.x = x; this.y = y; this.z = z;
			this.intensity = intensity;
			this.age = 0;
		}
	}

	private static void tickResonanceTrails(net.minecraft.client.MinecraftClient client) {
		if (client.world == null) return;

		Iterator<Map.Entry<Integer, ArrayDeque<ResTrailPoint>>> it = RES_TRAILS.entrySet().iterator();

		while (it.hasNext()) {
			var entry = it.next();
			ArrayDeque<ResTrailPoint> dq = entry.getValue();
			if (dq.isEmpty()) { it.remove(); continue; }

			// age it
			Iterator<ResTrailPoint> pit = dq.iterator();
			while (pit.hasNext()) {
				ResTrailPoint p = pit.next();
				p.age++;
				if (p.age >= RES_TRAIL_LIFETIME_TICKS) pit.remove();
			}

			if (dq.isEmpty()) { it.remove(); continue; }

			// re-emit a few particles each tick so it lingers
			// scale a bit with intensity, but keep it capped
			ResTrailPoint newest = dq.peekLast();
			float intensity = newest != null ? newest.intensity : 1.0f;

			int emit = Math.max(1, Math.min(6, (int)(2 * MathHelper.clamp(intensity, 0.6f, 1.6f))));

			// bias to newer points, but not always the exact same point
			int spreadPick = Math.min(dq.size(), 8);

			for (int k = 0; k < emit; k++) {
				ResTrailPoint p = dq.peekLast();
				if (p == null) break;

				// occasionally pick a slightly older point so it is still like seeable.
				if (spreadPick > 1 && client.world.random.nextFloat() < 0.45f) {
					int skip = client.world.random.nextInt(spreadPick);
					Iterator<ResTrailPoint> sit = dq.descendingIterator();
					ResTrailPoint chosen = p;
					for (int s = 0; s <= skip && sit.hasNext(); s++) chosen = sit.next();
					p = chosen;
				}

				// slight jitter so it looks like a smudge not a dotted line
				double jx = (client.world.random.nextDouble() - 0.5) * 0.18;
				double jy = (client.world.random.nextDouble() - 0.5) * 0.12;
				double jz = (client.world.random.nextDouble() - 0.5) * 0.18;

				client.world.addParticle(
						RES_BLUE_DUST,
						p.x + jx,
						p.y + jy,
						p.z + jz,
						0.0, 0.0, 0.0
				);
			}
		}
	}
	private static final Map<Integer, Integer> RES_LINES = new HashMap<>(); // targetId -> ticksLeft

	private static void tickResonanceLines(net.minecraft.client.MinecraftClient client) {
		if (client.world == null) return;
		if (client.player == null) return;

		var it = RES_LINES.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			int targetId = entry.getKey();
			int left = entry.getValue() - 1;

			if (left <= 0) {
				it.remove();
				continue;
			}
			entry.setValue(left);

			var e = client.world.getEntityById(targetId);
			if (!(e instanceof net.minecraft.entity.LivingEntity le)) continue;

			// draw line from NOT EYE! it was annoying
			Vec3d a = client.player.getPos().add(0, 0.20, 0);
			Vec3d b = le.getPos().add(0, le.getHeight() * 0.35, 0);

			Vec3d delta = b.subtract(a);
			double len = delta.length();
			if (len < 0.001) continue;

			int steps = MathHelper.clamp((int)(len * 10), 10, 80); // density along line
			Vec3d step = delta.multiply(1.0 / steps);

			Vec3d p = a;
			for (int i = 0; i <= steps; i++) {
				// make it a bit variable
				double jx = (client.world.random.nextDouble() - 0.5) * 0.06;
				double jy = (client.world.random.nextDouble() - 0.5) * 0.06;
				double jz = (client.world.random.nextDouble() - 0.5) * 0.06;

				client.world.addParticle(
						RES_BLUE_DUST,
						p.x + jx, p.y + jy, p.z + jz,
						0.0, 0.0, 0.0
				);

				p = p.add(step);
			}
		}
	}

	// sending payloads
	public static void sendPrimaryAbility() {
		ClientPlayNetworking.send(new AbilityPackets.PrimaryAbilityPayload());
	}

	public static void sendSecondaryAbility() {
		ClientPlayNetworking.send(new AbilityPackets.SecondaryAbilityPayload());
	}

	public static void sendUltimateAbility() {
		ClientPlayNetworking.send(new AbilityPackets.UltimateAbilityPayload());
	}

	public static void sendTogglePassive() {
		ClientPlayNetworking.send(new AbilityPackets.TogglePassivePayload());
	}
}

