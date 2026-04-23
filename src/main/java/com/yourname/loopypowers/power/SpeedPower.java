package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
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

// HANDLES ALL ATTRIBUTES OF SPEED POWER

public class SpeedPower implements Power { // SPEED

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
    private static final float  RUSH_DAMAGE                 = 6.0f;    // 3 hearts
    private static final double RUSH_KNOCKBACK_HORIZONTAL   = 1.4;
    private static final double RUSH_KNOCKBACK_VERTICAL     = 0.55;
    private static final double RUSH_KNOCKBACK_SCAN_RADIUS  = 4.5;
    private static final double RUSH_VELOCITY_BONUS         = 1.5;     // flat horizontal velocity on cast
    private static final int    RUSH_HASTE_AMPLIFIER        = 0;

    // ULT
    private static final int    OVERDRIVE_DURATION_TICKS    = 200;     // 10 seconds
    private static final double OVERDRIVE_MIN_SPEED         = 1.4;
    private static final double OVERDRIVE_FORWARD_PUSH      = 0.38;
    private static final double OVERDRIVE_ENTITY_DAMAGE     = 8.0f;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK  = 1.8;
    private static final double OVERDRIVE_ENTITY_KNOCKBACK_Y = 0.5;
    private static final double OVERDRIVE_COLLISION_SLOW_X  = 0.15;
    private static final double OVERDRIVE_COLLISION_SLOW_Y  = 0.10;
    private static final int    OVERDRIVE_SLOW_TICKS        = 12;
    private static final float  OVERDRIVE_BLOCK_HARDNESS_MAX = 3.0f;
    private static final float  OVERDRIVE_SELF_DAMAGE       = 1.5f;
    private static final int    OVERDRIVE_JUMP_AMPLIFIER    = 0;
    private static final int    OVERDRIVE_HASTE_AMPLIFIER   = 2;

    // sound
    private static final int RUSH_LOOP_INTERVAL_TICKS      = 25;   // ~0.9s
    private static final int OVERDRIVE_LOOP_INTERVAL_TICKS = 45;   // ~1.0s

    // particles
    private static final DustParticleEffect CYAN_BRIGHT  =
            new DustParticleEffect(new Vector3f(0.15f, 0.85f, 1.00f), 1.3f);
    private static final DustParticleEffect CYAN_PALE    =
            new DustParticleEffect(new Vector3f(0.70f, 0.95f, 1.00f), 1.0f);
    private static final DustParticleEffect WHITE_STREAK =
            new DustParticleEffect(new Vector3f(0.95f, 0.98f, 1.00f), 0.8f);

    // constants
    private static final String RUSH_TAG              = "speed_rush";
    private static final String RUSH_TICKS_PREFIX     = "speed_rush_ticks_";
    private static final String RUSH_LOOP_CD          = "speed_rush_loop_cd_";
    private static final String OVERDRIVE_TAG         = "overdrive";
    private static final String OVERDRIVE_TICKS_PREFIX = "overdrive_ticks_";
    private static final String OVERDRIVE_LOOP_CD     = "overdrive_loop_cd_";
    private static final String OVERDRIVE_SLOW_PREFIX = "overdrive_slow_";
    private static final String BLOCK_DMG_CD_PREFIX   = "overdrive_block_dmg_";
    private static final String BURST_CD_PREFIX       = "speed_burst_cd_";

