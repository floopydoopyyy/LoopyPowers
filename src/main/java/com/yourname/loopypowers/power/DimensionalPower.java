package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.entity.DisplaceEntity;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;
import java.util.*;

public class DimensionalPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
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

    // PASSIVE
    private static final double BASE_PHASE_CHANCE = 0.0005; // chance per tick
    private static final double DAMAGE_CHANCE_GAIN = 0.002; // amount chance is increased per hit
    private static final double MAX_PHASE_CHANCE = 0.25;   // cap
    private static final int FLICKER_CYCLE = 8;     // total loop length
    private static final int FLICKER_ON_TIME = 3;   // how long invisible per cycle

    private static final float SITUATIONAL_DIMENSION_CHANCE = 0.25f; // chance to adapt to specific events when flickering
    private static final float SILLY_EXIT_CHANCE = 0.03f; // chance to bring something stupid back

    // PRIMARY
    private static final int PHASE_SHIFT_DURATION = 55;

    private static final float ENTRY_SOUND_VOL = 0.7f;
    private static final float ENTRY_SOUND_PITCH = 1.3f;
    private static final float EXIT_SOUND_VOL = 0.9f;
    private static final float EXIT_SOUND_PITCH = 0.8f;

    private static final int TRAIL_INTERVAL = 3;
    private static final int EXIT_BURST_COUNT_MULT = 2;
    private static final double EXIT_BURST_SPREAD = 1.2;
    private static final double EXIT_BURST_SPEED = 0.08;

    private static final double EXIT_DAMAGE_RADIUS = 3.5;
    private static final float EXIT_DAMAGE = 16.5f;
    private static final double EXIT_PULL_STRENGTH = 0.45;
    private static final double EXIT_KNOCKBACK_STRENGTH = 0.8;
    private static final double EXIT_VERTICAL_BOOST = 0.15;

    /* ============================================================
       ESSENTIAL
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("int_")); // Cleanup legacy string tags just in case
        PLAYER_STATES.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        DimensionalState state = PLAYER_STATES.remove(player.getUuid());

        // Return them to survival if they were trapped in spectator mode by the ability
        if (state != null && state.phaseShiftTicks > 0 && player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
            player.changeGameMode(state.prevMode);
        }

        player.getCommandTags().removeIf(tag -> tag.startsWith("int_"));

        // Clear active ultimate fractures to prevent permanent slow zones
        ACTIVE_FRACTURES.remove(player.getUuid());

        // Sweep for entities that were displaced by the secondary
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

                // Free entities stuck inside the ultimate
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(150), LivingEntity::isAlive)) {
                    // Fix: Dynamically convert to RegistryEntry if ModEffects still returns raw StatusEffect
                    e.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.FRACTURED));
                    e.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.DISPLACED));
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

        ServerWorld world = player.getServerWorld();
        DimensionalState state = getState(player);

        handlePassive(player, state, world);
        handlePhaseShift(player, state, world);
        handleFracture(player); // ultimate tick

        // Process Displacements owned by this specific player without scanning the world
        Map<UUID, Integer> displaced = ACTIVE_DISPLACEMENTS.get(player.getUuid());
        if (displaced != null && !displaced.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = displaced.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                Entity ent = world.getEntity(entry.getKey());

                if (!(ent instanceof LivingEntity target) || !target.isAlive()) {
                    it.remove();
                    continue;
                }

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

        // Fast fail if immune from passive
        if (state.immuneTicks > 0) return false;

        // Increase phase chance on hit
        state.phaseChance = Math.min(MAX_PHASE_CHANCE, state.phaseChance + DAMAGE_CHANCE_GAIN);
        return true;
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private void handlePassive(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        if (state.phaseShiftTicks > 0) return;
        if (!PassiveManager.isEnabled(player)) return;

        // handle flicker first if phasing
        if (state.phaseTicks > 0) {
            handleFlicker(player, state, world);
            return;
        }

        // Then tick cooldown
        if (state.passiveCdTicks > 0) {
            state.passiveCdTicks--;
            return;
        }

        // Roll
        if (world.random.nextDouble() < state.phaseChance) {
            startPassivePhase(player, state);

            // Reset chance after proc
            state.phaseChance = BASE_PHASE_CHANCE;

            // Start cooldown
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

        // particles
        if (cyclePos == 0) {

            DustParticleEffect darkBlue = new DustParticleEffect(
                    new Vector3f(0.05f, 0.1f, 0.4f),
                    1.4f
            );

            DustParticleEffect midBlue = new DustParticleEffect(
                    new Vector3f(0.2f, 0.4f, 1.0f),
                    1.2f
            );

            DustParticleEffect brightBlue = new DustParticleEffect(
                    new Vector3f(0.6f, 0.8f, 1.0f),
                    0.9f
            );

            double x = player.getX();
            double y = player.getBodyY(0.5);
            double z = player.getZ();

            world.spawnParticles(brightBlue, x, y, z, 12, 0.5, 0.7, 0.5, 0.03);
            world.spawnParticles(midBlue, x, y, z, 8, 0.35, 0.55, 0.35, 0.02);

            if (world.random.nextFloat() < 0.4f) {
                world.spawnParticles(darkBlue, x, y, z, 3, 0.3, 0.4, 0.3, 0.015);
            }

            var flickerSound = switch (world.random.nextInt(3)) {
                case 0 -> ModSounds.FLICKER;
                case 1 -> ModSounds.FLICKER2;
                default -> ModSounds.FLICKER3;
            };

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    Registries.SOUND_EVENT.getEntry(flickerSound),
                    player.getSoundCategory(), 0.5f, 1.0f);
        }

        // flicker
        if (flickerActive) {

            // yeah
            RenderPackets.hidePlayerFromOthers(player, 1);

            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.INVISIBILITY,
                    2, //
                    0,
                    true,
                    false
            ));
        }

        // damage immunity
        state.immuneTicks = state.phaseTicks;

        state.phaseTicks--;

        // if the tag just expired and the player is fully returning
        if (state.phaseTicks <= 0) {
            state.immuneTicks = 0;
            triggerSituationalFlicker(player, world);
        }
    }

    private void triggerSituationalFlicker(ServerPlayerEntity player, ServerWorld world) {
        // rng check
        if (world.random.nextFloat() > SITUATIONAL_DIMENSION_CHANCE) return;

        // prioritize fall damage -> drowning -> freezing -> levitation -> blindness -> poison/wither -> starvation -> fire -> slowness
        if (player.fallDistance > 5.0f) {
            // zero-g void
            player.fallDistance = 0;
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 40, 0, false, false, false));

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, player.getSoundCategory(), 1.0f, 0.5f);
            world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);

        } else if (player.getAir() <= 0) {
            // air pocket
            player.setAir(player.getMaxAir());

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_BREATH, player.getSoundCategory(), 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getEyeY(), player.getZ(), 40, 0.4, 0.4, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY(), player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);

        } else if (player.getFrozenTicks() > 0) {
            // fire world
            player.setFrozenTicks(0);

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, player.getSoundCategory(), 0.8f, 1.0f);
            world.spawnParticles(ParticleTypes.LAVA, player.getX(), player.getBodyY(0.5), player.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);

        } else if (player.hasStatusEffect(StatusEffects.LEVITATION)) {
            // heavy gravity world
            player.removeStatusEffect(StatusEffects.LEVITATION);
            player.setVelocity(player.getVelocity().x, -1.5, player.getVelocity().z);
            player.velocityModified = true;

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_ANVIL_LAND, player.getSoundCategory(), 0.8f, 0.8f);
            world.spawnParticles(ParticleTypes.ASH, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.4, 0.6, 0.4, 0.1);

        } else if (player.hasStatusEffect(StatusEffects.BLINDNESS) || player.hasStatusEffect(StatusEffects.DARKNESS)) {
            // light world
            player.removeStatusEffect(StatusEffects.BLINDNESS);
            player.removeStatusEffect(StatusEffects.DARKNESS);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 100, 0, false, false, false));

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, player.getSoundCategory(), 1.0f, 1.5f);
            world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getEyeY(), player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);

        } else if (player.hasStatusEffect(StatusEffects.POISON) || player.hasStatusEffect(StatusEffects.WITHER)) {
            // idk cleanse
            player.removeStatusEffect(StatusEffects.POISON);
            player.removeStatusEffect(StatusEffects.WITHER);

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, player.getSoundCategory(), 1.2f, 1.0f);
            world.spawnParticles(ParticleTypes.GLOW, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.SCRAPE, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.1);

        } else if (player.getHungerManager().getFoodLevel() <= 6) {
            // food world
            player.getHungerManager().setFoodLevel(8);
            player.getHungerManager().setSaturationLevel(4.0f);

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_BURP, player.getSoundCategory(), 1.0f, 0.9f);
            world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getEyeY(), player.getZ(), 15, 0.2, 0.2, 0.2, 0.05);

        } else if (player.isOnFire()) {
            // water dimension
            player.extinguish();

            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_SPLASH, player.getSoundCategory(), 1.0f, 1.2f);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, player.getSoundCategory(), 0.6f, 1.0f);

            world.spawnParticles(ParticleTypes.SPLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 50, 0.4, 0.6, 0.4, 0.1);
            world.spawnParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.4, 0.6, 0.4, 0.1);

        } else if (player.hasStatusEffect(StatusEffects.SLOWNESS)) {
            // speed world
            StatusEffectInstance slowness = player.getStatusEffect(StatusEffects.SLOWNESS);
            if (slowness != null && slowness.getAmplifier() >= 1) { // only trigger on slowness II or worse
                player.removeStatusEffect(StatusEffects.SLOWNESS);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 1, false, false, false));

                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_MINECART_RIDING, player.getSoundCategory(), 0.5f, 1.5f);
                world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(0.2), player.getZ(), 25, 0.3, 0.1, 0.3, 0.1);
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

        // clear passive
        state.phaseTicks = 0;
        state.immuneTicks = 0;

        state.phaseShiftTicks = PHASE_SHIFT_DURATION;
        state.prevMode = player.interactionManager.getGameMode();

        // switch to spectator
        player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);

        spawnPhaseParticles(world, player);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(), ENTRY_SOUND_VOL, ENTRY_SOUND_PITCH);
    }

    private void handlePhaseShift(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {
        if (state.phaseShiftTicks <= 0) return;

        state.phaseShiftTicks--;

        if (state.phaseShiftTicks <= 0) {
            // if ended, exit
            if (player.interactionManager.getGameMode() == net.minecraft.world.GameMode.SPECTATOR) {
                exitPhaseShift(player, state, world);
            }
            return;
        }

        // particles
        if (world.getTime() % TRAIL_INTERVAL == 0) {

            DustParticleEffect midBlue = new DustParticleEffect(
                    new Vector3f(0.2f, 0.4f, 1.0f),
                    1.0f
            );

            world.spawnParticles(midBlue,
                    player.getX(),
                    player.getBodyY(0.5),
                    player.getZ(),
                    2,
                    0.15, 0.2, 0.15,
                    0.01
            );
        }
    }

    private void exitPhaseShift(ServerPlayerEntity player, DimensionalState state, ServerWorld world) {

        // back to previous gamemode
        player.changeGameMode(state.prevMode);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), EXIT_SOUND_VOL, EXIT_SOUND_PITCH);

        // damage nearby entities
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(EXIT_DAMAGE_RADIUS),
                en -> en.isAlive() && en != player)) {

            // damage
            e.damage(ModDamageTypes.phaseBurst(world, player), EXIT_DAMAGE);

            // direction vectors
            Vec3d fromPlayer = e.getPos().subtract(player.getPos());
            double distance = fromPlayer.length();

            if (distance < 0.001) continue;

            Vec3d dir = fromPlayer.normalize();

            // slight pull
            Vec3d pull = dir.multiply(-EXIT_PULL_STRENGTH);

            Vec3d knockback = dir.multiply(EXIT_KNOCKBACK_STRENGTH);

            // knockback push
            Vec3d finalVelocity = pull.add(knockback).add(0, EXIT_VERTICAL_BOOST, 0);

            e.addVelocity(finalVelocity.x, finalVelocity.y, finalVelocity.z);
            e.velocityModified = true;
        }

        // exit particles
        DustParticleEffect darkBlue = new DustParticleEffect(
                new Vector3f(0.05f, 0.1f, 0.4f),
                1.4f
        );

        DustParticleEffect midBlue = new DustParticleEffect(
                new Vector3f(0.2f, 0.4f, 1.0f),
                1.2f
        );

        DustParticleEffect brightBlue = new DustParticleEffect(
                new Vector3f(0.6f, 0.8f, 1.0f),
                1.1f
        );

        double x = player.getX();
        double y = player.getBodyY(0.5);
        double z = player.getZ();

        world.spawnParticles(brightBlue,
                x, y, z,
                25 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD, EXIT_BURST_SPREAD, EXIT_BURST_SPREAD,
                EXIT_BURST_SPEED
        );

        world.spawnParticles(midBlue,
                x, y, z,
                18 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8, EXIT_BURST_SPREAD * 0.8,
                EXIT_BURST_SPEED * 0.8
        );

        world.spawnParticles(darkBlue,
                x, y, z,
                8 * EXIT_BURST_COUNT_MULT,
                EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6, EXIT_BURST_SPREAD * 0.6,
                EXIT_BURST_SPEED * 0.6
        );

        CameraShake.shakeNearby(player, 5, 10, 0.6f);

        // -- easter egg --
        // brought something stupid back
        if (world.random.nextFloat() < SILLY_EXIT_CHANCE) {
            int gag = world.random.nextInt(9);
            switch (gag) {
                case 0 -> {
                    // bring a strider
                    net.minecraft.entity.passive.StriderEntity strider = net.minecraft.entity.EntityType.STRIDER.create(world);
                    if (strider != null) {
                        strider.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0);
                        world.spawnEntity(strider);
                        world.spawnParticles(ParticleTypes.LAVA, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 1 -> {
                    // bring a cat
                    net.minecraft.entity.passive.CatEntity cat = net.minecraft.entity.EntityType.CAT.create(world);
                    if (cat != null) {
                        cat.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0);
                        world.spawnEntity(cat);
                        world.spawnParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 2 -> {
                    // bring hamuel
                    net.minecraft.entity.passive.PigEntity pig = net.minecraft.entity.EntityType.PIG.create(world);
                    if (pig != null) {
                        pig.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0);
                        pig.setCustomName(net.minecraft.text.Text.translatable("entity.loopypowers.hamuel"));
                        world.spawnEntity(pig);
                        world.spawnParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 3 -> {
                    // bring woolliam
                    net.minecraft.entity.passive.SheepEntity sheep = net.minecraft.entity.EntityType.SHEEP.create(world);
                    if (sheep != null) {
                        sheep.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0);
                        sheep.setCustomName(net.minecraft.text.Text.translatable("entity.loopypowers.woolliam"));
                        world.spawnEntity(sheep);
                        world.spawnParticles(ParticleTypes.POOF, x, y + 0.5, z, 5, 0.2, 0.2, 0.2, 0.02);
                    }
                }
                case 4 -> {
                    // bring 3 cod
                    for (int c = 0; c < 3; c++) {
                        net.minecraft.entity.passive.CodEntity cod = net.minecraft.entity.EntityType.COD.create(world);
                        if (cod != null) {
                            cod.refreshPositionAndAngles(x, y, z, world.random.nextFloat() * 360f, 0);
                            world.spawnEntity(cod);
                        }
                    }
                    world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.5, z, 15, 0.3, 0.3, 0.3, 0.05);
                }
                case 5 -> {
                    // any leather armour being put on an empty slot
                    net.minecraft.entity.EquipmentSlot[] slots = {
                            net.minecraft.entity.EquipmentSlot.HEAD,
                            net.minecraft.entity.EquipmentSlot.CHEST,
                            net.minecraft.entity.EquipmentSlot.LEGS,
                            net.minecraft.entity.EquipmentSlot.FEET
                    };
                    net.minecraft.item.Item[] leathers = {
                            net.minecraft.item.Items.LEATHER_HELMET,
                            net.minecraft.item.Items.LEATHER_CHESTPLATE,
                            net.minecraft.item.Items.LEATHER_LEGGINGS,
                            net.minecraft.item.Items.LEATHER_BOOTS
                    };

                    int startIdx = world.random.nextInt(4);
                    for (int i = 0; i < 4; i++) {
                        int idx = (startIdx + i) % 4;
                        if (player.getEquippedStack(slots[idx]).isEmpty()) {
                            player.equipStack(slots[idx], new net.minecraft.item.ItemStack(leathers[idx]));
                            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, player.getSoundCategory(), 1.0f, 1.0f);
                            break;
                        }
                    }
                }
                case 6 -> {
                    // a steve head appearing on head slot
                    if (player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty()) {
                        player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, new net.minecraft.item.ItemStack(net.minecraft.item.Items.PLAYER_HEAD));
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, player.getSoundCategory(), 1.0f, 1.0f);
                    }
                }
                case 7 -> {
                    // a few snowballs being shot out
                    int balls = 3 + world.random.nextInt(3);
                    for (int i = 0; i < balls; i++) {
                        net.minecraft.entity.projectile.thrown.SnowballEntity snowball = new net.minecraft.entity.projectile.thrown.SnowballEntity(world, player);
                        snowball.setPosition(x, y + 1.0, z);
                        snowball.setVelocity((world.random.nextDouble() - 0.5) * 1.5, world.random.nextDouble(), (world.random.nextDouble() - 0.5) * 1.5);
                        world.spawnEntity(snowball);
                    }
                    world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, player.getSoundCategory(), 1.0f, 1.0f);
                }
                case 8 -> {
                    // a few falling sand blocks
                    int sands = 2 + world.random.nextInt(3);
                    for (int i = 0; i < sands; i++) {
                        net.minecraft.entity.FallingBlockEntity sand = net.minecraft.entity.FallingBlockEntity.spawnFromBlock(
                                world,
                                player.getBlockPos().up(2).add(world.random.nextInt(2) - 1, 0, world.random.nextInt(2) - 1),
                                net.minecraft.block.Blocks.SAND.getDefaultState()
                        );
                        sand.setVelocity((world.random.nextDouble() - 0.5) * 0.4, 0.2, (world.random.nextDouble() - 0.5) * 0.4);
                    }
                }
            }
        }
    }

    private void spawnPhaseParticles(ServerWorld world, ServerPlayerEntity player) {

        DustParticleEffect darkBlue = new DustParticleEffect(
                new Vector3f(0.05f, 0.1f, 0.4f),
                1.4f
        );

        DustParticleEffect midBlue = new DustParticleEffect(
                new Vector3f(0.2f, 0.4f, 1.0f),
                1.2f
        );

        DustParticleEffect brightBlue = new DustParticleEffect(
                new Vector3f(0.6f, 0.8f, 1.0f),
                1.0f
        );

        double x = player.getX();
        double y = player.getBodyY(0.5);
        double z = player.getZ();

        world.spawnParticles(brightBlue, x, y, z, 15, 0.6, 0.8, 0.6, 0.04);
        world.spawnParticles(midBlue, x, y, z, 10, 0.4, 0.6, 0.4, 0.03);

        if (world.random.nextFloat() < 0.5f) {
            world.spawnParticles(darkBlue, x, y, z, 4, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // direction
        Vec3d look = player.getRotationVec(1.0f);

        // spawn projectile
        DisplaceEntity bolt = new DisplaceEntity(
                ModEntities.DISPLACE_ENTITY, world);

        bolt.setOwner(player);

        bolt.setPos(
                player.getX(),
                player.getEyeY() - 0.1,
                player.getZ()
        );

        // slow moving
        bolt.setVelocity(look.multiply(0.6));

        world.spawnEntity(bolt);

        player.swingHand(Hand.MAIN_HAND, true);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                player.getSoundCategory(), 0.6f, 1.4f);
    }

    // Suppressed warning since this is called remotely via the projectile entity
    @SuppressWarnings("unused")
    public static void applyDisplace(ServerPlayerEntity caster, LivingEntity target, int durationTicks) {
        ACTIVE_DISPLACEMENTS.computeIfAbsent(caster.getUuid(), k -> new HashMap<>()).put(target.getUuid(), durationTicks);
    }

    private void tickDisplaceTarget(LivingEntity entity, ServerWorld world) {
        // set velocity to 0
        entity.setVelocity(Vec3d.ZERO);
        entity.velocityModified = true;
        entity.fallDistance = 0; // stops fall damage accumulating when froze

        // Lock position using packets
        if (entity instanceof ServerPlayerEntity displacedPlayer) {
            displacedPlayer.networkHandler.sendPacket(
                    new net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket(
                            displacedPlayer.getX(),
                            displacedPlayer.getY(),
                            displacedPlayer.getZ(),
                            displacedPlayer.getYaw(),
                            displacedPlayer.getPitch(),
                            java.util.Set.of(
                                    net.minecraft.network.packet.s2c.play.PositionFlag.X_ROT,
                                    net.minecraft.network.packet.s2c.play.PositionFlag.Y_ROT
                            ),
                            0
                    )
            );

            // stop mining
            displacedPlayer.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.MINING_FATIGUE, 5, 255, true, false, false));

            // stop actions
            if (!displacedPlayer.getMainHandStack().isEmpty()) { // set cooldowns on held item
                displacedPlayer.getItemCooldownManager().set(displacedPlayer.getMainHandStack().getItem(), 5);
            }
            displacedPlayer.stopUsingItem(); // force usables to not be used
        }

        // Invisibility
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.INVISIBILITY, 5, 0, true, false, false));
        // stop damage
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.WEAKNESS, 5, 255, true, false, false));
        entity.addStatusEffect(new StatusEffectInstance(
                StatusEffects.RESISTANCE, 5, 255, true, false, false));

        // disable mob ai
        if (entity instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }

        // fx
        DustParticleEffect darkBlue  = new DustParticleEffect(new Vector3f(0.05f, 0.1f, 0.4f),  1.4f);
        DustParticleEffect midBlue   = new DustParticleEffect(new Vector3f(0.2f,  0.4f, 1.0f),  1.2f);
        DustParticleEffect brightBlue = new DustParticleEffect(new Vector3f(0.6f, 0.8f, 1.0f),  0.9f);

        double x = entity.getX();
        double y = entity.getBodyY(0.5);
        double z = entity.getZ();

        world.spawnParticles(brightBlue,  x, y, z, 6, 0.5, 0.7, 0.5, 0.03);
        world.spawnParticles(midBlue,     x, y, z, 4, 0.35, 0.55, 0.35, 0.02);

        if (world.random.nextFloat() < 0.4f) {
            world.spawnParticles(darkBlue, x, y, z, 2, 0.3, 0.4, 0.3, 0.015);
        }
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    // ── Ultimate — Reality Fracture ─────────────────────────────
    private static final int    ULT_DURATION             = 300;  // ticks cracks last
    private static final double ULT_RIFT_RADIUS          = 26.0; // how far cracks spread from rift
    private static final int    ULT_CRACK_COUNT          = 15;   // number of crack lines
    private static final int    ULT_CRACK_SEGMENTS       = 8;    // jagged segments per crack line
    private static final double ULT_CRACK_SEGMENT_LENGTH = 2.3;  // length of each segment
    private static final double ULT_CRACK_JAGGED_ANGLE   = 0.6;  // max angle deviation (radians)
    private static final double ULT_CRACK_WIDTH          = 0.9;  // proximity to crack that triggers damage
    private static final double ULT_WALL_PARTICLE_HEIGHT = 3.0;  // how tall the particle wall rises
    private static final int    ULT_WALL_PARTICLE_DENSITY = 1;   // particles per crack point per interval
    private static final int    ULT_PARTICLE_INTERVAL    = 8;    // ticks between aura particle spawns
    private static final int    ULT_RING_COUNT        = 2; // amount of rings
    private static final double ULT_RING_SPACING      = 9.0; // space between them
    private static final int    ULT_RING_POINTS       = 20; // points in each circle
    private static final double ULT_RING_WIDTH        = 1.0; // yeah
    private static final double MAX_STEP_UP = 1.25;   // how much it can climb
    private static final double MAX_STEP_DOWN = 2.5;  // how much it can drop
    private static final int SEARCH_DOWN = 6;         // how far down to search
    private static final int SEARCH_UP = 2;           // how upward it can correct

    private static class FractureState {
        java.util.List<java.util.List<Vec3d>> cracks = new java.util.ArrayList<>();
        java.util.List<java.util.List<Vec3d>> rings = new java.util.ArrayList<>();
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

        java.util.Random rng = new java.util.Random();
        double angleStep = (Math.PI * 2.0) / ULT_CRACK_COUNT;

        // ── Build radial cracks ─────────────────────────────
        for (int i = 0; i < ULT_CRACK_COUNT; i++) {
            double baseAngle = angleStep * i + (rng.nextDouble() - 0.5) * angleStep * 0.6;
            state.cracks.add(buildCrackLine(origin, baseAngle, rng, world));
        }

        // visuals
        spawnRiftOpeningParticles(world, origin);

        for (int i = 1; i <= ULT_RING_COUNT; i++) {
            double radius = i * ULT_RING_SPACING;
            state.rings.add(buildRing(origin, radius, world)); // pass world
        }

        ACTIVE_FRACTURES.put(player.getUuid(), state);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                player.getSoundCategory(), 1.2f, 0.4f);
    }

    private java.util.List<Vec3d> buildRing(Vec3d center, double radius, ServerWorld world) {
        java.util.List<Vec3d> points = new java.util.ArrayList<>();

        int smoothPoints = ULT_RING_POINTS * 3; // hopefully its smoother

        for (int i = 0; i < smoothPoints; i++) {
            double angle = (Math.PI * 2.0 * i) / smoothPoints;

            Vec3d p = new Vec3d(
                    center.x + Math.cos(angle) * radius,
                    center.y,
                    center.z + Math.sin(angle) * radius
            );

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

            Vec3d nextFlat = current.add(
                    Math.cos(currentAngle) * segLen,
                    0,
                    Math.sin(currentAngle) * segLen
            );

            // interpolate between points
            int steps = 3 + rng.nextInt(3); // 3 sub-steps

            for (int i = 1; i <= steps; i++) {
                double t = i / (double) steps;

                Vec3d interp = new Vec3d(
                        current.x + (nextFlat.x - current.x) * t,
                        current.y,
                        current.z + (nextFlat.z - current.z) * t
                );

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
            return;
        }

        state.ticksRemaining--;

        ServerWorld world = player.getServerWorld();
        long time = world.getTime();

        // WALLS
        if (time % ULT_PARTICLE_INTERVAL == 0) {

            for (java.util.List<Vec3d> crack : state.cracks) {
                spawnCrackWallParticles(world, crack);
            }

            for (java.util.List<Vec3d> ring : state.rings) {
                spawnCrackWallParticles(world, ring);
            }

            if (state.origin != null) {
                spawnRiftAuraParticles(world, state.origin, time);
            }
        }

        // -- COLLISION --
        for (LivingEntity e : world.getEntitiesByClass(
                LivingEntity.class,
                player.getBoundingBox().expand(ULT_RIFT_RADIUS + 2),
                en -> en.isAlive() && en != player)) {

            if (isNearAnyCrack(e.getPos(), state) || isNearAnyRing(e.getPos(), state)) {
                e.addStatusEffect(new StatusEffectInstance(
                        Registries.STATUS_EFFECT.getEntry(ModEffects.FRACTURED),
                        40,
                        0,
                        false, false, true
                ));
            }
        }
    }

    private boolean isNearAnyCrack(Vec3d pos, FractureState state) {
        for (java.util.List<Vec3d> crack : state.cracks) {
            for (int i = 0; i < crack.size() - 1; i++) {
                if (distanceToSegment(pos, crack.get(i), crack.get(i + 1)) < ULT_CRACK_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        // Flatten to XZ
        Vec3d pFlat = new Vec3d(p.x, 0, p.z);
        Vec3d aFlat = new Vec3d(a.x, 0, a.z);
        Vec3d bFlat = new Vec3d(b.x, 0, b.z);

        Vec3d ab = bFlat.subtract(aFlat);
        double len2 = ab.lengthSquared();
        if (len2 < 0.0001) return pFlat.distanceTo(aFlat);

        double t = net.minecraft.util.math.MathHelper.clamp(
                pFlat.subtract(aFlat).dotProduct(ab) / len2, 0, 1);
        Vec3d closest = aFlat.add(ab.multiply(t));
        return pFlat.distanceTo(closest);
    }

    // particles
    private static final DustParticleEffect CRACK_DARK =
            new DustParticleEffect(new Vector3f(0.05f, 0.15f, 0.55f), 1.8f);  // deep blue
    private static final DustParticleEffect CRACK_MID =
            new DustParticleEffect(new Vector3f(0.25f, 0.55f, 1.0f), 1.4f);   // sky blue
    private static final DustParticleEffect CRACK_BRIGHT =
            new DustParticleEffect(new Vector3f(0.75f, 0.92f, 1.0f), 1.1f);   // icy white-blue
    private static final DustParticleEffect RIFT_WHITE =
            new DustParticleEffect(new Vector3f(0.9f, 0.97f, 1.0f), 1.2f);    // pure white-blue

    private void spawnCrackWallParticles(ServerWorld world, java.util.List<Vec3d> crack) {
        for (int i = 0; i < crack.size() - 1; i++) {
            Vec3d a = crack.get(i);
            Vec3d b = crack.get(i + 1);
            int steps = Math.max(1, (int)(a.distanceTo(b) / 0.55));

            for (int s = 0; s <= steps; s++) {
                if (world.random.nextInt(4) != 0) continue;

                double t = (double) s / steps;
                double baseX = a.x + (b.x - a.x) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseZ = a.z + (b.z - a.z) * t + (world.random.nextDouble() - 0.5) * 0.08;
                double baseY = a.y + (b.y - a.y) * t; // interpolate Y along terrain

                double wallHeight = ULT_WALL_PARTICLE_HEIGHT * (0.5 + world.random.nextDouble() * 0.5);

                // bottom crack
                world.spawnParticles(CRACK_DARK,
                        baseX, baseY + 0.05, baseZ,
                        1, 0.03, 0.01, 0.03, 0.003);
                world.spawnParticles(CRACK_MID,
                        baseX, baseY + 0.12, baseZ,
                        1, 0.04, 0.02, 0.04, 0.005);

                // make them rise slightly
                for (int h = 1; h < ULT_WALL_PARTICLE_DENSITY + 2; h++) {
                    double y = baseY + (wallHeight * h / (ULT_WALL_PARTICLE_DENSITY + 2));

                    float heightFraction = (float) h / (ULT_WALL_PARTICLE_DENSITY + 2);
                    DustParticleEffect color = heightFraction < 0.35f ? CRACK_DARK
                            : CRACK_BRIGHT;

                    double driftX = (world.random.nextDouble() - 0.5) * 0.015 * h;
                    double driftZ = (world.random.nextDouble() - 0.5) * 0.015 * h;

                    world.spawnParticles(color,
                            baseX + driftX, y, baseZ + driftZ,
                            1, 0.01, 0.02 + heightFraction * 0.03, 0.01,
                            0.003 + heightFraction * 0.006);
                }

                if (world.random.nextFloat() < 0.06f) {
                    world.spawnParticles(RIFT_WHITE,
                            baseX, baseY + wallHeight * 0.4, baseZ,
                            1, 0.06, 0.08, 0.06, 0.02);
                }

                if (world.random.nextFloat() < 0.04f) {
                    world.spawnParticles(ParticleTypes.END_ROD,
                            baseX, baseY + 0.1, baseZ,
                            1, (world.random.nextDouble() - 0.5) * 0.04,
                            0.06 + world.random.nextDouble() * 0.06,
                            (world.random.nextDouble() - 0.5) * 0.04,
                            0.01);
                }
            }
        }
    }

    private void spawnRiftOpeningParticles(ServerWorld world, Vec3d pos) {
        for (int ring = 0; ring < 3; ring++) {
            double r = 1.5 + ring * 1.8;
            int ringPoints = 16 + ring * 8;
            for (int i = 0; i < ringPoints; i++) {
                double angle = Math.PI * 2.0 * i / ringPoints;
                world.spawnParticles(ring == 0 ? RIFT_WHITE : ring == 1 ? CRACK_BRIGHT : CRACK_MID,
                        pos.x + Math.cos(angle) * r,
                        pos.y + 0.1,
                        pos.z + Math.sin(angle) * r,
                        1,
                        -Math.cos(angle) * (0.2 + ring * 0.05),
                        0.04,
                        -Math.sin(angle) * (0.2 + ring * 0.05),
                        0.015);
            }
        }

        world.spawnParticles(RIFT_WHITE,  pos.x, pos.y + 0.2, pos.z, 25, 0.4, 0.6, 0.4, 0.09);
        world.spawnParticles(CRACK_BRIGHT, pos.x, pos.y + 0.2, pos.z, 18, 0.5, 0.7, 0.5, 0.07);
        world.spawnParticles(CRACK_MID,   pos.x, pos.y + 0.1, pos.z, 12, 0.6, 0.4, 0.6, 0.05);
        world.spawnParticles(CRACK_DARK,  pos.x, pos.y + 0.1, pos.z, 8,  0.7, 0.3, 0.7, 0.04);

        // beam upward
        for (int h = 0; h < 14; h++) {
            double heightFraction = (double) h / 14;
            DustParticleEffect col = heightFraction < 0.3 ? CRACK_DARK
                    : heightFraction < 0.6 ? CRACK_MID
                    : heightFraction < 0.85 ? CRACK_BRIGHT
                    : RIFT_WHITE;

            world.spawnParticles(col,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.4,
                    pos.y + h * 0.45,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.4,
                    1, 0.04, 0.08, 0.04, 0.025);
        }

        for (int i = 0; i < 16; i++) {
            double a = world.random.nextDouble() * Math.PI * 2;
            double r = world.random.nextDouble() * 1.5;
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + Math.cos(a) * r,
                    pos.y + 0.2,
                    pos.z + Math.sin(a) * r,
                    1,
                    Math.cos(a) * 0.04, 0.12 + world.random.nextDouble() * 0.1,
                    Math.sin(a) * 0.04, 0.02);
        }

        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 0.5, pos.z, 3, 0.15, 0.1, 0.15, 0);
    }

    private void spawnRiftAuraParticles(ServerWorld world, Vec3d pos, long time) {
        // 2 rings around centre
        for (int ring = 0; ring < 2; ring++) {
            double speed  = ring == 0 ? 0.09 : -0.06;
            double radius = ring == 0 ? 0.7 : 1.1;
            double angle  = time * speed;
            int points    = ring == 0 ? 3 : 5;

            for (int i = 0; i < points; i++) {
                double a = angle + (i * Math.PI * 2.0 / points);
                double r = radius + Math.sin(time * 0.07 + i) * 0.15;

                world.spawnParticles(ring == 0 ? RIFT_WHITE : CRACK_BRIGHT,
                        pos.x + Math.cos(a) * r,
                        pos.y + 0.2 + Math.sin(time * 0.05 + i) * 0.12,
                        pos.z + Math.sin(a) * r,
                        1, 0, 0.02, 0, 0.008);
            }
        }

        // occasional star
        if (time % 5 == 0) {
            world.spawnParticles(ParticleTypes.END_ROD,
                    pos.x + (world.random.nextDouble() - 0.5) * 0.2,
                    pos.y + 0.15,
                    pos.z + (world.random.nextDouble() - 0.5) * 0.2,
                    1, 0, 0.07 + world.random.nextDouble() * 0.05, 0, 0.01);
        }

        if (time % 8 == 0) {
            world.spawnParticles(RIFT_WHITE, pos.x, pos.y + 0.4, pos.z,
                    1, 0.12, 0.12, 0.12, 0.025);
        }
    }

    private boolean isNearAnyRing(Vec3d pos, FractureState state) {
        for (java.util.List<Vec3d> ring : state.rings) {
            for (int i = 0; i < ring.size(); i++) {
                Vec3d a = ring.get(i);
                Vec3d b = ring.get((i + 1) % ring.size());

                if (distanceToSegment(pos, a, b) < ULT_RING_WIDTH) {
                    return true;
                }
            }
        }
        return false;
    }

    private Vec3d snapToGround(Vec3d pos, ServerWorld world) {
        if (world == null) return pos;

        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int baseY = (int) Math.floor(pos.y);

        int bestY = baseY;
        boolean found = false;

        for (int dy = 0; dy <= SEARCH_DOWN; dy++) {
            int y = baseY - dy;

            if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                bestY = y + 1;
                found = true;
                break;
            }
        }

        if (!found) {
            for (int dy = 1; dy <= SEARCH_UP; dy++) {
                int y = baseY + dy;

                if (isSolidGround(world, x, y, z) && isAirAbove(world, x, y, z)) {
                    if ((y - baseY) <= MAX_STEP_UP) {
                        bestY = y + 1;
                        found = true;
                    }
                    break;
                }
            }
        }

        if (!found) return pos;

        double newY = bestY + 0.05;

        // 🚫 Prevent massive jumps (this is the KEY part)
        double delta = newY - pos.y;

        if (delta > MAX_STEP_UP || delta < -MAX_STEP_DOWN) {
            return pos;
        }

        return new Vec3d(pos.x, newY, pos.z);
    }

    private boolean isSolidGround(ServerWorld world, int x, int y, int z) {
        return world.getBlockState(new net.minecraft.util.math.BlockPos(x, y, z))
                .isSolidBlock(world, new net.minecraft.util.math.BlockPos(x, y, z));
    }

    private boolean isAirAbove(ServerWorld world, int x, int y, int z) {
        return world.getBlockState(new net.minecraft.util.math.BlockPos(x, y + 1, z)).isAir();
    }

    /* ============================================================
       METADATA
       ============================================================ */

    @Override public String getName() { return "power.loopypowers.interdimensional.name"; }

    @Override public String getPassiveName()   { return Text.translatable("power.loopypowers.interdimensional.passive_name").getString(); }
    @Override public String getPrimaryName()   { return Text.translatable("power.loopypowers.interdimensional.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.interdimensional.secondary_name").getString(); }
    @Override public String getUltimateName()  { return Text.translatable("power.loopypowers.interdimensional.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 22_000; }
    @Override public long getSecondaryCooldownMs() { return 17_000; }
    @Override public long getUltimateCooldownMs()  { return 330_000; }

    @Override
    public String getOverviewDescription() {
        return Text.translatable("power.loopypowers.interdimensional.description.overview").getString();
    }

    @Override
    public String getPassiveDescription() {
        return Text.translatable("power.loopypowers.interdimensional.description.passive").getString();
    }

    @Override
    public String getPrimaryDescription() {
        return Text.translatable("power.loopypowers.interdimensional.description.primary").getString();
    }

    @Override
    public String getSecondaryDescription() {
        return Text.translatable("power.loopypowers.interdimensional.description.secondary").getString();
    }

    @Override
    public String getUltimateDescription() {
        return Text.translatable("power.loopypowers.interdimensional.description.ultimate").getString();
    }
}