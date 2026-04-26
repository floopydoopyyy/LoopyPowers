package com.yourname.loopypowers.power;

import com.yourname.loopypowers.network.CameraShake;
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

import java.util.Random;
import java.util.List;

public class LightningPower implements Power {
    private static final Random RNG = new Random();

    /* ============================================================
       PARTICLES — yellow dust added alongside vanilla spark types
       ============================================================ */

    // Bright lightning yellow — primary colour for FX
    private static final DustParticleEffect LIGHTNING_YELLOW =
            new DustParticleEffect(new Vector3f(1.00f, 0.88f, 0.05f), 1.4f);
    // Pale arc gold — secondary highlight, outer edges of blasts
    private static final DustParticleEffect ARC_GOLD =
            new DustParticleEffect(new Vector3f(1.00f, 0.95f, 0.55f), 1.1f);
    // White-yellow flash — used on the very hottest moments (hit centre, ult strike)
    private static final DustParticleEffect BOLT_WHITE =
            new DustParticleEffect(new Vector3f(1.00f, 1.00f, 0.82f), 1.6f);

    // TAGS N TIMERS CONSTANTS
    private static final String CHARGE_TICKS = "lt_charge_ticks_"; // PASSIVE
    private static final String CHARGE_LOCK = "lt_charge_lock_";   //

    // PRIMARY — now omnidirectional, no cone
    private static final double CLAP_RANGE = 7.0;

    private static final float CLAP_MAX_DAMAGE = 8.0f;   // close
    private static final float CLAP_MIN_DAMAGE = 2.5f;   // far

    private static final double CLAP_KB_MAX = 1.5;
    private static final double CLAP_KB_MIN = 0.6;

    private static final double CLAP_STUN_DISTANCE = 2.2; // how close they have to be for slowness

    @Override
    public void onAssign(ServerPlayerEntity player) {
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        // builds charge when not doing damage
        int ticks = getTimerTicks(player, CHARGE_TICKS);
        if (ticks < 0) ticks = 0;

        // lock between charging up again
        tickSingleTimer(player, CHARGE_LOCK);

        // max charge until no more increases
        if (ticks < MAX_CHARGE_TICKS) {
            setSingleTimerTag(player, CHARGE_TICKS, ticks + 1);
        }
        int tier = getChargeTier(ticks);

        // if player just reached tier 4
        if (tier >= 4 && !player.getCommandTags().contains(CHARGE_READY)) {
            player.getCommandTags().add(CHARGE_READY);

            ServerWorld w = player.getServerWorld();

            // sound
            w.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    player.getSoundCategory(), 0.8f, 1.6f);

            // bigger full-charge notification — yellow ring burst + sparks
            spawnChargeReadyBurst(w, player);
        }

        // If charge leaves tier 4
        if (tier < 4 && player.getCommandTags().contains(CHARGE_READY)) {
            player.getCommandTags().remove(CHARGE_READY);
        }