    /* ============================================================
       PASSIVE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // passive
        player.addStatusEffect(
                new StatusEffectInstance(StatusEffects.SPEED, 40, PASSIVE_SPEED_AMPLIFIER, true, false)
        );
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        tickPassiveSpeed(player);
        tickLowHealthBurst(player);
        tickBurstCooldown(player);

        if (player.getCommandTags().contains(RUSH_TAG)) {
            tickRushEffects(player);
            tickCountdown(player, RUSH_TICKS_PREFIX, RUSH_TAG, RUSH_LOOP_CD);
        }

        if (player.getCommandTags().contains(OVERDRIVE_TAG)) {
            tickOverdrive(player);
            tickSlowLock(player);
            tickBlockDamageCooldown(player);
            tickLoopSound(player, RUSH_LOOP_CD, RUSH_LOOP_INTERVAL_TICKS,    ModSounds.RUSHLOOP,  0.55f, 1.0f);
            //tickLoopSound(player, OVERDRIVE_LOOP_CD, OVERDRIVE_LOOP_INTERVAL_TICKS, ModSounds.ELECTRICITY, 0.65f, 1.0f);
            tickCountdown(player, OVERDRIVE_TICKS_PREFIX, OVERDRIVE_TAG, OVERDRIVE_LOOP_CD);
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
        player.getCommandTags().add(RUSH_TAG);
        player.getCommandTags().add(RUSH_TICKS_PREFIX + RUSH_DURATION_TICKS);

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
                    victim.damage(rushSrc, RUSH_DAMAGE);

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

        // 0 cd so the loop plays immediately on the next tick
        setSingleTimerTag(player, RUSH_LOOP_CD, 0);
    }

    /* ============================================================
       ULTIMATE — OVERDRIVE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        player.getCommandTags().add(OVERDRIVE_TAG);
        player.getCommandTags().add(OVERDRIVE_TICKS_PREFIX + OVERDRIVE_DURATION_TICKS);

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
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        if (speed == null || speed.getDuration() < 5) {
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SPEED, 200, PASSIVE_SPEED_AMPLIFIER, true, false)
            );
        }
    }

    /** Triggers a short burst of extreme speed when the player's health drops below the threshold. */
    private void tickLowHealthBurst(ServerPlayerEntity player) {
        // do nothing if burst is still on cooldown
        if (hasBurstCooldown(player)) return;

        if (player.getHealth() <= PASSIVE_LOW_HEALTH_THRESHOLD) {
            player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SPEED, PASSIVE_BURST_DURATION,
                            PASSIVE_BURST_AMPLIFIER, true, false)
            );

            // start cooldown
            setSingleTimerTag(player, BURST_CD_PREFIX, PASSIVE_BURST_COOLDOWN_TICKS);

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

    /** Ticks down the low-health burst cooldown tag. */
    private void tickBurstCooldown(ServerPlayerEntity player) {
        tickSingleTimer(player, BURST_CD_PREFIX);
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
        // sound loop call
        /* tickLoopSound(player, RUSH_LOOP_CD, RUSH_LOOP_INTERVAL_TICKS, ModSounds.RUSHLOOP, 0.55f, 1.0f); */
    }

