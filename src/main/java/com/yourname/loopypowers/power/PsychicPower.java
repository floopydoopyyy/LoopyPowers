package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.PuppetryEntity;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import com.yourname.loopypowers.entity.CompelEntity;
import com.yourname.loopypowers.entity.ModEntities;

import java.util.Iterator;

public class PsychicPower implements Power {

    /* ============================================================
       TAGS N CONSTANTS
       ============================================================ */

    private static final String COMPEL_TAG      = "psy_compel_";
    private static final String SPIKE_TAG       = "psy_spike_";
    private static final String ULT_CONTROL_TAG = "psy_ult_ctrl_";
    private static final String ATTACK_CD_TAG   = "psy_atk_cd_";
    private static final String LEECH_CD_TAG = "psy_leech_cd_";

    // Passive tuning
    private static final float LEECH_HEAL    = 1.0f;
    private static final long  LEECH_CDR_MS  = 500;
    private static final int LEECH_INTERNAL_CD = 10; //time between heals

    // Compel tuning
    private static final int    COMPEL_DURATION         = 70;
    private static final double COMPEL_SCAN_RADIUS      = 20.0;
    private static final double COMPEL_STOP_DISTANCE    = 2.3;
    private static final double COMPEL_MOB_SPEED        = 0.18;
    private static final double COMPEL_PLAYER_ACCEL     = 0.14;
    private static final double COMPEL_PLAYER_MAX_SPEED = 0.40;
    private static final float  COMPEL_LOOK_STRENGTH    = 0.25f;
    private static final double COMPEL_LOOK_MIN_DIST    = 2.5;
    private static final double COMPEL_PROJECTILE_SPEED = 1.3;

    // Spike tuning
    private static final double SPIKE_BEAM_RANGE       = 18.0;
    private static final double SPIKE_CHAIN_RADIUS     = 2.0;
    private static final int    SPIKE_CHAIN_COUNT      = 8;
    private static final int    SPIKE_STUN_DURATION    = 30;
    private static final int    SPIKE_SLOW_DURATION    = 100;
    private static final int    SPIKE_STUN_AMPLIFIER   = 4;
    private static final int    SPIKE_SLOW_AMPLIFIER   = 1;
    private static final int    SPIKE_FATIGUE_DURATION = 120;
    private static final int    SPIKE_FATIGUE_AMPLIFIER = 0;
    private static final int    SPIKE_JAGGED_SEGMENTS  = 18;
    private static final int    SPIKE_CHAIN_SEGMENTS   = 10;
    private static final double SPIKE_JAGGED_OFFSET    = 0.55;

    // Ultimate tuning
    private static final double ULT_RANGE            = 40.0;
    private static final int    ULT_CONTROL_DURATION = 180;
    private static final double ULT_PLAYER_ACCEL     = 0.30;
    private static final double ULT_PLAYER_MAX_SPEED = 0.65;
    private static final double ULT_MOB_SPEED        = 0.36;
    private static final float  ULT_LOOK_STRENGTH    = 0.4f;
    private static final double ULT_STOP_DISTANCE    = 1.5;
    private static final int    ATTACK_COOLDOWN      = 25;

    // Egg!!!
    private static final double COMPEL_CHAT_CHANCE = 0.05; // percentage chance
    private static final java.util.List<String> STUPID_MESSAGES = java.util.List.of(
            "I think I'll use my credit card.",
            "I came to goon!",
            "do u guys like Radiohead?",
            "I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG I LISTEN TO ALEXG.",
            "you know what 6 7 backwards spells? efok. because i dont give e fok until ive had my coffee",
            "i really need a wee",
            "hop on MARVEL RIVALS?",
            "my tummy hurt :(",
            "hello everyone my name is welcome",
            "no one is illegal on stolen land BTW", // this ones not stupid this one is BASED
            "morp",
            "hello everybody my name is welcome",
            "throw me into the wolves, and i'll come back pregnant",
            "JOIN THE REBELLION"
    );

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {}

