package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.*;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

public class ElementalRitual implements Ritual {

    /* ============================================================
        POWER POOL
       ============================================================ */

    private static final List<Supplier<Power>> ELEMENTAL_POWERS = List.of(
            FirePower::new,
            IcePower::new,
            LightningPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 70;
    private static final int STAGE_2_TICKS = 60;
    private static final int STAGE_3_TICKS = 55;
    private static final int STAGE_4_TICKS = 85;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.4f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 10;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private static final double COLUMN_HEIGHT = 10.0;

    /* ============================================================
       PARTICLES
       ============================================================ */

    // fire
    private static final DustParticleEffect FIRE_ORANGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.0f), 1.5f);
    private static final DustParticleEffect FIRE_RED =
            new DustParticleEffect(new Vector3f(0.9f, 0.05f, 0.0f), 1.3f);

    // water
    private static final DustParticleEffect ICE_BLUE =
            new DustParticleEffect(new Vector3f(0.35f, 0.8f, 1.0f), 1.4f);
    private static final DustParticleEffect ICE_WHITE =
            new DustParticleEffect(new Vector3f(0.8f, 0.95f, 1.0f), 1.1f);

    // Lightning
    private static final DustParticleEffect LIGHTNING_YELLOW =
            new DustParticleEffect(new Vector3f(1.0f, 0.95f, 0.1f), 1.5f);
    private static final DustParticleEffect LIGHTNING_WHITE =
            new DustParticleEffect(new Vector3f(0.9f, 0.95f, 1.0f), 1.2f);

    // Earth
    private static final DustParticleEffect EARTH_BROWN =
            new DustParticleEffect(new Vector3f(0.45f, 0.28f, 0.08f), 1.4f);
    private static final DustParticleEffect EARTH_GREEN =
            new DustParticleEffect(new Vector3f(0.2f, 0.55f, 0.1f), 1.2f);

    // Water2
    private static final DustParticleEffect WATER_TEAL =
            new DustParticleEffect(new Vector3f(0.1f, 0.6f, 0.8f), 1.3f);
    private static final DustParticleEffect WATER_CYAN =
            new DustParticleEffect(new Vector3f(0.4f, 0.9f, 0.9f), 1.0f);

    // Air
    private static final DustParticleEffect AIR_PALE =
            new DustParticleEffect(new Vector3f(0.85f, 0.95f, 1.0f), 0.9f);

    // Column definitions — each has a primary and secondary particle.
    private static final DustParticleEffect[] COL_PRIMARY   =
            { FIRE_ORANGE, ICE_BLUE, LIGHTNING_YELLOW, WATER_TEAL };
    private static final DustParticleEffect[] COL_SECONDARY =
            { FIRE_RED,    ICE_WHITE, LIGHTNING_WHITE,  WATER_CYAN };

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public ElementalRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) return true;
        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        if (stage < 4) lockPosition(sp);

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
            case 4 -> tickStage4(sp, world, stageTick);
        }

        if (ticks >= TOTAL_TICKS) {
            onComplete(sp, world);
            return true;
        }

        return false;
    }

    /* ============================================================
       STAGE HELPERS
       ============================================================ */

    private int currentStage() {
        if (ticks <= STAGE_1_TICKS) return 1;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS) return 2;
        if (ticks <= STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS) return 3;
        return 4;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
            case 4 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS - STAGE_3_TICKS;
            default -> ticks;
        };
    }

    private void lockPosition(ServerPlayerEntity sp) {
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;
        sp.fallDistance = 0;
        sp.setOnGround(true);
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 10, true, false, false));
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));
    }

    /* ============================================================
       STAGE 1 — Earth and water ground the player, air stirs above
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_MOSS_PLACE,        SoundCategory.PLAYERS, 1.0f, 0.7f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_WATER_AMBIENT,     SoundCategory.PLAYERS, 0.6f, 1.0f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d pos = sp.getPos();
        long time = world.getTime();

        // Earth ring (Not Urath)
        if (t % 3 == 0) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2.0 * i / 6 + time * 0.02;
                double r = 2.5 + Math.sin(time * 0.05 + i) * 0.3;
                world.spawnParticles(EARTH_BROWN,
                        pos.x + Math.cos(angle) * r, pos.y + 0.05, pos.z + Math.sin(angle) * r,
                        1, 0.1, 0.04, 0.1, 0.005);
                world.spawnParticles(EARTH_GREEN,
                        pos.x + Math.cos(angle + 0.3) * r, pos.y + 0.1, pos.z + Math.sin(angle + 0.3) * r,
                        1, 0.05, 0.05, 0.05, 0.003);
            }
        }

        // meant to be a water swirl
        int waterPoints = (int)(4 + progress * 6);
        for (int i = 0; i < waterPoints; i++) {
            double angle = -(time * 0.06) + (i * Math.PI * 2.0 / waterPoints);
            double r = 1.6;
            double y = pos.y + 0.6 + Math.sin(time * 0.07 + i) * 0.25;
            world.spawnParticles(WATER_TEAL,
                    pos.x + Math.cos(angle) * r, y, pos.z + Math.sin(angle) * r,
                    1, 0, 0.015, 0, 0.004);
            if (t % 4 == 0) {
                world.spawnParticles(WATER_CYAN,
                        pos.x + Math.cos(angle) * r, y + 0.15, pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.02, 0.02, 0.006);
            }
        }

        // Air going up
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.2;
                double h     = 1.5 + world.random.nextDouble() * 2.5;
                world.spawnParticles(AIR_PALE,
                        pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                        1, 0.04, 0.06, 0.04, 0.01);
            }
        }

        if (t % 30 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_WATER_AMBIENT,
                    SoundCategory.PLAYERS, 0.3f, 0.9f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.9f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d pos       = sp.getPos();
        long  time      = world.getTime();

        double columnRadius = 3.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle        = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx           = pos.x + Math.cos(angle) * columnRadius;
            double cz           = pos.z + Math.sin(angle) * columnRadius;
            double columnHeight = COLUMN_HEIGHT * progress;
            int    colSteps     = (int)(columnHeight / 0.5) + 1;

            for (int s = 0; s < colSteps; s++) {
                double y = pos.y + s * 0.5;
                if (t % 3 == 0) {
                    world.spawnParticles(COL_PRIMARY[col],
                            cx + (world.random.nextDouble() - 0.5) * 0.4, y,
                            cz + (world.random.nextDouble() - 0.5) * 0.4,
                            1, 0.04, 0.03, 0.04, 0.008);
                }
                if (t % 5 == 0 && s % 2 == 0) {
                    world.spawnParticles(COL_SECONDARY[col], cx, y + 0.15, cz,
                            1, 0.06, 0.02, 0.06, 0.006);
                }
            }

            // Top
            if (columnHeight > 2.0) {
                world.spawnParticles(COL_PRIMARY[col],
                        cx, pos.y + columnHeight, cz, 2, 0.3, 0.2, 0.3, 0.02);
            }
        }

        // Air swirl
        if (t % 4 == 0) {
            double apexY = pos.y + COLUMN_HEIGHT * progress;
            for (int i = 0; i < 5; i++) {
                double a = (time * 0.12) + (i * Math.PI * 2.0 / 5);
                world.spawnParticles(AIR_PALE,
                        pos.x + Math.cos(a) * (columnRadius * 0.6), apexY,
                        pos.z + Math.sin(a) * (columnRadius * 0.6),
                        1, 0.05, 0.04, 0.05, 0.01);
            }
        }

        // different sounds
        if (t == 15) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_BLAZE_AMBIENT,       SoundCategory.PLAYERS, 0.4f, 1.1f);
        if (t == 30) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_POWDER_SNOW_FALL,     SoundCategory.PLAYERS, 0.5f, 0.8f);
        if (t == 45) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.5f, 1.5f);
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.9f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d  pos      = sp.getPos();

        // shrink radius to player
        double surgeRadius = 3.5 - progress * 2.5;

        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double angle = col * (Math.PI * 2.0 / COL_PRIMARY.length);
            double cx    = pos.x + Math.cos(angle) * surgeRadius;
            double cz    = pos.z + Math.sin(angle) * surgeRadius;

            // make it a lil random
            int colSteps = (int)(COLUMN_HEIGHT * (0.6 + progress * 0.4));
            for (int s = 0; s < colSteps; s++) {
                if (t % 2 == 0) {
                    world.spawnParticles(COL_PRIMARY[col],
                            cx + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6),
                            pos.y + s * 0.45,
                            cz + (world.random.nextDouble() - 0.5) * (0.4 + progress * 0.6),
                            1, 0.06, 0.04, 0.06, 0.012);
                }
            }

            // stuff towards player
            if (t % 5 == 0) {
                Vec3d toPlayer = pos.add(0, 1, 0)
                        .subtract(cx, pos.y + COLUMN_HEIGHT * 0.5, cz)
                        .normalize().multiply(0.08 + progress * 0.06);
                world.spawnParticles(COL_SECONDARY[col],
                        cx, pos.y + COLUMN_HEIGHT * 0.5, cz,
                        1, toPlayer.x, toPlayer.y + 0.02, toPlayer.z, 0.01);
            }
        }

        // try and blend them
        long time = world.getTime();
        for (int col = 0; col < COL_PRIMARY.length; col++) {
            double a = (time * 0.1) + (col * Math.PI * 2.0 / COL_PRIMARY.length);
            double r = 1.4 - progress * 0.5;
            world.spawnParticles(COL_PRIMARY[col],
                    pos.x + Math.cos(a) * r, pos.y + 1.0, pos.z + Math.sin(a) * r,
                    1, 0.03, 0.03, 0.03, 0.01);
        }

        // ramp it up
        if (t % 18 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_BLAZE_AMBIENT,    SoundCategory.PLAYERS,
                    (float)(0.3 + progress * 0.4), 0.8f + (float) progress * 0.3f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_WATER_AMBIENT,     SoundCategory.PLAYERS,
                    (float)(0.3 + progress * 0.3), 1.0f);
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,   SoundCategory.PLAYERS, 1.3f, 0.6f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 1.0f);

            Vec3d pos = sp.getPos();
            for (DustParticleEffect col : COL_PRIMARY) {
                world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                        12, 1.2, 1.0, 1.2, 0.1);
            }
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    3, 0.2, 0.2, 0.2, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // blindess
        if (progress > 0.15f && progress < 0.88f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // damage no kill
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // engulf target
        int burstCount = (int)(8 + (1.0 - progress) * 14);
        for (int i = 0; i < burstCount; i++) {
            DustParticleEffect col = COL_PRIMARY[i % COL_PRIMARY.length];
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 1.8;
            double h     = world.random.nextDouble() * 2.5;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h, pos.z + Math.sin(angle) * r,
                    1, 0, 0.03, 0, 0.018);
        }

        // rainbow mess
        if (t % 4 == 0) {
            // Fire
            world.spawnParticles(ParticleTypes.FLAME,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0.08, 0.08, 0.08, 0.025);
            // Ice
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    pos.x + (world.random.nextDouble() - 0.5) * 2.0,
                    pos.y + 1.0 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 2.0,
                    1, 0.04, 0.04, 0.04, 0.015);
        }
        if (t % 5 == 0) {
            // Lightning
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.2,
                    pos.y + 0.5 + world.random.nextDouble() * 1.8,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.2,
                    1, 0.06, 0.1, 0.06, 0.03);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.3f + (float) progress * 0.2f, 1.6f);
        }

        // Calm it down
        if (progress > 0.8f) {
            for (DustParticleEffect sec : COL_SECONDARY) {
                world.spawnParticles(sec, pos.x, pos.y + 1.2, pos.z,
                        1, 0.4, 0.3, 0.4, 0.006);
            }
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        // Pick randomly from the elemental power pool
        Supplier<Power> factory =
                ELEMENTAL_POWERS.get(sp.getRandom().nextInt(ELEMENTAL_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.extinguish();
        sp.heal(3.0f);

        Vec3d pos = sp.getPos();
        for (DustParticleEffect col : COL_PRIMARY) {
            world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                    10, 1.0, 0.8, 1.0, 0.09);
        }
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }
}