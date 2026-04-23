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

public class SeveranceRitual implements Ritual {

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int STAGE_1_TICKS = 65;
    private static final int STAGE_2_TICKS = 55;
    private static final int STAGE_3_TICKS = 50;
    private static final int STAGE_4_TICKS = 75;

    private static final int TOTAL_TICKS =
            STAGE_1_TICKS + STAGE_2_TICKS + STAGE_3_TICKS + STAGE_4_TICKS;

    private static final float STAGE_4_DAMAGE_PER_TICK = 0.5f;   // slightly harsher than vestiges
    private static final int   STAGE_4_DAMAGE_INTERVAL = 8;
    private static final float STAGE_4_MIN_HEALTH      = 0.5f;

    /* ============================================================
       PARTICLES
       Visual language: void black, corrupt deep purple, cursed violet,
       clotted crimson, dead ash. Nothing bright or triumphant.
       ============================================================ */

    // Void black — the absence left behind when something is torn away
    private static final DustParticleEffect VOID_BLACK =
            new DustParticleEffect(new Vector3f(0.06f, 0.01f, 0.10f), 1.6f);
    // Corrupt deep purple — the stone's cursed energy; primary colour of the ritual
    private static final DustParticleEffect CORRUPT_PURPLE =
            new DustParticleEffect(new Vector3f(0.35f, 0.00f, 0.52f), 1.5f);
    // Curse violet — lighter highlight where the corruption peaks or flares
    private static final DustParticleEffect CURSE_VIOLET =
            new DustParticleEffect(new Vector3f(0.60f, 0.18f, 0.82f), 1.3f);
    // Clotted dark crimson — the cost of severance paid in blood
    private static final DustParticleEffect CRIMSON_CLOT =
            new DustParticleEffect(new Vector3f(0.28f, 0.01f, 0.03f), 1.5f);
    // Ash white — dead residue of the stripped power
    private static final DustParticleEffect ASH_WHITE =
            new DustParticleEffect(new Vector3f(0.72f, 0.70f, 0.68f), 0.9f);

    /* ============================================================
       same lines as ruin ritual
       ============================================================ */

    private static final double[] TENDRIL_ANGLES = {
            0.0,
            Math.PI * 0.42,
            Math.PI * 0.81,
            Math.PI,
            Math.PI * 1.35,
            Math.PI * 1.78
    };

    /* ============================================================
       STATE
       ============================================================ */

    private final PlayerEntity player;
    private final RitualManager.RitualType type;
    private int ticks = 0;

    public SeveranceRitual(PlayerEntity player, RitualManager.RitualType type) {
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

        lockPosition(sp);

        int stage     = currentStage();
        int stageTick = stageLocalTick();

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
       STAGE 1 — The stone awakens: cursed tendrils crawl inward from
       the dark. The player feels the grip before the tearing starts.
       ============================================================ */

    private void tickStage1(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.PLAYERS, 0.7f, 0.4f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_GRAVEL_PLACE,           SoundCategory.PLAYERS, 0.5f, 0.5f);
        }

        double progress = (double) t / STAGE_1_TICKS;
        Vec3d  pos      = sp.getPos();

        // Six tendrils that START at the outer edge and creep toward the player.
        // The opposite of Ruin's cracks — these are closing in.
        double maxReach  = 5.0;
        double innerEdge = maxReach * (1.0 - progress);  // the inner end crawls to 0 over the stage

        if (t % 2 == 0) {
            for (double angle : TENDRIL_ANGLES) {
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    // Place particles along the tendril from outer edge to inner edge
                    double d = innerEdge + (maxReach - innerEdge) * ((double) s / steps);

                    // Jitter perpendicular to the tendril for a claw-like ragged look
                    double jitter = (world.random.nextDouble() - 0.5) * 0.22;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                    // Outer end is void-black, inner end flares into corrupt purple as it nears the player
                    DustParticleEffect col = (s < 4) ? VOID_BLACK
                            : (s < 7) ? CORRUPT_PURPLE
                            : CURSE_VIOLET;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.02, 0.01, 0.02, 0.002);
                }

