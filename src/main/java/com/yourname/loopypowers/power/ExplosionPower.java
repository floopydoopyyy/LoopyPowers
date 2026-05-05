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

import java.util.List;

public class ExplosionPower implements Power {

    /* ============================================================
       TAGS / TIMERS
       ============================================================ */
    // Primary
    private static final String IGNITING = "ex_igniting_";
    // Secondary
    private static final String BLAST_CHARGES = "ex_blastc_";
    private static final String BLAST_RECHARGE = "ex_blastr_";
    private static final String BLAST_LOCK = "ex_blast_lock_";

    /* ============================================================
       TUNING
       ============================================================ */

    // Primary: ignition
    private static final int IGNITE_FUSE_TICKS = 20 * 5; // 5s charge
    private static final float IGNITE_POWER = 4.5f;      //  explosion strength (blocks/FX)
    private static final int IGNITE_SPEED_AMP = 1;       // speed amp
    private static final boolean IGNITE_BREAK_BLOCKS = true;

    // Secondary: blast
    private static final int BLAST_MAX_CHARGES = 2;
    private static final int BLAST_RECHARGE_TICKS = 240; // cooldown per charge
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
    public static final String ULT_ACTIVE = "ex_ult_active_";
    public static final String ULT_WARN = "ex_ult_warn_";
    public static final String ULT_AIR  = "ex_ult_air_";
    public static final String ULT_STAGE = "ex_ult_stage_";
    public static final String ULT_WAITING_LAND = "ex_ult_land_";
    private static final String NO_SELF_EXP_DMG = "ex_no_self_exp_";

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

    // DAMAGE AND RADI
    private static final float IGNITE_DAMAGE = 12.0f;
    private static final double IGNITE_DMG_RADIUS = 3.5;

    private static final float BLAST_DAMAGE = 8.0f;
    private static final double BLAST_DMG_RADIUS = 3.0;

    private static final float ULT_POP_DAMAGE = 11.0f;
    private static final double ULT_POP_DMG_RADIUS = 4.0;
    private static final float ULT_FINAL_DAMAGE = 16.0f;
    private static final double ULT_FINAL_DMG_RADIUS = 5.5;

    private static final String ULT_LAUNCH_T = "ex_ult_launch_";
    private static final String ULT_LAUNCH_X = "ex_ult_lx_";
    private static final String ULT_LAUNCH_Y = "ex_ult_ly_";
    private static final String ULT_LAUNCH_Z = "ex_ult_lz_";

    private static final int ULT_LAUNCH_DELAY_TICKS = 2;
    private static final double ULT_LAUNCH_KICK_Y = 0.12;
    private static final double ULT_LAUNCH_STORE_SCALE = 8000.0;

    // funnies
    private static final float GLASS_CONVERT_CHANCE = 0.07f; // silly glass
    private static final float WHY_SOUND_CHANCE = 0.005f; // i regret this

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        removeAllTagPrefix(player, "ex_");
        setIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        // Strip all explosion power tags cleanly
        removeAllTagPrefix(player, "ex_");

        // Strip any lingering statuses
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
        tickSingleTimer(player, BLAST_LOCK);
        tickSingleTimer(player, NO_SELF_EXP_DMG);

        tickBlastRecharge(player);
        updateBlastCooldownUI(player);
        tickIgnition(player);

        if (getTimerLeft(player, ULT_ACTIVE) >= 0) {
            tickUltimate(player);
        }
        tickPendingLaunch(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) { }

    /* ============================================================
       PASSIVE
       ============================================================ */

