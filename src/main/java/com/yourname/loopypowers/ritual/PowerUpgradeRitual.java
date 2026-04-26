package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
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

public class PowerUpgradeRitual implements Ritual {

    /* ============================================================
       CONSTANTS — deliberately shorter than a full vestige ritual.
       Three stages: the god's attention descends, the bond is tested,
       the connection is reforged stronger.
       ============================================================ */

    private static final int STAGE_1_TICKS = 50;   // the presence descends
    private static final int STAGE_2_TICKS = 45;   // the bond is tested and strained
    private static final int STAGE_3_TICKS = 55;   // reforging — the pain and the gift

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS;

    // Stage 2 deals minor damage — the test has a cost
    private static final float STAGE_2_DAMAGE_PER_TICK = 0.3f;
    private static final int   STAGE_2_DAMAGE_INTERVAL = 12;
    private static final float STAGE_2_MIN_HEALTH      = 1.0f;

    // Camera shake — values ramp across the three stages.
    // Stage 1: barely perceptible, just a tremor as the presence arrives.
    // Stage 2: noticeable, the bond being strained.
    // Stage 3: strong at the reforge burst, then easing out.
    private static final int   SHAKE_RADIUS          = 6;
    private static final int   SHAKE_STAGE_1_INTERVAL = 20;   // once per second — subtle
    private static final int   SHAKE_STAGE_2_INTERVAL = 10;   // twice per second
    private static final int   SHAKE_STAGE_3_INTERVAL = 5;    // four times per second at peak
    private static final float SHAKE_STAGE_1_BASE     = 0.08f;
    private static final float SHAKE_STAGE_2_BASE     = 0.16f;
    private static final float SHAKE_STAGE_3_BASE     = 0.22f;
    private static final float SHAKE_STAGE_3_PEAK     = 0.38f; // burst on t==1

    /* ============================================================
       PARTICLES
       Visual language: divine magenta-pink, deep connection purple,
       holy white, and a soft violet highlight.
       The god's presence is not golden fire — it is something stranger
       and more ancient, felt in the bones before it is seen.
       ============================================================ */

    // Vivid magenta pink — the raw divine contact, the god's touch
    private static final DustParticleEffect DIVINE_PINK =
            new DustParticleEffect(new Vector3f(0.95f, 0.15f, 0.70f), 1.5f);
    // Soft blush pink — ambient glow, the outer edge of the presence
    private static final DustParticleEffect BLUSH_PINK =
            new DustParticleEffect(new Vector3f(1.00f, 0.62f, 0.88f), 1.2f);
    // Deep connection purple — the bond itself, thick and resonant
    private static final DustParticleEffect BOND_PURPLE =
            new DustParticleEffect(new Vector3f(0.52f, 0.08f, 0.80f), 1.5f);
    // Soft violet — the transition between the pink and purple, where they merge
    private static final DustParticleEffect VIOLET_SOFT =
            new DustParticleEffect(new Vector3f(0.75f, 0.35f, 0.95f), 1.3f);
    // Holy white — the pure centre of the reforged connection
    private static final DustParticleEffect HOLY_WHITE =
            new DustParticleEffect(new Vector3f(0.96f, 0.94f, 1.00f), 1.3f);

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public PowerUpgradeRitual(PlayerEntity player, RitualManager.RitualType type) {
        this.player = player;
        this.type   = type;
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public boolean tick(ServerWorld world) {
        if (player.isRemoved() || !player.isAlive()) return true;
        if (!(player instanceof ServerPlayerEntity sp)) return true;

        ticks++;
        if (ticks > TOTAL_TICKS) return true;

        // Lock position throughout — the player is held in place by the god's grip
        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

        switch (stage) {
            case 1 -> tickStage1(sp, world, stageTick);
            case 2 -> tickStage2(sp, world, stageTick);
            case 3 -> tickStage3(sp, world, stageTick);
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
        return 3;
    }

    private int stageLocalTick() {
        return switch (currentStage()) {
            case 1 -> ticks;
            case 2 -> ticks - STAGE_1_TICKS;
            case 3 -> ticks - STAGE_1_TICKS - STAGE_2_TICKS;
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
       STAGE 1 — The presence descends.
       Pink light falls from above; the ground beneath the player
       ignites with divine sigils. The god is turning its attention
       toward this mortal. Camera shake: barely perceptible.
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 0.9f, 0.55f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 0.5f, 0.3f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();

        // Soft tremor — barely felt, like something vast shifting its attention
        if (t % SHAKE_STAGE_1_INTERVAL == 0) {
            // Intensity ramps very gently within stage 1 itself
            float intensity = SHAKE_STAGE_1_BASE + (float) progress * 0.04f;
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // Pink light falling from directly above — a column of divine attention
        // descending onto the player. Particles spawn high and drift down slowly.
        if (t % 2 == 0) {
            int columnCount = (int)(4 + progress * 8);
            for (int i = 0; i < columnCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.0;
                double h     = 8.0 + world.random.nextDouble() * 6.0;  // spawn high above
                DustParticleEffect col = world.random.nextBoolean() ? DIVINE_PINK : BLUSH_PINK;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.06, 0.02, 0.005);   // negative y = falling down
            }
        }

        // Expanding divine ring on the ground — the god's footprint forming.
        // A new ring pulse every 15 ticks, each one racing outward.
        int    pulseTimer = t % 15;
        double ringR      = pulseTimer * 0.32;
        if (ringR > 0.3 && t % 2 == 0) {
            int ringPoints = 16;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                // Inner pulse is deep purple; outer edge fades to blush
                DustParticleEffect col = (pulseTimer < 8) ? BOND_PURPLE : BLUSH_PINK;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * ringR,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * ringR,
                        1, 0.02, 0.02, 0.02, 0.004);
            }
        }

        // Slow ambient purple wisps rising from near the player — their own power
        // beginning to stir in response to the divine attention
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 1.2;
                world.spawnParticles(VIOLET_SOFT,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1 + world.random.nextDouble() * 2.0 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.04, 0.01, 0.007);
            }
        }