    /** All per-tick effects for the Overdrive ultimate. */
    public static void tickOverdrive(ServerPlayerEntity player) {

        // push forward
        forceForward(player);
        breakBlocks(player);

        // effects
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.JUMP_BOOST, 20, OVERDRIVE_JUMP_AMPLIFIER, true, false));
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.HASTE, 20, OVERDRIVE_HASTE_AMPLIFIER, true, false));

        // make water walk
        if (player.getCommandTags().contains(OVERDRIVE_TAG) && player.isTouchingWater()) {
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
                    entity.damage(overdriveSrc, (float) OVERDRIVE_ENTITY_DAMAGE);

                    Vec3d dir = entity.getPos().subtract(player.getPos()).normalize();
                    entity.addVelocity(
                            dir.x * OVERDRIVE_ENTITY_KNOCKBACK,
                            OVERDRIVE_ENTITY_KNOCKBACK_Y,
                            dir.z * OVERDRIVE_ENTITY_KNOCKBACK
                    );
                    entity.velocityModified = true;

                    applyCollisionSlow(player);

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

    private static void applyCollisionSlow(ServerPlayerEntity player) {
        // Slows player
        Vec3d vel = player.getVelocity();
        player.setVelocity(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.velocityModified = true;

        // Impact stun, number is tick duration of slow
        player.getCommandTags().add(OVERDRIVE_SLOW_PREFIX + OVERDRIVE_SLOW_TICKS);

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

    private static void applyCollisionSlowBlock(ServerPlayerEntity player) {
        // Slows player
        Vec3d vel = player.getVelocity();
        player.setVelocity(
                vel.x * OVERDRIVE_COLLISION_SLOW_X,
                Math.min(vel.y, OVERDRIVE_COLLISION_SLOW_Y),
                vel.z * OVERDRIVE_COLLISION_SLOW_X
        );
        player.velocityModified = true;

        // Impact stun, number is tick duration of slow
        player.getCommandTags().add(OVERDRIVE_SLOW_PREFIX + OVERDRIVE_SLOW_TICKS);

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

    private static void tickSlowLock(ServerPlayerEntity player) {
        tickSingleTimer(player, OVERDRIVE_SLOW_PREFIX);
    }

    private static void tickBlockDamageCooldown(ServerPlayerEntity player) {
        tickSingleTimer(player, BLOCK_DMG_CD_PREFIX);
    }

    private static void breakBlocks(ServerPlayerEntity player) {
        // Stops tunneling by blocking method
        if (isCollisionLocked(player)) return;

        var world   = player.getWorld();
        BlockPos base = player.getBlockPos();
        boolean impactedThisTick = false;

        for (BlockPos pos : BlockPos.iterate(
                base.add(-1, 0, -1),
                base.add(1, 2, 1)
        )) {
            var state = world.getBlockState(pos);
            if (state.isAir() || state.isIn(BlockTags.FIRE)) continue;

            float hardness = state.getHardness(world, pos);

            // Unbreakables
            if (hardness < 0) {
                if (!impactedThisTick) {
                    handleBlockImpact(player, 2.0f); // stronger shake for unbreakable
                    impactedThisTick = true;
                }
                continue;
            }

            // Weak blocks — destroy silently
            if (state.isReplaceable()
                    || state.isIn(BlockTags.LEAVES)
                    || state.isIn(BlockTags.FLOWERS)
                    || state.isIn(BlockTags.SMALL_FLOWERS)
                    || state.isIn(BlockTags.TALL_FLOWERS)) {
                world.breakBlock(pos, false);
                continue;
            }

            // Hard blocks that slow on impact
            if (hardness <= OVERDRIVE_BLOCK_HARDNESS_MAX && !state.isReplaceable()) {
                world.breakBlock(pos, true, player);

                if (!impactedThisTick) {
                    handleBlockImpact(player, 1.4f); // normal impact shake
                    impactedThisTick = true;
                }
            }
        }
    }

    private static void handleBlockImpact(ServerPlayerEntity player, float shakeStrength) {
        // Ensures this only occurs when collided
        if (!player.horizontalCollision) return;

        // Stops if collided recently
        if (isCollisionLocked(player)) return;

        applyCollisionSlowBlock(player);
        applyCollisionSelfDamage(player, OVERDRIVE_SELF_DAMAGE);

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

    private static void forceForward(ServerPlayerEntity player) {
        Vec3d look   = player.getRotationVec(1.0F);
        Vec3d vel    = player.getVelocity();
        Vec3d horiz  = new Vec3d(vel.x, 0, vel.z);

        if (horiz.length() >= OVERDRIVE_MIN_SPEED) return;

        // Scales push down if the player is in a collision-slow state
        int    slowTicks  = getSlowTicks(player);
        double slowFactor = (slowTicks > 0)
                ? MathHelper.clamp(slowTicks / 15.0, 0.15, 1.0)
                : 1.0;

        Vec3d push = new Vec3d(look.x, 0, look.z)
                .normalize()
                .multiply(OVERDRIVE_FORWARD_PUSH * slowFactor);

        player.addVelocity(push.x, 0, push.z);
        player.velocityModified = true;
    }

    private static void applyCollisionSelfDamage(ServerPlayerEntity player, float amount) {
        // Prevent taking damage every tick while inside the same wall
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(BLOCK_DMG_CD_PREFIX)) return;
        }

        player.damage(player.getDamageSources().flyIntoWall(), amount);

        // Damage cooldown
        player.getCommandTags().add(BLOCK_DMG_CD_PREFIX + "10");

        player.getServerWorld().playSound(
                null, player.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_HURT,
                player.getSoundCategory(), 0.8f, 1.0f
        );
    }

    /* ============================================================
       GENERAL TAG/TIMER HELPERS
       ============================================================ */

    private static void tickCountdown(ServerPlayerEntity player,
                                      String ticksPrefix,
                                      String abilityTag,
                                      String loopCdPrefix) {
        for (String tag : player.getCommandTags()) {
            if (!tag.startsWith(ticksPrefix)) continue;

            int ticks = Integer.parseInt(tag.substring(ticksPrefix.length())) - 1;
            player.getCommandTags().remove(tag);

            if (ticks > 0) {
                player.getCommandTags().add(ticksPrefix + ticks);
            } else {
                // Effect ends
                player.getCommandTags().remove(abilityTag);
                removeTagPrefix(player, loopCdPrefix);
            }
            break;
        }
    }

    private static boolean hasBurstCooldown(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(BURST_CD_PREFIX)) return true;
        }
        return false;
    }

    private static boolean isCollisionLocked(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(OVERDRIVE_SLOW_PREFIX)) return true;
        }
        return false;
    }

    private static int getSlowTicks(ServerPlayerEntity player) {
        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(OVERDRIVE_SLOW_PREFIX)) {
                return Integer.parseInt(tag.substring(OVERDRIVE_SLOW_PREFIX.length()));
            }
        }
        return 0;
    }

    private static void tickLoopSound(
            ServerPlayerEntity player,
            String cdPrefix,
            int intervalTicks,
            net.minecraft.sound.SoundEvent sound,
            float volume,
            float pitch
    ) {
        // Tick down cooldown
        int left = tickSingleTimer(player, cdPrefix);

        // If no tag existed treat as 0
        if (left == -1) left = 0;

        if (left <= 0) {
            player.getServerWorld().playSound(
                    null, player.getBlockPos(),
                    sound, player.getSoundCategory(),
                    volume, pitch
            );
            setSingleTimerTag(player, cdPrefix, intervalTicks);
        }
    }

    private static void removeTagPrefix(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) { it.remove(); return; }
        }
    }

    private static void setSingleTimerTag(ServerPlayerEntity p, String prefix, int ticks) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + ticks);
    }

    /** Decrements a single-timer tag; returns remaining ticks, or -1 if no tag was found. */
    private static int tickSingleTimer(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (!tag.startsWith(prefix)) continue;

            int ticks;
            try {
                ticks = Integer.parseInt(tag.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                it.remove();
                return -1;
            }

            it.remove();
            if (ticks > 0) p.getCommandTags().add(prefix + ticks);
            return ticks;
        }
        return -1;
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
    public long getPrimaryCooldownMs() { return 5_000; } // 5 seconds

    @Override
    public String getPrimaryDescription() {
        return "Dash in the direction you are looking. This dash prioritises horizontal movement over vertical " +
                "(meaning that you can't dash that far up).";
    }

    @Override
    public String getSecondaryName() { return "Rush"; }

    @Override
    public long getSecondaryCooldownMs() { return 5_000; } // 18 seconds

    @Override
    public String getSecondaryDescription() {
        return "Blast forward in a burst, damaging and knocking back nearby entities in an explosion on cast. " +
                "You then have increased speed and are forced to run forward.";
    }

    @Override
    public String getUltimateName() { return "Overdrive"; }

    @Override
    public long getUltimateCooldownMs() { return 10_000; } // 120 seconds

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