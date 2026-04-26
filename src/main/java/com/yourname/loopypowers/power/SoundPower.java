package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.SonicBoltEntity;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.block.BlockState;

public class SoundPower implements Power {

    /* ============================================================
       TAGS / TIMERS
       ============================================================ */

    // Passive: resonance tracking + trails
    private static final String RES_POINTS = "sd_res_points_";         // numeric tag
    private static final String RES_TRAIL_STEP = "sd_res_trail_step_";  // cadence for client-trail packets later
    private static final String RES_SCAN_STEP = "sd_res_scan_step_";    // cadence to scan nearby entities

    // “Resonated” marker on targets (server-side state)
    private static final String RESONATED = "sd_resonated_";            // sd_resonated_<ticks>

    // debuffs
    private static final String DAMPENED = "sd_dampened_";              // sd_dampened_<ticks>
    private static final String STUN_LOCK = "sd_stun_lock_";            // anti-spam on-hit

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // Passive scan radius
    private static final double RES_RADIUS = 14.0;
    // How often passive runs
    private static final int RES_SCAN_INTERVAL = 4;     // ticks between sound check
    private static final int RES_TRAIL_INTERVAL = 2;    // ticks between particles
    // heartbeat warning
    private static final String HB_CASTER_STEP = "sd_hb_caster_step_"; // caster cadence
    private static final String HB_TARGET_STEP = "sd_hb_target_step_"; // per-target cadence
    private static final double RES_INSTANT_RADIUS = 3.0; // where they are instantly detected
    private static final int RESONATED_TICKS = 80;      // how long they are glowed for

    /* ============================================================
       SERVER-ONLY PASSIVE STATE
       ============================================================ */

    // point per victim storage
    private static final Map<UUID, Integer> RES_SCORE = new HashMap<>();

    // Threshold to become resonated.
    private static final int RES_THRESHOLD = 15;

    // Natural decay each scan.
    private static final int RES_DECAY_PER_SCAN = 2;

    // how many entities are scanned
    private static final int MAX_TRAIL_TARGETS = 14;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // decorative
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        // i should probably implement cleanup
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        // Tick down timers/tags
        tickSingleTimer(player, RES_TRAIL_STEP);
        tickSingleTimer(player, RES_SCAN_STEP);

        // Tick down debuffs on player if you ever apply them to self (optional)
        tickSingleTimer(player, RES_POINTS); // (only if you store points as a timer tag; we may not)
        tickSingleTimer(player, HB_CASTER_STEP);

        // Passive placeholder
        tickResonancePassive(player);

        // secondary
        tickBassDrop(player);