    public static boolean shouldIgnoreSelfExplosionDamage(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(NO_SELF_EXP_DMG)) return true;
        }
        return false;
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        setSingleTimerTag(player, IGNITING, IGNITE_FUSE_TICKS);

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

    private static void tickIgnition(ServerPlayerEntity player) {
        int left = tickSingleTimer(player, IGNITING);
        if (left < 0) return;

        ServerWorld w = player.getServerWorld();

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, IGNITE_SPEED_AMP, true, false));

        float progress = 1.0f - (left / (float) IGNITE_FUSE_TICKS);
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

            if (left <= 30) {
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

        if (left > 0) return;

        setSingleTimerTag(player, NO_SELF_EXP_DMG, 6);

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
        if (getTimerLeft(player, BLAST_LOCK) > 0) return;

        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);
        if (charges <= 0) return;

        setIntTag(player, BLAST_CHARGES, charges - 1);
        setSingleTimerTag(player, BLAST_LOCK, BLAST_LOCK_TICKS);

        if (getTimerLeft(player, BLAST_RECHARGE) < 0) {
            setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
        }

        ServerWorld w = player.getServerWorld();

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d origin = player.getEyePos()
                .add(look.multiply(BLAST_SPAWN_DIST))
                .add(0.0, -BLAST_SPAWN_DOWN, 0.0);

        setSingleTimerTag(player, NO_SELF_EXP_DMG, 6);

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

    private static void tickBlastRecharge(ServerPlayerEntity player) {
        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);

        if (charges >= BLAST_MAX_CHARGES) {
            removeTagPrefix(player, BLAST_RECHARGE);
            return;
        }

        if (getTimerLeft(player, BLAST_RECHARGE) < 0) {
            setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
        }

        int leftAfterTick = tickSingleTimer(player, BLAST_RECHARGE);
        if (leftAfterTick < 0) return;

        if (leftAfterTick == 0) {
            charges = Math.min(BLAST_MAX_CHARGES, charges + 1);
            setIntTag(player, BLAST_CHARGES, charges);

            if (charges < BLAST_MAX_CHARGES) {
                setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
            } else {
                removeTagPrefix(player, BLAST_RECHARGE);
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

    private static void updateBlastCooldownUI(ServerPlayerEntity player) {
        String key = "ExplosionUI:SECONDARY";

        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);

        if (charges >= BLAST_MAX_CHARGES) {
            com.yourname.loopypowers.CooldownUI.clearCooldown(player, key);
            return;
        }

        int leftTicks = getTimerLeft(player, BLAST_RECHARGE);

        if (leftTicks < 0) leftTicks = BLAST_RECHARGE_TICKS;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = com.yourname.loopypowers.CooldownUI.makeChargeSuffix(
                charges, BLAST_MAX_CHARGES, leftTicks, BLAST_RECHARGE_TICKS
        );

        com.yourname.loopypowers.CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    /* ============================================================
   ULTIMATE
   ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        removeAllTagPrefix(player, "ex_ult_");

        setSingleTimerTag(player, ULT_ACTIVE, ULT_TOTAL_TICKS);
        setSingleTimerTag(player, ULT_AIR, ULT_FIZZLE_AIR_TICKS);

        setIntTag(player, ULT_STAGE, 0);

        if (!player.isOnGround()) {
            setSingleTimerTag(player, ULT_WAITING_LAND, 999999);
        }

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_TNT_PRIMED,
                player.getSoundCategory(),
                1.0f, 0.8f);
        CameraShake.shakeNearby(player, 10.0, 12, 1.1f);
    }

    private static void tickUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        int ultLeft = tickSingleTimer(player, ULT_ACTIVE);
        if (ultLeft < 0) return;

        tickUltAmbientFx(player, w);

        // Render drop zone indicator while airborne
        if (!player.isOnGround()) {
            tickUltDropZoneIndicator(player, w);

            int airLeft = tickSingleTimer(player, ULT_AIR);
            if (airLeft == 0) {
                cancelUltimate(player, w);
            }
            return;
        }

        setSingleTimerTag(player, ULT_AIR, ULT_FIZZLE_AIR_TICKS);

        if (hasTagPrefix(player, ULT_WAITING_LAND)) {
            removeTagPrefix(player, ULT_WAITING_LAND);
        }

        int warnNow = getTimerLeft(player, ULT_WARN);
        if (warnNow >= 0) {
            tickUltFinisherChargeFx(player, w, warnNow);

            int warnLeft = tickSingleTimer(player, ULT_WARN);
            if (warnLeft == 0) {
                removeAllTagPrefix(player, "ex_ult_");

                doUltPop(player, true, 3);
            }
            return;
        }

        int stage = getIntTag(player, ULT_STAGE, 0);

        if (stage < ULT_POPS_ACTUAL) {
            doUltPop(player, false, stage);
            setIntTag(player, ULT_STAGE, stage + 1);

            if (stage + 1 >= ULT_POPS_ACTUAL) {
                setSingleTimerTag(player, ULT_WARN, ULT_WARN_TICKS);
            }
            return;
        }

        setSingleTimerTag(player, ULT_WARN, ULT_WARN_TICKS);
    }

    public static void cancelUltimate(ServerPlayerEntity player, ServerWorld w) {
        removeAllTagPrefix(player, "ex_ult_");

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

    public static void forceEarlyDetonation(ServerPlayerEntity player, ServerWorld w) {
        // If we aren't currently waiting in the air for the next pop, do nothing
        if (getTimerLeft(player, ULT_AIR) < 0) return;

        // Clear the air timer so we don't naturally fizzle
        removeTagPrefix(player, ULT_AIR);

        // Remove the wait tag so the next tick instantly triggers the explosion
        removeTagPrefix(player, ULT_WAITING_LAND);

        // Optional: Play a sound/particle effect to show they were "shot down"
        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_DAMAGE, player.getSoundCategory(), 1.0f, 1.5f);

        // Reduce their upward momentum significantly so the forced pop doesn't send them into orbit
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 0.5, v.y * 0.2, v.z * 0.5);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void tickUltDropZoneIndicator(ServerPlayerEntity player, ServerWorld w) {
        // Raycast straight down to find where they will land
        Vec3d start = player.getPos();
        Vec3d end = start.subtract(0, 100, 0); // Cast 100 blocks down

        HitResult hit = w.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player
        ));

        if (hit.getType() == HitResult.Type.MISS) return; // In void, no ground to draw on

        Vec3d groundPos = hit.getPos();

        // Determine radius based on if this is a regular pop or the final boom
        int stage = getIntTag(player, ULT_STAGE, 0);
        double radius = (stage >= ULT_POPS_ACTUAL) ? ULT_FINAL_DMG_RADIUS : ULT_POP_DMG_RADIUS;

        // Draw spinning circle
        int points = 24;
        double time = (w.getTime() % 20) / 20.0; // 0 to 1 over a second
        double offsetAngle = time * Math.PI * 2;

        for (int i = 0; i < points; i++) {
            double angle = offsetAngle + (2 * Math.PI * i) / points;
            double x = groundPos.x + Math.cos(angle) * radius;
            double z = groundPos.z + Math.sin(angle) * radius;

            w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME,
                    x, groundPos.y + 0.1, z, 1, 0, 0, 0, 0);
        }
    }

    private static void doUltPop(ServerPlayerEntity player, boolean finisher, int popIndex) {
        ServerWorld w = player.getServerWorld();

        Vec3d pre = player.getVelocity();

        setSingleTimerTag(player, NO_SELF_EXP_DMG, finisher ? 8 : 6);

        // Apply braced so they survive the fall back down
        player.addStatusEffect(new StatusEffectInstance(ModEffects.BRACED, 200, 0, false, false, true));

        setSingleTimerTag(player, ULT_WAITING_LAND, 999999);

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

        // half it if they were forced to explode early
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

        scheduleLaunch(player, targetX, targetY, targetZ);

        float plingPitch = 1.25f + (0.12f * popIndex);
        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                player.getSoundCategory(),
                0.8f, plingPitch);

        if (finisher) {
            w.playSound(null, player.getBlockPos(),
                    ModSounds.EXPLODEBIG,
                    player.getSoundCategory(),
                    0.95f, 0.85f);
            CameraShake.shakeNearby(player, 14.0, 16, 1.55f);
        } else {
            float pitch = 1.05f + 0.12f * popIndex;
            w.playSound(null, player.getBlockPos(),
                    ModSounds.EXPLODEBIG,
                    player.getSoundCategory(),
                    0.8f, pitch);
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
                0.0f, // 0 power = no vanilla damage/knockback
                false,
                World.ExplosionSourceType.NONE
        );

        // explosion
        w.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION_EMITTER,
                pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);

        w.spawnParticles(net.minecraft.particle.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.x, pos.y, pos.z, (int)(power * 15), power * 0.4, power * 0.4, power * 0.4, 0.05);
        w.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA,
                pos.x, pos.y, pos.z, (int)(power * 4), power * 0.2, power * 0.2, power * 0.2, 0.1);


        // Recreate the vanilla raycasting algorithm for realistic cratering
        if (grief) {
            java.util.Set<BlockPos> blocksToBreak = new java.util.HashSet<>();
            int rays = 16;

            // Restored breakPower to 0.9f so it can reliably punch through stone
            // without being totally overpowered like it was originally.
            float breakPower = power * 0.9f;

            for (int x = 0; x < rays; ++x) {
                for (int y = 0; y < rays; ++y) {
                    for (int z = 0; z < rays; ++z) {
                        // Only shoot rays from the outer edges of the 16x16x16 grid
                        if (x == 0 || x == rays - 1 || y == 0 || y == rays - 1 || z == 0 || z == rays - 1) {
                            double dx = (double) x / (rays - 1) * 2.0 - 1.0;
                            double dy = (double) y / (rays - 1) * 2.0 - 1.0;
                            double dz = (double) z / (rays - 1) * 2.0 - 1.0;
                            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            dx /= dist;
                            dy /= dist;
                            dz /= dist;

                            // Randomize the ray's power slightly for jagged crater edges
                            float currentPower = breakPower * (0.7F + w.random.nextFloat() * 0.6F);
                            double cx = pos.x;
                            double cy = pos.y;
                            double cz = pos.z;

                            // Step along the ray
                            for (float step = 0.3F; currentPower > 0.0F; currentPower -= 0.225F) {
                                BlockPos targetPos = BlockPos.ofFloored(cx, cy, cz);
                                net.minecraft.block.BlockState state = w.getBlockState(targetPos);

                                // If we hit a block or fluid, reduce the ray's power by its blast resistance
                                if (!state.isAir()) {
                                    float resistance = state.getBlock().getBlastResistance();

                                    // Water and lava absorb blasts heavily
                                    if (!state.getFluidState().isEmpty()) {
                                        resistance = Math.max(resistance, 100.0F);
                                    }
                                    currentPower -= (resistance + 0.3F) * 0.3F;
                                }

                                // If the ray still has power, mark the block for breaking
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

            // Actually break the blocks we collected
            for (BlockPos targetPos : blocksToBreak) {
                net.minecraft.block.BlockState state = w.getBlockState(targetPos);

                // the glassing easter egg
                if (state.isIn(net.minecraft.registry.tag.BlockTags.SAND) && w.random.nextFloat() < GLASS_CONVERT_CHANCE) {
                    w.setBlockState(targetPos, net.minecraft.block.Blocks.GLASS.getDefaultState());
                    w.playSound(null, targetPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.BLOCKS, 0.5f, 2.6f);
                    continue;
                }

                // To stop lag and mining abuse, we mimic vanilla "decay"
                // The larger the explosion, the lower the chance a block drops as an item.
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

            // the "WHY" easter egg
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

    // ult launch stuff
    private static void scheduleLaunch(ServerPlayerEntity player, double x, double y, double z) {
        setIntTag(player, ULT_LAUNCH_X, (int) Math.round(x * ULT_LAUNCH_STORE_SCALE));
        setIntTag(player, ULT_LAUNCH_Y, (int) Math.round(y * ULT_LAUNCH_STORE_SCALE));
        setIntTag(player, ULT_LAUNCH_Z, (int) Math.round(z * ULT_LAUNCH_STORE_SCALE));

        setSingleTimerTag(player, ULT_LAUNCH_T, ULT_LAUNCH_DELAY_TICKS);
    }

    private static double readLaunch(ServerPlayerEntity player, String prefix) {
        int packed = getIntTag(player, prefix, 0);
        return packed / ULT_LAUNCH_STORE_SCALE;
    }

    private static void clearLaunch(ServerPlayerEntity player) {
        removeTagPrefix(player, ULT_LAUNCH_T);
        removeTagPrefix(player, ULT_LAUNCH_X);
        removeTagPrefix(player, ULT_LAUNCH_Y);
        removeTagPrefix(player, ULT_LAUNCH_Z);
    }

    private static void tickPendingLaunch(ServerPlayerEntity player) {
        int left = tickSingleTimer(player, ULT_LAUNCH_T);
        if (left < 0) return;

        if (left == 1) {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x, Math.max(v.y, ULT_LAUNCH_KICK_Y), v.z);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            return;
        }

        if (left == 0) {
            double x = readLaunch(player, ULT_LAUNCH_X);
            double y = readLaunch(player, ULT_LAUNCH_Y);
            double z = readLaunch(player, ULT_LAUNCH_Z);

            player.setVelocity(x, y, z);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

            clearLaunch(player);
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

    /* ============================================================
       TAG HELPERS
       ============================================================ */

    private static boolean hasTagPrefix(Entity e, String prefix) {
        for (String tag : e.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(Entity e, String prefix) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) { it.remove(); return; }
        }
    }

    private static void removeAllTagPrefix(Entity e, String prefix) {
        e.getCommandTags().removeIf(tag -> tag.startsWith(prefix));
    }

    private static void setSingleTimerTag(Entity e, String prefix, int ticks) {
        removeTagPrefix(e, prefix);
        e.getCommandTags().add(prefix + ticks);
    }

    private static int tickSingleTimer(Entity e, String prefix) {
        String found = null;
        for (String tag : e.getCommandTags()) {
            if (tag.startsWith(prefix)) { found = tag; break; }
        }
        if (found == null) return -1;

        e.getCommandTags().remove(found);

        int ticks;
        try {
            ticks = Integer.parseInt(found.substring(prefix.length())) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }

        if (ticks > 0) e.getCommandTags().add(prefix + ticks);
        return ticks;
    }

    public static int getTimerLeft(Entity e, String prefix) {
        for (String tag : e.getCommandTags()) {
            if (!tag.startsWith(prefix)) continue;
            try {
                return Integer.parseInt(tag.substring(prefix.length()));
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        return -1;
    }

    private static int getIntTag(Entity e, String prefix, int fallback) {
        for (String tag : e.getCommandTags()) {
            if (!tag.startsWith(prefix)) continue;
            try {
                return Integer.parseInt(tag.substring(prefix.length()));
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private static void setIntTag(Entity e, String prefix, int value) {
        removeTagPrefix(e, prefix);
        e.getCommandTags().add(prefix + value);
    }
}