        // Enchant particles radiating outward — the universe acknowledging the appeal
        if (t % 16 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z,
                    8, 0.5, 0.6, 0.5, 1.4);
        }

        // GLOW sparks from the ground — divine heat touching the earth
        if (t % 6 == 0 && progress > 0.3f) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.5 + world.random.nextDouble() * 2.0;
                world.spawnParticles(ParticleTypes.GLOW,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1, 0, 0, 0, 0);
            }
        }

        if (t % 20 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_HIT,
                    SoundCategory.PLAYERS, 0.5f, 0.4f + (float) progress * 0.4f);
        }
    }

    /* ============================================================
       STAGE 2 — The bond is tested.
       The god examines the existing connection — and it hurts.
       Streams of the player's own power are pulled upward to be
       inspected and judged. Camera shake: noticeable and building.
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_ACTIVATE,       SoundCategory.PLAYERS, 0.7f, 0.65f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // Shake ramps up through the stage — starts at the stage 2 base and
        // climbs toward stage 3 base by the end. Fires twice per second.
        if (t % SHAKE_STAGE_2_INTERVAL == 0) {
            float intensity = SHAKE_STAGE_2_BASE + (float) progress * (SHAKE_STAGE_3_BASE - SHAKE_STAGE_2_BASE);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // Power streams rising: the player's connection being pulled upward for inspection.
        // Six streams, each mapping to a different facet of their power.
        if (t % 2 == 0) {
            int streamCount = 6;
            for (int s = 0; s < streamCount; s++) {
                double baseAngle = s * Math.PI * 2.0 / streamCount;
                double spin      = baseAngle + time * 0.06;
                double r         = 0.5 + progress * 0.4;

                // Each stream: particles spawned close and drifting steeply upward,
                // becoming more turbulent (more spread) as the test intensifies
                int steps = 8;
                for (int i = 0; i < steps; i++) {
                    double h     = pos.y + 0.3 + i * (0.8 + progress * 0.5);
                    double angle = spin + i * 0.08;
                    DustParticleEffect col = switch (s % 3) {
                        case 0  -> DIVINE_PINK;
                        case 1  -> HOLY_WHITE;
                        default -> BOND_PURPLE;
                    };
                    if (i < steps - 1) {   // body of the stream
                        world.spawnParticles(col,
                                pos.x + Math.cos(angle) * r,
                                h,
                                pos.z + Math.sin(angle) * r,
                                1, 0.03 + progress * 0.04, 0.05, 0.03 + progress * 0.04, 0.005);
                    } else {               // tip disperses outward into the divine light above
                        world.spawnParticles(HOLY_WHITE,
                                pos.x + Math.cos(angle) * r,
                                h,
                                pos.z + Math.sin(angle) * r,
                                1, 0.08, 0.12, 0.08, 0.02);
                    }
                }
            }
        }

        // Falling pink rain intensifies — divine scrutiny pressing down
        if (t % 2 == 0) {
            int rainCount = (int)(6 + progress * 10);
            for (int i = 0; i < rainCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                DustParticleEffect col = world.random.nextBoolean() ? DIVINE_PINK : VIOLET_SOFT;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 6.0 + world.random.nextDouble() * 4.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.08, 0.01, 0.003);
            }
        }

        // Purple ring pulsing outward at waist height — the bond being stretched
        if (t % 8 == 0) {
            int ringPoints = 14;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                double speed = 0.12 + progress * 0.06;
                world.spawnParticles(BOND_PURPLE,
                        pos.x + Math.cos(angle) * 0.4, pos.y + 1.0, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }

        // GLOW flash — the test flaring bright at intervals
        if (t % 8 == 0) {
            world.spawnParticles(ParticleTypes.GLOW,
                    pos.x, pos.y + 1.0, pos.z,
                    4, 0.4, 0.5, 0.4, 0);
        }

        // Blindness — too close to the divine to see clearly
        if (progress > 0.20f && progress < 0.88f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // The test has a cost — minor damage as the bond is stretched
        if (t % STAGE_2_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_2_MIN_HEALTH + STAGE_2_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_2_DAMAGE_PER_TICK);
            }
        }

        if (t == 20) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, SoundCategory.PLAYERS, 0.6f, 0.5f);
        if (t == 38) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.PLAYERS, 0.5f, 0.8f);
    }

    /* ============================================================
       STAGE 3 — Reforging.
       The god has judged the connection worthy of strengthening.
       Divine energy floods downward through the bond — overwhelming,
       glorious, painful in its intensity. Camera shake: peaks at the
       reforge burst then eases as the bond settles.
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 0.8f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,     SoundCategory.PLAYERS, 1.0f, 0.9f);

            // Peak shake — the reforge moment hits hardest
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, SHAKE_STAGE_3_PEAK);

            // Opening reforge burst: a sphere of pink, purple, and white fired outward —
            // the god's power flooding into the player all at once
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 36; d++) {
                double theta = d * Math.PI * 2.0 / 36;
                double phi   = Math.PI * 0.3 + world.random.nextDouble() * Math.PI * 0.4;
                double speed = 0.20 + world.random.nextDouble() * 0.12;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> DIVINE_PINK;
                    case 1  -> HOLY_WHITE;
                    case 2  -> BOND_PURPLE;
                    default -> VIOLET_SOFT;
                };
                world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
            // Ground shockwave ring from the burst
            for (int i = 0; i < 18; i++) {
                double angle = i * Math.PI * 2.0 / 18;
                world.spawnParticles(BOND_PURPLE,
                        pos.x + Math.cos(angle) * 0.4, pos.y + 0.15, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * 0.30, 0.01, Math.sin(angle) * 0.30, 0.0);
            }
            world.spawnParticles(ParticleTypes.FLASH,
                    pos.x, pos.y + 1.0, pos.z, 4, 0.3, 0.3, 0.3, 0);
            world.spawnParticles(ParticleTypes.GLOW,
                    pos.x, pos.y + 1.0, pos.z, 12, 0.6, 0.5, 0.6, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_3_TICKS;
        long   time     = world.getTime();

        // Shake decays as the reforge settles — from STAGE_3_BASE down toward STAGE_1_BASE
        // by the end. Fires four times per second early, reducing to twice late.
        int shakeInterval = (progress < 0.50) ? SHAKE_STAGE_3_INTERVAL : SHAKE_STAGE_2_INTERVAL;
        if (t % shakeInterval == 0) {
            float intensity = SHAKE_STAGE_3_BASE * (float)(1.0 - progress * 0.65);
            intensity = Math.max(intensity, SHAKE_STAGE_1_BASE);
            CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, intensity);
        }

        // Blindness during the reforge peak — the bond flooding bright
        if (progress < 0.60f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // Levitation for a moment — lifted by the divine force
        if (progress < 0.45f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.LEVITATION, 8, 0, true, false, false));
        }

        // Falling divine column: dense pink and white rain straight down —
        // the full weight of the god's blessing pressing through the bond
        if (t % 2 == 0) {
            int columnCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < columnCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 0.8;
                DustParticleEffect col = world.random.nextBoolean() ? DIVINE_PINK : HOLY_WHITE;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 7.0 + world.random.nextDouble() * 5.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.12, 0.02, 0.004);
            }
        }

        // Rotating halo ring at head height — the new connection made visible,
        // tightening as the bond strengthens (radius shrinks with progress)
        if (t % 2 == 0) {
            int    haloPoints = 20;
            double haloR      = 1.4 - progress * 0.6;
            double haloSpeed  = time * 0.12;
            for (int i = 0; i < haloPoints; i++) {
                double angle = haloSpeed + i * Math.PI * 2.0 / haloPoints;
                DustParticleEffect col = (i % 3 == 0) ? DIVINE_PINK
                        : (i % 3 == 1) ? HOLY_WHITE
                        : BOND_PURPLE;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * haloR,
                        pos.y + 1.85,
                        pos.z + Math.sin(angle) * haloR,
                        1, 0.01, 0.02, 0.01, 0.005);
            }
        }

        // Second inner halo spinning the opposite direction — layered halos
        if (t % 3 == 0) {
            int    innerPoints = 12;
            double innerR      = 0.8 - progress * 0.25;
            double innerSpeed  = -time * 0.09;
            for (int i = 0; i < innerPoints; i++) {
                double angle = innerSpeed + i * Math.PI * 2.0 / innerPoints;
                world.spawnParticles(VIOLET_SOFT,
                        pos.x + Math.cos(angle) * innerR,
                        pos.y + 1.6,
                        pos.z + Math.sin(angle) * innerR,
                        1, 0.01, 0.015, 0.01, 0.004);
            }
        }

        // TOTEM_OF_UNDYING surging upward — a power being renewed, not gained for the
        // first time; the existing connection being elevated and immortalised
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        pos.x + (world.random.nextDouble() - 0.5) * 0.6,
                        pos.y + 0.3 + world.random.nextDouble() * 1.5,
                        pos.z + (world.random.nextDouble() - 0.5) * 0.6,
                        1,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.08 + world.random.nextDouble() * 0.10,
                        (world.random.nextDouble() - 0.5) * 0.04,
                        0.0);
            }
        }

        // Calming: the light softens, the bond settled into the player
        if (progress > 0.72f) {
            world.spawnParticles(BLUSH_PINK,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
            world.spawnParticles(HOLY_WHITE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.004);
        }

        // Enchant stream still radiating — the universe acknowledging the upgrade
        if (t % 10 == 0) {
            world.spawnParticles(ParticleTypes.ENCHANT,
                    pos.x, pos.y + 1.0, pos.z, 5, 0.4, 0.5, 0.4, 1.2);
        }

        if (t % 6 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS,
                    0.5f + (float) progress * 0.4f, 0.7f + (float) progress * 0.8f);
        }
    }

    /* ============================================================
       COMPLETION — bond strengthened, player ascended to Level 2
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        // Apply the upgrade — this is the entire purpose of the ritual
        PowerManager.setLevel(sp, 2);

        // Clean up status effects
        sp.removeStatusEffect(StatusEffects.LEVITATION);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.setVelocity(Vec3d.ZERO);
        sp.velocityModified = true;

        // Heal — the god's blessing restored the cost of the test
        sp.heal(4.0f);

        // Final gentle shake — the connection locking into place with a satisfying thud
        CameraShake.shakeNearby(sp, SHAKE_RADIUS, 8, 0.20f);

        // Completion burst: pink, purple, and white sphere — triumphant
        Vec3d pos = sp.getPos();
        for (int d = 0; d < 32; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi   = world.random.nextDouble() * Math.PI;
            double speed = 0.16;
            DustParticleEffect col = switch (d % 3) {
                case 0  -> DIVINE_PINK;
                case 1  -> HOLY_WHITE;
                default -> BOND_PURPLE;
            };
            world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                    1,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed,
                    0.0);
        }
        world.spawnParticles(DIVINE_PINK,  pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        world.spawnParticles(BOND_PURPLE,  pos.x, pos.y + 1.0, pos.z, 14, 1.1, 1.0, 1.1, 0.08);
        world.spawnParticles(HOLY_WHITE,   pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        world.spawnParticles(VIOLET_SOFT,  pos.x, pos.y + 1.0, pos.z, 10, 0.8, 0.8, 0.8, 0.07);
        world.spawnParticles(BLUSH_PINK,   pos.x, pos.y + 1.0, pos.z,  8, 0.7, 0.7, 0.7, 0.06);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y + 1.0, pos.z,
                16, 1.0, 1.2, 1.0, 0.18);
        world.spawnParticles(ParticleTypes.GLOW, pos.x, pos.y + 1.0, pos.z,
                10, 0.8, 0.6, 0.8, 0);
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.2, pos.z,
                3, 0.2, 0.2, 0.2, 0);

        world.playSound(null, sp.getBlockPos(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 0.8f, 1.4f);

        sp.sendMessage(
                net.minecraft.text.Text.literal("§dYour connection has been strengthened to Level 2."),
                false
        );
    }
}