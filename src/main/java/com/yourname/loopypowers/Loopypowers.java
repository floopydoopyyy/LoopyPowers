package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.generation.ModOreGeneration;
import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PlayerDataStore;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.AbilityPackets;
import com.yourname.loopypowers.power.*;
import com.yourname.loopypowers.ritual.RitualManager;
import com.yourname.loopypowers.sound.ModSounds;
import com.yourname.loopypowers.command.PowerCommand;
import com.yourname.loopypowers.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Loopypowers implements ModInitializer {

    public static final String MOD_ID = "loopypowers";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        registerSystems();
        LOGGER.info("Loopypowers loaded!");
    }

    /* ============================================================
       SYSTEM REGISTRATION
       ============================================================ */

    private void registerSystems() {
        PowerCommand.register();
        AbilityPackets.registerServer();
        ModEntities.init();
        ModSounds.register();
        ModItems.register();
        ModBlocks.init();
        ModDamageTypes.init();
        ModEffects.register();
        registerPlayerEvents();
        registerCombatEvents();
        registerTickEvents();
        ModOreGeneration.generateOres();
    }

    /* ============================================================
       PLAYER LIFECYCLE EVENTS
       ============================================================ */

    private void registerPlayerEvents() {

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            // Clear any stale in-memory state before loading this world's data.
            // Guards against leftover maps if a previous disconnect was unclean.
            PowerManager.clearPlayerState(player);
            PlayerDataStore.load(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerDataStore.save(player);
            // Let the power clean up its own static maps (entities, state machines, etc.)
            Power disconnectPower = PowerManager.getPower(player);
            if (disconnectPower != null) disconnectPower.onRemove(player);
            // Clear all in-memory state so nothing leaks to the next world.
            PowerManager.clearPlayerState(player);
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            Power oldPower = PowerManager.getPower(oldPlayer);
            if (oldPower != null) {
                PowerManager.setPower(newPlayer, oldPower, true);
            }

            PowerManager.setLevel(newPlayer, PowerManager.getLevel(oldPlayer));
            PowerManager.copyCooldowns(oldPlayer, newPlayer);

            double cdMult = PowerManager.getPlayerCooldownMultiplier(oldPlayer);
            PowerManager.setPlayerCooldownMultiplier(newPlayer, cdMult);

            PassiveManager.setPassiveState(newPlayer, PassiveManager.isEnabled(oldPlayer));

            PlayerDataStore.save(newPlayer);
        });

        // death hook
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof ServerPlayerEntity sp) {
                Power power = PowerManager.getPower(sp);
                if (power != null) {
                    power.onDeath(sp);
                }
            }
        });
    }

    /* ============================================================
       COMBAT EVENTS
       ============================================================ */

    private void registerCombatEvents() {
        registerMeleeHitCallback();
        registerDamageHook();
    }

    private void registerMeleeHitCallback() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target)) return ActionResult.PASS;

            Power power = PowerManager.getPower(sp);

            if (power != null) power.onHit(sp, target);

            return ActionResult.PASS;
        });
    }

    // --- HELPER METHODS for 1.21.1 bullshit ---
    private static boolean hasCustomEffect(LivingEntity entity, net.minecraft.entity.effect.StatusEffect effect) {
        for (net.minecraft.entity.effect.StatusEffectInstance instance : entity.getStatusEffects()) {
            if (instance.getEffectType().value() == effect) {
                return true;
            }
        }
        return false;
    }

    private static void removeCustomEffect(LivingEntity entity, net.minecraft.entity.effect.StatusEffect effect) {
        net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> toRemove = null;
        for (net.minecraft.entity.effect.StatusEffectInstance instance : entity.getStatusEffects()) {
            if (instance.getEffectType().value() == effect) {
                toRemove = instance.getEffectType();
                break;
            }
        }
        if (toRemove != null) {
            entity.removeStatusEffect(toRemove);
        }
    }
    // -----------------------------------------------------------

    private void registerDamageHook() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            // -- GLOBAL EVENTS --
            // keep displace immunity at top - to fast fail
            if (hasCustomEffect(victim, ModEffects.DISPLACED)) return false;
            if (source.getAttacker() instanceof LivingEntity attacker && hasCustomEffect(attacker, ModEffects.DISPLACED)) {
                return false;
            }

            // fall damage immunity (Braced)
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) {
                if (hasCustomEffect(victim, ModEffects.BRACED)) {
                    removeCustomEffect(victim, ModEffects.BRACED);
                    if (victim.getWorld() instanceof ServerWorld w) {
                        w.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_WOOL_FALL, net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
                        w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, victim.getX(), victim.getY(), victim.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                    }
                    return false;
                }
            }

            // ── ATTACKER-SIDE ─────────────────────────────────────────────────

            if (PsychicPower.onDamageGlobal(victim, source)) return false;

            if (FortunePower.tryAdjustFortuneDamage(victim, source, amount)) return false;

            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                Power attackerPower = PowerManager.getPower(attacker);

                // Check dynamic attacker hook
                if (attackerPower != null) {
                    if (!attackerPower.onAttack(attacker, victim, source, amount)) {
                        return false;
                    }
                }
            }

            // ── VICTIM-SIDE ───────────────────────────────────────────────────

            if (!(victim instanceof ServerPlayerEntity victimPlayer)) return true;
            Power victimPower = PowerManager.getPower(victimPlayer);

            // Check dynamic victim hook
            if (victimPower != null) {
                return victimPower.onDamaged(victimPlayer, source, amount);
            }

            return true;
        });
    }

    /* ============================================================
       SERVER TICK
       ============================================================ */

    private void registerTickEvents() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        RitualManager.tick(server);

        for (ServerWorld world : server.getWorlds()) {
            FortunePower.tickHousesWorld(world);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayerEntity player) {
        CooldownUI.tick(player);

        Power power = PowerManager.getPower(player);
        if (power != null) {
            power.onTick(player);
        }
    }
}