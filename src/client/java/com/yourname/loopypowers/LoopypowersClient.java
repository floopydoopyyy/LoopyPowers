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
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import com.yourname.loopypowers.network.AbilityPackets;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// handles client side keybinds, packets and camerashake (if it would work) and player views
public class LoopypowersClient implements ClientModInitializer {

	// keybinds
	public static KeyBinding PRIMARY_ABILITY_KEY;
	public static KeyBinding SECONDARY_ABILITY_KEY;
	public static KeyBinding ULTIMATE_ABILITY_KEY;

	@Override
	public void onInitializeClient() {
		// entity rendering
		EntityRendererRegistry.register(ModEntities.POWER_FIREBALL, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.BLOOD_CLOT, com.yourname.loopypowers.client.BloodClotRenderer::new);
		EntityRendererRegistry.register(ModEntities.SONIC_BOLT, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		EntityRendererRegistry.register(ModEntities.SHADOW_STEP, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		EntityRendererRegistry.register(ModEntities.COMPEL_ENTITY, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		EntityRendererRegistry.register(ModEntities.PUPPETRY_ENTITY, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		EntityRendererRegistry.register(ModEntities.BLACK_HOLE_ENTITY, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		EntityRendererRegistry.register(ModEntities.DISPLACE_ENTITY, (ctx) -> new net.minecraft.client.render.entity.EmptyEntityRenderer<>(ctx));
		// block rendering
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.THORN_VINE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.ICE_SPIKE, RenderLayer.getCutout());
		BlockRenderLayerMap.INSTANCE.putBlock(ModBlocks.CASINO_BARS, RenderLayer.getCutout());

		System.out.println("Loopypowers client loaded");

		// registering keybinds
		PRIMARY_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.loopypowers.primary",
						GLFW.GLFW_KEY_G,
						"category.loopypowers"
				)
		);

		SECONDARY_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.loopypowers.secondary",
						GLFW.GLFW_KEY_F,
						"category.loopypowers"
				)
		);

		ULTIMATE_ABILITY_KEY = KeyBindingHelper.registerKeyBinding(
				new KeyBinding(
						"key.loopypowers.ultimate",
						GLFW.GLFW_KEY_Q,
						"category.loopypowers"
				)
		);

		// register some powers client side
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.SYNC_STRENGTH_POWER,
				(client, handler, buf, responseSender) -> {
					boolean hasStrength = buf.readBoolean();
					client.execute(() -> com.yourname.loopypowers.network.ClientPowerState.setStrengthPower(hasStrength));
				}
		);

