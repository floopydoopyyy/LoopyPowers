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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.yourname.loopypowers.power.TelekinesisPower.hasTag;

public class Loopypowers implements ModInitializer {

    public static final String MOD_ID = "loopypowers";
    public static final Logger LOGGER  = LoggerFactory.getLogger(MOD_ID);

    // chance for onepunch
    private static final float ONE_PUNCH_CHANCE = 0.05f;

    // flower planting odds
    private static final float FLOWER_CORPSE_CHANCE = 0.15f;
    // pvz zombie kill odds
    private static final float PVZ_KILL_CHANCE = 0.03f;

    // if the debug setting is toggled
    public static boolean onePunchDebugEnabled = false;

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

            // EGG for opm
            // this was a waste of my time
            if (power instanceof StrengthPower && sp.getMainHandStack().isEmpty()) {

                boolean isUnarmoredPlayer = (target instanceof ServerPlayerEntity) && (target.getArmor() == 0);

                // check for armour, or debug being enabled
                if (onePunchDebugEnabled || (isUnarmoredPlayer && world.random.nextFloat() < ONE_PUNCH_CHANCE)) {

                    // wind up
                    world.playSound(null, sp.getBlockPos(), ModSounds.ONEPUNCH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

                    // launch into orbit
                    Vec3d dir = sp.getRotationVec(1.0f).normalize();
                    target.setVelocity(dir.x * 25.0, 4.0, dir.z * 25.0);
                    target.velocityModified = true;

                    if (target instanceof ServerPlayerEntity spTarget) {
                        spTarget.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(spTarget));
                    }

                    // air tunnel
                    if (world instanceof ServerWorld sw) {
                        Vec3d pos = target.getPos();

                        //hit
                        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 2, 0, 0, 0, 0);
                        sw.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 5, 1.0, 1.0, 1.0, 0);

                        // more
                        for (int i = 0; i < 150; i++) {
                            double step = i * 0.8;
                            double px = pos.x + dir.x * step;
                            double py = pos.y + 1.0 + dir.y * step;
                            double pz = pos.z + dir.z * step;

                            // dense inner trail
                            sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 15, 0.5, 0.5, 0.5, 0.1);
                            // massive outter
                            sw.spawnParticles(ParticleTypes.CLOUD, px, py, pz, 30, 3.0, 3.0, 3.0, 0.3);

                            // periodic extra
                            if (i % 8 == 0) {
                                sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 1, 0, 0, 0, 0);
                                sw.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 5, 4.0, 4.0, 4.0, 0);
                            }
                        }
                    }

                    // splat
                    target.damage(ModDamageTypes.onePunch(world, sp), 9999f);

                    return ActionResult.SUCCESS; // skip normal logic
                }
            }

            if (power != null) power.onHit(sp, target);

            return ActionResult.PASS;
        });
    }

    private void registerDamageHook() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            // ── GLOBAL EFFECT HANDLERS ────────────────────────────────────────

            // fall damage immunity
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_FALL)) {
                if (victim.hasStatusEffect(ModEffects.BRACED)) {
                    // coomsume effect
                    victim.removeStatusEffect(ModEffects.BRACED);

                    // Play feedback
                    if (victim.getWorld() instanceof ServerWorld w) {
                        w.playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_WOOL_FALL, net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
                        w.spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD, victim.getX(), victim.getY(), victim.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                    }
                    return false; // cancel it!
                }
            }

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

                // plant flower on corpse
                if (attackerPower instanceof NaturePower && amount >= victim.getHealth()) {
                    if (victim.getWorld() instanceof ServerWorld sw) {

                        // plant a flower
                        BlockPos under = victim.getBlockPos().down();
                        if (sw.getBlockState(under).isOf(Blocks.GRASS_BLOCK) && sw.getBlockState(victim.getBlockPos()).isAir()) {
                            if (sw.random.nextFloat() < FLOWER_CORPSE_CHANCE) {
                                Block[] flowers = {Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY};
                                Block flower = flowers[sw.random.nextInt(flowers.length)];
                                sw.setBlockState(victim.getBlockPos(), flower.getDefaultState());
                                victim.getWorld().playSound(null, victim.getBlockPos(), SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 0.6f, 1.0f);
                            }
                        }
                    }
                }
            }

            // PVZ GW2 funny pop sound
            if (victim instanceof ZombieEntity && amount >= victim.getHealth()) {
                if (source.isOf(ModDamageTypes.THORN) || source.isOf(ModDamageTypes.VINE_BIND)) {
                    if (victim.getWorld().random.nextFloat() < PVZ_KILL_CHANCE) {
                        victim.getWorld().playSound(null, victim.getBlockPos(), ModSounds.PVZPOP, net.minecraft.sound.SoundCategory.HOSTILE, 0.7f, 1.0f);
                    }
                }
            }

            // ── VICTIM-SIDE ───────────────────────────────────────────────────

            if (!(victim instanceof ServerPlayerEntity victimPlayer)) return true;
            Power victimPower = PowerManager.getPower(victimPlayer);

            if (victimPower instanceof TeleportPower tp) {
                if (tp.tryDodge(victimPlayer)) return false;
            }

            if (victimPower instanceof LightningPower) {
                // stop lightning damage
                if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_LIGHTNING)) {
                    return false;
                }
            }

            if (victimPower instanceof FlightPower fp) {
                if (fp.isBoomInvulnerable(victimPlayer)) return false;
                fp.onDamaged(victimPlayer);
            }

            if (victimPower instanceof BloodPower bp) {
                if (bp.tryBindDamage(victimPlayer, source, amount)) return false;
            }

            if (victimPower instanceof ExplosionPower) {
                // If they are airborne and their ultimate is active
                if (!victimPlayer.isOnGround() &&
                        ExplosionPower.getTimerLeft(victimPlayer, ExplosionPower.ULT_ACTIVE) > 0) {

                    // force pop if they take a big chunk of damage and its from a target
                    if (source.getAttacker() instanceof LivingEntity && amount >= 3.0f) {
                        if (victimPlayer.getWorld() instanceof ServerWorld sw) {
                            ExplosionPower.forceEarlyDetonation(victimPlayer, sw);
                        }
                    }
                }

                // self damage check
                if (ExplosionPower.shouldIgnoreSelfExplosionDamage(victimPlayer)
                        && source.isIn(net.minecraft.registry.tag.DamageTypeTags.IS_EXPLOSION)) return false;
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

            if (victimPower instanceof DimensionalPower dp) {
                if (DimensionalPower.hasTag(victimPlayer, "int_immune_")) return false;

                // FIXED: Actually call onDamaged so your passive triggers again!
                dp.onDamaged(victimPlayer);
            }

            // ── GLOBAL ────────────────────────────────
            // DISPLACE IMMUNITY

            if (victim.hasStatusEffect(ModEffects.DISPLACED)) return false;

            if (source.getAttacker() instanceof LivingEntity attacker) {
                return !attacker.hasStatusEffect(ModEffects.DISPLACED);
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
        // GLOBAL TICKS (Runs exactly once per server tick)
        RitualManager.tick(server);

        // DIMENSION TICKS (Runs once for overworld, once for nether, once for end)
        for (ServerWorld world : server.getWorlds()) {
            FortunePower.tickHousesWorld(world);
        }

        // PLAYER TICKS
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            tickPlayer(player);
        }
    }

    private void tickPlayer(ServerPlayerEntity player) {
        // note to self: only stuff that is ticked PER PLAYER should be here
        // I hate modding.
        CooldownUI.tick(player);

        Power power = PowerManager.getPower(player);
        if (power == null) return;

        power.onTick(player);

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