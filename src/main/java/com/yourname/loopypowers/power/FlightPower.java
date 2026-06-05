package com.yourname.loopypowers.power;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class FlightPower implements Power {
    private static final Random RNG = new Random();

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, FlightState> ACTIVE_STATES = new HashMap<>();

    // Tiny memory cache to bridge the easter egg tag across death/respawn
    private static final Set<UUID> DIED_WITH_EGG = new HashSet<>();

    private static class FlightState {
        boolean flightActive = false;
        boolean glideRequest = false;
        boolean boomInvuln = false;

        int stallTicks = 0;
        int trailStep = 0;
        int soundStep = 0;
        int wingEnforceStep = 0;

        // primary state
        int gustCharges = GUST_MAX_CHARGES;
        int gustRechargeTicks = 0;
        int gustLockTicks = 0;
        int gustEmpowermentTicks = 0;

        // secondary state
        int updraftEmpowermentTicks = 0;
        int updraftBlocksBroken = 0;

        // Boom (Ultimate) State
        int boomWindup = 0;
        int boomDash = 0;
        float boomYaw = 0;

        // easter egg timer
        int funnyTimer = 600;
    }

    private static FlightState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new FlightState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // FLIGHT STOP WHEN HURT
    private static final int HURT_LOCK_DURATION = 20 * 2; // ground after hit
    private static final float HURT_KNOCKOUT_MIN_YVEL = -1.15f; // y level drop when hurt

    // Trail + sound cadence
    private static final int TRAIL_INTERVAL = 2;   // particle trail delay in flight
    private static final int SOUND_INTERVAL = 10;  // sound loop delay

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // Clean legacy tags, but keep the easter egg memory if they have it
        player.getCommandTags().removeIf(tag -> tag.startsWith("fl_") && !tag.equals("fl_hecanfly_done"));

        ACTIVE_STATES.put(player.getUuid(), new FlightState());

        if (PassiveManager.isEnabled(player)) {
            equipWings(player);
        }

        // If they died with the easter egg, inject it into their new respawned body
        if (DIED_WITH_EGG.remove(player.getUuid())) {
            player.getCommandTags().add("fl_hecanfly_done");
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        unequipWings(player);
        ACTIVE_STATES.remove(player.getUuid());

        player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED));

        if (player.isAlive()) {
            player.getCommandTags().remove("fl_hecanfly_done");
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        // If they die with the easter egg completed, save it to the cache
        if (player.getCommandTags().contains("fl_hecanfly_done")) {
            DIED_WITH_EGG.add(player.getUuid());
        }
        onRemove(player);
    }

    private static void equipWings(ServerPlayerEntity player) {
        ItemStack chest = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);

        if (chest.isOf(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR)) return;

        if (!chest.isEmpty()) {
            boolean inserted = player.getInventory().insertStack(chest.copy());
            if (!inserted) player.dropItem(chest.copy(), true);
            player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        ItemStack wings = new ItemStack(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR);
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, wings);
    }

    private static void unequipWings(ServerPlayerEntity player) {
        ItemStack chest = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        if (!chest.isOf(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR)) return;

        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        FlightState state = getState(player);
        boolean passiveOn = PassiveManager.isEnabled(player);

        // EGG
        if (state.funnyTimer > 0) {
            state.funnyTimer--;
        } else {
            player.getCommandTags().add("fl_hecanfly_done");
        }

        // timers
        if (state.stallTicks > 0) state.stallTicks--;
        if (state.gustLockTicks > 0) state.gustLockTicks--;

        // Gust recharge & UI updates (always tick so they recharge while passive is off)
        tickGustRecharge(player, state);
        updateGustCooldownUI(player, state);

        if (passiveOn) {
            // enforce wings
            state.wingEnforceStep--;
            if (state.wingEnforceStep <= 0) {
                state.wingEnforceStep = 20;
                equipWings(player);
            }

            tickSonicBoomUltimate(player, state);
            tickPassiveFlight(player, state);
            tickGustEmpowerment(player, state);
            tickUpdraftEmpowerment(player, state);

            // particles while flying
            if (player.isFallFlying()) {
                tickFlightFx(player, state);
            } else {
                state.trailStep = 0;
                state.soundStep = 0;
            }

        } else {
            // Passive is OFF: clean up states and unequip wings
            unequipWings(player);

            if (state.flightActive || player.isFallFlying()) {
                player.stopFallFlying();
                state.flightActive = false;
                state.trailStep = 0;
                state.soundStep = 0;
            }

            if (state.boomWindup > 0 || state.boomDash > 0) {
                clearBoomState(state);
                Vec3d bv = player.getVelocity();
                player.setVelocity(bv.x, Math.min(bv.y, 0), bv.z);
                player.velocityModified = true;
                syncPlayerVelocity(player);
            }

            if (state.gustEmpowermentTicks > 0) state.gustEmpowermentTicks--;
            if (state.updraftEmpowermentTicks > 0) state.updraftEmpowermentTicks--;
        }
    }

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        FlightState state = getState(victim);

        if (state.boomInvuln) return false; // Invulnerable during boom dash

        // now only ground when hit midair
        if (victim.isFallFlying() || state.flightActive) {
            // Force them downward + lock re-glide
            Vec3d v = victim.getVelocity();
            victim.setVelocity(v.x, Math.min(v.y, HURT_KNOCKOUT_MIN_YVEL), v.z);
            victim.velocityModified = true;
            syncPlayerVelocity(victim);

            // Apply visual Grounded effect using registry
            victim.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED), HURT_LOCK_DURATION, 0, false, false, true));

            // Clear flight states
            state.flightActive = false;
            state.trailStep = 0;
            state.soundStep = 0;
            victim.stopFallFlying();

            ServerWorld w = victim.getServerWorld();
            sendToViewers(w, victim, new FlightKnockPayload(victim.getX(), victim.getY(), victim.getZ()));
            w.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP,
                    victim.getSoundCategory(), 0.7f, 0.9f);
        }

        return true;
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    // PRIMARY: Gust
    private static final int GUST_MAX_CHARGES = 2;
    private static final int GUST_RECHARGE_TICKS = 160;
    private static final int GUST_LOCK_TICKS = 10;      // 0.5 sec delay between dashes

    private static final int GUST_EMPOWERMENT_DURATION = 50;
    private static final double GUST_BURST_STRENGTH = 1.8;
    private static final double GUST_EMPOWERMENT_PUSH = 0.07;
    private static final double GUST_MAX_HORIZ_SPEED = 3.3;

    @Override
    public boolean tryActivatePrimary(ServerPlayerEntity player) {
        if (!PassiveManager.isEnabled(player)) {
            player.sendMessage(Text.translatable("message.loopypowers.passive_disabled").formatted(Formatting.RED), true);
            return false;
        }

        FlightState state = getState(player);

        if (state.gustLockTicks > 0) return false;
        if (state.gustCharges <= 0) return false;

        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED))) {
            player.sendMessage(Text.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activatePrimary(player);
        return true;
    }

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        FlightState state = getState(player);

        state.gustCharges--;
        state.gustLockTicks = GUST_LOCK_TICKS;

        if (state.gustRechargeTicks <= 0) {
            state.gustRechargeTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);
        }

        // force flight to start
        state.glideRequest = true;

        // FUNNY EGG
        if (player.isFallFlying() && !player.getCommandTags().contains("fl_hecanfly_done")) {
            // % chance to play when used
            if (RNG.nextFloat() < 0.25f) {
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HECANFLY, player.getSoundCategory(), 1.2f, 1.0f);
                // mark em
                player.getCommandTags().add("fl_hecanfly_done");
            }
        }

        // direction
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z);

        if (dir.lengthSquared() < 1.0e-6) return;
        dir = dir.normalize();

        // apply boost
        Vec3d v = player.getVelocity();
        Vec3d boosted = v.add(dir.multiply(GUST_BURST_STRENGTH));

        Vec3d horiz = new Vec3d(boosted.x, 0, boosted.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3d clampedHoriz = horiz.normalize().multiply(GUST_MAX_HORIZ_SPEED);
            boosted = new Vec3d(clampedHoriz.x, boosted.y, clampedHoriz.z);
        }

        player.setVelocity(boosted);
        player.velocityModified = true;
        syncPlayerVelocity(player);

        state.gustEmpowermentTicks = GUST_EMPOWERMENT_DURATION;

        ServerWorld w = player.getServerWorld();
        sendToViewers(w, player, new FlightGustPayload(player.getX(), player.getY(), player.getZ()));
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GUST,
                player.getSoundCategory(),
                0.9f, 1.6f
        );
    }

    private void tickGustRecharge(ServerPlayerEntity player, FlightState state) {
        if (PowerManager.areCooldownsDisabled()) {
            state.gustCharges = GUST_MAX_CHARGES;
            state.gustRechargeTicks = 0;
            return;
        }

        if (state.gustCharges >= GUST_MAX_CHARGES) {
            state.gustRechargeTicks = 0;
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);

        if (state.gustRechargeTicks <= 0) state.gustRechargeTicks = maxTicks;

        state.gustRechargeTicks--;

        if (state.gustRechargeTicks <= 0) {
            state.gustCharges++;
            if (state.gustCharges < GUST_MAX_CHARGES) {
                state.gustRechargeTicks = maxTicks;
            } else {
                state.gustRechargeTicks = 0;
            }
        }
    }

    private void updateGustCooldownUI(ServerPlayerEntity player, FlightState state) {
        String key = "FlightUI:PRIMARY";

        if (state.gustCharges >= GUST_MAX_CHARGES || PowerManager.areCooldownsDisabled()) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        int maxTicks = PowerManager.getModifiedCooldownTicks(player, GUST_RECHARGE_TICKS);
        long endMs = System.currentTimeMillis() + (state.gustRechargeTicks * 50L);
        Text suffix = CooldownUI.makeChargeSuffix(
                state.gustCharges, GUST_MAX_CHARGES, state.gustRechargeTicks, Math.max(1, maxTicks)
        );
        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    private void tickGustEmpowerment(ServerPlayerEntity player, FlightState state) {
        if (state.gustEmpowermentTicks <= 0) return;
        state.gustEmpowermentTicks--;

        if (!player.isFallFlying()) return;

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir = dir.normalize();

        Vec3d v = player.getVelocity();
        Vec3d next = v.add(dir.multiply(GUST_EMPOWERMENT_PUSH));

        Vec3d horiz = new Vec3d(next.x, 0, next.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3d clampedHoriz = horiz.normalize().multiply(GUST_MAX_HORIZ_SPEED);
            next = new Vec3d(clampedHoriz.x, next.y, clampedHoriz.z);
        }

        player.setVelocity(next);
        player.velocityModified = true;
        syncPlayerVelocity(player);

        if (state.gustEmpowermentTicks % 3 == 0) {
            ServerWorld gustW = player.getServerWorld();
            sendToViewers(gustW, player, new FlightGustTrailPayload(player.getX(), player.getY(), player.getZ()));
        }
    }

    // SECONDARY: Updraft (Homelander Takeoff & Burst)
    private static final double UPDRAFT_LAUNCH_Y = 3.2;
    private static final double UPDRAFT_KNOCKBACK_RADIUS = 5.0;
    private static final double UPDRAFT_KNOCKBACK_STRENGTH = 1.8;

    private static final int UPDRAFT_EMPOWERMENT_DURATION = 10;
    private static final double UPDRAFT_EMPOWERMENT_PUSH_Y = 0.25;
    private static final double UPDRAFT_MAX_Y_SPEED = 3.8;

    private static final int UPDRAFT_MAX_BLOCKS_BROKEN = 20;
    private static final float UPDRAFT_MAX_HARDNESS = 5.0f;
    private static final float UPDRAFT_DAMAGE_PER_BLOCK = 1.0f;

    @Override
    public boolean tryActivateSecondary(ServerPlayerEntity player) {
        if (!PassiveManager.isEnabled(player)) {
            player.sendMessage(Text.translatable("message.loopypowers.passive_disabled").formatted(Formatting.RED), true);
            return false;
        }

        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED))) {
            player.sendMessage(Text.translatable("power.loopypowers.flight.grounded"), true);
            return false;
        }
        activateSecondary(player);
        return true;
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        FlightState state = getState(player);
        ServerWorld w = player.getServerWorld();

        state.updraftBlocksBroken = 0;

        // FX
        sendToViewers(w, player, new FlightUpdraftPayload(player.getX(), player.getY(), player.getZ()));
        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 1.5f, 0.8f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.UPDRAFT, player.getSoundCategory(), 1.5f, 0.6f);
        CameraShake.shakeNearby(player, 15, 12, 1.2f);

        // knockback nearby entities
        Box box = player.getBoundingBox().expand(UPDRAFT_KNOCKBACK_RADIUS);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            Vec3d away = e.getPos().subtract(player.getPos());
            if (away.lengthSquared() < 0.0001) away = new Vec3d(1, 0, 0);
            Vec3d kb = away.normalize().multiply(UPDRAFT_KNOCKBACK_STRENGTH).add(0, 0.5, 0);
            e.addVelocity(kb.x, kb.y, kb.z);
            syncEntityVelocity(e);
        }

        // velocity
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.0) + UPDRAFT_LAUNCH_Y, v.z);
        player.velocityModified = true;
        syncPlayerVelocity(player);

        // force flight and empowerment
        player.startFallFlying();
        state.flightActive = true;
        state.updraftEmpowermentTicks = UPDRAFT_EMPOWERMENT_DURATION;
    }

    private void tickUpdraftEmpowerment(ServerPlayerEntity player, FlightState state) {
        if (state.updraftEmpowermentTicks <= 0) return;
        state.updraftEmpowermentTicks--;

        // Re-enable flight if vanilla physics cancelled it by bumping the ceiling
        if (!player.isFallFlying()) {
            player.startFallFlying();
        }

        ServerWorld w = player.getServerWorld();
        Vec3d v = player.getVelocity();
        int blocksBrokenNow = 0;

        // 9-point grid for a perfect 3x3 tunnel
        double hW = player.getWidth() * 0.6;
        Vec3d[] offsets = {
            new Vec3d(0, 0, 0),
            new Vec3d(hW, 0, hW),   new Vec3d(-hW, 0, hW),
            new Vec3d(hW, 0, -hW),  new Vec3d(-hW, 0, -hW),
            new Vec3d(hW, 0, 0),    new Vec3d(-hW, 0, 0),
            new Vec3d(0, 0, hW),    new Vec3d(0, 0, -hW)
        };

        boolean hitUnbreakable = false;
        double checkDist = Math.max(v.y, 3.5);

        for (Vec3d offset : offsets) {
            Vec3d start = player.getPos().add(offset).add(0, player.getHeight() - 0.2, 0);
            Vec3d end = start.add(0, checkDist, 0);

            int iterations = 0;
            while (iterations < 6 && state.updraftBlocksBroken < UPDRAFT_MAX_BLOCKS_BROKEN) {
                iterations++;
                BlockHitResult hit = w.raycast(new RaycastContext(
                        start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = hit.getBlockPos();
                    BlockState bState = w.getBlockState(pos);
                    float hardness = bState.getHardness(w, pos);

                    if (hardness >= 0 && hardness <= UPDRAFT_MAX_HARDNESS) {
                        w.breakBlock(pos, true, player);
                        state.updraftBlocksBroken++;
                        blocksBrokenNow++;
                        start = hit.getPos().add(0, 0.05, 0);
                    } else {
                        hitUnbreakable = true;
                        break;
                    }
                } else {
                    break;
                }
            }
            if (hitUnbreakable) break;
        }

        if (hitUnbreakable) {
            state.updraftEmpowermentTicks = 0;
            player.setVelocity(v.x, -0.2, v.z);
            player.velocityModified = true;
            syncPlayerVelocity(player);
            return;
        }

        if (blocksBrokenNow > 0) {
            player.damage(w.getDamageSources().flyIntoWall(), blocksBrokenNow * UPDRAFT_DAMAGE_PER_BLOCK);
            CameraShake.shakeNearby(player, 12, 10, 0.8f);
            w.spawnParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
            v = new Vec3d(v.x, Math.max(v.y, UPDRAFT_LAUNCH_Y * 0.8), v.z);
        }

        double nextY = v.y + UPDRAFT_EMPOWERMENT_PUSH_Y;
        if (nextY > UPDRAFT_MAX_Y_SPEED) nextY = UPDRAFT_MAX_Y_SPEED;

        player.setVelocity(v.x, nextY, v.z);
        player.velocityModified = true;
        syncPlayerVelocity(player);

        if (state.updraftEmpowermentTicks % 2 == 0) {
            w.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.5, player.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        }
    }

    // Ultimate

    private static final int BOOM_WINDUP_TICKS = 30;
    private static final int BOOM_DASH_TICKS = 18;
    private static final double BOOM_SPEED = 5.0;
    private static final double BOOM_RADIUS = 4.0;
    private static final double BOOM_IMPACT_RADIUS = 6.0;
    private static final float  BOOM_IMPACT_DAMAGE = 18.5f;
    private static final double BOOM_IMPACT_KB = 2.8;
    private static final int BOOM_KNOCKOUT_DURATION = 20 * 3;

    @Override
    public boolean tryActivateUltimate(ServerPlayerEntity player) {
        if (!PassiveManager.isEnabled(player)) {
            player.sendMessage(Text.translatable("message.loopypowers.passive_disabled").formatted(Formatting.RED), true);
            return false;
        }
        if (player.isOnGround() || player.isTouchingWater()) {
            player.sendMessage(Text.translatable("power.loopypowers.flight.must_be_airborne"), true);
            return false;
        }
        activateUltimate(player);
        return true;
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        if (player.isOnGround() || player.isTouchingWater()) return;

        FlightState state = getState(player);

        state.boomWindup = BOOM_WINDUP_TICKS;
        state.boomDash = 0;
        state.boomInvuln = false;

        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        syncPlayerVelocity(player);
        player.fallDistance = 0;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
                player.getSoundCategory(), 1.5f, 1.0f);

        sendToViewers(w, player, new FlightBoomStartPayload(player.getX(), player.getY(), player.getZ()));
    }

    private void tickSonicBoomUltimate(ServerPlayerEntity player, FlightState state) {
        ServerWorld world = player.getServerWorld();

        // windup
        if (state.boomWindup > 0) {
            state.boomWindup--;

            CameraShake.shakeNearby(player, 4, 10, 0.40f);

            player.setVelocity(0, 0, 0);
            player.velocityModified = true;
            syncPlayerVelocity(player);
            player.fallDistance = 0;

            if (state.boomWindup % 2 == 0) {
                boolean windupIsStart = (state.boomWindup == BOOM_WINDUP_TICKS - 2);
                Vec3d look = player.getRotationVec(1.0f);
                sendToViewers(world, player, new FlightBoomWindupPayload(
                        player.getX(), player.getBodyY(0.5), player.getZ(),
                        look.x, look.y, look.z, windupIsStart));
            }

            if (state.boomWindup <= 0) {
                Vec3d dir = player.getRotationVec(1.0f).normalize();
                state.boomYaw = player.getYaw();

                state.boomDash = BOOM_DASH_TICKS;
                state.boomInvuln = true;

                Vec3d launch = dir.multiply(BOOM_SPEED);
                player.setVelocity(launch.x, Math.max(launch.y, 0.05), launch.z);
                player.velocityModified = true;
                syncPlayerVelocity(player);

                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                        player.getSoundCategory(), 1.6f, 1.0f);
            }

            return;
        }

        // BIG PUSH
        if (state.boomDash > 0) {
            state.boomDash--;

            float pitch = player.getPitch();

            float yawRad = (float) Math.toRadians(state.boomYaw);
            float pitchRad = (float) Math.toRadians(pitch);

            double x = -Math.sin(yawRad) * Math.cos(pitchRad);
            double y = -Math.sin(pitchRad);
            double z =  Math.cos(yawRad) * Math.cos(pitchRad);

            Vec3d dir = new Vec3d(x, y, z).normalize();

            double maxUp = 0.75;
            double maxDown = -0.85;
            dir = new Vec3d(dir.x, Math.max(maxDown, Math.min(maxUp, dir.y)), dir.z).normalize();

            player.setVelocity(dir.multiply(BOOM_SPEED));
            player.velocityModified = true;
            syncPlayerVelocity(player);
            player.fallDistance = 0;
            player.startFallFlying();

            boolean dashIsStart = (state.boomDash == BOOM_DASH_TICKS - 1);
            sendToViewers(world, player, new FlightBoomDashPayload(
                    player.getX(), player.getBodyY(0.5), player.getZ(),
                    dir.x, dir.y, dir.z, dashIsStart));

            Box box = player.getBoundingBox().expand(BOOM_RADIUS);
            List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != player);

            // --- ENTITY COLLISION CHECK ---
            boolean hitEntity = false;
            Box hitBox = player.getBoundingBox().expand(0.8); // 0.8 block tolerance for direct impact

            for (LivingEntity e : nearby) {
                if (hitBox.intersects(e.getBoundingBox())) {
                    hitEntity = true;
                    break;
                }
            }

            // If we didn't get a direct hit, push nearby entities out of the way
            if (!hitEntity) {
                for (LivingEntity e : nearby) {
                    Vec3d away = e.getPos().subtract(player.getPos());
                    if (away.lengthSquared() < 0.0001) continue;
                    Vec3d knock = away.normalize().multiply(0.9).add(0, 0.15, 0);
                    e.addVelocity(knock.x, knock.y, knock.z);
                    syncEntityVelocity(e);
                }
            }

            // Calculate if we collided with terrain OR an entity
            boolean collided =
                    player.horizontalCollision
                            || player.verticalCollision
                            || player.isOnGround()
                            || boomHitsBlock(world, player)
                            || hitEntity;

            if (collided) {
                doBoomImpact(world, player);
                clearBoomState(state);

                if (player.isFallFlying()) player.stopFallFlying();
                return;
            }

            if (state.boomDash <= 0) {
                clearBoomState(state);
            }
        }
    }


    /* ============================================================
       PASSIVE TICK SECTIONS
       ============================================================ */

    private void tickPassiveFlight(ServerPlayerEntity player, FlightState state) {
        if (player.isCreative() || player.isSpectator()) return;

        if (player.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED))) {
            if (player.isFallFlying()) player.stopFallFlying();
            state.flightActive = false;
            return;
        }

        if (player.isFallFlying() && player.isSneaking()) {
            player.stopFallFlying();
            state.flightActive = false;
            state.trailStep = 0;
            state.soundStep = 0;
            return;
        }

        if (player.getCommandTags().remove("fl_glide_req")) {
            state.glideRequest = true;
        }

        if (state.glideRequest) {
            state.glideRequest = false;

            if (!player.isOnGround() && !player.isTouchingWater() && !player.isFallFlying()) {
                player.startFallFlying();
                state.flightActive = true;
            }
        }

        state.flightActive = player.isFallFlying();
    }

    private void tickFlightFx(ServerPlayerEntity player, FlightState state) {
        ServerWorld w = player.getServerWorld();

        state.trailStep--;
        if (state.trailStep <= 0) {
            state.trailStep = TRAIL_INTERVAL;

            Vec3d vel = player.getVelocity();
            Vec3d back = vel.lengthSquared() > 0.001 ? vel.normalize().multiply(-0.6) : new Vec3d(0, 0, 0);
            double x = player.getX() + back.x;
            double y = player.getY() + 0.6;
            double z = player.getZ() + back.z;

            sendToViewers(w, player, new FlightTrailPayload(x, y, z));
        }

        state.soundStep--;
        if (state.soundStep <= 0) {
            state.soundStep = SOUND_INTERVAL;
            w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, player.getSoundCategory(),
                    0.35f, 1.35f);
        }
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 0; } // Controlled by charges
    @Override public long getSecondaryCooldownMs() { return 33_000; }
    @Override public long getUltimateCooldownMs() { return 170_000; }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static void syncPlayerVelocity(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));
    }

    private static void syncEntityVelocity(LivingEntity e) {
        e.velocityModified = true;
        if (e instanceof ServerPlayerEntity sp) syncPlayerVelocity(sp);
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, payload));
    }

    private boolean boomHitsBlock(ServerWorld world, ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        if (vel.lengthSquared() < 1.0e-4) return false;

        Vec3d start = player.getPos().add(0, 1.0, 0);
        Vec3d end = start.add(vel.normalize().multiply(2.4));

        HitResult hit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        return hit.getType() == HitResult.Type.BLOCK;
    }

    private void doBoomImpact(ServerWorld world, ServerPlayerEntity player) {
        Vec3d c = player.getPos();

        sendToViewers(world, player, new FlightBoomImpactPayload(c.x, c.y, c.z));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 1.2f, 0.9f);

        float explodepower = 1.8f;

        world.createExplosion(
                player,
                player.getX(), player.getY(), player.getZ(),
                explodepower,
                false,
                World.ExplosionSourceType.BLOCK
        );

        Box box = new Box(c, c).expand(BOOM_IMPACT_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        CameraShake.shakeNearby(player,
                14,
                18,
                0.50f);

        for (LivingEntity e : targets) {
            e.damage(com.yourname.loopypowers.damage.ModDamageTypes.sonic(player.getWorld(), player), BOOM_IMPACT_DAMAGE);

            Vec3d away = e.getPos().subtract(c);
            if (away.lengthSquared() < 0.0001) away = new Vec3d(0, 0, 1);
            Vec3d kb = away.normalize().multiply(BOOM_IMPACT_KB).add(0, 0.45, 0);
            e.addVelocity(kb.x, kb.y, kb.z);
            syncEntityVelocity(e);
        }
        player.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.GROUNDED), BOOM_KNOCKOUT_DURATION, 0, false, false, true));
        player.setVelocity(0, Math.min(player.getVelocity().y, -0.25), 0);
        player.velocityModified = true;
        syncPlayerVelocity(player);
    }

    private void clearBoomState(FlightState state) {
        state.boomDash = 0;
        state.boomInvuln = false;
        state.boomYaw = 0;
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Text.translatable("power.loopypowers.flight.name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.flight.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.flight.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.flight.ultimate_name").getString(); }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.flight.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Text.translatable("power.loopypowers.flight.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.flight.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.flight.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.flight.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.flight.description.ultimate").getString();
    }
}