        // Ult things --------------------
        if (hasTagPrefix(player, STORM_TICKS)) {
            tickMaelstrom(player);
        }
    }

    // PASSIVE - hahah static like lightning
    private static final int MAX_CHARGE_TICKS = 20 * 10;      // seconds to max charge (must meet 20 10 times)
    private static final float MAX_BONUS_DAMAGE = 4.0f;      // bonus damage for full charge
    private static final int MIN_PROC_TICKS = 20 * 2; // must wait 2s without damaging to proc
    private static final String CHARGE_READY = "lt_charge_ready"; // marker, checks if charged

    // ring burst when fully charged — much more noticeable than before
    private static void spawnChargeReadyBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();

        // two concentric rings at ground + waist height
        for (double yOffset : new double[]{ 0.15, 1.0 }) {
            int points = 24;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                double speed = 0.18;
                world.spawnParticles(LIGHTNING_YELLOW,
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
        ServerWorld world = attacker.getServerWorld();

        // these appear no matter the tier
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                6, 0.25, 0.35, 0.25, 0.02);
        world.spawnParticles(ARC_GOLD,
                target.getX(), target.getY() + 1.0, target.getZ(),
                4, 0.15, 0.20, 0.15, 0.015);
        world.playSound(null, target.getBlockPos(),
                SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, // sound
                attacker.getSoundCategory(), 0.25f, 1.6f);

        // tries to stop multi proc
        if (hasTagPrefix(attacker, CHARGE_LOCK)) return;

        int chargeTicks = getTimerTicks(attacker, CHARGE_TICKS);
        if (chargeTicks < 0) chargeTicks = 0;

        // This is the key: charge = time since last hit.
        if (chargeTicks < MIN_PROC_TICKS) {
            setSingleTimerTag(attacker, CHARGE_TICKS, 0);
            return;
        }

        // returns charge in tiers
        int tier = getChargeTier(chargeTicks);         // 1-4
        float bonus = computeBonusDamage(chargeTicks); // gets bonus damage

        // Bonus damage
        DamageSource src = ModDamageTypes.smite(attacker.getWorld(), attacker);
        target.damage(src, bonus);

        // Lightning based on tiers
        spawnTierLightning(world, target, tier);

        // yellow ring burst on the target — scales with tier
        spawnHitRing(world, target, tier);

        // PARTICLES — yellow dust added alongside sparks
        int sparks = switch (tier) {
            case 1 -> 18;
            case 2 -> 28;
            case 3 -> 40;
            default -> 60;
        };

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                sparks, 0.45, 0.7, 0.45, 0.08);
        world.spawnParticles(LIGHTNING_YELLOW,
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
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, STUN_TICKS, STUN_AMP, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, STUN_TICKS, STUN_AMP, true, true));
        }
        // chain stuff
        if (tier >= 4) {
            LivingEntity current = target;
            double chainRadius = 6.0; // reach
            int jumps = 3; // how many times it can change

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
                    if (d < best) {
                        best = d;
                        next = e;
                    }
                }
                if (next == null) break;

                next.damage(ModDamageTypes.smite(attacker.getWorld(), attacker), 2.0f);

                Vec3d a = current.getPos().add(0, current.getHeight() * 0.6, 0);
                Vec3d b = next.getPos().add(0, next.getHeight() * 0.6, 0);

                spawnChainTrail(world, a, b); // spawns chain effect

                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        14, 0.35, 0.5, 0.35, 0.06);
                world.spawnParticles(LIGHTNING_YELLOW,
                        next.getX(), next.getY() + 1.0, next.getZ(),
                        8, 0.25, 0.35, 0.25, 0.05);

                current = next;
            }
        }

        // Debug
        attacker.sendMessage(net.minecraft.text.Text.literal(
                "ChargeTicks=" + chargeTicks + " tier=" + tier + " bonus=" + String.format("%.2f", bonus)
        ), true);

        // resets charge
        setSingleTimerTag(attacker, CHARGE_TICKS, 0);

        // Small lock
        setSingleTimerTag(attacker, CHARGE_LOCK, 2);
    }

    // yellow ring that expands outward from the target on a charged hit
    private static void spawnHitRing(ServerWorld world, LivingEntity target, int tier) {
        int points   = 8 + tier * 4;   // more points at higher tier
        double speed = 0.12 + tier * 0.04;
        double h     = target.getY() + 0.8;

        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2.0 / points;
            DustParticleEffect col = (i % 2 == 0) ? LIGHTNING_YELLOW : BOLT_WHITE;
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
    private static final int STUN_TICKS = 20;                 // 1 second
    private static final int STUN_AMP = 1;                    // slowness/weakness amplifier

    private static void spawnChainTrail(ServerWorld world, Vec3d from, Vec3d to) { //blink code but new font
        Vec3d delta = to.subtract(from); // draws a line between two areas
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp((int) (len * 10), 8, 60); // particles in line
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) {
            // alternate yellow dust and sparks along the chain for a more vivid trail
            if (i % 3 == 0) {
                world.spawnParticles(LIGHTNING_YELLOW,
                        p.x, p.y, p.z,
                        1, 0.03, 0.08, 0.03, 0.0);
            } else {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.x, p.y, p.z,
                        1, 0.04, 0.10, 0.8, 0.0);
            }
            p = p.add(step);
        }
    }

    /* ============================================================
       PRIMARY — now hits everyone in range, not just the cone.
       Damage still falls off with distance.
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin = player.getPos().add(0, 1, 0);

        // full sphere around the player — no angle check anymore
        Box box = new Box(player.getPos(), player.getPos()).expand(CLAP_RANGE, 2.5, CLAP_RANGE);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        //animations
        player.swingHand(Hand.MAIN_HAND, true);

        // radial shockwave burst — replaces the old cone since we're not directional anymore
        spawnClapBurst(world, player);

        world.playSound(null, player.getBlockPos(),
                ModSounds.THUNDERCLAP,
                player.getSoundCategory(),
                0.7f, 1.4f);

        int hits = 0;

        for (LivingEntity target : targets) {
            Vec3d to = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(origin);
            double dist = to.length();
            if (dist < 0.001 || dist > CLAP_RANGE) continue;

            // damage
            double t = 1.0 - (dist / CLAP_RANGE); // 1 near, 0 far
            float damage = (float) (CLAP_MIN_DAMAGE + t * (CLAP_MAX_DAMAGE - CLAP_MIN_DAMAGE));

            target.damage(ModDamageTypes.thunderclap(player.getWorld(), player), damage);

            // knockback
            Vec3d dir = to.normalize();
            double kb = CLAP_KB_MIN + t * (CLAP_KB_MAX - CLAP_KB_MIN);
            Vec3d push = dir.multiply(kb).add(0, 0.1 + t * 0.15, 0); // knockback (change constants above)
            target.addVelocity(push.x, push.y, push.z);
            target.velocityModified = true;
            target.setOnFireFor(2); // fire on hit

            // Close-range mini stun
            if (dist <= CLAP_STUN_DISTANCE) {
                target.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, 14, 1, true, true
                ));
            }

            // hit spark burst at each target, scaled by proximity
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    (int)(8 + 20 * t), 0.4, 0.6, 0.4, 0.06);
            world.spawnParticles(LIGHTNING_YELLOW,
                    target.getX(), target.getY() + 1.0, target.getZ(),
                    (int)(6 + 12 * t), 0.3, 0.4, 0.3, 0.05);
            world.spawnParticles(BOLT_WHITE,
                    target.getX(), target.getY() + 0.8, target.getZ(),
                    (int)(3 + 6 * t), 0.2, 0.2, 0.2, 0.04);

            hits++;
        }

        if (hits == 0) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT,
                    player.getSoundCategory(),
                    0.4f, 0.8f);
        }
    }

    // radial thunderclap shockwave: three expanding rings + a central spark column
    private void spawnClapBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();

        // three rings at different radii fired simultaneously — looks like a shockwave expanding
        for (double ringFraction : new double[]{ 0.35, 0.65, 1.0 }) {
            double r      = CLAP_RANGE * ringFraction;
            int    points = (int)(16 + ringFraction * 20);
            double speed  = 0.18 + ringFraction * 0.08;

            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;

                // inner ring: yellow; mid: arc gold; outer: mixed sparks + yellow
                DustParticleEffect col = ringFraction < 0.5 ? BOLT_WHITE
                        : ringFraction < 0.8 ? LIGHTNING_YELLOW
                        : ARC_GOLD;

                // small random jitter to break the perfect circle and look more electric
                double jitter = (RNG.nextDouble() - 0.5) * 0.5;
                world.spawnParticles(col,
                        center.x + Math.cos(angle) * (r + jitter),
                        center.y + 0.2 + RNG.nextDouble() * 2.2,
                        center.z + Math.sin(angle) * (r + jitter),
                        1, Math.cos(angle) * speed * 0.2, 0.01, Math.sin(angle) * speed * 0.2, 0.0);

                // sparse electric sparks layered on top of the outer ring
                if (ringFraction >= 1.0 && RNG.nextFloat() < 0.40f) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            center.x + Math.cos(angle) * r,
                            center.y + 0.3 + RNG.nextDouble() * 2.0,
                            center.z + Math.sin(angle) * r,
                            1, Math.cos(angle) * 0.06, 0.02, Math.sin(angle) * 0.06, 0.01);
                }
            }
        }

        // central upward column of sparks and yellow dust — the clap origin
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 1.0, center.z,
                45, 0.8, 0.8, 0.8, 0.10);
        world.spawnParticles(LIGHTNING_YELLOW,
                center.x, center.y + 1.0, center.z,
                20, 0.6, 0.7, 0.6, 0.08);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z,
                8, 0.3, 0.4, 0.3, 0.05);

        // END_ROD spikes fired outward to represent actual arc paths
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI * 2.0 / 12;
            world.spawnParticles(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * 0.5,
                    center.y + 0.5 + RNG.nextDouble() * 1.5,
                    center.z + Math.sin(angle) * 0.5,
                    1, Math.cos(angle) * 0.18, 0.03, Math.sin(angle) * 0.18, 0.0);
        }
    } // my head hurts


    // SECONDARY
    @Override
    public void activateSecondary(ServerPlayerEntity player) { // finally something simple
        ServerWorld world = player.getServerWorld();

        //visual lightning
        spawnCosmeticLightning(world, player.getPos());

        // buffs
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 150, 1, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 150, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 150, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 150, 0, true, true));

        //used to be code to strike nearby entities with lightning but this was silly

        spawnSuperchargeBurst(world, player);
    }

    // full supercharge FX: concentric rings, upward column, arc spikes, camera shake
    private void spawnSuperchargeBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();

        // tight charge ring at the feet
        spawnChargeRing(world, player);

        // large expanding ring fired outward from the player
        int outerPoints = 32;
        double outerSpeed = 0.30;
        for (int i = 0; i < outerPoints; i++) {
            double angle = i * Math.PI * 2.0 / outerPoints;
            DustParticleEffect col = (i % 3 == 0) ? BOLT_WHITE
                    : (i % 3 == 1) ? LIGHTNING_YELLOW
                    : ARC_GOLD;
            world.spawnParticles(col,
                    center.x + Math.cos(angle) * 0.4, center.y + 0.3, center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * outerSpeed, 0.01, Math.sin(angle) * outerSpeed, 0.0);
        }

        // dense spark + yellow upward column — the charge flooding into the player
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 0.5, center.z,
                80, 0.7, 1.2, 0.7, 0.12);
        world.spawnParticles(LIGHTNING_YELLOW,
                center.x, center.y + 1.0, center.z,
                40, 0.5, 1.0, 0.5, 0.09);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z,
                16, 0.3, 0.8, 0.3, 0.07);

        // arc spikes around the player — 8 END_ROD bolts fired outward at body height
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI * 2.0 / 8;
            world.spawnParticles(ParticleTypes.END_ROD,
                    center.x + Math.cos(angle) * 0.4,
                    center.y + 0.8 + RNG.nextDouble() * 1.0,
                    center.z + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.22, 0.04, Math.sin(angle) * 0.22, 0.0);
        }

        // cosmetic lightning around for chaos
        for (int i = 0; i < 3; i++) {
            Vec3d offset = new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 2.5,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 2.5);
            spawnCosmeticLightning(world, offset);
        }

        // feels like a real charge-up
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
    private static final String STORM_TICKS = "lt_storm_ticks_";
    private static final String STORM_STEP  = "lt_storm_step_";

    private static final double STORM_RADIUS = 15.0;
    private static final int STORM_DURATION_TICKS = 300; // 15 seconds
    private static final int STORM_PULSE_TICKS = 17;         // every 30 ticks

    private static final float STORM_DAMAGE = 4.0f;
    private static final int STORM_STUN_TICKS = 12; // 0.6s
    private static final int STORM_STUN_AMP = 0;

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        setSingleTimerTag(player, STORM_TICKS, STORM_DURATION_TICKS);
        setSingleTimerTag(player, STORM_STEP, STORM_PULSE_TICKS);

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                player.getSoundCategory(), 1.2f, 0.8f);

        // ult activation
        spawnMaelstromOpenBurst(w, player);
    }

    // massive burst when ult activates — bigger than secondary
    private static void spawnMaelstromOpenBurst(ServerWorld world, ServerPlayerEntity player) {
        Vec3d center = player.getPos();

        // four concentric rings at ground, waist, chest, head
        for (double yOffset : new double[]{ 0.1, 0.6, 1.2, 1.9 }) {
            int    points = 28;
            double speed  = 0.35;
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2.0 / points;
                DustParticleEffect col = switch (i % 3) {
                    case 0  -> BOLT_WHITE;
                    case 1  -> LIGHTNING_YELLOW;
                    default -> ARC_GOLD;
                };
                world.spawnParticles(col,
                        center.x + Math.cos(angle) * 0.4, center.y + yOffset, center.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.015, Math.sin(angle) * speed, 0.0);
            }
        }

        // pillar of sparks and yellow
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                center.x, center.y + 1.0, center.z,
                100, 1.0, 1.5, 1.0, 0.12);
        world.spawnParticles(LIGHTNING_YELLOW,
                center.x, center.y + 1.0, center.z,
                60, 0.8, 1.2, 0.8, 0.10);
        world.spawnParticles(BOLT_WHITE,
                center.x, center.y + 1.0, center.z,
                20, 0.4, 0.8, 0.4, 0.07);

        // cosmetic lightning storm on cast
        for (int i = 0; i < 5; i++) {
            Vec3d lPos = new Vec3d(
                    center.x + (RNG.nextDouble() - 0.5) * 8.0,
                    center.y,
                    center.z + (RNG.nextDouble() - 0.5) * 8.0);
            spawnCosmeticLightning(world, lPos);
        }

        CameraShake.shakeNearby(player, 12, 15, 0.38f);
    }

    private void tickMaelstrom(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos(); // follows player

        int left = tickSingleTimer(player, STORM_TICKS);
        if (left <= 0) {
            removeTagPrefix(player, STORM_TICKS);
            removeTagPrefix(player, STORM_STEP);
            return;
        }

        // Clouds
        if (left % 6 == 0) spawnStormClouds(world, center);

        // Targets inside radius
        Box box = new Box(center, center).expand(STORM_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != player
        );

        // spark + yellow flicker on targets while the storm is active
        if (!targets.isEmpty() && left % 3 == 0) {
            for (LivingEntity e : targets) {
                if (RNG.nextFloat() < 0.10f) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.25, 0.35, 0.25, 0.02);
                    world.spawnParticles(LIGHTNING_YELLOW,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            1, 0.15, 0.20, 0.15, 0.015);
                }
            }
        }

        // strike when timer done
        int stepLeft = tickSingleTimer(player, STORM_STEP);

        // me trying to stop spam
        if (stepLeft < 0) {
            setSingleTimerTag(player, STORM_STEP, STORM_PULSE_TICKS);
            return;
        }

        if (stepLeft > 0) return;

        // reset timer
        setSingleTimerTag(player, STORM_STEP, STORM_PULSE_TICKS);

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

    private void spawnStormClouds(ServerWorld world, Vec3d center) {
        double r = STORM_RADIUS;

        for (int i = 0; i < 80; i++) { // number of clouds
            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double rad = RNG.nextDouble() * r;

            double x = center.x + Math.cos(ang) * rad;
            double z = center.z + Math.sin(ang) * rad;
            double y = center.y + 6.0 + RNG.nextDouble() * 2.0;

            // cloud particles
            world.spawnParticles(ParticleTypes.CLOUD,
                    x, y, z, 2, 0.55, 0.25, 0.55, 0.01);
            world.spawnParticles(ParticleTypes.SMOKE,
                    x, y, z, 2, 0.55, 0.25, 0.55, 0.01);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y, z, 2, 0.55, 0.25, 0.55, 0.01);

            if (RNG.nextFloat() < 0.35f) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        x, y - 0.8, z, 1, 0.2, 0.2, 0.2, 0.0);
            }
            // yellow arc
            if (RNG.nextFloat() < 0.30f) {
                world.spawnParticles(LIGHTNING_YELLOW,
                        x + (RNG.nextDouble() - 0.5) * 1.0,
                        y - 0.4,
                        z + (RNG.nextDouble() - 0.5) * 1.0,
                        1, 0.15, 0.10, 0.15, 0.0);
            }
        }
    }

    private void strikeStormTarget(ServerWorld world, ServerPlayerEntity caster, LivingEntity target) {
        spawnCosmeticLightning(world, target.getPos());

        target.damage(ModDamageTypes.smite(caster.getWorld(), caster), STORM_DAMAGE);

        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, STORM_STUN_TICKS, STORM_STUN_AMP, true, true
        ));
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, STORM_STUN_TICKS, STORM_STUN_AMP, true, true
        ));

        // fx
        spawnHitRing(world, target, 3);

        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                target.getX(), target.getY() + 1.0, target.getZ(),
                22, 0.45, 0.7, 0.45, 0.08);
        world.spawnParticles(LIGHTNING_YELLOW,
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
        CameraShake.shakeNearby(caster,
                10,
                15,
                0.25f);
    }
    // ===========================
    // HELPERS
    // ===========================

    private static float computeBonusDamage(int chargeTicks) {
        int clamped = MathHelper.clamp(chargeTicks, 0, MAX_CHARGE_TICKS);
        float t = (float) clamped / (float) MAX_CHARGE_TICKS; // 0..1

        // Ease curve: slow start, strong end (quadratic)
        float eased = t * t;

        return MAX_BONUS_DAMAGE * eased;
    }

    private static boolean hasTagPrefix(ServerPlayerEntity p, String prefix) {
        for (String tag : p.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return; // remove ONE
            }
        }
    }

    private static void setSingleTimerTag(ServerPlayerEntity p, String prefix, int ticks) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + ticks);
    }

    // returns remaining ticks are decrement
    private static int tickSingleTimer(ServerPlayerEntity p, String prefix) {
        String foundTag = null;

        // find tag
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                foundTag = tag;
                break;
            }
        }

        if (foundTag == null) return -1;

        // remove  tag
        p.getCommandTags().remove(foundTag);

        int ticks;
        try {
            ticks = Integer.parseInt(foundTag.substring(prefix.length())) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }

        //
        if (ticks > 0) {
            p.getCommandTags().add(prefix + ticks);
        }
        return ticks;
    }

    // returns current ticks in tax prefix
    private static int getTimerTicks(ServerPlayerEntity p, String prefix) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                try {
                    return Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
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

    @Override public String getName() { return "Lightning"; }
    @Override public String getPrimaryName() { return "Thunderclap"; }
    @Override public String getSecondaryName() { return "Supercharge"; }
    @Override public String getUltimateName() { return "Maelstrom"; }

    @Override
    public long getSecondaryCooldownMs() {
        return 10_000; // 35 seconds
    }

    @Override
    public long getUltimateCooldownMs() {
        return 10_000; // 100 seconds
    }

    @Override
    public long getPrimaryCooldownMs() {
        return 3_500; // 6.5 seconds
    }

    @Override
    public String getOverviewDescription() {
        return "Lightning is intended to be a close range brawler that can do high amounts of damage to groups of enemies quickly, but very little at range. " +
                "It should also be said that lightning damage produced is edited (so it doesn't do the normal half a heart that normal lightning does)";
    }

    @Override
    public String getPassiveName() {
        return "Static Charge";
    }

    @Override
    public String getPassiveDescription() {
        return "Your fist charges up with bonus damage over time. Increased charge can cause lightning to strike the target or chain lightning and ignition to nearby enemies at higher charges.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Release a burst of lightning around you, damaging and igniting all nearby entities. Damage falls off with distance.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Empower yourself with lightning, giving yourself temporary regeneration, strength and speed.";
    }

    @Override
    public String getUltimateDescription() {
        return "Create a storm above you, striking random nearby entities with lightning, igniting them and dealing high damage. The cloud" +
                "particles indicate the range of the ultimate.";
    }
}