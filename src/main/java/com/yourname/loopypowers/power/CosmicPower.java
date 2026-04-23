package com.yourname.loopypowers.power;

import com.yourname.loopypowers.entity.BlackHoleEntity;
import com.yourname.loopypowers.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmicPower implements Power {

    /* ============================================================
       TAGS / CONSTANTS
       ============================================================ */

    private static final String FATE_DMG_TAG = "cos_fate_dmg_";
    private static final String FATE_TIMER_TAG = "cos_fate_timer_";
    private static final String FATE_TIMER_CD_TAG = "cos_fate_timer_cd_";
    private static final String FATE_DETON_TAG = "cos_fate_deton_";

    // Shooting star tags
    private static final String STAR_LAUNCH_TAG = "cos_star_launch_"; // tracks arc phase
    private static final String STAR_CD_TAG = "cos_star_cd_";

    private final Map<UUID, Vec3d> starTargets = new HashMap<>();

    // Black hole tag on the player
    private static final String BLACKHOLE_TAG = "cos_blackhole_";

    // Passive
    private static final int FATE_TIMER_DEFAULT = 200;    // ticks before fate detonates on first application
    private static final int FATE_TIMER_MAX = 300;    // max fate timer (cap)
    private static final float FATE_DAMAGE_CAP = 40.0f;  // max damage fate can store
    private static final int FATE_DETONATE_TICKS = 40;     // ticks over which stored damage is applied on detonation
    private static final int FATE_DETONATE_INTERVAL = 5;     // apply a portion of damage every N tick
    private static final float MELEE_FATE_RATIO = 0.6f; // 60% stored
    private static final int MELEE_TIMER_ADD = 12; // time added to countdown
    private static final int MELEE_TIMER_CD = 10; // ticks between timer increases
    // NOTE: The melee damage multiplier is handled in loopypowers.
    // immunity tags
    private static final String FATE_IMMUNE_TAG = "cos_fate_immune_";
    private static final int FATE_IMMUNE_TICKS = 200; // time immune

    // primary
    private static final double RAY_RANGE = 30.0;
    private static final float RAY_DIRECT_DAMAGE = 2.0f;   // damage on impact
    private static final float RAY_FATE_STORE = 4.0f;   // damage added to storage
    private static final int RAY_TIMER_ADD = 20;     // time added

    // secondary
    private static final double STAR_SLAM_RADIUS = 4.0;   // explosion radius
    private static final float STAR_FATE_STORE = 6.0f;  // fate damage applied to nearby entities
    private static final float STAR_TIMER_REDUCTION = 0.60f; // percentage of timer removed on slam
    private static final int STAR_ARC_TICKS = 40;    // ticks to reach peak height

    // Black hole tuning
    private static final double BH_OUTER_RADIUS = 20.0;  // gentle pull radius
    private static final float BH_TIMER_REDUCTION = 0.02f; // fraction of timer removed per tick in inner ring
    // a lot of this is in blackholeentity instead

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Tick all fate tags on nearby entities and handle detonation
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(Math.max(BH_OUTER_RADIUS, RAY_RANGE) + 5),
                LivingEntity::isAlive)) {

            if (e == player) continue;

            tickFate(e, world);
            tickTag(e, FATE_IMMUNE_TAG);
        }

        handleBlackHole(player);
        handleShootingStar(player);

        // Tick player's own tags
        tickTag(player, BLACKHOLE_TAG);
        tickTag(player, STAR_CD_TAG);
        tickTag(player, FATE_TIMER_CD_TAG);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {

    }

    public static void applyMeleeFate(LivingEntity target, float damageDealt) {
        // this is pretty much the onhit() method, but this is used instead, as fate needs to account for crits in this case.
        float fatePortion = damageDealt * MELEE_FATE_RATIO;

        addFate(target, fatePortion, 0);

        int cd = getFateRaw(target, FATE_TIMER_CD_TAG);

        if (cd <= 0) {
            addFate(target, 0, MELEE_TIMER_ADD);

            removeTagPrefix(target, FATE_TIMER_CD_TAG);
            target.getCommandTags().add(FATE_TIMER_CD_TAG + MELEE_TIMER_CD);
        }
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    public static boolean applyingReducedDamage = false;

    public static boolean isApplyingReducedDamage() {
        return applyingReducedDamage;
    }

    private static void addFate(LivingEntity target, float fateDmg, int timerAdd) {

        if (hasTag(target, FATE_IMMUNE_TAG)) return; // do not apply if immune

        int currentDmgRaw = getFateRaw(target, FATE_DMG_TAG);
        int capRaw = Math.round(FATE_DAMAGE_CAP * 10);

        int added = Math.round(fateDmg * 10);
        int newDmgRaw = Math.min(currentDmgRaw + added, capRaw);

        boolean hitCapNow = currentDmgRaw < capRaw && newDmgRaw >= capRaw;
        boolean alreadyAtCap = currentDmgRaw >= capRaw && added > 0;

        removeTagPrefix(target, FATE_DMG_TAG);
        target.getCommandTags().add(FATE_DMG_TAG + newDmgRaw);

        // if cap hit, play fx
        if ((hitCapNow || alreadyAtCap) && target.getWorld() instanceof ServerWorld world) {
            spawnFateCapParticles(world, target);
            playFateCapSound(world, target);
        }

        // timer logic
        int currentTimer = getFateRaw(target, FATE_TIMER_TAG);
        int newTimer;

        if (currentTimer <= 0) {
            newTimer = FATE_TIMER_DEFAULT;
        } else {
            newTimer = Math.min(currentTimer + timerAdd, FATE_TIMER_MAX);
        }

        removeTagPrefix(target, FATE_TIMER_TAG);
        target.getCommandTags().add(FATE_TIMER_TAG + newTimer);
    }

    private void tickFate(LivingEntity entity, ServerWorld world) {
        boolean detonating = tickTag(entity, FATE_DETON_TAG);

        if (detonating) {
            int dmgRaw = getFateRaw(entity, FATE_DMG_TAG);

            if (dmgRaw > 0 && entity.age % FATE_DETONATE_INTERVAL == 0) {

                float totalDamage = dmgRaw / 10.0f;

                int ticks = FATE_DETONATE_TICKS / FATE_DETONATE_INTERVAL;
                float damagePerTick = totalDamage / ticks;

                entity.damage(
                        entity.getDamageSources().magic(), // or your custom source
                        damagePerTick
                );

                spawnDetonateParticles(world, entity);
            }

            // clear after boom
            if (!hasTag(entity, FATE_DETON_TAG)) {
                removeTagPrefix(entity, FATE_DMG_TAG);

                removeTagPrefix(entity, FATE_IMMUNE_TAG); // make them immune
                entity.getCommandTags().add(FATE_IMMUNE_TAG + FATE_IMMUNE_TICKS);
            }

            return;
        }

        // normal timer countdown
        int timer = getFateRaw(entity, FATE_TIMER_TAG);
        if (timer <= 0) return;

        timer--;
        removeTagPrefix(entity, FATE_TIMER_TAG);

        if (timer <= 0) {
            removeTagPrefix(entity, FATE_TIMER_TAG);

            entity.getCommandTags().add(FATE_DETON_TAG + FATE_DETONATE_TICKS);

            spawnDetonateStartParticles(world, entity);
        } else {
            entity.getCommandTags().add(FATE_TIMER_TAG + timer);
            spawnFateAuraParticles(world, entity, timer);
        }
    }

     static void reduceFateTimer(LivingEntity target, float reduction) {
        int current = getFateRaw(target, FATE_TIMER_TAG);
        if (current <= 0) return;
        int reduced = Math.max(0, current - Math.round(current * reduction));
        removeTagPrefix(target, FATE_TIMER_TAG);
        if (reduced > 0) target.getCommandTags().add(FATE_TIMER_TAG + reduced);
        // If reduced to 0, detonation triggered in next fatetick
    }

    private void spawnFateAuraParticles(ServerWorld world, LivingEntity entity, int timer) {
        // scales with damage stored
        int dmgRaw = getFateRaw(entity, FATE_DMG_TAG);

        // 1 star per half a heart
        int starCount = MathHelper.clamp(dmgRaw / 10, 0, 19);

        // try to distribute stars
        double radius = 0.8;
        long time = entity.getWorld().getTime();

        // Countdown star
        float timerFraction = (float) timer / FATE_TIMER_MAX;

        // Speed ramp up
        double speed = 0.05 + (1.0 - timerFraction) * 0.35;
        double angle = time * speed;

        // Slightly larger orbit
        double orbitRadius = radius + 0.35;

        // Trail length scales with speed
        int trailPoints = (int)(3 + speed * 10);

        for (int t = 0; t < trailPoints; t++) {

            double trailAngle = angle - (t * 0.15);

            double cx = entity.getX() + Math.cos(trailAngle) * orbitRadius;
            double cz = entity.getZ() + Math.sin(trailAngle) * orbitRadius;
            double cy = entity.getBodyY(0.55); // this is the height

            float size = 1.2f - (t * 0.08f); // fade out

            DustParticleEffect orange = new DustParticleEffect(
                    new Vector3f(1.0f, 0.5f, 0.1f),
                    Math.max(0.4f, size)
            );

            world.spawnParticles(
                    orange,
                    cx, cy, cz,
                    1,
                    0, 0, 0,
                    0
            );
        }

        // extra particles when close
        if (timerFraction < 0.3f) {
            int flameCount = (int)((0.3f - timerFraction) * 20);

            for (int i = 0; i < flameCount; i++) {

                double randAngle = world.random.nextDouble() * Math.PI * 2;
                double r = orbitRadius + 0.1 + world.random.nextDouble() * 0.2;

                double fx = entity.getX() + Math.cos(randAngle) * r;
                double fz = entity.getZ() + Math.sin(randAngle) * r;
                double fy = entity.getBodyY(0.55) + world.random.nextDouble() * 0.3;

                world.spawnParticles(
                        ParticleTypes.FLAME,
                        fx, fy, fz,
                        1,
                        0.01, 0.02, 0.01,
                        0.01
                );
            }
        }

        if (entity.age % 3 != 0) return;

        for (int i = 0; i < starCount; i++) {

            // Stable seed per entity
            long seed = entity.getId() * 341873128712L + i * 132897987541L;

            // Generate fixed random values
            double offsetX = ((seed >> 16) & 0xFF) / 255.0 * 2.0 - 1.0;
            double offsetY = ((seed >> 8)  & 0xFF) / 255.0;
            double offsetZ = ((seed)       & 0xFF) / 255.0 * 2.0 - 1.0;

            // Scale them nicely around the body
            double spreadXZ = 0.8;
            double spreadY  = 1.2;

            double x = entity.getX() + offsetX * spreadXZ;
            double y = entity.getBodyY(offsetY * spreadY);
            double z = entity.getZ() + offsetZ * spreadXZ;

            world.spawnParticles(
                    ParticleTypes.WHITE_ASH,
                    x, y, z,
                    1,
                    0.01, 0.02, 0.01,
                    0.001
            );
        }
    }

    private void spawnDetonateStartParticles(ServerWorld world, LivingEntity entity) {

        Vec3d pos = entity.getPos();

        // big flash
        world.spawnParticles(ParticleTypes.FLASH,
                pos.x, pos.y + 1, pos.z,
                3, 0.2, 0.2, 0.2, 0);

        // burst outward stars
        for (int i = 0; i < 25; i++) {
            double vx = (world.random.nextDouble() - 0.5) * 0.6;
            double vy = world.random.nextDouble() * 0.6;
            double vz = (world.random.nextDouble() - 0.5) * 0.6;

            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x, pos.y + 1, pos.z,
                    1, vx, vy, vz, 0.1);
        }

        world.playSound(null, entity.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                entity.getSoundCategory(), 0.8f, 0.6f);
    }

    private void spawnDetonateParticles(ServerWorld world, LivingEntity entity) {

        if (entity.age % 2 != 0) return;

        Vec3d pos = entity.getPos();

        // inward particles
        for (int i = 0; i < 6; i++) {

            double angle = world.getTime() * 0.3 + i;

            double radius = 0.8;

            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + 0.8;

            double dx = (pos.x - x) * 0.2;
            double dy = 0.02;
            double dz = (pos.z - z) * 0.2;

            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    x, y, z,
                    1,
                    dx, dy, dz,
                    0);
        }
    }

    private static void spawnFateCapParticles(ServerWorld world, LivingEntity entity) {

        Vec3d pos = entity.getPos().add(0, entity.getHeight() * 0.6, 0);

        for (int i = 0; i < 40; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double speed = 0.4 + world.random.nextDouble() * 0.6;

            double vx = Math.cos(angle) * speed;
            double vy = (world.random.nextDouble() - 0.3) * 0.6;
            double vz = Math.sin(angle) * speed;

            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z,
                    1,
                    vx, vy, vz,
                    0.05
            );
        }
    }

    private static void playFateCapSound(ServerWorld world, LivingEntity entity) {

        world.playSound(
                null,
                entity.getBlockPos(),
                SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, // probably should change this
                entity.getSoundCategory(),
                0.8f,
                1.8f
        );
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d origin = player.getEyePos();
        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d end    = origin.add(look.multiply(RAY_RANGE));

        // Block clip
        var blockHit = world.raycast(new net.minecraft.world.RaycastContext(
                origin, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));
        if (blockHit.getType() != net.minecraft.util.hit.HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        spawnCosmicRayParticles(world, origin, end);

        LivingEntity target = getEntityOnBeam(world, player, origin, end);

        // all entities touching beam
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                new net.minecraft.util.math.Box(origin, end).expand(1.0),
                en -> en.isAlive() && en != player)) {

            var hit = e.getBoundingBox().expand(0.3).raycast(origin, end);

            if (hit.isPresent()) {

                // Damage + fate
                e.damage(player.getDamageSources().magic(), RAY_DIRECT_DAMAGE);
                addFate(e, RAY_FATE_STORE, RAY_TIMER_ADD);

                // Impact particles per enemy hit
                Vec3d hitPos = hit.get();

                world.spawnParticles(ParticleTypes.FLASH,
                        hitPos.x, hitPos.y, hitPos.z,
                        1, 0.05, 0.05, 0.05, 0);

                world.spawnParticles(
                        ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z,
                        3,
                        0.2, 0.2, 0.2,
                        0.02
                );
            }
        }

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                player.getSoundCategory(), 0.6f, 1.6f);

        player.swingHand(Hand.MAIN_HAND, true);

        if (target != null) {
            Vec3d hitPos = target.getBoundingBox().getCenter();

            world.spawnParticles(ParticleTypes.FLASH,
                    hitPos.x, hitPos.y, hitPos.z,
                    2, 0.1, 0.1, 0.1, 0);

            for (int i = 0; i < 8; i++) {
                world.spawnParticles(
                        ParticleTypes.END_ROD,
                        hitPos.x, hitPos.y, hitPos.z,
                        1,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        world.random.nextDouble() * 0.3,
                        (world.random.nextDouble() - 0.5) * 0.4,
                        0.05
                );
            }
        }
    }

    private void spawnCosmicRayParticles(ServerWorld world, Vec3d from, Vec3d to) {

        Vec3d dir  = to.subtract(from);
        double len = dir.length();
        Vec3d step = dir.normalize().multiply(0.25);
        int count  = (int)(len / 0.25);

        Vec3d pos = from;

        for (int i = 0; i < count; i++) {

            double progress = (double)i / count;

            // slight offset
            double angle = i * 0.4;
            double spiral = 0.08;

            double ox = Math.cos(angle) * spiral;
            double oz = Math.sin(angle) * spiral;

            double x = pos.x + ox;
            double y = pos.y;
            double z = pos.z + oz;

            // core particles
            DustParticleEffect purple = new DustParticleEffect(
                    new Vector3f(0.4f, 0.0f, 0.6f),
                    1.1f
            );

            world.spawnParticles(purple, x, y, z, 1, 0, 0, 0, 0);

            // other
            if (i % 2 == 0) {
                world.spawnParticles(
                        ParticleTypes.SMOKE,
                        x, y, z,
                        1,
                        0.01, 0.01, 0.01,
                        0.01
                );
            }

            // star
            if (world.random.nextFloat() < 0.25f) {
                world.spawnParticles(
                        ParticleTypes.END_ROD,
                        x, y, z,
                        1,
                        0.02, 0.02, 0.02,
                        0.01
                );
            }

            pos = pos.add(step);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // STRAIGHT UP launch (no forward movement)
        player.setVelocity(0, 1.2, 0);
        player.velocityModified = true;

        removeTagPrefix(player, STAR_LAUNCH_TAG);
        player.getCommandTags().add(STAR_LAUNCH_TAG + STAR_ARC_TICKS);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
                player.getSoundCategory(), 0.9f, 0.8f);
    }

    private void handleShootingStar(ServerPlayerEntity player) {
        if (!hasTag(player, STAR_LAUNCH_TAG)) return;

        ServerWorld world = player.getServerWorld();

        spawnStarTrailParticles(world, player);

        Vec3d velocity = player.getVelocity();

        // ASCENDING
        if (velocity.y > 0) {
            Vec3d look = player.getRotationVec(1.0f);

            // small air control while rising
            player.addVelocity(look.x * 0.02, 0, look.z * 0.02);
            return;
        }

        // DESCENDING

        Vec3d look = player.getRotationVec(1.0f);

        // set velocity per tick
        player.setVelocity(
                look.x * 1.2,
                -1.5,
                look.z * 1.2
        );
        player.velocityModified = true;

        // SLAM DETECTION
        boolean hitGround = player.isOnGround() || player.verticalCollision;

        if (hitGround) {

            Vec3d slamPos = player.getPos();

            for (LivingEntity e : world.getEntitiesByClass(
                    LivingEntity.class,
                    player.getBoundingBox().expand(STAR_SLAM_RADIUS),
                    en -> en.isAlive() && en != player)) {

                if (e.getPos().distanceTo(slamPos) > STAR_SLAM_RADIUS) continue;

                e.damage(player.getDamageSources().magic(), 4.0f);
                addFate(e, STAR_FATE_STORE, 0);
                reduceFateTimer(e, STAR_TIMER_REDUCTION);
            }

            spawnSlamParticles(world, slamPos);

            world.playSound(null, player.getBlockPos(),
                    SoundEvents.ENTITY_GENERIC_EXPLODE,
                    player.getSoundCategory(), 1.0f, 0.8f);

            // stop movement cleanly
            player.setVelocity(0, 0, 0);
            player.velocityModified = true;

            removeTagPrefix(player, STAR_LAUNCH_TAG);
        }
    }

    private void spawnStarTrailParticles(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();

        for (int i = 0; i < 3; i++) {
            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    pos.x,
                    pos.y + 0.5,
                    pos.z,
                    1,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.05,
                    (world.random.nextDouble() - 0.5) * 0.2,
                    0.02
            );
        }
    }

    private void spawnSlamParticles(ServerWorld world, Vec3d pos) {

        for (int i = 0; i < 30; i++) {
            double angle = (Math.PI * 2) * i / 30;

            double vx = Math.cos(angle) * 0.4;
            double vz = Math.sin(angle) * 0.4;

            world.spawnParticles(
                    ParticleTypes.END_ROD,
                    pos.x, pos.y + 0.1, pos.z,
                    1,
                    vx, 0.1, vz,
                    0.05
            );
        }

        world.spawnParticles(
                ParticleTypes.FLASH,
                pos.x, pos.y + 0.5, pos.z,
                3,
                0.2, 0.2, 0.2,
                0
        );
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Spawn the black hole
        Vec3d spawnPos = player.getEyePos().add(player.getRotationVec(1.0f).multiply(2.0));

        BlackHoleEntity bh = new BlackHoleEntity(ModEntities.BLACK_HOLE_ENTITY, world);
        bh.setOwner(player);
        bh.setPos(spawnPos.x, spawnPos.y - 1.0, spawnPos.z); // drop slightly below eye
        world.spawnEntity(bh);

        // player fx
        removeTagPrefix(player, BLACKHOLE_TAG);
        player.getCommandTags().add(BLACKHOLE_TAG + BlackHoleEntity.LIFESPAN);

        world.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_PORTAL_AMBIENT,
                player.getSoundCategory(), 1.2f, 0.4f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), 0.6f, 0.5f);
    }

    private void handleBlackHole(ServerPlayerEntity player) {
        if (!hasTag(player, BLACKHOLE_TAG)) return;

        // slowness on user
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                5, 4, true, false, false
        ));
        player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                StatusEffects.SLOW_FALLING,
                5, 0, true, false, false
        ));
    }

    public static void drainFateTimer(LivingEntity target) {
        reduceFateTimer(target, BH_TIMER_REDUCTION);
    }

    /* ============================================================
       META
       ============================================================ */

    @Override public String getName()          { return "Cosmic"; }
    @Override public String getPassiveName()   { return "Written in the Stars"; }
    @Override public String getPrimaryName()   { return "Cosmic Ray"; }
    @Override public String getSecondaryName() { return "Shooting Star"; }
    @Override public String getUltimateName()  { return "Black Hole"; }

    @Override public long getPrimaryCooldownMs()   { return 3000; }
    @Override public long getSecondaryCooldownMs() { return 8000; }
    @Override public long getUltimateCooldownMs()  { return 15000; }

    @Override
    public String getOverviewDescription() {
        return "Cosmic is a damage based power, the whole gimmick revolves around low damage being done initially, but having huge damage come in bursts later due" +
                " to the passive. All abilities revolve around this passive in different ways, and understanding how it works and how each ability interacts with it are" +
                " fundamental for doing well with this power. The explanation for the passive and each ability should help with this.";
    }

    @Override
    public String getPassiveDescription() {
        return "This is what your entire power revolves around. Your basic melee hits and some abilities apply a debuff called Fate, there are two elements to this debuff:" +
                " /n Fate damage: This is the amount of damage Fate currently stores, it increases with the hits you do and is indicated by the amount of dust coming off the" +
                " effected entity. This has a hard cap, when an entity has reached max damage, hitting it will play a sound and display star particles." +
                " /n Fate Timer: This is the time until the stored fate damage is quickly applied, it is indicated by the orbiting sun around the entity, with faster orbit speeds" +
                " meaning it is closer to detonation and extra fire particles will also appear when about to detonate." +
                " /n When the timer expires, the entity will explode and have the debuff removed, quickly taking all of the damage that was stored over a few seconds." +
                " They will then be immune to building fate for a long time (if they survive)." +
                "/n" +
                "/n But, your melee hits do significantly less damage and the lost damage is stored as Fate, melee hits also increase the countdown timer, giving you more time to build fate.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Fire a hitscan beam that deals minor direct damage but stores significant Fate damage on the target and extends their timer significantly." +
                " The beam pierces through entities but not blocks." +
                " This is a lot more reliable than extending it via melee hits.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Launch into the air and slam down towards your crosshair." +
                " The impact applies a small amount of fate damage to nearby enemies but significantly reduces their fate timer by a percentage." +
                " So this can be used to trigger detonations on groups of enemies.";
    }

    @Override
    public String getUltimateDescription() {
        return "Summon a black hole that follows your location, you are slowed during this." +
                "/n This black hole has a huge pull radius and gets a stronger pull the closer entities are to you, eventually getting too difficult to outrun." +
                "/n Any entities in the centre of the black hole will take constant damage (this does not apply Fate damage) and their Fate timer will be quickly drained down." +
                "/n Essentially, this is a way to quickly drain fate and explode groups of entities." +
                "/n Also, you should go into 3rd person for this ultimate, since there will be A LOT of particles around you.";
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static int getFateRaw(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                try {
                    return Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static boolean hasTag(LivingEntity entity, String prefix) {
        for (String tag : entity.getCommandTags()) {
            if (tag.startsWith(prefix)) return true;
        }
        return false;
    }

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
}