package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
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
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, Integer> ACTIVE_MISTS = new HashMap<>();
    private boolean applyingDarknessDamage = false;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        removeTagPrefix(player, "dk_"); // Clean up old string tags
        removeBlackoutNow(player.getServer(), player.getUuid());
        ACTIVE_MISTS.remove(player.getUuid());
        player.setNoGravity(false);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeTagPrefix(player, "dk_");

        removeBlackoutNow(player.getServer(), player.getUuid());
        ACTIVE_MISTS.remove(player.getUuid());

        player.setNoGravity(false);
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        handleMistForm(player);
        tickBlackoutsWorld(player.getServerWorld());
    }

    /* ============================================================
       DAMAGE HOOKS
       ============================================================ */

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        // Mist form grants total immunity
        if (ACTIVE_MISTS.containsKey(victim.getUuid())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0) return true;
        if (this.applyingDarknessDamage) return true; // Recursion guard

        float mult = 1.0f;
        boolean didBackstab = false;
        boolean didExposed = false;
        DamageSource finalSource = source;
        ServerWorld w = attacker.getServerWorld();

        // Backstab logic
        if (isBehindTarget(attacker, target)) {
            mult *= BACKSTAB_BONUS_MULT;
            didBackstab = true;
            finalSource = ModDamageTypes.darknessBackstab(w, attacker);
        }

        // Exposed effect logic
        if (target.hasStatusEffect(ModEffects.EXPOSED)) {
            mult *= EXPOSED_DAMAGE_MULT;
            didExposed = true;
            finalSource = ModDamageTypes.darkUlt(w, attacker);
        }

        // No change - let normal damage happen
        if (Math.abs(mult - 1.0f) < 1.0e-4f && finalSource == source) return true;

        float newAmount = amount * mult;

        // Apply modified damage
        this.applyingDarknessDamage = true;
        target.damage(finalSource, newAmount);
        this.applyingDarknessDamage = false;

        // Visuals
        if (didBackstab) triggerBackstabFx(w, target, attacker);
        if (didExposed) triggerExposedFx(w, target, attacker);
        if (didBackstab && didExposed) triggerComboFx(w, target, attacker);

        return false; // Cancel original vanilla hit
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final float BACKSTAB_BONUS_MULT = 1.30f; // +30%

    private static boolean isBehindTarget(LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof ServerPlayerEntity player) {
            if (!PassiveManager.isEnabled(player)) return false;
        }

        Vec3d victimForward = victim.getRotationVec(1.0f);
        Vec3d toAttacker = attacker.getPos().subtract(victim.getPos());

        victimForward = new Vec3d(victimForward.x, 0.0, victimForward.z);
        toAttacker = new Vec3d(toAttacker.x, 0.0, toAttacker.z);

        if (victimForward.lengthSquared() < 1.0e-4 || toAttacker.lengthSquared() < 1.0e-4) {
            return false;
        }

        victimForward = victimForward.normalize();
        toAttacker = toAttacker.normalize();

        double dot = victimForward.dotProduct(toAttacker);
        return dot < -0.35;
    }

    // Helper fx methods extracted for cleanliness
    private void triggerBackstabFx(ServerWorld w, LivingEntity victim, LivingEntity attacker) {
        w.playSound(null, victim.getBlockPos(), ModSounds.BACKSTAB, attacker.getSoundCategory(), 0.7f, 1.0f);
        w.spawnParticles(ParticleTypes.SMOKE, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 6, 0.2, 0.3, 0.2, 0.01);

        Vec3d dir = attacker.getRotationVec(1.0f).normalize();
        w.spawnParticles(ParticleTypes.SMOKE, victim.getX() + dir.x * 0.5, victim.getBodyY(0.5), victim.getZ() + dir.z * 0.5, 6, 0.1, 0.1, 0.1, 0.01);
    }

    private void triggerExposedFx(ServerWorld w, LivingEntity victim, LivingEntity attacker) {
        w.playSound(null, victim.getBlockPos(), ModSounds.BIGSTAB, attacker.getSoundCategory(), 0.5f, 1.2f);
        w.spawnParticles(ParticleTypes.SMOKE, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 20, 0.4, 0.5, 0.4, 0.04);
        w.spawnParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        w.spawnParticles(BLACK_DUST, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 12, 0.25, 0.3, 0.25, 0.0);

        Vec3d dir = attacker.getRotationVec(1.0f).normalize();
        for (int i = 0; i < 8; i++) {
            double angle = w.random.nextDouble() * Math.PI * 2;
            double radius = 0.6;
            double px = victim.getX() + Math.cos(angle) * radius;
            double pz = victim.getZ() + Math.sin(angle) * radius;
            w.spawnParticles(BLACK_DUST, px, victim.getBodyY(0.5), pz, 0, dir.x, 0.05, dir.z, 1.0);
        }
    }

    private void triggerComboFx(ServerWorld w, LivingEntity victim, LivingEntity attacker) {
        w.playSound(null, victim.getBlockPos(), ModSounds.BIGSTAB, attacker.getSoundCategory(), 0.9f, 0.8f);
        w.spawnParticles(ParticleTypes.CRIT, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 15, 0.3, 0.3, 0.3, 0.1);

        if (victim instanceof ServerPlayerEntity targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 3, 10, 0.06f);
        }
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
                ModSounds.DARKNESSTELEPORT,
                player.getSoundCategory(),
                0.6f, 1.4f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int MIST_DURATION = 80;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        RenderPackets.hidePlayerFromOthers(player, MIST_DURATION);

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, MIST_DURATION, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, MIST_DURATION, 0, false, false, true));

        w.playSound(null, player.getBlockPos(), ModSounds.MISTENTER, player.getSoundCategory(), 1.0f, 1.0f);
        w.spawnParticles(DARK_DUST, player.getX(), player.getBodyY(0.5), player.getZ(), 35, 0.8, 1.0, 0.8, 0.02);

        ACTIVE_MISTS.put(player.getUuid(), MIST_DURATION);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void handleMistForm(ServerPlayerEntity player) {
        Integer ticks = ACTIVE_MISTS.get(player.getUuid());
        if (ticks == null) return;

        ticks--;

        if (ticks <= 0) {
            ACTIVE_MISTS.remove(player.getUuid());
            player.setNoGravity(false);
            return;
        }

        ACTIVE_MISTS.put(player.getUuid(), ticks);

        spawnMistTrail(player);

        if (ticks % 18 == 0) {
            player.getServerWorld().playSound(
                    null,
                    player.getBlockPos(),
                    com.yourname.loopypowers.sound.ModSounds.MISTLOOP,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0f, 2.0f
            );
        }

        player.removeStatusEffect(StatusEffects.SLOWNESS);

        Vec3d look = player.getRotationVec(1.0f);
        double speed = 0.6;

        Vec3d newVel = look.multiply(speed);
        player.setVelocity(newVel);
        player.velocityModified = true;

        player.setOnGround(false);
        player.fallDistance = 0;
        player.setNoGravity(true);

        if (!player.getMainHandStack().isEmpty()) {
            player.getItemCooldownManager().set(player.getMainHandStack().getItem(), 5);
        }
        player.stopUsingItem();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20, 5, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 20, 0, true, false));
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
    // multiplier
    private static final float EXPOSED_DAMAGE_MULT = 1.60f; // damage boost
    // EGG
    private static final float FUNNY_SOUND_CHANCE = 0.0005f; // chance per tick

    private static final DustParticleEffect BLACK_DUST =
            new DustParticleEffect(new Vec3d(0.01, 0.01, 0.01).toVector3f(), 1.8f);

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

            if ((now % 25L) == 0L) {
                playDarknessLoop(w, st);
            }

            // --- EGG ---
            if (w.random.nextFloat() < FUNNY_SOUND_CHANCE) {
                tryPlayFunnySound(w, st);
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
            e.addStatusEffect(new StatusEffectInstance(ModEffects.EXPOSED, 45, 0, true, false)); // visual one
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

    private static void playDarknessLoop(ServerWorld world, BlackoutState st) {
        double radius = BLACKOUT_RADIUS;
        double radiusSq = radius * radius;

        for (ServerPlayerEntity p : world.getPlayers()) {

            double dx = p.getX() - st.center.x;
            double dz = p.getZ() - st.center.z;

            if ((dx * dx + dz * dz) > radiusSq) continue;

            // play locally for each player inside
            world.playSound(
                    null,
                    p.getBlockPos(),
                    ModSounds.DARKNESSLOOP,
                    p.getSoundCategory(),
                    0.6f,
                    1.0f
            );
        }
    }

    // EGG
    private static void tryPlayFunnySound(ServerWorld w, BlackoutState st) {
        List<ServerPlayerEntity> playersInside = new ArrayList<>();
        double radiusSq = BLACKOUT_RADIUS * BLACKOUT_RADIUS;

        for (ServerPlayerEntity p : w.getPlayers()) {
            // Find players within the radius who are not the caster
            if (p.squaredDistanceTo(st.center) <= radiusSq) {
                playersInside.add(p);
            }
        }

        if (!playersInside.isEmpty()) {
            // Pick one random victim
            ServerPlayerEntity victim = playersInside.get(w.random.nextInt(playersInside.size()));

            // play sound at players location
            w.playSound(
                    null,
                    victim.getBlockPos(),
                    ModSounds.FUNNYFNAF,
                    SoundCategory.PLAYERS,
                    1.0f,
                    1.0f
            );
        }
    }


    /* ============================================================
       I FORGOT WHAT I KEPT CAPTIONING THESE
       ============================================================ */

    @Override public String getName() { return "Darkness"; }

    @Override public String getPassiveName() { return "Blindspot"; }
    @Override public String getPrimaryName() { return "Shadow Step"; }
    @Override public String getSecondaryName() { return "Umbral Veil"; }
    @Override public String getUltimateName() { return "Dark Domain"; }

    @Override public long getPrimaryCooldownMs() { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 25_000; }
    @Override public long getUltimateCooldownMs() { return 320_000; }

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
}