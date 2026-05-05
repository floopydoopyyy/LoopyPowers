package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import com.yourname.loopypowers.sound.ModSounds;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// HANDLES ALL ATTRIBUTES OF SPEED POWER

public class SpeedPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SpeedState> ACTIVE_STATES = new HashMap<>();

    private static class SpeedState {
        int burstCdTicks = 0;
        int rushTicks = 0;
        int rushLoopCd = 0;
        int overdriveTicks = 0;
        int overdriveSlowTicks = 0;
        int blockDmgCd = 0;
    }

    private static SpeedState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new SpeedState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // PASSIVE
    private static final int    PASSIVE_SPEED_AMPLIFIER     = 1;       // swiftness 2
    private static final float  PASSIVE_LOW_HEALTH_THRESHOLD = 4.0f;   // 3 hearts
    private static final int    PASSIVE_BURST_AMPLIFIER     = 4;       // swiftness 5 burst
    private static final int    PASSIVE_BURST_DURATION      = 80;      // 4 seconds
    private static final int    PASSIVE_BURST_COOLDOWN_TICKS = 500;    // 10 second cooldown

    // PRIMARY
    private static final double DASH_HORIZONTAL_STRENGTH    = 2.6;
    private static final double DASH_VERTICAL_STRENGTH      = 1.3;
    private static final double DASH_MAX_DOWN               = -0.8;
    private static final double DASH_MAX_UP                 = 0.8;
    private static final double DASH_GROUND_MIN_Y           = 0.18;
    private static final int    DASH_PARTICLE_COUNT         = 22;

    // SECONDARY
    private static final int    RUSH_DURATION_TICKS         = 100;     // 5 seconds
    private static final float  RUSH_DAMAGE                 = 8.5f;
    private static final double RUSH_KNOCKBACK_HORIZONTAL   = 1.4;
    private static final double RUSH_KNOCKBACK_VERTICAL     = 0.55;
    private static final double RUSH_KNOCKBACK_SCAN_RADIUS  = 4.5;
    private static final double RUSH_VELOCITY_BONUS         = 1.5;     // flat horizontal velocity on cast
    private static final int    RUSH_HASTE_AMPLIFIER        = 0;

    // ULT
    private static final int    OVERDRIVE_DURATION_TICKS    = 200;     // 10 seconds
    private static final double OVERDRIVE_MIN_SPEED         = 1.4;
    private static final double OVERDRIVE_FORWARD_PUSH      = 0.38;
    private static final double OVERDRIVE_ENTITY_DAMAGE     = 16.0f;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK  = 1.8;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK_Y = 0.5;
    private static final double OVERDRIVE_COLLISION_SLOW_X  = 0.15;
    private static final double OVERDRIVE_COLLISION_SLOW_Y  = 0.10;
    private static final int    OVERDRIVE_SLOW_TICKS        = 12;
    private static final float  OVERDRIVE_BLOCK_HARDNESS_MAX = 3.0f;
    private static final float  OVERDRIVE_SELF_DAMAGE       = 1.5f;
    private static final int    OVERDRIVE_JUMP_AMPLIFIER    = 0;
    private static final int    OVERDRIVE_HASTE_AMPLIFIER   = 2;

    // EASTER EGG
    private static final int    A_TRAIN_CHANCE   = 250;

    // sound
    private static final int RUSH_LOOP_INTERVAL_TICKS      = 25;

    // particles
    private static final DustParticleEffect CYAN_BRIGHT  =
            new DustParticleEffect(new Vector3f(0.15f, 0.85f, 1.00f), 1.3f);
    private static final DustParticleEffect CYAN_PALE    =
            new DustParticleEffect(new Vector3f(0.70f, 0.95f, 1.00f), 1.0f);
    private static final DustParticleEffect WHITE_STREAK =
            new DustParticleEffect(new Vector3f(0.95f, 0.98f, 1.00f), 0.8f);

    /* ============================================================
       PASSIVE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_")); // Cleanup legacy tags
        ACTIVE_STATES.put(player.getUuid(), new SpeedState());

        // passive
        player.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false)
        );
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("speed_") || tag.startsWith("overdrive_"));
        ACTIVE_STATES.remove(player.getUuid());

        // Strip lingering buffs
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.HASTE);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        SpeedState state = getState(player);

        // Tick internal cooldowns
        if (state.burstCdTicks > 0) state.burstCdTicks--;
        if (state.blockDmgCd > 0) state.blockDmgCd--;
        if (state.overdriveSlowTicks > 0) state.overdriveSlowTicks--;
        if (state.rushLoopCd > 0) state.rushLoopCd--;

        tickPassiveSpeed(player);
        tickLowHealthBurst(player, state);

        if (state.rushTicks > 0) {
            state.rushTicks--;
            tickRushEffects(player);

            // Loop sound
            if (state.rushLoopCd <= 0) {
                player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.RUSHLOOP, player.getSoundCategory(), 0.55f, 1.0f);
                state.rushLoopCd = RUSH_LOOP_INTERVAL_TICKS;
            }
        }

        if (state.overdriveTicks > 0) {
            state.overdriveTicks--;
            tickOverdrive(player, state);
        }
    }

    /* ============================================================
       PRIMARY — DASH
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) { // Dash ability method
        Vec3d look = player.getRotationVec(1.0F); // gets player facing

        double y = MathHelper.clamp(
                look.y * DASH_VERTICAL_STRENGTH,
                DASH_MAX_DOWN,
                DASH_MAX_UP
        );
        double x = look.x * DASH_HORIZONTAL_STRENGTH;
        double z = look.z * DASH_HORIZONTAL_STRENGTH;

        // should allow player to climb things like slabs without issues
        if (player.isOnGround()) {
            y = Math.max(y, DASH_GROUND_MIN_Y);
        }

        player.addVelocity(x, y, z);
        player.velocityModified = true; // tells server velocity modified

        spawnDashParticles(player, look);

        player.getServerWorld().playSound(
                null,
                player.getBlockPos(),
                ModSounds.DASH,
                player.getSoundCategory(),
                1.0f, 1.0f
        );
        player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
    }

    /* ============================================================
       SECONDARY — RUSH
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        state.rushTicks = RUSH_DURATION_TICKS;
        state.rushLoopCd = 0; // Play immediately

        // Apply flat velocity burst in the look direction instead of a speed potion effect
        Vec3d look = player.getRotationVec(1.0F);
        player.setVelocity(
                look.x * RUSH_VELOCITY_BONUS,
                player.getVelocity().y,
                look.z * RUSH_VELOCITY_BONUS
        );
        player.velocityModified = true;

        player.addStatusEffect(
                new StatusEffectInstance(StatusEffects.HASTE, RUSH_DURATION_TICKS, RUSH_HASTE_AMPLIFIER, true, false)
        );

        // Pushback + damage
        player.getServerWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(RUSH_KNOCKBACK_SCAN_RADIUS),
                        e -> e instanceof LivingEntity)
                .forEach(e -> {
                    LivingEntity victim = (LivingEntity) e;

                    DamageSource rushSrc = ModDamageTypes.rush(player.getWorld(), player);

                    // IF check prevents multi-hits and ignores i-framed entities
                    if (victim.damage(rushSrc, RUSH_DAMAGE)) {
                        double dx = victim.getX() - player.getX();
                        double dz = victim.getZ() - player.getZ();
                        double dist = Math.sqrt(dx * dx + dz * dz);

                        if (dist > 0.001) {
                            victim.addVelocity(
                                    (dx / dist) * RUSH_KNOCKBACK_HORIZONTAL,
                                    RUSH_KNOCKBACK_VERTICAL,
                                    (dz / dist) * RUSH_KNOCKBACK_HORIZONTAL
                            );
                            victim.velocityModified = true;
                        }

                        CameraShake.shakeNearby(player, 8, 5, 0.45f);
                    }
                });

        spawnRushCastParticles(player);

        // sounds
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 1.0f, 1.1f
        );
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                ModSounds.RUSHSTART,
                player.getSoundCategory(), 1.0f, 1.0f
        );
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        SpeedState state = getState(player);
        state.overdriveTicks = OVERDRIVE_DURATION_TICKS;

        // Launch the player forward with direct velocity on activation
        Vec3d look = player.getRotationVec(1.0F);
        player.setVelocity(
                look.x * (OVERDRIVE_MIN_SPEED * 1.8),
                player.getVelocity().y + 0.2,
                look.z * (OVERDRIVE_MIN_SPEED * 1.8)
        );
        player.velocityModified = true;

        spawnOverdriveCastParticles(player);

        player.playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
        player.playSound(ModSounds.OVERDRIVESTART, 0.6f, 1.8f);
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 1.0f, 0.6f
        );
    }

    /* ============================================================
       TICK HELPERS
       ============================================================ */

    /** Keeps passive swiftness topped up every tick. */
    private void tickPassiveSpeed(ServerPlayerEntity player) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(player)) return;

        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed == null || speed.getDuration() < 5) {
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SPEED, 200, PASSIVE_SPEED_AMPLIFIER, true, false)
            );
        }
    }

    /** Triggers a short burst of extreme speed when the player's health drops below the threshold. */
    private void tickLowHealthBurst(ServerPlayerEntity player, SpeedState state) {
        // do nothing if burst is still on cooldown
        if (state.burstCdTicks > 0) return;

        if (player.getHealth() <= PASSIVE_LOW_HEALTH_THRESHOLD) {
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SPEED, PASSIVE_BURST_DURATION,
                            PASSIVE_BURST_AMPLIFIER, true, false)
            );

            // start cooldown
            state.burstCdTicks = PASSIVE_BURST_COOLDOWN_TICKS;

            // Small cyan flash at the feet — adrenaline kicking in
            player.getServerWorld().spawnParticles(
                    CYAN_BRIGHT,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    12, 0.3, 0.4, 0.3, 0.06
            );
            player.getServerWorld().spawnParticles(
                    ParticleTypes.SWEEP_ATTACK,
                    player.getX(), player.getY() + 0.5, player.getZ(),
                    3, 0.4, 0.2, 0.4, 0
            );
            player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.6f);
        }
    }

    /** Tick effects active during the Rush ability — cloud trail and velocity maintenance. */
    private void tickRushEffects(ServerPlayerEntity player) {
        // Maintain constant horizontal velocity so the player doesn't decelerate mid-rush
        Vec3d vel   = player.getVelocity();
        Vec3d horiz = new Vec3d(vel.x, 0, vel.z);
        if (horiz.length() < RUSH_VELOCITY_BONUS * 0.6) {
            Vec3d look = player.getRotationVec(1.0F);
            player.addVelocity(look.x * 0.18, 0, look.z * 0.18);
            player.velocityModified = true;
        }

        // Trail cloud
        player.getServerWorld().spawnParticles(
                ParticleTypes.CLOUD,
                player.getX(), player.getY() + 0.05, player.getZ(),
                3, 0.15, 0.05, 0.15, 0.005
        );
    }

    /** All per-tick effects for the Overdrive ultimate. */
    public static void tickOverdrive(ServerPlayerEntity player, SpeedState state) {

        // push forward
        forceForward(player, state);
        breakBlocks(player, state);

        // effects
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.JUMP_BOOST, 20, OVERDRIVE_JUMP_AMPLIFIER, true, false));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.HASTE, 20, OVERDRIVE_HASTE_AMPLIFIER, true, false));

        // make water walk
        if (player.isTouchingWater()) {
            Vec3d vel = player.getVelocity();

            // Hard clamp vertical movement so you don't sink
            if (vel.y < 0) {
                player.setVelocity(vel.x, 0.08, vel.z); // slight upward bias
                player.velocityModified = true;
            }

            // force grounded
            player.setOnGround(true);
        }

        // Entity collisions
        player.getServerWorld()
                .getOtherEntities(player, player.getBoundingBox().expand(1.2),
                        e -> e instanceof LivingEntity)
                .forEach(entity -> {
                    DamageSource overdriveSrc = ModDamageTypes.overdrive(player.getWorld(), player);

                    // protects against multi-hit chicanary
                    if (entity.damage(overdriveSrc, (float) OVERDRIVE_ENTITY_DAMAGE)) {

                        // EASTER EGG -------------------
                        if (!entity.isAlive() && entity instanceof ServerPlayerEntity) {
                            if (player.getRandom().nextInt(A_TRAIN_CHANCE) == 0) {
                                // string in chat
                                net.minecraft.text.Text message = net.minecraft.text.Text.literal(
                                        "<" + player.getName().getString() + "> I can't stop. I can't stop. I can't stop. I can't stop."
                                );
                                player.getServer().getPlayerManager().broadcast(message, false);
                            }
                        }
                        // -----------------------------------------------

                        Vec3d dir = entity.getPos().subtract(player.getPos()).normalize();
                        entity.addVelocity(
                                dir.x * OVERDRIVE_ENTITY_KNOCKBACK,
                                OVERDRIVE_ENTITY_KNOCKBACK_Y,
                                dir.z * OVERDRIVE_ENTITY_KNOCKBACK
                        );
                        entity.velocityModified = true;

                        applyCollisionSlow(player, state);

                        player.getServerWorld().spawnParticles(
                                ParticleTypes.EXPLOSION_EMITTER,
                                player.getX(), player.getY() + 1, player.getZ(),
                                4, 0.6, 0.2, 0.6, 0.1
                        );
                        player.getServerWorld().playSound(
                                null, player.getBlockPos(),
                                SoundEvents.ENTITY_GENERIC_EXPLODE,
                                player.getSoundCategory(), 1.0f, 0.8f
                        );
                    }
                });
        spawnOverdriveTrailParticles(player);
    }

    /* ============================================================
       PARTICLE HELPERS
       ============================================================ */

    private static void spawnDashParticles(ServerPlayerEntity player, Vec3d look) {
        double ox = player.getX();
        double oy = player.getY() + 0.8;
        double oz = player.getZ();

        // cyan particles
        player.getServerWorld().spawnParticles(
                CYAN_BRIGHT, ox, oy, oz,
                DASH_PARTICLE_COUNT, 0.3, 0.25, 0.3, 0.06
        );

        // direction based on look vector
        for (int i = 1; i <= 8; i++) {
            double d   = i * 0.35;
            double vel = 0.04 + i * 0.008;
            player.getServerWorld().spawnParticles(
                    (i % 2 == 0) ? CYAN_BRIGHT : WHITE_STREAK,
                    ox - look.x * d, oy - look.y * d * 0.5, oz - look.z * d,
                    1, -look.x * vel, 0.01, -look.z * vel, 0.0
            );
        }

        // white
        for (int i = 0; i < 10; i++) {
            double angle = i * Math.PI * 2.0 / 10;
            player.getServerWorld().spawnParticles(
                    WHITE_STREAK,
                    ox + Math.cos(angle) * 0.4, oy, oz + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.10, 0.015, Math.sin(angle) * 0.10, 0.0
            );
        }

        // Sweep arcs on the ground
        player.getServerWorld().spawnParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, player.getY() + 0.3, oz,
                3, 0.5, 0.15, 0.5, 0
        );
    }

    private static void spawnRushCastParticles(ServerPlayerEntity player) {
        double ox = player.getX();
        double oy = player.getY() + 1.0;
        double oz = player.getZ();

        // Ground-level shockwave ring
        int ringPoints = 20;
        for (int i = 0; i < ringPoints; i++) {
            double angle = i * Math.PI * 2.0 / ringPoints;
            double speed = 0.22;
            player.getServerWorld().spawnParticles(
                    CYAN_BRIGHT,
                    ox + Math.cos(angle) * 0.5, player.getY() + 0.1, oz + Math.sin(angle) * 0.5,
                    1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0
            );
        }

        // blue
        player.getServerWorld().spawnParticles(
                CYAN_BRIGHT, ox, oy, oz,
                20, 0.5, 0.5, 0.5, 0.10
        );
        player.getServerWorld().spawnParticles(
                CYAN_PALE, ox, oy, oz,
                12, 0.6, 0.6, 0.6, 0.08
        );

        // explosions
        player.getServerWorld().spawnParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                ox, oy, oz, 3, 0.6, 0.2, 0.6, 0.1
        );
        player.getServerWorld().spawnParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, player.getY() + 0.5, oz, 4, 0.6, 0.25, 0.6, 0
        );
    }

    private static void spawnOverdriveCastParticles(ServerPlayerEntity player) {
        double ox = player.getX();
        double oy = player.getY() + 1.0;
        double oz = player.getZ();

        // fx in all directions
        for (int d = 0; d < 24; d++) {
            double theta = d * Math.PI * 2.0 / 24;
            double phi   = Math.PI / 4;  // ~45-degree upward angle
            double speed = 0.20;
            player.getServerWorld().spawnParticles(
                    CYAN_BRIGHT, ox, oy, oz,
                    1,
                    Math.cos(theta) * Math.cos(phi) * speed,
                    Math.sin(phi) * speed,
                    Math.sin(theta) * Math.cos(phi) * speed,
                    0.0
            );
        }

        // Outward ground ring
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2.0 / 16;
            player.getServerWorld().spawnParticles(
                    WHITE_STREAK,
                    ox + Math.cos(angle) * 0.4, player.getY() + 0.1, oz + Math.sin(angle) * 0.4,
                    1, Math.cos(angle) * 0.28, 0.01, Math.sin(angle) * 0.28, 0.0
            );
        }

        player.getServerWorld().spawnParticles(
                ParticleTypes.FIREWORK,
                ox, oy, oz, 18, 0.5, 0.6, 0.5, 0.08
        );
        player.getServerWorld().spawnParticles(
                ParticleTypes.FLASH,
                ox, oy, oz, 1, 0.4, 0.2, 0.4, 0
        );
        player.getServerWorld().spawnParticles(
                ParticleTypes.SWEEP_ATTACK,
                ox, oy, oz, 5, 0.7, 0.3, 0.7, 0
        );
    }


    private static void spawnOverdriveTrailParticles(ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity();
        double ox  = player.getX();
        double oy  = player.getY() + 0.8;
        double oz  = player.getZ();

        // backwards kick
        Vec3d back = vel.normalize().negate();
        for (int i = 0; i < 6; i++) {
            double d       = 0.5 + i * 0.55;
            double spread  = 0.10 + i * 0.04;
            double speed   = 0.06 + i * 0.012;
            player.getServerWorld().spawnParticles(
                    (i % 2 == 0) ? CYAN_BRIGHT : CYAN_PALE,
                    ox + back.x * d, oy + back.y * d * 0.3, oz + back.z * d,
                    2, back.x * speed + spread, 0.03, back.z * speed + spread, 0.0
            );
        }

        // Large cloud puffs
        player.getServerWorld().spawnParticles(
                ParticleTypes.CLOUD,
                ox + back.x * 1.5, player.getY() + 0.5, oz + back.z * 1.5,
                8, 0.25, 0.20, 0.25, 0.04
        );
        player.getServerWorld().spawnParticles(
                ParticleTypes.SMOKE,
                ox + back.x * 1.0, player.getY() + 0.7, oz + back.z * 1.0,
                5, 0.15, 0.15, 0.15, 0.03
        );

        // extra stuff
        player.getServerWorld().spawnParticles(
                ParticleTypes.END_ROD,
                ox, oy, oz,
                14, 0.3, 0.6, 0.3, 0.06
        );
        player.getServerWorld().spawnParticles(
                ParticleTypes.FIREWORK,
                ox, oy, oz,
                5, 0.35, 0.55, 0.25, 0.05
        );

        if ((int)(player.getWorld().getTime() % 3) == 0) {
            Vec3d right = new Vec3d(-vel.z, 0, vel.x).normalize();
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI * 2.0 / 8;
                double rx    = right.x * Math.cos(angle) * 0.6;
                double rz    = right.z * Math.cos(angle) * 0.6;
                player.getServerWorld().spawnParticles(
                        WHITE_STREAK,
                        ox + rx, oy, oz + rz,
                        1, back.x * 0.12 + rx * 0.06, 0.015, back.z * 0.12 + rz * 0.06, 0.0
                );
            }
        }
    }

    /* ============================================================
       OVERDRIVE BEHAVIOUR HELPERS
       ============================================================ */

    private static void applyCollisionSlow(ServerPlayerEntity player, SpeedState state) {
        // Slows player
        Vec3d vel = player.getVelocity();
        player.setVelocity(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.velocityModified = true;

        // Impact stun
        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        // Temporary slowness
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 2, true, false
        ));

        // Sound
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_SMALL_FALL,
                player.getSoundCategory(), 0.8f, 0.7f
        );

        // Camera shake
        CameraShake.shakeNearby(player, 5, 15, 0.35f);
    }

    private static void applyCollisionSlowBlock(ServerPlayerEntity player, SpeedState state) {
        // Slows player
        Vec3d vel = player.getVelocity();
        player.setVelocity(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.velocityModified = true;

        // Impact stun
        state.overdriveSlowTicks = OVERDRIVE_SLOW_TICKS;

        // Temporary slowness
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 8, 2, true, false
        ));

        // Sound
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
                player.getSoundCategory(), 0.8f, 0.7f
        );

        // Camera shake
        CameraShake.shakeNearby(player, 6, 17, 0.45f);
    }

    private static void breakBlocks(ServerPlayerEntity player, SpeedState state) {
        // Stops tunneling by blocking method
        if (state.overdriveSlowTicks > 0) return;

        var world   = player.getWorld();
        BlockPos base = player.getBlockPos();
        boolean impactedThisTick = false;

        for (BlockPos pos : BlockPos.iterate(
                base.add(-1, 0, -1),
                base.add(1, 2, 1)
        )) {
            var blockState = world.getBlockState(pos);
            if (blockState.isAir() || blockState.isIn(BlockTags.FIRE)) continue;

            float hardness = blockState.getHardness(world, pos);

            // Unbreakables
            if (hardness < 0) {
                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 2.0f); // stronger shake for unbreakable
                    impactedThisTick = true;
                }
                continue;
            }

            // Weak blocks — destroy silently
            if (blockState.isReplaceable()
                    || blockState.isIn(BlockTags.LEAVES)
                    || blockState.isIn(BlockTags.FLOWERS)
                    || blockState.isIn(BlockTags.SMALL_FLOWERS)
                    || blockState.isIn(BlockTags.TALL_FLOWERS)) {
                world.breakBlock(pos, false);
                continue;
            }

            // Hard blocks that slow on impact
            if (hardness <= OVERDRIVE_BLOCK_HARDNESS_MAX && !blockState.isReplaceable()) {
                world.breakBlock(pos, true, player);

                if (!impactedThisTick) {
                    handleBlockImpact(player, state, 1.4f); // normal impact shake
                    impactedThisTick = true;
                }
            }
        }
    }

    private static void handleBlockImpact(ServerPlayerEntity player, SpeedState state, float shakeStrength) {
        // Ensures this only occurs when collided
        if (!player.horizontalCollision) return;

        // Stops if collided recently
        if (state.overdriveSlowTicks > 0) return;

        applyCollisionSlowBlock(player, state);
        applyCollisionSelfDamage(player, state, OVERDRIVE_SELF_DAMAGE);

        CameraShake.shakeNearby(player, 12.0, 8, shakeStrength);

        player.getServerWorld().spawnParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                player.getX(), player.getY() + 1, player.getZ(),
                3, 0.15, 0.10, 0.15, 0.02
        );
        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE,
                player.getSoundCategory(), 0.8f, 0.85f
        );
    }

    private static void forceForward(ServerPlayerEntity player, SpeedState state) {
        Vec3d look   = player.getRotationVec(1.0F);
        Vec3d vel    = player.getVelocity();
        Vec3d horiz  = new Vec3d(vel.x, 0, vel.z);

        if (horiz.length() >= OVERDRIVE_MIN_SPEED) return;

        // Scales push down if the player is in a collision-slow state
        double slowFactor = (state.overdriveSlowTicks > 0)
                ? MathHelper.clamp(state.overdriveSlowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3d push = new Vec3d(look.x, 0, look.z)
                .normalize()
                .multiply(OVERDRIVE_FORWARD_PUSH * slowFactor);

        player.addVelocity(push.x, 0, push.z);
        player.velocityModified = true;
    }

    private static void applyCollisionSelfDamage(ServerPlayerEntity player, SpeedState state, float amount) {
        // Prevent taking damage every tick while inside the same wall
        if (state.blockDmgCd > 0) return;

        // Custom damage type applied for painting walls with your own lifeblood
        player.damage(ModDamageTypes.wallCollision(player.getWorld()), amount);

        // Damage cooldown
        state.blockDmgCd = 10;

        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_HURT,
                player.getSoundCategory(), 0.8f, 1.0f
        );
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override
    public String getName() { return "Speed"; }

    @Override
    public String getPassiveName() { return "Zippy"; }

    @Override
    public String getPassiveDescription() {
        return "You have permanent speed. This speed increases temporarily when low on health, with a cooldown.";
    }

    @Override
    public String getPrimaryName() { return "Dash"; }

    @Override
    public long getPrimaryCooldownMs() { return 6_000; }

    @Override
    public String getPrimaryDescription() {
        return "Dash in the direction you are looking. This dash prioritises horizontal movement over vertical " +
                "(meaning that you can't dash that far up).";
    }

    @Override
    public String getSecondaryName() { return "Rush"; }

    @Override
    public long getSecondaryCooldownMs() { return 18_000; } // 18 seconds

    @Override
    public String getSecondaryDescription() {
        return "Blast forward in a burst, damaging and knocking back nearby entities in an explosion on cast. " +
                "You then have increased speed and are forced to run forward.";
    }

    @Override
    public String getUltimateName() { return "Overclock"; }

    @Override
    public long getUltimateCooldownMs() { return 400_000; }

    @Override
    public String getUltimateDescription() {
        return "Become extremely fast and propelled forward constantly. Running into soft blocks will destroy them instantly. " +
                "Running into more solid blocks will damage and stop movement briefly, but destroy the wall. Colliding with " +
                "entities will do high damage and knock them away. You can also run on water.";
    }

    @Override
    public String getOverviewDescription() {
        return "Speed mainly focuses on mobility (Who would've guessed!) where most abilities will offer limited direct combat utility, but will provide" +
                " high speed, making you hard to pin down and kill.";
    }
}