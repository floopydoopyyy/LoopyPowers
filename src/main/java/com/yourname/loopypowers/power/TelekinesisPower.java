package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

public class TelekinesisPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, TKCasterState> ACTIVE_CASTERS = new HashMap<>();
    private static final Map<UUID, TKVictimState> ACTIVE_VICTIMS = new HashMap<>();

    private static class DebrisField {
        final Map<UUID, Double>  orbitAngles  = new HashMap<>();
        final Queue<BlockPos>    harvestQueue = new ArrayDeque<>();
        int     ticksRemaining = 0;
        boolean wooliamSpawned = false;
    }

    private static class TKCasterState {
        int  readyThrowTicks = 0;
        int  throwCd         = 0;
        int  debrisThrowCd   = 0;
        boolean prevSwing    = false;
        long lastSwingTime   = 0;

        DebrisField      debrisField  = null;
        final Map<UUID, Vec3d> thrownBlocks = new HashMap<>();
    }

    private static class TKVictimState {
        int  suspendTicks = 0;
        int  chokeTicks   = 0;
        UUID suspendOwner = null;

        int    airborneTicks = 0;
        UUID   impactOwner   = null;
        Vec3d  prevVelocity  = null;

        int  yankTicks = 0;
        UUID yankOwner = null;
    }

    private static TKCasterState getCasterState(Entity player) {
        return ACTIVE_CASTERS.computeIfAbsent(player.getUuid(), k -> new TKCasterState());
    }

    // Guard for re-entrant yank fall damage application
    private static boolean applyingYankDamage = false;

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // Passive
    private static final double PASSIVE_KB_MULT     = 1.8;
    private static final double PASSIVE_KB_VERTICAL = 0.2;

    // Primary — Yank
    private static final double YANK_RANGE             = 12.0;
    private static final double YANK_CONE_DOT          = 0.6;
    private static final double YANK_STRENGTH          = 0.9;
    private static final double YANK_VERTICAL          = 0.3;
    private static final float  YANK_EXTRA_FALL_DAMAGE = 6.0f;
    private static final int    YANK_FALL_WINDOW_TICKS = 60;

    // Secondary — Suspend/Throw
    private static final int    SUSPEND_TICKS       = 50;
    private static final double SUSPEND_FLOAT_VEL   = 0.05;
    private static final int    THROW_READY_TICKS   = 45;
    private static final double THROW_SCAN_RANGE    = 14.0;
    private static final double THROW_SCAN_WIDTH    = 2.5;
    private static final double THROW_SPEED_H       = 1.5;
    private static final double THROW_SPEED_V       = 0.6;
    private static final double THROW_RELEASE_RANGE = 12.0;

    // Choke
    private static final int    CHOKE_TICKS           = 60;
    private static final int    CHOKE_DAMAGE_INTERVAL = 10;
    private static final float  CHOKE_DAMAGE_PER_TICK = 2.0f;
    private static final double CHOKE_ENTRY_LIFT      = 0.28;
    private static final double CHOKE_HOVER_VEL       = 0.06;

    // Easter egg
    private static final int QUOTE_CHANCE = 450;

    // Impact
    private static final int    TK_AIRBORNE_TICKS  = 40;
    private static final double IMPACT_MIN_SPEED_H = 0.6;
    private static final double IMPACT_MIN_SPEED_V = 0.7;
    private static final float  IMPACT_WALL_DAMAGE  = 16.0f;
    private static final float  IMPACT_WALL_SCALE   = 2.5f;
    private static final float  IMPACT_FLOOR_DAMAGE = 9.5f;
    private static final float  IMPACT_FLOOR_SCALE  = 1.8f;

    // Ultimate — Debris Field
    private static final int    DEBRIS_MAX_BLOCKS            = 8;
    private static final double DEBRIS_HARVEST_RADIUS        = 5.0;
    private static final int    DEBRIS_ORBIT_TICKS           = 260;
    private static final double DEBRIS_ORBIT_RADIUS          = 6.5;
    private static final double DEBRIS_ORBIT_RADIUS_INNER    = 2.5;
    private static final double DEBRIS_ORBIT_SPEED           = 0.035;
    private static final double DEBRIS_ORBIT_SPEED_INNER     = 0.20;
    private static final double DEBRIS_PULL_RADIUS           = 12.0;
    private static final double DEBRIS_PULL_STRENGTH         = 0.07;
    private static final float  DEBRIS_PULL_DAMAGE           = 0.5f;
    private static final float  DEBRIS_THROW_DAMAGE          = 13.0f;
    private static final double DEBRIS_THROW_SPEED           = 2.4;
    private static final double DEBRIS_THROW_EXPLOSION_RADIUS = 4.0;
    private static final double DEBRIS_INTERCEPT_RADIUS      = 5.0;

    private static final int DEBRIS_REGEN_DELAY_TICKS = 15;
    private static final int DEBRIS_REGEN_INTERVAL    = 10;
    private static final int DEBRIS_REGEN_AMOUNT      = 4;

    private static final int WOOLLIAM_CHANCE = 70;

    /* ============================================================
       GLOBAL DAMAGE HOOK (yank bonus fall damage)
       ============================================================ */

    /**
     * Returns modified damage amount. Call from ALLOW_DAMAGE hook.
     * Returns same amount if no modification needed.
     */
    public static float onDamageGlobal(LivingEntity victim, DamageSource source, float amount) {
        if (applyingYankDamage) return amount;
        if (source.isOf(DamageTypes.FALL)) {
            TKVictimState vState = ACTIVE_VICTIMS.get(victim.getUuid());
            if (vState != null && vState.yankTicks > 0) {
                vState.yankTicks = 0;
                vState.yankOwner = null;

                if (victim.getWorld() instanceof ServerWorld w) {
                    TKYankFallPayload fx = new TKYankFallPayload(victim.getX(), victim.getY() + 0.5, victim.getZ());
                    PlayerLookup.tracking(w, victim.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
                    w.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_BONE_BLOCK_BREAK, victim.getSoundCategory(), 1.0f, 0.8f);
                }

                return amount + YANK_EXTRA_FALL_DAMAGE;
            }
        }
        return amount;
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

        if (caster != null && player.getServer() != null) {
            String pidStr = player.getUuidAsString();
            for (ServerWorld w : player.getServer().getWorlds()) {
                if (caster.debrisField != null) {
                    for (UUID uuid : caster.debrisField.orbitAngles.keySet()) {
                        Entity e = w.getEntity(uuid);
                        if (e != null) e.discard();
                    }
                }

                for (UUID uuid : caster.thrownBlocks.keySet()) {
                    Entity e = w.getEntity(uuid);
                    if (e != null) e.discard();
                }

                w.getEntitiesByClass(Entity.class, player.getBoundingBox().expand(150),
                        e -> e.getCommandTags().contains("tk_debris") && e.getCommandTags().contains(pidStr))
                        .forEach(Entity::discard);
            }
            caster.debrisField = null;
            caster.thrownBlocks.clear();
        }

        Iterator<Map.Entry<UUID, TKVictimState>> it = ACTIVE_VICTIMS.entrySet().iterator();
        while (it.hasNext()) {
            TKVictimState vState = it.next().getValue();
            if (pid.equals(vState.suspendOwner)) {
                vState.suspendOwner = null;
                vState.suspendTicks = 0;
                vState.chokeTicks   = 0;
            }
            if (pid.equals(vState.impactOwner)) {
                vState.impactOwner  = null;
                vState.airborneTicks = 0;
                vState.prevVelocity = null;
            }
            if (pid.equals(vState.yankOwner)) {
                vState.yankOwner = null;
                vState.yankTicks = 0;
            }
            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0 && vState.yankTicks <= 0) {
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

        boolean isSwinging  = player.handSwinging;
        boolean wasSwinging = caster.prevSwing;

        if (isSwinging && !wasSwinging) {
            if (caster.throwCd <= 0) {
                performThrow(player, caster);
                caster.throwCd = 8;
            }
        }

        // Throw debris every tick while swinging (not just on start)
        if (isSwinging) {
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
            boolean ownsImpact  = player.getUuid().equals(vState.impactOwner);
            boolean ownsYank    = player.getUuid().equals(vState.yankOwner);

            if (!ownsSuspend && !ownsImpact && !ownsYank) continue;

            Entity ent = world.getEntity(entry.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                if (ownsSuspend) { vState.suspendTicks = 0; vState.chokeTicks = 0; vState.suspendOwner = null; }
                if (ownsImpact)  { vState.airborneTicks = 0; vState.impactOwner = null; vState.prevVelocity = null; }
                if (ownsYank)    { vState.yankTicks = 0; vState.yankOwner = null; }
            } else {
                if (ownsSuspend) handleSuspendAndChoke(le, world, vState);
                if (ownsImpact)  handleImpactDamage(le, world, vState);
                if (ownsYank && vState.yankTicks > 0) {
                    vState.yankTicks--;
                    if (vState.yankTicks <= 0) vState.yankOwner = null;
                }
            }

            if (vState.suspendTicks <= 0 && vState.chokeTicks <= 0 && vState.airborneTicks <= 0 && vState.yankTicks <= 0) {
                it.remove();
            }
        }

        handleDebrisField(player, caster);
        tickThrownBlocks(player, caster);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;

        TKCasterState caster = getCasterState(attacker);
        caster.lastSwingTime = attacker.getServerWorld().getTime();

        target.addVelocity(
                attacker.getRotationVec(1.0f).x * PASSIVE_KB_MULT,
                PASSIVE_KB_VERTICAL,
                attacker.getRotationVec(1.0f).z * PASSIVE_KB_MULT);
        target.velocityModified = true;
        if (target instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
        }

        markForImpactTracking(target, target.getVelocity(), attacker.getUuid());

        TKPassiveHitPayload fx = new TKPassiveHitPayload(target.getId());
        PlayerLookup.tracking(attacker.getServerWorld(), target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
    }

    private void markForImpactTracking(LivingEntity entity, Vec3d launchVelocity, UUID attackerUuid) {
        TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(entity.getUuid(), k -> new TKVictimState());
        vState.airborneTicks = TK_AIRBORNE_TICKS;
        vState.impactOwner   = attackerUuid;
        vState.prevVelocity  = launchVelocity;
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
            boolean floorHit    = prev.y < -IMPACT_MIN_SPEED_V && entity.isOnGround();

            Entity attacker = resolveOwner(vState.impactOwner, world);

            if (wallStopped) {
                float damage = IMPACT_WALL_DAMAGE + (float)(prevH - IMPACT_MIN_SPEED_H) * IMPACT_WALL_SCALE;
                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);

                TKWallImpactPayload fx = new TKWallImpactPayload(entity.getId());
                PlayerLookup.tracking(world, entity.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_STONE_HIT, entity.getSoundCategory(), 0.9f, 0.7f);

                vState.airborneTicks = 0; vState.prevVelocity = null; vState.impactOwner = null;
                return;
            }

            if (floorHit) {
                float damage = IMPACT_FLOOR_DAMAGE + (float)(-prev.y - IMPACT_MIN_SPEED_V) * IMPACT_FLOOR_SCALE;
                entity.damage(ModDamageTypes.wallCollision(world, attacker), damage);

                TKFloorImpactPayload fx = new TKFloorImpactPayload(entity.getId());
                PlayerLookup.tracking(world, entity.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
                world.playSound(null, entity.getBlockPos(), SoundEvents.BLOCK_STONE_FALL, entity.getSoundCategory(), 0.8f, 0.9f);

                vState.airborneTicks = 0; vState.prevVelocity = null; vState.impactOwner = null;
                return;
            }
        }

        vState.prevVelocity = curr;
        if (vState.airborneTicks <= 0) { vState.impactOwner = null; vState.prevVelocity = null; }
    }

    /* ============================================================
       PRIMARY — YANK
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(YANK_RANGE));

        TKBeamPayload beam = new TKBeamPayload(origin.x, origin.y, origin.z, end.x, end.y, end.z);
        sendToViewers(world, player, beam);

        boolean hitAnything = false;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(YANK_RANGE),
                en -> en.isAlive() && en != player)) {

            Vec3d toTarget = e.getPos().subtract(origin).normalize();
            if (look.dotProduct(toTarget) < YANK_CONE_DOT) continue;

            Vec3d pull     = origin.subtract(e.getPos()).normalize().multiply(YANK_STRENGTH);
            Vec3d launchVel = e.getVelocity().add(pull.x, pull.y + YANK_VERTICAL, pull.z);
            e.setVelocity(launchVel);
            e.velocityModified = true;
            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
            }

            markForImpactTracking(e, launchVel, player.getUuid());

            // Track yank window for bonus fall damage
            TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(e.getUuid(), k -> new TKVictimState());
            vState.yankTicks = YANK_FALL_WINDOW_TICKS;
            vState.yankOwner = player.getUuid();

            TKYankTargetPayload fx = new TKYankTargetPayload(e.getId());
            PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
            hitAnything = true;
        }

        world.playSound(null, player.getBlockPos(), ModSounds.YANK, player.getSoundCategory(), 0.6f, 1.2f);
        if (hitAnything) world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, player.getSoundCategory(), 0.4f, 1.5f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY — SUSPEND / THROW
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(THROW_SCAN_RANGE));

        TKBeamPayload beam = new TKBeamPayload(origin.x, origin.y, origin.z, end.x, end.y, end.z);
        sendToViewers(world, player, beam);

        int grabbed = 0;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new Box(origin, end).expand(THROW_SCAN_WIDTH),
                en -> en.isAlive() && en != player)) {

            TKVictimState vState = ACTIVE_VICTIMS.computeIfAbsent(e.getUuid(), k -> new TKVictimState());
            vState.suspendTicks = SUSPEND_TICKS;
            vState.chokeTicks   = 0;
            vState.suspendOwner = player.getUuid();

            TKGrabTargetPayload fx = new TKGrabTargetPayload(e.getId());
            PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
            grabbed++;
        }

        if (grabbed > 0) {
            TKCasterState caster = getCasterState(player);
            caster.readyThrowTicks = THROW_READY_TICKS;
        }

        world.playSound(null, player.getBlockPos(), ModSounds.SUSPEND, player.getSoundCategory(), 0.7f, 0.9f);
    }

    private void handleSuspendAndChoke(LivingEntity e, ServerWorld world, TKVictimState vState) {
        if (vState.suspendTicks > 0) {
            vState.suspendTicks--;
            e.setVelocity(e.getVelocity().x * 0.3, SUSPEND_FLOAT_VEL, e.getVelocity().z * 0.3);
            e.velocityModified = true;
            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
            }
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 5, 4, true, false, false));

            TKSuspendAuraPayload fx = new TKSuspendAuraPayload(e.getId(), world.getTime());
            PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));

            if (vState.suspendTicks <= 0) {
                vState.chokeTicks = CHOKE_TICKS;

                if (e instanceof ServerPlayerEntity && world.random.nextInt(QUOTE_CHANCE) == 0) {
                    Entity owner = resolveOwner(vState.suspendOwner, world);
                    if (owner instanceof ServerPlayerEntity attacker) {
                        world.getServer().getPlayerManager().broadcast(Text.literal(
                                "<" + attacker.getName().getString() + "> I find your lack of faith... disturbing..."), false);
                    }
                }
            }
            return;
        }

        if (vState.chokeTicks > 0) {
            boolean chokeEntry = (vState.chokeTicks == CHOKE_TICKS);
            vState.chokeTicks--;
            float chokeProgress = 1.0f - ((float) vState.chokeTicks / CHOKE_TICKS);

            double newY = chokeEntry ? CHOKE_ENTRY_LIFT : CHOKE_HOVER_VEL;
            e.setVelocity(e.getVelocity().x * 0.2, newY, e.getVelocity().z * 0.2);
            e.velocityModified = true;
            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
            }

            if (e.age % CHOKE_DAMAGE_INTERVAL == 0) {
                Entity attacker = resolveOwner(vState.suspendOwner, world);
                e.damage(ModDamageTypes.strangle(world, attacker), CHOKE_DAMAGE_PER_TICK * (0.5f + chokeProgress));
                world.playSound(null, e.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, e.getSoundCategory(), 0.4f + chokeProgress * 0.3f, 0.9f);
            }

            TKChokeAuraPayload fx = new TKChokeAuraPayload(e.getId(), world.getTime(), chokeProgress);
            PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));

            if (vState.chokeTicks <= 0) vState.suspendOwner = null;
        }
    }

    private void performThrow(ServerPlayerEntity player, TKCasterState caster) {
        if (caster.readyThrowTicks <= 0) return;
        caster.readyThrowTicks = 0;

        Vec3d look  = player.getRotationVec(1.0f);
        Vec3d throwVel = new Vec3d(look.x * THROW_SPEED_H, THROW_SPEED_V, look.z * THROW_SPEED_H);
        ServerWorld world = player.getServerWorld();
        boolean thrown = false;

        for (Map.Entry<UUID, TKVictimState> entry : ACTIVE_VICTIMS.entrySet()) {
            TKVictimState vState = entry.getValue();
            if ((vState.suspendTicks > 0 || vState.chokeTicks > 0) && player.getUuid().equals(vState.suspendOwner)) {
                Entity e = world.getEntity(entry.getKey());
                if (e instanceof LivingEntity le && le.isAlive() && le.squaredDistanceTo(player) <= (THROW_RELEASE_RANGE * THROW_RELEASE_RANGE)) {
                    vState.suspendTicks = 0;
                    vState.chokeTicks   = 0;
                    vState.suspendOwner = null;

                    le.setVelocity(throwVel);
                    le.velocityModified = true;
                    if (le instanceof ServerPlayerEntity sp) {
                        sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
                    }

                    markForImpactTracking(le, throwVel, player.getUuid());

                    TKThrowPayload fx = new TKThrowPayload(le.getId());
                    PlayerLookup.tracking(world, le.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));
                    thrown = true;
                }
            }
        }

        if (thrown) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, player.getSoundCategory(), 0.6f, 1.4f);
        }
    }

    /* ============================================================
       ULTIMATE — DEBRIS FIELD
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

        String pidStr = player.getUuidAsString();
        world.getEntitiesByClass(Entity.class, player.getBoundingBox().expand(150),
                e -> e.getCommandTags().contains("tk_debris") && e.getCommandTags().contains(pidStr))
                .forEach(Entity::discard);

        DebrisField field = new DebrisField();
        field.ticksRemaining = DEBRIS_ORBIT_TICKS;
        caster.debrisField = field;

        queueHarvestBlocks(player, world, field);

        TKDebrisActivatePayload fx = new TKDebrisActivatePayload(player.getX(), player.getBodyY(0.5), player.getZ());
        sendToViewers(world, player, fx);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 1.2f, 0.4f);
    }

    private void queueHarvestBlocks(ServerPlayerEntity player, ServerWorld world, DebrisField field) {
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
        int count = 0;
        for (BlockPos pos : candidates) {
            if (count >= DEBRIS_MAX_BLOCKS) break;
            field.harvestQueue.add(pos);
            count++;
        }
    }

    private void harvestSingleBlock(ServerPlayerEntity player, ServerWorld world, DebrisField field, BlockPos pos) {
        // Skip ahead if the queued block is no longer harvestable
        while (!isHarvestable(world, pos) && !field.harvestQueue.isEmpty()) {
            pos = field.harvestQueue.poll();
        }
        if (!isHarvestable(world, pos)) return;

        BlockState state = world.getBlockState(pos);

        Entity orbitEntity;

        if (!field.wooliamSpawned && world.random.nextInt(WOOLLIAM_CHANCE) == 0) {
            net.minecraft.entity.passive.SheepEntity sheep = net.minecraft.entity.EntityType.SHEEP.create(world);
            if (sheep != null) {
                sheep.setCustomName(Text.literal("Woolliam"));
                sheep.setNoGravity(true);
                sheep.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
                world.removeBlock(pos, false);
                world.spawnEntity(sheep);
                orbitEntity = sheep;
                field.wooliamSpawned = true;
            } else {
                world.removeBlock(pos, false);
                orbitEntity = FallingBlockEntity.spawnFromBlock(world, pos, state);
            }
        } else {
            world.removeBlock(pos, false);
            orbitEntity = FallingBlockEntity.spawnFromBlock(world, pos, state);
        }

        if (orbitEntity instanceof FallingBlockEntity falling) {
            falling.setNoGravity(true);
            falling.dropItem  = false;
            falling.timeFalling = -32768;
        }

        orbitEntity.addCommandTag("tk_debris");
        orbitEntity.addCommandTag(player.getUuidAsString());
        field.orbitAngles.put(orbitEntity.getUuid(), world.random.nextDouble() * Math.PI * 2.0);

        world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.5f, 0.6f);

        TKDebrisSpawnPayload spawnFx = new TKDebrisSpawnPayload(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        sendToViewers(world, player, spawnFx);
    }

    private boolean isHarvestable(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || world.getBlockEntity(pos) != null) return false;
        if (!state.isOpaque()) return false;

        float hardness = state.getHardness(world, pos);
        if (hardness < 0 || hardness > 4.0f) return false;

        return state.isIn(BlockTags.LOGS)
                || state.isIn(BlockTags.DIRT)
                || state.isIn(BlockTags.SAND)
                || state.isIn(BlockTags.SNOW)
                || state.isIn(BlockTags.PICKAXE_MINEABLE)
                || state.isIn(BlockTags.SHOVEL_MINEABLE)
                || state.isIn(BlockTags.AXE_MINEABLE)
                || state.isIn(BlockTags.HOE_MINEABLE)
                || state.isIn(BlockTags.LEAVES);
    }

    private void handleDebrisField(ServerPlayerEntity player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null || field.ticksRemaining <= 0) return;

        ServerWorld world = player.getServerWorld();

        // Harvest one block per tick from the queue
        if (!field.harvestQueue.isEmpty()) {
            BlockPos pos = field.harvestQueue.poll();
            harvestSingleBlock(player, world, field, pos);
        }

        handleDebrisRegen(player, caster, field);
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

            if (ent == null || ent.isRemoved()) {
                toRemove.add(uuid);
                index++;
                continue;
            }

            boolean isInner   = (index % 2 == 1);
            double orbitRadius = isInner ? DEBRIS_ORBIT_RADIUS_INNER : DEBRIS_ORBIT_RADIUS;
            double orbitSpeed  = isInner ? -DEBRIS_ORBIT_SPEED_INNER  : DEBRIS_ORBIT_SPEED;

            double angle = (Math.PI * 2.0 * (double)(index / 2)) / (double) Math.max(1, total / 2) + (world.getTime() * orbitSpeed);
            entry.setValue(angle);

            double x = playerPos.x + Math.cos(angle) * orbitRadius;
            double z = playerPos.z + Math.sin(angle) * orbitRadius;
            double y = playerPos.y + Math.sin(angle * 1.5 + world.getTime() * 0.08) * 0.5 + (isInner ? 0.3 : 0.0);

            Vec3d target = new Vec3d(x, y, z);
            ent.setVelocity(target.subtract(ent.getPos()).multiply(0.35));
            ent.velocityModified = true;

            if (target.distanceTo(ent.getPos()) > 5.0) {
                ent.setPos(target.x, target.y, target.z);
                world.getChunkManager().sendToNearbyPlayers(ent, new EntityPositionS2CPacket(ent));
            }

            ent.setNoGravity(true);
            if (ent instanceof FallingBlockEntity fb) fb.timeFalling = -32768;

            TKDebrisOrbitPayload orbitFx = new TKDebrisOrbitPayload(target.x, target.y, target.z, isInner);
            sendToViewers(world, player, orbitFx);

            index++;
        }
        toRemove.forEach(field.orbitAngles::remove);

        // Pull + contact damage nearby entities
        double contactRadius = DEBRIS_ORBIT_RADIUS_INNER * 1.2;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(DEBRIS_PULL_RADIUS),
                en -> en.isAlive() && en != player)) {

            Vec3d toPlayer = playerPos.subtract(e.getPos());
            double dist = toPlayer.length();
            if (dist < 0.5 || dist > DEBRIS_PULL_RADIUS) continue;

            double strength = DEBRIS_PULL_STRENGTH * (1.0 - dist / DEBRIS_PULL_RADIUS);
            Vec3d pull = toPlayer.normalize().multiply(strength);

            e.addVelocity(pull.x, pull.y * 0.3, pull.z);
            e.velocityModified = true;
            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
            }

            if (dist <= contactRadius && world.getTime() % 10 == 0) {
                e.damage(ModDamageTypes.debrisOrbit(world, player), DEBRIS_PULL_DAMAGE);
            }

            if (world.getTime() % 4 == 0) {
                TKDebrisPullPayload pullFx = new TKDebrisPullPayload(e.getId(), (float)(pull.x * 2), (float)(pull.y * 2), (float)(pull.z * 2));
                PlayerLookup.tracking(world, e.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, pullFx));
            }
        }

        interceptProjectiles(player, world, field);

        TKDebrisAuraPayload auraFx = new TKDebrisAuraPayload(playerPos.x, playerPos.y, playerPos.z, world.getTime());
        sendToViewers(world, player, auraFx);
    }

    private void interceptProjectiles(ServerPlayerEntity player, ServerWorld world, DebrisField field) {
        List<ProjectileEntity> projectiles = world.getEntitiesByClass(ProjectileEntity.class,
                player.getBoundingBox().expand(DEBRIS_INTERCEPT_RADIUS),
                p -> p.getOwner() != player && p.isAlive());

        for (ProjectileEntity p : projectiles) {
            if (field.orbitAngles.isEmpty()) break;

            UUID sacrificeId = field.orbitAngles.keySet().iterator().next();
            field.orbitAngles.remove(sacrificeId);
            Entity sacrifice = world.getEntity(sacrificeId);

            Vec3d fxPos = (sacrifice != null) ? sacrifice.getPos() : p.getPos();
            TKDebrisExplosionPayload impactFx = new TKDebrisExplosionPayload(fxPos.x, fxPos.y, fxPos.z);
            sendToViewers(world, player, impactFx);

            if (sacrifice != null) sacrifice.discard();
            p.discard();

            world.playSound(null, p.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.8f);
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5f, 1.2f);
        }
    }

    private void throwDebrisProjectile(ServerPlayerEntity player, TKCasterState caster) {
        DebrisField field = caster.debrisField;
        if (field == null || field.orbitAngles.isEmpty()) return;
        if (caster.debrisThrowCd > 0) return;

        ServerWorld world = player.getServerWorld();

        Iterator<Map.Entry<UUID, Double>> it = field.orbitAngles.entrySet().iterator();
        if (!it.hasNext()) return;

        Map.Entry<UUID, Double> entry = it.next();
        UUID uuid = entry.getKey();
        Entity orbitEntity = world.getEntity(uuid);
        it.remove();

        if (orbitEntity == null || orbitEntity.isRemoved()) return;

        orbitEntity.setNoGravity(true);
        if (orbitEntity instanceof FallingBlockEntity fb) {
            fb.timeFalling = -32768;
            fb.dropItem = false;
        }

        // Raycast to determine aimed target point
        Vec3d eyePos    = player.getEyePos();
        Vec3d look      = player.getRotationVec(1.0f);
        Vec3d targetPt  = eyePos.add(look.multiply(60.0));

        var hit = world.raycast(new RaycastContext(eyePos, targetPt,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            targetPt = hit.getPos();
        }

        Vec3d vel = targetPt.subtract(orbitEntity.getPos()).normalize().multiply(DEBRIS_THROW_SPEED);
        orbitEntity.setVelocity(vel);
        orbitEntity.velocityModified = true;

        caster.thrownBlocks.put(uuid, orbitEntity.getPos());

        TKDebrisThrowPayload fx = new TKDebrisThrowPayload(orbitEntity.getX(), orbitEntity.getY(), orbitEntity.getZ());
        sendToViewers(world, player, fx);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EGG_THROW, player.getSoundCategory(), 1.0f, 0.6f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, player.getSoundCategory(), 0.8f, 1.8f);

        caster.debrisThrowCd = 3;
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

            Vec3d vel = ent.getVelocity();
            boolean hitSomething = ent.horizontalCollision || ent.isOnGround() || vel.lengthSquared() < 0.2;

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
                new Box(pos, pos).expand(DEBRIS_THROW_EXPLOSION_RADIUS),
                en -> en.isAlive() && en != player)) {

            double dist = e.getPos().distanceTo(pos);
            if (dist > DEBRIS_THROW_EXPLOSION_RADIUS) continue;

            float damage = DEBRIS_THROW_DAMAGE * (float)(1.0 - dist / DEBRIS_THROW_EXPLOSION_RADIUS);
            e.damage(ModDamageTypes.blockThrow(world, player), damage);

            Vec3d knockback = e.getPos().subtract(pos).normalize().multiply(0.8);
            e.addVelocity(knockback.x, 0.4, knockback.z);
            e.velocityModified = true;
            if (e instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp.getId(), sp.getVelocity()));
            }

            markForImpactTracking(e, e.getVelocity(), player.getUuid());
        }

        TKDebrisExplosionPayload fx = new TKDebrisExplosionPayload(pos.x, pos.y, pos.z);
        sendToViewers(world, player, fx);

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), player.getSoundCategory(), 0.6f, 0.5f);
    }

    private void handleDebrisRegen(ServerPlayerEntity player, TKCasterState caster, DebrisField field) {
        if (!field.harvestQueue.isEmpty()) return;

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
        BlockPos targetPos = null;
        BlockPos center = player.getBlockPos();
        int radius = 8;

        for (int i = 0; i < 30; i++) {
            BlockPos p = center.add(
                    world.random.nextInt(radius * 2) - radius,
                    world.random.nextInt(8) - 4,
                    world.random.nextInt(radius * 2) - radius);
            if (isHarvestable(world, p)) {
                targetPos = p;
                break;
            }
        }

        BlockState state;
        double px, py, pz;

        if (targetPos != null) {
            state = world.getBlockState(targetPos);
            world.removeBlock(targetPos, false);
            px = targetPos.getX() + 0.5;
            py = targetPos.getY();
            pz = targetPos.getZ() + 0.5;

            world.playSound(null, targetPos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.5f, 0.6f);

            TKDebrisSpawnPayload fx = new TKDebrisSpawnPayload(px, py + 0.5, pz);
            sendToViewers(world, player, fx);

            FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, targetPos, state);
            block.setNoGravity(true);
            block.dropItem   = false;
            block.timeFalling = -32768;
            block.addCommandTag("tk_debris");
            block.addCommandTag(player.getUuidAsString());
            field.orbitAngles.put(block.getUuid(), world.random.nextDouble() * Math.PI * 2);
        } else {
            // Fallback: create a stone block above the player out of thin air
            state = Blocks.STONE.getDefaultState();
            px = player.getX();
            py = player.getY() + 2.0;
            pz = player.getZ();

            BlockPos safePos = player.getBlockPos().up(15);
            BlockState original = world.getBlockState(safePos);
            FallingBlockEntity block = FallingBlockEntity.spawnFromBlock(world, safePos, state);
            world.setBlockState(safePos, original, 3);
            block.setPos(px, py, pz);
            block.setNoGravity(true);
            block.dropItem   = false;
            block.timeFalling = -32768;

            field.orbitAngles.put(block.getUuid(), world.random.nextDouble() * Math.PI * 2);
        }
    }

    /* ============================================================
       CLEANSES
       ============================================================ */

    public static boolean cleanseTelekinesis(LivingEntity target) {
        TKVictimState vState = ACTIVE_VICTIMS.get(target.getUuid());
        if (vState != null && (vState.suspendTicks > 0 || vState.chokeTicks > 0 || vState.yankTicks > 0)) {
            vState.suspendTicks = 0;
            vState.chokeTicks   = 0;
            vState.suspendOwner = null;
            vState.yankTicks    = 0;
            vState.yankOwner    = null;
            target.removeStatusEffect(StatusEffects.SLOWNESS);
            return true;
        }
        return false;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static Entity resolveOwner(UUID ownerUuid, ServerWorld world) {
        if (ownerUuid == null) return null;
        return world.getServer().getPlayerManager().getPlayer(ownerUuid);
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return Text.translatable("power.loopypowers.telekinesis.name").getString(); }
    @Override public String getPassiveName()   { return Text.translatable("power.loopypowers.telekinesis.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.telekinesis.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.telekinesis.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.telekinesis.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 11_000; }
    @Override public long getSecondaryCooldownMs() { return 37_000; }
    @Override public long getUltimateCooldownMs()  { return 310_000; }

    @Override public String getOverviewDescription()  { return Text.translatable("power.loopypowers.telekinesis.description.overview").getString(); }
    @Override public String getPassiveDescription()   { return Text.translatable("power.loopypowers.telekinesis.description.passive").getString(); }
    @Override public String getPrimaryDescription()   { return Text.translatable("power.loopypowers.telekinesis.description.primary").getString(); }
    @Override public String getSecondaryDescription() { return Text.translatable("power.loopypowers.telekinesis.description.secondary").getString(); }
    @Override public String getUltimateDescription()  { return Text.translatable("power.loopypowers.telekinesis.description.ultimate").getString(); }
}
