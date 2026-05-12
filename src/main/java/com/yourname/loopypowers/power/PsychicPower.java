package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.CompelEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.PuppetryEntity;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.AbilityPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

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
       PARTICLE CONSTANTS (CACHED)
       ============================================================ */

    // Leech (passive)
    private static final DustParticleEffect LEECH_DUST_MAIN  = new DustParticleEffect(new Vector3f(0.80f, 0.00f, 0.90f), 0.8f);
    private static final DustParticleEffect LEECH_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.40f, 0.90f), 0.5f);
    private static final DustParticleEffect LEECH_DUST_END   = new DustParticleEffect(new Vector3f(1.00f, 0.20f, 0.80f), 1.0f);

    // Compel (primary)
    private static final DustParticleEffect COMPEL_DUST_MAIN  = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.7f);
    private static final DustParticleEffect COMPEL_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.5f);

    // Spike (secondary) — impact, aura, beam
    private static final DustParticleEffect SPIKE_DUST_IMPACT_DARK  = new DustParticleEffect(new Vector3f(0.50f, 0.00f, 0.60f), 1.0f);
    private static final DustParticleEffect SPIKE_DUST_IMPACT_LIGHT = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.8f);
    private static final DustParticleEffect SPIKE_DUST_AURA_MAIN    = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleEffect SPIKE_DUST_AURA_LIGHT   = new DustParticleEffect(new Vector3f(0.50f, 0.00f, 0.60f), 0.5f);
    private static final DustParticleEffect SPIKE_BEAM_MAIN         = new DustParticleEffect(new Vector3f(0.90f, 0.20f, 0.60f), 0.6f);
    private static final DustParticleEffect SPIKE_BEAM_LIGHT        = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.4f);

    // Ultimate/Puppetry
    private static final DustParticleEffect ULT_DUST_MAIN  = new DustParticleEffect(new Vector3f(1.00f, 0.20f, 0.80f), 0.9f);
    private static final DustParticleEffect ULT_DUST_LIGHT = new DustParticleEffect(new Vector3f(1.00f, 0.60f, 0.90f), 0.6f);
    private static final DustParticleEffect ULT_DUST_DARK  = new DustParticleEffect(new Vector3f(0.70f, 0.00f, 1.00f), 1.1f);

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
        cleanupEntries(server, state.compelled.values(),  le -> le.removeStatusEffect(ModEffects.COMPELLED));
        cleanupEntries(server, state.spiked.values(),     le -> {
            le.removeStatusEffect(ModEffects.STUN);
            le.removeStatusEffect(StatusEffects.SLOWNESS);
            le.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        });
        cleanupEntries(server, state.possessed.values(),  le -> {
            le.removeStatusEffect(ModEffects.POSSESSED);
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

        ServerWorld world = attacker.getWorld();

        attacker.heal(LEECH_HEAL);
        spawnLeechParticles(world, attacker, target);
        world.playSound(null, attacker.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                attacker.getSoundCategory(), 0.6f, 1.4f);
        PowerManager.reduceAllCooldowns(attacker, LEECH_CDR_MS);

        state.leechCd = LEECH_INTERNAL_CD;
    }

    /* ============================================================
       PASSIVE — MIND SAP
       ============================================================ */

    private static void spawnLeechParticles(ServerWorld world, LivingEntity attacker, LivingEntity target) {
        Vec3d from    = new Vec3d(target.getX(),   target.getBodyY(0.7),   target.getZ());
        Vec3d to      = new Vec3d(attacker.getX(), attacker.getBodyY(0.7), attacker.getZ());
        Vec3d step    = to.subtract(from).multiply(1.0 / 10);
        Vec3d current = from;

        for (int i = 0; i < 10; i++) {
            double curve = Math.sin(i / 10.0 * Math.PI) * 0.15;
            world.spawnParticles(LEECH_DUST_MAIN,  current.x, current.y - curve, current.z, 2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(LEECH_DUST_LIGHT, current.x, current.y - curve, current.z, 1, 0.02, 0.02, 0.02, 0.0);
            current = current.add(step);
        }

        world.spawnParticles(LEECH_DUST_END,
                attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(),
                10, 0.3, 0.4, 0.3, 0.02);
    }

    /* ============================================================
       PRIMARY — COMPEL
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();

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
                ModEffects.COMPELLED, COMPEL_DURATION, 0, false, false, true));

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
            ServerWorld w = player.getWorld();
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
                spawnCompelParticles(w, player);
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
                le.removeStatusEffect(ModEffects.COMPELLED);
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
            spawnCompelParticles(w, le);
        }
    }

    private static void applyControlEffects(LivingEntity entity) {
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 1, true, false, false));
    }

    private static void spawnCompelParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(COMPEL_DUST_MAIN,
                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                2, 0.2, 0.3, 0.2, 0.01);
        world.spawnParticles(COMPEL_DUST_LIGHT,
                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                1, 0.3, 0.4, 0.3, 0.005);
    }

    /* ============================================================
       SECONDARY — SPIKE
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();

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

        spawnJaggedBeam(world, origin, end, SPIKE_JAGGED_SEGMENTS, SPIKE_JAGGED_OFFSET);

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
            spawnSpikeImpactParticles(world, primaryTarget);
        }

        Vec3d chainOrigin = (primaryTarget != null) ? primaryTarget.getEyePos() : end;
        for (LivingEntity chain : chainTargets) {
            spawnJaggedBeam(world, chainOrigin, chain.getEyePos(), SPIKE_CHAIN_SEGMENTS, SPIKE_JAGGED_OFFSET * 0.7);
            applySpike(state, chain);
            spawnSpikeImpactParticles(world, chain);
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
                ModEffects.STUN, SPIKE_STUN_DURATION, SPIKE_STUN_AMPLIFIER, false, false, true));
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

        long time = player.getWorld().getTime();

        Iterator<SpikedEntry> it = state.spiked.values().iterator();
        while (it.hasNext()) {
            SpikedEntry entry = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) { it.remove(); continue; }

            spawnSpikeAuraParticles(w, le);

            // Send a 15-tick subtle shake every 10 ticks to avoid network spam
            if (le instanceof ServerPlayerEntity targetPlayer && time % 10 == 0) {
                shake(targetPlayer, 15, 0.05f);
            }
        }
    }

    public static void shake(ServerPlayerEntity target, int ticks, float strength) {
        var buf = PacketByteBufs.create();
        buf.writeInt(ticks);
        buf.writeFloat(strength);
        ServerPlayNetworking.send(target, AbilityPackets.CAMERA_SHAKE, buf);
    }

    private static void spawnSpikeImpactParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(SPIKE_DUST_IMPACT_DARK,  entity.getX(), entity.getBodyY(0.5), entity.getZ(), 20, 0.4, 0.5, 0.4, 0.03);
        world.spawnParticles(SPIKE_DUST_IMPACT_LIGHT, entity.getX(), entity.getBodyY(0.5), entity.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
    }

    private static void spawnSpikeAuraParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(SPIKE_DUST_AURA_MAIN,  entity.getX(), entity.getBodyY(0.7), entity.getZ(), 2, 0.25, 0.30, 0.25, 0.01);
        world.spawnParticles(SPIKE_DUST_AURA_LIGHT, entity.getX(), entity.getBodyY(0.4), entity.getZ(), 1, 0.20, 0.20, 0.20, 0.005);
    }

    /* ============================================================
       ULTIMATE — PUPPETRY
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();

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
     *   PsychicPower.applyUltimateControl((ServerPlayerEntity) getOwner(), target)
     */
    public static void applyUltimateControl(ServerPlayerEntity caster, LivingEntity target) {
        PsychicState state = getState(caster);
        // Overwrite to reset timer if controlled again
        state.possessed.put(target.getUuid(),
                new PossessedEntry(target.getUuid(), target.getWorld().getRegistryKey()));

        target.addStatusEffect(new StatusEffectInstance(
                ModEffects.POSSESSED, ULT_CONTROL_DURATION, 0, false, false, true));

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

        ServerWorld playerWorld = player.getWorld();

        Iterator<PossessedEntry> it = state.possessed.values().iterator();
        while (it.hasNext()) {
            PossessedEntry entry = it.next();
            ServerWorld w = Objects.requireNonNull(player.getServer()).getWorld(entry.worldKey);
            if (w == null) { it.remove(); continue; }

            Entity ent = w.getEntity(entry.target);
            if (!(ent instanceof LivingEntity le) || !le.isAlive()) { it.remove(); continue; }

            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                le.removeStatusEffect(ModEffects.POSSESSED);
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

            spawnControlParticles(w, le);
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
            LivingEntity target = findNearestTarget(world, entity, 3.0);
            if (target != null && entity.canSee(target)) {
                forceLook(entity, target.getEyePos().subtract(entity.getEyePos()).normalize(), ULT_LOOK_STRENGTH);
            }
            return;
        }

        LivingEntity target = findNearestTarget(world, entity, 3.0);
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

    private static LivingEntity findNearestTarget(ServerWorld world, LivingEntity attacker, double range) {
        LivingEntity closest      = null;
        double       closestDistSq = range * range;

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                attacker.getBoundingBox().expand(range),
                en -> en.isAlive() && en != attacker && attacker.canSee(en))) {

            double dist = attacker.squaredDistanceTo(e);
            if (dist < closestDistSq) {
                closestDistSq = dist;
                closest = e;
            }
        }
        return closest;
    }

    private static void spawnControlParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(ULT_DUST_MAIN,  entity.getX(), entity.getBodyY(0.7), entity.getZ(), 4, 0.35, 0.45, 0.35, 0.02);
        world.spawnParticles(ULT_DUST_LIGHT, entity.getX(), entity.getBodyY(0.5), entity.getZ(), 2, 0.30, 0.30, 0.30, 0.01);
        world.spawnParticles(ULT_DUST_DARK,  entity.getX(), entity.getBodyY(0.6), entity.getZ(), 3, 0.30, 0.40, 0.30, 0.02);
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

    private static void spawnJaggedBeam(ServerWorld world, Vec3d from, Vec3d to, int segments, double offset) {
        Vec3d step    = to.subtract(from).multiply(1.0 / segments);
        Vec3d beamDir = to.subtract(from).normalize();
        Vec3d perp    = Math.abs(beamDir.y) < 0.9
                ? new Vec3d(-beamDir.z, 0, beamDir.x).normalize()
                : new Vec3d(1, 0, 0);
        Vec3d perp2 = beamDir.crossProduct(perp).normalize();

        Vec3d current = from;
        Random rng = new Random(from.hashCode()); // stable seed = consistent look per cast

        for (int i = 0; i < segments; i++) {
            Vec3d next = current.add(step);

            double jag  = (i % 2 == 0 ? 1 : -1) * offset * (0.5 + rng.nextDouble() * 0.5);
            double jag2 = (i % 3 == 0 ? 1 : -1) * offset * 0.4 * rng.nextDouble();
            Vec3d jaggedNext = next.add(perp.multiply(jag)).add(perp2.multiply(jag2));

            for (int j = 0; j <= 4; j++) {
                Vec3d pos = current.lerp(jaggedNext, j / 4.0);
                world.spawnParticles(SPIKE_BEAM_MAIN,  pos.x, pos.y, pos.z, 1, 0.00,  0.00,  0.00,  0);
                world.spawnParticles(SPIKE_BEAM_LIGHT, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
            }

            current = jaggedNext;
        }
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

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return "Psychic"; }
    @Override public String getPassiveName()   { return "Mind Sap"; }
    @Override public String getPrimaryName()   { return "Compel"; }
    @Override public String getSecondaryName() { return "Spike"; }
    @Override public String getUltimateName()  { return "Puppetry"; }

    @Override public long getPrimaryCooldownMs()   { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 29_000; }
    @Override public long getUltimateCooldownMs()  { return 560_000; }

    @Override
    public String getOverviewDescription() {
        return "Psychic is a control-focused power that disrupts enemy movement and actions rather than dealing high damage." +
                " Your abilities revolve around stunning and taking away player's movement." +
                " Your passive allows you to use abilities more often if used well.";
    }

    @Override
    public String getPassiveDescription() {
        return "Hitting enemies affected by any of your abilities heal you slightly and reduce all your cooldowns." +
                " This has a short internal cooldown, so spam hits aren't as effective.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Fire a projectile that compels an enemy to walk towards you, this projectile moves towards your cursor." +
                " Affected targets are forced to look and walk towards you, this had a range limit and will not walk towards you if not close enough." +
                " They are also inflicted with slowness and mining fatigue and will stop when too close." +
                " The projectile has a lifespan and will break if no one is hit within that time.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Fire a piercing hitscan beam that stuns nearby enemies briefly, effecting them with a stronger slowness initially, and then a weaker more prolonged slowness." +
                " The beam can chain to nearby enemies, so you can hit many targets if they're close.";
    }

    @Override
    public String getUltimateDescription() {
        return "Shoot a large, slow-moving projectile that moves towards your cursor. This is similar to compel but with a larger size and hitbox and a slower speed and shorter lifespan." +
                " On hit, the entity becomes controlled:" +
                " Controlled targets are forced to walk and look towards your crosshair and will automatically attack the closest entity (if they are able to attack). This had a range limit and entities will not walk towards the cursor if too far." +
                " Controlled players will also have constant mining fatigue, making it hider to mine and making them hit slower, forced attacks when controlled will not account for this.";
    }
}