package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.DisplaceEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.damage.DamageSource;

import java.util.*;

public class DimensionalPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, DimensionalState> PLAYER_STATES = new HashMap<>();
    private static final Map<UUID, Map<UUID, Integer>> ACTIVE_DISPLACEMENTS = new HashMap<>();
    private static final Map<UUID, FractureState> ACTIVE_FRACTURES = new HashMap<>();

    private static class DimensionalState {
        double phaseChance = BASE_PHASE_CHANCE;
        int passiveCdTicks = 0;
        int phaseTicks = 0;
        int immuneTicks = 0;
        int phaseShiftTicks = 0;
        net.minecraft.world.GameMode prevMode = net.minecraft.world.GameMode.SURVIVAL;
    }

    private DimensionalState getState(ServerPlayerEntity player) {
        return PLAYER_STATES.computeIfAbsent(player.getUuid(), k -> new DimensionalState());
    }

    /* ============================================================
       CONSTANTS
       ============================================================ */

    private static final int PASSIVE_PHASE_TICKS = 40;
    private static final int PASSIVE_COOLDOWN    = 50;

    private static final double BASE_PHASE_CHANCE         = 0.0005;
    private static final double DAMAGE_CHANCE_GAIN        = 0.002;
    private static final double MAX_PHASE_CHANCE          = 0.25;
    private static final int    FLICKER_CYCLE             = 8;
    private static final int    FLICKER_ON_TIME           = 3;
    private static final float  SITUATIONAL_DIMENSION_CHANCE = 0.25f;
    private static final float  SILLY_EXIT_CHANCE         = 0.03f;

    private static final int    PHASE_SHIFT_DURATION      = 55;
    private static final float  ENTRY_SOUND_VOL           = 0.7f;
    private static final float  ENTRY_SOUND_PITCH         = 1.3f;
    private static final float  EXIT_SOUND_VOL            = 0.9f;
    private static final float  EXIT_SOUND_PITCH          = 0.8f;
    private static final int    TRAIL_INTERVAL            = 3;
    private static final int    EXIT_BURST_COUNT_MULT     = 2;
    private static final double EXIT_BURST_SPREAD         = 1.2;
    private static final double EXIT_BURST_SPEED          = 0.08;
    private static final double EXIT_DAMAGE_RADIUS        = 3.5;
    private static final float  EXIT_DAMAGE               = 16.5f;
    private static final double EXIT_PULL_STRENGTH        = 0.45;
    private static final double EXIT_KNOCKBACK_STRENGTH   = 0.8;
    private static final double EXIT_VERTICAL_BOOST       = 0.15;

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("int_"));
        PLAYER_STATES.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        DimensionalState state = PLAYER_STATES.remove(player.getUuid());
        if (state != null && state.phaseShiftTicks > 0
                && player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
            player.changeGameMode(state.prevMode);
        }
        player.getCommandTags().removeIf(tag -> tag.startsWith("int_"));

        // Send rift-end before clearing state so nearby clients can clean up
        FractureState frac = ACTIVE_FRACTURES.remove(player.getUuid());
        if (frac != null) {
            ServerWorld w = player.getServerWorld();
            DimensionalRiftEndPayload endPay = new DimensionalRiftEndPayload(player.getId());
            PlayerLookup.tracking(w, BlockPos.ofFloored(frac.origin)).forEach(p ->
                    ServerPlayNetworking.send(p, endPay));
            ServerPlayNetworking.send(player, endPay);
        }

        Map<UUID, Integer> myDisplacements = ACTIVE_DISPLACEMENTS.remove(player.getUuid());
        if (myDisplacements != null && player.getServer() != null) {
            for (ServerWorld w : player.getServer().getWorlds()) {
                for (UUID targetId : myDisplacements.keySet()) {
                    Entity e = w.getEntity(targetId);
                    if (e instanceof LivingEntity le) {
                        le.removeStatusEffect(StatusEffects.INVISIBILITY);
                        le.removeStatusEffect(StatusEffects.WEAKNESS);
                        le.removeStatusEffect(StatusEffects.RESISTANCE);
                        le.removeStatusEffect(StatusEffects.MINING_FATIGUE);
                        if (le instanceof MobEntity mob) mob.setAiDisabled(false);
                    }
                }
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class,
                        player.getBoundingBox().expand(150), LivingEntity::isAlive)) {
                    e.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FRACTURED));
                    e.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED));
                }
            }
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) { onRemove(player); }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;

        ServerWorld world = player.getServerWorld();
        DimensionalState state = getState(player);

        handlePassive(player, state, world);
        handlePhaseShift(player, state, world);
        handleFracture(player);

        Map<UUID, Integer> displaced = ACTIVE_DISPLACEMENTS.get(player.getUuid());
        if (displaced != null && !displaced.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = displaced.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                Entity ent = world.getEntity(entry.getKey());
                if (!(ent instanceof LivingEntity target) || !target.isAlive()) { it.remove(); continue; }
                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    if (target instanceof MobEntity mob) mob.setAiDisabled(false);
                    it.remove();
                } else {
                    entry.setValue(ticksLeft);
                    tickDisplaceTarget(target, world);
                }
            }
        }
    }

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        DimensionalState state = getState(victim);
        if (state.immuneTicks > 0) return false;
        state.phaseChance = Math.min(MAX_PHASE_CHANCE, state.phaseChance + DAMAGE_CHANCE_GAIN);
        return true;
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void handlePassive(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        if (state.phaseShiftTicks > 0) return;
        if (!PassiveManager.isEnabled(player)) return;

        if (state.phaseTicks > 0) { handleFlicker(player, state, world); return; }

        if (state.passiveCdTicks > 0) { state.passiveCdTicks--; return; }

        if (world.random.nextDouble() < state.phaseChance) {
            startPassivePhase(player, state);
            state.phaseChance = BASE_PHASE_CHANCE;
            state.passiveCdTicks = PASSIVE_COOLDOWN;
        }
    }

    private void startPassivePhase(ServerPlayerEntity player, DimensionalState state) {
        state.phaseTicks = PASSIVE_PHASE_TICKS;
        player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                Registries.SOUND_EVENT.getEntry(ModSounds.FLICKER),
                player.getSoundCategory(), 0.5f, 1.2f);
    }

    private void handleFlicker(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        long time = world.getTime();
        int cyclePos = (int)(time % FLICKER_CYCLE);
        boolean flickerActive = cyclePos < FLICKER_ON_TIME;

        if (cyclePos == 0) {
            // flicker burst → client
            double x = player.getX(), y = player.getBodyY(0.5), z = player.getZ();
            DimensionalFlickerPayload pay = new DimensionalFlickerPayload(x, y, z);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, pay));

            var flickerSound = switch (world.random.nextInt(3)) {
                case 0 -> ModSounds.FLICKER;
                case 1 -> ModSounds.FLICKER2;
                default -> ModSounds.FLICKER3;
            };
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    Registries.SOUND_EVENT.getEntry(flickerSound),
                    player.getSoundCategory(), 0.5f, 1.0f);
        }

        if (flickerActive) {
            RenderPackets.hidePlayerFromOthers(player, 1);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 2, 0, true, false));
        }

        state.immuneTicks = state.phaseTicks;
        state.phaseTicks--;

        if (state.phaseTicks <= 0) {
            state.immuneTicks = 0;
            triggerSituationalFlicker(player, world);
        }
    }

    private void triggerSituationalFlicker(ServerPlayerEntity player, ServerWorld world) {
        if (world.random.nextFloat() > SITUATIONAL_DIMENSION_CHANCE) return;

        double x = player.getX(), z = player.getZ();
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);

        if (player.fallDistance > 5.0f) {
            player.fallDistance = 0;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, false));
            world.playSound(null, x, player.getY(), z, SoundEvents.ENTITY_PHANTOM_FLAP, player.getSoundCategory(), 1.0f, 0.5f);
            double y = player.getBodyY(0.5);
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 0)));

        } else if (player.getAir() <= 0) {
            player.setAir(player.getMaxAir());
            world.playSound(null, x, player.getY(), z, SoundEvents.ENTITY_PLAYER_BREATH, player.getSoundCategory(), 1.0f, 1.0f);
            double y = player.getEyeY();
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 1)));

        } else if (player.getFrozenTicks() > 0) {
            player.setFrozenTicks(0);
            world.playSound(null, x, player.getY(), z, SoundEvents.BLOCK_FIRE_EXTINGUISH, player.getSoundCategory(), 0.8f, 1.0f);
            double y = player.getBodyY(0.5);
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 2)));

        } else if (player.hasStatusEffect(StatusEffects.LEVITATION)) {
            player.removeStatusEffect(StatusEffects.LEVITATION);
            player.setVelocity(player.getVelocity().x, -1.5, player.getVelocity().z);
            player.velocityModified = true;
            world.playSound(null, x, player.getY(), z, SoundEvents.BLOCK_ANVIL_LAND, player.getSoundCategory(), 0.8f, 0.8f);
            double y = player.getBodyY(0.5);
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 3)));

        } else if (player.hasStatusEffect(StatusEffects.BLINDNESS) || player.hasStatusEffect(StatusEffects.DARKNESS)) {
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            player.removeStatusEffect(StatusEffects.DARKNESS);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100, 0, false, false, false));
            world.playSound(null, x, player.getY(), z, SoundEvents.BLOCK_BEACON_ACTIVATE, player.getSoundCategory(), 1.0f, 1.5f);
            double y = player.getEyeY();
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 4)));

        } else if (player.hasStatusEffect(StatusEffects.POISON) || player.hasStatusEffect(StatusEffects.WITHER)) {
            player.removeStatusEffect(StatusEffects.POISON);
            player.removeStatusEffect(StatusEffects.WITHER);
            world.playSound(null, x, player.getY(), z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, player.getSoundCategory(), 1.2f, 1.0f);
            double y = player.getBodyY(0.5);
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 5)));

        } else if (player.getHungerManager().getFoodLevel() <= 6) {
            player.getHungerManager().setFoodLevel(8);
            player.getHungerManager().setSaturationLevel(4.0f);
            world.playSound(null, x, player.getY(), z, SoundEvents.ENTITY_PLAYER_BURP, player.getSoundCategory(), 1.0f, 0.9f);
            double y = player.getEyeY();
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 6)));

        } else if (player.isOnFire()) {
            player.extinguish();
            world.playSound(null, x, player.getY(), z, SoundEvents.ENTITY_GENERIC_SPLASH, player.getSoundCategory(), 1.0f, 1.2f);
            world.playSound(null, x, player.getY(), z, SoundEvents.BLOCK_FIRE_EXTINGUISH, player.getSoundCategory(), 0.6f, 1.0f);
            double y = player.getBodyY(0.5);
            viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 7)));

        } else if (player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
            if (slowness != null && slowness.getAmplifier() >= 1) {
                player.removeStatusEffect(StatusEffects.SLOWNESS);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 1, false, false, false));
                world.playSound(null, x, player.getY(), z, SoundEvents.ENTITY_MINECART_RIDING, player.getSoundCategory(), 0.5f, 1.5f);
                double y = player.getBodyY(0.2);
                viewers.forEach(p -> ServerPlayNetworking.send(p, new DimensionalSituationalFxPayload(x, y, z, 8)));
            }
        }
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        DimensionalState state = getState(player);

        state.phaseTicks = 0;
        state.immuneTicks = 0;
        state.phaseShiftTicks = PHASE_SHIFT_DURATION;
        state.prevMode = player.interactionManager.getGameMode();

        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);

        // phase enter burst → client
        double px = player.getX(), py = player.getBodyY(0.5), pz = player.getZ();
        DimensionalPhaseEnterPayload enterPay = new DimensionalPhaseEnterPayload(px, py, pz);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, enterPay));

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(), ENTRY_SOUND_VOL, ENTRY_SOUND_PITCH);
    }

    private void handlePhaseShift(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        if (state.phaseShiftTicks <= 0) return;
        state.phaseShiftTicks--;

        if (state.phaseShiftTicks <= 0) {
            if (player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR)
                exitPhaseShift(player, state, world);
            return;
        }

        if (world.getTime() % TRAIL_INTERVAL == 0) {
            // trail → client
            double px = player.getX(), py = player.getBodyY(0.5), pz = player.getZ();
            DimensionalPhaseTrailPayload trailPay = new DimensionalPhaseTrailPayload(px, py, pz);
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, trailPay));
        }
    }

    private void exitPhaseShift(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        player.changeGameMode(state.prevMode);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), EXIT_SOUND_VOL, EXIT_SOUND_PITCH);

        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(EXIT_DAMAGE_RADIUS),
                en -> en.isAlive() && en != player)) {

            e.damage(ModDamageTypes.phaseBurst(world, player), EXIT_DAMAGE);

            Vec3d fromPlayer = e.getPos().subtract(player.getPos());
            double distance = fromPlayer.length();
            if (distance < 0.001) continue;

            Vec3d dir = fromPlayer.normalize();
            Vec3d finalVelocity = dir.multiply(-EXIT_PULL_STRENGTH)
                    .add(dir.multiply(EXIT_KNOCKBACK_STRENGTH))
                    .add(0, EXIT_VERTICAL_BOOST, 0);
            e.addVelocity(finalVelocity.x, finalVelocity.y, finalVelocity.z);
            e.velocityModified = true;
        }

        // exit burst → client
        double px = player.getX(), py = player.getBodyY(0.5), pz = player.getZ();
        DimensionalPhaseExitPayload exitPay = new DimensionalPhaseExitPayload(px, py, pz);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, exitPay));

        CameraShake.shakeNearby(player, 5, 10, 0.6f);

        // easter egg
        if (world.random.nextFloat() < SILLY_EXIT_CHANCE) {
            int gag = world.random.nextInt(9);
            switch (gag) {
                case 0 -> {
                    net.minecraft.entity.passive.StriderEntity strider = net.minecraft.entity.EntityType.STRIDER.create(world);
                    if (strider != null) {
                        strider.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360f, 0);
                        world.spawnEntity(strider);
                        viewers.forEach(p -> ServerPlayNetworking.send(p,
                                new DimensionalSituationalFxPayload(px, py + 0.5, pz, 9)));
                    }
                }
                case 1 -> {
                    net.minecraft.entity.passive.CatEntity cat = net.minecraft.entity.EntityType.CAT.create(world);
                    if (cat != null) {
                        cat.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360f, 0);
                        world.spawnEntity(cat);
                        viewers.forEach(p -> ServerPlayNetworking.send(p,
                                new DimensionalSituationalFxPayload(px, py + 0.5, pz, 10)));
                    }
                }
                case 2 -> {
                    net.minecraft.entity.passive.PigEntity pig = net.minecraft.entity.EntityType.PIG.create(world);
                    if (pig != null) {
                        pig.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360f, 0);
                        pig.setCustomName(Text.translatable("entity.loopypowers.hamuel"));
                        world.spawnEntity(pig);
                        viewers.forEach(p -> ServerPlayNetworking.send(p,
                                new DimensionalSituationalFxPayload(px, py + 0.5, pz, 10)));
                    }
                }
                case 3 -> {
                    net.minecraft.entity.passive.SheepEntity sheep = net.minecraft.entity.EntityType.SHEEP.create(world);
                    if (sheep != null) {
                        sheep.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360f, 0);
                        sheep.setCustomName(Text.translatable("entity.loopypowers.woolliam"));
                        world.spawnEntity(sheep);
                        viewers.forEach(p -> ServerPlayNetworking.send(p,
                                new DimensionalSituationalFxPayload(px, py + 0.5, pz, 10)));
                    }
                }
                case 4 -> {
                    for (int c = 0; c < 3; c++) {
                        net.minecraft.entity.passive.CodEntity cod = net.minecraft.entity.EntityType.COD.create(world);
                        if (cod != null) {
                            cod.refreshPositionAndAngles(px, py, pz, world.random.nextFloat() * 360f, 0);
                            world.spawnEntity(cod);
                        }
                    }
                    viewers.forEach(p -> ServerPlayNetworking.send(p,
                            new DimensionalSituationalFxPayload(px, py + 0.5, pz, 11)));
                }
                case 5 -> {
                    net.minecraft.entity.EquipmentSlot[] slots = {
                            net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                            net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET
                    };
                    net.minecraft.item.Item[] leathers = {
                            net.minecraft.item.Items.LEATHER_HELMET, net.minecraft.item.Items.LEATHER_CHESTPLATE,
                            net.minecraft.item.Items.LEATHER_LEGGINGS, net.minecraft.item.Items.LEATHER_BOOTS
                    };
                    int startIdx = world.random.nextInt(4);
                    for (int i = 0; i < 4; i++) {
                        int idx = (startIdx + i) % 4;
                        if (player.getEquippedStack(slots[idx]).isEmpty()) {
                            player.equipStack(slots[idx], new net.minecraft.item.ItemStack(leathers[idx]));
                            world.playSound(null, px, player.getY(), pz,
                                    SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, player.getSoundCategory(), 1.0f, 1.0f);
                            break;
                        }
                    }
                }
                case 6 -> {
                    if (player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty()) {
                        player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD,
                                new net.minecraft.item.ItemStack(net.minecraft.item.Items.PLAYER_HEAD));
                        world.playSound(null, px, player.getY(), pz,
                                SoundEvents.ENTITY_ITEM_PICKUP, player.getSoundCategory(), 1.0f, 1.0f);
                    }
                }
                case 7 -> {
                    int balls = 3 + world.random.nextInt(3);
                    for (int i = 0; i < balls; i++) {
                        net.minecraft.entity.projectile.thrown.SnowballEntity snowball =
                                new net.minecraft.entity.projectile.thrown.SnowballEntity(world, player);
                        snowball.setPosition(px, py + 1.0, pz);
                        snowball.setVelocity((world.random.nextDouble() - 0.5) * 1.5,
                                world.random.nextDouble(),
                                (world.random.nextDouble() - 0.5) * 1.5);
                        world.spawnEntity(snowball);
                    }
                    world.playSound(null, px, player.getY(), pz,
                            SoundEvents.ENTITY_SNOWBALL_THROW, player.getSoundCategory(), 1.0f, 1.0f);
                }
                case 8 -> {
                    int sands = 2 + world.random.nextInt(3);
                    for (int i = 0; i < sands; i++) {
                        net.minecraft.entity.FallingBlockEntity sand = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(
                                world,
                                player.getBlockPos().up(2).add(world.random.nextInt(2) - 1, 0, world.random.nextInt(2) - 1),
                                net.minecraft.block.Blocks.SAND.getDefaultState());
                        sand.setVelocity((world.random.nextDouble() - 0.5) * 0.4, 0.2,
                                (world.random.nextDouble() - 0.5) * 0.4);
                    }
                }
            }
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d look = player.getRotationVec(1.0f);
        DisplaceEntity bolt = new DisplaceEntity(ModEntities.DISPLACE_ENTITY, world);
        bolt.setOwner(player);
        bolt.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        bolt.setVelocity(look.multiply(0.6));
        world.spawnEntity(bolt);

        player.swingHand(Hand.MAIN_HAND, true);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 0.6f, 1.4f);
    }

    @SuppressWarnings("unused")
    public static void applyDisplace(ServerPlayerEntity caster, LivingEntity target, int durationTicks) {
        ACTIVE_DISPLACEMENTS.computeIfAbsent(caster.getUuid(), k -> new HashMap<>())
                .put(target.getUuid(), durationTicks);
    }

    private void tickDisplaceTarget(LivingEntity entity, ServerWorld world) {
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
        entity.fallDistance = 0;

        if (entity instanceof ServerPlayerEntity displacedPlayer) {
            displacedPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(
                            displacedPlayer.getX(), displacedPlayer.getY(), displacedPlayer.getZ(),
                            displacedPlayer.getYaw(), displacedPlayer.getPitch(),
                            java.util.Set.of(
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT),
                            0));
            displacedPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 255, true, false, false));
            if (!displacedPlayer.getMainHandStack().isEmpty())
                displacedPlayer.getItemCooldownManager().set(displacedPlayer.getMainHandStack().getItem(), 5);
            displacedPlayer.stopUsingItem();
        }

        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 5, 0, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,     5, 255, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,   5, 255, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED), 5, 0, true, false, false));
        if (entity instanceof MobEntity mob) mob.setAiDisabled(true);

        // displace fx → client
        double x = entity.getX(), y = entity.getBodyY(0.5), z = entity.getZ();
        DimensionalDisplaceFxPayload pay = new DimensionalDisplaceFxPayload(x, y, z);
        PlayerLookup.tracking(world, entity.getBlockPos()).forEach(p ->
                ServerPlayNetworking.send(p, pay));
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    private static final int    ULT_DURATION             = 300;
    private static final double ULT_RIFT_RADIUS          = 26.0;
    private static final int    ULT_CRACK_COUNT          = 15;
    private static final int    ULT_CRACK_SEGMENTS       = 8;
    private static final double ULT_CRACK_SEGMENT_LENGTH = 2.3;
    private static final double ULT_CRACK_JAGGED_ANGLE   = 0.6;
    private static final double ULT_CRACK_WIDTH          = 0.9;
    private static final double ULT_WALL_PARTICLE_HEIGHT = 3.0;
    private static final int    ULT_WALL_PARTICLE_DENSITY = 1;
    private static final int    ULT_PARTICLE_INTERVAL    = 8;
    private static final int    ULT_RING_COUNT           = 2;
    private static final double ULT_RING_SPACING         = 9.0;
    private static final int    ULT_RING_POINTS          = 20;
    private static final double ULT_RING_WIDTH           = 1.0;
    private static final double MAX_STEP_UP  = 1.25;
    private static final double MAX_STEP_DOWN = 2.5;
    private static final int    SEARCH_DOWN  = 6;
    private static final int    SEARCH_UP    = 2;

    private static class FractureState {
        java.util.List<java.util.List<Vec3d>> cracks = new java.util.ArrayList<>();
        java.util.List<java.util.List<Vec3d>> rings  = new java.util.ArrayList<>();
        Vec3d origin;
        int ticksRemaining;
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        Vec3d look   = player.getRotationVec(1.0f);
        Vec3d origin = player.getPos().add(look.x * 3.0, 0, look.z * 3.0);

        FractureState state = new FractureState();
        state.origin = origin;
        state.ticksRemaining = ULT_DURATION;

        // Seeded RNG so client can reproduce exact same geometry
        long rngSeed = world.random.nextLong();
        java.util.Random rng = new java.util.Random(rngSeed);

        double angleStep = (Math.PI * 2.0) / ULT_CRACK_COUNT;
        for (int i = 0; i < ULT_CRACK_COUNT; i++) {
            double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
            state.cracks.add(buildCrackLine(origin, baseAngle, rng, world));
        }
        for (int i = 1; i <= ULT_RING_COUNT; i++) {
            state.rings.add(buildRing(origin, i * ULT_RING_SPACING, world));
        }

        ACTIVE_FRACTURES.put(player.getUuid(), state);

        // rift open → client (opening particles + geometry seed)
        DimensionalRiftOpenPayload openPay = new DimensionalRiftOpenPayload(
                player.getId(), origin.x, origin.y, origin.z, rngSeed);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(world, BlockPos.ofFloored(origin)).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, openPay));

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM, player.getSoundCategory(), 1.2f, 0.4f);
    }

    private java.util.List<Vec3d> buildRing(Vec3d center, double radius, ServerWorld world) {
        java.util.List<Vec3d> points = new java.util.ArrayList<>();
        int smooth = ULT_RING_POINTS * 3;
        for (int i = 0; i < smooth; i++) {
            double angle = (Math.PI * 2.0 * i) / smooth;
            Vec3d p = new Vec3d(center.x + Math.cos(angle) * radius, center.y,
                                center.z + Math.sin(angle) * radius);
            points.add(snapToGround(p, world));
        }
        return points;
    }

    private java.util.List<Vec3d> buildCrackLine(Vec3d origin, double baseAngle,
                                                  java.util.Random rng, ServerWorld world) {
        java.util.List<Vec3d> points = new java.util.ArrayList<>();
        Vec3d current = origin;
        points.add(snapToGround(current, world));

        double currentAngle = baseAngle;
        for (int seg = 0; seg < ULT_CRACK_SEGMENTS; seg++) {
            currentAngle += (rng.nextDouble() - 0.5) * ULT_CRACK_JAGGED_ANGLE;
            double segLen = ULT_CRACK_SEGMENT_LENGTH * (0.7 + rng.nextDouble() * 0.6);
            Vec3d nextFlat = current.add(Math.cos(currentAngle) * segLen, 0,
                                         Math.sin(currentAngle) * segLen);
            int steps = 3 + rng.nextInt(3);
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;
                Vec3d interp = new Vec3d(current.x + (nextFlat.x - current.x) * t, current.y,
                                         current.z + (nextFlat.z - current.z) * t);
                points.add(snapToGround(interp, world));
            }
            current = nextFlat;
            if (current.distanceTo(origin) > ULT_RIFT_RADIUS) break;
        }
        return points;
    }

    private void handleFracture(ServerPlayerEntity player) {
        FractureState state = ACTIVE_FRACTURES.get(player.getUuid());
        if (state == null) return;

        if (state.ticksRemaining <= 0) {
            ACTIVE_FRACTURES.remove(player.getUuid());
            ServerWorld world = player.getServerWorld();
            DimensionalRiftEndPayload endPay = new DimensionalRiftEndPayload(player.getId());
            PlayerLookup.tracking(world, BlockPos.ofFloored(state.origin)).forEach(p ->
                    ServerPlayNetworking.send(p, endPay));
            ServerPlayNetworking.send(player, endPay);
            return;
        }

        state.ticksRemaining--;
        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        if (time % ULT_PARTICLE_INTERVAL == 0) {
            // crack wall + aura → client
            DimensionalRiftTickPayload tickPay = new DimensionalRiftTickPayload(player.getId());
            Set<ServerPlayerEntity> viewers = new HashSet<>();
            PlayerLookup.tracking(world, BlockPos.ofFloored(state.origin)).forEach(viewers::add);
            viewers.add(player);
            viewers.forEach(p -> ServerPlayNetworking.send(p, tickPay));
        }

        // crack collision → stay server-side (it's game logic, not visuals)
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                player.getBoundingBox().expand(ULT_RIFT_RADIUS + 2),
                en -> en.isAlive() && en != player)) {

            if (isNearAnyCrack(e.getPos(), state) || isNearAnyRing(e.getPos(), state)) {
                e.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(ModEffects.FRACTURED),
                        40, 0, false, false, true));
            }
        }
    }

    private boolean isNearAnyCrack(Vec3d pos, FractureState state) {
        for (java.util.List<Vec3d> crack : state.cracks) {
            for (int i = 0; i < crack.size() - 1; i++) {
                if (distanceToSegment(pos, crack.get(i), crack.get(i + 1)) < ULT_CRACK_WIDTH)
                    return true;
            }
        }
        return false;
    }

    private boolean isNearAnyRing(Vec3d pos, FractureState state) {
        for (java.util.List<Vec3d> ring : state.rings) {
            for (int i = 0; i < ring.size(); i++) {
                Vec3d a = ring.get(i);
                Vec3d b = ring.get((i + 1) % ring.size());
                if (distanceToSegment(pos, a, b) < ULT_RING_WIDTH) return true;
            }
        }
        return false;
    }

    private double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d pFlat = new Vec3d(p.x, 0, p.z);
        Vec3d aFlat = new Vec3d(a.x, 0, a.z);
        Vec3d bFlat = new Vec3d(b.x, 0, b.z);
        Vec3d ab = bFlat.subtract(aFlat);
        double len2 = ab.lengthSquared();
        if (len2 < 0.0001) return pFlat.distanceTo(aFlat);
        double t = MathHelper.clamp(pFlat.subtract(aFlat).dotProduct(ab) / len2, 0, 1);
        return pFlat.distanceTo(aFlat.add(ab.multiply(t)));
    }

    private Vec3d snapToGround(Vec3d pos, ServerWorld world) {
        int x = (int) Math.floor(pos.x), z = (int) Math.floor(pos.z), baseY = (int) Math.floor(pos.y);
        int bestY = baseY;
        boolean found = false;

        for (int dy = 0; dy <= SEARCH_DOWN; dy++) {
            int y = baseY - dy;
            if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                bestY = y + 1; found = true; break;
            }
        }
        if (!found) {
            for (int dy = 1; dy <= SEARCH_UP; dy++) {
                int y = baseY + dy;
                if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                    if ((y - baseY) <= MAX_STEP_UP) { bestY = y + 1; found = true; }
                    break;
                }
            }
        }
        if (!found) return pos;

        double newY = bestY + 0.05;
        double delta = newY - pos.y;
        if (delta > MAX_STEP_UP || delta < -MAX_STEP_DOWN) return pos;
        return new Vec3d(pos.x, newY, pos.z);
    }

    private boolean isSolidGround(ServerWorld world, int x, int y, int z) {
        BlockPos bp = new BlockPos(x, y, z);
        return world.getBlockState(bp).isSolidBlock(world, bp);
    }

    private boolean isAirAbove(ServerWorld world, int x, int y, int z) {
        return world.getBlockState(new BlockPos(x, y + 1, z)).isAir();
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override public String getName() { return Text.translatable("power.loopypowers.interdimensional.name").getString(); }

    @Override public String getPassiveName()   { return Text.translatable("power.loopypowers.interdimensional.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.interdimensional.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.interdimensional.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.interdimensional.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 22_000; }
    @Override public long getSecondaryCooldownMs() { return 17_000; }
    @Override public long getUltimateCooldownMs()  { return 330_000; }

    @Override public String getOverviewDescription()  { return Text.translatable("power.loopypowers.interdimensional.description.overview").getString(); }
    @Override public String getPassiveDescription()   { return Text.translatable("power.loopypowers.interdimensional.description.passive").getString(); }
    @Override public String getPrimaryDescription()   { return Text.translatable("power.loopypowers.interdimensional.description.primary").getString(); }
    @Override public String getSecondaryDescription() { return Text.translatable("power.loopypowers.interdimensional.description.secondary").getString(); }
    @Override public String getUltimateDescription()  { return Text.translatable("power.loopypowers.interdimensional.description.ultimate").getString(); }
}
