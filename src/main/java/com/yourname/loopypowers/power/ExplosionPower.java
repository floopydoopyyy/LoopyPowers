package com.yourname.loopypowers.power;

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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;

import java.util.List;

/**
 * Explosion power (1.20.1 / Fabric) with:
 * - Vanilla explosions for block damage + FX
 * - NO self-damage (handled by Loopypowers.ALLOW_DAMAGE via a short-lived tag)
 * - Manual damage to nearby entities (fully tunable)
 * - Manual recoil for reliable movement (Blast)
 */
public class ExplosionPower implements Power {

    /* ============================================================
       TAGS / TIMERS
       ============================================================ */
    // Primary
    private static final String IGNITING = "ex_igniting_";       // ex_igniting_<ticks>
    // Secondary
    private static final String BLAST_CHARGES = "ex_blastc_";    // ex_blastc_<0..2>
    private static final String BLAST_RECHARGE = "ex_blastr_";   // ex_blastr_<ticks> (counts down to add one charge)
    private static final String BLAST_LOCK = "ex_blast_lock_";   // tiny anti-double fire


    /* ============================================================
       TUNING
       ============================================================ */

    // Primary: ignition
    private static final int IGNITE_FUSE_TICKS = 20 * 6; // 6s charge
    private static final float IGNITE_POWER = 5.2f;      // vanilla explosion strength (blocks/FX)
    private static final int IGNITE_SPEED_AMP = 1;       // speed amp
    private static final boolean IGNITE_BREAK_BLOCKS = true;