		// reciever - hides player
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.HIDE_PLAYER,
				(client, handler, buf, responseSender) -> {
					int entityId = buf.readInt();
					int ticks = buf.readInt();
					//System.out.println("HIDE_PLAYER recv: " + entityId + " for " + ticks);
					client.execute(() -> HiddenPlayersClient.hide(entityId, ticks));
				}
		);

		// sets up camerashake
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.CAMERA_SHAKE,
				(client, handler, buf, responseSender) -> {
					int ticks = buf.readInt();
					float strength = buf.readFloat();
					client.execute(() -> CameraShakeClient.start(ticks, strength));
				}
		);

		// stun audio reduction
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.STUN_AUDIO,
				(client, handler, buf, responseSender) -> {
					int ticks = buf.readInt();
					client.execute(() -> StunAudioClient.setStun(ticks));
				}
		);

		// FOR POWERS USING JUMP
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;
			// detect if space used
			if (!client.options.jumpKey.wasPressed()) return;
			// must be falling and not gliding
			if (client.player.isOnGround()) return;
			if (client.player.isFallFlying()) return;
			// if also moving downwards
			if (client.player.getVelocity().y > -0.08) return;

			ClientPlayNetworking.send(AbilityPackets.FLIGHT_GLIDE_REQUEST, PacketByteBufs.empty()); // send request to glide
		});

		//this is the tick handler
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			while (PRIMARY_ABILITY_KEY.wasPressed()) sendPrimaryAbility();
			while (SECONDARY_ABILITY_KEY.wasPressed()) sendSecondaryAbility();
			while (ULTIMATE_ABILITY_KEY.wasPressed()) sendUltimateAbility();

			CameraShakeClient.tick(client);
			HiddenPlayersClient.tick();
			tickResonanceTrails(client);
			tickResonanceLines(client);
			StunAudioClient.tick();
		});

		// sound (the power) particle packets
		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.RESONANCE_TRAIL,
				(client, handler, buf, responseSender) -> {
					int targetId = buf.readInt();
					int count = buf.readInt();
					float intensity = buf.readFloat();

					client.execute(() -> {
						if (client.world == null) return;
						var e = client.world.getEntityById(targetId);
						if (!(e instanceof net.minecraft.entity.LivingEntity le)) return;

						// make it look like sculk trailing
						Vec3d vel = le.getVelocity();
						Vec3d back = vel.lengthSquared() > 1.0e-4 ? vel.normalize().multiply(-0.35) : new Vec3d(0, 0, 0);

						// density of particles but VERY clamped
						int n = Math.max(1, Math.min(10, (int) (count * MathHelper.clamp(intensity, 0.6f, 1.6f))));

						// store points so we can re-emit for ~3 seconds
						ArrayDeque<ResTrailPoint> dq = RES_TRAILS.computeIfAbsent(targetId, k -> new ArrayDeque<>());

						for (int i = 0; i < n; i++) {
							double jx = (client.world.random.nextDouble() - 0.5) * 0.45;
							double jy = client.world.random.nextDouble() * (le.getHeight() * 0.9);
							double jz = (client.world.random.nextDouble() - 0.5) * 0.45;

							double px = le.getX() + back.x + jx;
							double py = le.getY() + 0.10 + jy;
							double pz = le.getZ() + back.z + jz;

							dq.addLast(new ResTrailPoint(px, py, pz, intensity));
						}

						while (dq.size() > RES_TRAIL_MAX_POINTS) dq.removeFirst();
					});
				}
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.RESONANCE_RING,
				(client, handler, buf, responseSender) -> {
					int targetId = buf.readInt();
					float intensity = buf.readFloat();

					client.execute(() -> {
						if (client.world == null) return;
						var e = client.world.getEntityById(targetId);
						if (!(e instanceof net.minecraft.entity.LivingEntity le)) return;

						// mid-body
						double cx = le.getX();
						double cy = le.getY() + le.getHeight() * 0.55;
						double cz = le.getZ();

						int points = 18;
						double radius = 0.9 + (MathHelper.clamp(intensity, 0.6f, 1.6f) - 1.0) * 0.25;

						for (int i = 0; i < points; i++) {
							double a = (Math.PI * 2.0) * (i / (double) points);

							double x = cx + Math.cos(a) * radius;
							double z = cz + Math.sin(a) * radius;

							// ring around player
							client.world.addParticle(
									ParticleTypes.SCULK_CHARGE_POP,
									x, cy, z,
									0.0, 0.0, 0.0
							);

							// occasional effect
							if (client.world.random.nextFloat() < 0.35f) {
								client.world.addParticle(
										ParticleTypes.SCULK_SOUL,
										x, cy + (client.world.random.nextDouble() - 0.5) * 0.35, z,
										0.0, 0.0, 0.0
								);
							}
						}
					});
				}
		);

		ClientPlayNetworking.registerGlobalReceiver(
				AbilityPackets.RESONANCE_LINE,
				(client, handler, buf, responseSender) -> {
					int targetId = buf.readInt();
					int ticks = buf.readInt();
					float intensity = buf.readFloat(); // ignore!!!

					client.execute(() -> {
						// store/refresh
						RES_LINES.put(targetId, Math.max(RES_LINES.getOrDefault(targetId, 0), ticks));
					});
				}
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

	// sending packets
	public static void sendPrimaryAbility() {
		ClientPlayNetworking.send(
				AbilityPackets.PRIMARY_ABILITY,
				PacketByteBufs.empty()
		);
	}

	public static void sendSecondaryAbility() {
		ClientPlayNetworking.send(
				AbilityPackets.SECONDARY_ABILITY,
				PacketByteBufs.empty()
		);
	}

	public static void sendUltimateAbility() {
		ClientPlayNetworking.send(
				AbilityPackets.ULTIMATE_ABILITY,
				PacketByteBufs.empty()
		);
	}
}