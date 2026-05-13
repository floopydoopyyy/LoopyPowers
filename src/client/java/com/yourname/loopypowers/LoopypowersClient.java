package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.client.HiddenPlayersClient;
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

