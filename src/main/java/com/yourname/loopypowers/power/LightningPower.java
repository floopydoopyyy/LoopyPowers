package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import com.yourname.loopypowers.damage.ModDamageTypes;
import org.joml.Vector3f;

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

    /* ============================================================
       PARTICLES
       ============================================================ */

    private static final DustParticleEffect BOLT_YELLOW =
            new DustParticleEffect(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    private static final DustParticleEffect BOLT_GOLD =
            new DustParticleEffect(new Vector3f(1.00f, 0.95f, 0.55f), 1.1f);
    private static final DustParticleEffect BOLT_WHITE =
            new DustParticleEffect(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);
    private static final DustParticleEffect STORM_BLACK =
            new DustParticleEffect(new Vector3f(0.08f, 0.08f, 0.10f), 1.8f);
    private static final DustParticleEffect STORM_GREY =
            new DustParticleEffect(new Vector3f(0.20f, 0.20f, 0.22f), 1.5f);

    // secondary active tag + tick interval for the aura
    private static final int    SUPERCHARGE_AURA_INTERVAL = 4; // aura pulse every 4 ticks (~0.2s)

    // PRIMARY
    private static final double CLAP_RANGE     = 7.0;
    private static final double CLAP_ANGLE_DEG = 65.0;  // horizontal spread
    private static final double CLAP_VERT_FLAT = 0.45;  // vertical squash on cone check (< 1 = flatter)

    private static final float  CLAP_MAX_DAMAGE    = 13.0f;   // close
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
    private static final float MAX_BONUS_DAMAGE = 4.0f;      // bonus damage for full charge
    private static final int MIN_PROC_TICKS = 20 * 2;        // must wait 2s without damaging to proc

    // ring burst when fully charged — much more noticeable than before
    private static void spawnChargeReadyBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();

        // two concentric rings at ground + waist height
        for (double yOffset : new double[]{ 0.15, 1.0 }) {
            int points = 24;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                double speed = 0.18;
                world.spawnParticles(BOLT_YELLOW,
                        pos.x + Math.cos(angle) * 0.4, pos.y + yOffset, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }
        // upward column of sparks
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                50, 0.5, 0.8, 0.5, 0.10);
        world.spawnParticles(BOLT_WHITE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                14, 0.4, 0.5, 0.4, 0.06);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return; // this is for you dylan

        ServerWorld world = attacker.getServerWorld();

        // these appear no matter the tier
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                6, 0.25, 0.35, 0.25, 0.02);
        world.spawnParticles(BOLT_GOLD,
                target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.15, 0.20, 0.15, 0.015);
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

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                sparks, 0.45, 0.7, 0.45, 0.08);
        world.spawnParticles(BOLT_YELLOW,
                target.getX(), target.getY() + 1.0, target.getZ(),
                sparks / 2, 0.35, 0.55, 0.35, 0.07);

        world.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                attacker.getSoundCategory(), 0.4f, 1.2f + (tier * 0.1f));

        world.playSound(null, target.getBlockPos(),
                ModSounds.SHOCK,
                attacker.getSoundCategory(), 0.9f, 1.00f);

        // Stun
        if (tier >= 3) {
            target.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, STUN_TICKS, STUN_AMP, true, true));
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

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        14, 0.35, 0.5, 0.35, 0.06);
                world.spawnParticles(BOLT_YELLOW,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        8, 0.25, 0.35, 0.25, 0.05);

                current = next;
            }
        }

        // resets charge
        state.chargeTicks = 0;

        // Small lock
        state.chargeLockTicks = 2;
    }

    // yellow ring that expands outward from the target on a charged hit
    private static void spawnHitRing(ServerWorld world, LivingEntity target, int tier) {
        int    points = 8 + tier * 4;    // more points at higher tier
        double speed  = 0.12 + tier * 0.04;
        double h      = target.getY() + 0.8;

        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points;
            DustParticleEffect col = (i % 2 == 0) ? BOLT_YELLOW : BOLT_WHITE;
            world.spawnParticles(col,
                    target.getX() + Math.cos(angle) * 0.3,
                    h,
                    target.getZ() + Math.sin(angle) * 0.3,
                    1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
        }
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

    private static void spawnChainTrail(ServerWorld world, Vec3d from, Vec3d to) { //blink code but new font
        Vec3d delta = to.subtract(from); // draws a line between two areas
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp((int) (len * 10), 8, 60); // particles in line
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) {

            if (i % 3 == 0) {
                world.spawnParticles(BOLT_YELLOW,
                        p.x, p.y, p.z, 1, 0.03, 0.08, 0.03, 0.0);
            } else {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z, 1, 0.04, 0.10, 0.8, 0.0);
            }
            p = p.add(step);
        }
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
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), (int)(8 + 20 * t), 0.4, 0.6, 0.4, 0.06);
                world.spawnParticles(BOLT_YELLOW, target.getX(), target.getY() + 1.0, target.getZ(), (int)(6 + 12 * t), 0.3, 0.4, 0.3, 0.05);
            }
        }
    }

    private void spawnClapCone(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center  = player.getPos().add(0, 0.8, 0);   // at torso height
        Vec3d forward = player.getRotationVec(1.0f).normalize();

        // right vector — spreads particles perpendicular to look direction
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).normalize();

        double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);

        // 6 depth slices along the forward axis; each slice is wider than the last
        int   depthSlices = 6;
        int   arcPoints   = 20;

        for (int d = 1; d <= depthSlices; d++) {
            double depth    = CLAP_RANGE * ((double) d / depthSlices);
            double arcWidth = depth * Math.tan(halfAngle);   // cone widens with depth
            Vec3d  slicePos = center.add(forward.multiply(depth));

            for (int i = 0; i <= arcPoints; i++) {
                // lateral offset within the arc width for this slice
                double lateral = -arcWidth + 2.0 * arcWidth * ((double) i / arcPoints);
                double jitter  = (RNG.nextDouble() - 0.5) * 0.6;   // break the grid

                double x = slicePos.x + right.x * (lateral + jitter);
                double z = slicePos.z + right.z * (lateral + jitter);
                // vertical: very tight — CLAP_VERT_FLAT squashes it
                double y = slicePos.y + (RNG.nextDouble() - 0.5) * 1.5;

                if (RNG.nextFloat() > 0.65f) continue;   // sparse

                DustParticleEffect col = d <= 2 ? BOLT_WHITE
                        : d <= 4 ? BOLT_YELLOW
                        : BOLT_GOLD;
                world.spawnParticles(col, x, y, z, 1, 0, 0, 0, 0);

                if (RNG.nextFloat() < 0.20f) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            x, y, z, 1,
                            forward.x * 0.12, 0.01, forward.z * 0.12, 0.0);
                }
            }
        }

        // small central column of sparks right at the player's hand — origin of the blast
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y, center.z,
                18, 0.3, 0.3, 0.3, 0.08);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y, center.z,
                6, 0.15, 0.15, 0.15, 0.05);
    } // my head hurts

    // EGG
    private void spawnConfetti(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center  = player.getPos().add(0, 0.8, 0);
        Vec3d forward = player.getRotationVec(1.0f).normalize();
        Vec3d right = new Vec3d(-forward.z, 0, forward.x).normalize();

        double halfAngle = Math.toRadians(CLAP_ANGLE_DEG * 0.5);
        int depthSlices = 6;
        int arcPoints = 20;

        for (int d = 1; d <= depthSlices; d++) {
            double depth = CLAP_RANGE * ((double) d / depthSlices);
            double arcWidth = depth * Math.tan(halfAngle);
            Vec3d slicePos = center.add(forward.multiply(depth));

            for (int i = 0; i <= arcPoints; i++) {
                double lateral = -arcWidth + 2.0 * arcWidth * ((double) i / arcPoints);
                double jitter = (RNG.nextDouble() - 0.5) * 0.6;

                double x = slicePos.x + right.x * (lateral + jitter);
                double z = slicePos.z + right.z * (lateral + jitter);
                double y = slicePos.y + (RNG.nextDouble() - 0.5) * 1.5;

                if (RNG.nextFloat() > 0.50f) continue;

                // Randomized Rainbow Confetti
                Vector3f rainbow = new Vector3f(RNG.nextFloat(), RNG.nextFloat(), RNG.nextFloat());
                world.spawnParticles(new DustParticleEffect(rainbow, 1.1f), x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnTargetConfetti(ServerWorld world, LivingEntity target, double t) {
        int count = (int)(25 + 30 * t);
        for (int i = 0; i < count; i++) {
            Vector3f color = new Vector3f(RNG.nextFloat(), RNG.nextFloat(), RNG.nextFloat());
            world.spawnParticles(new DustParticleEffect(color, 0.85f), target.getX(), target.getY() + 1.0, target.getZ(), 1, 0.3, 0.4, 0.3, 0.03);
        }
    }



    // SECONDARY
    @Override
    public void activateSecondary(ServerPlayerEntity player) { // finally something simple
        ServerWorld world = player.getServerWorld();

        //visual lightning
        spawnCosmeticLightning(world, player.getPos());

        // buffs
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        150, 1, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,      150, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,  150, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE,         150, 0, true, true));

        // start the per-tick aura — 150 ticks matches the buff duration
        LightningState state = getState(player);
        state.superchargeTicks = 150;

        spawnSuperchargeBurst(world, player);
    }

    private void tickSuperchargeAura(ServerPlayerEntity player, LightningState state) {
        ServerWorld world = player.getServerWorld();

        if (state.superchargeTicks % SUPERCHARGE_AURA_INTERVAL != 0) return;

        Vec3d pos = player.getPos();

        int points = 16;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points + (world.getTime() * 0.10);
            double r     = 0.65;
            world.spawnParticles(BOLT_YELLOW,
                    pos.x + Math.cos(angle) * r, pos.y + 0.9, pos.z + Math.sin(angle) * r,
                    1, 0, 0.005, 0, 0.0);
        }

        if (RNG.nextFloat() < 0.5f) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + Math.cos(angle) * 0.65, pos.y + 0.9, pos.z + Math.sin(angle) * 0.65,
                    2, Math.cos(angle) * 0.10, 0.04, Math.sin(angle) * 0.10, 0.0);
        }

        if (state.superchargeTicks % (SUPERCHARGE_AURA_INTERVAL * 5) == 0) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (RNG.nextDouble() - 0.5) * 0.4,
                    pos.y + 0.6 + RNG.nextDouble() * 1.2,
                    pos.z + (RNG.nextDouble() - 0.5) * 0.4,
                    1, Math.cos(angle) * 0.15, 0.06, Math.sin(angle) * 0.15, 0.0);
        }
    }

    private void spawnSuperchargeBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();

        // tight charge ring at the feet
        spawnChargeRing(world, player);

        // large expanding ring from player
        int    outerPoints = 32;
        double outerSpeed  = 0.30;
        for (int i = 0; i < outerPoints; i++) {
            double angle = i * Math.PI * 2.0 / outerPoints;
            DustParticleEffect col = (i % 3 == 0) ? BOLT_WHITE
                    : (i % 3 == 1) ? BOLT_YELLOW
                    : BOLT_GOLD;
            world.spawnParticles(col,
                    center.x + Math.cos(angle) * 0.4, center.y + 0.3, center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * outerSpeed, 0.01, Math.sin(angle) * outerSpeed, 0.0);
        }

        // dense particles on player
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.5, center.z, 80, 0.7, 1.2, 0.7, 0.12);
        world.spawnParticles(BOLT_YELLOW,
                center.x, center.y + 1.0, center.z, 40, 0.5, 1.0, 0.5, 0.09);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z, 16, 0.3, 0.8, 0.3, 0.07);

        // fx at player
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 2.0 / 8;
            world.spawnParticles(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * 0.4,
                    center.y + 0.8 + RNG.nextDouble(),
                    center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.22, 0.04, Math.sin(angle) * 0.22, 0.0);
        }

        // cosmetic lightning
        for (int i = 0; i < 3; i++) {
            Vec3d offset = new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 2.5,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 2.5);
            spawnCosmeticLightning(world, offset);
        }

        CameraShake.shakeNearby(player, 6, 10, 0.22f);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                player.getSoundCategory(), 0.9f, 1.5f);
    }

    private void spawnChargeRing(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();
        int points = 40;

        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.x + Math.cos(angle) * 1.2;
            double z = center.z + Math.sin(angle) * 1.2;

            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, center.y + 0.2, z,
                    1, 0, 0, 0, 0);
        }
    }

    // ULT
    private static final double STORM_RADIUS        = 15.0;
    private static final int    STORM_DURATION_TICKS = 300;  // 15 seconds
    private static final int    STORM_PULSE_TICKS    = 17;   // every 30 ticks

    private static final float STORM_DAMAGE     = 4.0f;


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

        // four rings at ground, waist, chest, head
        for (double yOffset : new double[]{ 0.1, 0.6, 1.2, 1.9 }) {
            int    points = 28;
            double speed  = 0.35;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                DustParticleEffect col = switch (i % 3) {
                    case 0  -> BOLT_WHITE;
                    case 1  -> BOLT_YELLOW;
                    default -> BOLT_GOLD;
                };
                world.spawnParticles(col,
                        center.x + Math.cos(angle) * 0.4, center.y + yOffset, center.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.015, Math.sin(angle) * speed, 0.0);
            }
        }

        // pillar of sparks and yellow
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 1.0, center.z, 100, 1.0, 1.5, 1.0, 0.12);
        world.spawnParticles(BOLT_YELLOW,
                center.x, center.y + 1.0, center.z, 60, 0.8, 1.2, 0.8, 0.10);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z, 20, 0.4, 0.8, 0.4, 0.07);

        // cosmetic lightning
        for (int i = 0; i < 5; i++) {
            Vec3d lPos = new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 8.0,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 8.0);
            spawnCosmeticLightning(world, lPos);
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
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.25, 0.35, 0.25, 0.02);
                    world.spawnParticles(BOLT_YELLOW,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.15, 0.20, 0.15, 0.015);
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

        for (int i = 0; i < 80; i++) { // number of clouds
            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double rad = RNG.nextDouble() * STORM_RADIUS;

            double x = center.x + Math.cos(ang) * rad;
            double z = center.z + Math.sin(ang) * rad;
            double y = center.y + 6.0 + RNG.nextDouble() * 2.0;

            // black cloud body — dense overlapping puffs for a solid dark mass
            DustParticleEffect cloudCol = (RNG.nextFloat() < 0.6f) ? STORM_BLACK : STORM_GREY;
            world.spawnParticles(cloudCol,
                    x, y, z, 2, 0.55, 0.20, 0.55, 0.003);

            // very sparse vanilla cloud mixed in for puffiness
            if (RNG.nextFloat() < 0.18f) {
                world.spawnParticles(ParticleTypes.CLOUD,
                        x, y, z, 1, 0.40, 0.15, 0.40, 0.005);
            }

            // yellow under cloud
            if (RNG.nextFloat() < 0.45f) {
                world.spawnParticles(BOLT_YELLOW,
                        x + (RNG.nextDouble() - 0.5) * 1.2,
                        y - 0.5,
                        z + (RNG.nextDouble() - 0.5) * 1.2,
                        1, 0.12, 0.06, 0.12, 0.0);
            }

            // sparks from clouds
            if (RNG.nextFloat() < 0.28f) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        x, y - 0.7, z,
                        1, 0.15, 0.10, 0.15, 0.0);
            }
        }
    }

    private void strikeStormTarget(ServerWorld world, ServerPlayerEntity caster, LivingEntity target) {
        spawnCosmeticLightning(world, target.getPos());

        target.damage(ModDamageTypes.smite(caster.getWorld(), caster), STORM_DAMAGE);

        // fx
        spawnHitRing(world, target, 3);

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                22, 0.45, 0.7, 0.45, 0.08);
        world.spawnParticles(BOLT_YELLOW,
                target.getX(), target.getY() + 1.0, target.getZ(),
                14, 0.35, 0.55, 0.35, 0.07);
        world.spawnParticles(BOLT_WHITE,
                target.getX(), target.getY() + 0.8, target.getZ(),
                6, 0.20, 0.25, 0.20, 0.05);

        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2.0 / 6;
            world.spawnParticles(ParticleTypes.END_ROD,
                    target.getX() + Math.cos(angle) * 0.3,
                    target.getY() + 0.5 + RNG.nextDouble() * 1.5,
                    target.getZ() + Math.sin(angle) * 0.3,
                    1, Math.cos(angle) * 0.14, 0.06, Math.sin(angle) * 0.14, 0.0);
        }

        world.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                caster.getSoundCategory(), 0.6f, 1.2f);

        // camerashake
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

    @Override public String getName()          { return "Lightning"; }
    @Override public String getPrimaryName()   { return "Thunderclap"; }
    @Override public String getSecondaryName() { return "Overcharge"; }
    @Override public String getUltimateName()  { return "Stormcaller"; }

    @Override public long getSecondaryCooldownMs() { return 8_000; } //
    @Override public long getUltimateCooldownMs()  { return 32_000; } //
    @Override public long getPrimaryCooldownMs()   { return 365_000;  } //

    @Override
    public String getOverviewDescription() {
        return "Lightning is intended to be a close range brawler that can do high amounts of damage to groups of enemies quickly, but very little at range. " +
                "It should also be said that lightning damage produced is edited (so it doesn't do the normal half a heart that normal lightning does)";
    }

    @Override public String getPassiveName() { return "Static Charge"; }

    @Override
    public String getPassiveDescription() {
        return "Your fist charges up with bonus damage over time. Increased charge can cause lightning to strike the target or chain lightning and ignition to nearby enemies at higher charges.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Create a directional blast of sparks in front of you that damages and ignites entities in a wide cone. Damage falls off with distance.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Empower yourself with lightning, giving yourself temporary regeneration, strength and speed. This emits particles while active.";
    }

    @Override
    public String getUltimateDescription() {
        return "Create a storm above you, striking random nearby entities with lightning, igniting them and dealing high damage. The cloud" +
                " particles indicate the range of the ultimate.";
    }
}