        // ult (for windup)
        tickUltimate(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // silly
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void tickResonancePassive(ServerPlayerEntity player) {
        // scans nearby entites and applies points accordingly
        // the actual particles can be edited in the LoopyPowersClient class.

        ServerWorld w = player.getServerWorld();

        // find nearby living entities
        Box box = new Box(player.getPos(), player.getPos()).expand(RES_RADIUS, 6.0, RES_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        // calls client side particles
        int trailLeft = tickSingleTimer(player, RES_TRAIL_STEP);
        if (trailLeft <= 0) {
            setSingleTimerTag(player, RES_TRAIL_STEP, RES_TRAIL_INTERVAL);

            int sent = 0;
            for (LivingEntity e : nearby) {
                // spawns the trail

                // cap to prevent nuking perf
                if (sent >= MAX_TRAIL_TARGETS) break;

                // Send packet to THIS player only.
                RenderPackets.sendResonanceTrail(
                        player,
                        e.getId(),
                        2,      // count per burst
                        1.0f    // intensity
                );
                sent++;
            }
        }

        // initiates a scan on the entities
        int scanLeft = tickSingleTimer(player, RES_SCAN_STEP);
        if (scanLeft > 0) return;
        setSingleTimerTag(player, RES_SCAN_STEP, RES_SCAN_INTERVAL);

        // Track which UUIDs are still nearby so we can decay/clean up
        // decay everyone we seen and clean entries not seen
        Map<UUID, Boolean> seen = new HashMap<>();

        for (LivingEntity e : nearby) {
            UUID id = e.getUuid();
            seen.put(id, true);

            // tick down entity-side tags
            tickSingleTimer(e, RESONATED);
            tickSingleTimer(e, DAMPENED);
            tickSingleTimer(e, HB_TARGET_STEP);

            // base decay
            int cur = RES_SCORE.getOrDefault(id, 0);
            cur = Math.max(0, cur - RES_DECAY_PER_SCAN);

            // compute points
            cur += computeConspicuousPoints(e);
            RES_SCORE.put(id, cur);

            // instantly resonated if too close
            if (player.squaredDistanceTo(e) <= (RES_INSTANT_RADIUS * RES_INSTANT_RADIUS)) {
                cur = RES_THRESHOLD;
            }

            RES_SCORE.put(id, cur);

            // SOUND WARNING SYSTEM - honestly useless
            if (cur < RES_THRESHOLD) {
                float frac = cur / (float) RES_THRESHOLD; // 0..1
                boolean alreadyResonated = hasTagPrefix(e, RESONATED);

                // only warn when close, and not already resonated
                if (!alreadyResonated && frac >= 0.70f) {

                    // per-caster cadence check
                    int casterHb = tickSingleTimer(player, HB_CASTER_STEP);

                    // per-target cadence check
                    int targetHb = tickSingleTimer(e, HB_TARGET_STEP);

                    if (casterHb <= 0 && targetHb <= 0) {

                        // heartbeat speeds up as they get closer
                        float t = MathHelper.clamp((frac - 0.70f) / 0.30f, 0.0f, 1.0f);
                        int interval = (int) MathHelper.lerp(16.0f, 5.0f, t);

                        // set cadences
                        setSingleTimerTag(player, HB_CASTER_STEP, Math.max(3, interval - 2));
                        setSingleTimerTag(e, HB_TARGET_STEP, interval);

                        // scale volume/pitch
                        float vol = 0.25f + 0.35f * t;
                        float pitch = 0.85f + 0.25f * t;

                        // play to caster
                        player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, vol, pitch);

                        // play to the target if player
                        if (e instanceof ServerPlayerEntity spTarget) {
                            spTarget.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, vol * 0.9f, pitch);
                        }

                        // particles when closer
                        if (frac >= 0.92f) {
                            RenderPackets.sendResonanceRing(player, e.getId(), 1.2f);
                            if (e instanceof ServerPlayerEntity spTarget2) {
                                RenderPackets.sendResonanceRing(spTarget2, e.getId(), 1.0f);
                            }
                        }
                    }
                }
            }

            // If they cross threshold
            if (cur >= RES_THRESHOLD) {
                boolean already = hasTagPrefix(e, RESONATED);

                // refresh resonated duration
                setSingleTimerTag(e, RESONATED, RESONATED_TICKS);

                // glow
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, RESONATED_TICKS + 60, 0, true, true));

                // first time feedback
                if (!already) {
                    w.playSound(null, e.getBlockPos(),
                            SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                            player.getSoundCategory(),
                            0.7f, 1.1f);

                    w.spawnParticles(ParticleTypes.SCULK_SOUL,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            6, 0.35, 0.45, 0.35, 0.02);

                    // draw line to detection
                    RenderPackets.sendResonanceLine(player, e.getId(), 20, 1.0f);
                    // if the target is a player show it to them too
                    if (e instanceof ServerPlayerEntity spTarget) {
                        RenderPackets.sendResonanceLine(spTarget, e.getId(), 20, 1.0f);
                    }
                }
            }
        }

        // remove scores from people who don't need it
        var it = RES_SCORE.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (!seen.containsKey(id)) it.remove();
        }
    }

    //i wish this was actually useful, I worked so hard on this system.
    private static int computeConspicuousPoints(LivingEntity e) {
        int pts = 0;

        Vec3d v = e.getVelocity();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);

        // movement
        if (h > 0.08) pts += 1;        // walking-ish
        if (h > 0.22) pts += 3;        // fast movement
        if (e.isSprinting()) pts += 4; // explicit sprint
        // jumping
        if (!e.isOnGround() && v.y > 0.10) pts += 3;
        // falling quickly
        if (!e.isOnGround() && v.y < -0.25) pts += 2;
        // being hurt
        if (e.hurtTime > 0) pts += 2;
        // swimming
        if (e.isTouchingWater()) pts += 1;
        return pts;
    }

    // STILL SORT OF FOR PASSIVE BUT NOT REALLY:
    // these are used for the extra damage on resonated targets
    public static void applyAbilityHit(ServerPlayerEntity caster, LivingEntity target,
                                       float baseDamage,
                                       boolean allowBurst) {

        // base hit
        target.damage(ModDamageTypes.sound(target.getWorld(), caster), baseDamage);

        if (!allowBurst) return; // return if exempt for whatever reason

        // burst only if currently resonated
        if (!hasTagPrefix(target, RESONATED)) return;

        // consume resonance
        removeTagPrefix(target, RESONATED);
        target.removeStatusEffect(StatusEffects.GLOWING);

        // burst tuning
        float burstDamageBonus = 4.0f;
        //float burstKbBonus = 1.2f;

        // extra damage (also knockback mb)
        target.damage(ModDamageTypes.sound(target.getWorld(), caster), burstDamageBonus);
        //applyKnockbackFrom(caster, target, baseKb + burstKbBonus);

        // stun
        target.setVelocity(0, Math.min(target.getVelocity().y, 0.0), 0);
        target.velocityModified = true;

        // tag timers
        setSingleTimerTag(target, STUN_LOCK, 35); // how long player is stunned.
        setSingleTimerTag(target, STUN_EARRING_ONCE, 35); // how long they are blocked from earinging again
        setSingleTimerTag(target, DAMPENED, 45);     // how long audio is dampened for

        // effects
        ServerWorld sw = (ServerWorld) target.getWorld();
        sw.spawnParticles(
                ParticleTypes.SONIC_BOOM,
                target.getX(),
                target.getY() + target.getHeight() * 0.6,
                target.getZ(),
                1,
                0, 0, 0,
                0
        );
        // particles
        sw.spawnParticles(
                ParticleTypes.EXPLOSION,
                target.getX(),
                target.getY() + 0.2,
                target.getZ(),
                6,
                0.35, 0.25, 0.35,
                0.02
        );
        // sounds
        sw.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                caster.getSoundCategory(),
                0.9f, 1.3f);
        // camerashake
        if (target instanceof ServerPlayerEntity spTarget) {
            CameraShake.shake(spTarget, 20, 1.0f);
        }
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    // PRIMARY
    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // spawn bolt
        SonicBoltEntity bolt = new SonicBoltEntity(ModEntities.SONIC_BOLT, w);
        bolt.setOwner(player);

        Vec3d start = player.getEyePos().add(player.getRotationVec(1.0f).multiply(0.6));
        bolt.setPos(start.x, start.y, start.z);

        // very fast projectile
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        bolt.setVelocity(dir.multiply(1.7)); // speed of bolt

        w.spawnEntity(bolt);

        w.playSound(null, player.getBlockPos(),
                ModSounds.BOLT,
                player.getSoundCategory(), 0.4f, 1.2f);
    }

    // SECONDARY
    // stats
    private static final int BD_PULSE_COUNT = 6;
    private static final int BD_PULSE_GAP_TICKS = 5;     // spacing between pull pulses
    private static final int BD_FINAL_DELAY_TICKS = 2;  // delay before big burst

    private static final double BD_PULL_RADIUS = 10.0;   // who gets pulled
    private static final double BD_FINAL_RADIUS = 7.0;   // who gets launched by the big burst
    private static final float  BD_PULL_STRENGTH = 0.22f;
    private static final float  BD_PULL_UP = 0.02f;

    private static final float  BD_FINAL_KB = 1.00f;
    private static final float  BD_FINAL_UP = 0.30f;
    private static final float  BD_FINAL_DAMAGE = 4.0f;  // final burst damage
    private static final int    BD_FINAL_STUN_TICKS = 35; // stun time if resonated
    private static final int    BD_REMOTE_STUN_TICKS = 30;

    // SPHERE VISUALS
    private static final int PULL_VIZ_STEPS = 8;   // how many ticks the pull sphere animates
    private static final int BLAST_VIZ_STEPS = 8;  // how many ticks the blast sphere animates
    private static final int PULL_POINTS = 90;     // per tick
    private static final int BLAST_POINTS = 120;   // per tick

    private static final class BassDropState {
        int nextPulse;         // pulses left to fire
        int nextPulseIn;       // timer until next pulse
        boolean finalPending;  // waiting for final burst
        int finalIn;           // timer until final burst
        final java.util.Set<UUID> scanned = new java.util.HashSet<>();

        // animated spheres (to make size change obvious)
        int pullVizStep = -1;
        int blastVizStep = -1;
    }
    private static final Map<UUID, BassDropState> BD_STATES = new HashMap<>();

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        UUID casterId = player.getUuid();

        BassDropState s = new BassDropState();
        s.nextPulse = BD_PULSE_COUNT;
        s.nextPulseIn = 0;      // fire first pull insantly
        s.finalPending = false;

        // starts ult
        BD_STATES.put(casterId, s);

        // Start feedback
        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_SCULK_CATALYST_BLOOM,
                player.getSoundCategory(),
                0.7f, 1.4f);

        w.spawnParticles(ParticleTypes.SONIC_BOOM,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1, 0, 0, 0, 0);
    }

    private static void tickBassDrop(ServerPlayerEntity caster) {
        BassDropState s = BD_STATES.get(caster.getUuid());
        if (s == null) return;

        ServerWorld w = caster.getServerWorld();

        // my attempt to animate the spheres
        animateBassSpheres(w, caster, s);

        // handle pull pulses
        if (!s.finalPending) {
            s.nextPulseIn--;

            if (s.nextPulseIn <= 0 && s.nextPulse > 0) {
                doBassPullPulse(w, caster);
                s.nextPulse--;
                s.nextPulseIn = BD_PULSE_GAP_TICKS;

                if (s.nextPulse <= 0) {
                    s.finalPending = true;
                    s.finalIn = BD_FINAL_DELAY_TICKS;
                }
            }
            return;
        }

        // handle final burst
        s.finalIn--;
        if (s.finalIn > 0) return;

        doBassFinalBurst(w, caster, s.scanned);
        BD_STATES.remove(caster.getUuid());
    }

    private static void doBassFinalBurst(ServerWorld w, ServerPlayerEntity caster, java.util.Set<UUID> scanned) {
        Vec3d cPos = caster.getPos();

        // biggg bursttt
        Box box = new Box(cPos, cPos).expand(BD_FINAL_RADIUS, 6.0, BD_FINAL_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        // Bigger fx
        w.playSound(null, caster.getBlockPos(), ModSounds.BASSDROP, caster.getSoundCategory(), 1.0f, 1.00f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cPos.x, cPos.y + 0.25, cPos.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);

        // explosion
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cPos.x, cPos.y + 0.25, cPos.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);

        // sphere
        spawnExpandingSphere(
                w,
                cPos,
                1.0,              // starting radius
                BD_FINAL_RADIUS,   // final radius
                10,                // shells
                150,               // particles per shell
                ParticleTypes.SCULK_CHARGE_POP
        );

        // extra other particles
        spawnSphereShell(w, cPos, BD_FINAL_RADIUS * 0.85, 18, ParticleTypes.SCULK_SOUL, 0.9);

        // kick off the animated blast sphere
        BassDropState s = BD_STATES.get(caster.getUuid());
        if (s != null) s.blastVizStep = 0;

        for (LivingEntity t : nearby) {
            applyBassBurstHit(caster, t, BD_FINAL_DAMAGE, BD_FINAL_KB, BD_FINAL_UP, BD_FINAL_STUN_TICKS);
        }

        // burst all resonated
        var server = caster.getServer();
        if (server == null) return;

        for (UUID id : scanned) {
            LivingEntity t = null;
            for (ServerWorld ww : server.getWorlds()) {
                Entity e = ww.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) { t = le; break; }
            }
            if (t == null || t == caster) continue;

            // don't hit again if they already were
            if (t.getWorld() == w && t.squaredDistanceTo(caster) <= (BD_FINAL_RADIUS * BD_FINAL_RADIUS)) continue;

            // fx
            if (t.getWorld() instanceof ServerWorld tw) {
                tw.spawnParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.2, t.getZ(),
                        6, 0.35, 0.20, 0.35, 0.02);
                tw.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, caster.getSoundCategory(), 0.7f, 1.3f);
            }
            applyBassBurstHit(caster, t, 0.0f, BD_FINAL_KB, BD_FINAL_UP, BD_REMOTE_STUN_TICKS);
        }
    }

    private static void doBassPullPulse(ServerWorld w, ServerPlayerEntity caster) {
        Vec3d cPos = caster.getPos();

        Box box = new Box(cPos, cPos).expand(BD_PULL_RADIUS, 6.0, BD_PULL_RADIUS);
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        // FX
        w.playSound(null, caster.getBlockPos(), ModSounds.BASSSINGLE, caster.getSoundCategory(), 0.6f, 1.6f);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);
        // sphere
        spawnContractingSphere(
                w,
                cPos,
                BD_PULL_RADIUS,   // start radius
                5.2,              // end radius
                10,                // shells
                120,               // points
                ParticleTypes.SCULK_CHARGE_POP
        );

        spawnSphereShell(w, cPos, BD_PULL_RADIUS * 0.65, 12, ParticleTypes.SCULK_SOUL, 0.9);

        // kick off the animated pull sphere
        BassDropState s = BD_STATES.get(caster.getUuid());
        if (s != null) s.pullVizStep = 0;

        for (LivingEntity t : targets) {
            Vec3d toCaster = cPos.subtract(t.getPos());
            Vec3d horiz = new Vec3d(toCaster.x, 0.0, toCaster.z);
            if (horiz.lengthSquared() < 1.0e-6) continue;

            Vec3d dir = horiz.normalize();

            // idk bro
            t.addVelocity(dir.x * BD_PULL_STRENGTH, BD_PULL_UP, dir.z * BD_PULL_STRENGTH);
            t.velocityModified = true;

            // particle each pulse
            w.spawnParticles(ParticleTypes.SCULK_CHARGE_POP,
                    t.getX(), t.getY() + t.getHeight() * 0.55, t.getZ(),
                    2, 0.12, 0.10, 0.12, 0.01);
        }
    }

    private static void applyBassBurstHit(ServerPlayerEntity caster, LivingEntity target,
                                          float damage, float kb, float up, int stunTicks) {
        if (damage > 0.0f) {
            target.damage(ModDamageTypes.sound(target.getWorld(), caster), damage);
        }

        // knockback
        Vec3d dir = target.getPos().subtract(caster.getPos());
        dir = new Vec3d(dir.x, 0.0, dir.z);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        target.addVelocity(dir.x * kb, up, dir.z * kb);
        target.velocityModified = true;

        // stun and consume if resonated
        if (hasTagPrefix(target, RESONATED)) {
            removeTagPrefix(target, RESONATED);
            target.removeStatusEffect(StatusEffects.GLOWING);
            setSingleTimerTag(target, STUN_LOCK, stunTicks); // time
            setSingleTimerTag(target, STUN_EARRING_ONCE, stunTicks); // sound
            setSingleTimerTag(target, DAMPENED, Math.max(20, stunTicks)); // audio dampen
        }

        // camera shake
        if (target instanceof ServerPlayerEntity spTarget) {
            CameraShake.shake(spTarget, 14, 1.4f);
        }
    }

    // secondary particles
    private static void spawnSphereShell(ServerWorld w, Vec3d center, double radius, int points,
                                         net.minecraft.particle.ParticleEffect particle, double yOffset) {
        for (int i = 0; i < points; i++) {
            // random point on sphere
            double u = w.random.nextDouble();
            double v = w.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            // slight jitter
            double j = 0.12;
            double px = center.x + sx * radius + (w.random.nextDouble() - 0.5) * j;
            double py = center.y + yOffset + sy * radius + (w.random.nextDouble() - 0.5) * j;
            double pz = center.z + sz * radius + (w.random.nextDouble() - 0.5) * j;

            w.spawnParticles(
                    particle,
                    px, py, pz,
                    1,
                    0.0, 0.0, 0.0,
                    0.0
            );
        }
    }

    private static void spawnContractingSphere(ServerWorld w, Vec3d center,
                                               double startRadius, double endRadius,
                                               int shells, int pointsPerShell,
                                               net.minecraft.particle.ParticleEffect particle) {
        // shell 0 = biggest, shell N = smallest
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, startRadius, endRadius);
            spawnSphereShell(w, center, r, pointsPerShell, particle, 0.9);
        }
    }

    private static void spawnExpandingSphere(ServerWorld w, Vec3d center,
                                             double startRadius, double endRadius,
                                             int shells, int pointsPerShell,
                                             net.minecraft.particle.ParticleEffect particle) {
        // shell 0 = smallest, shell N = biggest
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, startRadius, endRadius);
            spawnSphereShell(w, center, r, pointsPerShell, particle, 0.9);
        }
    }

    private static void animateBassSpheres(ServerWorld w, ServerPlayerEntity caster, BassDropState s) {
        //
        if (s.pullVizStep >= 0 && s.pullVizStep < PULL_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);

            double t = (PULL_VIZ_STEPS <= 1) ? 1.0 : (s.pullVizStep / (double)(PULL_VIZ_STEPS - 1));
            // accelerate inward
            t = t * t;

            // instant sphere
            double r = MathHelper.lerp(t, BD_PULL_RADIUS, 2.6); // this multiplier is the aesthetic thingymabob

            spawnSphereShell(w, c, r, PULL_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            // extra depth every other tick
            if ((s.pullVizStep & 1) == 0) {
                spawnSphereShell(w, c, r * 0.55, 22, ParticleTypes.SCULK_SOUL, 0.0);
            }

            s.pullVizStep++;
            if (s.pullVizStep >= PULL_VIZ_STEPS) s.pullVizStep = -1;
        }

        // blast sphere should expand over time
        if (s.blastVizStep >= 0 && s.blastVizStep < BLAST_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);

            double t = (BLAST_VIZ_STEPS <= 1) ? 1.0 : (s.blastVizStep / (double)(BLAST_VIZ_STEPS - 1));
            // make it slow as it expands
            double ease = 1.0 - Math.pow(1.0 - t, 2.0);

            // go past radius slighty
            double r = MathHelper.lerp(ease, 1.0, BD_FINAL_RADIUS * 1.5); // THIS MULTIPLIER SHOULD MAKE IT MORE CLEAR

            spawnSphereShell(w, c, r, BLAST_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            // ground ring so its easier to see
            if ((s.blastVizStep & 1) == 0) {
                spawnGroundRing(w, caster.getPos(), r, 36, ParticleTypes.SCULK_CHARGE_POP);
            }

            s.blastVizStep++;
            if (s.blastVizStep >= BLAST_VIZ_STEPS) s.blastVizStep = -1;
        }
    }

    private static void spawnGroundRing(ServerWorld w, Vec3d center, double radius, int points,
                                        net.minecraft.particle.ParticleEffect particle) {
        double y = center.y + 0.10;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double)points);
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;

            // tiny jitter
            double j = 0.06;
            x += (w.random.nextDouble() - 0.5) * j;
            z += (w.random.nextDouble() - 0.5) * j;

            w.spawnParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    // ULT
    // tweakers
    private static final String ULT_WINDUP = "sd_ult_windup_";
    private static final int ULT_WINDUP_TICKS = 22;        // windup time
    private static final double ULT_RANGE = 50.0;          // beam length
    private static final double ULT_BEAM_RADIUS = 1.35;    // beam thickness
    private static final float ULT_DAMAGE = 7.0f;          // direct hit damage
    private static final float ULT_KB = 2.5f;              // knockback
    private static final float ULT_UP = 1.2f;             // lift
    private static final int ULT_TEAR_STEPS = 36;          // how many times it attempts to damage world
    private static final float ULT_TEAR_CHANCE = 0.45f;    // chance the floor is damaged
    private static final double ULT_PARTICLE_STEP = 0.55;  // how spaced out the particles are
    private static final int ULT_MAX_BLOCKS_BROKEN = 128;     // hard cap so you can't just tunnel with it
    private static final int ULT_SURFACE_SEARCH = 4;         // how far down we search for the surface each step (to cap the explosion)

    private static final class UltState {
        // intentionally empty:
        // used to store stuff so it couldn't be adjusted. Felt awful
        // this existing does allow the ult to be cancelled, though (if implemented)
    }
    private static final Map<UUID, UltState> ULT_STATES = new HashMap<>();

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // store ONLY that the ult is in progress (counterplay window)
        ULT_STATES.put(player.getUuid(), new UltState());

        // setting windup timer
        setSingleTimerTag(player, ULT_WINDUP, ULT_WINDUP_TICKS);

        // windup fx
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_CHARGE,
                player.getSoundCategory(),
                1.0f, 1.0f);

        w.spawnParticles(ParticleTypes.SONIC_BOOM,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1, 0, 0, 0, 0);

        // camerashake
        CameraShake.shake(player, 8, 0.9f);
    }

    private static void tickUltimate(ServerPlayerEntity player) {
        // Only do anything if windup exists
        int left = tickSingleTimer(player, ULT_WINDUP);
        if (left < 0) return;

        // lock movement
        Vec3d v = player.getVelocity();
        player.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        player.velocityModified = true;
        player.setSprinting(false);
        player.fallDistance = 0.0f;

        // movement is already locked, but the fov zoom could be cool i think
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 8, 10, true, false
        ));

        // more particles
        if (player.getWorld() instanceof ServerWorld w) {
            if (left % 4 == 0) {
                w.spawnParticles(ParticleTypes.SCULK_CHARGE_POP,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        6, 0.25, 0.35, 0.25, 0.01);
            }
        }

        // fire!!!
        if (left > 0) return;

        // this shouldn't happen, but cancel ult if ID is gone.
        UltState stored = ULT_STATES.remove(player.getUuid());
        if (stored == null) return;

        fireUltimateBeam(player);
    }

    private static void fireUltimateBeam(ServerPlayerEntity caster) {
        ServerWorld w = caster.getServerWorld();

        // compute beam at the moment of firing (from current player + where they look)
        Vec3d start = caster.getEyePos();
        Vec3d dir = caster.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(ULT_RANGE));

        // fire fx
        w.playSound(null, caster.getBlockPos(),
                ModSounds.RAILGUN,
                caster.getSoundCategory(),
                1.2f, 0.9f);

        CameraShake.shake(caster, 18, 1.6f);

        // beam particles
        spawnBeamParticles(w, caster, start, end);

        // the hitbox passes through walls
        Box search = new Box(start, end).expand(ULT_BEAM_RADIUS + 1.0);
        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, search,
                e -> e.isAlive() && e != caster);

        for (LivingEntity target : hits) { // for each entity hit
            if (!segmentIntersectsExpandedAabb(start, end, target.getBoundingBox().expand(ULT_BEAM_RADIUS))) continue; // check

            // If they were resonated, stun
            if (hasTagPrefix(target, RESONATED)) {
                applyAbilityHit(caster, target, 0.0f, true);
            }

            // ult damage
            target.damage(ModDamageTypes.sound(target.getWorld(), caster), ULT_DAMAGE);

            // ult knockback
            target.addVelocity(dir.x * ULT_KB, ULT_UP, dir.z * ULT_KB);
            target.velocityModified = true;
        }

        // environmental damage
        tearGroundAlongBeam(w, start, end);
    }

    private static void spawnBeamParticles(ServerWorld w, ServerPlayerEntity caster, Vec3d start, Vec3d end) { // minecraft culls particles like this annoyingly - it should still show for the caster.
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp((int)(len / ULT_PARTICLE_STEP), 10, 220);
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            // core beam
            w.spawnParticles(caster, ParticleTypes.SONIC_BOOM, true,
                    p.x, p.y, p.z,
                    1, 0, 0, 0, 0);

            // extras
            if ((i & 1) == 0) {
                w.spawnParticles(caster, ParticleTypes.SCULK_CHARGE_POP, true,
                        p.x, p.y, p.z,
                        3, 0.18, 0.18, 0.18, 0.01);
            } else {
                w.spawnParticles(caster, ParticleTypes.SCULK_SOUL, true,
                        p.x, p.y, p.z,
                        1, 0.10, 0.10, 0.10, 0.0);
            }

            p = p.add(step);
        }

        // end burst
        w.spawnParticles(caster, ParticleTypes.EXPLOSION_EMITTER, true,
                end.x, end.y, end.z,
                1, 0, 0, 0, 0);
    }

    private static void tearGroundAlongBeam(ServerWorld w, Vec3d start, Vec3d end) { // environmental damage - tears up ground and does explosion on collision
        // i should probably implement this at some point.
        // if (!w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) return;

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp(ULT_TEAR_STEPS, 8, 80);
        Vec3d step = delta.multiply(1.0 / steps);

        int broken = 0;

        // antispam
        BlockPos lastBoom = null;
        int boomCooldown = 0;

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            // COLLISION EXPLOSION
            if (boomCooldown > 0) boomCooldown--;

            BlockPos beamPos = BlockPos.ofFloored(p.x, p.y, p.z);
            BlockState beamState = w.getBlockState(beamPos);

            // collision test
            float bh = beamState.getHardness(w, beamPos);
            boolean collides = !beamState.isAir()
                    && bh >= 0.0f
                    && bh < 30.0f
                    && w.getBlockEntity(beamPos) == null;

            if (collides && boomCooldown <= 0) {

                // don't explode repeatedly on the exact same spot
                if (lastBoom == null || lastBoom.getManhattanDistance(beamPos) >= 2) {

                    // FX
                    w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                            beamPos.getX() + 0.5, beamPos.getY() + 0.5, beamPos.getZ() + 0.5,
                            1, 0, 0, 0, 0);

                    w.playSound(null, beamPos,
                            SoundEvents.ENTITY_GENERIC_EXPLODE,
                            casterSoundCategoryFallback(w),
                            0.9f, 1.2f);

                    // add blocks to limit
                    broken += breakBlastCapped(w, beamPos, 2, (ULT_MAX_BLOCKS_BROKEN - broken));

                    lastBoom = beamPos;
                    boomCooldown = 3; // a few ticks gap so thick walls don't spam explosions
                }
            }

            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            //SURFACE TEAR
            BlockPos base = BlockPos.ofFloored(p.x, p.y, p.z);

            BlockPos hitPos = null;
            BlockState hitState = null;

            for (int dy = 0; dy <= ULT_SURFACE_SEARCH; dy++) {
                BlockPos q = base.down(dy);
                BlockState s = w.getBlockState(q);

                if (s.isAir()) continue;

                // skip certain blocks
                if (w.getBlockEntity(q) != null) continue; // air
                if (s.getHardness(w, q) < 0.0f) continue; // unbreakable
                if (s.getHardness(w, q) >= 30.0f) continue; // too hard

                hitPos = q;
                hitState = s;
                break;
            }

            if (hitPos != null && hitState != null) {

                // ground
                if (w.random.nextFloat() < ULT_TEAR_CHANCE) {
                    w.spawnParticles(
                            new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, hitState),
                            hitPos.getX() + 0.5, hitPos.getY() + 0.75, hitPos.getZ() + 0.5,
                            12,
                            0.35, 0.25, 0.35,
                            0.10
                    );
                    w.spawnParticles(ParticleTypes.CRIT,
                            hitPos.getX() + 0.5, hitPos.getY() + 1.1, hitPos.getZ() + 0.5,
                            2, 0.10, 0.10, 0.10, 0.0);
                }

                // break blocks
                boolean drop = true;
                if (w.breakBlock(hitPos, drop)) {
                    broken++;
                }
            }

            p = p.add(step);
        }
    }

    private static net.minecraft.sound.SoundCategory casterSoundCategoryFallback(ServerWorld w) {
        // fallback category so this method doesn't need the caster parameter (there's probably a much for efficient way to do this)
        return net.minecraft.sound.SoundCategory.PLAYERS;
    }

    private static int breakBlastCapped(ServerWorld w, BlockPos center, int radius, int budgetLeft) { // caps it so it can't be used for tunneling
        if (budgetLeft <= 0) return 0;

        int broken = 0;

        // blast cube
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (broken >= budgetLeft) return broken;

                    // skips corners slightly
                    int md = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (md > radius + 1) continue;

                    BlockPos p = center.add(dx, dy, dz);
                    BlockState s = w.getBlockState(p);

                    if (s.isAir()) continue;
                    if (s.getHardness(w, p) < 0.0f) continue;
                    if (w.getBlockEntity(p) != null) continue;
                    if (s.getHardness(w, p) >= 30.0f) continue; // too hard

                    // particle per broken block sometimes
                    if (w.random.nextFloat() < 0.35f) {
                        w.spawnParticles(
                                new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, s),
                                p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                                6,
                                0.25, 0.25, 0.25,
                                0.08
                        );
                    }
                    boolean drop = true;
                    if (w.breakBlock(p, drop)) {
                        broken++;
                    }
                }
            }
        }
        return broken;
    }

    // sample along segment
    private static boolean segmentIntersectsExpandedAabb(Vec3d a, Vec3d b, Box box) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 0.001) return box.contains(a);

        int steps = MathHelper.clamp((int)(len * 6.0), 12, 220); // more steps = more accurate
        Vec3d step = d.multiply(1.0 / steps);

        Vec3d p = a;
        for (int i = 0; i <= steps; i++) {
            if (box.contains(p)) return true;
            p = p.add(step);
        }
        return false;
    }

    /* ============================================================
       HELPER STUFF
       ============================================================ */

    // overload for mobs
    private static boolean hasTagPrefix(Entity e, String prefix) {
        for (String tag : e.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return;
            }
        }
    }

    private static void removeTagPrefix(Entity e, String prefix) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return;
            }
        }
    }

    // the classics
    private static void setSingleTimerTag(ServerPlayerEntity p, String prefix, int ticks) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + ticks);
    }

    private static void setSingleTimerTag(Entity e, String prefix, int ticks) {
        removeTagPrefix(e, prefix);
        e.getCommandTags().add(prefix + ticks);
    }

    private static int tickSingleTimer(ServerPlayerEntity p, String prefix) {
        String found = null;
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(prefix)) { found = tag; break; }
        }
        if (found == null) return -1;

        p.getCommandTags().remove(found);

        int ticks;
        try {
            ticks = Integer.parseInt(found.substring(prefix.length())) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }

        if (ticks > 0) p.getCommandTags().add(prefix + ticks);
        return ticks;
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

    private static final String STUN_AUDIO_STEP = "sd_stun_audio_step_";

    public static void tickStun(ServerPlayerEntity p) { // enforces stun on players - called from the actual server tick loop. This does not fully stun like entity stuns do.
        // only do anything if stunned
        int left = tickSingleTimer(p, STUN_LOCK);
        if (left < 0) return;

        // play sound once
        if (hasTagPrefix(p, STUN_EARRING_ONCE)) {
            removeTagPrefix(p, STUN_EARRING_ONCE);

            // play only to the stunned player
            p.playSound(ModSounds.EARRING, net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f);
        }

        // throttle packets to stop spam
        int step = tickSingleTimer(p, STUN_AUDIO_STEP);
        if (step <= 0) {
            setSingleTimerTag(p, STUN_AUDIO_STEP, 10); // every 10 ticks while stunned

            // dampen the player audio
            RenderPackets.sendStunAudio(p, left);

            // give effects
            p.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SLOWNESS, 15, 2, true, false
            ));
            p.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 15, 1, true, false
            ));
        }

        /* stop velocity every tick
        Vec3d v = p.getVelocity();
        p.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        p.velocityModified = true; */

        // stop sprint immediately
        p.setSprinting(false);

        // knock them downward a bit if they're in the air
        p.fallDistance = 0.0f;

        // camerashake
        CameraShake.shake(p, 2, 1.9f);
    }

    private static final String STUN_EARRING_ONCE = "sd_stun_earring_"; // initial stun sound marker

    public static void tickStunEntity(LivingEntity e) { // this should stop entities from doing anything entirely
        int left = tickSingleTimer(e, STUN_LOCK);
        if (left < 0) return;

        // stop AI movement from battling slow
        if (e instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.getNavigation().stop();
            mob.getMoveControl().strafeTo(0.0f, 0.0f);
            mob.setTarget(null);
        }

        /* velocity stop (was not really necessary for entities, just made knockback a pain)
        Vec3d v = e.getVelocity();
        e.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        e.velocityModified = true; */

        // stop them from looking like crazy
        e.setYaw(e.prevYaw);
        e.setPitch(e.prevPitch);

        // effects
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 6, 8, true, false));
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 6, 1, true, false));
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return "Sound"; }
    @Override public String getPrimaryName() { return "Doppler"; }
    @Override public String getSecondaryName() { return "Bass Drop"; }
    @Override public String getUltimateName() { return "Sonic Shriek"; }

    @Override public long getPrimaryCooldownMs() { return 2_000; }
    @Override public long getSecondaryCooldownMs() { return 5_000; }
    @Override public long getUltimateCooldownMs() { return 12_000; }

    @Override
    public String getOverviewDescription() {
        return "Sound is a primarily ranged power that also offers some (although probably not that great practically) utility " +
                "in the form of tracking and stunning. These abilities pierce through walls and work best at longer ranges.";
    }

    @Override
    public String getPassiveName() {
        return "Resonance";
    }

    @Override
    public String getPassiveDescription() {
        return "You listening senses are heightened: nearby entities leave lingering sound trails and if they are too conspicuous or close will become resonated," +
                " directing you to their position and making them glow. Hitting resonated targets with your abilities deals bonus damage and stuns briefly." +
                " Actions such as taking damage are considered conspicuous meaning hits with your abilities can resonate targets. Only you can see the resonation particles" +
                " but effects like glowing are seen by all.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Fire a small, long range, piercing projectile in the direction you are looking that damages any entity it touches. Resonated targets will be stunned and take extra damage,";
    }

    @Override
    public String getSecondaryDescription() {
        return "Do multiple small sound pulses that pull in nearby entities (resonating them if they get too close) and unleash a burst that knocks" +
                "back any nearby entities and stunning + dealing extra damage to anything resonated.";
    }

    @Override
    public String getUltimateDescription() {
        return "Stop all movement and charge up, unleashing a beam  with huge range that tears up nearby blocks and pierces walls. The range" +
                "is a lot wider then you will probably see (particle rendering) and hit entities will take high damage and knockback and will also" +
                "be stunned and take extra damage if resonated; Think of it like a bigger warden beam.";
    }
}