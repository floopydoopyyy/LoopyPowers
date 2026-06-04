package com.yourname.loopypowers.power;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

import java.util.*;

import static com.yourname.loopypowers.CooldownUI.makeChargeSuffix;

public class TeleportPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, TeleportState> ACTIVE_STATES = new HashMap<>();

    private static class TeleportState {
        int blinkCharges = PRIMARY_MAX_CHARGES;
        int blinkRechargeTicks = -1;
        int blinkLockTicks = 0;

        int dodgeCdTicks = 0;
        int phaseTicks = 0;

        int frenzyTicks = 0;
        int frenzyStep = 0;
    }

    private static TeleportState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new TeleportState());
    }

    /* ============================================================
       CONSTANTS - PASSIVE
       ============================================================ */
    private static final float PASSIVE_DODGE_CHANCE     = 0.18f;
    private static final int   PASSIVE_HIDE_TICKS       = 12;
    private static final int   PASSIVE_IFRAME_TICKS     = 18;
    private static final int   PASSIVE_COOLDOWN_TICKS   = 160;

    /* ============================================================
       CONSTANTS - PRIMARY
       ============================================================ */
    private static final int    PRIMARY_MAX_CHARGES       = 3;
    private static final int    PRIMARY_RECHARGE_TICKS    = 120;
    private static final int    PRIMARY_LOCK_TICKS        = 5;
    private static final double PRIMARY_BLINK_DIST        = 14.0;
    private static final double PRIMARY_AIR_LOOK_THRESHOLD = 0.55;

    /* ============================================================
       CONSTANTS - SECONDARY
       ============================================================ */
    private static final long   SECONDARY_COOLDOWN_MS = 16_000;
    private static final double SECONDARY_RANGE       = 24.0;
    private static final double SECONDARY_HIT_MARGIN  = 2.0;

    /* ============================================================
       CONSTANTS - ULTIMATE
       ============================================================ */
    private static final long   ULTIMATE_COOLDOWN_MS          = 420_000;
    private static final int    ULTIMATE_DURATION_TICKS       = 120;
    private static final int    ULTIMATE_ATTACK_STEP_INITIAL  = 4;
    private static final int    ULTIMATE_ATTACK_STEP_ONGOING  = 6;
    private static final double ULTIMATE_SEARCH_RADIUS        = 7.0;
    private static final double ULTIMATE_TELEPORT_OFFSET      = -1.5;
    private static final double ULTIMATE_AURA_RADIUS          = 11.0;

    private static final Random RNG = new Random();

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tp_"));
        ACTIVE_STATES.put(player.getUuid(), new TeleportState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tp_"));
        ACTIVE_STATES.remove(player.getUuid());
        player.removeStatusEffect(StatusEffects.HASTE);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        TeleportState state = getState(player);

        if (state.blinkLockTicks > 0) state.blinkLockTicks--;
        tickBlinkRecharge(state);
        updateBlinkCooldownUI(player, state);

        if (state.dodgeCdTicks > 0) state.dodgeCdTicks--;
        if (state.phaseTicks > 0) state.phaseTicks--;

        if (state.frenzyTicks > 0) {
            tickFrenzy(player, state);
        }
    }

    // =========================
    // PASSIVE
    // =========================

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        return !tryDodge(victim);
    }

    public boolean tryDodge(ServerPlayerEntity player) {
        if (!PassiveManager.isEnabled(player)) return false;

        TeleportState state = getState(player);
        if (state.dodgeCdTicks > 0) return false;
        if (RNG.nextFloat() > PASSIVE_DODGE_CHANCE) return false;

        RenderPackets.hidePlayerFromOthers(player, PASSIVE_HIDE_TICKS);

        state.phaseTicks = PASSIVE_IFRAME_TICKS;
        state.dodgeCdTicks = PASSIVE_COOLDOWN_TICKS;

        ServerWorld w = player.getServerWorld();
        TeleportDodgePayload fx = new TeleportDodgePayload(player.getX(), player.getY() + 1, player.getZ());
        sendToViewers(w, player, fx);

        w.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, player.getSoundCategory(), 1.0f, 1.2f);

        return true;
    }

    // =========================
    // PRIMARY
    // =========================

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        TeleportState state = getState(player);
        if (state.blinkLockTicks > 0) return;
        if (state.blinkCharges <= 0) return;

        state.blinkCharges--;
        state.blinkLockTicks = PRIMARY_LOCK_TICKS;

        if (state.blinkRechargeTicks < 0) {
            state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
        }

        blinkForward(player);
    }

    private static void tickBlinkRecharge(TeleportState state) {
        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            state.blinkRechargeTicks = -1;
            return;
        }

        if (state.blinkRechargeTicks >= 0) {
            state.blinkRechargeTicks--;

            if (state.blinkRechargeTicks <= 0) {
                state.blinkCharges++;

                if (state.blinkCharges < PRIMARY_MAX_CHARGES) {
                    state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
                } else {
                    state.blinkRechargeTicks = -1;
                }
            }
        }
    }

    private static void updateBlinkCooldownUI(ServerPlayerEntity player, TeleportState state) {
        String key = "Teleport:PRIMARY";

        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        int leftTicks = state.blinkRechargeTicks;
        if (leftTicks < 0) leftTicks = PRIMARY_RECHARGE_TICKS;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = makeChargeSuffix(
                state.blinkCharges, PRIMARY_MAX_CHARGES, leftTicks, PRIMARY_RECHARGE_TICKS
        ).getString();

        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    private static void blinkForward(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d eye  = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);

        double distanceMultiplier = 1.0;
        if (look.y < 0) {
            distanceMultiplier = Math.max(0.2, 1.0 + look.y);
        }

        double actualDistance = PRIMARY_BLINK_DIST * distanceMultiplier;
        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;

        if (!allowAir) {
            look = new Vec3d(look.x, 0.0, look.z);
            if (look.lengthSquared() < 1.0e-6) return;
            look = look.normalize();
        }

        Vec3d desired = eye.add(look.multiply(actualDistance));
        if (!allowAir) {
            desired = new Vec3d(desired.x, player.getY(), desired.z);
        }

        Vec3d safe   = findSafeTeleportSpot(world, player, desired);
        Vec3d origin = player.getPos();

        // Trail + bursts (sent before teleporting so player is still at origin)
        TeleportBlinkPayload fx = new TeleportBlinkPayload(
                origin.x, origin.y, origin.z,
                safe.x, safe.y, safe.z);
        sendToViewers(world, player, fx);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 1.0f, 1.2f);
        world.playSound(null, player.getBlockPos(), ModSounds.TELEPORTSNAP, player.getSoundCategory(), 0.7f, 1.4f);

        safeTeleport(player, safe.x, safe.y, safe.z);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 5, 50, true, false, false));
    }

    private static Vec3d findSafeTeleportSpot(ServerWorld world, ServerPlayerEntity player, Vec3d desired) {
        BlockPos base = BlockPos.ofFloored(desired.x, desired.y, desired.z);
        Vec3d look = player.getRotationVec(1.0f);
        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;

        int[] order = new int[] { 0, 1, -1, 2, -2, 3, -3 };

        for (int dy : order) {
            BlockPos feet = base.up(dy);
            BlockPos head = feet.up();

            boolean feetEmpty = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
            boolean headEmpty = world.getBlockState(head).getCollisionShape(world, head).isEmpty();
            boolean feetSafeFluid = world.getFluidState(feet).isEmpty() || world.getFluidState(feet).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            boolean headSafeFluid = world.getFluidState(head).isEmpty() || world.getFluidState(head).isIn(net.minecraft.registry.tag.FluidTags.WATER);

            if (!feetEmpty || !headEmpty || !feetSafeFluid || !headSafeFluid) continue;

            if (!allowAir) {
                BlockPos below = feet.down();
                boolean hasFloor = !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
                boolean floorSafeFluid = world.getFluidState(below).isEmpty() || world.getFluidState(below).isIn(net.minecraft.registry.tag.FluidTags.WATER);
                if (!hasFloor || !floorSafeFluid) continue;
            }

            return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }

        HitResult hit = world.raycast(new RaycastContext(
                player.getEyePos(), desired,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3d direction = desired.subtract(player.getEyePos()).normalize();
            return hit.getPos().subtract(direction.multiply(0.5));
        }

        return desired;
    }

    @Override
    public long getPrimaryCooldownMs() { return 0; }

    // =========================
    // SECONDARY
    // =========================

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        Entity target = getLookedAtEntity(player);

        if (!(target instanceof LivingEntity living)) {
            secondaryWhiffFx(player, world);
            return;
        }

        Vec3d beamStart = player.getEyePos();
        Vec3d beamEnd   = living.getPos().add(0, living.getHeight() * 0.5, 0);

        // Aim beam to target + portal burst at target
        TeleportAimBeamPayload beam = new TeleportAimBeamPayload(
                beamStart.x, beamStart.y, beamStart.z,
                beamEnd.x, beamEnd.y, beamEnd.z);
        sendToViewers(world, player, beam);

        Vec3d pPos = player.getPos();
        Vec3d tPos = target.getPos();

        // Perform the swap
        player.teleport(world, tPos.x, tPos.y, tPos.z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
        target.requestTeleport(pPos.x, pPos.y, pPos.z);
        faceEntity(player, living);

        // Dual trails + large bursts at both positions
        TeleportSwapBurstPayload burst = new TeleportSwapBurstPayload(
                pPos.x, pPos.y, pPos.z,
                tPos.x, tPos.y, tPos.z);
        sendToViewers(world, player, burst);

        world.playSound(null, player.getBlockPos(), ModSounds.TELEPORTCLAP, player.getSoundCategory(), 0.7f, 1.4f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, player.getSoundCategory(), 0.7f, 1.4f);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 5, 50, true, false, false));
    }

    @Override
    public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }

    private static void secondaryWhiffFx(ServerPlayerEntity player, ServerWorld world) {
        Vec3d start = player.getEyePos();
        Vec3d end   = start.add(player.getRotationVec(1.0f).multiply(SECONDARY_RANGE));

        HitResult hr = world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        Vec3d hitPos = (hr.getType() == HitResult.Type.MISS) ? end : hr.getPos();

        TeleportAimBeamPayload beam = new TeleportAimBeamPayload(
                start.x, start.y, start.z,
                hitPos.x, hitPos.y, hitPos.z);
        sendToViewers(world, player, beam);

        world.playSound(null, player.getBlockPos(), ModSounds.TELEPORTCLAP, player.getSoundCategory(), 0.7f, 1.4f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, player.getSoundCategory(), 0.7f, 1.4f);
    }

    // =========================
    // ULTIMATE
    // =========================

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        TeleportState state = getState(player);
        state.frenzyTicks = ULTIMATE_DURATION_TICKS;
        state.frenzyStep  = ULTIMATE_ATTACK_STEP_INITIAL;

        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, player.getSoundCategory(), 0.8f, 1.4f);
    }

    @Override
    public long getUltimateCooldownMs() { return ULTIMATE_COOLDOWN_MS; }

    private void tickFrenzy(ServerPlayerEntity player, TeleportState state) {
        state.frenzyTicks--;
        if (state.frenzyTicks <= 0) return;

        ServerWorld world = player.getServerWorld();

        TeleportFrenzyTickPayload tick = new TeleportFrenzyTickPayload(player.getX(), player.getY(), player.getZ());
        sendToViewers(world, player, tick);

        if (state.frenzyStep > 0) {
            state.frenzyStep--;
            return;
        }

        state.frenzyStep = ULTIMATE_ATTACK_STEP_ONGOING;

        Box box = new Box(player.getPos(), player.getPos()).expand(ULTIMATE_SEARCH_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(RNG.nextInt(targets.size()));

        Vec3d behind  = target.getPos().add(target.getRotationVec(1.0f).multiply(ULTIMATE_TELEPORT_OFFSET));
        Vec3d safePos = findSafeTeleportSpot(world, player, behind);

        BlockPos feetPos = BlockPos.ofFloored(safePos);
        BlockPos headPos = feetPos.up();
        if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty() ||
                !world.getBlockState(headPos).getCollisionShape(world, headPos).isEmpty()) {
            safePos = target.getPos();
        }

        safeTeleport(player, safePos.x, safePos.y, safePos.z);
        faceEntity(player, target);
        forceAttack(player, target);

        TeleportFrenzyStrikePayload strike = new TeleportFrenzyStrikePayload(player.getX(), player.getY() + 1, player.getZ());
        sendToViewers(world, player, strike);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 0.6f, 1.5f);
    }

    // =========================
    // RAYCAST ENTITY HELPER
    // =========================

    private static Entity getLookedAtEntity(ServerPlayerEntity player) {
        Vec3d start = player.getEyePos();
        Vec3d look  = player.getRotationVec(1.0f);
        Vec3d end   = start.add(look.multiply(SECONDARY_RANGE));

        Box box = player.getBoundingBox().stretch(look.multiply(SECONDARY_RANGE)).expand(SECONDARY_HIT_MARGIN);

        var hit = ProjectileUtil.raycast(player, start, end, box,
                e -> e instanceof LivingEntity && e != player, SECONDARY_RANGE * SECONDARY_RANGE);

        return hit != null ? hit.getEntity() : null;
    }

    // =========================
    // TELEPORT HELPERS
    // =========================

    private static void safeTeleport(ServerPlayerEntity player, double x, double y, double z) {
        player.teleport(player.getServerWorld(), x, y, z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
    }

    private static void forceAttack(ServerPlayerEntity player, LivingEntity target) {
        float baseDamage = (float) player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
        DamageSource frenzySrc = ModDamageTypes.frenzy(player.getWorld(), player);

        float totalDamage = baseDamage;
        if (player.getWorld() instanceof ServerWorld serverWorld) {
            totalDamage = net.minecraft.enchantment.EnchantmentHelper.getDamage(
                    serverWorld, player.getMainHandStack(), target, frenzySrc, baseDamage);
        }

        player.swingHand(Hand.MAIN_HAND, true);
        target.damage(frenzySrc, totalDamage);
    }

    private static void faceEntity(ServerPlayerEntity player, Entity target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);
        Vec3d diff = targetPos.subtract(playerPos);

        double horizontalDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw   = (float)(MathHelper.atan2(diff.z, diff.x) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(MathHelper.atan2(diff.y, horizontalDist) * (180F / Math.PI)));

        player.setYaw(yaw);
        player.setPitch(pitch);
        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), yaw, pitch);
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    // =========================
    // DISPLAY
    // =========================

    @Override public String getName()          { return Text.translatable("power.loopypowers.teleport.name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.teleport.primary.name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.teleport.secondary.name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.teleport.ultimate.name").getString(); }

    @Override public String getOverviewDescription()  { return Text.translatable("power.loopypowers.teleport.description.overview").getString(); }
    @Override public String getPassiveName()          { return Text.translatable("power.loopypowers.teleport.passive.name").getString(); }
    @Override public String getPassiveDescription()   { return Text.translatable("power.loopypowers.teleport.description.passive").getString(); }
    @Override public String getPrimaryDescription()   { return Text.translatable("power.loopypowers.teleport.description.primary").getString(); }
    @Override public String getSecondaryDescription() { return Text.translatable("power.loopypowers.teleport.description.secondary").getString(); }
    @Override public String getUltimateDescription()  { return Text.translatable("power.loopypowers.teleport.description.ultimate").getString(); }
}
