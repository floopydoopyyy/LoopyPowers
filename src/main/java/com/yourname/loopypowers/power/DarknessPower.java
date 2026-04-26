package com.yourname.loopypowers.power;

import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.yourname.loopypowers.entity.ShadowStepEntity;

import java.util.*;

import static com.yourname.loopypowers.entity.ModEntities.SHADOW_STEP;

public class DarknessPower implements Power {

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        removeTagPrefix(player, ULT_ACTIVE);
        removeBlackoutNow(player.getServer(), player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeTagPrefix(player, ULT_ACTIVE);
        removeBlackoutNow(player.getServer(), player.getUuid());
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        handleMistForm(player);   // secondary
        tickBlackoutsWorld(player.getServerWorld()); // ult
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // not needed for the current passive
        // passive bonus is handled in tryAdjustDarknessDamage(...)
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final float BACKSTAB_BONUS_MULT = 1.30f; // +30%

    /**
     * Returns true if attacker is behind victim.
     * This checks whether the attacker is generally in the victim's rear arc.
     */
    private static boolean isBehindTarget(LivingEntity attacker, LivingEntity victim) {
        Vec3d victimForward = victim.getRotationVec(1.0f);
        Vec3d toAttacker = attacker.getPos().subtract(victim.getPos());

        // flatten both vectors so vertical angle doesn't matter
        victimForward = new Vec3d(victimForward.x, 0.0, victimForward.z);
        toAttacker = new Vec3d(toAttacker.x, 0.0, toAttacker.z);

        if (victimForward.lengthSquared() < 1.0e-4 || toAttacker.lengthSquared() < 1.0e-4) {
            return false;
        }

        victimForward = victimForward.normalize();
        toAttacker = toAttacker.normalize();

        // If dot is strongly negative, attacker is behind victim.
        // -1.0 = directly behind, 0 = side, +1.0 = directly in front.
        double dot = victimForward.dotProduct(toAttacker);

        return dot < -0.35; // tweak if you want stricter / looser rear check
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        ShadowStepEntity proj = new ShadowStepEntity(SHADOW_STEP, w);
        proj.setOwner(player);

        Vec3d look = player.getRotationVec(1.0f);

        proj.setPosition(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        proj.setVelocity(look.multiply(1.2)); // speed

        w.spawnEntity(proj);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(),
                0.8f, 0.5f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final String MIST_TAG = "dk_mist_"; // dk_mist_<ticks>
    private static final int duration = 80;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // make invisible
        RenderPackets.hidePlayerFromOthers(player, duration);

        // effects
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                duration,
                1, // Speed II
                false, false, true
        ));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.JUMP_BOOST,
                duration,
                0,
                false, false, true
        ));

        // initial particles
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                player.getSoundCategory(),
                0.9f, 0.4f);

        w.spawnParticles(
                DARK_DUST,
                player.getX(),
                player.getBodyY(0.5),
                player.getZ(),
                35,
                0.8, 1.0, 0.8,
                0.02
        );

        // constant particles
        player.getCommandTags().add("dk_mist_" + duration);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void handleMistForm(ServerPlayerEntity player) {
        var it = player.getCommandTags().iterator();
        String newTag = null;

        while (it.hasNext()) {
            String tag = it.next();

            if (tag.startsWith(MIST_TAG)) {
                int ticks = Integer.parseInt(tag.substring(MIST_TAG.length())) - 1;

                it.remove(); // safe removal

                if (ticks <= 0) {
                    player.setNoGravity(false);
                    return;
                }

                newTag = MIST_TAG + ticks;

                // particles
                spawnMistTrail(player);

                // remove slow effects
                player.removeStatusEffect(StatusEffects.SLOWNESS);

                // MOVEMENT
                Vec3d look = player.getRotationVec(1.0f);
                double speed = 0.6;

                Vec3d newVel = look.multiply(speed);
                player.setVelocity(newVel);
                player.velocityModified = true;

                // ignore ground friction
                player.setOnGround(false);
                player.fallDistance = 0;

                // let them fly
                player.setNoGravity(true);

                // stop actions
                if (!player.getMainHandStack().isEmpty()) { // set cooldowns on held item
                    player.getItemCooldownManager().set(player.getMainHandStack().getItem(), 5);
                }
                player.stopUsingItem(); // force usables to not be used
                // stop swings
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 5, true, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20, 0, true, false));

                break; // only handle on tag at a time
            }
        }

        // apply new tag AFTER iteration
        if (newTag != null) {
            player.getCommandTags().add(newTag);
        }
    }

    private static final DustParticleEffect DARK_DUST =
            new DustParticleEffect(new Vec3d(0.05, 0.05, 0.05).toVector3f(), 1.4f);

    private static void spawnMistTrail(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        w.spawnParticles(
                DARK_DUST,
                player.getX(),
                player.getBodyY(0.5),
                player.getZ(),
                12,                 // count
                0.6, 0.8, 0.6,      // spread
                0.01                // speed
        );
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    private static final String ULT_ACTIVE = "dk_ult_"; // dk_ult_<ticks>

    // SIZE
    private static final int BLACKOUT_RADIUS = 16;     // size
    private static final double BLACKOUT_HEIGHT = 13.0; // height

    // TIMING
    private static final int BLACKOUT_DURATION_TICKS = 20 * 10; // ult time
    private static final int BLACKOUT_APPLY_EVERY_TICKS = 5;
    private static final int BLACKOUT_FX_EVERY_TICKS = 3; //

    // VISUALS
    private static final int BLACKOUT_RING_POINTS = 48;   // smooth circle
    private static final int BLACKOUT_VERTICAL_LAYERS = 20;
    // interior particles
    private static final int BLACKOUT_INNER_PARTICLES = 100; // density
    private static final double BLACKOUT_INNER_SPREAD = BLACKOUT_RADIUS * 0.9;

    private static final DustParticleEffect BLACK_DUST =
            new DustParticleEffect(new Vec3d(0.01, 0.01, 0.01).toVector3f(), 1.8f);

    // DAMAGE
    private static final float BLACKOUT_DAMAGE_MULT = 1.50f;

    private static final Map<UUID, BlackoutState> ACTIVE_BLACKOUTS = new HashMap<>();
    private static final Map<RegistryKey<World>, Long> BLACKOUT_LAST_TICK = new HashMap<>();

    private static final class BlackoutState {
        final UUID owner;
        final RegistryKey<World> worldKey;
        final Vec3d center;
        int ticksLeft;

        BlackoutState(UUID owner, RegistryKey<World> worldKey, Vec3d center, int ticksLeft) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.center = center;
            this.ticksLeft = ticksLeft;
        }
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        removeBlackoutNow(player.getServer(), player.getUuid());

        setSingleTimerTag(player, ULT_ACTIVE, BLACKOUT_DURATION_TICKS);

        BlackoutState st = new BlackoutState(
                player.getUuid(),
                w.getRegistryKey(),
                player.getPos(),
                BLACKOUT_DURATION_TICKS
        );

        ACTIVE_BLACKOUTS.put(player.getUuid(), st);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_END_PORTAL_SPAWN,
                SoundCategory.PLAYERS,
                0.9f, 0.6f);

        w.spawnParticles(ParticleTypes.LARGE_SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                40, 1.0, 0.7, 1.0, 0.03);

        w.spawnParticles(ParticleTypes.SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                55, 2.0, 1.0, 2.0, 0.01);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    public static void tickBlackoutsWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();

        Long last = BLACKOUT_LAST_TICK.get(key);
        if (last != null && last == now) return;
        BLACKOUT_LAST_TICK.put(key, now);

        if (ACTIVE_BLACKOUTS.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();

        for (var entry : new ArrayList<>(ACTIVE_BLACKOUTS.entrySet())) {
            UUID owner = entry.getKey();
            BlackoutState st = entry.getValue();

            if (!st.worldKey.equals(key)) continue;

            st.ticksLeft--;
            if (st.ticksLeft <= 0) {
                toRemove.add(owner);
                continue;
            }

            if ((now % BLACKOUT_APPLY_EVERY_TICKS) == 0L) {
                applyBlackoutEffects(w, st);
            }

            if ((now % BLACKOUT_FX_EVERY_TICKS) == 0L) {
                spawnBlackoutFx(w, st);
            }
        }

        for (UUID owner : toRemove) {
            removeBlackoutNow(w.getServer(), owner);
        }
    }

    private static void applyBlackoutEffects(ServerWorld w, BlackoutState st) {
        Box box = new Box(
                st.center.x - BLACKOUT_RADIUS, st.center.y - BLACKOUT_HEIGHT, st.center.z - BLACKOUT_RADIUS,
                st.center.x + BLACKOUT_RADIUS, st.center.y + BLACKOUT_HEIGHT, st.center.z + BLACKOUT_RADIUS
        );

        List<LivingEntity> entities = w.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive);

        double radiusSq = BLACKOUT_RADIUS * BLACKOUT_RADIUS;

        for (LivingEntity e : entities) {
            // skip caster
            if (e.getUuid().equals(st.owner)) continue;

            double dx = e.getX() - st.center.x;
            double dz = e.getZ() - st.center.z;
            double distSq = dx * dx + dz * dz;

            if (distSq > radiusSq) continue;

            // everyone inside the bubble gets effects
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, true, false));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0, true, false));
        }
    }

    private static void spawnBlackoutFx(ServerWorld w, BlackoutState st) {

        // ===== CENTER =====
        w.spawnParticles(ParticleTypes.SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                12,
                2.0, 1.5, 2.0,
                0.01);

        w.spawnParticles(ParticleTypes.LARGE_SMOKE,
                st.center.x, st.center.y + 1.0, st.center.z,
                6,
                1.5, 1.0, 1.5,
                0.01);

        // ===== INTERIOR  =====
        for (int i = 0; i < BLACKOUT_INNER_PARTICLES; i++) {

            double angle = w.random.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(w.random.nextDouble()) * BLACKOUT_INNER_SPREAD;

            double x = st.center.x + Math.cos(angle) * radius;
            double z = st.center.z + Math.sin(angle) * radius;

            double y = st.center.y + (w.random.nextDouble() * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT);

            // actual particles
            w.spawnParticles(ParticleTypes.SMOKE,
                    x, y, z,
                    1,
                    0.05, 0.05, 0.05,
                    0.005);

            if (w.random.nextFloat() < 0.35f) {
                w.spawnParticles(BLACK_DUST,
                        x, y, z,
                        1,
                        0.02, 0.02, 0.02,
                        0.0);
            }
        }

        // ===== OUTER =====
        for (int y = 0; y < BLACKOUT_VERTICAL_LAYERS; y++) {

            double heightOffset = ((double) y / (BLACKOUT_VERTICAL_LAYERS - 1)) * BLACKOUT_HEIGHT * 2 - BLACKOUT_HEIGHT;

            double layerRadius = BLACKOUT_RADIUS * Math.sqrt(1 - Math.pow(heightOffset / BLACKOUT_HEIGHT, 2));

            for (int i = 0; i < BLACKOUT_RING_POINTS; i++) {

                double angle = (Math.PI * 2.0 * i) / BLACKOUT_RING_POINTS;

                double x = st.center.x + Math.cos(angle) * layerRadius;
                double z = st.center.z + Math.sin(angle) * layerRadius;
                double yPos = st.center.y + heightOffset;

                w.spawnParticles(ParticleTypes.SMOKE,
                        x, yPos, z,
                        1,
                        0.1, 0.1, 0.1,
                        0.0);

                // occasional thicker particles
                if (i % 4 == 0) {
                    w.spawnParticles(ParticleTypes.LARGE_SMOKE,
                            x, yPos, z,
                            1,
                            0.05, 0.05, 0.05,
                            0.0);
                }
            }
        }
    }

    private static void removeBlackoutNow(net.minecraft.server.MinecraftServer server, UUID owner) {
        if (server == null) return;

        BlackoutState st = ACTIVE_BLACKOUTS.remove(owner);
        if (st == null) return;

        ServerWorld w = server.getWorld(st.worldKey);
        if (w == null) return;

        w.playSound(null, BlockPos.ofFloored(st.center),
                SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.PLAYERS,
                0.8f, 0.65f);

        w.spawnParticles(ParticleTypes.CLOUD,
                st.center.x, st.center.y + 1.0, st.center.z,
                25, 1.0, 0.6, 1.0, 0.03);
    }

    /* ============================================================
       DAMAGE HOOK
       ============================================================ */

    private static final String DARKNESS_DMG_GUARD = "dk_dmg_guard";

    /**
     called from global hook and picks if the boost is the backstab, ultimate or both.
     */
    public static boolean tryAdjustDarknessDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0) return false;

        Entity atkEnt = source.getAttacker();
        if (!(atkEnt instanceof LivingEntity attacker)) return false;

        if (!(victim.getWorld() instanceof ServerWorld w)) return false;

        // recursion guard
        if (victim.getCommandTags().contains(DARKNESS_DMG_GUARD)) return false;
        if (attacker.getCommandTags().contains(DARKNESS_DMG_GUARD)) return false;

        float mult = 1.0f;
        boolean didBackstab = false;
        boolean didUlt = false;

        // backstab
        if (isBehindTarget(attacker, victim)) {
            mult *= BACKSTAB_BONUS_MULT;
            didBackstab = true;
        }

        // ULTIMATE
        if (attacker instanceof ServerPlayerEntity sp) {
            BlackoutState st = ACTIVE_BLACKOUTS.get(sp.getUuid());
            if (st != null && st.worldKey.equals(w.getRegistryKey()) && isInsideBlackout(st, victim)) {
                mult *= BLACKOUT_DAMAGE_MULT;
                didUlt = true;
            }
        }

        // no change - let normal damage happen
        if (Math.abs(mult - 1.0f) < 1.0e-4f) return false;

        float newAmount = amount * mult;

        // APPLY MODIFIED DAMAGE
        victim.getCommandTags().add(DARKNESS_DMG_GUARD);
        attacker.getCommandTags().add(DARKNESS_DMG_GUARD);
        try {
            victim.damage(source, newAmount);

            // BACKSTAB FX
            if (didBackstab) {

                w.playSound(
                        null,
                        victim.getBlockPos(),
                        ModSounds.BACKSTAB,
                        attacker.getSoundCategory(),
                        0.9f,
                        1.0f
                );

                w.spawnParticles(
                        ParticleTypes.SMOKE,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        12,
                        0.3, 0.4, 0.3,
                        0.02
                );

                w.spawnParticles(
                        ParticleTypes.LARGE_SMOKE,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        6,
                        0.2, 0.3, 0.2,
                        0.01
                );

                // directional particles
                Vec3d dir = attacker.getRotationVec(1.0f).normalize();

                w.spawnParticles(
                        ParticleTypes.SMOKE,
                        victim.getX() + dir.x * 0.5,
                        victim.getBodyY(0.5),
                        victim.getZ() + dir.z * 0.5,
                        6,
                        0.1, 0.1, 0.1,
                        0.01
                );
            }

            // ULTIMATE
            if (didUlt) {

                w.playSound(
                        null,
                        victim.getBlockPos(),
                        ModSounds.BIGSTAB,
                        attacker.getSoundCategory(),
                        0.7f,
                        1.2f
                );

                // dark burst
                w.spawnParticles(
                        ParticleTypes.SMOKE,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        20,
                        0.4, 0.5, 0.4,
                        0.04
                );

                w.spawnParticles(
                        ParticleTypes.LARGE_SMOKE,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        10,
                        0.3, 0.4, 0.3,
                        0.02
                );

                // black dust
                w.spawnParticles(
                        BLACK_DUST,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        12,
                        0.25, 0.3, 0.25,
                        0.0
                );

                // inward "collapse" effect
                Vec3d dir = attacker.getRotationVec(1.0f).normalize();

                for (int i = 0; i < 8; i++) {
                    double angle = w.random.nextDouble() * Math.PI * 2;
                    double radius = 0.6;

                    double px = victim.getX() + Math.cos(angle) * radius;
                    double pz = victim.getZ() + Math.sin(angle) * radius;
                    double py = victim.getBodyY(0.5);

                    w.spawnParticles(
                            BLACK_DUST,
                            px, py, pz,
                            0,
                            dir.x, 0.05, dir.z,
                            1.0
                    );
                }
            }

            // COMBO
            if (didBackstab && didUlt) {

                w.playSound(
                        null,
                        victim.getBlockPos(),
                        ModSounds.BIGSTAB,
                        attacker.getSoundCategory(),
                        0.9f,
                        0.8f
                );

                w.spawnParticles(
                        ParticleTypes.CRIT,
                        victim.getX(),
                        victim.getBodyY(0.5),
                        victim.getZ(),
                        15,
                        0.3, 0.3, 0.3,
                        0.1
                );
            }

        } finally {
            victim.getCommandTags().remove(DARKNESS_DMG_GUARD);
            attacker.getCommandTags().remove(DARKNESS_DMG_GUARD);
        }

        return true; // we handled the damage so cancel it
    }

    private static boolean isInsideBlackout(BlackoutState st, LivingEntity e) {
        double dx = e.getX() - st.center.x;
        double dz = e.getZ() - st.center.z;
        return (dx * dx + dz * dz) <= (BLACKOUT_RADIUS * BLACKOUT_RADIUS);
    }

    /* ============================================================
       I FORGOT WHAT I KEPT CAPTIONING THESE
       ============================================================ */

    @Override public String getName() { return "Darkness"; }

    @Override public String getPassiveName() { return "Backstabbing"; }
    @Override public String getPrimaryName() { return "Shadow Step"; }
    @Override public String getSecondaryName() { return "Mist Form"; }
    @Override public String getUltimateName() { return "Blackout"; }

    @Override public long getPrimaryCooldownMs() { return 8_000; }
    @Override public long getSecondaryCooldownMs() { return 12_000; }
    @Override public long getUltimateCooldownMs() { return 18_000; }

    @Override
    public String getOverviewDescription() {
        return "Darkness is supposed to be a high damage, melee stealth power, with tools to get in and get out along with high amounts of bonus" +
                " damage on hit, especially with higher tier weapons.";
    }

    @Override
    public String getPassiveDescription() {
        return "Deal bonus damage when hitting an entity from behind, this bonus damage uses a multiplier, so it improves with the weapon being used.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Shoot a slow projectile that on hitting an entity briefly blinds them, deals a small chunk of damage and teleports you behind them (should be in a safe place).";
    }

    @Override
    public String getSecondaryDescription() {
        return "Temporarily transform into a cloud of mist, allowing you to fly freely. You will not be able to" +
                " deal or receive damage in this form and will be immune to all slow effects (apart from blocks like cobwebs)." +
                " Once finished, you will be invisible for a brief time.";
    }

    @Override
    public String getUltimateDescription() {
        return "Create a sphere of darkness. All entities in it are blinded and glowing. You do bonus damage to entities in this bubble and it stacks" +
                " multiplicatively with your backstab passive (so your backstab does even more increased damage, along with the bonus damage).";
    }

    /* ============================================================
       TAG HELPERS
       ============================================================ */

    private static void removeTagPrefix(Entity e, String prefix) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return;
            }
        }
    }

    private static void setSingleTimerTag(Entity e, String prefix, int ticks) {
        removeTagPrefix(e, prefix);
        e.getCommandTags().add(prefix + ticks);
    }
}