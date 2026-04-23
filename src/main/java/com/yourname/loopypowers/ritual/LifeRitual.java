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
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.List;
import java.util.function.Supplier;

public class LifeRitual implements Ritual {

    /* ============================================================
       POWERS
       ============================================================ */

    private static final List<Supplier<Power>> LIFE_POWERS = List.of(
            HealingPower::new,
            NaturePower::new,
            StrengthPower::new
    );

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 75;   // roots and vines creep up, gentle blooms
    private static final int STAGE_2_TICKS = 65;   // life energy rises, flowers bloom overhead
    private static final int STAGE_3_TICKS = 60;   // energy condenses and descends as light
    private static final int STAGE_4_TICKS = 80;   // life force floods the player

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    // damage
    private static final float STAGE_4_DAMAGE_PER_TICK = 0.25f;
    private static final int   STAGE_4_DAMAGE_INTERVAL = 12;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    private static final double BLOOM_HEIGHT    = 10.0; // height the bloom forms above
    private static final double ORBIT_RADIUS    = 2.2;  // ring orbit radius around player
    private static final double OUTER_RING_RADIUS = 3.8; // outer ground ring

    /* ============================================================
       PARTICLES
       ============================================================ */

    // greens
    private static final DustParticleEffect GREEN =
            new DustParticleEffect(new Vector3f(0.15f, 0.85f, 0.2f), 1.4f);
    private static final DustParticleEffect BRIGHT_GREEN =
            new DustParticleEffect(new Vector3f(0.4f, 1.0f, 0.3f), 1.2f);
    private static final DustParticleEffect DARK_GREEN =
            new DustParticleEffect(new Vector3f(0.05f, 0.5f, 0.1f), 1.6f);

    // yellow
    private static final DustParticleEffect YELLOW =
            new DustParticleEffect(new Vector3f(0.85f, 1.0f, 0.2f), 1.3f);
    private static final DustParticleEffect YELLOW_WHITE =
            new DustParticleEffect(new Vector3f(0.9f, 1.0f, 0.75f), 1.1f);