                // Claw tip: the leading edge crawling inward flares bright violet
                if (t % 4 == 0 && innerEdge > 0.4) {
                    world.spawnParticles(CURSE_VIOLET,
                            pos.x + Math.cos(angle) * innerEdge,
                            pos.y + 0.12,
                            pos.z + Math.sin(angle) * innerEdge,
                            2, 0.05, 0.08, 0.05, 0.018);
                    // Small upward wisp from each tip — corruption rising from the ground
                    world.spawnParticles(CORRUPT_PURPLE,
                            pos.x + Math.cos(angle) * innerEdge,
                            pos.y + 0.1,
                            pos.z + Math.sin(angle) * innerEdge,
                            1, 0.02, 0.05, 0.02, 0.010);
                }
            }
        }

        // Ash slowly raining down — the world around the player beginning to grey
        if (t % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.8 + world.random.nextDouble() * 4.0;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 2.0 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, -0.008, 0.02, 0.003);
            }
        }

        // A slow pulse of void particles rising from directly beneath the player —
        // the stone calling through the ground
        if (t % 5 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 0.6;
                world.spawnParticles(VOID_BLACK,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05 + world.random.nextDouble() * 1.5 * progress,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.03, 0.01, 0.005);
            }
        }

        // Corrupt purple wisps drifting upward between tendrils — miasma bleeding up
        if (t % 6 == 0) {
            for (int i = 0; i < 2; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 1.0 + world.random.nextDouble() * 2.5;
                world.spawnParticles(CORRUPT_PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.04, 0.01, 0.008);
            }
        }

        // Witch particles sparking near the tendrils — the magic is active and hostile
        if (t % 8 == 0 && progress > 0.3f) {
            double angle = TENDRIL_ANGLES[world.random.nextInt(TENDRIL_ANGLES.length)];
            double r     = innerEdge + world.random.nextDouble() * 2.0;
            world.spawnParticles(ParticleTypes.WITCH,
                    pos.x + Math.cos(angle) * r,
                    pos.y + 0.5 + world.random.nextDouble() * 0.8,
                    pos.z + Math.sin(angle) * r,
                    2, 0.06, 0.06, 0.06, 0.02);
        }

        // Weak wither starts early — the corruption is already touching the player
        if (progress > 0.50f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WITHER, 10, 0, true, false, false));
        }

        if (t % 22 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                    SoundCategory.PLAYERS, 0.5f, 0.35f + (float) progress * 0.2f);
        }
    }

    /* ============================================================
       STAGE 2 — The power surfaces: the bond between player and power
       becomes visible as it is dragged to the surface and corrupted.
       Tendrils now fully grip the player; corruption spreads upward.
       ============================================================ */

    private void tickStage2(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.6f, 0.5f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_SOUL_SAND_HIT,   SoundCategory.PLAYERS, 0.8f, 0.6f);
        }

        double progress = (double) t / STAGE_2_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // Tendrils now reach fully to the player and slowly rotate, like fingers tightening.
        // The inner tips coil around the feet in a slow spiral.
        double coilRotation = time * 0.03;
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                int    steps = 10;
                // Tendril goes from outer (~4.5 blocks) down to coiling at 0.6 blocks.
                // Colour now grades from void at the outer end to bright violet at the inner coil.
                for (int s = 0; s < steps; s++) {
                    double d      = 0.6 + (double) s / steps * 3.9;
                    double jitter = (world.random.nextDouble() - 0.5) * 0.18;
                    double perpX  = Math.cos(angle + Math.PI * 0.5) * jitter;
                    double perpZ  = Math.sin(angle + Math.PI * 0.5) * jitter;

                    DustParticleEffect col = (s < 3) ? VOID_BLACK
                            : (s < 6) ? CORRUPT_PURPLE
                            : CURSE_VIOLET;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * d + perpX,
                            pos.y + 0.05,
                            pos.z + Math.sin(angle) * d + perpZ,
                            1, 0.02, 0.01, 0.02, 0.003);
                }

                // Corruption climbing upward from the coil tips — rising
                // through the legs toward the heart where the power lives
                double riseHeight = 0.5 + progress * 3.5;
                if (t % 3 == 0) {
                    world.spawnParticles(CORRUPT_PURPLE,
                            pos.x + Math.cos(angle) * 0.7,
                            pos.y + world.random.nextDouble() * riseHeight,
                            pos.z + Math.sin(angle) * 0.7,
                            1, 0.02, 0.04, 0.02, 0.008);
                    // Brighter violet at the top of the rising column
                    if (world.random.nextFloat() < 0.4f) {
                        world.spawnParticles(CURSE_VIOLET,
                                pos.x + Math.cos(angle) * 0.55,
                                pos.y + riseHeight,
                                pos.z + Math.sin(angle) * 0.55,
                                1, 0.03, 0.03, 0.03, 0.012);
                    }
                }
            }
        }

        // The power manifesting: warped spores erupting from the player's core —
        // the bond becoming visible as it is forced to the surface.
        // These represent the player's actual power struggling against the corruption.
        if (t % 3 == 0) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.2 + world.random.nextDouble() * 0.9;
                world.spawnParticles(ParticleTypes.WARPED_SPORE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.5 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.05, 0.01, 0.012);
            }
        }

        // Soul fire flickering around the body — the spiritual cost made visible
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 0.8;
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.3 + world.random.nextDouble() * 2.2,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.04, 0.02, 0.01);
            }
        }

        // Corrupt purple cloud pooling around the knees — the miasma thickening
        if (t % 4 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.4 + world.random.nextDouble() * 1.6;
                world.spawnParticles(CORRUPT_PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.3 + world.random.nextDouble() * 0.8,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.02, 0.01, 0.005);
            }
        }

        // Ash dense now — the environment fully affected
        if (t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 5.0;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 1.5 + world.random.nextDouble() * 3.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.01, 0.01, 0.003);
            }
        }

        // Witch particles sparking more frequently now — hostile magic fully active
        if (t % 5 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.6,
                    pos.y + 0.5 + world.random.nextDouble() * 1.5,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.6,
                    3, 0.08, 0.08, 0.08, 0.025);
        }

        // Wither continuous from here
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WITHER, 15, 0, true, false, false));

        // Weakness creeping in — the power is being destabilised
        if (progress > 0.4f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WEAKNESS, 20, 0, true, false, false));
        }

        if (t == 20) world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_WITHER_SHOOT,          SoundCategory.PLAYERS, 0.4f, 0.6f);
        if (t == 42) world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK,  SoundCategory.PLAYERS, 0.5f, 0.55f);
    }

    /* ============================================================
       STAGE 3 — The tearing begins: the bond between player and power
       is visibly strained. Particles representing the power are PULLED
       outward and upward — ripped from the player's core.
       ============================================================ */

    private void tickStage3(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.8f, 0.7f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_CHAIN_BREAK,        SoundCategory.PLAYERS, 0.9f, 0.7f);
        }

        double progress = (double) t / STAGE_3_TICKS;
        Vec3d  pos      = sp.getPos();
        long   time     = world.getTime();

        // Outward explosion of power fragments: particles fired outward from the player's
        // chest with directional velocity — the exact reverse of any ritual's implosion pull.
        // These are the power being torn free in ragged chunks.
        if (t % 2 == 0) {
            int burstCount = (int)(8 + progress * 14);
            for (int i = 0; i < burstCount; i++) {
                double theta = world.random.nextDouble() * Math.PI * 2;
                double phi   = world.random.nextDouble() * Math.PI * 0.6;   // mostly horizontal/upward
                double speed = 0.08 + progress * 0.07;

                DustParticleEffect col = switch (i % 5) {
                    case 0  -> VOID_BLACK;
                    case 1  -> CORRUPT_PURPLE;
                    case 2  -> CURSE_VIOLET;
                    case 3  -> CRIMSON_CLOT;
                    default -> ASH_WHITE;
                };
                world.spawnParticles(col,
                        pos.x, pos.y + 1.1, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed * 0.6 + 0.03,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }
        }

        // Tendril coil still tightening — the stone not releasing, even as it tears
        double coilRotation = time * 0.05;   // faster than stage 2
        if (t % 2 == 0) {
            for (double baseAngle : TENDRIL_ANGLES) {
                double angle = baseAngle + coilRotation;
                // Coil radius tightens as the power is pulled out
                double r = 0.65 * (1.0 - progress * 0.35);
                if (r > 0.2) {
                    DustParticleEffect col = (t % 4 == 0) ? CURSE_VIOLET : VOID_BLACK;
                    world.spawnParticles(col,
                            pos.x + Math.cos(angle) * r,
                            pos.y + 0.3,
                            pos.z + Math.sin(angle) * r,
                            1, 0.02, 0.02, 0.02, 0.006);
                }
            }
        }

        // Crimson clot particles pooling at the feet — the blood price rising
        if (t % 3 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.8;
                world.spawnParticles(CRIMSON_CLOT,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.05,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.01, 0.01, 0.003);
            }
        }

        // Soul fire intensifies — the bond burning as it breaks
        if (t % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 1.0;
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 2.5,
                        pos.z + Math.sin(angle) * r,
                        1, 0.02, 0.05, 0.02, 0.016);
            }
        }

        // Purple corona — corrupt violet halo forming around the player's torso
        // as the power is drawn to the surface just before the snap
        if (t % 3 == 0) {
            int    coronaPoints = 12;
            double coronaR      = 0.9 - progress * 0.2;
            for (int i = 0; i < coronaPoints; i++) {
                double angle = world.getTime() * 0.08 + i * Math.PI * 2.0 / coronaPoints;
                world.spawnParticles(CURSE_VIOLET,
                        pos.x + Math.cos(angle) * coronaR,
                        pos.y + 1.1,
                        pos.z + Math.sin(angle) * coronaR,
                        1, 0.02, 0.03, 0.02, 0.007);
            }
        }

        // Witch sparks at their densest — the ritual is at peak intensity
        if (t % 3 == 0) {
            world.spawnParticles(ParticleTypes.WITCH,
                    pos.x + (world.random.nextDouble() - 0.5) * 1.4,
                    pos.y + 0.5 + world.random.nextDouble() * 2.0,
                    pos.z + (world.random.nextDouble() - 0.5) * 1.4,
                    4, 0.10, 0.10, 0.10, 0.03);
        }

        // Nauseated — the self is destabilising
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.NAUSEA, 30, 0, true, false, false));

        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WITHER, 10, 1, true, false, false));   // wither intensifies

        if (t % 14 == 0) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS,
                    (float)(0.5 + progress * 0.4), 0.55f + (float) progress * 0.35f);
        }
        if (t == 28) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.0f, 0.6f);
        }
    }

    /* ============================================================
       STAGE 4 — The severing: the bond snaps completely.
       The power erupts outward in a violent burst and vanishes.
       What remains is the void — darkness, silence, and ash.
       ============================================================ */

    private void tickStage4(ServerPlayerEntity sp, ServerWorld world, int t) {
        if (t == 1) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_SONIC_BOOM,  SoundCategory.PLAYERS, 1.1f, 0.5f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.BLOCK_CHAIN_BREAK,         SoundCategory.PLAYERS, 1.0f, 0.5f);
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 0.7f);

            // The snap: a violent outward burst in all directions — the power being
            // expelled from the body. Unlike any vestige ritual's opening burst,
            // this uses ONLY void, purple, and crimson — no colours, no cosmic elements.
            Vec3d pos = sp.getPos();
            for (int d = 0; d < 40; d++) {
                double theta = d * Math.PI * 2.0 / 40;
                double phi   = Math.PI * 0.35 + world.random.nextDouble() * Math.PI * 0.3;
                double speed = 0.22 + world.random.nextDouble() * 0.14;
                DustParticleEffect col = switch (d % 4) {
                    case 0  -> CORRUPT_PURPLE;
                    case 1  -> VOID_BLACK;
                    case 2  -> CRIMSON_CLOT;
                    default -> CURSE_VIOLET;
                };
                world.spawnParticles(col, pos.x, pos.y + 1.1, pos.z,
                        1,
                        Math.sin(phi) * Math.cos(theta) * speed,
                        Math.cos(phi) * speed,
                        Math.sin(phi) * Math.sin(theta) * speed,
                        0.0);
            }

            // One large soul fire burst at the moment of snapping
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1.0, pos.z, 24, 1.1, 0.9, 1.1, 0.14);

            // Witch particle explosion — the hostile magic releasing all at once
            world.spawnParticles(ParticleTypes.WITCH,
                    pos.x, pos.y + 1.0, pos.z, 16, 0.8, 0.6, 0.8, 0.10);

            // Ash explosion
            world.spawnParticles(ParticleTypes.ASH,
                    pos.x, pos.y + 1.0, pos.z, 35, 1.3, 0.9, 1.3, 0.11);

            // Corrupt purple shockwave ring fired outward at ground level
            int ringPoints = 18;
            for (int i = 0; i < ringPoints; i++) {
                double angle = i * Math.PI * 2.0 / ringPoints;
                double speed = 0.28;
                world.spawnParticles(CORRUPT_PURPLE,
                        pos.x + Math.cos(angle) * 0.4, pos.y + 0.2, pos.z + Math.sin(angle) * 0.4,
                        1, Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed, 0.0);
            }
        }

        Vec3d  pos      = sp.getPos();
        double progress = (double) t / STAGE_4_TICKS;

        // Blindness — vision lost as the power that may have sustained it is gone
        if (progress > 0.10f && progress < 0.80f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.BLINDNESS, 15, 0, true, false, false));
        }

        // Darkness — deeper than blindness, the void filling the space where the power was
        if (progress > 0.05f && progress < 0.70f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.DARKNESS, 15, 0, true, false, false));
        }

        // Wither fading — the stone's grip is complete; there is nothing left to consume
        if (progress < 0.55f) {
            sp.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.WITHER, 10, 0, true, false, false));
        }

        // Weakness persists — they are hollow now
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 20, 1, true, false, false));

        // Damage (no kill)
        if (t % STAGE_4_DAMAGE_INTERVAL == 0) {
            float health = sp.getHealth();
            if (health > STAGE_4_MIN_HEALTH + STAGE_4_DAMAGE_PER_TICK) {
                sp.damage(world.getDamageSources().magic(), STAGE_4_DAMAGE_PER_TICK);
            }
        }

        // Void implosion: after the initial burst, the wound collapses inward —
        // a secondary pull as the void rushes in to fill the space the power left.
        // Distinctly INWARD after the initial OUTWARD snap.
        if (t > 8 && t % 2 == 0) {
            int pullCount = (int)(10 + (1.0 - progress) * 14);
            for (int i = 0; i < pullCount; i++) {
                double theta  = world.random.nextDouble() * Math.PI * 2;
                double phi    = world.random.nextDouble() * Math.PI;
                double srcR   = 2.5 + world.random.nextDouble() * 4.5;
                double fromX  = pos.x + Math.sin(phi) * Math.cos(theta) * srcR;
                double fromY  = pos.y + 1.0 + Math.cos(phi) * srcR * 0.4;
                double fromZ  = pos.z + Math.sin(phi) * Math.sin(theta) * srcR;
                Vec3d  toward = pos.add(0, 1, 0)
                        .subtract(fromX, fromY, fromZ)
                        .normalize()
                        .multiply(0.10 + (1.0 - progress) * 0.06);

                DustParticleEffect col = switch (i % 4) {
                    case 0  -> VOID_BLACK;
                    case 1  -> ASH_WHITE;
                    case 2  -> CORRUPT_PURPLE;
                    default -> CRIMSON_CLOT;
                };
                world.spawnParticles(col, fromX, fromY, fromZ,
                        1, toward.x, toward.y, toward.z, 0.008);
            }
        }

        // Continuous soul fire dying — the flame guttering out
        if (progress < 0.65f && t % 3 == 0) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r     = world.random.nextDouble() * 0.7;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + Math.cos(angle) * r,
                    pos.y + 0.2 + world.random.nextDouble() * 2.0,
                    pos.z + Math.sin(angle) * r,
                    1, 0.01, 0.03, 0.01, 0.008);
        }

        // Corrupt purple miasma — thick and choking, at its peak in the middle of the stage
        if (progress > 0.15f && progress < 0.65f && t % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = 0.3 + world.random.nextDouble() * 1.2;
                world.spawnParticles(CORRUPT_PURPLE,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.2 + world.random.nextDouble() * 2.2,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, 0.02, 0.01, 0.006);
            }
        }

        // Ash settling — thickest in the middle of the stage, then thinning
        if (t % 3 == 0 && progress < 0.75f) {
            for (int i = 0; i < 5; i++) {
                double angle = world.random.nextDouble() * Math.PI * 2;
                double r     = world.random.nextDouble() * 4.5;
                world.spawnParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 3.0 + world.random.nextDouble() * 2.0,
                        pos.z + Math.sin(angle) * r,
                        1, 0.01, -0.015, 0.01, 0.003);
            }
        }

        // The silence settling: void black, corrupt purple, and ash white drifting very slowly —
        // the absence where the power was
        if (progress > 0.72f) {
            world.spawnParticles(VOID_BLACK,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.4, 0.3, 0.4, 0.003);
            world.spawnParticles(ASH_WHITE,
                    pos.x, pos.y + 0.8, pos.z, 1, 0.3, 0.2, 0.3, 0.002);
            world.spawnParticles(CORRUPT_PURPLE,
                    pos.x, pos.y + 1.0, pos.z, 1, 0.3, 0.3, 0.3, 0.002);
        }

        if (t % 8 == 0 && progress < 0.60f) {
            world.playSound(null, sp.getBlockPos(),
                    SoundEvents.ENTITY_WITHER_AMBIENT,
                    SoundCategory.PLAYERS,
                    (float)(0.4 + (1.0 - progress) * 0.3), 0.45f + (float) progress * 0.3f);
        }
    }

    /* ============================================================
       COMPLETION — power removed, player left hollow
       ============================================================ */

    private void onComplete(ServerPlayerEntity sp, ServerWorld world) {
        // Remove the power — this is the purpose of the entire ritual
        PowerManager.removePower(sp);

        // Consume the item now that the ritual has completed
        if (!sp.getAbilities().creativeMode) {
            // Consume from whichever hand the stone is in
            for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
                var stack = sp.getStackInHand(hand);
                if (!stack.isEmpty() && stack.getItem() instanceof com.yourname.loopypowers.item.SeveranceStoneItem) {
                    stack.decrement(1);
                    break;
                }
            }
        }

        // Clean up status effects
        sp.removeStatusEffect(StatusEffects.RESISTANCE);
        sp.removeStatusEffect(StatusEffects.SLOWNESS);
        sp.removeStatusEffect(StatusEffects.BLINDNESS);
        sp.removeStatusEffect(StatusEffects.DARKNESS);
        sp.removeStatusEffect(StatusEffects.NAUSEA);
        sp.removeStatusEffect(StatusEffects.WITHER);

        // Weakness lingers after — the player is still hollow, still paying the price
        sp.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 200, 0, false, true, true));

        // Minimal, quiet completion. No flash, no triumph.
        // Just void, purple, and silence.
        Vec3d pos = sp.getPos();
        world.spawnParticles(VOID_BLACK,     pos.x, pos.y + 1.0, pos.z, 20, 1.3, 1.0, 1.3, 0.07);
        world.spawnParticles(CORRUPT_PURPLE, pos.x, pos.y + 1.0, pos.z, 16, 1.1, 0.9, 1.1, 0.06);
        world.spawnParticles(CURSE_VIOLET,   pos.x, pos.y + 1.0, pos.z, 10, 0.9, 0.8, 0.9, 0.05);
        world.spawnParticles(ASH_WHITE,      pos.x, pos.y + 1.0, pos.z, 12, 1.0, 0.8, 1.0, 0.05);
        world.spawnParticles(ParticleTypes.ASH, pos.x, pos.y + 1.0, pos.z,
                22, 1.5, 1.0, 1.5, 0.06);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0, pos.z,
                8, 0.6, 0.5, 0.6, 0.05);
        world.spawnParticles(ParticleTypes.WITCH, pos.x, pos.y + 1.0, pos.z,
                6, 0.5, 0.4, 0.5, 0.06);

        // No challenge toast — this is not an achievement.
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.ENTITY_WITHER_DEATH,
                SoundCategory.PLAYERS, 0.8f, 1.2f);
        world.playSound(null, sp.getBlockPos(),
                SoundEvents.BLOCK_SOUL_SAND_HIT,
                SoundCategory.PLAYERS, 0.6f, 0.5f);

        sp.sendMessage(net.minecraft.text.Text.literal("§8Your power has been stripped..."), true);
    }
}