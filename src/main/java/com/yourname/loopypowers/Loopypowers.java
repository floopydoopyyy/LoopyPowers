package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.generation.ModOreGeneration;
import com.yourname.loopypowers.item.ModItems;
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
            PlayerDataStore.load(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerDataStore.save(player);
            // OPTIMIZATION: Prevent memory leaks on disconnect
            PowerManager.clearPlayerState(player);
        });

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            Power oldPower = PowerManager.getPower(oldPlayer);
            if (oldPower != null) {
                PowerManager.setPower(newPlayer, oldPower);
            }

            int level = PowerManager.getLevel(oldPlayer);
            PowerManager.setLevel(newPlayer, level);
            PowerManager.copyCooldowns(oldPlayer, newPlayer);
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

    private void registerDamageHook() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            // -- GLOBAL EVENTS --
            // keep displace immunity at top - to fast fail
            if (victim.hasStatusEffect(ModEffects.DISPLACED)) return false;
            if (source.getAttacker() instanceof LivingEntity attacker && attacker.hasStatusEffect(ModEffects.DISPLACED)) {
                return false;
            }

            // fall damage immunity (Braced)
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) {
                if (victim.hasStatusEffect(ModEffects.BRACED)) {
                    victim.removeStatusEffect(ModEffects.BRACED);
                    if (victim.getWorld() instanceof ServerWorld w) {
                        w.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_WOOL_FALL, net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
                        w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, victim.getX(), victim.getY(), victim.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                    }
                    return false;
                }
            }

            // ── ATTACKER-SIDE ─────────────────────────────────────────────────

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