package com.yourname.loopypowers.ritual;

import com.yourname.loopypowers.manager.PowerManager;
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

    /* ============================================================
       PARTICLES
       Visual language: blinding divine gold, radiant white, warm amber,
       and a hint of the player's own power-light returning stronger.
       No darkness, no corruption. Pure overwhelming presence.
       ============================================================ */

    // Divine gold — the god's raw presence
    private static final DustParticleEffect DIVINE_GOLD =
            new DustParticleEffect(new Vector3f(1.00f, 0.82f, 0.10f), 1.5f);
    // Radiant pale gold — outer halo and ambient glow
    private static final DustParticleEffect GOLD_PALE =
            new DustParticleEffect(new Vector3f(1.00f, 0.95f, 0.60f), 1.2f);
    // Holy white — the pure centre of the connection
    private static final DustParticleEffect HOLY_WHITE =
            new DustParticleEffect(new Vector3f(0.98f, 0.98f, 1.00f), 1.3f);
    // Warm amber — the heat of divine power
    private static final DustParticleEffect AMBER =
            new DustParticleEffect(new Vector3f(1.00f, 0.55f, 0.05f), 1.4f);
    // Ascension blue-white — the player's own power resonating upward
    private static final DustParticleEffect ASCEND_BLUE =
            new DustParticleEffect(new Vector3f(0.80f, 0.92f, 1.00f), 1.1f);

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
       Golden light falls from above; the ground beneath the player
       ignites with divine sigils. The god is turning its attention
       toward this mortal.
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
        long   time     = world.getTime();

        // Gold light falling from directly above — a column of divine attention
        // descending onto the player. Particles spawn high and drift down slowly.
        if (t % 2 == 0) {
            int columnCount = (int)(4 + progress * 8);
            for (int i = 0; i < columnCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.0;
                double h     = 8.0 + world.random.nextDouble() * 6.0;  // spawn high above
                DustParticleEffect col = world.random.nextBoolean() ? DIVINE_GOLD : GOLD_PALE;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * r,
                        pos.y + h,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.06, 0.02, 0.005);   // negative y = falling down
            }
        }

        // Expanding divine ring on the ground — the god's footprint forming
        // A new ring pulse every 15 ticks, each one racing outward
        int pulseTimer  = t % 15;
        double ringR    = pulseTimer * 0.32;
        if (ringR > 0.3 && t % 2 == 0) {
            int ringPoints = 16;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                DustParticleEffect col = (pulseTimer < 8) ? DIVINE_GOLD : GOLD_PALE;
                world.spawnParticles(col,
                        pos.x + Math.cos(angle) * ringR,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * ringR,
                        1, 0.02, 0.02, 0.02, 0.004);
            }
        }

        // Slow ambient gold wisps rising from near the player — their own power
        // beginning to stir in response to the divine attention
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 1.2;
                world.spawnParticles(GOLD_PALE,
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

        // Glowstone dust sparks from the ground — divine heat touching the earth
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
       inspected and judged. The player is momentarily blinded by
       proximity to divine presence.
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

        // Power streams rising: the player's connection being pulled upward for inspection.
        // Six streams, each mapping to a different facet of their power, rising steeply.
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
                        case 0  -> DIVINE_GOLD;
                        case 1  -> HOLY_WHITE;
                        default -> AMBER;
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

        // Falling gold rain intensifies — divine scrutiny pressing down
        if (t % 2 == 0) {
            int rainCount = (int)(6 + progress * 10);
            for (int i = 0; i < rainCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                world.spawnParticles(world.random.nextBoolean() ? DIVINE_GOLD : GOLD_PALE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 6.0 + world.random.nextDouble() * 4.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.08, 0.01, 0.003);
            }
        }

        // Flash of GLOW particles — the test flaring bright at intervals
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
       glorious, painful in its intensity. A supernova of golden light
       erupts from the player as the Level 2 connection locks in.
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.7f, 0.8f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_BEACON_POWER_SELECT,     SoundCategory.PLAYERS, 1.0f, 0.9f);

            // Opening reforge burst: a sphere of divine gold and white fired outward —
            // the god's power flooding into the player all at once
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 32; d++) {
                double theta = d * Math.PI * 2.0 / 32;
                double phi   = Math.PI * 0.3 + world.random.nextDouble() * Math.PI * 0.4;
                double speed = 0.20 + world.random.nextDouble() * 0.12;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> DIVINE_GOLD;
                    case 1  -> HOLY_WHITE;
                    case 2  -> GOLD_PALE;
                    default -> AMBER;
                };
                world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
            world.spawnParticles(ParticleTypes.FLASH,
                    pos.x, pos.y + 1.0, pos.z, 4, 0.3, 0.3, 0.3, 0);
            world.spawnParticles(ParticleTypes.GLOW,
                    pos.x, pos.y + 1.0, pos.z, 12, 0.6, 0.5, 0.6, 0);
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_3_TICKS;
        long   time     = world.getTime();

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

        // Falling divine column: dense gold rain straight down onto the player —
        // the full weight of the god's blessing pressing through the bond
        if (t % 2 == 0) {
            int columnCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < columnCount; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 0.8;
                world.spawnParticles(world.random.nextBoolean() ? DIVINE_GOLD : HOLY_WHITE,
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
                DustParticleEffect col = (i % 3 == 0) ? DIVINE_GOLD
                        : (i % 3 == 1) ? HOLY_WHITE
                        : GOLD_PALE;
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
            double innerR      = (0.8 - progress * 0.25);
            double innerSpeed  = -time * 0.09;
            for (int i = 0; i < innerPoints; i++) {
                double angle = innerSpeed + i * Math.PI * 2.0 / innerPoints;
                world.spawnParticles(AMBER,
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
            world.spawnParticles(GOLD_PALE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.5, 0.4, 0.5, 0.005);
            world.spawnParticles(ASCEND_BLUE,
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

        // Completion burst: radiant gold and white sphere, triumphant
        Vec3d pos = sp.getPos();
        for (int d = 0; d < 28; d++) {
            double theta = world.random.nextDouble() * Math.PI * 2;
            double phi   = world.random.nextDouble() * Math.PI;
            double speed = 0.16;
            DustParticleEffect col = (d % 2 == 0) ? DIVINE_GOLD : HOLY_WHITE;
            world.spawnParticles(col, pos.x, pos.y + 1.0, pos.z,
                    1,
                    Math.sin(phi) * Math.cos(theta) * speed,
                    Math.cos(phi) * speed,
                    Math.sin(phi) * Math.sin(theta) * speed,
                    0.0);
        }
        world.spawnParticles(DIVINE_GOLD,  pos.x, pos.y + 1.0, pos.z, 18, 1.3, 1.1, 1.3, 0.09);
        world.spawnParticles(GOLD_PALE,    pos.x, pos.y + 1.0, pos.z, 14, 1.1, 1.0, 1.1, 0.08);
        world.spawnParticles(HOLY_WHITE,   pos.x, pos.y + 1.0, pos.z, 12, 0.9, 0.8, 0.9, 0.07);
        world.spawnParticles(ASCEND_BLUE,  pos.x, pos.y + 1.0, pos.z,  8, 0.7, 0.7, 0.7, 0.06);
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
                net.minecraft.text.Text.literal("§6Your connection has been strengthened to Level 2."),
                false
        );
    }
}