package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.generation.ModOreGeneration;
import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.AbilityTypes;
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
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.yourname.loopypowers.power.SoundPower.tickStun;
import static com.yourname.loopypowers.power.TelekinesisPower.hasTag;

public class Loopypowers implements ModInitializer {

    public static final String MOD_ID = "loopypowers";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        registerSystems();
        registerPlayerEvents();
        registerCombatEvents();
        registerTickEvents();
        ModOreGeneration.generateOres();

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
    }

    /* ============================================================
       PLAYER LIFECYCLE EVENTS
       ============================================================ */

    private void registerPlayerEvents() {

        // ── JOIN ─────────────────────────────────────────────────────────────
        // Load power/level/cooldowns from disk every time a player connects.
        // This covers: first login, re-join after disconnect, and server restart.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerDataStore.load(player);
        });

        // ── DISCONNECT ───────────────────────────────────────────────────────
        // Save to disk whenever a player leaves cleanly.
        // This is the primary save path for normal gameplay.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            PlayerDataStore.save(player);
        });

        // ── RESPAWN / DIMENSION CHANGE ────────────────────────────────────────
        // Fabric fires COPY_FROM for both death+respawn AND dimension travel.
        // We copy the in-memory state from old → new entity, then save immediately
        // so the file reflects the respawned player.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {

            // Copy power
            Power oldPower = PowerManager.getPower(oldPlayer);
            if (oldPower != null) {
                PowerManager.setPower(newPlayer, oldPower);
                // Note: setPower calls onAssign and saves, so no extra save needed
                // for the power itself. We still copy level + cooldowns below.
            }

            // Copy level (setPower resets to 1, so we must restore it after)
            int level = PowerManager.getLevel(oldPlayer);
            PowerManager.setLevel(newPlayer, level);

            // Copy cooldowns
            PowerManager.copyCooldowns(oldPlayer, newPlayer);

            // Persist the newly assembled state for the respawned player
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

    /**
     * Fires when a player lands a melee hit.
     * Calls onHit() on the attacker's power — powers use this for
     * passive procs that don't need to know the exact damage value.
     */
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

    /**
     * Fires on every damage event, before it is applied.
     * Return false to cancel the original damage.
     * Return true to let it through unchanged.
     */
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
                        && !CosmicPower.isApplyingReducedDamage()) {

                    CosmicPower.applyMeleeFate(victim, amount);
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

                float smoothing = hp.getSmoothing(victimPlayer);
                if (smoothing > 0f) {
                    if (source.getTypeRegistryEntry().matchesKey(ModDamageTypes.ABSORB)) return true;

                    victimPlayer.damage(
                            victimPlayer.getDamageSources().create(ModDamageTypes.ABSORB),
                            amount * (1.0f - smoothing)
                    );
                    return false;
                }
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

    /**
     * Per-player tick — runs every server tick for each online player.
     */
    private void tickPlayer(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        CooldownUI.tick(player);
        tickStun(player);
        RitualManager.tick(world);

        Box nearbyBox = new Box(player.getPos(), player.getPos()).expand(32, 16, 32);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, nearbyBox,
                ent -> ent.isAlive() && !(ent instanceof ServerPlayerEntity))) {
            SoundPower.tickStunEntity(e);
        }

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