package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.entity.PowerFireballEntity;
import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.FireHoverPayload;
import com.yourname.loopypowers.network.payload.FireUltChargePayload;
import com.yourname.loopypowers.damage.ModDamageTypes;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FirePower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, FireState> ACTIVE_STATES = new HashMap<>();

    private static class FireState {
        int hoverTicks = 0;
        int ultChargeTicks = 0;
    }

    private static FireState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new FireState());
    }

    /* ============================================================
       CONSTANTS - PASSIVE
       ============================================================ */
    private static final int PASSIVE_FIRE_RESIST_DURATION = 600; // ticks
    private static final int PASSIVE_FIRE_ON_HIT_DURATION = 4;   // seconds

    /* ============================================================
       CONSTANTS - PRIMARY
       ============================================================ */
    private static final float PRIMARY_SPEED = 2.6f;
    private static final float PRIMARY_DIRECT_DAMAGE = 9.5f;
    private static final int PRIMARY_EXPLOSION_POWER = 3;
    private static final float PRIMARY_EXPLOSION_DAMAGE = 8.8f;
    private static final double PRIMARY_SPAWN_OFFSET = 0.6;

    /* ============================================================
       CONSTANTS - SECONDARY
       ============================================================ */
    private static final float SECONDARY_EXPLOSION_POWER = 2.5f;
    private static final float SECONDARY_EXPLOSION_DAMAGE = 13.5f;
    private static final double SECONDARY_LAUNCH_STRENGTH = 1.7;
    private static final double SECONDARY_KB_HORIZONTAL = 1.7;
    private static final double SECONDARY_KB_VERTICAL = 0.5;
    private static final int SECONDARY_FIRE_DURATION = 4; // seconds
    private static final int SECONDARY_HOVER_TICKS = 140;
    private static final int SECONDARY_NO_FALL_TICKS = 200;

    // chance to cook a snack
    private static final float COOK_FOOD_CHANCE = 0.35f;

    // Hover Mechanics
    private static final double HOVER_LIFT_FORCE = 0.045;
    private static final double HOVER_GRAVITY_LIMIT = -0.08;
    private static final double HOVER_AIR_CONTROL = 0.99;
    private static final double HOVER_STEERING_FORCE = 0.035;

    // Camera Shake
    private static final int SECONDARY_SHAKE_DURATION = 20;
    private static final int SECONDARY_SHAKE_AMPLITUDE = 10;
    private static final float SECONDARY_SHAKE_INTENSITY = 1.0f;

    private static final int SECONDARY_EXPLOSION_SHAKE_DUR = 6;
    private static final int SECONDARY_EXPLOSION_SHAKE_AMP = 8;
    private static final float SECONDARY_EXPLOSION_SHAKE_INT = 0.25f;

    /* ============================================================
       CONSTANTS - ULTIMATE
       ============================================================ */
    private static final int ULTIMATE_CHARGE_TICKS = 100;
    private static final float  ULTIMATE_EXPLOSION_POWER = 10.0f;
    private static final float  ULTIMATE_DAMAGE_RADIUS = 12.0f;
    private static final float  ULTIMATE_MAX_DAMAGE = 40.0f;
    private static final int    ULTIMATE_FIRE_DURATION = 6;

    // Pull
    private static final double ULTIMATE_PULL_BASE_RADIUS = 6.0;
    private static final double ULTIMATE_PULL_SCALED_RADIUS = 14.0;
    private static final double ULTIMATE_PULL_BASE_STRENGTH = 0.01;
    private static final double ULTIMATE_PULL_SCALED_STRENGTH = 0.04;
    private static final double ULTIMATE_PULL_VERTICAL_MODIFIER = 0.2;

    // Camera Shake
    private static final int ULT_START_SHAKE_RADIUS = 30;
    private static final int ULT_START_SHAKE_TIME = 100;
    private static final float ULT_START_SHAKE_INTENSITY = 0.6f;

    private static final int ULT_DETONATE_SHAKE_DURATION = 50;
    private static final int ULT_DETONATE_SHAKE_AMPLITUDE = 30;
    private static final float ULT_DETONATE_SHAKE_INTENSITY = 2.5f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("fire_")); // Clean legacy tags
        ACTIVE_STATES.put(player.getUuid(), new FireState());

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE,
                PASSIVE_FIRE_RESIST_DURATION,
                0,
                true,
                false
        ));
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("fire_"));
        ACTIVE_STATES.remove(player.getUuid());

        player.removeStatusEffect(StatusEffects.FIRE_RESISTANCE);

        // Dynamically fetching RegistryEntry for custom effect
        player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.BRACED));

        player.removeStatusEffect(StatusEffects.RESISTANCE);
        player.removeStatusEffect(StatusEffects.SLOWNESS);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        FireState state = getState(player);

        // PASSIVES
        if (PassiveManager.isEnabled(player)) {
            StatusEffectInstance fireRes = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
            if (fireRes == null || fireRes.getDuration() < 100) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.FIRE_RESISTANCE,
                        PASSIVE_FIRE_RESIST_DURATION,
                        0,
                        true,
                        false
                ));
            }
        }

        // SECONDARY
        if (state.hoverTicks > 0) {
            tickHover(player, state);
        }

        // ULTIMATE
        if (state.ultChargeTicks > 0) {
            tickUltimateCharge(player, state);
        }
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        if (!PassiveManager.isEnabled(attacker)) return;
        target.setOnFireFor(PASSIVE_FIRE_ON_HIT_DURATION);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        shootFireball(world, player, PRIMARY_SPEED, PRIMARY_DIRECT_DAMAGE, PRIMARY_EXPLOSION_POWER, PRIMARY_EXPLOSION_DAMAGE);
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

        Vec3d spawnPos = player.getEyePos().add(look.multiply(PRIMARY_SPAWN_OFFSET));
        fireball.refreshPositionAndAngles(
                spawnPos.x, spawnPos.y, spawnPos.z,
                player.getYaw(), player.getPitch()
        );

        player.swingHand(Hand.MAIN_HAND, true);

        world.spawnEntity(fireball);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {

        launchExplosion(player);

        FireState state = getState(player);
        state.hoverTicks = SECONDARY_HOVER_TICKS;

        // snack time
        if (player.getServerWorld().random.nextFloat() < COOK_FOOD_CHANCE) {
            tryCookSnack(player);
        }

        CameraShake.shakeNearby(player, SECONDARY_SHAKE_DURATION, SECONDARY_SHAKE_AMPLITUDE, SECONDARY_SHAKE_INTENSITY);

        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
    }

    private void tryCookSnack(ServerPlayerEntity player) { // check if player is holding a cookable food and cook it
        Hand[] hands = { Hand.MAIN_HAND, Hand.OFF_HAND };
        for (Hand hand : hands) {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) continue;

            Item cooked = getCookedVariant(stack.getItem());
            if (cooked != null) {
                stack.decrement(1);
                player.getInventory().offerOrDrop(new ItemStack(cooked));

                // Fixed playSound coordinates
                player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH, net.minecraft.sound.SoundCategory.PLAYERS, 0.4f, 2.0f);
                break; // only do one
            }
        }
    }

    private Item getCookedVariant(Item raw) { // cookable items
        if (raw == Items.BEEF) return Items.COOKED_BEEF;
        if (raw == Items.PORKCHOP) return Items.COOKED_PORKCHOP;
        if (raw == Items.CHICKEN) return Items.COOKED_CHICKEN;
        if (raw == Items.MUTTON) return Items.COOKED_MUTTON;
        if (raw == Items.RABBIT) return Items.COOKED_RABBIT;
        if (raw == Items.COD) return Items.COOKED_COD;
        if (raw == Items.SALMON) return Items.COOKED_SALMON;
        if (raw == Items.POTATO) return Items.BAKED_POTATO;
        if (raw == Items.KELP) return Items.DRIED_KELP;
        return null;
    }

    private void launchExplosion(ServerPlayerEntity player) {

        ServerWorld world = player.getServerWorld();

        DamageSource explosionSource = ModDamageTypes.firePower(world, player);

        world.createExplosion(
                player,
                explosionSource,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SECONDARY_EXPLOSION_POWER,
                true,
                World.ExplosionSourceType.MOB
        );

        CameraShake.shakeNearby(player,
                SECONDARY_EXPLOSION_SHAKE_DUR,
                SECONDARY_EXPLOSION_SHAKE_AMP,
                SECONDARY_EXPLOSION_SHAKE_INT);

        player.setVelocity(
                player.getVelocity().x * 0.7,
                SECONDARY_LAUNCH_STRENGTH,
                player.getVelocity().z * 0.7
        );

        player.velocityModified = true;

        player.fallDistance = 0;

        // stop fall damage using fetched RegistryEntry
        player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.BRACED),
                SECONDARY_NO_FALL_TICKS,
                0,
                true,
                false,
                true // showIcon
        ));

        world.getOtherEntities(
                player,
                player.getBoundingBox().expand(4),
                e -> e instanceof LivingEntity
        ).forEach(entity -> {

            Vec3d dir = entity.getPos()
                    .subtract(player.getPos())
                    .normalize();

            entity.addVelocity(dir.x * SECONDARY_KB_HORIZONTAL, SECONDARY_KB_VERTICAL, dir.z * SECONDARY_KB_HORIZONTAL);

            entity.velocityModified = true;

            entity.damage(
                    explosionSource,
                    SECONDARY_EXPLOSION_DAMAGE
            );
            entity.setOnFireFor(SECONDARY_FIRE_DURATION);
        });
    }

    private void tickHover(ServerPlayerEntity player, FireState state) {

        if (player.isOnGround() || player.isSneaking()) {
            state.hoverTicks = 0;
            return;
        }

        Vec3d vel = player.getVelocity();

        double newY = vel.y;

        if (newY < 0.25)
            newY += HOVER_LIFT_FORCE;

        if (newY < HOVER_GRAVITY_LIMIT)
            newY = HOVER_GRAVITY_LIMIT;

        player.setVelocity(
                vel.x * HOVER_AIR_CONTROL,
                newY,
                vel.z * HOVER_AIR_CONTROL
        );

        Vec3d look = player.getRotationVec(1.0f);

        player.addVelocity(
                look.x * HOVER_STEERING_FORCE,
                0,
                look.z * HOVER_STEERING_FORCE
        );

        player.velocityModified = true;

        player.fallDistance = 0;

        // hover jets → client
        ServerWorld hoverWorld = player.getServerWorld();
        FireHoverPayload hoverPay = new FireHoverPayload(player.getX(), player.getY(), player.getZ());
        Set<ServerPlayerEntity> hoverViewers = new HashSet<>();
        PlayerLookup.tracking(hoverWorld, player.getBlockPos()).forEach(hoverViewers::add);
        hoverViewers.add(player);
        hoverViewers.forEach(p -> ServerPlayNetworking.send(p, hoverPay));

        state.hoverTicks--;
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    @Override
    public void activateUltimate(ServerPlayerEntity player) {

        FireState state = getState(player);
        state.ultChargeTicks = ULTIMATE_CHARGE_TICKS;

        CameraShake.shakeNearby(player, ULT_START_SHAKE_RADIUS, ULT_START_SHAKE_TIME, ULT_START_SHAKE_INTENSITY);

        // Fixed playSound coordinates
        player.getServerWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                net.minecraft.sound.SoundEvents.ITEM_TOTEM_USE,
                player.getSoundCategory(),
                1.0f,
                0.6f
        );
    }

    private void tickUltimateCharge(ServerPlayerEntity player, FireState state) {

        var world = player.getServerWorld();

        state.ultChargeTicks--;

        if (state.ultChargeTicks <= 0) {
            detonateUltimate(player);
            return;
        }

        float progress = 1f - (state.ultChargeTicks / (float) ULTIMATE_CHARGE_TICKS);

        applyUltimateChargeEffects(player, world, progress, state.ultChargeTicks);
    }

    private void applyUltimateChargeEffects(
            ServerPlayerEntity player,
            ServerWorld world,
            float progress,
            int ticksRemaining
    ) {

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 4, true, false));

        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, 5, 3, true, false));

        player.fallDistance = 0;

        // ult charge build-up → client
        boolean nearEnd = ticksRemaining < 20;
        FireUltChargePayload chargePay = new FireUltChargePayload(
                player.getX(), player.getY(), player.getZ(), progress, nearEnd);
        Set<ServerPlayerEntity> chargeViewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(chargeViewers::add);
        chargeViewers.add(player);
        chargeViewers.forEach(p -> ServerPlayNetworking.send(p, chargePay));

        CameraShake.shakeNearby(
                player,
                20,
                2,
                0.15f + progress * 0.6f
        );

        pullEntitiesToward(player, world, progress);

        if (ticksRemaining % 20 == 0) {
            // Fixed playSound coordinates
            world.playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    net.minecraft.sound.SoundEvents.BLOCK_FIRE_AMBIENT,
                    player.getSoundCategory(),
                    1.5f,
                    0.5f + progress
            );
        }

        if (ticksRemaining < 20) {
            CameraShake.shakeNearby(player, 60, 3, 1.2f);
        }
    }

    private void pullEntitiesToward(
            ServerPlayerEntity player,
            ServerWorld world,
            float progress
    ) {
        double radius = ULTIMATE_PULL_BASE_RADIUS + progress * ULTIMATE_PULL_SCALED_RADIUS;

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

            double strength = ULTIMATE_PULL_BASE_STRENGTH + progress * ULTIMATE_PULL_SCALED_STRENGTH;

            entity.addVelocity(
                    dir.x * strength,
                    dir.y * strength * ULTIMATE_PULL_VERTICAL_MODIFIER,
                    dir.z * strength
            );

            entity.velocityModified = true;
        }
    }

    private void detonateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Ensure the source attributes the player so death messages work
        DamageSource source = ModDamageTypes.fireExplosion(world, player);

        world.createExplosion(
                player,
                source,
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ULTIMATE_EXPLOSION_POWER,
                true,
                World.ExplosionSourceType.MOB
        );

        // This now handles the bulk of the "controlled" damage
        applyLOSExplosionDamage(player, ULTIMATE_DAMAGE_RADIUS, ULTIMATE_MAX_DAMAGE);

        CameraShake.shakeNearby(player, ULT_DETONATE_SHAKE_DURATION, ULT_DETONATE_SHAKE_AMPLITUDE, ULT_DETONATE_SHAKE_INTENSITY);

        // Fixed playSound coordinates
        world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE, player.getSoundCategory(), 3.5f, 0.6f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 4.0f, 0.5f);
    }

    private void applyLOSExplosionDamage(ServerPlayerEntity sourcePlayer, float radius, float maxDamage) {
        ServerWorld world = sourcePlayer.getServerWorld();
        Vec3d origin = sourcePlayer.getPos();
        Box area = new Box(origin, origin).expand(radius);

        // get attacker
        DamageSource source = ModDamageTypes.fireExplosion(world, sourcePlayer);

        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, area, e -> e != sourcePlayer)) {
            Vec3d target = entity.getPos().add(0, entity.getHeight() * 0.5, 0);

            // line of sight check
            var hit = world.raycast(new net.minecraft.world.RaycastContext(
                    origin,
                    target,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    sourcePlayer
            ));

            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK)
                continue;

            double dist = origin.distanceTo(target);
            double falloff = 1.0 - (dist / radius);

            if (falloff <= 0) continue;

            // calculate damage based on distance
            float damage = (float)(maxDamage * falloff);

            entity.setOnFireFor(ULTIMATE_FIRE_DURATION);

            // damage the entity
            entity.damage(source, damage);
        }
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override
    public long getSecondaryCooldownMs() {
        return 45_000;
    }

    @Override
    public long getUltimateCooldownMs() {
        return 500_000;
    }

    @Override
    public long getPrimaryCooldownMs() {
        return 8_500;
    }

    @Override
    public String getName() {
        return Text.translatable("power.loopypowers.fire.name").getString();
    }

    @Override
    public String getPrimaryName() {
        return Text.translatable("power.loopypowers.fire.primary_name").getString();
    }

    @Override
    public String getSecondaryName() {
        return Text.translatable("power.loopypowers.fire.secondary_name").getString();
    }

    @Override
    public String getUltimateName() {
        return Text.translatable("power.loopypowers.fire.ultimate_name").getString();
    }

    @Override
    public String getPassiveName() {
        return Text.translatable("power.loopypowers.fire.passive_name").getString();
    }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.fire.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.fire.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.fire.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.fire.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.fire.description.ultimate").getString();
    }
}