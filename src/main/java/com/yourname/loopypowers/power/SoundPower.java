package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.SonicBoltEntity;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

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
        int immunityTicks = 0;
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
    private static final int    RES_IMMUNITY_TICKS = 140;
    private static final double RES_RADIUS         = 14.0;
    private static final int    RES_SCAN_INTERVAL  = 4;
    private static final int    RES_TRAIL_INTERVAL = 2;
    private static final double RES_INSTANT_RADIUS = 3.0;
    private static final int    RESONATED_TICKS    = 80;
    private static final int    RES_THRESHOLD      = 15;
    private static final int    RES_DECAY_PER_SCAN = 1;
    private static final int    MAX_TRAIL_TARGETS  = 14;
    private static final float  BURST_BONUS_DAMAGE  = 7.5f;

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
    private static final int    BD_PULSE_COUNT       = 7;
    private static final int    BD_FINAL_DELAY_TICKS = 6;
    private static final double BD_PULL_RADIUS       = 10.0;
    private static final double BD_FINAL_RADIUS      = 7.0;
    private static final float  BD_PULL_STRENGTH     = 0.21f;
    private static final float  BD_PULL_UP           = 0.02f;
    private static final float  BD_FINAL_KB          = 1.15f;
    private static final float  BD_FINAL_UP          = 0.30f;
    private static final float  BD_FINAL_DAMAGE      = 16.5f;
    private static final int    BD_FINAL_STUN_TICKS  = 40;
    private static final int    BD_REMOTE_STUN_TICKS = 30;

    private static final int PULL_VIZ_STEPS  = 8;
    private static final int BLAST_VIZ_STEPS = 8;

    // Ultimate
    private static final int    ULT_WINDUP_TICKS      = 28;
    private static final double ULT_RANGE             = 45.0;
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
                w.getEntitiesByClass(SonicBoltEntity.class, player.getBoundingBox().expand(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);

                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(150), LivingEntity::isAlive)) {
                    SoundVictimState state = VICTIM_STATES.get(e.getUuid());
                    if (state != null && state.resonatedTicks > 0) {
                        state.resonatedTicks = 0;
                        e.removeStatusEffect(StatusEffects.GLOWING);
                        e.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.STUN));
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
        if (!player.isAlive()) return;

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
            VICTIM_STATES.values().removeIf(v -> (now - v.lastSeenTick) > 100 && v.score <= 0 && v.resonatedTicks <= 0 && v.immunityTicks <= 0);
        }
    }

    public static void applyAbilityHit(ServerPlayerEntity caster, LivingEntity target, float baseDamage, boolean allowBurst) {
        target.damage(ModDamageTypes.sound(target.getWorld(), caster), baseDamage);

        if (!allowBurst) return;

        SoundVictimState vState = VICTIM_STATES.get(target.getUuid());
        if (vState == null || vState.resonatedTicks <= 0) return;

        vState.resonatedTicks = 0;
        target.removeStatusEffect(StatusEffects.GLOWING);

        target.damage(ModDamageTypes.sound(target.getWorld(), caster), BURST_BONUS_DAMAGE);

        target.setVelocity(0, Math.min(target.getVelocity().y, 0.0), 0);
        target.velocityModified = true;
        syncEntityVelocity(target);

        target.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.STUN), 35, 0, false, false, true));
        vState.immunityTicks = RES_IMMUNITY_TICKS;
        vState.score = 0;

        if (target instanceof ServerPlayerEntity targetPlayer) {
            CameraShake.shakeNearby(targetPlayer, 3, 15, 0.08f);
        }

        if (target.getWorld() instanceof ServerWorld sw) {
            SoundBurstHitPayload fx = new SoundBurstHitPayload(target.getId());
            PlayerLookup.tracking(sw, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
            sw.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, caster.getSoundCategory(), 0.9f, 1.3f);
        }
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

            if (vState.lastTickTime != nowTick) {
                int delta = (int) (nowTick - vState.lastTickTime);
                if (delta > 20) delta = 20;
                vState.lastTickTime = nowTick;

                boolean wasResonated = vState.resonatedTicks > 0;
                if (vState.resonatedTicks > 0) vState.resonatedTicks -= delta;

                if (wasResonated && vState.resonatedTicks <= 0) {
                    vState.immunityTicks = RES_IMMUNITY_TICKS;
                    vState.score = 0;
                }

                if (vState.immunityTicks > 0) vState.immunityTicks -= delta;
                if (vState.hbStep > 0) vState.hbStep -= delta;
            }

            if (vState.immunityTicks > 0) {
                vState.score = 0;
            } else {
                vState.score = Math.max(0, vState.score - RES_DECAY_PER_SCAN);
                vState.score += computeConspicuousPoints(e);

                if (player.squaredDistanceTo(e) <= (RES_INSTANT_RADIUS * RES_INSTANT_RADIUS)) {
                    vState.score = RES_THRESHOLD;
                }
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

                        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, vol, pitch);

                        if (e instanceof ServerPlayerEntity spTarget) {
                            w.playSound(null, spTarget.getX(), spTarget.getY(), spTarget.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, vol * 0.9f, pitch);
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
                    w.playSound(null, e.getX(), e.getY(), e.getZ(),
                            SoundEvents.BLOCK_SCULK_SENSOR_CLICKING,
                            player.getSoundCategory(),
                            0.7f, 1.1f);

                    SoundResonanceBurstPayload fx = new SoundResonanceBurstPayload(e.getId());
                    PlayerLookup.tracking(w, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));

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
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BOLT, player.getSoundCategory(), 0.4f, 1.2f);
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

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, player.getSoundCategory(), 0.7f, 1.4f);

        SoundBoomCastPayload fx = new SoundBoomCastPayload(player.getX(), player.getY() + 1.0, player.getZ());
        sendToViewers(w, player, fx);
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
                s.nextPulseIn = Math.max(2, s.nextPulse * 2);

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

        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.BASSDROP, caster.getSoundCategory(), 1.0f, 1.00f);

        SoundBassFinalPayload finalFx = new SoundBassFinalPayload(cPos.x, cPos.y, cPos.z);
        sendToViewers(w, caster, finalFx);

        if (state.bdState != null) state.bdState.blastVizStep = 0;

        for (LivingEntity t : nearby) {
            applyBassBurstHit(caster, t, BD_FINAL_DAMAGE, BD_FINAL_STUN_TICKS);
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
                SoundBassFinalRemotePayload remoteFx = new SoundBassFinalRemotePayload(t.getId());
                PlayerLookup.tracking(tw, t.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, remoteFx));
                tw.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, caster.getSoundCategory(), 0.7f, 1.3f);
            }
            applyBassBurstHit(caster, t, 0.0f, BD_REMOTE_STUN_TICKS);
        }
        CameraShake.shakeNearby(caster, 5, 15, 0.04f);
    }

    private static void doBassPullPulse(ServerWorld w, ServerPlayerEntity caster, BassDropState s) {
        Vec3d cPos = caster.getPos();

        Box box = new Box(cPos, cPos).expand(BD_PULL_RADIUS, 6.0, BD_PULL_RADIUS);
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != caster);

        float pitch = 1.0f + ((BD_PULSE_COUNT - s.nextPulse) * 0.15f);
        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.BASSSINGLE, caster.getSoundCategory(), 0.6f, pitch);

        sendToViewers(w, caster, new SoundBassPullPayload(cPos.x, cPos.y, cPos.z, w.random.nextLong()));

        s.pullVizStep = 0;

        for (LivingEntity t : targets) {
            Vec3d toCaster = cPos.subtract(t.getPos());
            Vec3d horiz = new Vec3d(toCaster.x, 0.0, toCaster.z);
            if (horiz.lengthSquared() < 1.0e-6) continue;

            Vec3d dir = horiz.normalize();
            t.addVelocity(dir.x * BD_PULL_STRENGTH, BD_PULL_UP, dir.z * BD_PULL_STRENGTH);
            t.velocityModified = true;
            syncEntityVelocity(t);

            s.scanned.add(t.getUuid());
        }
    }

    private static void applyBassBurstHit(ServerPlayerEntity caster, LivingEntity target, float damage, int stunTicks) {
        if (damage > 0.0f) {
            target.damage(ModDamageTypes.sound(target.getWorld(), caster), damage);
        }

        Vec3d dir = target.getPos().subtract(caster.getPos());
        dir = new Vec3d(dir.x, 0.0, dir.z);
        if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
        dir = dir.normalize();
        target.addVelocity(dir.x * BD_FINAL_KB, BD_FINAL_UP, dir.z * BD_FINAL_KB);
        target.velocityModified = true;
        syncEntityVelocity(target);

        SoundVictimState vState = VICTIM_STATES.get(target.getUuid());
        if (vState != null && vState.resonatedTicks > 0) {
            vState.resonatedTicks = 0;
            target.removeStatusEffect(StatusEffects.GLOWING);
            target.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.STUN), stunTicks, 0, false, false, true));

            vState.immunityTicks = RES_IMMUNITY_TICKS;
            vState.score = 0;

            if (target instanceof ServerPlayerEntity spTarget) {
                spTarget.getServerWorld().playSound(null, spTarget.getX(), spTarget.getY(), spTarget.getZ(), ModSounds.EARRING, net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f);
            }
        }

        if (target instanceof ServerPlayerEntity spTarget) {
            CameraShake.shakeNearby(spTarget, 14, 15, 1.4f);
        }
    }

    private static void animateBassSpheres(ServerWorld w, ServerPlayerEntity caster, BassDropState s) {
        if (s.pullVizStep >= 0 && s.pullVizStep < PULL_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);
            sendToViewers(w, caster, new SoundBassPullVizPayload(c.x, c.y, c.z, s.pullVizStep, w.random.nextLong()));
            s.pullVizStep++;
            if (s.pullVizStep >= PULL_VIZ_STEPS) s.pullVizStep = -1;
        }

        if (s.blastVizStep >= 0 && s.blastVizStep < BLAST_VIZ_STEPS) {
            Vec3d c = caster.getPos().add(0, 0.9, 0);
            sendToViewers(w, caster, new SoundBassBlastVizPayload(c.x, c.y, c.z, s.blastVizStep, w.random.nextLong()));
            s.blastVizStep++;
            if (s.blastVizStep >= BLAST_VIZ_STEPS) s.blastVizStep = -1;
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

        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, player.getSoundCategory(), 1.0f, 1.0f);

        SoundBoomCastPayload fx = new SoundBoomCastPayload(player.getX(), player.getY() + 1.0, player.getZ());
        sendToViewers(w, player, fx);

        CameraShake.shakeNearby(player, 10.0, 8, 0.9f);
    }

    private static void tickUltimate(ServerPlayerEntity player, SoundCasterState state) {
        if (state.ultWindup <= 0) return;
        state.ultWindup--;

        Vec3d v = player.getVelocity();
        player.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        player.velocityModified = true;
        syncPlayerVelocity(player);
        player.setSprinting(false);
        player.fallDistance = 0.0f;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 10, true, false));

        if (player.getWorld() instanceof ServerWorld w) {
            if (state.ultWindup % 4 == 0) {
                w.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
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

        w.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.RAILGUN, caster.getSoundCategory(), 1.2f, 0.9f);
        CameraShake.shakeNearby(caster, 20.0, 18, 1.6f);

        SoundUltBeamPayload beamFx = new SoundUltBeamPayload(start.x, start.y, start.z, end.x, end.y, end.z, w.random.nextLong());
        sendToViewers(w, caster, beamFx);

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
            syncEntityVelocity(target);
        }

        tearGroundAlongBeam(w, start, end);
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
                    w.playSound(null, beamPos.getX() + 0.5, beamPos.getY() + 0.5, beamPos.getZ() + 0.5, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.9f, 1.2f);
                    broken += breakBlastCapped(w, beamPos, (ULT_MAX_BLOCKS_BROKEN - broken));
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

            if (hitPos != null) {
                if (w.random.nextFloat() < ULT_TEAR_CHANCE) {
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, hitState), hitPos.getX() + 0.5, hitPos.getY() + 0.75, hitPos.getZ() + 0.5, 12, 0.35, 0.25, 0.35, 0.10);
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
       CLEANSES
       ============================================================ */

    public static boolean cleanseResonance(LivingEntity target) {
        SoundVictimState state = VICTIM_STATES.get(target.getUuid());
        if (state != null && (state.score > 0 || state.resonatedTicks > 0)) {
            state.score = 0;
            state.resonatedTicks = 0;
            state.immunityTicks = RES_IMMUNITY_TICKS;
            target.removeStatusEffect(StatusEffects.GLOWING);
            return true;
        }
        return false;
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return Text.translatable("power.loopypowers.sound.name").getString(); }
    @Override public String getPassiveName()   { return Text.translatable("power.loopypowers.sound.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.sound.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.sound.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.sound.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 7_000; }
    @Override public long getSecondaryCooldownMs() { return 26_000; }
    @Override public long getUltimateCooldownMs()  { return 290_000; }

    @Override public String getOverviewDescription() { return Text.translatable("power.loopypowers.sound.description.overview").getString(); }
    @Override public String getPassiveDescription()  { return Text.translatable("power.loopypowers.sound.description.passive").getString(); }
    @Override public String getPrimaryDescription()  { return Text.translatable("power.loopypowers.sound.description.primary").getString(); }
    @Override public String getSecondaryDescription(){ return Text.translatable("power.loopypowers.sound.description.secondary").getString(); }
    @Override public String getUltimateDescription() { return Text.translatable("power.loopypowers.sound.description.ultimate").getString(); }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static int breakBlastCapped(ServerWorld w, BlockPos center, int budgetLeft) {
        if (budgetLeft <= 0) return 0;
        int broken = 0;
        int radius = 2;

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
                        w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.08);
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

    private static void syncPlayerVelocity(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));
    }

    private static void syncEntityVelocity(LivingEntity e) {
        if (e instanceof ServerPlayerEntity sp) syncPlayerVelocity(sp);
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }
}
