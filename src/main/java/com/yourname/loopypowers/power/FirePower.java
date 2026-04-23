package com.yourname.loopypowers.power;
import com.yourname.loopypowers.entity.ModEntities;

import com.yourname.loopypowers.entity.PowerFireballEntity;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class FirePower implements Power {

    @Override
    public void onAssign(ServerPlayerEntity player) { // when power gained
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE,
                600,   // 30 seconds
                0,
                true,
                false
        ));
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        // PASSIVES:
        // FIRE RESI
        StatusEffectInstance fireRes =
                player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);

        if (fireRes == null || fireRes.getDuration() < 100) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.FIRE_RESISTANCE,
                    600,
                    0,
                    true,
                    false
            ));
        }
        // SECONDARY:
        if (player.getCommandTags().contains("fire_hover"))
            tickHover(player);

        if (player.getCommandTags().contains("fire_no_fall"))
            tickNoFall(player);
        // ULTIMATE:
        if (player.getCommandTags().contains("fire_ultimate_charge"))
            tickUltimateCharge(player); //everytime i add something to ticks i feel like im bumfucking the server sexual style
    }
    // FIRE ASPECT
    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // sets the target on fire
        target.setOnFireFor(4);
    }

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        // Fireball features
        float speed = 2.6f;          // higher = faster travel
        float directDamage = 8.0f;   // damage on direct hit
        int explosionPower = 4;      // damage to world
        float explosionDamage = 6.0f; // AoE damage

        shootFireball(world, player, speed, directDamage, explosionPower, explosionDamage);
    }

    private void shootFireball(
            ServerWorld world,
            ServerPlayerEntity player,
            float speed,
            float directDamage,
            int explosionPower,
            float explosionDamage
    ) {
        Vec3d look = player.getRotationVec(1.0f);

        PowerFireballEntity fireball = new PowerFireballEntity(
                ModEntities.POWER_FIREBALL,
                world,
                player,
                look.x * speed,
                look.y * speed,
                look.z * speed,
                explosionPower
        );

        fireball.setDamageValues(directDamage, explosionDamage);

        // Spawn from the player’s eyes, slightly forward so it doesn't collide instantly
        Vec3d spawnPos = player.getEyePos().add(look.multiply(0.6));
        fireball.refreshPositionAndAngles(
                spawnPos.x, spawnPos.y, spawnPos.z,
                player.getYaw(), player.getPitch()
        );

        player.swingHand(Hand.MAIN_HAND, true);

        world.spawnEntity(fireball); // ServerWorld spawns the entity
    }

    //SECONDARY
    @Override
    public void activateSecondary(ServerPlayerEntity player) {

        launchExplosion(player);

        player.getCommandTags().add("fire_hover");
        player.getCommandTags().add("fire_hover_ticks_100");

        CameraShake.shakeNearby(player, 20, 10, 1.0f);
    }

    private void launchExplosion(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();

        DamageSource explosionSource =
                player.getDamageSources().explosion(player, player);

        world.createExplosion(
                player,
                explosionSource,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                2.5f,
                true,
                World.ExplosionSourceType.MOB
        );

        // camerashake
        CameraShake.shakeNearby(player,
                6,
                8,
                0.25f);

        // launch upward
        double launchStrength = 1.7; // STRENGTH OF LAUNCH

        player.setVelocity(
                player.getVelocity().x * 0.7,
                launchStrength,
                player.getVelocity().z * 0.7
        );

        player.velocityModified = true;

        player.fallDistance = 0;

        player.getCommandTags().add("fire_no_fall");
        player.getCommandTags().add("fire_no_fall_ticks_200"); // TIME OF HOVER (in ticks)

        // knockback entities
        world.getOtherEntities(
                player,
                player.getBoundingBox().expand(4),
                e -> e instanceof LivingEntity
        ).forEach(entity -> {

            Vec3d dir = entity.getPos()
                    .subtract(player.getPos())
                    .normalize();

            entity.addVelocity(dir.x * 1.8, 0.6, dir.z * 1.8);

            entity.velocityModified = true;

            entity.damage(
                    player.getDamageSources().explosion(player, player),
                    6.0f
            );
            entity.setOnFireFor(4);
        });
    }

    private void tickHover(ServerPlayerEntity player) {

        if (player.isOnGround()) { // dodges method if not floating
            player.getCommandTags().remove("fire_hover");
            return;
        }

        Vec3d vel = player.getVelocity();

        // movement stuff
        double liftForce = 0.045;     // upward force
        double gravityLimit = -0.08;  // max fall speed
        double airControl = 0.99;     // horizontal control

        double newY = vel.y;

        // apply lift if movement is poor
        if (newY < 0.25)
            newY += liftForce;

        // clamp fall speed
        if (newY < gravityLimit)
            newY = gravityLimit;

        player.setVelocity(
                vel.x * airControl,
                newY,
                vel.z * airControl
        );

        Vec3d look = player.getRotationVec(1.0f); // Make moving easier

        double steeringForce = 0.035;

        player.addVelocity(
                look.x * steeringForce,
                0,
                look.z * steeringForce
        );

        player.velocityModified = true;

        player.fallDistance = 0;

        spawnHoverParticles(player);
        tickHoverTimer(player);
    }

    private void spawnHoverParticles(ServerPlayerEntity player) {

        var world = player.getServerWorld();

        world.spawnParticles(
                ParticleTypes.FLAME,
                player.getX(),
                player.getY() - 0.4,
                player.getZ(),
                8,
                0.25,
                0.1,
                0.25,
                0.02
        );

        world.spawnParticles(
                ParticleTypes.SMOKE,
                player.getX(),
                player.getY() - 0.4,
                player.getZ(),
                4,
                0.2,
                0.05,
                0.2,
                0.01
        );
    }

    private void tickHoverTimer(ServerPlayerEntity player) {

        for (String tag : player.getCommandTags()) {

            if (tag.startsWith("fire_hover_ticks_")) {

                int ticks = Integer.parseInt(tag.substring(17)) - 1;

                player.getCommandTags().remove(tag);

                if (ticks > 0) {
                    player.getCommandTags().add("fire_hover_ticks_" + ticks);
                }
                else {
                    player.getCommandTags().remove("fire_hover");
                }

                break;
            }
        }
    }
    private void tickNoFall(ServerPlayerEntity player) {

        player.fallDistance = 0;

        final String prefix = "fire_no_fall_ticks_";

        for (String tag : player.getCommandTags()) {

            if (tag.startsWith(prefix)) {

                int ticks;
                try {
                    ticks = Integer.parseInt(tag.substring(prefix.length())) - 1;
                } catch (NumberFormatException e) {
                    // If tag is corrupted, remove it safely
                    player.getCommandTags().remove(tag);
                    player.getCommandTags().remove("fire_no_fall");
                    break;
                }

                player.getCommandTags().remove(tag);

                if (ticks > 0) {
                    player.getCommandTags().add(prefix + ticks);
                } else {
                    player.getCommandTags().remove("fire_no_fall");
                }

                break;
            }
        }
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {

        player.getCommandTags().add("fire_ultimate_charge");
        player.getCommandTags().add("fire_ultimate_charge_ticks_100"); // this just does the countdown, other methods do the thing

        CameraShake.shakeNearby(player, 30, 100, 0.6f);

        player.getServerWorld().playSound(
                null,
                player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE, //change this, it sucks
                player.getSoundCategory(),
                1.0f,
                0.6f
        );
    }

    private void tickUltimateCharge(ServerPlayerEntity player) {

        var world = player.getServerWorld();

        final String prefix = "fire_ultimate_charge_ticks_";

        int ticksRemaining = 0;

        for (String tag : player.getCommandTags()) {
            if (tag.startsWith(prefix)) {

                ticksRemaining = Integer.parseInt(tag.substring(prefix.length()));

                player.getCommandTags().remove(tag);

                if (ticksRemaining > 1) {
                    player.getCommandTags().add(prefix + (ticksRemaining - 1));
                } else {
                    player.getCommandTags().remove("fire_ultimate_charge");
                    detonateUltimate(player);
                    return;
                }
                break;
            }
        }

        float progress = 1f - (ticksRemaining / 100f);

        applyUltimateChargeEffects(player, world, progress, ticksRemaining);
    }

    private void applyUltimateChargeEffects(
            ServerPlayerEntity player,
            ServerWorld world,
            float progress,
            int ticksRemaining
    ) {

        // Strong resistance and immobility
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 4, true, false));

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 3, true, false));

        player.fallDistance = 0;

        double intensity = 0.5 + progress * 2.5;

        // fire particles
        world.spawnParticles(
                ParticleTypes.FLAME,
                player.getX(),
                player.getY(),
                player.getZ(),
                (int)(10 * intensity),
                0.4 * intensity,
                1.2 * intensity,
                0.4 * intensity,
                0.02 * intensity
        );

        // Lava droplets increase over time
        world.spawnParticles(
                ParticleTypes.LAVA,
                player.getX(),
                player.getY() + 0.5,
                player.getZ(),
                (int)(2 + progress * 10),
                0.6,
                0.8,
                0.6,
                0.03
        );

        // Smoke column
        world.spawnParticles(
                ParticleTypes.LARGE_SMOKE,
                player.getX(),
                player.getY(),
                player.getZ(),
                (int)(5 * intensity),
                0.5,
                1.5,
                0.5,
                0.01
        );
        // camerashake
        CameraShake.shakeNearby(
                player,
                20,
                2,
                0.15f + progress * 0.6f
        );

        // this just sounds cool tbh
        pullEntitiesToward(player, world, progress);

        // constants
        if (ticksRemaining % 20 == 0) {

            world.playSound(
                    null,
                    player.getBlockPos(),
                    net.minecraft.sound.SoundEvents.BLOCK_FIRE_AMBIENT,
                    player.getSoundCategory(),
                    1.5f,
                    0.5f + progress
            );
        }

        // final second
        if (ticksRemaining < 20) {

            world.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    player.getX(),
                    player.getY() + 1,
                    player.getZ(),
                    40,
                    1.2,
                    1.5,
                    1.2,
                    0.08
            );

            CameraShake.shakeNearby(player, 60, 3, 1.2f);
        }
    }
    private void pullEntitiesToward(
            ServerPlayerEntity player,
            ServerWorld world,
            float progress
    ) {
        double radius = 6 + progress * 14; //cool effect but not worth the time

        for (LivingEntity entity : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(radius),
                e -> e != player
        )) {

            Vec3d dir = player.getPos()
                    .subtract(entity.getPos());

            double distance = dir.length();

            if (distance < 0.1) continue;

            dir = dir.normalize();

            double strength = 0.03 + progress * 0.12; // STRENGTH = PULL STRENGTH
            // keep in mind that this happens 20 times a second
            entity.addVelocity(
                    dir.x * strength,
                    dir.y * strength * 0.4,
                    dir.z * strength
            );

            entity.velocityModified = true;
        }
    }

    private void detonateUltimate(ServerPlayerEntity player) { // the actual explosion
        // holy shit this took so long to even get to
        ServerWorld world = player.getServerWorld();

        float explosionPower = 30.0f; // DONT FORGET TO CHANGE THE OTHER VARIABLES WITH THIS
        float damageRadius = 10f;
        float maxDamage = 18f;

        DamageSource source =
                player.getDamageSources().explosion(player, player);

        // real explosion
        world.createExplosion(
                player,
                source,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                explosionPower,
                true,
                World.ExplosionSourceType.MOB
        );

        // LOS-based entity damage
        applyLOSExplosionDamage(player, damageRadius, maxDamage);

        CameraShake.shakeNearby(player, 50, 30, 2.5f);

        world.playSound(
                null,
                player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE,
                player.getSoundCategory(),
                3.5f,
                0.6f
        );

        world.playSound(
                null,
                player.getBlockPos(),
                net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(),
                4.0f,
                0.5f
        );
    }
    private void applyLOSExplosionDamage( // without this, it would just kill everything
            ServerPlayerEntity sourcePlayer, // essentially this just scales the maxdamage with LOS
            float radius,
            float maxDamage
    ) {

        ServerWorld world = sourcePlayer.getServerWorld();

        Vec3d origin = sourcePlayer.getPos();

        Box area = new Box(origin, origin).expand(radius);

        DamageSource source =
                sourcePlayer.getDamageSources().explosion(sourcePlayer, sourcePlayer);

        for (LivingEntity entity : world.getEntitiesByClass(
                LivingEntity.class,
                area,
                e -> e != sourcePlayer
        )) {

            Vec3d target = entity.getPos().add(0, entity.getHeight() * 0.5, 0);

            // raycast from start
            var hit = world.raycast(new net.minecraft.world.RaycastContext(
                    origin,
                    target,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    sourcePlayer
            ));

            // if wall blocks, skip damage
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK)
                continue;

            double dist = origin.distanceTo(target);

            double falloff = 1.0 - (dist / radius);

            if (falloff <= 0) continue;

            float damage = (float)(maxDamage * falloff);

            entity.setOnFireFor(6);

            entity.damage(source, damage);
        }
    }

    @Override
    public long getSecondaryCooldownMs() {
        return 5_000; // 35 seconds
    }

    @Override
    public long getUltimateCooldownMs() {
        return 10_000; // 100 seconds
    }

    @Override
    public long getPrimaryCooldownMs() {
        return 2_500; // 6.5 seconds
    }

    @Override
    public String getPrimaryName() {
        return "Fireball";
    }

    @Override
    public String getSecondaryName() {
        return "Rising Sun";
    }

    @Override
    public String getUltimateName() {
        return "Supernova";
    }

    @Override
    public String getName() {
        return "Fire";
    }

    @Override
    public String getOverviewDescription() {
        return "Fire is mainly effective at range due to the fireball ability, which the secondary compliments. It also still has some utility up close, since" +
                "your hits inflict fire and you do not take fire damage. These abilities are destructive though so be careful.";
    }

    @Override
    public String getPassiveName() {
        return "Heart of fire";
    } // i just suck at namers huh

    @Override
    public String getPassiveDescription() {
        return "You have constant fire resistance and your hits will set entities on fire. This does not stack with fire aspect (the stronger fire will be applied)";
    }

    @Override
    public String getPrimaryDescription() {
        return "Shoot an explosive fireball in the direction that you are looking. This does extra damage for a direct hit and damages nearby blocks.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Make an explosion at your feet and shoot up in the air, you will now be levitating using fire for a few seconds. You will not take fall damage when you " +
                "land. This is supposed to keep you at range from others. You can cancel the levitation by sneaking. This can also be used for movement during your ultimate.";
    }

    @Override
    public String getUltimateDescription() {
        return "Charge up for an extended period of time, surrounding yourself in particles and slowing down greatly. Nearby enemies will be pulled in slightly. After charge unleash a huge explosion damaging anything nearby. This will not" +
                "damage you.";
    }
}