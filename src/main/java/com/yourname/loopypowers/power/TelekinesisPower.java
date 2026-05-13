package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

public class TelekinesisPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, TKCasterState> ACTIVE_CASTERS = new HashMap<>();
    private static final Map<UUID, TKVictimState> ACTIVE_VICTIMS = new HashMap<>();

    private static class DebrisField {
        final Map<UUID, Double> orbitAngles = new HashMap<>();
        int ticksRemaining = 0;
    }

    private static class TKCasterState {
        int readyThrowTicks = 0;
        int throwCd = 0;
        int debrisThrowCd = 0;
        boolean prevSwing = false;
        long lastSwingTime = 0;

        DebrisField debrisField = null;
        final Map<UUID, Vec3d> thrownBlocks = new HashMap<>();
    }

    private static class TKVictimState {
        int suspendTicks = 0;
        int chokeTicks = 0;
        UUID suspendOwner = null;

        int airborneTicks = 0;
        UUID impactOwner = null;
        Vec3d prevVelocity = null;
    }

    private static TKCasterState getCasterState(Entity player) {
        return ACTIVE_CASTERS.computeIfAbsent(player.getUuid(), k -> new TKCasterState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // ── Passive — Forceful Strikes ──────────────────────────────
    private static final double PASSIVE_KB_MULT     = 1.8;
    private static final double PASSIVE_KB_VERTICAL = 0.2;

    // ── Primary — Yank ──────────────────────────────────────────
    private static final double YANK_RANGE          = 12.0;
    private static final double YANK_CONE_DOT       = 0.6;
    private static final double YANK_STRENGTH       = 0.9;
    private static final double YANK_VERTICAL       = 0.3;

    // ── Secondary Suspend / throw phase ─────────────────────────────
    private static final int    SUSPEND_TICKS         = 50;
    private static final double SUSPEND_FLOAT_VEL     = 0.05;
    private static final int    THROW_READY_TICKS     = 45;
    private static final double THROW_SCAN_RANGE      = 14.0;
    private static final double THROW_SCAN_WIDTH      = 2.5;
    private static final double THROW_SPEED_H         = 1.5;
    private static final double THROW_SPEED_V         = 0.6;
    private static final double THROW_RELEASE_RANGE   = 12.0;

    // ── Choke phase ──────────────────────
    private static final int    CHOKE_TICKS           = 60;   // duration of choke
    private static final int    CHOKE_DAMAGE_INTERVAL = 10;   // ticks between damage pulses
    private static final float  CHOKE_DAMAGE_PER_TICK = 2.0f; // damage per pulse
    private static final double CHOKE_SQUEEZE_VEL     = -0.01; // upward squeeze velocity
    private static final double CHOKE_ORBIT_RADIUS_START = 0.5; // particle orbit start radius
    private static final double CHOKE_ORBIT_RADIUS_END   = 0.2; // tightens to this by end

    // EGG
    private static final int    QUOTE_CHANCE           = 450;

    // ── Impact system ──
    private static final int    TK_AIRBORNE_TICKS   = 40;

    private static final double IMPACT_MIN_SPEED_H  = 0.6;
    private static final double IMPACT_MIN_SPEED_V  = 0.7;

    private static final float  IMPACT_WALL_DAMAGE  = 16.0f;
    private static final float  IMPACT_WALL_SCALE   = 2.5f;

    private static final float  IMPACT_FLOOR_DAMAGE = 9.5f;
    private static final float  IMPACT_FLOOR_SCALE  = 1.8f;

    // ── Ultimate ──────────────────────────────────
    private static final int    DEBRIS_MAX_BLOCKS            = 10;   // max orbiting blocks
    private static final double DEBRIS_HARVEST_RADIUS        = 10.0; // radius to harvest blocks from
    private static final int    DEBRIS_ORBIT_TICKS           = 260;  // how long ult lasts
    private static final double DEBRIS_ORBIT_RADIUS          = 4.5;  // orbit radius around player
    private static final double DEBRIS_ORBIT_RADIUS_INNER    = 2.5;  // inner ring radius (dual-ring)
    private static final double DEBRIS_ORBIT_SPEED           = 0.045;// outer ring radians per tick
    private static final double DEBRIS_ORBIT_SPEED_INNER     = 0.08; // inner ring — faster, opposite dir
    private static final double DEBRIS_PULL_RADIUS           = 12.0; // entity pull radius
    private static final double DEBRIS_PULL_STRENGTH         = 0.07;
    private static final float  DEBRIS_PULL_DAMAGE           = 0.1f;
    private static final int    DEBRIS_THROW_COOLDOWN        = 8;    // ticks between throws
    private static final int    DEBRIS_THROW_COUNT           = 5;    // blocks per throw
    private static final float  DEBRIS_THROW_DAMAGE          = 13.0f;
    private static final double DEBRIS_THROW_SPEED           = 2.4;
    private static final double DEBRIS_THROW_SPREAD          = 0.3; // shotgun spread per extra block
    private static final double DEBRIS_THROW_EXPLOSION_RADIUS = 4.0;

    // block regen
    private static final int    DEBRIS_REGEN_DELAY_TICKS = 15;  // no swing time before regen starts
    private static final int    DEBRIS_REGEN_INTERVAL    = 20;  // ticks per block regen
    private static final int    DEBRIS_REGEN_AMOUNT      = 5;   // blocks per regen tick

    // EGG
    private static final int    WOOLLIAM_CHANCE            = 70;   // 1 in whatever chance for woolliam to make a cameo

    /* ============================================================
       PARTICLE STUFF
       ============================================================ */

    private static final DustParticleEffect TK_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.2f, 0.7f), 1.2f);
    private static final DustParticleEffect TK_LIGHT_PINK =
            new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.85f), 0.9f);
    private static final DustParticleEffect TK_MAGENTA =
            new DustParticleEffect(new Vector3f(0.85f, 0.0f, 0.5f), 1.5f);
    private static final DustParticleEffect TK_PINK_LARGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.35f, 0.75f), 2.2f);
    private static final DustParticleEffect TK_DARK_PINK =
            new DustParticleEffect(new Vector3f(0.6f, 0.0f, 0.35f), 1.8f);

    private static void spawnImpactRing(ServerWorld world, Vec3d pos, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            world.spawnParticles(TK_MAGENTA,
                    pos.x, pos.y + 0.1, pos.z,
                    1, Math.cos(angle) * radius, 0.08, Math.sin(angle) * radius, 0.04);
        }
        world.spawnParticles(TK_PINK, pos.x, pos.y + 0.5, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
    }

    private static void spawnSuspendAura(ServerWorld world, LivingEntity entity, long time) {
        double radius = 0.7;
        for (int i = 0; i < 6; i++) {
            double angle = (time * 0.12) + (i * Math.PI * 2.0 / 6);
            world.spawnParticles(TK_PINK,
                    entity.getX() + Math.cos(angle) * radius,
                    entity.getBodyY(0.6),
                    entity.getZ() + Math.sin(angle) * radius,
                    1, 0, 0.02, 0, 0);
        }
        if (time % 3 == 0) {
            world.spawnParticles(TK_LIGHT_PINK,
                    entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                    2, 0.25, 0.1, 0.25, 0.01);
        }
    }

    private static void spawnChokeAura(ServerWorld world, LivingEntity entity, long time, float chokeProgress) {
        // Orbit tightens
        double radius = CHOKE_ORBIT_RADIUS_START
                + (CHOKE_ORBIT_RADIUS_END - CHOKE_ORBIT_RADIUS_START) * chokeProgress;

        // speed up
        double spinSpeed = 0.12 + chokeProgress * 0.30;
        int points = 8;

        for (int i = 0; i < points; i++) {
            double angle = (time * spinSpeed) + (i * Math.PI * 2.0 / points);
            double x = entity.getX() + Math.cos(angle) * radius;
            double z = entity.getZ() + Math.sin(angle) * radius;

            // Spiral upward
            double y = entity.getBodyY(0.3 + chokeProgress * 0.4);

            world.spawnParticles(TK_DARK_PINK, x, y, z, 1, 0, 0.01, 0, 0);
        }

        // final vortex
        if (chokeProgress > 0.5f) {
            double innerRadius = radius * 0.4;
            for (int i = 0; i < 4; i++) {
                double angle = -(time * spinSpeed * 1.5) + (i * Math.PI / 2.0);
                world.spawnParticles(TK_MAGENTA,
                        entity.getX() + Math.cos(angle) * innerRadius,
                        entity.getBodyY(0.5),
                        entity.getZ() + Math.sin(angle) * innerRadius,
                        1, 0, 0.02, 0, 0);
            }
        }

        if (time % 2 == 0) {
            world.spawnParticles(TK_DARK_PINK,
                    entity.getX(), entity.getBodyY(0.8), entity.getZ(),
                    2, 0.15, 0.08, 0.15, 0.03);
        }
    }

    private static void spawnBeam(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from);
        int steps = (int)(dir.length() / 0.35);
        Vec3d step = dir.normalize().multiply(0.35);
        Vec3d pos = from;
        for (int i = 0; i < steps; i++) {
            if (i % 2 == 0) world.spawnParticles(TK_PINK, pos.x, pos.y, pos.z, 1, 0.03, 0.03, 0.03, 0);
            if (world.random.nextFloat() < 0.2f) world.spawnParticles(TK_LIGHT_PINK, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0.01);
            pos = pos.add(step);
        }
    }

    private static void spawnSlamImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 24, 0.5);
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2.0 * i / 20;
            world.spawnParticles(TK_PINK_LARGE,
                    pos.x + Math.cos(angle) * 1.5, pos.y + 0.1, pos.z + Math.sin(angle) * 1.5,
                    1, Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15, 0.02);
        }
        world.spawnParticles(TK_MAGENTA, pos.x, pos.y + 0.3, pos.z, 12, 0.5, 0.4, 0.5, 0.08);
        world.spawnParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.5, pos.z, 8, 0.4, 0.3, 0.4, 0.06);
    }

    private static void spawnWallImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 18, 0.4);
        world.spawnParticles(TK_MAGENTA, pos.x, pos.y + 0.5, pos.z, 10, 0.4, 0.4, 0.4, 0.07);
        world.spawnParticles(TK_PINK_LARGE, pos.x, pos.y + 0.3, pos.z, 5, 0.3, 0.3, 0.3, 0.04);

        for (int i = 0; i < 12; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.1 + world.random.nextDouble() * 0.2;
            world.spawnParticles(TK_DARK_PINK,
                    pos.x, pos.y + 0.5, pos.z,
                    1, Math.cos(angle) * speed, (world.random.nextDouble() - 0.3) * speed, Math.sin(angle) * speed, 0.01);
        }
    }

    private static void spawnFloorImpact(ServerWorld world, Vec3d pos) {
        spawnImpactRing(world, pos, 12, 0.35);
        world.spawnParticles(TK_PINK, pos.x, pos.y + 0.2, pos.z, 5, 0.3, 0.1, 0.3, 0.04);
    }

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tk_"));
        ACTIVE_CASTERS.put(player.getUuid(), new TKCasterState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tk_"));

        UUID pid = player.getUuid();
        TKCasterState caster = ACTIVE_CASTERS.remove(pid);

        if (caster == null) return;

        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                // Clear debris field instantly
                if (caster.debrisField != null) {
                    for (UUID uuid : caster.debrisField.orbitAngles.keySet()) {
                        Entity e = w.getEntity(uuid);
                        if (e != null) e.discard();
                    }
                    caster.debrisField = null;
                }

                // Clear pending thrown blocks
                for (UUID uuid : caster.thrownBlocks.keySet()) {
                    Entity e = w.getEntity(uuid);
                    if (e != null) e.discard();
                }
                caster.thrownBlocks.clear();
            }
        }

        // Clear targets suspended, choked, or knocked airborne by this player globally
        Iterator<Map.Entry<UUID, TKVictimState>> it = ACTIVE_VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            TKVictimState vState = it.next().getValue();
            if (pid.equals(vState.suspendOwner)) {
                vState.suspendOwner = null;
                vState.suspendTicks = 0;
                vState.chokeTicks = 0;
            }
            if (pid.equals(vState.impactOwner)) {
                vState.impactOwner = null;
                vState.airborneTicks = 0;
                vState.prevVelocity = null;
            }
            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0) {
                it.remove();
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

        ServerWorld world = player.getServerWorld();
        TKCasterState caster = getCasterState(player);

        boolean isSwinging = player.handSwinging;
        boolean wasSwinging = caster.prevSwing;

        if (isSwinging && !wasSwinging) {
            if (caster.throwCd <= 0) {
                performThrow(player, caster);
                caster.throwCd = 8;
            }
            throwDebrisProjectile(player, caster);
        }

        caster.prevSwing = isSwinging;
        if (caster.throwCd > 0) caster.throwCd--;
        if (caster.readyThrowTicks > 0) caster.readyThrowTicks--;
        if (caster.debrisThrowCd > 0) caster.debrisThrowCd--;

        Iterator<Map.Entry<UUID, TKVictimState>> it = ACTIVE_VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TKVictimState> entry = it.next();
            TKVictimState vState = entry.getValue();

            boolean ownsSuspend = player.getUuid().equals(vState.suspendOwner);
            boolean ownsImpact = player.getUuid().equals(vState.impactOwner);

            if (!ownsSuspend && !ownsImpact) continue;

            Entity ent = world.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                if (ownsSuspend) { vState.suspendTicks = 0; vState.chokeTicks = 0; vState.suspendOwner = null; }
                if (ownsImpact) { vState.airborneTicks = 0; vState.impactOwner = null; vState.prevVelocity = null; }
            } else {
                if (ownsSuspend) handleSuspendAndChoke(le, world, vState);
                if (ownsImpact) handleImpactDamage(le, world, vState);
            }

            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0) {
                it.remove();
            }
        }

        handleDebrisField(player, caster);
        tickThrownBlocks(player, caster);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        TKCasterState caster = getCasterState(attacker);
        caster.lastSwingTime = attacker.getServerWorld().getTime();

        Vec3d look = attacker.getRotationVec(1.0f);
        target.addVelocity(look.x * PASSIVE_KB_MULT, PASSIVE_KB_VERTICAL, look.z * PASSIVE_KB_MULT);
        target.velocityModified = true;

        markForImpactTracking(target, target.getVelocity(), attacker.getUuid());

        ServerWorld world = attacker.getServerWorld();
        spawnImpactRing(world, target.getPos(), 10, 0.3);
        world.spawnParticles(TK_LIGHT_PINK, target.getX(), target.getBodyY(0.7), target.getZ(), 4, 0.2, 0.3, 0.2, 0.03);
    }

    // IMPACT DAMAGE

    // attackerUuid is who caused the entity to become airborne, used for death messages
    private void markForImpactTracking(LivingEntity entity, Vec3d launchVelocity, UUID attackerUuid) {
        TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(entity.getUuid(), k -> new TKVictimState());
        vState.airborneTicks = TK_AIRBORNE_TICKS;
        vState.impactOwner = attackerUuid;
        vState.prevVelocity = launchVelocity;
    }

    private void handleImpactDamage(LivingEntity entity, ServerWorld world, TKVictimState vState) {
        if (vState.airborneTicks <= 0) return;

        vState.airborneTicks--;

        Vec3d prev = vState.prevVelocity;
        Vec3d curr = entity.getVelocity();

        if (prev != null) {
            double prevH = Math.sqrt(prev.x * prev.x + prev.z * prev.z);
            double currH = Math.sqrt(curr.x * curr.x + curr.z * curr.z);

            boolean wallStopped = prevH > IMPACT_MIN_SPEED_H && currH < prevH * 0.35 && entity.horizontalCollision;
            boolean floorHit = prev.y < -IMPACT_MIN_SPEED_V && entity.isOnGround();

            Entity attacker = resolveOwner(vState.impactOwner, world);

            if (wallStopped) {
                float damage = IMPACT_WALL_DAMAGE + (float)(prevH - IMPACT_MIN_SPEED_H) * IMPACT_WALL_SCALE;
                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);
                spawnWallImpact(world, entity.getPos().add(0, 0.8, 0));
                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_STONE_HIT, entity.getSoundCategory(), 0.9f, 0.7f);

                vState.airborneTicks = 0;
                vState.prevVelocity = null;
                vState.impactOwner = null;
                return;
            }

            if (floorHit) {
                float damage = IMPACT_FLOOR_DAMAGE + (float)(-prev.y - IMPACT_MIN_SPEED_V) * IMPACT_FLOOR_SCALE;
                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);
                spawnFloorImpact(world, entity.getPos());
                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_STONE_FALL, entity.getSoundCategory(), 0.8f, 0.9f);

                vState.airborneTicks = 0;
                vState.prevVelocity = null;
                vState.impactOwner = null;
                return;
            }
        }

        vState.prevVelocity = curr;

        if (vState.airborneTicks <= 0) {
            vState.impactOwner = null;
            vState.prevVelocity = null;
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);

        spawnBeam(world, origin, origin.add(look.multiply(YANK_RANGE)));

        boolean hitAnything = false;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(YANK_RANGE),
                en -> en.isAlive() && en != player)) {

            Vec3d toTarget = e.getPos().subtract(origin).normalize();
            if (look.dotProduct(toTarget) < YANK_CONE_DOT) continue;

            Vec3d pull = origin.subtract(e.getPos()).normalize().multiply(YANK_STRENGTH);
            Vec3d launchVel = e.getVelocity().add(pull.x, pull.y + YANK_VERTICAL, pull.z);
            e.setVelocity(launchVel);
            e.velocityModified = true;

            markForImpactTracking(e, launchVel, player.getUuid());
            spawnImpactRing(world, e.getPos(), 8, 0.25);
            hitAnything = true;
        }

        world.playSound(null, player.getBlockPos(),
                ModSounds.YANK, player.getSoundCategory(), 0.6f, 1.2f);
        if (hitAnything) world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, player.getSoundCategory(), 0.4f, 1.5f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(THROW_SCAN_RANGE));

        spawnBeam(world, origin, end);

        int grabbed = 0;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new Box(origin, end).expand(THROW_SCAN_WIDTH),
                en -> en.isAlive() && en != player)) {

            TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(e.getUuid(), k -> new TKVictimState());
            vState.suspendTicks = SUSPEND_TICKS;
            vState.chokeTicks = 0;
            vState.suspendOwner = player.getUuid();

            spawnImpactRing(world, e.getPos(), 10, 0.3);
            grabbed++;
        }

        if (grabbed > 0) {
            TKCasterState caster = getCasterState(player);
            caster.readyThrowTicks = THROW_READY_TICKS;
        }

        world.playSound(null, player.getBlockPos(),
                ModSounds.SUSPEND, player.getSoundCategory(), 0.7f, 0.9f);
    }

    private void handleSuspendAndChoke(LivingEntity e, ServerWorld world, TKVictimState vState) {
        if (vState.suspendTicks > 0) {
            vState.suspendTicks--;
            e.setVelocity(e.getVelocity().x * 0.3, SUSPEND_FLOAT_VEL, e.getVelocity().z * 0.3);
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, 5, 4, true, false, false));
            spawnSuspendAura(world, e, world.getTime());

            if (vState.suspendTicks <= 0) {
                vState.chokeTicks = CHOKE_TICKS;

                // --- EASTER EGG ---
                if (e instanceof ServerPlayerEntity && world.random.nextInt(QUOTE_CHANCE) == 0) {
                    Entity owner = resolveOwner(vState.suspendOwner, world);
                    if (owner instanceof ServerPlayerEntity attacker) {
                        net.minecraft.text.Text message = net.minecraft.text.Text.literal(
                                "<" + attacker.getName().getString() + "> I find your lack of faith... disturbing..."
                        );
                        world.getServer().getPlayerManager().broadcast(message, false);
                    }
                }
            }
            return;
        }

        if (vState.chokeTicks > 0) {
            vState.chokeTicks--;
            float chokeProgress = 1.0f - ((float) vState.chokeTicks / CHOKE_TICKS);
            e.setVelocity(e.getVelocity().x * 0.2, CHOKE_SQUEEZE_VEL + chokeProgress * 0.10, e.getVelocity().z * 0.2);
            e.velocityModified = true;

            if (e.age % CHOKE_DAMAGE_INTERVAL == 0) {
                Entity attacker = resolveOwner(vState.suspendOwner, world);
                e.damage(ModDamageTypes.strangle(world, attacker), CHOKE_DAMAGE_PER_TICK * (0.5f + chokeProgress));
                world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, e.getSoundCategory(), 0.4f + chokeProgress * 0.3f, 0.9f);
            }
            spawnChokeAura(world, e, world.getTime(), chokeProgress);

            if (vState.chokeTicks <= 0) {
                vState.suspendOwner = null;
            }
        }
    }

    private void performThrow(ServerPlayerEntity player, TKCasterState caster) {
        if (caster.readyThrowTicks <= 0) return;
        caster.readyThrowTicks = 0;

        Vec3d look = player.getRotationVec(1.0f);
        ServerWorld world = player.getServerWorld();

        Vec3d throwVel = new Vec3d(
                look.x * THROW_SPEED_H,
                THROW_SPEED_V,
                look.z * THROW_SPEED_H
        );

        boolean thrown = false;

        for (Map.Entry<UUID, TKVictimState> entry : ACTIVE_VICTIMS.entrySet()) {
            TKVictimState vState = entry.getValue();
            if ((vState.suspendTicks > 0 || vState.chokeTicks > 0) && player.getUuid().equals(vState.suspendOwner)) {
                Entity e = world.getEntity(entry.getKey());
                if (e instanceof LivingEntity le && le.isAlive() && le.squaredDistanceTo(player) <= (THROW_RELEASE_RANGE * THROW_RELEASE_RANGE)) {

                    vState.suspendTicks = 0;
                    vState.chokeTicks = 0;
                    vState.suspendOwner = null;

                    le.setVelocity(throwVel);
                    le.velocityModified = true;

                    markForImpactTracking(le, throwVel, player.getUuid());

                    spawnImpactRing(world, le.getPos(), 14, 0.4);
                    world.spawnParticles(TK_MAGENTA,
                            le.getX(), le.getBodyY(0.5), le.getZ(),
                            8, 0.3, 0.3, 0.3, 0.06);
                    thrown = true;
                }
            }
        }

        if (thrown) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_ENDER_DRAGON_FLAP,
                    player.getSoundCategory(), 0.6f, 1.4f);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        TKCasterState caster = getCasterState(player);

        if (caster.debrisField != null) {
            for (UUID uuid : caster.debrisField.orbitAngles.keySet()) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
        }

        DebrisField field = new DebrisField();
        field.ticksRemaining = DEBRIS_ORBIT_TICKS;
        caster.debrisField = field;

        int harvested = tryHarvestBlocks(player, world, DEBRIS_MAX_BLOCKS, field);

        // Visual rings always spawn to show the energy field is active
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = 1.5 + ring * 2.5;
            int points = 12 + ring * 6;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points;
                world.spawnParticles(ring % 2 == 0 ? TK_PINK_LARGE : TK_MAGENTA,
                        player.getX() + Math.cos(angle) * ringRadius,
                        player.getBodyY(0.5),
                        player.getZ() + Math.sin(angle) * ringRadius,
                        1, 0, 0.12, 0, 0.03);
            }
        }

        // Field activation FX
        for (int i = 0; i < 20; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.spawnParticles(TK_DARK_PINK,
                    player.getX() + Math.cos(a) * r,
                    player.getBodyY(0.3) + world.random.nextDouble() * 2.0,
                    player.getZ() + Math.sin(a) * r,
                    1, 0, 0.15, 0, 0.04);
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 1.2f, 0.4f);

        // Only play the "crunchy" stone break sound if we actually grabbed blocks
        if (harvested > 0) {
            world.playSound(null, player.getBlockPos(),
                    SoundEvents.BLOCK_STONE_BREAK, player.getSoundCategory(), 1.5f, 0.6f);
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 0.6f, 0.5f);
    }

    private int tryHarvestBlocks(ServerPlayerEntity player, ServerWorld world, int maxCount, DebrisField field) {
        Vec3d center = player.getPos();
        int radius = (int) Math.ceil(DEBRIS_HARVEST_RADIUS);
        List<BlockPos> candidates = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = player.getBlockPos().add(dx, dy, dz);
                    if (pos.isWithinDistance(center, DEBRIS_HARVEST_RADIUS) && isHarvestable(world, pos)) {
                        candidates.add(pos);
                    }
                }
            }
        }

        java.util.Collections.shuffle(candidates, new java.util.Random());

        int harvested = 0;
        boolean wooliamSpawned = false;

        for (BlockPos pos : candidates) {
            if (harvested >= maxCount) break;

            BlockState state = world.getBlockState(pos);
            world.removeBlock(pos, false);

            Entity orbitEntity;

            // --- EASTER EGG ---
            if (!wooliamSpawned && world.random.nextInt(WOOLLIAM_CHANCE) == 0) {
                net.minecraft.entity.passive.SheepEntity sheep = net.minecraft.entity.EntityType.SHEEP.create(world);
                if (sheep != null) {
                    sheep.setCustomName(net.minecraft.text.Text.literal("Woolliam"));
                    sheep.setNoGravity(true);
                    sheep.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                    world.spawnEntity(sheep);
                    orbitEntity = sheep;
                    wooliamSpawned = true;
                } else {
                    orbitEntity = FallingBlockEntity.spawnFromBlock(world, pos, state);
                }
            } else {
                orbitEntity = FallingBlockEntity.spawnFromBlock(world, pos, state);
            }

            // orbital entity setup
            if (orbitEntity instanceof FallingBlockEntity falling) {
                falling.setNoGravity(true);
                falling.dropItem = false;
                falling.timeFalling = -32768;
            }

            field.orbitAngles.put(orbitEntity.getUuid(), (Math.PI * 2.0 * harvested) / maxCount);
            harvested++;
        }
        return harvested;
    }

    private boolean isHarvestable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || world.getBlockEntity(pos) != null) return false;
        if (!state.isOpaque()) return false;

        return state.isIn(net.minecraft.registry.tag.BlockTags.LOGS)
                || state.isIn(net.minecraft.registry.tag.BlockTags.DIRT)
                || state.isIn(net.minecraft.registry.tag.BlockTags.SAND)
                || state.isIn(net.minecraft.registry.tag.BlockTags.SNOW)
                || state.isIn(net.minecraft.registry.tag.BlockTags.PICKAXE_MINEABLE);
    }

    private void handleDebrisField(ServerPlayerEntity player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null || field.ticksRemaining <= 0) return;

        handleDebrisRegen(player, caster, field);
        ServerWorld world = player.getServerWorld();
        field.ticksRemaining--;

        if (field.ticksRemaining <= 0) {
            for (UUID uuid : field.orbitAngles.keySet()) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
            }
            caster.debrisField = null;
            return;
        }

        Vec3d playerPos = player.getPos().add(0, 1.0, 0);
        Set<UUID> toRemove = new HashSet<>();
        int total = field.orbitAngles.size();

        int index = 0;
        for (Map.Entry<UUID, Double> entry : field.orbitAngles.entrySet()) {
            UUID uuid = entry.getKey();
            Entity ent = world.getEntity(uuid);

            // Allow any orbiting entity (like Wooliam)
            if (ent == null || ent.isRemoved()) {
                toRemove.add(uuid);
                index++;
                continue;
            }

            boolean isInner = (index % 2 == 1);
            double orbitRadius = isInner ? DEBRIS_ORBIT_RADIUS_INNER : DEBRIS_ORBIT_RADIUS;
            double orbitSpeed  = isInner ? -DEBRIS_ORBIT_SPEED_INNER : DEBRIS_ORBIT_SPEED;

            double angle = (Math.PI * 2.0 * (index / 2)) / Math.max(1, total / 2) + (world.getTime() * orbitSpeed);
            entry.setValue(angle);

            double x = playerPos.x + Math.cos(angle) * orbitRadius;
            double z = playerPos.z + Math.sin(angle) * orbitRadius;
            double y = playerPos.y + Math.sin(angle * 1.5 + world.getTime() * 0.08) * 0.5 + (isInner ? 0.3 : 0.0);

            Vec3d target = new Vec3d(x, y, z);
            ent.setVelocity(target.subtract(ent.getPos()).multiply(0.35));
            ent.velocityModified = true;

            if (target.distanceTo(ent.getPos()) > 5.0) ent.setPos(target.x, target.y, target.z);

            ent.setNoGravity(true);
            if (ent instanceof FallingBlockEntity fb) fb.timeFalling = -32768;

            world.spawnParticles(isInner ? TK_MAGENTA : TK_DARK_PINK, target.x, target.y, target.z, 1, 0.04, 0.04, 0.04, 0.01);
            index++;
        }
        toRemove.forEach(field.orbitAngles::remove);

        // pull and damage
        double contactRadius = DEBRIS_ORBIT_RADIUS_INNER * 1.2;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(DEBRIS_PULL_RADIUS),
                en -> en.isAlive() && en != player)) {

            Vec3d toPlayer = playerPos.subtract(e.getPos());
            double dist = toPlayer.length();
            if (dist < 0.5 || dist > DEBRIS_PULL_RADIUS) continue;

            // slight pull
            double strength = DEBRIS_PULL_STRENGTH * (1.0 - dist / DEBRIS_PULL_RADIUS);
            Vec3d pull = toPlayer.normalize().multiply(strength);

            e.addVelocity(pull.x, pull.y * 0.3, pull.z);
            e.velocityModified = true;

            // damage if close — attributed to the player running the ult
            if (dist <= contactRadius && world.getTime() % 10 == 0) {
                e.damage(ModDamageTypes.debrisOrbit(world, player), DEBRIS_PULL_DAMAGE);
            }

            if (world.getTime() % 4 == 0) {
                world.spawnParticles(TK_LIGHT_PINK,
                        e.getX(), e.getBodyY(0.5), e.getZ(),
                        1, pull.x * 2, pull.y * 2, pull.z * 2, 0.01);
            }
        }

        // player particles
        long time = world.getTime();
        for (int ring = 0; ring < 3; ring++) {
            double ringR = DEBRIS_ORBIT_RADIUS_INNER + ring * 0.8;
            double ringSpeed = 0.06 + ring * 0.02;
            double ringDir = (ring % 2 == 0) ? 1 : -1;
            int auraPoints = 4 + ring * 2;

            for (int i = 0; i < auraPoints; i++) {
                double a = (time * ringSpeed * ringDir) + (i * Math.PI * 2.0 / auraPoints);
                world.spawnParticles(ring == 0 ? TK_MAGENTA : ring == 1 ? TK_PINK : TK_LIGHT_PINK,
                        playerPos.x + Math.cos(a) * ringR,
                        playerPos.y + Math.sin(a * 0.5) * 0.3,
                        playerPos.z + Math.sin(a) * ringR,
                        1, 0, 0.015, 0, 0);
            }
        }
    }

    private void throwDebrisProjectile(ServerPlayerEntity player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null) return;
        if (caster.debrisThrowCd > 0) return;

        ServerWorld world = player.getServerWorld();
        if (field.orbitAngles.size() < DEBRIS_THROW_COUNT) {
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_STONE_HIT, player.getSoundCategory(), 0.6f, 0.5f);
            return;
        }

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d right = new Vec3d(-look.z, 0, look.x).normalize();
        Vec3d up = look.crossProduct(right).normalize();

        int thrown = 0;
        Iterator<Map.Entry<UUID, Double>> it = field.orbitAngles.entrySet().iterator();

        while (it.hasNext() && thrown < DEBRIS_THROW_COUNT) {
            Map.Entry<UUID, Double> entry = it.next();
            UUID uuid = entry.getKey();

            Entity orbitEntity = world.getEntity(uuid);
            it.remove();

            if (orbitEntity != null && !orbitEntity.isRemoved()) {
                orbitEntity.setNoGravity(false); // Re-enable gravity for the throw

                if (orbitEntity instanceof FallingBlockEntity fb) {
                    fb.timeFalling = 0;
                    fb.dropItem = false;
                }

                double spreadH = (thrown == 0) ? 0 : (thrown % 2 == 0 ? 1 : -1) * DEBRIS_THROW_SPREAD * ((thrown + 1) / 2);
                double spreadV = (world.random.nextDouble() - 0.5) * DEBRIS_THROW_SPREAD * 0.5;

                Vec3d vel = look.multiply(DEBRIS_THROW_SPEED).add(right.multiply(spreadH)).add(up.multiply(spreadV));
                orbitEntity.setVelocity(vel);
                orbitEntity.velocityModified = true;

                caster.thrownBlocks.put(uuid, orbitEntity.getPos());

                spawnImpactRing(world, orbitEntity.getPos(), 8, 0.25);
                world.spawnParticles(TK_MAGENTA, orbitEntity.getX(), orbitEntity.getY(), orbitEntity.getZ(), 4, 0.2, 0.2, 0.2, 0.06);
            }
            thrown++;
        }

        if (thrown > 0) {
            caster.debrisThrowCd = DEBRIS_THROW_COOLDOWN;
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 0.8f, 1.1f);
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, player.getSoundCategory(), 1.2f, 1.2f);
        }
    }

    private void tickThrownBlocks(ServerPlayerEntity player, TKCasterState caster) {
        if (caster.thrownBlocks.isEmpty()) return;

        ServerWorld world = player.getServerWorld();
        Set<UUID> toExplode = new HashSet<>();

        for (Map.Entry<UUID, Vec3d> entry : caster.thrownBlocks.entrySet()) {
            UUID entityUuid = entry.getKey();
            Entity ent = world.getEntity(entityUuid);

            if (ent == null || ent.isRemoved()) {
                triggerDebrisExplosion(player, world, entry.getValue());
                toExplode.add(entityUuid);
                continue;
            }

            entry.setValue(ent.getPos());

            boolean hitSomething = ent.horizontalCollision || ent.isOnGround();

            if (hitSomething) {
                triggerDebrisExplosion(player, world, ent.getPos());
                ent.discard();
                toExplode.add(entityUuid);
            }
        }

        toExplode.forEach(caster.thrownBlocks::remove);
    }

    private void triggerDebrisExplosion(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(pos, pos).expand(DEBRIS_THROW_EXPLOSION_RADIUS),
                en -> en.isAlive() && en != player)) {

            double dist = e.getPos().distanceTo(pos);
            if (dist > DEBRIS_THROW_EXPLOSION_RADIUS) continue;

            float damage = DEBRIS_THROW_DAMAGE * (float)(1.0 - dist / DEBRIS_THROW_EXPLOSION_RADIUS);
            e.damage(ModDamageTypes.blockThrow(world, player), damage);

            Vec3d knockback = e.getPos().subtract(pos).normalize().multiply(0.8);
            e.addVelocity(knockback.x, 0.4, knockback.z);
            e.velocityModified = true;

            markForImpactTracking(e, e.getVelocity(), player.getUuid());
        }

        spawnSlamImpact(world, pos);
        world.spawnParticles(ParticleTypes.EXPLOSION,
                pos.x, pos.y + 0.5, pos.z, 3, 0.5, 0.3, 0.5, 0.1);
        for (int i = 0; i < 15; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 0.8;
            world.spawnParticles(TK_DARK_PINK,
                    pos.x + Math.cos(a) * r, pos.y + 0.3, pos.z + Math.sin(a) * r,
                    1, Math.cos(a) * 0.3, 0.2, Math.sin(a) * 0.3, 0.05);
        }

        world.playSound(null,
                new BlockPos((int)pos.x, (int)pos.y, (int)pos.z),
                SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS,
                1.0f, 0.8f);
    }

    private void handleDebrisRegen(ServerPlayerEntity player, TKCasterState caster, DebrisField field) {
        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        long idleTime = time - caster.lastSwingTime;

        if (idleTime < DEBRIS_REGEN_DELAY_TICKS) return;
        if (time % DEBRIS_REGEN_INTERVAL != 0) return;
        if (field.orbitAngles.size() >= DEBRIS_MAX_BLOCKS) return;

        int toRegen = Math.min(DEBRIS_REGEN_AMOUNT, DEBRIS_MAX_BLOCKS - field.orbitAngles.size());

        for (int i = 0; i < toRegen; i++) {
            spawnOrbitBlock(player, world, field);
        }
    }

    private void spawnOrbitBlock(ServerPlayerEntity player, ServerWorld world, DebrisField field) {
        BlockPos pos = player.getBlockPos().down();
        BlockState state = Blocks.STONE.getDefaultState();

        FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, pos, state);
        block.setNoGravity(true);
        block.dropItem = false;
        block.timeFalling = -32768;

        field.orbitAngles.put(block.getUuid(), world.random.nextDouble() * Math.PI * 2);

        world.spawnParticles(TK_MAGENTA,
                block.getX(), block.getY(), block.getZ(),
                6, 0.2, 0.2, 0.2, 0.05);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_STONE_PLACE,
                player.getSoundCategory(), 0.4f, 1.2f);
    }

    // Resolve the player entity from a stored UUID
    private static Entity resolveOwner(UUID ownerUuid, ServerWorld world) {
        if (ownerUuid == null) return null;
        return world.getServer().getPlayerManager().getPlayer(ownerUuid);
    }

    /* ============================================================
       YAP
       ============================================================ */

    @Override public String getName()          { return "Telekinesis"; }
    @Override public String getPassiveName()   { return "Kinetic Thrust"; }
    @Override public String getPrimaryName()   { return "Yank"; }
    @Override public String getSecondaryName() { return "Stasis"; }
    @Override public String getUltimateName()  { return "Debris Vortex"; }

    @Override public long getPrimaryCooldownMs()   { return 11_000; }
    @Override public long getSecondaryCooldownMs() { return 24_000; }
    @Override public long getUltimateCooldownMs()  { return 310_000; }

    @Override
    public String getOverviewDescription() {
        return "Telekinesis is a control power focused on moving entities around and keeping them away from you." +
                " Where most abilities can vary on power dependent on the current environment, meaning this power may be weaker in some areas, such as wide open spaces, but stronger" +
                " in enclosed spaces, or cliff terrain as your knockback can prevent melee attacks or deal high fall/wall collision damage.";
    }

    @Override
    public String getPassiveDescription() {
        return "Melee hits have increases knockback, this stacks with knockback enchantments.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Pull all entities in front of you towards you, this pull will also bring entities upwards.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Suspend a group of entities in front of you, disabling their movement and floating them into the air. You then have two choices for actions to take:" +
                " \nSwing your hand in any direction and launch the suspended entities in the direction you were looking, entities will take increased fall damage or bonus damage when colliding" +
                " with a wall." +
                " \nDo not swing your hand while they are suspended and will begin to choke them, losing the ability to throw them and dealing damage over time.";
    }

    @Override
    public String getUltimateDescription() {
        return "Surround yourself in telekinetic energy, pulling in nearby entities and damaging them if they are very close." +
                "\nNearby natural blocks are pulled from the world and orbit you, swinging will launch some of these blocks in the direction you are looking, dealing AOE damage and" +
                " knockback." +
                "\nYou must have a certain number of blocks to launch them and you replenish blocks slowly after not swinging for a few seconds." +
                "\nThere are no changes to your movement during this, but your vision may be obscured. ";
    }
}