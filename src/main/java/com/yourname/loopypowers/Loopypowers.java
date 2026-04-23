package com.yourname.loopypowers;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.generation.ModOreGeneration;
import com.yourname.loopypowers.item.ModItems;
import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.power.*;
import com.yourname.loopypowers.ritual.RitualManager;
import com.yourname.loopypowers.sound.ModSounds;
import com.yourname.loopypowers.command.PowerCommand;
import com.yourname.loopypowers.entity.ModEntities;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.AbilityPackets;
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
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
        /*
        // Assign power on first join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PowerManager.assignRandomPower(handler.player)
        );

        // Re-assign power on death respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) PowerManager.assignRandomPower(newPlayer);
        });
         */
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
     * (Cosmic uses ALLOW_DAMAGE instead, since it needs the crit-adjusted amount.)
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
     * Return false to cancel the original damage (e.g. when replacing it with modified damage).
     * Return true to let it through unchanged.
     * <p>
     * Order of checks:
     * 1. Attacker-side effects (powers that modify outgoing damage)
     * 2. Victim-side effects   (powers that modify incoming damage)
     */
    private void registerDamageHook() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {

            // ── ATTACKER-SIDE ─────────────────────────────────────────

            // Fortune: duel / ultimate — may re-apply scaled damage and cancel original
            if (FortunePower.tryAdjustFortuneDamage(victim, source, amount)) return false;

            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                Power attackerPower = PowerManager.getPower(attacker);

                // Blood: apply bleed on hit (side effect only, doesn't cancel damage)
                if (attackerPower instanceof BloodPower bp && amount > 0
                        && source.getSource() == attacker) {
                    bp.tryApplyBleed(attacker, victim, source, amount);
                }

                // Darkness: backstab / ultimate — may replace damage value
                if (attackerPower instanceof DarknessPower dp) {
                    if (dp.tryAdjustDarknessDamage(victim, source, amount)) return false;
                }

                // Cosmic: reduce direct melee damage, store the rest as fate
                // Uses ALLOW_DAMAGE (not onHit) so the crit-multiplied amount is available
                if (attackerPower instanceof CosmicPower
                        && source.getSource() == attacker // only apply for melee damage
                        && !CosmicPower.isApplyingReducedDamage()) { // recursion guard

                    CosmicPower.applyMeleeFate(victim, amount);

                    CosmicPower.applyingReducedDamage = true;
                    victim.damage(source, amount * 0.4f);
                    CosmicPower.applyingReducedDamage = false;

                    return false;
                }
            }

            // ── VICTIM-SIDE ───────────────────────────────────────────
            // Only player victims below this point

            if (!(victim instanceof ServerPlayerEntity victimPlayer)) return true;
            Power victimPower = PowerManager.getPower(victimPlayer);

            // Teleport: chance to dodge incoming damage entirely
            if (victimPower instanceof TeleportPower tp) {
                if (tp.tryDodge(victimPlayer)) return false;
            }

            // Flight: invulnerability window after boom; also notifies power of damage
            if (victimPower instanceof FlightPower fp) {
                if (fp.isBoomInvulnerable(victimPlayer)) return false;
                fp.onDamaged(victimPlayer);
            }

            // Blood: binding effect may reduce or cancel damage
            if (victimPower instanceof BloodPower bp) {
                if (bp.tryBindDamage(victimPlayer, source, amount)) return false;
            }

            // Explosion: ignore self-inflicted explosion and fall damage
            if (victimPower instanceof ExplosionPower) {
                if (ExplosionPower.shouldIgnoreSelfExplosionDamage(victimPlayer)
                        && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) return false;
                if (ExplosionPower.shouldIgnoreFallDamage(victimPlayer)
                        && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) return false;
            }

            // Darkness: mist phase makes the player invulnerable
            if (victimPower instanceof DarknessPower) {
                for (String tag : victimPlayer.getCommandTags()) {
                    if (tag.startsWith("dk_mist_")) return false;
                }
            }

            // Healing: interrupt passive regen on damage; optionally absorb and smooth damage
            if (victimPower instanceof HealingPower hp) {
                HealingPower.resetPassiveDelay(victimPlayer);

                float smoothing = hp.getSmoothing(victimPlayer);
                if (smoothing > 0f) {
                    // Guard against infinite recursion from the damage call below
                    if (source.getTypeRegistryEntry().matchesKey(ModDamageTypes.ABSORB)) return true;

                    victimPlayer.damage(
                            victimPlayer.getDamageSources().create(ModDamageTypes.ABSORB),
                            amount * (1.0f - smoothing)
                    );
                    return false;
                }
            }

            // Dimensional: flicker immunity
            if (victimPower instanceof DimensionalPower) {
                if (DimensionalPower.hasTag(victimPlayer, "int_immune_")) {
                    return false;
                }
            }

            // Dimensional: Increase chance to phase
            if (victimPower instanceof DimensionalPower dp) {
                dp.onDamaged(victimPlayer);
            }

            // ── GLOBAL: DISPLACEMENT IMMUNITY ─────────────────────────

            // Displace victim - Can't be hurt
            if (DimensionalPower.hasTag(victim, "int_displaced_")) {
                return false;
            }

            // Displace attacker - cant attack
            if (source.getAttacker() instanceof LivingEntity attacker) {
                if (DimensionalPower.hasTag(attacker, "int_displaced_")) {
                    return false;
                }
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
        // Fortune: tick all active gambling houses across all worlds
        for (ServerWorld world : server.getWorlds()) {
            FortunePower.tickHousesWorld(world);
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    /**
     * Per-player tick — runs every server tick for each online player.
     * Handles UI, stun states, nearby entity effects, and the player's active power.
     */
    private void tickPlayer(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();

        // Cooldown UI overlay
        CooldownUI.tick(player);

        // Sound power stun (player)
        tickStun(player);

        // Ritual Tick
        RitualManager.tick(world);

        // Sound power stun on nearby non-player entities
        Box nearbyBox = new Box(player.getPos(), player.getPos()).expand(32, 16, 32);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, nearbyBox,
                ent -> ent.isAlive() && !(ent instanceof ServerPlayerEntity))) {
            SoundPower.tickStunEntity(e);
        }

        // Tick the player's active power
        Power power = PowerManager.getPower(player);
        if (power == null) return;

        power.onTick(player);

        // Teleport: blink cooldown is triggered internally and consumed here for UI sync
        if (power instanceof TeleportPower tp && tp.consumeBlinkCooldownRequest(player)) {
            String key = PowerManager.abilityKey(power, AbilityTypes.PRIMARY);
            CooldownUI.startCooldown(player, key, tp.getPrimaryCooldownMs());
        }

        // Telekinesis: Check if player is swinging
        if (power instanceof TelekinesisPower tk) {

            boolean isSwinging = player.handSwinging;
            boolean wasSwinging = TelekinesisPower.hasTag(player, "tk_prev_swing");

            if (isSwinging && !wasSwinging) {
                if (!hasTag(player, "tk_throw_cd_")) {
                    tk.performThrow(player);
                    player.getCommandTags().add("tk_throw_cd_8");
                }

                // Also try debris throw if field is active
                tk.throwDebrisProjectile(player);
            }

            TelekinesisPower.removeTagPrefix(player, "tk_prev_swing");
            if (isSwinging) player.getCommandTags().add("tk_prev_swing");
        }
    }
}