package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.UUID;

public class LightningPower implements Power {
    private static final Random RNG = new Random();

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, LightningState> ACTIVE_STATES = new HashMap<>();

    private static class LightningState {
        int chargeTicks = 0;
        int chargeLockTicks = 0;
        boolean chargeReady = false;

        int superchargeTicks = 0;

        int stormTicks = 0;
        int stormStepTicks = 0;
    }

    private static LightningState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new LightningState());
    }

    // secondary active tag + tick interval for the aura
    private static final int    SUPERCHARGE_AURA_INTERVAL = 4; // aura pulse every 4 ticks (~0.2s)

    // PRIMARY
    private static final double CLAP_RANGE     = 7.0;
    private static final double CLAP_ANGLE_DEG = 65.0;  // horizontal spread
    private static final double CLAP_VERT_FLAT = 0.45;  // vertical squash on cone check (< 1 = flatter)

    private static final float  CLAP_MAX_DAMAGE    = 17.0f;   // close
    private static final float  CLAP_MIN_DAMAGE    = 4.5f;   // far
    // EGG Tuning
    private static final int PARTY_CHANCE = 650; // 1 in n chance

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("lt_")); // Cleanup legacy tags
        ACTIVE_STATES.put(player.getUuid(), new LightningState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("lt_"));
        ACTIVE_STATES.remove(player.getUuid());

        // Remove supercharge buffs
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.STRENGTH);
        player.removeStatusEffect(StatusEffects.REGENERATION);
        player.removeStatusEffect(StatusEffects.HASTE);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        LightningState state = getState(player);

        // builds charge when not doing damage
        if (state.chargeLockTicks > 0) {
            state.chargeLockTicks--;
        }

        // max charge until no more increases
        if (state.chargeTicks < MAX_CHARGE_TICKS) {
            state.chargeTicks++;
        }
        int tier = getChargeTier(state.chargeTicks);

        // if player just reached tier 4
        if (tier >= 4 && !state.chargeReady) {
            state.chargeReady = true;

            ServerWorld w = player.getServerWorld();

            // sound
            w.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    player.getSoundCategory(), 0.8f, 1.6f);

            // bigger full-charge notification — yellow ring burst + sparks
            spawnChargeReadyBurst(w, player);

            // shake so the player actually notices they're fully charged
            CameraShake.shakeNearby(player, 5, 8, 0.18f);
        }

        // If charge leaves tier 4
        if (tier < 4 && state.chargeReady) {
            state.chargeReady = false;
        }

        // supercharge aura tick — emits particles while active
        if (state.superchargeTicks > 0) {
            state.superchargeTicks--;
            tickSuperchargeAura(player, state);
        }

        // Ult things --------------------
        if (state.stormTicks > 0) {
            tickMaelstrom(player, state);
        }
    }

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        // Immunity to lightning damage
        return !source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_LIGHTNING);
    }

    // PASSIVE - hahah static like lightning
    private static final int MAX_CHARGE_TICKS = 20 * 10;     // seconds to max charge (must meet 20 10 times)
    private static final float MAX_BONUS_DAMAGE = 5.0f;      // bonus damage for full charge
    private static final int MIN_PROC_TICKS = 20 * 2;        // must wait 2s without damaging to proc

    // ring burst when fully charged — much more noticeable than before
    private static void spawnChargeReadyBurst(ServerWorld world, ServerPlayerEntity player) {
        LightningChargeReadyPayload payload = new LightningChargeReadyPayload(player.getX(), player.getY(), player.getZ());
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return; // this is for you dylan

        ServerWorld world = attacker.getServerWorld();

        // these appear no matter the tier
        LightningHitBasicPayload basicFx = new LightningHitBasicPayload(target.getId());
        PlayerLookup.tracking(world, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, basicFx));
        world.playSound(null, target.getBlockPos(),
                SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, // sound
                attacker.getSoundCategory(), 0.25f, 1.6f);

        LightningState state = getState(attacker);

        // tries to stop multi proc
        if (state.chargeLockTicks > 0) return;

        // This is the key: charge = time since last hit.
        if (state.chargeTicks < MIN_PROC_TICKS) {
            state.chargeTicks = 0;
            return;
        }

        // returns charge in tiers
        int tier = getChargeTier(state.chargeTicks);         // 1-4
        float bonus = computeBonusDamage(state.chargeTicks); // gets bonus damage

        // Bonus damage
        DamageSource src = ModDamageTypes.smite(attacker.getWorld(), attacker);
        target.damage(src, bonus);

        // Lightning based on tiers
        spawnTierLightning(world, target, tier);

        // yellow ring on target
        spawnHitRing(world, target, tier);

        int sparks = switch (tier) {
            case 1 -> 18;
            case 2 -> 28;
            case 3 -> 40;
            default -> 60;
        };

        LightningHitTierPayload tierFx = new LightningHitTierPayload(target.getId(), tier);
        PlayerLookup.tracking(world, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, tierFx));

        world.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                attacker.getSoundCategory(), 0.4f, 1.2f + (tier * 0.1f));

        world.playSound(null, target.getBlockPos(),
                ModSounds.SHOCK,
                attacker.getSoundCategory(), 0.9f, 1.00f);

        // Stun
        if (tier >= 3) {
            target.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.STUN), STUN_TICKS, STUN_AMP, true, true));
        }
        // chain stuff
        if (tier >= 4) {
            LivingEntity current = target;
            double chainRadius = 6.0; // reach
            int jumps = 3;            // how many times it can change

            for (int i = 0; i < jumps; i++) { // for each jump
                Box box = current.getBoundingBox().expand(chainRadius);

                LivingEntity currentTarget = current; // this is only here for some silly lambda rule

                List<LivingEntity> nearby = world.getEntitiesByClass(
                        LivingEntity.class,
                        box,
                        e -> e.isAlive() && e != attacker && e != currentTarget
                );

                if (nearby.isEmpty()) break;

                // pick nearest
                LivingEntity next = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity e : nearby) {
                    double d = e.squaredDistanceTo(current);
                    if (d < best) { best = d; next = e; }
                }
                if (next == null) break;

                next.damage(ModDamageTypes.smite(attacker.getWorld(), attacker), 2.0f);

                Vec3d a = current.getPos().add(0, current.getHeight() * 0.6, 0);
                Vec3d b = next.getPos().add(0, next.getHeight() * 0.6, 0);

                spawnChainTrail(world, a, b); // spawns chain effect

                LightningChainHitPayload chainHitFx = new LightningChainHitPayload(next.getId());
                PlayerLookup.tracking(world, next.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, chainHitFx));

                current = next;
            }
        }

        // resets charge
        state.chargeTicks = 0;

        // Small lock
        state.chargeLockTicks = 2;
    }

    // Hit ring is now reproduced client-side as part of LightningHitTierPayload and LightningStormStrikePayload
    private static void spawnHitRing(ServerWorld world, LivingEntity target, int tier) {
        // This is handled client-side by the payload now, but kept as a no-op for compatibility
    }

    private static int getChargeTier(int chargeTicks) {
        int c = MathHelper.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        float t = (float) c / (float) MAX_CHARGE_TICKS;

        if (t >= 0.90f) return 4;
        if (t >= 0.65f) return 3;
        if (t >= 0.40f) return 2;
        return 1;
    }

    private static void spawnTierLightning(ServerWorld world, LivingEntity target, int tier) {
        // Tier 1-2 cosmetic bolt
        // Tier 3-4 real bolt
        boolean cosmetic = tier <= 2;

        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;

        bolt.refreshPositionAfterTeleport(target.getX(), target.getY(), target.getZ());
        bolt.setCosmetic(cosmetic);
        world.spawnEntity(bolt);

        // second bolt if pretty much full charge
        if (tier >= 4 && RNG.nextFloat() < 0.35f) {
            LightningEntity bolt2 = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt2 != null) {
                bolt2.refreshPositionAfterTeleport(target.getX() + (RNG.nextDouble() - 0.5) * 1.5,
                        target.getY(),
                        target.getZ() + (RNG.nextDouble() - 0.5) * 1.5);
                bolt2.setCosmetic(true);
                world.spawnEntity(bolt2);
            }
        }
    }

    // Stun target
    private static final int STUN_TICKS = 20;   // 1 second
    private static final int STUN_AMP   = 1;    // slowness/weakness amplifier

    private static void spawnChainTrail(ServerWorld world, Vec3d from, Vec3d to) {
        LightningChainTrailPayload payload = new LightningChainTrailPayload(
                from.x, from.y, from.z, to.x, to.y, to.z);
        BlockPos mid = BlockPos.ofFloored((from.x + to.x) * 0.5, (from.y + to.y) * 0.5, (from.z + to.z) * 0.5);
        PlayerLookup.tracking(world, mid).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin  = player.getEyePos();
        Vec3d forward = player.getRotationVec(1.0f).normalize();

        // Squash vertically
        Vec3d flatForward = new Vec3d(forward.x, forward.y * CLAP_VERT_FLAT, forward.z).normalize();
        double cos = Math.cos(Math.toRadians(CLAP_ANGLE_DEG));

        Box box = new Box(player.getPos(), player.getPos()).expand(CLAP_RANGE, 2.5, CLAP_RANGE);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        player.swingHand(Hand.MAIN_HAND, true);

        // Roll for the funny
        boolean isParty = RNG.nextInt(PARTY_CHANCE) == 0;

        if (isParty) {
            world.playSound(null, player.getBlockPos(), ModSounds.PARTYPOPPER, player.getSoundCategory(), 1.0f, 1.0f);
            spawnConfetti(world, player);
        } else {
            world.playSound(null, player.getBlockPos(), ModSounds.THUNDERCLAP, player.getSoundCategory(), 0.7f, 1.4f);
            spawnClapCone(world, player);
        }

        for (LivingEntity target : targets) {
            Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(origin);
            double dist = to.length();

            if (dist < 0.001 || dist > CLAP_RANGE) continue;

            Vec3d flatTo = new Vec3d(to.x, to.y * CLAP_VERT_FLAT, to.z).normalize();
            if (flatForward.dotProduct(flatTo) < cos) continue;

            if (!player.canSee(target)) continue;

            double t = 1.0 - (dist / CLAP_RANGE);
            target.damage(ModDamageTypes.thunderclap(player.getWorld(), player), (float) (CLAP_MIN_DAMAGE + t * (CLAP_MAX_DAMAGE - CLAP_MIN_DAMAGE)));

            // Hit feedback
            if (isParty) {
                spawnTargetConfetti(world, target, t);
            } else {
                LightningClapHitPayload clapHitFx = new LightningClapHitPayload(target.getId(), (float) t);
                PlayerLookup.tracking(world, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, clapHitFx));
            }
        }
    }

    private void spawnClapCone(ServerWorld world, ServerPlayerEntity player) {
        Vec3d forward = player.getRotationVec(1.0f).normalize();
        Vec3d center  = player.getPos().add(0, 0.8, 0);
        LightningClapConePayload payload = new LightningClapConePayload(
                center.x, center.y, center.z, forward.x, forward.y, forward.z);
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private void spawnConfetti(ServerWorld world, ServerPlayerEntity player) {
        Vec3d forward = player.getRotationVec(1.0f).normalize();
        Vec3d center  = player.getPos().add(0, 0.8, 0);
        LightningConfettiPayload payload = new LightningConfettiPayload(
                center.x, center.y, center.z, forward.x, forward.y, forward.z);
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private void spawnTargetConfetti(ServerWorld world, LivingEntity target, double t) {
        LightningTargetConfettiPayload payload = new LightningTargetConfettiPayload(target.getId(), (float) t);
        PlayerLookup.tracking(world, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private static final int SECONDARY_DURATION = 160;

    // SECONDARY
    @Override
    public void activateSecondary(ServerPlayerEntity player) { // finally something simple
        ServerWorld world = player.getServerWorld();

        //visual lightning
        spawnCosmeticLightning(world, player.getPos());

        // buffs
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        SECONDARY_DURATION, 1, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,      SECONDARY_DURATION, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,  SECONDARY_DURATION, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE,         SECONDARY_DURATION, 0, true, true));

        // start the per-tick aura — 150 ticks matches the buff duration
        LightningState state = getState(player);
        state.superchargeTicks = 150;

        spawnSuperchargeBurst(world, player);
    }

    private void tickSuperchargeAura(ServerPlayerEntity player, LightningState state) {
        ServerWorld world = player.getServerWorld();
        if (state.superchargeTicks % SUPERCHARGE_AURA_INTERVAL != 0) return;
        boolean spark  = RNG.nextFloat() < 0.5f;
        boolean endRod = state.superchargeTicks % (SUPERCHARGE_AURA_INTERVAL * 5) == 0;
        LightningSuperchargeAuraPayload payload = new LightningSuperchargeAuraPayload(
                player.getX(), player.getY(), player.getZ(), world.getTime(), spark, endRod);
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private void spawnSuperchargeBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();
        LightningSuperchargeBurstPayload payload = new LightningSuperchargeBurstPayload(center.x, center.y, center.z);
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));

        for (int i = 0; i < 3; i++) {
            Vec3d offset = new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 2.5,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 2.5);
            spawnCosmeticLightning(world, offset);
        }

        CameraShake.shakeNearby(player, 6, 10, 0.22f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, player.getSoundCategory(), 0.9f, 1.5f);
    }

    // ULT
    private static final double STORM_RADIUS        = 15.0;
    private static final int    STORM_DURATION_TICKS = 300;  // 15 seconds
    private static final int    STORM_PULSE_TICKS    = 17;   // every 30 ticks

    private static final float STORM_DAMAGE     = 4.5f;


    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        LightningState state = getState(player);
        state.stormTicks = STORM_DURATION_TICKS;
        state.stormStepTicks = STORM_PULSE_TICKS;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                player.getSoundCategory(), 1.2f, 0.8f);

        // ult activation — big radial burst
        spawnMaelstromOpenBurst(w, player);
    }

    // massive burst when ult activates
    private static void spawnMaelstromOpenBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();
        LightningMaelstromOpenPayload payload = new LightningMaelstromOpenPayload(center.x, center.y, center.z);
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, payload));

        for (int i = 0; i < 5; i++) {
            spawnCosmeticLightning(world, new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 8.0, center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 8.0));
        }
        CameraShake.shakeNearby(player, 12, 15, 0.38f);
    }

    private void tickMaelstrom(ServerPlayerEntity player, LightningState state) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos(); // follows player

        state.stormTicks--;
        if (state.stormTicks <= 0) {
            state.stormStepTicks = 0;
            return;
        }

        // Clouds
        if (state.stormTicks % 6 == 0) spawnStormClouds(world, center);

        // Targets inside radius
        Box box = new Box(center, center).expand(STORM_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != player
        );

        // sparks on targets at risk of being hit
        if (!targets.isEmpty() && state.stormTicks % 3 == 0) {
            for (LivingEntity e : targets) {
                if (RNG.nextFloat() < 0.10f) {
                    LightningStormTargetAuraPayload auraFx = new LightningStormTargetAuraPayload(e.getId());
                    PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, auraFx));
                }
            }
        }

        // strike when timer done
        state.stormStepTicks--;

        if (state.stormStepTicks > 0) return;

        // reset timer
        state.stormStepTicks = STORM_PULSE_TICKS;

        if (targets.isEmpty()) return;

        // Strike 2 distinct targets if possible
        LivingEntity first = targets.get(RNG.nextInt(targets.size()));
        strikeStormTarget(world, player, first);

        if (targets.size() > 1) {
            LivingEntity second = first;
            for (int tries = 0; tries < 8 && second == first; tries++) {
                second = targets.get(RNG.nextInt(targets.size()));
            }
            if (second != first) strikeStormTarget(world, player, second);
        }
    }

    // black stormclouds with yellow
    private void spawnStormClouds(ServerWorld world, Vec3d center) {
        LightningStormCloudsPayload payload = new LightningStormCloudsPayload(center.x, center.y, center.z);
        PlayerLookup.tracking(world, BlockPos.ofFloored(center)).forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private void strikeStormTarget(ServerWorld world, ServerPlayerEntity caster, LivingEntity target) {
        spawnCosmeticLightning(world, target.getPos());
        target.damage(ModDamageTypes.smite(caster.getWorld(), caster), STORM_DAMAGE);

        LightningStormStrikePayload strikeFx = new LightningStormStrikePayload(target.getId());
        PlayerLookup.tracking(world, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, strikeFx));

        world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, caster.getSoundCategory(), 0.6f, 1.2f);
        CameraShake.shakeNearby(caster, 10, 15, 0.25f);
    }

    // ===========================
    // HELPERS
    // ===========================

    private static float computeBonusDamage(int chargeTicks) {
        int clamped = MathHelper.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        float t = (float) clamped / (float) MAX_CHARGE_TICKS; // 0..1

        // Ease curve: slow start, strong end
        float eased = t * t;

        return MAX_BONUS_DAMAGE * eased;
    }

    private static void spawnCosmeticLightning(ServerWorld world, Vec3d pos) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) return;

        bolt.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
        bolt.setCosmetic(true); // does no damage
        world.spawnEntity(bolt);
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName()          { return Text.translatable("power.loopypowers.lightning.name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.lightning.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.lightning.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.lightning.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 8_000;  } //
    @Override public long getSecondaryCooldownMs() { return 32_000; } //
    @Override public long getUltimateCooldownMs()  { return 450_000; } //


    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.lightning.description.overview").getString();
    }

    @Override public String getPassiveName() { return Text.translatable("power.loopypowers.lightning.passive_name").getString(); }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.lightning.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.lightning.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.lightning.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.lightning.description.ultimate").getString();
    }
}