    // Secondary: blast
    private static final int BLAST_MAX_CHARGES = 2;
    private static final int BLAST_RECHARGE_TICKS = 20 * 6; // 6s per charge
    private static final int BLAST_LOCK_TICKS = 4;
    private static final float BLAST_POWER = 2.6f;       // vanilla explosion strength (blocks/FX)
    private static final boolean BLAST_BREAK_BLOCKS = true;
    private static final double BLAST_SPAWN_DIST = 1.2;   // "hands" distance
    private static final double BLAST_SPAWN_DOWN = 0.10;  // slightly lower than eyes
    // Recoil movement tuning
    private static final double RECOIL_STRENGTH = 1.35;   // overall push
    private static final double RECOIL_UP_BONUS = 0.55;   // extra upward help
    private static final double RECOIL_MAX_Y = 1.10;      // cap vertical
    private static final double RECOIL_MAX_H = 1.85;      // cap horizontal


    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        setIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);
        removeTagPrefix(player, BLAST_RECHARGE);
        removeTagPrefix(player, BLAST_LOCK);
        removeTagPrefix(player, IGNITING);
        removeTagPrefix(player, ULT_ACTIVE);
        removeTagPrefix(player, ULT_POP_GAP);
        removeTagPrefix(player, ULT_POPS_LEFT);
        removeTagPrefix(player, ULT_WAITING_LAND);
        removeTagPrefix(player, NO_SELF_EXP_DMG);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeTagPrefix(player, IGNITING);
        removeTagPrefix(player, BLAST_CHARGES);
        removeTagPrefix(player, BLAST_RECHARGE);
        removeTagPrefix(player, BLAST_LOCK);
        removeTagPrefix(player, ULT_ACTIVE);
        removeTagPrefix(player, ULT_POP_GAP);
        removeTagPrefix(player, ULT_POPS_LEFT);
        removeTagPrefix(player, ULT_WAITING_LAND);
        removeTagPrefix(player, NO_SELF_EXP_DMG);
        removeTagPrefix(player, ULT_WARN);
        removeTagPrefix(player, ULT_AIR);
        removeTagPrefix(player, NO_FALL_DMG);
        removeTagPrefix(player, ULT_NEXT);
        removeTagPrefix(player, ULT_STAGE);
        removeTagPrefix(player, ULT_LAUNCH_T);
        removeTagPrefix(player, ULT_LAUNCH_X);
        removeTagPrefix(player, ULT_LAUNCH_Y);
        removeTagPrefix(player, ULT_LAUNCH_Z);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        // tick timers
        tickSingleTimer(player, BLAST_LOCK);

        // this expires immunity from explosion self damage
        tickSingleTimer(player, NO_SELF_EXP_DMG);

        // fall immunity expiry
        tickSingleTimer(player, NO_FALL_DMG);

        // Secondary
        tickBlastRecharge(player);
        updateBlastCooldownUI(player);

        // Primary
        tickIgnition(player);

        // Ultimate - don't tick ult tags here — handled by tickChainReaction
        if (getTimerLeft(player, ULT_ACTIVE) >= 0) {
            tickUltimate(player);
        }
        // also make sure any launches happen
        tickPendingLaunch(player);
    }


    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // not needed
    }

    /* ============================================================
       PASSIVE
       ============================================================ */
    /* Generic explosion resist handled by EntityExplodeResistMixn
       Self explosion damage handled by loopypowers class and method below
     */
    public static boolean shouldIgnoreSelfExplosionDamage(ServerPlayerEntity p) { // for abilities
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(NO_SELF_EXP_DMG)) return true;
        }
        return false;
    }
    public static boolean shouldIgnoreFallDamage(ServerPlayerEntity p) { // only for ult
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(NO_FALL_DMG)) return true;
        }
        return false;
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    private static final float IGNITE_DAMAGE = 12.0f;
    private static final double IGNITE_DMG_RADIUS = 3.5;

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        setSingleTimerTag(player, IGNITING, IGNITE_FUSE_TICKS);

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_TNT_PRIMED,
                player.getSoundCategory(),
                1.0f, 1.0f);

        // initial feedback
        w.spawnParticles(net.minecraft.particle.ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.35, 0.35, 0.02);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickIgnition(ServerPlayerEntity player) {
        int left = tickSingleTimer(player, IGNITING);
        if (left < 0) return;

        ServerWorld w = player.getServerWorld();

        // effects
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, IGNITE_SPEED_AMP, true, false));

        // 0 = just started, 1 = exploded
        float progress = 1.0f - (left / (float) IGNITE_FUSE_TICKS);
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        // particles ramp up near end
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

        // sound ramp
        if (w.getTime() % 20 == 0) {
            float pitch = 0.9f + 0.35f * progress;
            w.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_TNT_PRIMED,
                    player.getSoundCategory(),
                    0.55f,
                    pitch);
        }

        // when timer hits 0
        if (left > 0) return;

        // Prevents all explosion damage for a few ticks
        setSingleTimerTag(player, NO_SELF_EXP_DMG, 6);

        Vec3d center = player.getPos().add(0, 0.1, 0);

        // damage world
        explodeAt(w, center, IGNITE_POWER, IGNITE_BREAK_BLOCKS);

        // damage nearby entities
        applyExplosionDamage(player, w, center, IGNITE_DMG_RADIUS, IGNITE_DAMAGE);

        // small lift
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.65), v.z);
        player.velocityModified = true;
        player.fallDistance = 0.0f;

        CameraShake.shakeNearby(player, 9.0, 16, 1.4f);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final float BLAST_DAMAGE = 7.0f;
    private static final double BLAST_DMG_RADIUS = 4.0;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (getTimerLeft(player, BLAST_LOCK) > 0) return;

        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);
        if (charges <= 0) return;

        // consume charge
        setIntTag(player, BLAST_CHARGES, charges - 1);
        setSingleTimerTag(player, BLAST_LOCK, BLAST_LOCK_TICKS);

        // start recharge if not running
        if (getTimerLeft(player, BLAST_RECHARGE) < 0) {
            setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
        }

        ServerWorld w = player.getServerWorld();

        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d origin = player.getEyePos()
                .add(look.multiply(BLAST_SPAWN_DIST))
                .add(0.0, -BLAST_SPAWN_DOWN, 0.0);

        // prevent self damage
        setSingleTimerTag(player, NO_SELF_EXP_DMG, 6);

        // vanilla explosion (probably will change this)
        explodeAt(w, origin, BLAST_POWER, BLAST_BREAK_BLOCKS);

        // damage
        applyExplosionDamage(player, w, origin, BLAST_DMG_RADIUS, BLAST_DAMAGE);

        // recoil
        applyRecoil(player, origin);

        // feedback
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(),
                0.9f, 1.15f);

        CameraShake.shakeNearby(player, 8.0, 8, 0.95f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBlastRecharge(ServerPlayerEntity player) {
        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);

        // if full, exit
        if (charges >= BLAST_MAX_CHARGES) {
            removeTagPrefix(player, BLAST_RECHARGE);
            return;
        }

        // start timer if not running and charge has been used
        if (getTimerLeft(player, BLAST_RECHARGE) < 0) {
            setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
        }

        // tick timer
        int leftAfterTick = tickSingleTimer(player, BLAST_RECHARGE);
        if (leftAfterTick < 0) return;

        //  when hit 0, give a charge
        if (leftAfterTick == 0) {
            charges = Math.min(BLAST_MAX_CHARGES, charges + 1);
            setIntTag(player, BLAST_CHARGES, charges);

            // If still not full, start next recharge window
            if (charges < BLAST_MAX_CHARGES) {
                setSingleTimerTag(player, BLAST_RECHARGE, BLAST_RECHARGE_TICKS);
            } else {
                removeTagPrefix(player, BLAST_RECHARGE);
            }
        }
    }

    private static void applyRecoil(ServerPlayerEntity player, Vec3d explosionOrigin) { // just sends user flying in opposite direction to where they are looking.
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

        // clamp
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
        // works with cooldown ui.
        // yay
        String key = "ExplosionUI:SECONDARY";

        int charges = getIntTag(player, BLAST_CHARGES, BLAST_MAX_CHARGES);

        // hide ui when at full charge
        if (charges >= BLAST_MAX_CHARGES) {
            com.yourname.loopypowers.CooldownUI.clearCooldown(player, key);
            return;
        }

        // show progress towards nearest charge
        int leftTicks = getTimerLeft(player, BLAST_RECHARGE);

        // if timer isn't present show it anyway
        if (leftTicks < 0) leftTicks = BLAST_RECHARGE_TICKS;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = com.yourname.loopypowers.CooldownUI.makeChargeSuffix(
                charges, BLAST_MAX_CHARGES, leftTicks, BLAST_RECHARGE_TICKS
        );

        com.yourname.loopypowers.CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    /* ============================================================
   ULTIMATE (3 pops -> warn -> final, 10s airborne fizzle)
   ============================================================ */

    // state tags
    private static final String ULT_WARN = "ex_ult_warn_";     // warning before big boom
    private static final String ULT_AIR  = "ex_ult_air_";      // fizzle timer
    private static final String NO_FALL_DMG = "ex_no_fall_";   // fall damage immunity time
    private static final String ULT_NEXT = "ex_ult_next_";     // until next boom
    private static final String ULT_STAGE = "ex_ult_stage_";   // how many pops have been done
    // state tracker
    private static final String ULT_ACTIVE = "ex_ult_active_";
    private static final String ULT_POP_GAP = "ex_ult_gap_";     // ex_ult_gap_<ticks> (min ticks between checks)
    private static final String ULT_POPS_LEFT = "ex_ult_pops_";  // ex_ult_pops_<n>
    private static final String ULT_WAITING_LAND = "ex_ult_land_"; // marker timer while waiting (optional)
    // stop explosions from hitting them when exploding
    private static final String NO_SELF_EXP_DMG = "ex_no_self_exp_"; // ex_no_self_exp_<ticks>

    // tunings
    private static final int ULT_POPS_ACTUAL = 3;              // amount of smaller explosions
    private static final int ULT_WARN_TICKS = 8;               // delay before final pop
    private static final int ULT_FIZZLE_AIR_TICKS = 500;   // time until expires
    private static final int NO_FALL_REFRESH_TICKS = 50;       // how long immune to fall damage for

    private static final double ULT_AIR_STEER = 0.18; // this feels like it changes nothing
    private static final double ULT_POP_LAUNCH_Y = 2.70;
    private static final double ULT_FINAL_LAUNCH_Y = 1.10;

    private static final int ULT_TOTAL_TICKS = 1000;    // safety timeout
    private static final float ULT_POP_POWER = 3.2f;      // explosion power of smaller boom
    private static final boolean ULT_POP_BREAK_BLOCKS = true;
    private static final float ULT_FINAL_POWER = 8.4f;    // explosion power of big boom
    private static final boolean ULT_FINAL_BREAK_BLOCKS = true;
    private static final int ULT_CHARGE_SLOWNESS_AMP = 4;    // slowness before big boom
    private static final int ULT_CHARGE_REFRESH_TICKS = 10;  // refresh rate of slowness
    // Damage
    private static final float ULT_POP_DAMAGE = 5.0f;
    private static final double ULT_POP_DMG_RADIUS = 3.5;
    private static final float ULT_FINAL_DAMAGE = 14.0f;
    private static final double ULT_FINAL_DMG_RADIUS = 7.0;
    // pending launch
    // pretty much applies the launch at a delay so the explosion doesn't mess with it
    private static final String ULT_LAUNCH_T = "ex_ult_launch_"; // ex_ult_launch_<ticks>
    private static final String ULT_LAUNCH_X = "ex_ult_lx_";     // ex_ult_lx_<int>
    private static final String ULT_LAUNCH_Y = "ex_ult_ly_";     // ex_ult_ly_<int>
    private static final String ULT_LAUNCH_Z = "ex_ult_lz_";     // ex_ult_lz_<int>

    private static final int ULT_LAUNCH_DELAY_TICKS = 2;         // apply full launch after this delay
    private static final double ULT_LAUNCH_KICK_Y = 0.12;        // instant dislodge
    private static final double ULT_LAUNCH_STORE_SCALE = 8000.0; // int packing scale

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        // clear any old ult state
        removeTagPrefix(player, ULT_ACTIVE);
        removeTagPrefix(player, ULT_WARN);
        removeTagPrefix(player, ULT_AIR);
        removeTagPrefix(player, ULT_STAGE);
        removeTagPrefix(player, ULT_WAITING_LAND);
        removeTagPrefix(player, NO_FALL_DMG);
        removeTagPrefix(player, ULT_LAUNCH_T);
        removeTagPrefix(player, ULT_LAUNCH_X);
        removeTagPrefix(player, ULT_LAUNCH_Y);
        removeTagPrefix(player, ULT_LAUNCH_Z);

        // start safety timer and pop safey timer
        setSingleTimerTag(player, ULT_ACTIVE, ULT_TOTAL_TICKS);
        setSingleTimerTag(player, ULT_AIR, ULT_FIZZLE_AIR_TICKS);

        // set player to stage 0
        setIntTag(player, ULT_STAGE, 0);

        // if airborne, wait until land before actually starting
        if (!player.isOnGround()) {
            setSingleTimerTag(player, ULT_WAITING_LAND, 999999);
        }

        // feedback fx
        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_TNT_PRIMED,
                player.getSoundCategory(),
                1.0f, 0.8f);
        CameraShake.shakeNearby(player, 10.0, 12, 1.1f);
    }

    /**
     * Call this from onTick while ULT_ACTIVE is present.
     * This is the ONLY ultimate tick method; do not duplicate again
     */
    private static void tickUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // overall safety timer
        int ultLeft = tickSingleTimer(player, ULT_ACTIVE);
        if (ultLeft < 0) return;

        // constant particles
        tickUltAmbientFx(player, w);

        // give them fall damage immunity while ulting
        // without this, it was very unreliable.
        setSingleTimerTag(player, NO_FALL_DMG, 40);

        // only countdown expire if airborne
        if (!player.isOnGround()) {
            int airLeft = tickSingleTimer(player, ULT_AIR);
            if (airLeft == 0) {
                // remove tags
                removeTagPrefix(player, ULT_ACTIVE);
                removeTagPrefix(player, ULT_WARN);
                removeTagPrefix(player, ULT_AIR);
                removeTagPrefix(player, ULT_STAGE);
                removeTagPrefix(player, ULT_WAITING_LAND);

                w.playSound(null, player.getBlockPos(),
                        SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        player.getSoundCategory(),
                        0.9f, 1.2f);

                w.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        12, 0.35, 0.25, 0.35, 0.01);
            }
            return; // do nothing else when airborne
        }

        // grounded resets the airborne countdown
        setSingleTimerTag(player, ULT_AIR, ULT_FIZZLE_AIR_TICKS);

        // If waiting for landing, consume that landing now and allow action next tick
        if (hasTagPrefix(player, ULT_WAITING_LAND)) {
            removeTagPrefix(player, ULT_WAITING_LAND);
        }

        // warning phase - ONLY FOR THE FINAL BOOM
        int warnNow = getTimerLeft(player, ULT_WARN);
        if (warnNow >= 0) {
            // FX and EFFECTS (see what i did there!) while waiting for final
            tickUltFinisherChargeFx(player, w, warnNow);

            int warnLeft = tickSingleTimer(player, ULT_WARN);
            if (warnLeft == 0) {
                removeTagPrefix(player, ULT_WARN);
                removeTagPrefix(player, ULT_AIR);
                removeTagPrefix(player, ULT_STAGE);
                removeTagPrefix(player, ULT_WAITING_LAND);
                removeTagPrefix(player, ULT_ACTIVE);

                doUltPop(player, true, 3); // finisher, treated as 'pop 4'
            }
            return;
        }

        // small pop sequence
        int stage = getIntTag(player, ULT_STAGE, 0); // stage of pop

        if (stage < ULT_POPS_ACTUAL) {
            doUltPop(player, false, stage); // popIndex 0,1,2
            setIntTag(player, ULT_STAGE, stage + 1);

            // after the last small pop, start the warning
            if (stage + 1 >= ULT_POPS_ACTUAL) {
                setSingleTimerTag(player, ULT_WARN, ULT_WARN_TICKS);
            }
            return;
        }

        // if stage is done but warn somehow missing, start it
        setSingleTimerTag(player, ULT_WARN, ULT_WARN_TICKS);
    }

    private static void doUltPop(ServerPlayerEntity player, boolean finisher, int popIndex) {
        ServerWorld w = player.getServerWorld();

        // Capture velocity before the vanilla explosion so knockback can't fw our launch
        Vec3d pre = player.getVelocity();

        // prevent self explosion damage + keep fall immunity up
        setSingleTimerTag(player, NO_SELF_EXP_DMG, finisher ? 8 : 6);
        setSingleTimerTag(player, NO_FALL_DMG, NO_FALL_REFRESH_TICKS);

        // After pop, require a landing before anything else progresses
        setSingleTimerTag(player, ULT_WAITING_LAND, 999999);

        Vec3d origin = player.getPos().add(0, finisher ? 0.1 : 0.2, 0);

        if (finisher) {
            explodeAt(w, origin, ULT_FINAL_POWER, ULT_FINAL_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_FINAL_DMG_RADIUS, ULT_FINAL_DAMAGE);
        } else {
            explodeAt(w, origin, ULT_POP_POWER, ULT_POP_BREAK_BLOCKS);
            applyExplosionDamage(player, w, origin, ULT_POP_DMG_RADIUS, ULT_POP_DAMAGE);
        }

        // dislodge velocity a bit and then pend launch for a few ticks later
        Vec3d look = player.getRotationVec(1.0f);
        double launchY = finisher ? ULT_FINAL_LAUNCH_Y : ULT_POP_LAUNCH_Y;

        double targetX = pre.x + look.x * ULT_AIR_STEER;
        double targetZ = pre.z + look.z * ULT_AIR_STEER;
        double targetY = launchY;

        // immediate dislodge so the later setVelocity isn't eaten by ground collision
        Vec3d vNow = player.getVelocity();
        player.setVelocity(vNow.x, Math.max(vNow.y, ULT_LAUNCH_KICK_Y), vNow.z);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        // pend the real launch
        scheduleLaunch(player, targetX, targetY, targetZ);

        // sound that pitches up
        float plingPitch = 1.25f + (0.12f * popIndex);
        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                player.getSoundCategory(),
                0.8f, plingPitch);

        // explosion fx
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
        // particles
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
        // fx while pending final boom
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

        // Slowness while charging so they can't run away
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS,
                ULT_CHARGE_REFRESH_TICKS,
                ULT_CHARGE_SLOWNESS_AMP,
                true,
                false
        ));
        player.setSprinting(false);

        // kill most horizontal speed when in air
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x * 0.2, v.y, v.z * 0.2);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    // SHIELDING
    private static boolean isShieldBlockingExplosion(ServerPlayerEntity sp, Vec3d center) {
        if (!sp.isBlocking()) return false;

        // player look direction
        Vec3d look = sp.getRotationVec(1.0f).normalize();

        // direction from player to explosion
        Vec3d toExplosion = center.subtract(sp.getPos()).normalize();

        // if explosion is roughly in front of the shield (dot > ~0.35 = ~70deg cone)
        return look.dotProduct(toExplosion) > 0.35;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static void explodeAt(ServerWorld w, Vec3d pos, float power, boolean breakBlocks) {
        boolean grief = breakBlocks && w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);

        World.ExplosionSourceType type = grief ? World.ExplosionSourceType.MOB : World.ExplosionSourceType.NONE;

        w.createExplosion(
                null,
                pos.x, pos.y, pos.z,
                power,
                false, // createFire
                type
        );
    }

    private static void applyExplosionDamage(ServerPlayerEntity caster, ServerWorld w,
                                             Vec3d center, double radius, float maxDamage) {

        Box box = new Box(center, center).expand(radius, radius, radius);

        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != caster);

        DamageSource src = ModDamageTypes.superExplosion(w, caster);

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

            e.damage(src, dmg);

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
        // store target velocity as ints
        setIntTag(player, ULT_LAUNCH_X, (int) Math.round(x * ULT_LAUNCH_STORE_SCALE));
        setIntTag(player, ULT_LAUNCH_Y, (int) Math.round(y * ULT_LAUNCH_STORE_SCALE));
        setIntTag(player, ULT_LAUNCH_Z, (int) Math.round(z * ULT_LAUNCH_STORE_SCALE));

        // start timer
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

        // tick 1: dislodge again
        if (left == 1) {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x, Math.max(v.y, ULT_LAUNCH_KICK_Y), v.z);
            player.velocityModified = true;
            player.fallDistance = 0.0f;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            return;
        }

        // tick 0: liftoff!!!
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
    @Override public String getSecondaryName() { return "Blast"; }
    @Override public String getUltimateName() { return "Chain Reaction"; }

    @Override public long getPrimaryCooldownMs() { return 9_000; }
    @Override public long getSecondaryCooldownMs() { return 0; } // charges handle this
    @Override public long getUltimateCooldownMs() { return 5_000; }

    @Override
    public String getOverviewDescription() {
        return "To be honest, this power is a weird one to describe. I would say it is more movement focussed as all of your abilities allow you to deal high" +
                "damage and have great movement. But you have to be smart about it since you're not immune to fall damage. (you are immune to your own explosions)";
    }

    @Override
    public String getPassiveName() {
        return "Explosure Therapy";
    } // haha! see what i did there. this name sucks

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
                "you will still take fall damage after using this. This ability has 2 charges on seperate cooldowns.";
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

    private static int getTimerLeft(Entity e, String prefix) {
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