    @Override
    public void onTick(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        tickTag(player, LEECH_CD_TAG);

        // Tick marks on all nearby entities
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(40),
                LivingEntity::isAlive)) {
        }

        handleCompel(player);
        handleSpike(player);
        handleUltimate(player);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // dodge if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        // internal cooldown check
        if (hasTag(attacker, LEECH_CD_TAG)) return;

        if (hasTag(target, COMPEL_TAG)
                || hasTag(target, SPIKE_TAG)
                || hasTag(target, ULT_CONTROL_TAG)) {

            ServerWorld world = attacker.getServerWorld();

            // heal
            attacker.heal(LEECH_HEAL);

            //particles
            spawnLeechParticles(world, attacker, target);

            // sound
            world.playSound(null, attacker.getBlockPos(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                    attacker.getSoundCategory(), 0.6f, 1.4f);

            // reduce ability cooldowns
            PowerManager.reduceAllCooldowns(attacker, LEECH_CDR_MS);

            // put on cooldown
            removeTagPrefix(attacker, LEECH_CD_TAG);
            attacker.getCommandTags().add(LEECH_CD_TAG + LEECH_INTERNAL_CD);
        }
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void spawnLeechParticles(ServerWorld world, LivingEntity attacker, LivingEntity target) {

        // Use body positions
        Vec3d from = new Vec3d(target.getX(), target.getBodyY(0.7), target.getZ());
        Vec3d to   = new Vec3d(attacker.getX(), attacker.getBodyY(0.7), attacker.getZ());

        Vec3d direction = to.subtract(from);
        Vec3d step = direction.multiply(1.0 / 10);

        Vec3d current = from;

        for (int i = 0; i < 10; i++) {

            // Slight downward curve
            double curve = Math.sin(i / 10.0 * Math.PI) * 0.15;

            world.spawnParticles(
                    new net.minecraft.particle.DustParticleEffect(
                            new Vec3d(0.8, 0.0, 0.9).toVector3f(), 0.8f),
                    current.x, current.y - curve, current.z,
                    2, 0.05, 0.05, 0.05, 0.0);

            world.spawnParticles(
                    new net.minecraft.particle.DustParticleEffect(
                            new Vec3d(1.0, 0.4, 0.9).toVector3f(), 0.5f),
                    current.x, current.y - curve, current.z,
                    1, 0.02, 0.02, 0.02, 0.0);

            current = current.add(step);
        }

        // kept below eye level
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(1.0, 0.2, 0.8).toVector3f(), 1.0f),
                attacker.getX(), attacker.getBodyY(0.6), attacker.getZ(),
                10, 0.3, 0.4, 0.3, 0.02);
    }

    /* ============================================================
       PRIMARY
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

    public static void applyCompel(LivingEntity target) {
        removeTagPrefix(target, COMPEL_TAG);
        target.getCommandTags().add(COMPEL_TAG + COMPEL_DURATION);
        target.addStatusEffect(new StatusEffectInstance(ModEffects.COMPELLED, COMPEL_DURATION, 0, false, false, true));

        // --- EGG ---
        if (target instanceof ServerPlayerEntity player && player.getServer() != null) {
            // roll
            if (player.getRandom().nextDouble() < COMPEL_CHAT_CHANCE) {
                // pick a random message
                String msg = STUPID_MESSAGES.get(player.getRandom().nextInt(STUPID_MESSAGES.size()));

                // send message
                net.minecraft.text.Text chatText = net.minecraft.text.Text.literal(
                        "<" + player.getName().getString() + "> " + msg
                );

                // broadcast to server
                player.getServer().getPlayerManager().broadcast(chatText, false);
            }
        }
    }

    private void handleCompel(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(COMPEL_SCAN_RADIUS),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            boolean isCompelled = tickTag(e, COMPEL_TAG);
            ControlPair control = resolveControlTargets(player, world, e, isCompelled);

            if (!control.isCompelled) continue;

            Vec3d dir = control.target.getEyePos().subtract(control.controlled.getEyePos());
            double distance = dir.length();
            if (distance > 0.0001) dir = dir.normalize();

            if (distance > COMPEL_LOOK_MIN_DIST) {
                forceLook(control.controlled, dir, COMPEL_LOOK_STRENGTH);
            }
            applyMovement(control.controlled, dir, distance,
                    COMPEL_MOB_SPEED, COMPEL_PLAYER_ACCEL, COMPEL_PLAYER_MAX_SPEED, COMPEL_STOP_DISTANCE);
            applyControlEffects(control.controlled);
            spawnCompelParticles(world, control.controlled);
        }
    }

    private void applyControlEffects(LivingEntity entity) {
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 1, true, false, false
        ));
    }

    private void spawnCompelParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.9, 0.2, 0.6).toVector3f(), 0.7f),
                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                2, 0.2, 0.3, 0.2, 0.01);
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(1.0, 0.6, 0.9).toVector3f(), 0.5f),
                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                1, 0.3, 0.4, 0.3, 0.005);
    }

    // DEBUG control pair resolution
    private static class ControlPair {
        final LivingEntity controlled;
        final LivingEntity target;
        final boolean isCompelled;

        ControlPair(LivingEntity controlled, LivingEntity target, boolean isCompelled) {
            this.controlled = controlled;
            this.target = target;
            this.isCompelled = isCompelled;
        }
    }

    private ControlPair resolveControlTargets(ServerPlayerEntity player, ServerWorld world,
                                              LivingEntity e, boolean isCompelled) {
        LivingEntity controlled = e;
        LivingEntity target = player;

        if (player.getCommandTags().contains("psy_debug")) {
            LivingEntity nearest = world.getClosestEntity(
                    LivingEntity.class,
                    net.minecraft.entity.ai.TargetPredicate.DEFAULT,
                    player,
                    player.getX(), player.getY(), player.getZ(),
                    player.getBoundingBox().expand(20)
            );
            if (nearest != null && nearest != player) {
                controlled = player;
                target = nearest;
                isCompelled = true;
            }
        }

        return new ControlPair(controlled, target, isCompelled);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(SPIKE_BEAM_RANGE));

        // Block raycast to find where beam terminates
        var blockHit = world.raycast(new net.minecraft.world.RaycastContext(
                origin, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        spawnJaggedBeam(world, origin, end, SPIKE_JAGGED_SEGMENTS, SPIKE_JAGGED_OFFSET);

        LivingEntity primaryTarget = getEntityOnBeam(world, player, origin, end);

        // Collect chain targets — entities near the beam line even if not directly hit
        java.util.List<LivingEntity> chainTargets = new java.util.ArrayList<>();
        Vec3d finalEnd = end;

        world.getEntitiesByClass(LivingEntity.class,
                        new net.minecraft.util.math.Box(origin, finalEnd).expand(SPIKE_CHAIN_RADIUS),
                        e -> e.isAlive() && e != player && e != primaryTarget
                ).stream()
                .filter(e -> distanceToLine(origin, finalEnd, e.getEyePos()) < SPIKE_CHAIN_RADIUS)
                .sorted(java.util.Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                .limit(SPIKE_CHAIN_COUNT)
                .forEach(chainTargets::add);

        if (primaryTarget != null) {
            applySpike(primaryTarget);
            spawnSpikeImpactParticles(world, primaryTarget);
        }

        // Chain beams to nearby targets
        Vec3d chainOrigin = (primaryTarget != null) ? primaryTarget.getEyePos() : end;
        for (LivingEntity chain : chainTargets) {
            spawnJaggedBeam(world, chainOrigin, chain.getEyePos(),
                    SPIKE_CHAIN_SEGMENTS, SPIKE_JAGGED_OFFSET * 0.7);
            applySpike(chain);
            spawnSpikeImpactParticles(world, chain);
            chainOrigin = chain.getEyePos(); // chain links sequentially
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                player.getSoundCategory(), 0.8f, 0.7f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT,
                player.getSoundCategory(), 0.3f, 1.8f);
    }

    public static void applySpike(LivingEntity target) {
        removeTagPrefix(target, SPIKE_TAG);
        target.getCommandTags().add(SPIKE_TAG + (SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION));

        // Stun
        target.addStatusEffect(new StatusEffectInstance(
                ModEffects.STUN, SPIKE_STUN_DURATION, SPIKE_STUN_AMPLIFIER,
                false, true, true));

        // lingering stun
        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, SPIKE_STUN_DURATION + SPIKE_SLOW_DURATION, SPIKE_SLOW_AMPLIFIER,
                false, false, true));

        target.addStatusEffect(new StatusEffectInstance(
                StatusEffects.MINING_FATIGUE, SPIKE_FATIGUE_DURATION, SPIKE_FATIGUE_AMPLIFIER,
                false, false, true));
    }

    private void handleSpike(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(SPIKE_BEAM_RANGE + 5),
                LivingEntity::isAlive)) {

            boolean active = tickTag(e, SPIKE_TAG);
            if (active) spawnSpikeAuraParticles(world, e);
        }
    }

    private void spawnSpikeImpactParticles(ServerWorld world, LivingEntity entity) {
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.5, 0.0, 0.6).toVector3f(), 1.0f),
                entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                20, 0.4, 0.5, 0.4, 0.03);
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.9, 0.2, 0.6).toVector3f(), 0.8f),
                entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                12, 0.3, 0.4, 0.3, 0.02);
    }

    private void spawnSpikeAuraParticles(ServerWorld world, LivingEntity entity) {
        // Per-tick aura while spiked
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.9, 0.2, 0.6).toVector3f(), 0.6f),
                entity.getX(), entity.getBodyY(0.7), entity.getZ(),
                2, 0.25, 0.3, 0.25, 0.01);
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.5, 0.0, 0.6).toVector3f(), 0.5f),
                entity.getX(), entity.getBodyY(0.4), entity.getZ(),
                1, 0.2, 0.2, 0.2, 0.005);
    }

    /* ============================================================
       ULTIMATE
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

    private void handleUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(ULT_RANGE),
                e -> e.isAlive() && hasTag(e, ULT_CONTROL_TAG))) {

            if (e == player) continue;

            boolean active = tickTag(e, ULT_CONTROL_TAG);
            if (!active) continue;

            Vec3d cursorPos = getCursorTarget(player, world);
            Vec3d dir = cursorPos.subtract(e.getEyePos());
            double dist = dir.length();
            if (dist > 0.0001) dir = dir.normalize();

            forceLook(e, dir, ULT_LOOK_STRENGTH);
            applyMovement(e, dir, dist,
                    ULT_MOB_SPEED, ULT_PLAYER_ACCEL, ULT_PLAYER_MAX_SPEED, ULT_STOP_DISTANCE);

            e.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 2, true, false, false));

            spawnControlParticles(world, e);
            handleControlledAttacks(world, e);
        }
    }

    private Vec3d getCursorTarget(ServerPlayerEntity player, ServerWorld world) {
        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(ULT_RANGE));

        var hit = world.raycast(new net.minecraft.world.RaycastContext(
                origin, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));

        return (hit.getType() != net.minecraft.util.hit.HitResult.Type.MISS)
                ? hit.getPos()
                : end;
    }

    private void handleControlledAttacks(ServerWorld world, LivingEntity entity) {
        if (!entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)) return;

        boolean onCooldown = tickTag(entity, ATTACK_CD_TAG);

        LivingEntity target = findNearestTarget(world, entity, 3.0);
        if (target == null) return;

        // entity must have LOS
        if (!entity.canSee(target)) return;

        // Face target
        Vec3d dir = target.getEyePos().subtract(entity.getEyePos()).normalize();
        forceLook(entity, dir, ULT_LOOK_STRENGTH);

        if (onCooldown) return;

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            if (mob.tryAttack(target)) applyAttackCooldown(entity);
        } else if (entity instanceof ServerPlayerEntity player) {
            player.swingHand(Hand.MAIN_HAND, true);
            player.attack(target);
            applyAttackCooldown(entity);
        }
    }

    private LivingEntity findNearestTarget(ServerWorld world, LivingEntity attacker, double range) {
        LivingEntity closest = null;
        double closestDist = range * range;

        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                attacker.getBoundingBox().expand(range),
                en -> en.isAlive() && en != attacker && attacker.canSee(en))) { // must also see attacker

            double dist = attacker.squaredDistanceTo(e);
            if (dist < closestDist) {
                closestDist = dist;
                closest = e;
            }
        }
        return closest;
    }

    private void applyAttackCooldown(LivingEntity e) {
        removeTagPrefix(e, ATTACK_CD_TAG);
        e.getCommandTags().add(ATTACK_CD_TAG + ATTACK_COOLDOWN);
    }

    public static void applyUltimateControl(LivingEntity target) {

        removeTagPrefix(target, ULT_CONTROL_TAG);
        target.getCommandTags().add(ULT_CONTROL_TAG + ULT_CONTROL_DURATION);
        target.addStatusEffect(new StatusEffectInstance(ModEffects.POSSESSED, ULT_CONTROL_DURATION, 0, false, false, true));

        //  sound to nearby players
        if (target.getWorld() instanceof ServerWorld world) {
            world.playSound(
                    null,
                    target.getBlockPos(),
                    ModSounds.POSSESSION,
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0f,   // volume
                    1.0f    // pitch
            );
        }
    }

    private void spawnControlParticles(ServerWorld world, LivingEntity entity) {
        // Main pink aura
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(1.0, 0.2, 0.8).toVector3f(), 0.9f),
                entity.getX(), entity.getBodyY(0.7), entity.getZ(),
                4, 0.35, 0.45, 0.35, 0.02);
        // Soft secondary glow
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(1.0, 0.6, 0.9).toVector3f(), 0.6f),
                entity.getX(), entity.getBodyY(0.5), entity.getZ(),
                2, 0.3, 0.3, 0.3, 0.01);
        // the least
        world.spawnParticles(
                new net.minecraft.particle.DustParticleEffect(
                        new Vec3d(0.7, 0.0, 1.0).toVector3f(), 1.1f),
                entity.getX(), entity.getBodyY(0.6), entity.getZ(),
                3, 0.3, 0.4, 0.3, 0.02);
    }

    /* ============================================================
       MOVEMENT AND LOOK
       ============================================================ */

    /**
     * Shared movement logic used by both Compel and Ultimate.
     * Parameters control how it works
     */
    private void applyMovement(LivingEntity entity, Vec3d dir, double distance,
                               double mobSpeed, double playerAccel, double playerMaxSpeed,
                               double stopDistance) {
        Vec3d velocity = entity.getVelocity();

        if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.getNavigation().stop();

            if (distance > stopDistance) {
                Vec3d flatDir = new Vec3d(dir.x, 0, dir.z);
                if (flatDir.lengthSquared() > 0.0001) flatDir = flatDir.normalize();

                entity.setVelocity(flatDir.x * mobSpeed, velocity.y, flatDir.z * mobSpeed);
                entity.velocityModified = true;

                if (entity.getWorld() instanceof ServerWorld serverWorld) {
                    serverWorld.getChunkManager().sendToNearbyPlayers(entity,
                            new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(entity));
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

    /**
     * Shared look-forcing logic used by compel and ult.
     * strength controls how quickly rotation is pulled (0-1).
     */
    private void forceLook(LivingEntity entity, Vec3d dir, float strength) {
        if (dir.lengthSquared() < 0.0001) return;

        float targetYaw = (float)(Math.toDegrees(MathHelper.atan2(dir.z, dir.x))) - 90f;
        double horizontal = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float targetPitch = (float)(-Math.toDegrees(MathHelper.atan2(dir.y, horizontal)));

        if (entity instanceof ServerPlayerEntity serverPlayer) {
            float yawDiff   = MathHelper.wrapDegrees(targetYaw - serverPlayer.getYaw());
            float pitchDiff = targetPitch - serverPlayer.getPitch();

            serverPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(
                            0, 0, 0,
                            yawDiff   * strength,
                            pitchDiff * strength,
                            java.util.Set.of(
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Z,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT
                            ),
                            0
                    )
            );
        } else {
            entity.setYaw(targetYaw);
            entity.setHeadYaw(targetYaw);
            entity.setPitch(targetPitch);
            entity.setBodyYaw(targetYaw);
            // Rotation synced to clients via EntityPositionS2CPacket in applyMovement
        }
    }

    /* ============================================================
       SHARED BEAM HELPERS
       ============================================================ */

    private void spawnJaggedBeam(ServerWorld world, Vec3d from, Vec3d to, int segments, double offset) {
        Vec3d step    = to.subtract(from).multiply(1.0 / segments);
        Vec3d beamDir = to.subtract(from).normalize();
        Vec3d perp    = Math.abs(beamDir.y) < 0.9
                ? new Vec3d(-beamDir.z, 0, beamDir.x).normalize()
                : new Vec3d(1, 0, 0);
        Vec3d perp2 = beamDir.crossProduct(perp).normalize();

        Vec3d current = from;
        java.util.Random rng = new java.util.Random(from.hashCode()); // stable seed = consistent look

        for (int i = 0; i < segments; i++) {
            Vec3d next = current.add(step);

            double jag  = (i % 2 == 0 ? 1 : -1) * offset * (0.5 + rng.nextDouble() * 0.5);
            double jag2 = (i % 3 == 0 ? 1 : -1) * offset * 0.4 * rng.nextDouble();
            Vec3d jaggedNext = next.add(perp.multiply(jag)).add(perp2.multiply(jag2));

            for (int j = 0; j <= 4; j++) {
                double t   = j / 4.0;
                Vec3d pos  = current.lerp(jaggedNext, t);
                world.spawnParticles(
                        new net.minecraft.particle.DustParticleEffect(
                                new Vec3d(0.9, 0.2, 0.6).toVector3f(), 0.6f),
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
                world.spawnParticles(
                        new net.minecraft.particle.DustParticleEffect(
                                new Vec3d(1.0, 0.6, 0.9).toVector3f(), 0.4f),
                        pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
            }

            current = jaggedNext;
        }
    }

    private LivingEntity getEntityOnBeam(ServerWorld world, ServerPlayerEntity player,
                                         Vec3d origin, Vec3d end) {
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);
            if (hit.isPresent()) {
                double dist = origin.squaredDistanceTo(hit.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    private double distanceToLine(Vec3d lineStart, Vec3d lineEnd, Vec3d point) {
        Vec3d line = lineEnd.subtract(lineStart);
        double len = line.length();
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
    @Override public String getSecondaryName() { return "Mind Spike"; }
    @Override public String getUltimateName()  { return "Puppetry"; }

    @Override public long getPrimaryCooldownMs()   { return 4000; }
    @Override public long getSecondaryCooldownMs() { return 6000; }
    @Override public long getUltimateCooldownMs()  { return 6000; }

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
                " Controlled targets are forced to walk and look towards your crosshair and will automatically attack the closest entity, if they are able to attack, this had a range limit and entities will not walk towards the cursor if too far." +
                " Controlled players will also have constant mining fatigue, making it hider to mine and making them hit slower, forced attacks when controlled will not account for this.";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static boolean hasTag(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Decrements a timer tag by 1 tick. Removes it if expired.
     * Returns true if the tag is still active after this tick.
     */
    private static boolean tickTag(LivingEntity entity, String prefix) {
        Iterator<String> it = entity.getCommandTags().iterator();
        String newTag = null;
        boolean active = false;

        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                try {
                    int ticks = Integer.parseInt(tag.substring(prefix.length())) - 1;
                    it.remove();
                    if (ticks > 0) {
                        newTag = prefix + ticks;
                        active = true;
                    }
                } catch (NumberFormatException ignored) {
                    it.remove();
                }
                break;
            }
        }

        if (newTag != null) entity.getCommandTags().add(newTag);
        return active;
    }

    private static void removeTagPrefix(Entity e, String prefix) {
        Iterator<String> it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) it.remove();
        }
    }
}