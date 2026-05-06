package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.SonicBoltEntity;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

public class SoundPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, SoundCasterState> CASTER_STATES = new HashMap<>();
    private static final Map<UUID, SoundVictimState> VICTIM_STATES = new HashMap<>();

    private static class SoundCasterState {
        int trailStep = 0;
        int scanStep = 0;
        int hbStep = 0;
        int ultWindup = 0;
        boolean ultPendingFire = false;
        BassDropState bdState = null;
    }

    private static class SoundVictimState {
        int score = 0;
        int resonatedTicks = 0;
        int dampenedTicks = 0;
        int hbStep = 0;
        long lastSeenTick = 0;
        long lastTickTime = 0;
    }

    private static final class BassDropState {
        int nextPulse;
        int nextPulseIn;
        boolean finalPending;
        int finalIn;
        final java.util.Set<UUID> scanned = new java.util.HashSet<>();
        int pullVizStep = -1;
        int blastVizStep = -1;
    }

    private static SoundCasterState getCasterState(ServerPlayerEntity player) {
        return CASTER_STATES.computeIfAbsent(player.getUuid(), k -> new SoundCasterState());
    }

    private static SoundVictimState getVictimState(LivingEntity victim) {
        return VICTIM_STATES.computeIfAbsent(victim.getUuid(), k -> new SoundVictimState());
    }

    /* ============================================================
       CONSTANTS & TUNING
       ============================================================ */

    // Passive
    private static final double RES_RADIUS         = 14.0;
    private static final int    RES_SCAN_INTERVAL  = 4;
    private static final int    RES_TRAIL_INTERVAL = 2;
    private static final double RES_INSTANT_RADIUS = 3.0;
    private static final int    RESONATED_TICKS    = 80;
    private static final int    RES_THRESHOLD      = 15;
    private static final int    RES_DECAY_PER_SCAN = 1;
    private static final int    MAX_TRAIL_TARGETS  = 14;
    private static final float    BURST_BONUS_DAMAGE  = 9.5f;

    private static final int PTS_SLOW_MOVE = 1;
    private static final int PTS_FAST_MOVE = 2;
    private static final int PTS_SPRINT    = 3;
    private static final int PTS_JUMP      = 3;
    private static final int PTS_FALL      = 4;
    private static final int PTS_HURT      = 2;
    private static final int PTS_WATER     = 1;

    // Primary
    private static final double BOLT_SPAWN_OFFSET  = 0.6;
    private static final double BOLT_SPEED         = 1.7;

    // Secondary
    private static final int    BD_PULSE_COUNT       = 6;
    private static final int    BD_PULSE_GAP_TICKS   = 4;
    private static final int    BD_FINAL_DELAY_TICKS = 2;
    private static final double BD_PULL_RADIUS       = 10.0;
    private static final double BD_FINAL_RADIUS      = 7.0;
    private static final float  BD_PULL_STRENGTH     = 0.24f;
    private static final float  BD_PULL_UP           = 0.02f;
    private static final float  BD_FINAL_KB          = 1.00f;
    private static final float  BD_FINAL_UP          = 0.30f;
    private static final float  BD_FINAL_DAMAGE      = 15.5f;
    private static final int    BD_FINAL_STUN_TICKS  = 40;
    private static final int    BD_REMOTE_STUN_TICKS = 30;

    private static final int PULL_VIZ_STEPS  = 8;
    private static final int BLAST_VIZ_STEPS = 8;
    private static final int PULL_POINTS     = 90;
    private static final int BLAST_POINTS    = 120;

    // Ultimate
    private static final int    ULT_WINDUP_TICKS      = 22;
    private static final double ULT_RANGE             = 50.0;
    private static final double ULT_BEAM_RADIUS       = 1.35;
    private static final float  ULT_DAMAGE            = 20.5f;
    private static final float  ULT_KB                = 2.5f;
    private static final float  ULT_UP                = 1.2f;
    private static final int    ULT_TEAR_STEPS        = 36;
    private static final float  ULT_TEAR_CHANCE       = 0.45f;
    private static final float  ULT_DROP_CHANCE       = 0.15f;
    private static final double ULT_PARTICLE_STEP     = 0.55;
    private static final int    ULT_MAX_BLOCKS_BROKEN = 150;
    private static final int    ULT_SURFACE_SEARCH    = 4;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("sd_")); // Clean legacy
        CASTER_STATES.put(player.getUuid(), new SoundCasterState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("sd_"));
        CASTER_STATES.remove(player.getUuid());

        player.removeStatusEffect(StatusEffects.SLOWNESS);

        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                // Remove mid-air Sonic Bolts owned by this player
                w.getEntitiesByClass(SonicBoltEntity.class, player.getBoundingBox().expand(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);

                // Strip GLOWING and STUN from entities that the player resonated
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(150), LivingEntity::isAlive)) {
                    SoundVictimState state = VICTIM_STATES.get(e.getUuid());
                    if (state != null && state.resonatedTicks > 0) {
                        state.resonatedTicks = 0;
                        e.removeStatusEffect(StatusEffects.GLOWING);
                        e.removeStatusEffect(ModEffects.STUN);
                    }
                }
            }
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        SoundCasterState caster = getCasterState(player);

        if (caster.trailStep > 0) caster.trailStep--;
        if (caster.scanStep > 0) caster.scanStep--;
        if (caster.hbStep > 0) caster.hbStep--;

        if (PassiveManager.isEnabled(player)) {
            tickResonancePassive(player, caster);
        }

        if (caster.bdState != null) {
            tickBassDrop(player, caster);
        }

        if (caster.ultWindup > 0) {
            tickUltimate(player, caster);
        }

        // Clean up global victims map occasionally to prevent memory leaks
        if (player.age % 100 == 0) {
            long now = player.getServerWorld().getTime();
            VICTIM_STATES.values().removeIf(v -> (now - v.lastSeenTick) > 100 && v.score <= 0 && v.resonatedTicks <= 0);
        }
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {}

    public static void applyAbilityHit(ServerPlayerEntity caster, LivingEntity target, float baseDamage, boolean allowBurst) {
        target.damage(ModDamageTypes.sound(target.getWorld(), caster), baseDamage);

        if (!allowBurst) return;

        SoundVictimState vState = VICTIM_STATES.get(target.getUuid());
        if (vState == null || vState.resonatedTicks <= 0) return;

        vState.resonatedTicks = 0;
        target.removeStatusEffect(StatusEffects.GLOWING);

        target.damage(ModDamageTypes.sound(target.getWorld(), caster), BURST_BONUS_DAMAGE);

        // resets velocity before stunning so they stop moving
        target.setVelocity(0, Math.min(target.getVelocity().y, 0.0), 0);
        target.velocityModified = true;

        // Apply stun
        target.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, 35, 0, false, false, true));
        vState.dampenedTicks = 45;

        if (target instanceof ServerPlayerEntity targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 3, 15, 0.08f);
        }

        ServerWorld sw = (ServerWorld) target.getWorld();
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getHeight() * 0.6, target.getZ(), 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(), 6, 0.35, 0.25, 0.35, 0.02);
        sw.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, caster.getSoundCategory(), 0.9f, 1.3f);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void tickResonancePassive(ServerPlayerEntity player, SoundCasterState caster) {
        ServerWorld w = player.getServerWorld();
        long nowTick = w.getTime();

        Box box = new Box(player.getPos(), player.getPos()).expand(RES_RADIUS, 6.0, RES_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        if (caster.trailStep <= 0) {
            caster.trailStep = RES_TRAIL_INTERVAL;
            int sent = 0;
            for (LivingEntity e : nearby) {
                if (sent >= MAX_TRAIL_TARGETS) break;
                RenderPackets.sendResonanceTrail(player, e.getId(), 2, 1.0f);
                sent++;
            }
        }

        if (caster.scanStep > 0) return;
        caster.scanStep = RES_SCAN_INTERVAL;

        for (LivingEntity e : nearby) {
            SoundVictimState vState = getVictimState(e);
            vState.lastSeenTick = nowTick;

            // Decouple ticking from the 4-tick scan loop to ensure accurate countdowns
            if (vState.lastTickTime != nowTick) {
                long delta = nowTick - vState.lastTickTime;
                if (delta > 20) delta = 20; // prevent massive jumps
                vState.lastTickTime = nowTick;

                if (vState.resonatedTicks > 0) vState.resonatedTicks -= delta;
                if (vState.dampenedTicks > 0) vState.dampenedTicks -= delta;
                if (vState.hbStep > 0) vState.hbStep -= delta;
            }

            vState.score = Math.max(0, vState.score - RES_DECAY_PER_SCAN);
            vState.score += computeConspicuousPoints(e);

            if (player.squaredDistanceTo(e) <= (RES_INSTANT_RADIUS * RES_INSTANT_RADIUS)) {
                vState.score = RES_THRESHOLD;
            }

            if (vState.score < RES_THRESHOLD) {
                float frac = vState.score / (float) RES_THRESHOLD;
                if (vState.resonatedTicks <= 0 && frac >= 0.70f) {
                    if (caster.hbStep <= 0 && vState.hbStep <= 0) {
                        float t = MathHelper.clamp((frac - 0.70f) / 0.30f, 0.0f, 1.0f);
                        int interval = (int) MathHelper.lerp(16.0f, 5.0f, t);

                        caster.hbStep = Math.max(3, interval - 2);
                        vState.hbStep = interval;

                        float vol = 0.25f + 0.35f * t;
                        float pitch = 0.85f + 0.25f * t;

                        player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, vol, pitch);

                        if (e instanceof ServerPlayerEntity spTarget) {
                            spTarget.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, vol * 0.9f, pitch);
                        }

                        if (frac >= 0.92f) {
                            RenderPackets.sendResonanceRing(player, e.getId(), 1.2f);
                            if (e instanceof ServerPlayerEntity spTarget2) {
                                RenderPackets.sendResonanceRing(spTarget2, e.getId(), 1.0f);
                            }
                        }
                    }
                }
            }

            if (vState.score >= RES_THRESHOLD) {
                boolean already = vState.resonatedTicks > 0;

                vState.resonatedTicks = RESONATED_TICKS;
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, RESONATED_TICKS + 60, 0, true, true));

                if (!already) {
                    w.playSound(null, e.getBlockPos(),
                            SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                            player.getSoundCategory(),
                            0.7f, 1.1f);

                    w.spawnParticles(ParticleTypes.SCULK_SOUL,
                            e.getX(), e.getY() + 1.0, e.getZ(),
                            6, 0.35, 0.45, 0.35, 0.02);

                    RenderPackets.sendResonanceLine(player, e.getId(), 20, 1.0f);
                    if (e instanceof ServerPlayerEntity spTarget) {
                        RenderPackets.sendResonanceLine(spTarget, e.getId(), 20, 1.0f);
                    }
                }
            }
        }
    }

    private static int computeConspicuousPoints(LivingEntity e) {
        int pts = 0;
        Vec3d v = e.getVelocity();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);

        if (h > 0.08) pts += PTS_SLOW_MOVE;
        if (h > 0.22) pts += PTS_FAST_MOVE;
        if (e.isSprinting()) pts += PTS_SPRINT;
        if (!e.isOnGround() && v.y > 0.10) pts += PTS_JUMP;
        if (!e.isOnGround() && v.y < -0.25) pts += PTS_FALL;
        if (e.hurtTime > 0) pts += PTS_HURT;
        if (e.isTouchingWater()) pts += PTS_WATER;
        return pts;
    }

    /* ============================================================
       PRIMARY  –  Doppler
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        SonicBoltEntity bolt = new SonicBoltEntity(ModEntities.SONIC_BOLT, w);
        bolt.setOwner(player);

        Vec3d start = player.getEyePos().add(player.getRotationVec(1.0f).multiply(BOLT_SPAWN_OFFSET));
        bolt.setPos(start.x, start.y, start.z);

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        bolt.setVelocity(dir.multiply(BOLT_SPEED));

        w.spawnEntity(bolt);
        w.playSound(null, player.getBlockPos(), ModSounds.BOLT, player.getSoundCategory(), 0.4f, 1.2f);
    }

    /* ============================================================
       SECONDARY  –  Bass Drop
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        SoundCasterState caster = getCasterState(player);

        BassDropState s = new BassDropState();
        s.nextPulse = BD_PULSE_COUNT;
        s.nextPulseIn = 0;
        s.finalPending = false;

        caster.bdState = s;

        w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, player.getSoundCategory(), 0.7f, 1.4f);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
    }

    private static void tickBassDrop(ServerPlayerEntity caster, SoundCasterState state) {
        BassDropState s = state.bdState;
        ServerWorld w = caster.getServerWorld();

        animateBassSpheres(w, caster, s);

        if (!s.finalPending) {
            s.nextPulseIn--;

            if (s.nextPulseIn <= 0 && s.nextPulse > 0) {
                doBassPullPulse(w, caster, s);
                s.nextPulse--;
                s.nextPulseIn = BD_PULSE_GAP_TICKS;

                if (s.nextPulse <= 0) {
                    s.finalPending = true;
                    s.finalIn = BD_FINAL_DELAY_TICKS;
                }
            }
            return;
        }

        s.finalIn--;
        if (s.finalIn > 0) return;

        doBassFinalBurst(w, caster, state, s.scanned);
        state.bdState = null;
    }

    private static void doBassFinalBurst(ServerWorld w, ServerPlayerEntity caster, SoundCasterState state, java.util.Set<UUID> scanned) {
        Vec3d cPos = caster.getPos();

        Box box = new Box(cPos, cPos).expand(BD_FINAL_RADIUS, 6.0, BD_FINAL_RADIUS);
        List<LivingEntity> nearby = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        w.playSound(null, caster.getBlockPos(), ModSounds.BASSDROP, caster.getSoundCategory(), 1.0f, 1.00f);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cPos.x, cPos.y + 0.25, cPos.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);

        spawnExpandingSphere(w, cPos, 1.0, BD_FINAL_RADIUS, 10, 150, ParticleTypes.SCULK_CHARGE_POP);
        spawnSphereShell(w, cPos, BD_FINAL_RADIUS * 0.85, 18, ParticleTypes.SCULK_SOUL, 0.9);

        if (state.bdState != null) state.bdState.blastVizStep = 0;

        for (LivingEntity t : nearby) {
            applyBassBurstHit(caster, t, BD_FINAL_DAMAGE, BD_FINAL_KB, BD_FINAL_UP, BD_FINAL_STUN_TICKS);
        }

        var server = caster.getServer();
        if (server == null) return;

        for (UUID id : scanned) {
            LivingEntity t = null;
            for (ServerWorld ww : server.getWorlds()) {
                Entity e = ww.getEntity(id);
                if (e instanceof LivingEntity le && le.isAlive()) { t = le; break; }
            }
            if (t == null || t == caster) continue;

            if (t.getWorld() == w && t.squaredDistanceTo(caster) <= (BD_FINAL_RADIUS * BD_FINAL_RADIUS)) continue;

            if (t.getWorld() instanceof ServerWorld tw) {
                tw.spawnParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 0.2, t.getZ(), 6, 0.35, 0.20, 0.35, 0.02);
                tw.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, caster.getSoundCategory(), 0.7f, 1.3f);
            }
            applyBassBurstHit(caster, t, 0.0f, BD_FINAL_KB, BD_FINAL_UP, BD_REMOTE_STUN_TICKS);
        }
        CameraShake.shakeNearby(caster, 5,15, 0.04f);
    }

    private static void doBassPullPulse(ServerWorld w, ServerPlayerEntity caster, BassDropState s) {
        Vec3d cPos = caster.getPos();

        Box box = new Box(cPos, cPos).expand(BD_PULL_RADIUS, 6.0, BD_PULL_RADIUS);
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        w.playSound(null, caster.getBlockPos(), ModSounds.BASSSINGLE, caster.getSoundCategory(), 0.6f, 1.6f);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, cPos.x, cPos.y + 1.0, cPos.z, 1, 0, 0, 0, 0);
        spawnContractingSphere(w, cPos, BD_PULL_RADIUS, 5.2, 10, 120, ParticleTypes.SCULK_CHARGE_POP);
        spawnSphereShell(w, cPos, BD_PULL_RADIUS * 0.65, 12, ParticleTypes.SCULK_SOUL, 0.9);

        s.pullVizStep = 0;

        for (LivingEntity t : targets) {
            Vec3d toCaster = cPos.subtract(t.getPos());
            Vec3d horiz = new Vec3d(toCaster.x, 0.0, toCaster.z);
            if (horiz.lengthSquared() < 1.0e-6) continue;

            Vec3d dir = horiz.normalize();
            t.addVelocity(dir.x * BD_PULL_STRENGTH, BD_PULL_UP, dir.z * BD_PULL_STRENGTH);
            t.velocityModified = true;

            w.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, t.getX(), t.getY() + t.getHeight() * 0.55, t.getZ(), 2, 0.12, 0.10, 0.12, 0.01);
            s.scanned.add(t.getUuid());
        }
    }

    private static void applyBassBurstHit(ServerPlayerEntity caster, LivingEntity target, float damage, float kb, float up, int stunTicks) {
        if (damage > 0.0f) {
            target.damage(ModDamageTypes.sound(target.getWorld(), caster), damage);
        }

        Vec3d dir = target.getPos().subtract(caster.getPos());
        dir = new Vec3d(dir.x, 0.0, dir.z);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        target.addVelocity(dir.x * kb, up, dir.z * kb);
        target.velocityModified = true;

        // Apply STUN Effect if resonated
        SoundVictimState vState = VICTIM_STATES.get(target.getUuid());
        if (vState != null && vState.resonatedTicks > 0) {
            vState.resonatedTicks = 0;
            target.removeStatusEffect(StatusEffects.GLOWING);
            target.addStatusEffect(new StatusEffectInstance(ModEffects.STUN, stunTicks, 0, false, false, true));
            vState.dampenedTicks = Math.max(20, stunTicks);

            if (target instanceof ServerPlayerEntity spTarget) {
                spTarget.playSound(ModSounds.EARRING, net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f);
            }
        }

        if (target instanceof ServerPlayerEntity spTarget) {
            CameraShake.shake(spTarget, 14, 1.4f);
        }
    }

    private static void spawnSphereShell(ServerWorld w, Vec3d center, double radius, int points, net.minecraft.particle.ParticleEffect particle, double yOffset) {
        for (int i = 0; i < points; i++) {
            double u = w.random.nextDouble();
            double v = w.random.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);

            double sx = Math.sin(phi) * Math.cos(theta);
            double sy = Math.cos(phi);
            double sz = Math.sin(phi) * Math.sin(theta);

            double j = 0.12;
            double px = center.x + sx * radius + (w.random.nextDouble() - 0.5) * j;
            double py = center.y + yOffset + sy * radius + (w.random.nextDouble() - 0.5) * j;
            double pz = center.z + sz * radius + (w.random.nextDouble() - 0.5) * j;

            w.spawnParticles(particle, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void spawnContractingSphere(ServerWorld w, Vec3d center, double startRadius, double endRadius, int shells, int pointsPerShell, net.minecraft.particle.ParticleEffect particle) {
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, startRadius, endRadius);
            spawnSphereShell(w, center, r, pointsPerShell, particle, 0.9);
        }
    }

    private static void spawnExpandingSphere(ServerWorld w, Vec3d center, double startRadius, double endRadius, int shells, int pointsPerShell, net.minecraft.particle.ParticleEffect particle) {
        for (int s = 0; s < shells; s++) {
            double t = (shells <= 1) ? 1.0 : (s / (double)(shells - 1));
            double r = MathHelper.lerp(t, startRadius, endRadius);
            spawnSphereShell(w, center, r, pointsPerShell, particle, 0.9);
        }
    }

    private static void animateBassSpheres(ServerWorld w, ServerPlayerEntity caster, BassDropState s) {
        if (s.pullVizStep >= 0 && s.pullVizStep < PULL_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);
            double t = (PULL_VIZ_STEPS <= 1) ? 1.0 : (s.pullVizStep / (double)(PULL_VIZ_STEPS - 1));
            t = t * t;
            double r = MathHelper.lerp(t, BD_PULL_RADIUS, 2.6);

            spawnSphereShell(w, c, r, PULL_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            if ((s.pullVizStep & 1) == 0) {
                spawnSphereShell(w, c, r * 0.55, 22, ParticleTypes.SCULK_SOUL, 0.0);
            }

            s.pullVizStep++;
            if (s.pullVizStep >= PULL_VIZ_STEPS) s.pullVizStep = -1;
        }

        if (s.blastVizStep >= 0 && s.blastVizStep < BLAST_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);
            double t = (BLAST_VIZ_STEPS <= 1) ? 1.0 : (s.blastVizStep / (double)(BLAST_VIZ_STEPS - 1));
            double ease = 1.0 - Math.pow(1.0 - t, 2.0);
            double r = MathHelper.lerp(ease, 1.0, BD_FINAL_RADIUS * 1.5);

            spawnSphereShell(w, c, r, BLAST_POINTS, ParticleTypes.SCULK_CHARGE_POP, 0.0);

            if ((s.blastVizStep & 1) == 0) {
                spawnGroundRing(w, caster.getPos(), r, 36, ParticleTypes.SCULK_CHARGE_POP);
            }

            s.blastVizStep++;
            if (s.blastVizStep >= BLAST_VIZ_STEPS) s.blastVizStep = -1;
        }
    }

    private static void spawnGroundRing(ServerWorld w, Vec3d center, double radius, int points, net.minecraft.particle.ParticleEffect particle) {
        double y = center.y + 0.10;
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double)points);
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;

            double j = 0.06;
            x += (w.random.nextDouble() - 0.5) * j;
            z += (w.random.nextDouble() - 0.5) * j;

            w.spawnParticles(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    /* ============================================================
       ULTIMATE  –  Sonic Shriek
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        SoundCasterState caster = getCasterState(player);

        caster.ultWindup = ULT_WINDUP_TICKS;
        caster.ultPendingFire = true;

        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, player.getSoundCategory(), 1.0f, 1.0f);
        w.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        CameraShake.shake(player, 8, 0.9f);
    }

    private static void tickUltimate(ServerPlayerEntity player, SoundCasterState state) {
        if (state.ultWindup <= 0) return;
        state.ultWindup--;

        Vec3d v = player.getVelocity();
        player.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        player.velocityModified = true;
        player.setSprinting(false);
        player.fallDistance = 0.0f;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 10, true, false));

        if (player.getWorld() instanceof ServerWorld w) {
            if (state.ultWindup % 4 == 0) {
                w.spawnParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.25, 0.35, 0.25, 0.01);
            }
        }

        if (state.ultWindup > 0) return;

        if (!state.ultPendingFire) return;
        state.ultPendingFire = false;

        fireUltimateBeam(player);
    }

    private static void fireUltimateBeam(ServerPlayerEntity caster) {
        ServerWorld w = caster.getServerWorld();

        Vec3d start = caster.getEyePos();
        Vec3d dir = caster.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(ULT_RANGE));

        w.playSound(null, caster.getBlockPos(), ModSounds.RAILGUN, caster.getSoundCategory(), 1.2f, 0.9f);
        CameraShake.shake(caster, 18, 1.6f);

        spawnBeamParticles(w, caster, start, end);

        Box search = new Box(start, end).expand(ULT_BEAM_RADIUS + 1.0);
        List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, search, e -> e.isAlive() && e != caster);

        for (LivingEntity target : hits) {
            if (!segmentIntersectsExpandedAabb(start, end, target.getBoundingBox().expand(ULT_BEAM_RADIUS))) continue;

            SoundVictimState vState = VICTIM_STATES.get(target.getUuid());
            if (vState != null && vState.resonatedTicks > 0) {
                applyAbilityHit(caster, target, 0.0f, true);
            }

            target.damage(ModDamageTypes.sound(target.getWorld(), caster), ULT_DAMAGE);
            target.addVelocity(dir.x * ULT_KB, ULT_UP, dir.z * ULT_KB);
            target.velocityModified = true;
        }

        tearGroundAlongBeam(w, start, end);
    }

    private static void spawnBeamParticles(ServerWorld w, ServerPlayerEntity caster, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp((int)(len / ULT_PARTICLE_STEP), 10, 220);
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            w.spawnParticles(caster, ParticleTypes.SONIC_BOOM, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);

            if ((i & 1) == 0) {
                w.spawnParticles(caster, ParticleTypes.SCULK_CHARGE_POP, true, p.x, p.y, p.z, 3, 0.18, 0.18, 0.18, 0.01);
            } else {
                w.spawnParticles(caster, ParticleTypes.SCULK_SOUL, true, p.x, p.y, p.z, 1, 0.10, 0.10, 0.10, 0.0);
            }
            p = p.add(step);
        }

        w.spawnParticles(caster, ParticleTypes.EXPLOSION_EMITTER, true, end.x, end.y, end.z, 1, 0, 0, 0, 0);
    }

    private static void tearGroundAlongBeam(ServerWorld w, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        int steps = MathHelper.clamp(ULT_TEAR_STEPS, 8, 80);
        Vec3d step = delta.multiply(1.0 / steps);

        int broken = 0;
        BlockPos lastBoom = null;
        int boomCooldown = 0;

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            if (boomCooldown > 0) boomCooldown--;

            BlockPos beamPos = BlockPos.ofFloored(p.x, p.y, p.z);
            BlockState beamState = w.getBlockState(beamPos);

            float bh = beamState.getHardness(w, beamPos);
            boolean collides = !beamState.isAir() && bh >= 0.0f && bh < 30.0f && w.getBlockEntity(beamPos) == null;

            if (collides && boomCooldown <= 0) {
                if (lastBoom == null || lastBoom.getManhattanDistance(beamPos) >= 2) {
                    w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, beamPos.getX() + 0.5, beamPos.getY() + 0.5, beamPos.getZ() + 0.5, 1, 0, 0, 0, 0);
                    w.playSound(null, beamPos, SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS, 0.9f, 1.2f);
                    broken += breakBlastCapped(w, beamPos, 2, (ULT_MAX_BLOCKS_BROKEN - broken));
                    lastBoom = beamPos;
                    boomCooldown = 3;
                }
            }

            if (broken >= ULT_MAX_BLOCKS_BROKEN) break;

            BlockPos base = BlockPos.ofFloored(p.x, p.y, p.z);
            BlockPos hitPos = null;
            BlockState hitState = null;

            for (int dy = 0; dy <= ULT_SURFACE_SEARCH; dy++) {
                BlockPos q = base.down(dy);
                BlockState s = w.getBlockState(q);

                if (s.isAir()) continue;
                if (w.getBlockEntity(q) != null) continue;
                if (s.getHardness(w, q) < 0.0f) continue;
                if (s.getHardness(w, q) >= 30.0f) continue;

                hitPos = q;
                hitState = s;
                break;
            }

            if (hitPos != null && hitState != null) {
                if (w.random.nextFloat() < ULT_TEAR_CHANCE) {
                    w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, hitState), hitPos.getX() + 0.5, hitPos.getY() + 0.75, hitPos.getZ() + 0.5, 12, 0.35, 0.25, 0.35, 0.10);
                    w.spawnParticles(ParticleTypes.CRIT, hitPos.getX() + 0.5, hitPos.getY() + 1.1, hitPos.getZ() + 0.5, 2, 0.10, 0.10, 0.10, 0.0);
                }

                boolean drop = w.random.nextFloat() < ULT_DROP_CHANCE;
                if (w.breakBlock(hitPos, drop)) {
                    broken++;
                }
            }
            p = p.add(step);
        }
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return "Sound"; }
    @Override public String getPassiveName()   { return "Resonance"; }
    @Override public String getPrimaryName()   { return "Doppler"; }
    @Override public String getSecondaryName() { return "Bass Drop"; }
    @Override public String getUltimateName()  { return "Sonic Shriek"; }

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 26_000; }
    @Override public long getUltimateCooldownMs()  { return 290_000; }

    @Override
    public String getOverviewDescription() {
        return "Sound is a primarily ranged power that also offers some (although probably not that great practically) utility " +
                "in the form of tracking and stunning. These abilities pierce through walls and work best at longer ranges.";
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

    /* ============================================================
       HELPERS
       ============================================================ */

    private static int breakBlastCapped(ServerWorld w, BlockPos center, int radius, int budgetLeft) {
        if (budgetLeft <= 0) return 0;
        int broken = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (broken >= budgetLeft) return broken;

                    int md = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (md > radius + 1) continue;

                    BlockPos p = center.add(dx, dy, dz);
                    BlockState s = w.getBlockState(p);

                    if (s.isAir()) continue;
                    if (s.getHardness(w, p) < 0.0f) continue;
                    if (w.getBlockEntity(p) != null) continue;
                    if (s.getHardness(w, p) >= 30.0f) continue;

                    if (w.random.nextFloat() < 0.35f) {
                        w.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.08);
                    }
                    boolean drop = w.random.nextFloat() < ULT_DROP_CHANCE;
                    if (w.breakBlock(p, drop)) {
                        broken++;
                    }
                }
            }
        }
        return broken;
    }

    private static boolean segmentIntersectsExpandedAabb(Vec3d a, Vec3d b, Box box) {
        Vec3d d = b.subtract(a);
        double len = d.length();
        if (len < 0.001) return box.contains(a);

        int steps = MathHelper.clamp((int)(len * 6.0), 12, 220);
        Vec3d step = d.multiply(1.0 / steps);

        Vec3d p = a;
        for (int i = 0; i <= steps; i++) {
            if (box.contains(p)) return true;
            p = p.add(step);
        }
        return false;
    }
}