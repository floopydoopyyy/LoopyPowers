package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.generation.ModOreGeneration;
import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.AbilityTypes;
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
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.yourname.loopypowers.power.TelekinesisPower.hasTag;

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

            // ── ATTACKER-SIDE ─────────────────────────────────────────────────

            if (FortunePower.tryAdjustFortuneDamage(victim, source, amount)) return false;

            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                Power attackerPower = PowerManager.getPower(attacker);

                if (attackerPower instanceof BloodPower bp && amount > 0
                        && source.getSource() == attacker) {
                    bp.tryApplyBleed(attacker, victim, source, amount);
                }

                if (attackerPower instanceof DarknessPower dp) {
                    if (dp.tryAdjustDarknessDamage(victim, source, amount)) return false;
                }

                if (attackerPower instanceof CosmicPower
                        && source.getSource() == attacker
                        && !CosmicPower.isApplyingReducedDamage()
                        && PassiveManager.isEnabled(attacker)) {

                    if (source.isOf(ModDamageTypes.FATE) || (source.isOf(ModDamageTypes.BLACK_HOLE))) return true;

                    CosmicPower.applyMeleeFate(attacker, victim, amount);

                    CosmicPower.applyingReducedDamage = true;
                    victim.damage(source, amount * 0.4f);
                    CosmicPower.applyingReducedDamage = false;

                    return false;
                }
            }

            // ── VICTIM-SIDE ───────────────────────────────────────────────────

            if (!(victim instanceof ServerPlayerEntity victimPlayer)) return true;
            Power victimPower = PowerManager.getPower(victimPlayer);

            if (victimPower instanceof TeleportPower tp) {
                if (tp.tryDodge(victimPlayer)) return false;
            }

            if (victimPower instanceof FlightPower fp) {
                if (fp.isBoomInvulnerable(victimPlayer)) return false;
                fp.onDamaged(victimPlayer);
            }

            if (victimPower instanceof BloodPower bp) {
                if (bp.tryBindDamage(victimPlayer, source, amount)) return false;
            }

            if (victimPower instanceof ExplosionPower) {
                if (ExplosionPower.shouldIgnoreSelfExplosionDamage(victimPlayer)
                        && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) return false;
                if (ExplosionPower.shouldIgnoreFallDamage(victimPlayer)
                        && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) return false;
            }

            if (victimPower instanceof DarknessPower) {
                for (String tag : victimPlayer.getCommandTags()) {
                    if (tag.startsWith("dk_mist_")) return false;
                }
            }

            if (victimPower instanceof HealingPower hp) {

                HealingPower.resetPassiveDelay(victimPlayer);

                if (source.isOf(ModDamageTypes.ABSORB) ||
                        source.isOf(ModDamageTypes.SMOOTHING)) {
                    return true;
                }

                if (HealingPower.handleAbsorbDamage(victimPlayer, amount)) {
                    return false;
                }

                float smoothing = hp.getSmoothing(victimPlayer);

                if (smoothing > 0f) {
                    float reduced = amount * (1.0f - smoothing);

                    victimPlayer.damage(
                            ModDamageTypes.smoothing(victimPlayer.getWorld()),
                            reduced
                    );

                    return false;
                }

                return true;
            }

            if (victimPower instanceof DimensionalPower) {
                if (DimensionalPower.hasTag(victimPlayer, "int_immune_")) return false;
            }

            if (victimPower instanceof DimensionalPower dp) {
                dp.onDamaged(victimPlayer);
            }

            // ── GLOBAL: DISPLACEMENT IMMUNITY ────────────────────────────────

            if (DimensionalPower.hasTag(victim, "int_displaced_")) return false;

            if (source.getAttacker() instanceof LivingEntity attacker) {
                if (DimensionalPower.hasTag(attacker, "int_displaced_")) return false;
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
        for (ServerWorld world : server.getWorlds()) {
            FortunePower.tickHousesWorld(world);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        CooldownUI.tick(player);
        RitualManager.tick(world);

        Power power = PowerManager.getPower(player);
        if (power == null) return;

        power.onTick(player);

        if (power instanceof TeleportPower tp && tp.consumeBlinkCooldownRequest(player)) {
            String key = PowerManager.abilityKey(power, AbilityTypes.PRIMARY);
            CooldownUI.startCooldown(player, key, tp.getPrimaryCooldownMs());
        }

        if (power instanceof TelekinesisPower tk) {
            boolean isSwinging  = player.handSwinging;
            boolean wasSwinging = TelekinesisPower.hasTag(player, "tk_prev_swing");

            if (isSwinging && !wasSwinging) {
                if (!hasTag(player, "tk_throw_cd_")) {
                    tk.performThrow(player);
                    player.getCommandTags().add("tk_throw_cd_8");
                }
                tk.throwDebrisProjectile(player);
            }

            TelekinesisPower.removeTagPrefix(player, "tk_prev_swing");
            if (isSwinging) player.getCommandTags().add("tk_prev_swing");
        }
    }
}