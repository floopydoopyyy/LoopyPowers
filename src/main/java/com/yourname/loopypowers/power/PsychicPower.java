package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.CompelEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.PuppetryEntity;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;

public class PsychicPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, PsychicState> ACTIVE_STATES = new HashMap<>();

    private static class PsychicState {
        int leechCd = 0; // replaces LEECH_CD_TAG on the player
        final Map<UUID, CompelEntry>   compelled = new HashMap<>();
        final Map<UUID, SpikedEntry>   spiked    = new HashMap<>();
        final Map<UUID, PossessedEntry> possessed = new HashMap<>();
    }

    /** Shared base so onRemove can iterate all entry types generically. */
    private abstract static class ControlEntry {
        final UUID target;
        final RegistryKey<World> worldKey;
        int ticksLeft;

        ControlEntry(UUID target, RegistryKey<World> worldKey, int ticksLeft) {
            this.target    = target;
            this.worldKey  = worldKey;
            this.ticksLeft = ticksLeft;
        }
    }

    private static class CompelEntry extends ControlEntry {
        CompelEntry(UUID target, RegistryKey<World> worldKey) {
            super(target, worldKey, COMPEL_DURATION);
        }
    }

    private static class SpikedEntry extends ControlEntry {
        SpikedEntry(UUID target, RegistryKey<World> worldKey) {
            super(target, worldKey, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION);
        }
    }

    private static class PossessedEntry extends ControlEntry {
        int attackCd; // replaces ATTACK_CD_TAG on the entity

        PossessedEntry(UUID target, RegistryKey<World> worldKey) {
            super(target, worldKey, ULT_CONTROL_DURATION);
            this.attackCd = 0;
        }
    }

    private static PsychicState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new PsychicState());
    }

    /* ============================================================
       TUNING CONSTANTS
       ============================================================ */

    // Passive
    private static final float LEECH_HEAL       = 3.5f;
    private static final long  LEECH_CDR_MS     = 500;
    private static final int   LEECH_INTERNAL_CD = 35;

    // Compel (primary)
    private static final int    COMPEL_DURATION         = 80;
    private static final double COMPEL_STOP_DISTANCE    = 2.3;
    private static final double COMPEL_MOB_SPEED        = 0.20;
    private static final double COMPEL_PLAYER_ACCEL     = 0.14;
    private static final double COMPEL_PLAYER_MAX_SPEED = 0.40;
    private static final float  COMPEL_LOOK_STRENGTH    = 0.25f;
    private static final double COMPEL_LOOK_MIN_DIST    = 2.5;
    private static final double COMPEL_PROJECTILE_SPEED = 1.3;

    // Spike (secondary)
    private static final double SPIKE_BEAM_RANGE        = 18.0;
    private static final double SPIKE_CHAIN_RADIUS      = 2.0;
    private static final int    SPIKE_CHAIN_COUNT       = 8;
    private static final int    SPIKE_STUN_DURATION     = 30;
    private static final int    SPIKE_SLOW_DURATION     = 100;
    private static final int    SPIKE_STUN_AMPLIFIER    = 4;
    private static final int    SPIKE_SLOW_AMPLIFIER    = 1;
    private static final int    SPIKE_FATIGUE_DURATION  = 120;
    private static final int    SPIKE_FATIGUE_AMPLIFIER = 0;
    private static final int    SPIKE_JAGGED_SEGMENTS   = 18;
    private static final int    SPIKE_CHAIN_SEGMENTS    = 10;
    private static final double SPIKE_JAGGED_OFFSET     = 0.55;

    // Ultimate
    private static final double ULT_RANGE            = 40.0;
    private static final int    ULT_CONTROL_DURATION  = 180;
    private static final double ULT_PLAYER_ACCEL      = 0.30;
    private static final double ULT_PLAYER_MAX_SPEED  = 0.65;
    private static final double ULT_MOB_SPEED         = 0.36;
    private static final float  ULT_LOOK_STRENGTH     = 0.3f;
    private static final double ULT_STOP_DISTANCE     = 1.5;
    private static final int    ATTACK_COOLDOWN        = 35;

    // Easter egg
    private static final double COMPEL_CHAT_CHANCE = 0.05;
    private static final List<String> STUPID_MESSAGES = List.of(
            "I think I'll use my credit card.",
            "erm is this thing on?",
            "do u guys like Radiohead?",
            "I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG.",
            "roflcopter!!!",
            "i really need a wee",
            "hop on MARVEL RIVALS?",
            "my tummy hurt :(",
            "hello everyone my name is welcome",
            "morp",
            "haha six seven",
            "throw me into the wolves, and i'll come back pregnant",
            "JOIN THE REBELLION"
    );

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // Clear any stale state from a previous power assignment
        ACTIVE_STATES.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        PsychicState state = ACTIVE_STATES.remove(player.getUuid());

        // Projectile entities have no state entry — world scan still required here,
        // but this only runs on disconnect/death so the cost is acceptable.
        if (player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                w.getEntitiesByClass(CompelEntity.class,   player.getBoundingBox().expand(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
                w.getEntitiesByClass(PuppetryEntity.class, player.getBoundingBox().expand(150), e -> player.equals(e.getOwner())).forEach(Entity::discard);
            }
        }

        if (state == null || player.getServer() == null) return;

        // OPTIMIZATION: Release controlled entities via exact UUID lookup instead of
        // the old broad entity scan across ALL entities in ALL worlds (150-block radius).
        MinecraftServer server = player.getServer();
        cleanupEntries(server, state.compelled.values(),  le -> le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.COMPELLED)));
        cleanupEntries(server, state.spiked.values(),     le -> {
            le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.STUN));
            le.removeStatusEffect(StatusEffects.SLOWNESS);
            le.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        });
        cleanupEntries(server, state.possessed.values(),  le -> {
            le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.POSSESSED));
            le.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        });
    }

    /** Resolve each entry's UUID in its stored world and run a cleanup action. */
    private static void cleanupEntries(MinecraftServer server,
                                       Collection<? extends ControlEntry> entries,
                                       Consumer<LivingEntity> fn) {
        for (ControlEntry entry : entries) {
            ServerWorld w = server.getWorld(entry.worldKey);
            if (w == null) continue;
            Entity ent = w.getEntity(entry.target);
            if (ent instanceof LivingEntity le) fn.accept(le);
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        PsychicState state = getState(player);

        // Tick the leech internal cooldown (replaces tickTag on player)
        if (state.leechCd > 0) state.leechCd--;

        tickCompel(player, state);
        tickSpike(player, state);
        tickUltimate(player, state);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;

        PsychicState state = getState(attacker);
        if (state.leechCd > 0) return;

        // OPTIMIZATION: Direct map lookup instead of scanning command tags on the entity
        UUID tid = target.getUuid();
        if (!state.compelled.containsKey(tid)
                && !state.spiked.containsKey(tid)
                && !state.possessed.containsKey(tid)) return;

        ServerWorld world = attacker.getServerWorld();

        attacker.heal(LEECH_HEAL);

        PsychicLeechPayload leechPayload = new PsychicLeechPayload(attacker.getId(), target.getId());
        sendToViewers(world, attacker, leechPayload);

        world.playSound(null, attacker.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                attacker.getSoundCategory(), 0.6f, 1.4f);
        PowerManager.reduceAllCooldowns(attacker, LEECH_CDR_MS);

        state.leechCd = LEECH_INTERNAL_CD;
    }

    /* ============================================================
       PRIMARY — COMPEL
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        CompelEntity proj = new CompelEntity(ModEntities.COMPEL_ENTITY, world);
        proj.setOwner(player);

        Vec3d eyePos = player.getEyePos();
        proj.setPos(eyePos.x, eyePos.y, eyePos.z);

        Vec3d look = player.getRotationVec(1.0f);
        proj.setVelocity(
                look.x * COMPEL_PROJECTILE_SPEED,
                look.y * COMPEL_PROJECTILE_SPEED,
                look.z * COMPEL_PROJECTILE_SPEED
        );

        world.spawnEntity(proj);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                player.getSoundCategory(), 0.7f, 1.2f);
    }

    public static void applyCompel(ServerPlayerEntity caster, LivingEntity target) {
        PsychicState state = getState(caster);
        // Overwrite any existing entry to reset the timer
        state.compelled.put(target.getUuid(),
                new CompelEntry(target.getUuid(), target.getWorld().getRegistryKey()));

        target.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.COMPELLED), COMPEL_DURATION, 0, false, false, true));

        // Easter egg: compelled player says something stupid in chat
        if (target instanceof ServerPlayerEntity player && player.getServer() != null) {
            if (player.getRandom().nextDouble() < COMPEL_CHAT_CHANCE) {
                String msg = STUPID_MESSAGES.get(player.getRandom().nextInt(STUPID_MESSAGES.size()));
                net.minecraft.text.Text chatText = net.minecraft.text.Text.literal(
                        "<" + player.getName().getString() + "> " + msg);
                player.getServer().getPlayerManager().broadcast(chatText, false);
            }
        }
    }

    private static void tickCompel(ServerPlayerEntity player, PsychicState state) {
        // DEBUG: test control by compelling the caster itself towards the nearest entity
        if (player.getCommandTags().contains("psy_debug")) {
            ServerWorld w = player.getServerWorld();
            LivingEntity nearest = w.getClosestEntity(
                    LivingEntity.class,
                    net.minecraft.entity.ai.TargetPredicate.DEFAULT,
                    player, player.getX(), player.getY(), player.getZ(),
                    player.getBoundingBox().expand(20)
            );
            if (nearest != null && nearest != player) {
                Vec3d dir = nearest.getEyePos().subtract(player.getEyePos());
                double dist = dir.length();
                if (dist > 0.0001) dir = dir.normalize();
                if (dist > COMPEL_LOOK_MIN_DIST) forceLook(player, dir, COMPEL_LOOK_STRENGTH);
                applyMovement(player, dir, dist, COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);
                applyControlEffects(player);

                PsychicCompelAuraPayload aura = new PsychicCompelAuraPayload(player.getId());
                sendToViewers(w, player, aura);
            }
        }

        if (state.compelled.isEmpty()) return;

        Iterator<CompelEntry> it = state.compelled.values().iterator();
        while (it.hasNext()) {
            CompelEntry entry = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.COMPELLED));
                it.remove();
                continue;
            }

            Vec3d dir = player.getEyePos().subtract(le.getEyePos());
            double distance = dir.length();
            if (distance > 0.0001) dir = dir.normalize();

            if (distance > COMPEL_LOOK_MIN_DIST) {
                forceLook(le, dir, COMPEL_LOOK_STRENGTH);
            }
            applyMovement(le, dir, distance, COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);
            applyControlEffects(le);

            PsychicCompelAuraPayload aura = new PsychicCompelAuraPayload(le.getId());
            PlayerLookup.tracking(w, le.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, aura));
        }
    }

    private static void applyControlEffects(LivingEntity entity) {
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 1, true, false, false));
    }

    /* ============================================================
       SECONDARY — SPIKE
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(SPIKE_BEAM_RANGE));

        // Terminate beam at first block hit
        var blockHit = world.raycast(new RaycastContext(
                origin, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        sendJaggedBeam(world, player, origin, end, SPIKE_JAGGED_SEGMENTS, (float) SPIKE_JAGGED_OFFSET);

        LivingEntity primaryTarget = getEntityOnBeam(world, player, origin, end);

        // Collect chain targets near the beam line
        Vec3d finalEnd = end;
        List<LivingEntity> chainTargets = new ArrayList<>();
        world.getEntitiesByClass(LivingEntity.class,
                        new Box(origin, finalEnd).expand(SPIKE_CHAIN_RADIUS),
                        e -> e.isAlive() && e != player && e != primaryTarget
                ).stream()
                .filter(e -> distanceToLine(origin, finalEnd, e.getEyePos()) < SPIKE_CHAIN_RADIUS)
                .sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                .limit(SPIKE_CHAIN_COUNT)
                .forEach(chainTargets::add);

        PsychicState state = getState(player);

        if (primaryTarget != null) {
            applySpike(state, primaryTarget);
            PsychicSpikeImpactPayload impact = new PsychicSpikeImpactPayload(primaryTarget.getId());
            PlayerLookup.tracking(world, primaryTarget.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, impact));
        }

        Vec3d chainOrigin = (primaryTarget != null) ? primaryTarget.getEyePos() : end;
        for (LivingEntity chain : chainTargets) {
            sendJaggedBeam(world, player, chainOrigin, chain.getEyePos(), SPIKE_CHAIN_SEGMENTS, (float)(SPIKE_JAGGED_OFFSET * 0.7));
            applySpike(state, chain);
            PsychicSpikeImpactPayload impact = new PsychicSpikeImpactPayload(chain.getId());
            PlayerLookup.tracking(world, chain.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, impact));
            chainOrigin = chain.getEyePos();
        }

        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, player.getSoundCategory(), 0.8f, 0.7f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,  player.getSoundCategory(), 0.3f, 1.8f);
    }

    private static void applySpike(PsychicState state, LivingEntity target) {
        // Overwrite entry to reset timer if hit again
        state.spiked.put(target.getUuid(),
                new SpikedEntry(target.getUuid(), target.getWorld().getRegistryKey()));

        target.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.STUN), SPIKE_STUN_DURATION, SPIKE_STUN_AMPLIFIER, false, false, true));
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION, SPIKE_SLOW_AMPLIFIER, false, false, true));
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.MINING_FATIGUE, SPIKE_FATIGUE_DURATION, SPIKE_FATIGUE_AMPLIFIER, false, false, true));
    }

    /**
     * OPTIMIZATION: Replaces a broad 23-block getEntitiesByClass scan (every tick)
     * with direct iteration over only the entities we know are spiked.
     */
    private static void tickSpike(ServerPlayerEntity player, PsychicState state) {
        if (state.spiked.isEmpty()) return;

        long time = player.getServerWorld().getTime();

        Iterator<SpikedEntry> it = state.spiked.values().iterator();
        while (it.hasNext()) {
            SpikedEntry entry = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) { it.remove(); continue; }

            PsychicSpikeAuraPayload aura = new PsychicSpikeAuraPayload(le.getId());
            PlayerLookup.tracking(w, le.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, aura));

            // Send a 15-tick subtle shake every 10 ticks to avoid network spam
            if (le instanceof ServerPlayerEntity targetPlayer && time % 10 == 0) {
                shake(targetPlayer, 15, 0.05f);
            }
        }
    }

    public static void shake(ServerPlayerEntity target, int ticks, float strength) {
        CameraShake.shakeNearby(target, 10.0, ticks, strength);
    }

    /* ============================================================
       ULTIMATE — PUPPETRY
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        PuppetryEntity proj = new PuppetryEntity(ModEntities.PUPPETRY_ENTITY, world);
        proj.setOwner(player);

        Vec3d eyePos = player.getEyePos();
        proj.setPos(eyePos.x, eyePos.y, eyePos.z);

        Vec3d look = player.getRotationVec(1.0f);
        proj.setVelocity(
                look.x * PuppetryEntity.SPEED,
                look.y * PuppetryEntity.SPEED,
                look.z * PuppetryEntity.SPEED
        );

        world.spawnEntity(proj);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                player.getSoundCategory(), 1.0f, 0.6f);
    }

    /**
     * Called by PuppetryEntity when it hits a target.
     * NOTE: PuppetryEntity must be updated to pass the caster as the first argument:
     * PsychicPower.applyUltimateControl((ServerPlayerEntity) getOwner(), target)
     */
    public static void applyUltimateControl(ServerPlayerEntity caster, LivingEntity target) {
        PsychicState state = getState(caster);
        // Overwrite to reset timer if controlled again
        state.possessed.put(target.getUuid(),
                new PossessedEntry(target.getUuid(), target.getWorld().getRegistryKey()));

        target.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.POSSESSED), ULT_CONTROL_DURATION, 0, false, false, true));

        if (target.getWorld() instanceof ServerWorld world) {
            world.playSound(null, target.getBlockPos(),
                    ModSounds.POSSESSION, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    /**
     * OPTIMIZATION: Replaces a broad 40-block getEntitiesByClass scan filtered by tag
     * (every tick) with direct iteration over only the entities we know are possessed.
     */
    private static void tickUltimate(ServerPlayerEntity player, PsychicState state) {
        if (state.possessed.isEmpty()) return;

        ServerWorld playerWorld = player.getServerWorld();

        Iterator<PossessedEntry> it = state.possessed.values().iterator();
        while (it.hasNext()) {
            PossessedEntry entry = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                le.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.POSSESSED));
                it.remove();
                continue;
            }

            Vec3d cursorPos = getCursorTarget(player, playerWorld);
            Vec3d dir = cursorPos.subtract(le.getEyePos());
            double dist = dir.length();
            if (dist > 0.0001) dir = dir.normalize();

            forceLook(le, dir, ULT_LOOK_STRENGTH);
            applyMovement(le, dir, dist, ULT_MOB_SPEED, ULT_PLAYER_ACCEL, ULT_PLAYER_MAX_SPEED, ULT_STOP_DISTANCE);

            le.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 2, true, false, false));

            PsychicControlAuraPayload aura = new PsychicControlAuraPayload(le.getId());
            PlayerLookup.tracking(w, le.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, aura));

            handleControlledAttacks(w, le, entry);
        }
    }

    private static Vec3d getCursorTarget(ServerPlayerEntity player, ServerWorld world) {
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(ULT_RANGE));

        var hit = world.raycast(new RaycastContext(
                origin, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        return (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) ? hit.getPos() : end;
    }

    private static void handleControlledAttacks(ServerWorld world, LivingEntity entity, PossessedEntry entry) {
        if (!entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)) return;

        // OPTIMIZATION: attack cooldown stored in PossessedEntry, replacing ATTACK_CD_TAG on entity
        if (entry.attackCd > 0) {
            entry.attackCd--;
            // Still face nearest target even while on cooldown
            LivingEntity target = findNearestTarget(world, entity);
            if (target != null && entity.canSee(target)) {
                forceLook(entity, target.getEyePos().subtract(entity.getEyePos()).normalize(), ULT_LOOK_STRENGTH);
            }
            return;
        }

        LivingEntity target = findNearestTarget(world, entity);
        if (target == null || !entity.canSee(target)) return;

        forceLook(entity, target.getEyePos().subtract(entity.getEyePos()).normalize(), ULT_LOOK_STRENGTH);

        if (entity instanceof MobEntity mob) {
            if (mob.tryAttack(target)) entry.attackCd = ATTACK_COOLDOWN;
        } else if (entity instanceof ServerPlayerEntity controlledPlayer) {
            controlledPlayer.swingHand(Hand.MAIN_HAND, true);
            controlledPlayer.attack(target);
            entry.attackCd = ATTACK_COOLDOWN;
        }
    }

    private static LivingEntity findNearestTarget(ServerWorld world, LivingEntity attacker) {
        LivingEntity closest      = null;
        double       closestDistSq = 3.0 * 3.0;

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                attacker.getBoundingBox().expand(3.0),
                en -> en.isAlive() && en != attacker && attacker.canSee(en))) {

            double dist = attacker.squaredDistanceTo(e);
            if (dist < closestDistSq) {
                closestDistSq = dist;
                closest = e;
            }
        }
        return closest;
    }

    /* ============================================================
       MOVEMENT AND LOOK  (made static — no instance state needed)
       ============================================================ */

    private static void applyMovement(LivingEntity entity, Vec3d dir, double distance,
                                      double mobSpeed, double playerAccel, double playerMaxSpeed,
                                      double stopDistance) {
        Vec3d velocity = entity.getVelocity();

        if (entity instanceof MobEntity mob) {
            mob.getNavigation().stop();

            if (distance > stopDistance) {
                Vec3d flatDir = new Vec3d(dir.x, 0, dir.z);
                if (flatDir.lengthSquared() > 0.0001) flatDir = flatDir.normalize();
                entity.setVelocity(flatDir.x * mobSpeed, velocity.y, flatDir.z * mobSpeed);
                entity.velocityModified = true;

                if (entity.getWorld() instanceof ServerWorld sw) {
                    sw.getChunkManager().sendToNearbyPlayers(entity, new EntityPositionS2CPacket(entity));
                }
            } else {
                entity.setVelocity(velocity.x * 0.4, velocity.y, velocity.z * 0.4);
                entity.velocityModified = true;
            }

        } else if (entity instanceof ServerPlayerEntity) {
            if (distance > stopDistance) {
                Vec3d flatDir = new Vec3d(dir.x, 0, dir.z);
                if (flatDir.lengthSquared() > 0.0001) flatDir = flatDir.normalize();

                Vec3d newVel = velocity.add(flatDir.multiply(playerAccel));
                if (newVel.horizontalLength() > playerMaxSpeed) {
                    newVel = new Vec3d(newVel.x, 0, newVel.z)
                            .normalize().multiply(playerMaxSpeed)
                            .add(0, velocity.y, 0);
                }
                entity.setVelocity(newVel.x, velocity.y, newVel.z);
                entity.velocityModified = true;
            } else {
                entity.setVelocity(velocity.x * 0.4, velocity.y, velocity.z * 0.4);
                entity.velocityModified = true;
            }
        }
    }

    private static void forceLook(LivingEntity entity, Vec3d dir, float strength) {
        if (dir.lengthSquared() < 0.0001) return;

        float  targetYaw   = (float)(Math.toDegrees(MathHelper.atan2(dir.z, dir.x))) - 90f;
        double horizontal  = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float  targetPitch = (float)(-Math.toDegrees(MathHelper.atan2(dir.y, horizontal)));

        if (entity instanceof ServerPlayerEntity sp) {
            float yawDiff   = MathHelper.wrapDegrees(targetYaw - sp.getYaw());
            float pitchDiff = targetPitch - sp.getPitch();

            sp.networkHandler.sendPacket(new PlayerPositionLookS2CPacket(
                    0, 0, 0,
                    yawDiff   * strength,
                    pitchDiff * strength,
                    Set.of(PositionFlag.X, PositionFlag.Y, PositionFlag.Z,
                            PositionFlag.Y_ROT, PositionFlag.X_ROT),
                    0
            ));
        } else {
            entity.setYaw(targetYaw);
            entity.setHeadYaw(targetYaw);
            entity.setPitch(targetPitch);
            entity.setBodyYaw(targetYaw);
            // Rotation synced to clients via EntityPositionS2CPacket in applyMovement
        }
    }

    /* ============================================================
       BEAM HELPERS
       ============================================================ */

    private static void sendJaggedBeam(ServerWorld world, ServerPlayerEntity player,
                                       Vec3d from, Vec3d to, int segments, float offset) {
        PsychicSpikeBeamPayload payload = new PsychicSpikeBeamPayload(
                from.x, from.y, from.z, to.x, to.y, to.z, segments, offset);
        Vec3d mid = from.add(to).multiply(0.5);
        // send to player and nearby viewers
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    private static LivingEntity getEntityOnBeam(ServerWorld world, ServerPlayerEntity player,
                                                Vec3d origin, Vec3d end) {
        LivingEntity closest      = null;
        double       closestDistSq = Double.MAX_VALUE;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);
            if (hit.isPresent()) {
                double dist = origin.squaredDistanceTo(hit.get());
                if (dist < closestDistSq) {
                    closestDistSq = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private static double distanceToLine(Vec3d lineStart, Vec3d lineEnd, Vec3d point) {
        Vec3d  line = lineEnd.subtract(lineStart);
        double len  = line.length();
        if (len < 0.0001) return point.distanceTo(lineStart);
        double t = MathHelper.clamp(
                point.subtract(lineStart).dotProduct(line) / (len * len), 0, 1);
        return point.distanceTo(lineStart.add(line.multiply(t)));
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

    @Override public String getName() { return Text.translatable("power.loopypowers.psychic.name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.psychic.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.psychic.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.psychic.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 29_000; }
    @Override public long getUltimateCooldownMs()  { return 560_000; }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.psychic.description.overview").getString();
    }

    @Override
    public String getPassiveName() {
        return Text.translatable("power.loopypowers.psychic.passive_name").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.psychic.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.psychic.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.psychic.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.psychic.description.ultimate").getString();
    }
}