    // pink
    private static final DustParticleEffect PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.7f, 0.8f), 1.0f);
    private static final DustParticleEffect PINK_WHITE =
            new DustParticleEffect(new Vector3f(0.98f, 0.95f, 0.9f), 0.9f);

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public LifeRitual(PlayerEntity player, RitualManager.RitualType type) {
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
       STAGE 1
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_MOSS_PLACE,
                    SoundCategory.PLAYERS, 0.9f, 0.8f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_GRASS_PLACE,
                    SoundCategory.PLAYERS, 0.6f, 0.9f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d pos = sp.getPos();
        long time = world.getTime();

        // ring slowly rising
        if (t % 3 == 0) {
            int ringPoints = 18;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints + time * 0.015;
                double r     = OUTER_RING_RADIUS * (0.5 + progress * 0.5);
                world.spawnParticles(DARK_GREEN,
                        pos.x + Math.cos(angle) * r, pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0.06, 0.03, 0.06, 0.004);
            }
        }

        // spiral rising
        int vinePoints = (int)(4 + progress * 8);
        for (int i = 0; i < vinePoints; i++) {
            double angle = (time * 0.05) + (i * Math.PI * 2.0 / vinePoints);
            double r     = 0.8 + progress * 0.6;
            double h     = (i / (double) vinePoints) * (1.5 * progress);
            world.spawnParticles(GREEN,
                    pos.x + Math.cos(angle) * r,
                    pos.y + h,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0.02, 0, 0.005);
        }

        // blossoms around
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                world.spawnParticles(PINK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 1.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.008);
            }
        }

        // sound
        if (t % 25 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AZALEA_LEAVES_PLACE,
                    SoundCategory.PLAYERS, 0.5f, 0.9f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT,
                    SoundCategory.PLAYERS, 0.4f, 1.6f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE,
                    SoundCategory.PLAYERS, 0.7f, 0.8f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d pos       = sp.getPos();
        long  time      = world.getTime();

        // why not
        if (t % 20 == 0) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, 25, 0, true, false, false));
        }

        // double orbit
        for (int ring = 0; ring < 2; ring++) {
            double ringR   = ORBIT_RADIUS - ring * 0.6;
            double dir     = ring == 0 ? 1 : -1;
            int    points  = 8 + ring * 4;

            for (int i = 0; i < points; i++) {
                double angle = (time * 0.07 * dir) + (i * Math.PI * 2.0 / points);
                double h     = 0.8 + Math.sin(time * 0.06 + i + ring) * 0.3;
                DustParticleEffect col = ring == 0 ? GREEN : BRIGHT_GREEN;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * ringR,
                        pos.y + h,
                        pos.z + Math.sin(angle) * ringR,
                        1, 0, 0.02, 0, 0.005);
            }
        }

        // bloom
        double bloomY    = pos.y + BLOOM_HEIGHT;
        int bloomDensity = (int)(5 + progress * 25);
        double bloomR    = 2.0 - progress * 0.5;

        for (int i = 0; i < bloomDensity; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * bloomR;
            DustParticleEffect col = world.random.nextFloat() < 0.6f
                    ? YELLOW_WHITE : PINK_WHITE;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r,
                    bloomY + (world.random.nextDouble() - 0.4) * 1.5,
                    pos.z + Math.sin(angle) * r,
                    1, 0.04, 0.06, 0.04, 0.015);
        }

        // extra
        if (t % 4 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * bloomR * 1.5,
                    bloomY,
                    pos.z + (world.random.nextDouble() - 0.5) * bloomR * 1.5,
                    1, 0, 0.04, 0, 0.02);
        }

        // more petals
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 2.5;
                world.spawnParticles(PINK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + world.random.nextDouble() * 3.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.015, 0.05, 0.015, 0.01);
            }
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.getBlockPos(),
                        SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE,
                    SoundCategory.PLAYERS, 0.4f, 1.0f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       STAGE 3
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                    SoundCategory.PLAYERS, 0.5f, 0.7f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d pos       = sp.getPos();
        long  time      = world.getTime();

        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.LEVITATION, 5, 1, true, false, false));

        // cluster goes down
        double bloomY    = pos.y + BLOOM_HEIGHT - (BLOOM_HEIGHT - 2.5) * progress;
        int    density   = 18 + (int)(progress * 15);
        double bloomR    = 1.8 - progress * 1.2; // concentrates as it nears

        for (int i = 0; i < density; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * Math.max(0.2, bloomR);
            DustParticleEffect col = world.random.nextFloat() < 0.5f
                    ? YELLOW_WHITE : YELLOW;
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r,
                    bloomY + (world.random.nextDouble() - 0.3) * 1.0,
                    pos.z + Math.sin(angle) * r,
                    1, 0.03, 0.05, 0.03, 0.012 + progress * 0.015);
        }

        // connect it to player
        if (t % 2 == 0) {
            int beamSteps = 10;
            for (int i = 0; i < beamSteps; i++) {
                double beamT = i / (double) beamSteps;
                double beamY = pos.y + 1.2 + (bloomY - pos.y - 1.2) * beamT;
                double jitter = 0.12 * (1.0 - beamT); // wider at top, tight at player
                world.spawnParticles(BRIGHT_GREEN,
                        pos.x + (world.random.nextDouble() - 0.5) * jitter,
                        beamY,
                        pos.z + (world.random.nextDouble() - 0.5) * jitter,
                        1, 0, 0.02, 0, 0.006);
            }
        }

        // extra end rods
        if (progress > 0.5f && t % 3 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    bloomY - 0.3,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    1, 0.04, 0.06, 0.04, 0.025);
        }

        // maintain orbit
        for (int i = 0; i < 6; i++) {
            double angle = (time * 0.08) + (i * Math.PI * 2.0 / 6);
            world.spawnParticles(BRIGHT_GREEN,
                    pos.x + Math.cos(angle) * ORBIT_RADIUS,
                    pos.y + 1.0,
                    pos.z + Math.sin(angle) * ORBIT_RADIUS,
                    1, 0, 0.015, 0, 0.004);
        }

        // contact
        if (progress > 0.93f) {
            world.spawnParticles(YELLOW_WHITE, pos.x, pos.y + 1.5, pos.z,
                    8, 0.5, 0.4, 0.5, 0.05);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.5, pos.z,
                    1, 0, 0, 0, 0);
            if (t % 5 == 0) {
                world.playSound(null, sp.getBlockPos(),
                        SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE,
                        SoundCategory.PLAYERS, 0.6f, 1.4f);
            }
        }
    }

    /* ============================================================
       STAGE 4
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                    SoundCategory.PLAYERS, 1.1f, 0.7f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_FLOWERING_AZALEA_PLACE,
                    SoundCategory.PLAYERS, 1.0f, 0.5f);

            Vec3d pos = sp.getPos();
            world.spawnParticles(YELLOW_WHITE, pos.x, pos.y + 1.0, pos.z,
                    45, 1.3, 1.1, 1.3, 0.1);
            world.spawnParticles(GREEN, pos.x, pos.y + 1.0, pos.z,
                    30, 1.0, 0.9, 1.0, 0.09);
            world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                    2, 0.15, 0.1, 0.15, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // Blindness
        if (progress > 0.1f && progress < 0.82f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 25, 0, true, false, false));
        }

        // damage
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // just for fun
        if (t % 15 == 0) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, 20, 1, true, false, false));
        }

        // final fx
        int burstCount = (int)(10 + (1.0 - progress) * 12);
        for (int i = 0; i < burstCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 2.0;
            double h     = world.random.nextDouble() * 2.8;
            DustParticleEffect col = switch (i % 4) {
                case 0  -> GREEN;
                case 1  -> BRIGHT_GREEN;
                case 2  -> YELLOW;
                default -> YELLOW_WHITE;
            };
            world.spawnParticles(col,
                    pos.x + Math.cos(angle) * r, pos.y + h,
                    pos.z + Math.sin(angle) * r,
                    1, 0, 0.025, 0, 0.015);
        }

        // Vanilla nature particles in the storm
        if (t % 2 == 0) {
            world.spawnParticles(ParticleTypes.COMPOSTER,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0.05, 0.08, 0.05, 0.01);
        }

        // blossoms
        if (t % 2 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.6;
                world.spawnParticles(PINK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + world.random.nextDouble() * 2.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.012);
            }
        }

        // sparks
        if (t % 6 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.5,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.5,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        // calming
        if (progress > 0.78f) {
            world.spawnParticles(PINK_WHITE, pos.x, pos.y + 1.2, pos.z,
                    3, 0.5, 0.3, 0.5, 0.005);
            world.spawnParticles(YELLOW, pos.x, pos.y + 1.4, pos.z,
                    2, 0.4, 0.2, 0.4, 0.004);
        }

        if (t % 22 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AZALEA_LEAVES_PLACE,
                    SoundCategory.PLAYERS, 0.4f, 0.8f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       COMPLETION
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        Supplier<Power> factory =
                LIFE_POWERS.get(sp.getRandom().nextInt(LIFE_POWERS.size()));
        PowerManager.setPower(sp, factory.get());

        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.REGENERATION);
        sp.heal(4.0f); // heal

        Vec3d pos = sp.getPos();
        world.spawnParticles(GREEN,      pos.x, pos.y + 1.0, pos.z, 25, 1.1, 0.9, 1.1, 0.08);
        world.spawnParticles(YELLOW_WHITE, pos.x, pos.y + 1.0, pos.z, 20, 0.9, 0.7, 0.9, 0.07);
        world.spawnParticles(PINK,    pos.x, pos.y + 1.0, pos.z, 15, 0.8, 0.6, 0.8, 0.06);
        world.spawnParticles(YELLOW,       pos.x, pos.y + 1.2, pos.z, 10, 0.6, 0.5, 0.6, 0.05);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z,
                2, 0.2, 0.1, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 0.9f);
    }
}