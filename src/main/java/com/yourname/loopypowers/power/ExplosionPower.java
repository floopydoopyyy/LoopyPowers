package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import com.yourname.loopypowers.damage.ModDamageTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ExplosionPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, ExplosionState> ACTIVE_STATES = new HashMap<>();

    private static class ExplosionState {
        int ignitingTicks = 0;

        int blastCharges = BLAST_MAX_CHARGES;
        int blastRechargeTicks = 0;
        int blastLockTicks = 0;

        int noSelfExpTicks = 0;

        int ultActiveTicks = 0;
        int ultWarnTicks = -1;
        int ultAirTicks = 0;
        int ultStage = 0;
        boolean ultWaitingLand = false;

        int ultLaunchDelayTicks = 0;
        double launchX = 0;
        double launchY = 0;
        double launchZ = 0;
    }

    private static ExplosionState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new ExplosionState());
    }

    /* ============================================================
       TUNING
       ============================================================ */

    // Primary: ignition
    private static final int IGNITE_FUSE_TICKS = 20 * 5; // 5s charge
    private static final float IGNITE_POWER = 4.5f;      // explosion strength
    private static final int IGNITE_SPEED_AMP = 1;       // speed amp
    private static final boolean IGNITE_BREAK_BLOCKS = true;

    // Secondary: blast
    private static final int BLAST_MAX_CHARGES = 2;
    private static final int BLAST_RECHARGE_TICKS = 220; // cooldown per charge
    private static final int BLAST_LOCK_TICKS = 4;
    private static final float BLAST_POWER = 2.6f;       // explosion strength
    private static final boolean BLAST_BREAK_BLOCKS = true;
    private static final double BLAST_SPAWN_DIST = 1.2;
    private static final double BLAST_SPAWN_DOWN = 0.10;

    // Recoil movement tuning
    private static final double RECOIL_STRENGTH = 1.35;   // overall push
    private static final double RECOIL_UP_BONUS = 0.55;   // extra upward help
    private static final double RECOIL_MAX_Y = 1.10;      // cap vertical
    private static final double RECOIL_MAX_H = 1.85;      // cap horizontal

    // Ultimate: Chain Reaction
    private static final int ULT_POPS_ACTUAL = 3;
    private static final int ULT_WARN_TICKS = 8;
    private static final int ULT_FIZZLE_AIR_TICKS = 500;

    private static final double ULT_AIR_STEER = 0.18;
    private static final double ULT_POP_LAUNCH_Y = 2.70;
    private static final double ULT_FINAL_LAUNCH_Y = 1.10;

    private static final int ULT_TOTAL_TICKS = 1000;
    private static final float ULT_POP_POWER = 3.2f;
    private static final boolean ULT_POP_BREAK_BLOCKS = true;
    private static final float ULT_FINAL_POWER = 8.4f;
    private static final boolean ULT_FINAL_BREAK_BLOCKS = true;
    private static final int ULT_CHARGE_SLOWNESS_AMP = 4;
    private static final int ULT_CHARGE_REFRESH_TICKS = 10;

    // DAMAGE AND RADII
    private static final float IGNITE_DAMAGE = 23.5f;
    private static final double IGNITE_DMG_RADIUS = 5.5;

    private static final float BLAST_DAMAGE = 21.0f;
    private static final double BLAST_DMG_RADIUS = 3.0;

    private static final float ULT_POP_DAMAGE = 22.5f;
    private static final double ULT_POP_DMG_RADIUS = 4.5;
    private static final float ULT_FINAL_DAMAGE = 25.0f;
    private static final double ULT_FINAL_DMG_RADIUS = 6.5;

    private static final int ULT_LAUNCH_DELAY_TICKS = 2;
    private static final double ULT_LAUNCH_KICK_Y = 0.12;

    // funnies
    private static final float GLASS_CONVERT_CHANCE = 0.07f; // silly glass
    private static final float WHY_SOUND_CHANCE = 0.005f; // i regret this

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("ex_")); // clean legacy string tags
        ACTIVE_STATES.put(player.getUuid(), new ExplosionState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("ex_"));
        ACTIVE_STATES.remove(player.getUuid());

        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
        player.removeStatusEffect(ModEffects.BRACED);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        ExplosionState state = getState(player);

        if (state.blastLockTicks > 0) state.blastLockTicks--;
        if (state.noSelfExpTicks > 0) state.noSelfExpTicks--;

        tickBlastRecharge(player, state);
        updateBlastCooldownUI(player, state);

        if (state.ignitingTicks > 0) {
            tickIgnition(player, state);
        }

        if (state.ultActiveTicks > 0) {
            tickUltimate(player, state);
        }

        if (state.ultLaunchDelayTicks > 0) {
            tickPendingLaunch(player, state);
        }
    }

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        ExplosionState state = getState(victim);

        // Force early ultimate detonation if hit hard while airborne in ult
        if (!victim.isOnGround() && state.ultActiveTicks > 0) {
            if (source.getAttacker() instanceof LivingEntity && amount >= 3.0f) {
                if (victim.getWorld() instanceof ServerWorld sw) {
                    forceEarlyDetonation(victim, state, sw);
                }
            }
        }

        // Immunity to own explosion damage
        if (state.noSelfExpTicks > 0 && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) {
            return false;
        }

        return true;
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ExplosionState state = getState(player);
        state.ignitingTicks = IGNITE_FUSE_TICKS;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_TNT_PRIMED,
                player.getSoundCategory(),
                1.0f, 1.0f);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.35, 0.35, 0.02);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickIgnition(ServerPlayerEntity player, ExplosionState state) {
        state.ignitingTicks--;
        ServerWorld w = player.getServerWorld();

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, IGNITE_SPEED_AMP, true, false));

        float progress = 1.0f - (state.ignitingTicks / (float) IGNITE_FUSE_TICKS);
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        int interval = MathHelper.clamp((int) MathHelper.lerp(progress, 6.0f, 1.0f), 1, 6);

        if (w.getTime() % interval == 0) {
            int smokeCount = 2 + (int)(progress * 10.0f);
            w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                    player.getX(), player.getY() + 0.9, player.getZ(),
                    smokeCount,
                    0.25, 0.25, 0.25,
                    0.01);

            if (progress > 0.55f) {
                int flameCount = 1 + (int)((progress - 0.55f) * 10.0f);
                w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                        player.getX(), player.getY() + 0.9, player.getZ(),
                        flameCount,
                        0.18, 0.22, 0.18,
                        0.005);
            }

            if (state.ignitingTicks <= 30) {
                w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        2,
                        0.20, 0.25, 0.20,
                        0.01);
            }
        }

        if (w.getTime() % 20 == 0) {
            float pitch = 0.9f + 0.35f * progress;
            w.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_TNT_PRIMED,
                    player.getSoundCategory(),
                    0.55f,
                    pitch);
        }

        if (state.ignitingTicks > 0) return;

        state.noSelfExpTicks = 6;

        Vec3d center = player.getPos().add(0, 0.1, 0);

        explodeAt(w, center, IGNITE_POWER, IGNITE_BREAK_BLOCKS);
        applyExplosionDamage(player, w, center, IGNITE_DMG_RADIUS, IGNITE_DAMAGE, false, false);

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.65), v.z);
        player.velocityModified = true;
        player.fallDistance = 0.0f;

        CameraShake.shakeNearby(player, 9.0, 16, 1.4f);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ExplosionState state = getState(player);
        if (state.blastLockTicks > 0) return;
        if (state.blastCharges <= 0) return;

        state.blastCharges--;
        state.blastLockTicks = BLAST_LOCK_TICKS;

        if (state.blastRechargeTicks <= 0) {
            state.blastRechargeTicks = BLAST_RECHARGE_TICKS;
        }

        ServerWorld w = player.getServerWorld();

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d origin = player.getEyePos()
                .add(look.multiply(BLAST_SPAWN_DIST))
                .add(0.0, -BLAST_SPAWN_DOWN, 0.0);

        state.noSelfExpTicks = 6;

        explodeAt(w, origin, BLAST_POWER, BLAST_BREAK_BLOCKS);
        applyExplosionDamage(player, w, origin, BLAST_DMG_RADIUS, BLAST_DAMAGE, false, true);

        applyRecoil(player, origin);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(),
                0.9f, 1.15f);

        CameraShake.shakeNearby(player, 8.0, 8, 0.95f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBlastRecharge(ServerPlayerEntity player, ExplosionState state) {
        if (state.blastCharges >= BLAST_MAX_CHARGES) {
            state.blastRechargeTicks = 0;
            return;
        }

        if (state.blastRechargeTicks <= 0) {
            state.blastRechargeTicks = BLAST_RECHARGE_TICKS;
        }

        state.blastRechargeTicks--;

        if (state.blastRechargeTicks == 0) {
            state.blastCharges++;
            if (state.blastCharges < BLAST_MAX_CHARGES) {
                state.blastRechargeTicks = BLAST_RECHARGE_TICKS;
            }
        }
    }

    private static void applyRecoil(ServerPlayerEntity player, Vec3d explosionOrigin) {
        Vec3d toPlayer = player.getPos().add(0, 0.9, 0).subtract(explosionOrigin);
        Vec3d horiz = new Vec3d(toPlayer.x, 0.0, toPlayer.z);

        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
        horiz = horiz.normalize();

        double pushH = RECOIL_STRENGTH;
        double pushY = RECOIL_UP_BONUS;

        Vec3d look = player.getRotationVec(1.0f);
        if (look.y < -0.35) pushY += 0.20;
        if (look.y > 0.35)  pushH += 0.10;

        Vec3d v = player.getVelocity();
        double nx = v.x + horiz.x * pushH;
        double nz = v.z + horiz.z * pushH;
        double ny = Math.max(v.y, 0.0) + pushY;

        Vec3d hv = new Vec3d(nx, 0.0, nz);
        double hLen = hv.length();
        if (hLen > RECOIL_MAX_H) {
            Vec3d hN = hv.normalize().multiply(RECOIL_MAX_H);
            nx = hN.x;
            nz = hN.z;
        }
        ny = Math.min(ny, RECOIL_MAX_Y);

        player.setVelocity(nx, ny, nz);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
    }

    private static void updateBlastCooldownUI(ServerPlayerEntity player, ExplosionState state) {
        String key = "ExplosionUI:SECONDARY";

        if (state.blastCharges >= BLAST_MAX_CHARGES) {
            com.yourname.loopypowers.CooldownUI.clearCooldown(player, key);
            return;
        }

        long endMs = System.currentTimeMillis() + (state.blastRechargeTicks * 50L);
        String suffix = com.yourname.loopypowers.CooldownUI.makeChargeSuffix(
                state.blastCharges, BLAST_MAX_CHARGES, state.blastRechargeTicks, BLAST_RECHARGE_TICKS
        );

        com.yourname.loopypowers.CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    /* ============================================================
   ULTIMATE
   ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ExplosionState state = getState(player);

        state.ultActiveTicks = ULT_TOTAL_TICKS;
        state.ultAirTicks = ULT_FIZZLE_AIR_TICKS;
        state.ultStage = 0;
        state.ultWarnTicks = -1;

        if (!player.isOnGround()) {
            state.ultWaitingLand = true;
        }

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_TNT_PRIMED,
                player.getSoundCategory(),
                1.0f, 0.8f);
        CameraShake.shakeNearby(player, 10.0, 12, 1.1f);
    }

    private static void tickUltimate(ServerPlayerEntity player, ExplosionState state) {
        ServerWorld w = player.getServerWorld();
        state.ultActiveTicks--;

        tickUltAmbientFx(player, w);

        // Render drop zone indicator while airborne
        if (!player.isOnGround()) {
            tickUltDropZoneIndicator(player, w, state.ultStage);

            if (state.ultAirTicks > 0) {
                state.ultAirTicks--;
                if (state.ultAirTicks == 0) {
                    cancelUltimate(player, state, w);
                }
            }
            return;
        }

        state.ultAirTicks = ULT_FIZZLE_AIR_TICKS;
        state.ultWaitingLand = false;

        if (state.ultWarnTicks >= 0) {
            tickUltFinisherChargeFx(player, w, state.ultWarnTicks);

            if (state.ultWarnTicks == 0) {
                state.ultWarnTicks = -1; // reset
                state.ultActiveTicks = 0; // End ult
                doUltPop(player, state, true, 3);
            } else {
                state.ultWarnTicks--;
            }
            return;
        }

        if (state.ultStage < ULT_POPS_ACTUAL) {
            doUltPop(player, state, false, state.ultStage);
            state.ultStage++;

            if (state.ultStage >= ULT_POPS_ACTUAL) {
                state.ultWarnTicks = ULT_WARN_TICKS;
            }
            return;
        }

        state.ultWarnTicks = ULT_WARN_TICKS;
    }

    public static void cancelUltimate(ServerPlayerEntity player, ExplosionState state, ServerWorld w) {
        state.ultActiveTicks = 0;
        state.ultWarnTicks = -1;
        state.ultStage = 0;
        state.ultWaitingLand = false;
        state.ultLaunchDelayTicks = 0;

        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_FIRE_EXTINGUISH,
                player.getSoundCategory(),
                0.9f, 1.2f);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.35, 0.25, 0.35, 0.01);

        // Stun them briefly to punish the fizzle
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 40, 5, true, false
        ));
    }

    public static void forceEarlyDetonation(ServerPlayerEntity player, ExplosionState state, ServerWorld w) {
        if (state.ultAirTicks <= 0) return;

        state.ultAirTicks = 0;
        state.ultWaitingLand = false;

        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, player.getSoundCategory(), 1.0f, 1.5f);

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 0.5, v.y * 0.2, v.z * 0.5);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void tickUltDropZoneIndicator(ServerPlayerEntity player, ServerWorld w, int ultStage) {
        Vec3d start = player.getPos();
        Vec3d end = start.subtract(0, 100, 0);

        HitResult hit = w.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
        ));

        if (hit.getType() == HitResult.Type.MISS) return;

        Vec3d groundPos = hit.getPos();

        double radius = (ultStage >= ULT_POPS_ACTUAL) ? ULT_FINAL_DMG_RADIUS : ULT_POP_DMG_RADIUS;

        int points = 24;
        double time = (w.getTime() % 20) / 20.0;
        double offsetAngle = time * Math.PI * 2;

        for (int i = 0; i < points; i++) {
            double angle = offsetAngle + (2 * Math.PI * i) / points;
            double x = groundPos.x + Math.cos(angle) * radius;
            double z = groundPos.z + Math.sin(angle) * radius;

            w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    x, groundPos.y + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    private static void doUltPop(ServerPlayerEntity player, ExplosionState state, boolean finisher, int popIndex) {
        ServerWorld w = player.getServerWorld();

        Vec3d pre = player.getVelocity();

        state.noSelfExpTicks = finisher ? 8 : 6;

        player.addStatusEffect(new StatusEffectInstance(ModEffects.BRACED, 200, 0, false, false, true));

        state.ultWaitingLand = true;

        Vec3d origin = player.getPos().add(0, finisher ? 0.1 : 0.2, 0);

        if (finisher) {
            explodeAt(w, origin, ULT_FINAL_POWER, ULT_FINAL_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_FINAL_DMG_RADIUS, ULT_FINAL_DAMAGE, true, false);
        } else {
            explodeAt(w, origin, ULT_POP_POWER, ULT_POP_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_POP_DMG_RADIUS, ULT_POP_DAMAGE, true, false);
        }

        Vec3d look = player.getRotationVec(1.0f);
        double launchY = finisher ? ULT_FINAL_LAUNCH_Y : ULT_POP_LAUNCH_Y;

        if (!player.isOnGround()) {
            launchY *= 0.5;
        }

        double targetX = pre.x + look.x * ULT_AIR_STEER;
        double targetZ = pre.z + look.z * ULT_AIR_STEER;
        double targetY = launchY;

        Vec3d vNow = player.getVelocity();

        player.setVelocity(vNow.x, Math.max(vNow.y, !player.isOnGround() ? ULT_LAUNCH_KICK_Y * 0.5 : ULT_LAUNCH_KICK_Y), vNow.z);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        scheduleLaunch(state, targetX, targetY, targetZ);

        float plingPitch = 1.25f + (0.12f * popIndex);
        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                player.getSoundCategory(),
                0.8f, plingPitch);

        if (finisher) {
            w.playSound(null, player.getBlockPos(),
                    ModSounds.EXPLODEBIG,
                    player.getSoundCategory(),
                    0.8f, 0.85f);
            CameraShake.shakeNearby(player, 14.0, 16, 1.55f);
        } else {
            float pitch = 1.05f + 0.12f * popIndex;
            w.playSound(null, player.getBlockPos(),
                    ModSounds.EXPLODEBIG,
                    player.getSoundCategory(),
                    0.75f, pitch);
            CameraShake.shakeNearby(player, 9.0, 8, 0.9f);
        }
    }

    private static void tickUltAmbientFx(ServerPlayerEntity player, ServerWorld w) {
        w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 0.15, player.getZ(),
                1, 0.18, 0.03, 0.18, 0.002);

        if (w.getTime() % 3 == 0) {
            w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    player.getX(), player.getY() + 0.25, player.getZ(),
                    1, 0.12, 0.06, 0.12, 0.01);
        }
    }

    private static void tickUltFinisherChargeFx(ServerPlayerEntity player, ServerWorld w, int warnLeft) {
        float progress = 1.0f - (warnLeft / (float) ULT_WARN_TICKS);
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        int smokeCount = 6 + (int) (progress * 16.0f);
        int flameCount = 2 + (int) (progress * 10.0f);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 0.20, player.getZ(),
                smokeCount, 0.65, 0.05, 0.65, 0.02);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                player.getX(), player.getY() + 0.35, player.getZ(),
                flameCount, 0.35, 0.12, 0.35, 0.02);

        if (w.getTime() % 2 == 0) {
            w.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                    player.getX(), player.getY() + 0.25, player.getZ(),
                    1, 0.25, 0.05, 0.25, 0.0);
        }

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                ULT_CHARGE_REFRESH_TICKS,
                ULT_CHARGE_SLOWNESS_AMP,
                true,
                false
        ));
        player.setSprinting(false);

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 0.2, v.y, v.z * 0.2);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static boolean isShieldBlockingExplosion(ServerPlayerEntity sp, Vec3d center) {
        if (!sp.isBlocking()) return false;

        Vec3d look = sp.getRotationVec(1.0f).normalize();
        Vec3d toExplosion = center.subtract(sp.getPos()).normalize();

        return look.dotProduct(toExplosion) > 0.35;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static void explodeAt(ServerWorld w, Vec3d pos, float power, boolean breakBlocks) {
        boolean grief = breakBlocks && w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);

        // Vanilla zero-power explosion for the base sound
        w.createExplosion(
                null,
                pos.x, pos.y, pos.z,
                0.0f,
                false,
                World.ExplosionSourceType.NONE
        );

        w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.x, pos.y, pos.z, (int)(power * 15), power * 0.4, power * 0.4, power * 0.4, 0.05);
        w.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, (int)(power * 4), power * 0.2, power * 0.2, power * 0.2, 0.1);

        if (grief) {
            java.util.Set<BlockPos> blocksToBreak = new java.util.HashSet<>();
            int rays = 16;
            float breakPower = power * 0.9f;

            for (int x = 0; x < rays; ++x) {
                for (int y = 0; y < rays; ++y) {
                    for (int z = 0; z < rays; ++z) {
                        if (x == 0 || x == rays - 1 || y == 0 || y == rays - 1 || z == 0 || z == rays - 1) {
                            double dx = (double) x / (rays - 1) * 2.0 - 1.0;
                            double dy = (double) y / (rays - 1) * 2.0 - 1.0;
                            double dz = (double) z / (rays - 1) * 2.0 - 1.0;
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            dx /= dist;
                            dy /= dist;
                            dz /= dist;

                            float currentPower = breakPower * (0.7F + w.random.nextFloat() * 0.6F);
                            double cx = pos.x;
                            double cy = pos.y;
                            double cz = pos.z;

                            for (float step = 0.3F; currentPower > 0.0F; currentPower -= 0.225F) {
                                BlockPos targetPos = BlockPos.ofFloored(cx, cy, cz);
                                net.minecraft.block.BlockState state = w.getBlockState(targetPos);

                                if (!state.isAir()) {
                                    float resistance = state.getBlock().getBlastResistance();
                                    if (!state.getFluidState().isEmpty()) {
                                        resistance = Math.max(resistance, 100.0F);
                                    }
                                    currentPower -= (resistance + 0.3F) * 0.3F;
                                }

                                if (currentPower > 0.0F && !state.isAir() && state.getBlock().getBlastResistance() < 1200.0F && state.getFluidState().isEmpty()) {
                                    blocksToBreak.add(targetPos);
                                }

                                cx += dx * 0.3D;
                                cy += dy * 0.3D;
                                cz += dz * 0.3D;
                            }
                        }
                    }
                }
            }

            for (BlockPos targetPos : blocksToBreak) {
                net.minecraft.block.BlockState state = w.getBlockState(targetPos);

                if (state.isIn(net.minecraft.registry.tag.BlockTags.SAND) && w.random.nextFloat() < GLASS_CONVERT_CHANCE) {
                    w.setBlockState(targetPos, net.minecraft.block.Blocks.GLASS.getDefaultState());
                    w.playSound(null, targetPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 2.6f);
                    continue;
                }

                boolean shouldDrop = w.random.nextFloat() < (1.0F / Math.max(1.0F, breakPower * 1.5f));
                w.breakBlock(targetPos, shouldDrop);
            }
        }
    }

    private static void applyExplosionDamage(ServerPlayerEntity caster, ServerWorld w,
                                             Vec3d center, double radius, float maxDamage, boolean isUltimate, boolean isSecondary) {

        Box box = new Box(center, center).expand(radius, radius, radius);

        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster);

        DamageSource src = isUltimate ?
                ModDamageTypes.superExplosion(w, caster) :
                ModDamageTypes.explosionNormal(w, caster);

        for (LivingEntity e : hits) {
            double d = e.getPos().distanceTo(center);
            if (d > radius) continue;

            float t = 1.0f - (float)(d / radius);
            t = MathHelper.clamp(t, 0.0f, 1.0f);

            float dmg = maxDamage * (0.25f + 0.75f * (t * t));

            double knockMul = 1.0;
            if (e instanceof ServerPlayerEntity sp && isShieldBlockingExplosion(sp, center)) {
                dmg *= 0.25f;
                knockMul = 0.25;
            }

            boolean wasAlive = e.getHealth() > 0;
            e.damage(src, dmg);

            if (isSecondary && wasAlive && e.getHealth() <= 0 && e instanceof ServerPlayerEntity) {
                if (w.random.nextFloat() < WHY_SOUND_CHANCE) {
                    w.playSound(null, e.getBlockPos(), ModSounds.WHY, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
            }

            Vec3d push = e.getPos().subtract(center);
            Vec3d horiz = new Vec3d(push.x, 0.0, push.z);
            if (horiz.lengthSquared() > 1.0e-6) {
                Vec3d dir = horiz.normalize();
                e.addVelocity(dir.x * (0.25 * t) * knockMul, 0.08 * t * knockMul, dir.z * (0.25 * t) * knockMul);
                e.velocityModified = true;
            }
        }
    }

    private static void scheduleLaunch(ExplosionState state, double x, double y, double z) {
        state.launchX = x;
        state.launchY = y;
        state.launchZ = z;
        state.ultLaunchDelayTicks = ULT_LAUNCH_DELAY_TICKS;
    }

    private static void tickPendingLaunch(ServerPlayerEntity player, ExplosionState state) {
        state.ultLaunchDelayTicks--;

        if (state.ultLaunchDelayTicks == 1) {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x, Math.max(v.y, ULT_LAUNCH_KICK_Y), v.z);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            return;
        }

        if (state.ultLaunchDelayTicks == 0) {
            player.setVelocity(state.launchX, state.launchY, state.launchZ);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        }
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return "Explosion"; }
    @Override public String getPrimaryName() { return "Ignition"; }
    @Override public String getSecondaryName() { return "Propulsion Blast"; }
    @Override public String getUltimateName() { return "Chain Reaction"; }

    @Override public long getPrimaryCooldownMs() { return 28_000; }
    @Override public long getSecondaryCooldownMs() { return 0; }
    @Override public long getUltimateCooldownMs() { return 460_000; }

    @Override
    public String getOverviewDescription() {
        return "Explosion is a high damage, combo-based and movement-oriented power, where abilities are intended to be used together to move quickly and deal high amounts of group damage." +
                " While also offering some other utilities, like being able to resist all forms of explosion damage, including creepers, end crystals etc. And can also be very destructive.";
    }

    @Override
    public String getPassiveName() {
        return "Shock Absorption";
    }

    @Override
    public String getPassiveDescription() {
        return "You take less explosion damage";
    }

    @Override
    public String getPrimaryDescription() {
        return "Ignite yourself, speeding yourself up and explode after a few seconds, damaging anything around you.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Shoot an explosion where you are looking. The recoil will send you flying in the direction you shot the blast from" +
                "you will still take fall damage after using this. This ability has 2 charges on separate cooldowns.";
    }

    @Override
    public String getUltimateDescription() {
        return "Shoot yourself in the air with a big explosion at your feet (or shoot yourself when you land if airborne.) each time you land you will explode and be" +
                "shot up again. On your final explosion, you will briefly pause and do a bigger explosion. These falls will not inflict fall damage and you are free to use" +
                "other abilities while airborne. Being in water will also pause the fuse. (so you will explode when surfacing instead)";
    }
}