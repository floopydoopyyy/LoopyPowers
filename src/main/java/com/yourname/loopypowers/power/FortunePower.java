package com.yourname.loopypowers.power;

import com.yourname.loopypowers.block.ModBlocks;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import com.yourname.loopypowers.CooldownUI;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.state.property.Properties;

import java.util.*;

public class FortunePower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, FortuneState> ACTIVE_STATES = new HashMap<>();
    private static final Set<UUID> DAMAGE_GUARDS = new HashSet<>();

    private static class FortuneState {
        int luck = 0;
        int luckDecayTicks = 0;
    }

    private static FortuneState getState(Entity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new FortuneState());
    }

    private static FortuneState getStateOpt(Entity player) {
        return ACTIVE_STATES.get(player.getUuid());
    }

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("fo_")); // Cleanup legacy tags
        ACTIVE_STATES.put(player.getUuid(), new FortuneState());

        breakDuel(player.getUuid());

        var server = player.getServer();
        if (server != null) {
            removeHouseNow(server, player.getUuid());
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("fo_"));
        ACTIVE_STATES.remove(player.getUuid());

        breakDuel(player.getUuid());

        var server = player.getServer();
        if (server != null) {
            removeHouseNow(server, player.getUuid());
        }
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        FortuneState state = getState(player);
        tickLuck(player, state);
        tickDuelsWorld(player.getWorld());
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        FortuneState state = getState(attacker);
        addLuck(attacker, state, LUCK_GAIN_ON_HIT);
        tryProcOnHit(attacker, state, target);
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final int LUCK_MAX = 100;
    private static final int PROC_ACTIONBAR_TICKS = 35;

    // how fast you build luck per hit
    private static final int LUCK_GAIN_ON_HIT = 6;

    // decay after you stop hitting
    private static final int LUCK_DECAY_DELAY_TICKS = 30;   // 3s after last hit
    private static final int LUCK_DECAY_STEP_TICKS  = 10;   // then decay every 0.5s
    private static final int LUCK_DECAY_STEP_POINTS = 5;

    // proc chance scales with luck
    private static final float PROC_BASE = 0.04f;           // 4%
    private static final float PROC_BONUS_AT_MAX = 0.18f;   // // keep in mind base is separate from this and added to this total

    // spend luck on proc
    private static final int LUCK_SPEND_ON_PROC = 45;

    private static void addLuck(ServerPlayerEntity p, FortuneState state, int add) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(p)) return;
        if (add <= 0) return;

        state.luck = MathHelper.clamp(state.luck + add, 0, LUCK_MAX);
        state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;
    }

    private static void tickLuck(ServerPlayerEntity p, FortuneState state) {
        if (state.luck <= 0) return;

        state.luckDecayTicks--;

        // when delay hits 0, step down and schedule next step
        if (state.luckDecayTicks == 0) {
            state.luck = Math.max(0, state.luck - LUCK_DECAY_STEP_POINTS);
            if (state.luck > 0) {
                state.luckDecayTicks = LUCK_DECAY_STEP_TICKS;
            }
        }
    }

    private static void tryProcOnHit(ServerPlayerEntity attacker, FortuneState state, LivingEntity target) {
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(attacker)) return;

        ServerWorld w = attacker.getWorld();

        float t = state.luck / (float) LUCK_MAX;
        float chance = PROC_BASE + (PROC_BONUS_AT_MAX * t);

        if (w.random.nextFloat() >= chance) return;

        // spend luck when we proc
        state.luck = Math.max(0, state.luck - LUCK_SPEND_ON_PROC);
        state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;

        int roll = w.random.nextInt(3);

        if (roll == 0) {
            //extra damage
            float extra = 1.5f + w.random.nextFloat() * 2.5f; // 1.5..4.0
            target.damage(attacker.getDamageSources().playerAttack(attacker), extra);

            // actionbar
            CooldownUI.pushActionbarOverride(attacker, "§6§lJACKPOT:§r §eLucky Shot!", PROC_ACTIONBAR_TICKS);

            // particles on enemy
            spawnProcParticlesEnemy(w, target);

            // sound
            w.playSound(null, target.getBlockPos(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY : ModSounds.JACKPOT,
                    attacker.getSoundCategory(),
                    0.5f, 1.0f);

        } else if (roll == 1) {
            //debuff enemy
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, true, false));

            CooldownUI.pushActionbarOverride(attacker, "§6§lJACKPOT:§r §cBad Beat!", PROC_ACTIONBAR_TICKS);

            spawnProcParticlesEnemy(w, target);

            w.playSound(null, target.getBlockPos(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY : ModSounds.JACKPOT2,
                    attacker.getSoundCategory(),
                    0.5f, 1.0f);

        } else {
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false));
            attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 60, 0, true, false));

            CooldownUI.pushActionbarOverride(attacker, "§6§lJACKPOT:§r §7Favour!", PROC_ACTIONBAR_TICKS);

            spawnProcParticlesSelf(w, attacker);

            w.playSound(null, attacker.getBlockPos(),
                    w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY : ModSounds.JACKPOT3,
                    attacker.getSoundCategory(),
                    0.5f, 1.0f);
        }
    }

    private static void spawnProcParticlesEnemy(ServerWorld w, LivingEntity target) {
        w.spawnParticles(
                ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getHeight() * 0.60, target.getZ(),
                14,
                0.25, 0.30, 0.25,
                0.0
        );
        w.spawnParticles(
                ParticleTypes.CRIT,
                target.getX(), target.getY() + target.getHeight() * 0.55, target.getZ(),
                10,
                0.20, 0.20, 0.20,
                0.04
        );
    }

    private static void spawnProcParticlesSelf(ServerWorld w, ServerPlayerEntity player) {
        w.spawnParticles(
                ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18,
                0.35, 0.45, 0.35,
                0.0
        );
        w.spawnParticles(
                ParticleTypes.FIREWORK,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1,
                0.0, 0.0, 0.0,
                0.0
        );
    }


    /* ============================================================
       PRIMARY
       ============================================================ */

    private static final float PRIM_SELF_DAMAGE = 6.0f; // 3 hearts
    private static final int PRIM_STR_AMP = 0;          // Strength 1
    private static final int PRIM_SPEED_AMP = 1;        // Speed 2

    private static final float JACKPOT_BASE = 0.06f;        // no luck odds
    private static final float JACKPOT_BONUS_AT_MAX = 0.28f; // max luck bonus chance
    private static final float JACKPOT_MAX = 0.60f;         // hard cap

    // Jackpot rewards
    private static final int JACKPOT_REGEN_TICKS  = 40;     // regeneration time
    private static final int PRIM_BUFF_TICKS = 80; // time buffed

    // egg
    private static final int FUNNY_CHANCE = 300; // funny

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getWorld();

        // self damage
        player.damage(ModDamageTypes.bet(w), PRIM_SELF_DAMAGE); // bet damage

        // If they died stop
        if (!player.isAlive()) return;

        // base buffs
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, PRIM_BUFF_TICKS, PRIM_STR_AMP, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, PRIM_BUFF_TICKS, PRIM_SPEED_AMP, true, false));

        // Base FX
        w.playSound(null, player.getBlockPos(),
                ModSounds.ALLIN,
                player.getSoundCategory(),
                0.6f, 1.0f);

        w.spawnParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                18, 0.35, 0.45, 0.35, 0.0);

        // JACKPOT
        if (tryAllInJackpot(player, w)) {
            net.minecraft.sound.SoundEvent jpSound = switch(w.random.nextInt(3)) {
                case 0 -> ModSounds.JACKPOT;
                case 1 -> ModSounds.JACKPOT2;
                default -> ModSounds.JACKPOT3;
            };

            if (w.random.nextInt(FUNNY_CHANCE) == 0) {
                jpSound = ModSounds.JACKPOTFUNNY;
            }

            w.playSound(null, player.getBlockPos(),
                    jpSound,
                    player.getSoundCategory(),
                    0.5f, 1.0f);

            w.spawnParticles(ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);

            w.spawnParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1.1, player.getZ(),
                    60, 0.65, 0.55, 0.65, 0.0);

        }
        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static boolean tryAllInJackpot(ServerPlayerEntity player, ServerWorld w) { // attempts and applies jackpot
        FortuneState state = getState(player);
        float t = state.luck / (float) LUCK_MAX;

        float chance = JACKPOT_BASE + (JACKPOT_BONUS_AT_MAX * t);
        chance = MathHelper.clamp(chance, 0.0f, JACKPOT_MAX);

        if (w.random.nextFloat() >= chance) return false;

        // picks one jackpot effect
        int roll = w.random.nextInt(3);

        if (roll == 0) {
            // Strength 2 instead
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.STRENGTH, PRIM_BUFF_TICKS, 1, true, false
            ));
            CooldownUI.pushActionbarOverride(player, "§6§lJACKPOT:§r §4Raise!", 50);

        } else if (roll == 1) {
            // brief regeneration
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.REGENERATION, JACKPOT_REGEN_TICKS, 0, true, false
            ));
            CooldownUI.pushActionbarOverride(player, "§6§lJACKPOT:§r §dDraw no bet!", 50);

        } else {
            // doubled time
            int doubled = PRIM_BUFF_TICKS * 2;

            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.STRENGTH, doubled, PRIM_STR_AMP, true, false
            ));
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, doubled, PRIM_SPEED_AMP, true, false
            ));
            CooldownUI.pushActionbarOverride(player, "§6§lJACKPOT:§r §eDouble Time!", 50);
        }
        return true;
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final double DUEL_CAST_RANGE = 24.0;
    private static final double DUEL_MAX_RANGE  = 20.0;        // leash before it breaks
    private static final int    DUEL_DURATION_TICKS = 20 * 10; // 10s
    private static final float  DUEL_VS_PARTNER_MULT = 1.5f;   // +50%
    private static final float  DUEL_VS_OTHERS_MULT  = 0.5f;   // -50%

    // fx cadence
    private static final int DUEL_TETHER_EVERY_TICKS = 2;

    private static final Map<UUID, DuelInstance> ACTIVE_DUELS = new HashMap<>();
    private static final Map<RegistryKey<World>, Long> DUEL_LAST_TICK = new HashMap<>();

    private static final class DuelInstance {
        final RegistryKey<World> worldKey;
        final UUID a;
        final UUID b;
        int ticksLeft;

        DuelInstance(RegistryKey<World> worldKey, UUID a, UUID b, int ticksLeft) {
            this.worldKey = worldKey;
            this.a = a;
            this.b = b;
            this.ticksLeft = ticksLeft;
        }

        boolean contains(UUID u) {
            return a.equals(u) || b.equals(u);
        }
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getWorld();

        // If already dueling, recast cancels it
        if (isInDuel(player)) {
            breakDuel(player.getUuid());
            w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, player.getSoundCategory(), 0.8f, 1.0f);
            CooldownUI.pushActionbarOverride(player, "§7Duel ended.", 35);
            return;
        }

        player.swingHand(Hand.MAIN_HAND, true);

        Vec3d start = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0f).normalize();
        Vec3d end = start.add(dir.multiply(DUEL_CAST_RANGE));

        // stop at blocks
        HitResult blockHit = w.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        Vec3d blockEnd = (blockHit.getType() == HitResult.Type.BLOCK) ? blockHit.getPos() : end;

        // find nearest living entity along the segment
        LivingEntity hitEntity = null;
        Vec3d hitPos = null;
        double bestDistSq = start.squaredDistanceTo(blockEnd);

        Box searchBox = new Box(start, blockEnd).expand(1.2);
        List<LivingEntity> candidates = w.getEntitiesByClass(
                LivingEntity.class,
                searchBox,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity e : candidates) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.25).raycast(start, blockEnd);
            if (hit.isEmpty()) continue;

            Vec3d p = hit.get();
            double d = start.squaredDistanceTo(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                hitEntity = e;
                hitPos = p;
            }
        }

        Vec3d beamEnd = (hitPos != null) ? hitPos : blockEnd;

        // beam fx
        spawnDuelBeam(w, start, beamEnd);
        w.playSound(null, player.getBlockPos(), ModSounds.RAISESTAKES, player.getSoundCategory(), 1.0f, 1.0f);

        if (hitEntity == null) {
            w.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, player.getSoundCategory(), 0.6f, 0.6f);
            return;
        }

        // start duel
        startDuel(player, hitEntity);
    }

    private static boolean isInDuel(LivingEntity e) {
        return ACTIVE_DUELS.containsKey(e.getUuid());
    }

    private static void breakDuel(UUID member) {
        DuelInstance d = ACTIVE_DUELS.get(member);
        if (d == null) return;

        ACTIVE_DUELS.remove(d.a);
        ACTIVE_DUELS.remove(d.b);
    }

    private static void startDuel(ServerPlayerEntity caster, LivingEntity target) {
        // break any existing duel on anyone effected
        breakDuel(caster.getUuid());
        breakDuel(target.getUuid());

        ServerWorld w = caster.getWorld();
        DuelInstance d = new DuelInstance(w.getRegistryKey(), caster.getUuid(), target.getUuid(), DUEL_DURATION_TICKS);

        // index by both members
        ACTIVE_DUELS.put(d.a, d);
        ACTIVE_DUELS.put(d.b, d);

        w.playSound(null, caster.getBlockPos(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
                caster.getSoundCategory(),
                0.9f, 1.15f);
    }

    private static void tickDuelsWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();

        Long last = DUEL_LAST_TICK.get(key);
        if (last != null && last == now) return;
        DUEL_LAST_TICK.put(key, now);

        if (ACTIVE_DUELS.isEmpty()) return;

        // snapshot to avoid infinite
        List<DuelInstance> snapshot = new ArrayList<>(ACTIVE_DUELS.values());
        Set<DuelInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<UUID> toBreak = new ArrayList<>();

        for (DuelInstance d : snapshot) {
            if (!seen.add(d)) continue;          // dedupe
            if (!d.worldKey.equals(key)) continue;

            d.ticksLeft--;
            if (d.ticksLeft <= 0) {
                toBreak.add(d.a);
                continue;
            }

            Entity ea = w.getEntity(d.a);
            Entity eb = w.getEntity(d.b);

            if (!(ea instanceof LivingEntity a) || !(eb instanceof LivingEntity b) || !a.isAlive() || !b.isAlive()) {
                toBreak.add(d.a);
                continue;
            }

            double maxSq = DUEL_MAX_RANGE * DUEL_MAX_RANGE;
            if (a.squaredDistanceTo(b) > maxSq) {
                w.playSound(null, a.getBlockPos(),
                        SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK,
                        a.getSoundCategory(),
                        0.8f, 1.0f);
                toBreak.add(d.a);
                continue;
            }

            if ((now % DUEL_TETHER_EVERY_TICKS) == 0) {
                spawnDuelTether(w, a, b);
                spawnDuelAura(w, a);
                spawnDuelAura(w, b);
            }
        }

        // Apply removals after iteration - avoid crash
        for (UUID u : toBreak) {
            breakDuel(u);
        }
    }

    private static void spawnDuelBeam(ServerWorld w, Vec3d start, Vec3d end) {
        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);
        int steps = MathHelper.clamp((int)(len / 0.35), 8, 120);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            w.spawnParticles(ParticleTypes.ENCHANT,
                    p.x, p.y, p.z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0);

            if ((i & 3) == 0) {
                w.spawnParticles(ParticleTypes.CRIT,
                        p.x, p.y, p.z,
                        1,
                        0.02, 0.02, 0.02,
                        0.02);
            }

            p = p.add(dir.multiply(len / steps));
        }
    }

    private static void spawnDuelTether(ServerWorld w, LivingEntity a, LivingEntity b) {
        Vec3d start = a.getPos().add(0, a.getHeight() * 0.65, 0);
        Vec3d end   = b.getPos().add(0, b.getHeight() * 0.65, 0);

        Vec3d delta = end.subtract(start);
        double len = delta.length();
        if (len < 0.01) return;

        Vec3d dir = delta.multiply(1.0 / len);
        int steps = MathHelper.clamp((int)(len / 0.45), 10, 90);

        Vec3d p = start;
        for (int i = 0; i <= steps; i++) {
            w.spawnParticles(ParticleTypes.ENCHANT,
                    p.x, p.y, p.z,
                    1,
                    0.03, 0.03, 0.03,
                    0.0);
            p = p.add(dir.multiply(len / steps));
        }

        // endpoints
        w.spawnParticles(ParticleTypes.ENCHANT,
                start.x, start.y, start.z,
                3, 0.15, 0.15, 0.15, 0.0);
        w.spawnParticles(ParticleTypes.ENCHANT,
                end.x, end.y, end.z,
                3, 0.15, 0.15, 0.15, 0.0);
    }

    private static void spawnDuelAura(ServerWorld w, LivingEntity e) {
        Vec3d p = e.getPos().add(0, e.getHeight() * 0.65, 0);
        w.spawnParticles(ParticleTypes.ENCHANT,
                p.x, p.y, p.z,
                2,
                0.18, 0.25, 0.18,
                0.0);
    }

    private static float getDuelMultiplier(LivingEntity victim, LivingEntity attacker) {
        DuelInstance dv = ACTIVE_DUELS.get(victim.getUuid());
        DuelInstance da = ACTIVE_DUELS.get(attacker.getUuid());

        if (dv == null && da == null) return 1.0f;

        if (dv != null) {
            return dv.contains(attacker.getUuid()) ? DUEL_VS_PARTNER_MULT : DUEL_VS_OTHERS_MULT;
        }
        return DUEL_VS_OTHERS_MULT;
    }

   /* ============================================================
   ULTIMATE
   ============================================================ */

    // Cage
    private static final int HOUSE_RADIUS = 8;
    private static final int HOUSE_WALL_LAYERS = 4;
    private static final int HOUSE_BUILD_INTERVAL_TICKS = 7;
    private static final int HOUSE_ACTIVE_TICKS = 238; // - 2 if a multiple of 40
    private static final int HOUSE_CLEAR_HEIGHT = 10;

    // Roof particles
    private static final int HOUSE_ROOF_FX_EVERY_TICKS = 2;
    private static final int HOUSE_ROOF_FX_PARTICLES = 28;
    private static final double HOUSE_ROOF_FX_Y_OFFSET = 1.15;

    // Block breaking
    private static final int HOUSE_BREAK_FROM_TOP_OFFSET = 2;  // y >= baseY + (HOUSE_WALL_LAYERS - 2)
    private static final int HOUSE_BREAK_SCAN_EXTRA_Y = 10;
    private static final int HOUSE_BREAK_EVERY_TICKS = 2;
    private static final int HOUSE_BREAK_MARGIN = 1;
    private static final int HOUSE_BREAK_ENCHANT_PARTICLES = 10;

    // Rules
    private static final int HOUSE_RULE_INTERVAL_TICKS = 80; // every 4s
    private static final int HOUSE_RULE_MIN_PLAYERS_TO_ANNOUNCE = 1;

    private static final float RULE_WOOLIAM_CHANCE = 0.004f; // EGG!!!

    // Roulette
    private static final float RULE_ROULETTE_DAMAGE = 14.0f;

    // Lightning Round
    private static final int RULE_LIGHTNING_EVERY_TICKS = 10;
    private static final float RULE_LIGHTNING_DAMAGE = 1.0f;

    // Hot Seat
    private static final int RULE_HOTSEAT_FUSE_TICKS = 60;
    private static final float RULE_HOTSEAT_DAMAGE = 28.0f;
    private static final int RULE_HOTSEAT_PARTICLES_EVERY_TICKS = 2;

    // Double or Nothing
    private static final float RULE_DOUBLE_MULT = 1.25f;

    // Rule effect duration scaling (all potion rules)
    private static final float HOUSE_RULE_EFFECT_MULT = 1.75f; // e.g. 1.75x longer
    private static final int   HOUSE_RULE_EFFECT_BONUS_TICKS = 40; // +2s

    // Chip Toss
    private static final float RULE_CHIP_TOSS_FRACTION = 0.50f; // 50%
    private static final double RULE_CHIP_TOSS_UP_MIN = 1.05;
    private static final double RULE_CHIP_TOSS_UP_MAX = 1.75;
    private static final double RULE_CHIP_TOSS_SIDE = 0.35;

    // Smoke Machine
    private static final int RULE_SMOKE_PARTICLES_EVERY_TICKS = 2;
    private static final int RULE_SMOKE_PARTICLES_PER_ENTITY = 8;

    // Spotlight
    private static final float RULE_SPOTLIGHT_MULT = 1.50f; // victim takes +50% dmg
    private static final int RULE_SPOTLIGHT_RETRY_EVERY_TICKS = 10;

    // Jackpot (next hit in room)
    private static final float RULE_JACKPOT_MULT = 4.0f;
    private static final int RULE_JACKPOT_FX_PARTICLES = 18;

    // Wildcards
    private static final int RULE_WILDCARDS_MIN = 2;
    private static final int RULE_WILDCARDS_MAX = 8;

    private static int ruleEffectDurationTicks() {
        int base = HOUSE_RULE_INTERVAL_TICKS + 10;
        return MathHelper.ceil(base * HOUSE_RULE_EFFECT_MULT) + HOUSE_RULE_EFFECT_BONUS_TICKS;
    }

    private enum HouseRule {
        LOADED_DICE,
        FREE_DRINKS,
        VIP_PASS,
        NO_RUNNING,
        NO_FIGHTING,
        DOUBLE_OR_NOTHING,
        SHUFFLE,
        ROULETTE,
        LIGHTNING_ROUND,
        HOT_SEAT,
        CHIP_TOSS,
        WILDCARDS,
        SMOKE_MACHINE,
        SPOTLIGHT,
        JACKPOT,
        CARD_COUNTER,
        WOOLIAM_INVASION
    }

    private static final Map<UUID, HouseState> ACTIVE_HOUSES = new HashMap<>();
    private static final Map<RegistryKey<World>, Long> HOUSE_LAST_TICK = new HashMap<>();

    private static final class HouseState {
        final UUID owner;
        final RegistryKey<World> worldKey;
        final BlockPos center;
        final int baseY;

        int buildLayer;
        int buildWait;
        int activeLeft;
        boolean built;

        final Set<BlockPos> placed = new HashSet<>();
        final Set<UUID> inside = new HashSet<>();

        // rules
        HouseRule rule = null;
        int ruleLeft = HOUSE_RULE_INTERVAL_TICKS;

        // hot seat
        UUID hotSeatHolder = null;
        int hotSeatFuse = 0;

        // spotlight + jackpot
        UUID spotlightTarget = null;
        boolean jackpotArmed = false;

        HouseState(UUID owner, RegistryKey<World> worldKey, BlockPos center) {
            this.owner = owner;
            this.worldKey = worldKey;
            this.center = center;
            this.baseY = center.getY();

            this.buildLayer = 0;
            this.buildWait = HOUSE_BUILD_INTERVAL_TICKS;
            this.activeLeft = HOUSE_ACTIVE_TICKS + (HOUSE_WALL_LAYERS * HOUSE_BUILD_INTERVAL_TICKS) + 20;
            this.built = false;
        }
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getWorld();
        var server = player.getServer();
        if (server == null) return;

        removeHouseNow(server, player.getUuid());

        BlockPos center = player.getBlockPos();
        HouseState st = new HouseState(player.getUuid(), w.getRegistryKey(), center);
        ACTIVE_HOUSES.put(player.getUuid(), st);

        placeHouseFloor(w, st);
        clearHouseInteriorAbove(w, st);
        updateHouseInside(w, st);

        // fx
        w.playSound(null, center, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.9f, 0.9f);

        w.spawnParticles(ParticleTypes.ENCHANT,
                center.getX() + 0.5, center.getY() + 1.2, center.getZ() + 0.5,
                35, 0.9, 0.4, 0.9, 0.0);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    // this is called by the world in loopypowers class
    public static void tickHousesWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();

        Long last = HOUSE_LAST_TICK.get(key);
        if (last != null && last == now) return;
        HOUSE_LAST_TICK.put(key, now);

        if (ACTIVE_HOUSES.isEmpty()) return;

        var server = w.getServer();
        if (server == null) return;

        List<Map.Entry<UUID, HouseState>> snapshot = new ArrayList<>(ACTIVE_HOUSES.entrySet());
        List<UUID> toRemove = new ArrayList<>();

        for (var entry : snapshot) {
            UUID owner = entry.getKey();
            HouseState st = entry.getValue();

            if (!st.worldKey.equals(key)) continue;

            updateHouseInside(w, st);

            if ((now % HOUSE_ROOF_FX_EVERY_TICKS) == 0L) spawnHouseRoofFx(w, st);
            if ((now % HOUSE_BREAK_EVERY_TICKS) == 0L) enforceHouseBuildCeiling(w, st);

            // Build phase
            if (!st.built) {
                st.buildWait--;
                if (st.buildWait <= 0) {
                    placeHouseBarsLayer(w, st, st.buildLayer);
                    st.buildLayer++;
                    st.buildWait = HOUSE_BUILD_INTERVAL_TICKS;

                    if (st.buildLayer >= HOUSE_WALL_LAYERS) {
                        st.built = true;

                        w.playSound(null, st.center, SoundEvents.BLOCK_ANVIL_LAND,
                                net.minecraft.sound.SoundCategory.PLAYERS, 0.75f, 1.1f);

                        w.spawnParticles(ParticleTypes.POOF,
                                st.center.getX() + 0.5, st.baseY + 0.2, st.center.getZ() + 0.5,
                                16, 0.6, 0.2, 0.6, 0.04);

                        forceNewRule(w, st, true);
                    } else {
                        w.playSound(null, st.center, SoundEvents.BLOCK_CHAIN_PLACE,
                                net.minecraft.sound.SoundCategory.PLAYERS, 0.45f, 1.25f);
                    }
                }
                continue;
            }

            // active phase
            st.activeLeft--;

            // rules tick + rotate
            tickHouseRules(w, st);

            if (st.activeLeft <= 0) toRemove.add(owner);
        }

        for (UUID owner : toRemove) {
            removeHouseNow(server, owner);
        }
    }

    private static void tickHouseRules(ServerWorld w, HouseState st) {
        if (st.rule == null) {
            forceNewRule(w, st, true);
            return;
        }

        // per-rule ticking behavior
        if (st.rule == HouseRule.LIGHTNING_ROUND) {
            if ((w.getTime() % RULE_LIGHTNING_EVERY_TICKS) == 0L) {
                Entity owner = w.getEntity(st.owner);
                for (LivingEntity e : getHouseLiving(w, st)) {
                    w.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(),
                            6, 0.25, 0.25, 0.25, 0.0);
                    e.damage(ModDamageTypes.house(w, owner), RULE_LIGHTNING_DAMAGE); // house deals damage
                }
            }
        } else if (st.rule == HouseRule.HOT_SEAT) {
            tickHotSeat(w, st);
        } else if (st.rule == HouseRule.SMOKE_MACHINE) {
            if ((w.getTime() % RULE_SMOKE_PARTICLES_EVERY_TICKS) == 0L) {
                spawnSmokeMachineFx(w, st);
            }
        } else if (st.rule == HouseRule.SPOTLIGHT) {
            if ((w.getTime() % RULE_SPOTLIGHT_RETRY_EVERY_TICKS) == 0L) {
                tickSpotlight(w, st);
            }
        }

        st.ruleLeft--;
        if (st.ruleLeft <= 0) {
            forceNewRule(w, st, false);
        }
    }

    private static void forceNewRule(ServerWorld w, HouseState st, boolean first) {
        // clear last rules persistent state
        if (st.rule == HouseRule.HOT_SEAT) {
            st.hotSeatHolder = null;
            st.hotSeatFuse = 0;
        }
        if (st.rule == HouseRule.SPOTLIGHT) {
            st.spotlightTarget = null;
        }
        if (st.rule == HouseRule.JACKPOT) {
            st.jackpotArmed = false;
        }

        HouseRule next = rollRule(w, st.rule);
        st.rule = next;
        st.ruleLeft = HOUSE_RULE_INTERVAL_TICKS;

        announceRule(w, st, next);

        switch (next) { // rules that just apply potion effects
            case LOADED_DICE -> applyToOwner(w, st,
                    new StatusEffectInstance(StatusEffects.STRENGTH, ruleEffectDurationTicks(), 1, true, false));

            case VIP_PASS -> applyToOwner(w, st,
                    new StatusEffectInstance(StatusEffects.REGENERATION, ruleEffectDurationTicks(), 0, true, false));

            case FREE_DRINKS -> applyToAll(w, st,
                    new StatusEffectInstance(StatusEffects.REGENERATION, ruleEffectDurationTicks(), 0, true, false));

            case NO_RUNNING -> applyToAll(w, st,
                    new StatusEffectInstance(StatusEffects.SLOWNESS, ruleEffectDurationTicks(), 1, true, false));

            case NO_FIGHTING -> applyToAll(w, st,
                    new StatusEffectInstance(StatusEffects.WEAKNESS, ruleEffectDurationTicks(), 1, true, false));

            // other

            case DOUBLE_OR_NOTHING -> w.spawnParticles(ParticleTypes.ENCHANT,
                    st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                    18, 0.9, 0.35, 0.9, 0.0);

            case SHUFFLE -> doShuffle(w, st);

            case ROULETTE -> doRoulette(w, st);

            case LIGHTNING_ROUND -> w.playSound(null, st.center, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.4f);

            case HOT_SEAT -> startHotSeat(w, st);

            case CHIP_TOSS -> doChipToss(w, st);

            case WILDCARDS -> spawnWildcards(w, st);

            case SMOKE_MACHINE -> {
                applyToAll(w, st, new StatusEffectInstance(StatusEffects.BLINDNESS, ruleEffectDurationTicks(), 0, true, false));
                w.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                        25, 1.2, 0.6, 1.2, 0.02);
            }

            case SPOTLIGHT -> startSpotlight(w, st);

            case JACKPOT -> armJackpot(w, st);

            case CARD_COUNTER -> doCardCounter(w, st);

            case WOOLIAM_INVASION -> spawnWooliamInvasion(w, st);
        }
    }

    private static HouseRule rollRule(ServerWorld w, HouseRule current) {
        if (w.random.nextFloat() < RULE_WOOLIAM_CHANCE) {
            return HouseRule.WOOLIAM_INVASION;
        }

        HouseRule[] all = HouseRule.values();
        HouseRule pick;
        int guard = 0;
        do {
            pick = all[w.random.nextInt(all.length)];
            guard++;
        } while ((pick == current || pick == HouseRule.WOOLIAM_INVASION) && guard < 10);

        return pick;
    }

    private static void announceRule(ServerWorld w, HouseState st, HouseRule rule) {
        String name = switch (rule) { // rule names
            case LOADED_DICE -> "Loaded Dice";
            case FREE_DRINKS -> "Free Drinks";
            case VIP_PASS -> "VIP Pass";
            case NO_RUNNING -> "No Running";
            case NO_FIGHTING -> "No Fighting";
            case DOUBLE_OR_NOTHING -> "Double or Nothing";
            case SHUFFLE -> "Shuffle";
            case ROULETTE -> "Roulette";
            case LIGHTNING_ROUND -> "Lightning Round";
            case HOT_SEAT -> "Hot Seat";
            case CHIP_TOSS -> "Chip Toss";
            case WILDCARDS -> "Wildcards";
            case SMOKE_MACHINE -> "Smoke Machine";
            case SPOTLIGHT -> "Spotlight";
            case JACKPOT -> "Jackpot";
            case CARD_COUNTER -> "Card Counter";
            case WOOLIAM_INVASION -> "Wooliam";
        };

        String desc = switch (rule) { // rule descriptions
            case LOADED_DICE -> "Owner gains Strength.";
            case FREE_DRINKS -> "Everyone gains Regeneration.";
            case VIP_PASS -> "Owner gains Regeneration.";
            case NO_RUNNING -> "Everyone gets Slowness.";
            case NO_FIGHTING -> "Everyone gets Weakness.";
            case DOUBLE_OR_NOTHING -> "Everyone deals and receives increased damage.";
            case SHUFFLE -> "Everyone swaps positions.";
            case ROULETTE -> "A random entity is damaged";
            case LIGHTNING_ROUND -> "Everyone takes periodic damage.";
            case HOT_SEAT -> "Someone is marked; hit to pass it on before it detonates.";
            case CHIP_TOSS -> "Some people will be launched.";
            case WILDCARDS -> "Random entities are spawned.";
            case SMOKE_MACHINE -> "Everyone is blinded.";
            case SPOTLIGHT -> "Someone is glowing and takes extra damage.";
            case JACKPOT -> "The next hit is amplified.";
            case CARD_COUNTER -> "Owner's luck is set to the max.";
            case WOOLIAM_INVASION -> "";
        };

        Text msg = Text.literal("§6§l[HOUSE RULE]§r §e" + name + " §7- " + desc); // outputs rule

        int playerCount = 0;
        for (UUID u : st.inside) {
            Entity e = w.getEntity(u);
            if (e instanceof ServerPlayerEntity sp) {
                playerCount++;
                sp.sendMessage(msg, false);
            }
        }

        if (playerCount >= HOUSE_RULE_MIN_PLAYERS_TO_ANNOUNCE) {
            w.playSound(null, st.center, ModSounds.NEWRULE,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 0.9f);
        }
    }

    // helper methods for effects
    private static void applyToOwner(ServerWorld w, HouseState st, StatusEffectInstance fx) {
        Entity e = w.getEntity(st.owner);
        if (e instanceof LivingEntity le && le.isAlive()) le.addStatusEffect(fx);
    }

    private static void applyToAll(ServerWorld w, HouseState st, StatusEffectInstance fx) {
        for (LivingEntity e : getHouseLiving(w, st)) e.addStatusEffect(fx);
    }

    private static List<LivingEntity> getHouseLiving(ServerWorld w, HouseState st) {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID u : st.inside) {
            Entity e = w.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) out.add(le);
        }
        return out;
    }

    /* rule methods */

    private static void doShuffle(ServerWorld w, HouseState st) { // swaps around entities
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.size() <= 1) return;

        List<Vec3d> positions = new ArrayList<>(ents.size());
        List<Float> yaws = new ArrayList<>(ents.size());
        List<Float> pitches = new ArrayList<>(ents.size());

        for (LivingEntity e : ents) {
            positions.add(e.getPos());
            yaws.add(e.getYaw());
            pitches.add(e.getPitch());
        }

        int n = ents.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        for (int i = n - 1; i > 0; i--) {
            int j = w.random.nextInt(i + 1);
            int tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp;
        }

        for (int i = 0; i < n; i++) {
            LivingEntity e = ents.get(i);
            Vec3d p = positions.get(idx[i]);
            float yaw = yaws.get(idx[i]);
            float pitch = pitches.get(idx[i]);
            teleportEntity(w, e, p, yaw, pitch);

            w.spawnParticles(ParticleTypes.POOF,
                    p.x, p.y + e.getHeight() * 0.5, p.z,
                    8, 0.25, 0.25, 0.25, 0.02);
        }

        w.playSound(null, st.center, SoundEvents.BLOCK_END_PORTAL_SPAWN,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    private static void doRoulette(ServerWorld w, HouseState st) { // does not select owner
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        ents.removeIf(e -> e.getUuid().equals(st.owner));
        if (ents.isEmpty()) return;

        LivingEntity pick = ents.get(w.random.nextInt(ents.size()));

        w.spawnParticles(ParticleTypes.CRIT,
                pick.getX(), pick.getY() + pick.getHeight() * 0.6, pick.getZ(),
                16, 0.35, 0.35, 0.35, 0.08);

        Entity owner = w.getEntity(st.owner);
        pick.damage(ModDamageTypes.house(w, owner), RULE_ROULETTE_DAMAGE); // house deals damage

        if (pick instanceof ServerPlayerEntity sp) {
            sp.sendMessage(Text.literal("§cRoulette chose you!"), false);
        }

        w.playSound(null, pick.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.9f, 0.7f);
    }

    // hot potato
    private static void startHotSeat(ServerWorld w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        LivingEntity pick = ents.get(w.random.nextInt(ents.size()));
        st.hotSeatHolder = pick.getUuid();
        st.hotSeatFuse = RULE_HOTSEAT_FUSE_TICKS;

        w.spawnParticles(ParticleTypes.ENCHANT,
                pick.getX(), pick.getY() + pick.getHeight() * 0.7, pick.getZ(),
                18, 0.35, 0.35, 0.35, 0.0);
    }

    private static void tickHotSeat(ServerWorld w, HouseState st) {
        if (st.hotSeatHolder == null) {
            startHotSeat(w, st);
            return;
        }

        Entity e = w.getEntity(st.hotSeatHolder);
        if (!(e instanceof LivingEntity holder) || !holder.isAlive() || !st.inside.contains(st.hotSeatHolder)) {
            st.hotSeatHolder = null;
            st.hotSeatFuse = 0;
            startHotSeat(w, st);
            return;
        }

        if ((w.getTime() % RULE_HOTSEAT_PARTICLES_EVERY_TICKS) == 0L) {
            w.spawnParticles(ParticleTypes.ENCHANT,
                    holder.getX(), holder.getY() + holder.getHeight() * 0.6, holder.getZ(),
                    10, 0.25, 0.35, 0.25, 0.0);
            w.spawnParticles(ParticleTypes.CRIT,
                    holder.getX(), holder.getY() + holder.getHeight() * 0.6, holder.getZ(),
                    2, 0.15, 0.15, 0.15, 0.02);
        }

        st.hotSeatFuse--;
        if (st.hotSeatFuse > 0) return;

        w.spawnParticles(ParticleTypes.CLOUD,
                holder.getX(), holder.getY() + holder.getHeight() * 0.5, holder.getZ(),
                18, 0.35, 0.25, 0.35, 0.03);

        Entity owner = w.getEntity(st.owner);
        holder.damage(ModDamageTypes.house(w, owner), RULE_HOTSEAT_DAMAGE); // house deals damage

        st.hotSeatHolder = null;
        st.hotSeatFuse = 0;
        startHotSeat(w, st);

        w.playSound(null, st.center, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
                net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.25f);
    }

    // if current holder does damage pass the hot potato
    private static void tryPassHotSeatOnHit(ServerWorld w, HouseState st, LivingEntity attacker, LivingEntity victim) {
        if (st.rule != HouseRule.HOT_SEAT) return;
        if (st.hotSeatHolder == null) return;
        if (!attacker.getUuid().equals(st.hotSeatHolder)) return;

        LivingEntity newHolder = victim;

        if (!st.inside.contains(victim.getUuid())) {
            List<LivingEntity> options = getHouseLiving(w, st);
            options.removeIf(ent -> ent.getUuid().equals(attacker.getUuid()));
            if (options.isEmpty()) return;
            newHolder = options.get(w.random.nextInt(options.size()));
        }

        st.hotSeatHolder = newHolder.getUuid();
        st.hotSeatFuse = RULE_HOTSEAT_FUSE_TICKS;

        w.spawnParticles(ParticleTypes.ENCHANT,
                attacker.getX(), attacker.getY() + attacker.getHeight() * 0.6, attacker.getZ(),
                10, 0.25, 0.25, 0.25, 0.0);
        w.spawnParticles(ParticleTypes.ENCHANT,
                newHolder.getX(), newHolder.getY() + newHolder.getHeight() * 0.6, newHolder.getZ(),
                14, 0.25, 0.25, 0.25, 0.0);

        w.playSound(null, newHolder.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 1.35f);
    }

    // throw random entities in air, ignores owner
    private static void doChipToss(ServerWorld w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        if (ents.isEmpty()) return;

        ents.removeIf(e -> e.getUuid().equals(st.owner));
        if (ents.isEmpty()) return;

        Collections.shuffle(ents, new Random(w.random.nextLong()));
        int count = Math.max(1, Math.round(ents.size() * RULE_CHIP_TOSS_FRACTION));

        for (int i = 0; i < count; i++) {
            LivingEntity e = ents.get(i);

            double up = RULE_CHIP_TOSS_UP_MIN + w.random.nextDouble() * (RULE_CHIP_TOSS_UP_MAX - RULE_CHIP_TOSS_UP_MIN);
            double sx = (w.random.nextDouble() * 2 - 1) * RULE_CHIP_TOSS_SIDE;
            double sz = (w.random.nextDouble() * 2 - 1) * RULE_CHIP_TOSS_SIDE;

            e.addVelocity(sx, up, sz);
            e.velocityDirty = true;

            w.spawnParticles(ParticleTypes.CRIT,
                    e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ(),
                    10, 0.25, 0.35, 0.25, 0.10);
        }

        w.spawnParticles(ParticleTypes.ENCHANT,
                st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                18, 1.0, 0.35, 1.0, 0.0);
    }

    // spawns random entities
    private static void spawnWildcards(ServerWorld w, HouseState st) {
        int n = RULE_WILDCARDS_MIN + w.random.nextInt(RULE_WILDCARDS_MAX - RULE_WILDCARDS_MIN + 1);

        net.minecraft.entity.EntityType[] pool = new net.minecraft.entity.EntityType[] { // entities that can spawn
                net.minecraft.entity.EntityType.ZOMBIE,
                net.minecraft.entity.EntityType.SKELETON,
                net.minecraft.entity.EntityType.SPIDER,
                net.minecraft.entity.EntityType.HUSK,
                net.minecraft.entity.EntityType.STRAY
        };

        for (int i = 0; i < n; i++) {
            net.minecraft.entity.EntityType type = pool[w.random.nextInt(pool.length)];
            Entity created = type.create(w);
            if (!(created instanceof LivingEntity mob)) continue;

            BlockPos p = randomInsidePos(w, st, 2);
            mob.refreshPositionAndAngles(
                    p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5,
                    w.random.nextFloat() * 360f, 0f
            );

            w.spawnEntity(mob);

            w.spawnParticles(ParticleTypes.POOF,
                    mob.getX(), mob.getY() + mob.getHeight() * 0.5, mob.getZ(),
                    10, 0.25, 0.25, 0.25, 0.02);
        }
    }

    // EGG - SEND THE WOOLIAMS
    private static void spawnWooliamInvasion(ServerWorld w, HouseState st) {
        // 20 woolliam
        for (int i = 0; i < 20; i++) {
            net.minecraft.entity.passive.SheepEntity sheep = net.minecraft.entity.EntityType.SHEEP.create(w);
            if (sheep != null) {
                BlockPos p = randomInsidePos(w, st, 1);
                sheep.refreshPositionAndAngles(p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
                sheep.setCustomName(Text.literal("wooliam"));
                w.spawnEntity(sheep);
                w.spawnParticles(ParticleTypes.POOF, sheep.getX(), sheep.getY() + 0.5, sheep.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
            }
        }
        // 1 hamuel
        net.minecraft.entity.passive.PigEntity pig = net.minecraft.entity.EntityType.PIG.create(w);
        if (pig != null) {
            BlockPos p = randomInsidePos(w, st, 1);
            pig.refreshPositionAndAngles(p.getX() + 0.5, st.baseY + 0.1, p.getZ() + 0.5, w.random.nextFloat() * 360f, 0f);
            pig.setCustomName(Text.literal("hamuel"));
            w.spawnEntity(pig);
            w.spawnParticles(ParticleTypes.POOF, pig.getX(), pig.getY() + 0.5, pig.getZ(), 5, 0.2, 0.2, 0.2, 0.02);
        }
        w.playSound(null, st.center, SoundEvents.ENTITY_SHEEP_AMBIENT, net.minecraft.sound.SoundCategory.PLAYERS, 1.5f, 1.0f);
    }

    // gets random spot in area, for mob spawning
    private static BlockPos randomInsidePos(ServerWorld w, HouseState st, int margin) {
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int r = Math.max(1, HOUSE_RADIUS - margin);
        int dx = w.random.nextInt(r * 2 + 1) - r;
        int dz = w.random.nextInt(r * 2 + 1) - r;

        return new BlockPos(cx + dx, st.baseY, cz + dz);
    }

    // smoke particles
    private static void spawnSmokeMachineFx(ServerWorld w, HouseState st) {
        for (LivingEntity e : getHouseLiving(w, st)) {
            w.spawnParticles(ParticleTypes.SMOKE,
                    e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ(),
                    RULE_SMOKE_PARTICLES_PER_ENTITY,
                    0.45, 0.35, 0.45,
                    0.01);
        }
    }

    // pick a target and give them effects
    private static void startSpotlight(ServerWorld w, HouseState st) {
        LivingEntity target = pickSpotlightTarget(w, st);
        if (target == null) {
            st.spotlightTarget = null;
            return;
        }

        st.spotlightTarget = target.getUuid();
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, ruleEffectDurationTicks(), 0, true, false));

        w.spawnParticles(ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getHeight() * 0.7, target.getZ(),
                18, 0.35, 0.35, 0.35, 0.0);
    }

    private static void tickSpotlight(ServerWorld w, HouseState st) {
        if (st.spotlightTarget == null) {
            startSpotlight(w, st); // selects new target if target dead
            return;
        }

        Entity e = w.getEntity(st.spotlightTarget);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !st.inside.contains(st.spotlightTarget)) {
            st.spotlightTarget = null;
            startSpotlight(w, st);
            return;
        }

        w.spawnParticles(ParticleTypes.CRIT,
                le.getX(), le.getY() + le.getHeight() * 0.8, le.getZ(),
                2, 0.25, 0.25, 0.25, 0.02);
    }

    private static LivingEntity pickSpotlightTarget(ServerWorld w, HouseState st) {
        List<LivingEntity> ents = getHouseLiving(w, st);
        ents.removeIf(e -> e.getUuid().equals(st.owner)); // cannot be caster
        if (ents.isEmpty()) return null;
        return ents.get(w.random.nextInt(ents.size()));
    }

    // start jackpot
    private static void armJackpot(ServerWorld w, HouseState st) {
        st.jackpotArmed = true;
        w.spawnParticles(ParticleTypes.ENCHANT,
                st.center.getX() + 0.5, st.baseY + 1.2, st.center.getZ() + 0.5,
                22, 0.8, 0.35, 0.8, 0.0);
    }

    // set passive luck to max
    private static void doCardCounter(ServerWorld w, HouseState st) {
        Entity owner = w.getEntity(st.owner);
        if (owner instanceof ServerPlayerEntity sp && sp.isAlive()) {
            FortuneState state = getState(sp);
            state.luck = LUCK_MAX;
            state.luckDecayTicks = LUCK_DECAY_DELAY_TICKS;

            w.spawnParticles(ParticleTypes.ENCHANT,
                    sp.getX(), sp.getY() + 1.0, sp.getZ(),
                    22, 0.45, 0.55, 0.45, 0.0);
        }
    }

    // damage hook: decides what rule applies to the damage event
    public static boolean tryAdjustFortuneDamage(LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0) return false;

        Entity atkEnt = source.getAttacker();
        LivingEntity attacker = (atkEnt instanceof LivingEntity le) ? le : null;

        // recursion guard via global hashset
        if (victim.getUuid() != null && DAMAGE_GUARDS.contains(victim.getUuid())) return false;
        if (attacker != null && DAMAGE_GUARDS.contains(attacker.getUuid())) return false;

        if (!(victim.getWorld() instanceof ServerWorld w)) return false;

        // find house relevant to attacker
        HouseState hs = null;
        if (attacker != null) hs = findHouseForEntity(w, attacker.getUuid());
        if (hs == null) hs = findHouseForEntity(w, victim.getUuid());

        // pass hot potato
        if (hs != null && attacker != null) {
            tryPassHotSeatOnHit(w, hs, attacker, victim);
        }

        float mult = 1.0f;
        DamageSource finalSource = source;

        // double or nothing damage boost
        if (hs != null && hs.rule == HouseRule.DOUBLE_OR_NOTHING) {
            boolean aIn = (attacker != null && hs.inside.contains(attacker.getUuid()));
            boolean vIn = hs.inside.contains(victim.getUuid());
            if (aIn || vIn) mult *= RULE_DOUBLE_MULT;
        }

        // Spotlight
        if (hs != null && hs.rule == HouseRule.SPOTLIGHT && hs.spotlightTarget != null) {
            if (victim.getUuid().equals(hs.spotlightTarget)) {
                mult *= RULE_SPOTLIGHT_MULT;
            }
        }

        // Jackpot - only applied once
        if (hs != null && hs.rule == HouseRule.JACKPOT && hs.jackpotArmed) {
            boolean aIn = (attacker != null && hs.inside.contains(attacker.getUuid()));
            boolean vIn = hs.inside.contains(victim.getUuid());
            if (attacker != null && aIn && vIn) {
                mult *= RULE_JACKPOT_MULT;
                hs.jackpotArmed = false;
                finalSource = ModDamageTypes.house(w, attacker); // uses house damage type for jackpot

                w.spawnParticles(ParticleTypes.FIREWORK,
                        victim.getX(), victim.getY() + victim.getHeight() * 0.6, victim.getZ(),
                        1, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.CRIT,
                        victim.getX(), victim.getY() + victim.getHeight() * 0.6, victim.getZ(),
                        RULE_JACKPOT_FX_PARTICLES, 0.35, 0.35, 0.35, 0.12);

                net.minecraft.sound.SoundEvent jpSound = w.random.nextInt(FUNNY_CHANCE) == 0 ? ModSounds.JACKPOTFUNNY : ModSounds.JACKPOT;
                w.playSound(null, victim.getBlockPos(), jpSound, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }

        // Duel multiplier - not from ultimate
        if (attacker != null) {
            mult *= getDuelMultiplier(victim, attacker);
        }

        if (Math.abs(mult - 1.0f) < 1.0e-4f && finalSource == source) return false;

        float newAmount = amount * mult;

        if (victim.getUuid() != null) DAMAGE_GUARDS.add(victim.getUuid());
        if (attacker != null) DAMAGE_GUARDS.add(attacker.getUuid());
        try {
            victim.damage(finalSource, newAmount);
        } finally {
            if (victim.getUuid() != null) DAMAGE_GUARDS.remove(victim.getUuid());
            if (attacker != null) DAMAGE_GUARDS.remove(attacker.getUuid());
        }
        return true; // cancel original damage in hook
    }

    private static HouseState findHouseForEntity(ServerWorld w, UUID u) {
        RegistryKey<World> key = w.getRegistryKey();
        for (HouseState st : ACTIVE_HOUSES.values()) {
            if (!st.worldKey.equals(key)) continue;
            if (st.inside.contains(u)) return st;
        }
        return null;
    }

    /* cage building etc */

    private static void placeHouseFloor(ServerWorld w, HouseState st) {
        BlockState floor = ModBlocks.CASINO_FLOOR.getDefaultState();
        if (floor.contains(Properties.WATERLOGGED)) floor = floor.with(Properties.WATERLOGGED, false);

        int cx = st.center.getX();
        int cz = st.center.getZ();
        int yFloor = st.baseY - 1;

        for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
            for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                setBlockForced(w, new BlockPos(cx + dx, yFloor, cz + dz), floor, st);
            }
        }
    }

    private static void clearHouseInteriorAbove(ServerWorld w, HouseState st) {
        int cx = st.center.getX();
        int cz = st.center.getZ();
        int inner = Math.max(0, HOUSE_RADIUS - 1);

        for (int dy = 0; dy < HOUSE_CLEAR_HEIGHT; dy++) {
            int y = st.baseY + dy;
            for (int dx = -inner; dx <= inner; dx++) {
                for (int dz = -inner; dz <= inner; dz++) {
                    clearToAirIfBreakable(w, new BlockPos(cx + dx, y, cz + dz));
                }
            }
        }
    }

    private static void placeHouseBarsLayer(ServerWorld w, HouseState st, int layer) {
        BlockState bars = ModBlocks.CASINO_BARS.getDefaultState();
        if (bars.contains(Properties.WATERLOGGED)) bars = bars.with(Properties.WATERLOGGED, false);

        int cx = st.center.getX();
        int cz = st.center.getZ();
        int y  = st.baseY + layer;

        List<BlockPos> placedThisLayer = new ArrayList<>();

        for (int dx = -HOUSE_RADIUS; dx <= HOUSE_RADIUS; dx++) {
            for (int dz = -HOUSE_RADIUS; dz <= HOUSE_RADIUS; dz++) {
                boolean edge = (Math.abs(dx) == HOUSE_RADIUS) || (Math.abs(dz) == HOUSE_RADIUS);
                if (!edge) continue;

                BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                clearToAirIfBreakable(w, p);
                setBlockForced(w, p, bars, st);
                placedThisLayer.add(p);
            }
        }

        for (BlockPos p : placedThisLayer) {
            BlockState cur = w.getBlockState(p);
            if (!cur.isOf(ModBlocks.CASINO_BARS)) continue;

            BlockState fixed = computeConnectedBarsState(w, p, cur);
            if (!fixed.equals(cur)) w.setBlockState(p, fixed, 3);
        }
    }

    private static BlockState computeConnectedBarsState(ServerWorld w, BlockPos pos, BlockState base) { // builds the bars so they don't appear in + shape
        BlockState s = base;

        if (s.contains(Properties.NORTH)) s = s.with(Properties.NORTH, shouldBarsConnect(w, pos, Direction.NORTH));
        if (s.contains(Properties.EAST))  s = s.with(Properties.EAST,  shouldBarsConnect(w, pos, Direction.EAST));
        if (s.contains(Properties.SOUTH)) s = s.with(Properties.SOUTH, shouldBarsConnect(w, pos, Direction.SOUTH));
        if (s.contains(Properties.WEST))  s = s.with(Properties.WEST,  shouldBarsConnect(w, pos, Direction.WEST));

        if (s.contains(Properties.WATERLOGGED)) s = s.with(Properties.WATERLOGGED, false);
        return s;
    }

    private static boolean shouldBarsConnect(ServerWorld w, BlockPos pos, Direction dir) { // this sucks
        BlockPos np = pos.offset(dir);
        BlockState nb = w.getBlockState(np);

        if (nb.isOf(ModBlocks.CASINO_BARS)) return true;
        return nb.isSideSolidFullSquare(w, np, dir.getOpposite());
    }

    private static void enforceHouseBuildCeiling(ServerWorld w, HouseState st) { // breaks block n-2 from wall height
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int breakFromY = st.baseY + (HOUSE_WALL_LAYERS - HOUSE_BREAK_FROM_TOP_OFFSET);
        int yMax = breakFromY + HOUSE_BREAK_SCAN_EXTRA_Y;

        int r = HOUSE_RADIUS + HOUSE_BREAK_MARGIN;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = cx + dx;
                int z = cz + dz;

                for (int y = breakFromY; y <= yMax; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!w.isInBuildLimit(p)) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    BlockState bs = w.getBlockState(p);
                    if (bs.isAir()) continue;

                    if (bs.isOf(ModBlocks.CASINO_BARS) || bs.isOf(ModBlocks.CASINO_FLOOR)) continue;

                    float hardness = bs.getHardness(w, p);
                    if (hardness < 0) continue;

                    w.spawnParticles(ParticleTypes.ENCHANT,
                            p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5,
                            HOUSE_BREAK_ENCHANT_PARTICLES,
                            0.25, 0.25, 0.25, 0.0);

                    w.syncWorldEvent(2001, p, net.minecraft.block.Block.getRawIdFromState(bs));
                    w.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }

    private static void updateHouseInside(ServerWorld w, HouseState st) { // maintain members
        int cx = st.center.getX();
        int cz = st.center.getZ();

        int yMin = st.baseY - 1;
        int yMax = st.baseY + HOUSE_WALL_LAYERS + 12;

        Box box = new Box(
                cx - HOUSE_RADIUS, yMin, cz - HOUSE_RADIUS,
                cx + HOUSE_RADIUS + 1, yMax, cz + HOUSE_RADIUS + 1
        );

        List<LivingEntity> insideNow = w.getEntitiesByClass(LivingEntity.class, box, ent -> ent.isAlive());
        Set<UUID> newInside = new HashSet<>();

        for (LivingEntity ent : insideNow) {
            BlockPos p = ent.getBlockPos();
            int dx = p.getX() - cx;
            int dz = p.getZ() - cz;

            if (Math.abs(dx) <= HOUSE_RADIUS && Math.abs(dz) <= HOUSE_RADIUS) {
                newInside.add(ent.getUuid());
                if (!st.inside.contains(ent.getUuid())) {
                    w.spawnParticles(ParticleTypes.ENCHANT,
                            ent.getX(), ent.getY() + ent.getHeight() * 0.6, ent.getZ(),
                            8, 0.25, 0.25, 0.25, 0.0);
                }
            }
        }

        st.inside.clear();
        st.inside.addAll(newInside);
    }

    private static void spawnHouseRoofFx(ServerWorld w, HouseState st) {
        int cx = st.center.getX();
        int cz = st.center.getZ();

        double y = st.baseY + (HOUSE_WALL_LAYERS - 1) + HOUSE_ROOF_FX_Y_OFFSET;

        for (int i = 0; i < HOUSE_ROOF_FX_PARTICLES; i++) {
            double x = cx + 0.5 + (w.random.nextDouble() * 2 - 1) * HOUSE_RADIUS;
            double z = cz + 0.5 + (w.random.nextDouble() * 2 - 1) * HOUSE_RADIUS;
            w.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private static void teleportEntity(ServerWorld w, Entity e, Vec3d pos, float yaw, float pitch) {
        if (e instanceof ServerPlayerEntity sp) {
            sp.teleport(w, pos.x, pos.y, pos.z, yaw, pitch);
        } else {
            e.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, pitch);
            e.setVelocity(Vec3d.ZERO);
            e.velocityDirty = true;
        }
    }

    private static void removeHouseNow(net.minecraft.server.MinecraftServer server, UUID owner) {
        HouseState st = ACTIVE_HOUSES.remove(owner);
        if (st == null) return;

        ServerWorld w = server.getWorld(st.worldKey);
        if (w == null) return;

        st.inside.clear();

        for (BlockPos p : st.placed) {
            BlockState cur = w.getBlockState(p);
            if (cur.isOf(ModBlocks.CASINO_BARS) || cur.isOf(ModBlocks.CASINO_FLOOR)) {
                w.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            }
        }
        st.placed.clear();

        w.playSound(null, st.center, SoundEvents.BLOCK_CHAIN_BREAK,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.85f, 1.0f);

        w.spawnParticles(ParticleTypes.CLOUD,
                st.center.getX() + 0.5, st.baseY + 1.0, st.center.getZ() + 0.5,
                40, 0.9, 0.45, 0.9, 0.05);
    }

    private static void setBlockForced(ServerWorld w, BlockPos pos, BlockState state, HouseState st) {
        if (!w.isInBuildLimit(pos)) return;
        if (w.getBlockEntity(pos) != null) return;

        BlockState existing = w.getBlockState(pos);
        float hardness = existing.getHardness(w, pos);
        if (hardness < 0) return; // unbreakable

        BlockState place = state;
        if (place.contains(Properties.WATERLOGGED)) place = place.with(Properties.WATERLOGGED, false);

        w.setBlockState(pos, place, 3);
        st.placed.add(pos);
    }

    private static void clearToAirIfBreakable(ServerWorld w, BlockPos pos) {
        if (!w.isInBuildLimit(pos)) return;
        if (w.getBlockEntity(pos) != null) return;

        BlockState existing = w.getBlockState(pos);
        if (existing.isAir()) return;

        // clear all waterlogged stuff
        if (!existing.getFluidState().isEmpty()) {
            w.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
            return;
        }

        float hardness = existing.getHardness(w, pos);
        if (hardness < 0) return; // unbreakable

        w.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
    }

    // COOLDOWNS
    @Override public String getName() { return "Fortune"; }
    @Override public String getPrimaryName() { return "All In"; }
    @Override public String getSecondaryName() { return "Raise The Stakes"; }
    @Override public String getUltimateName() { return "House Rule"; }

    @Override public long getPrimaryCooldownMs() { return 26_000; }
    @Override public long getSecondaryCooldownMs() { return 48_000; }
    @Override public long getUltimateCooldownMs() { return 540_000; }

    @Override
    public String getOverviewDescription() {
        return "Yes, this is a gambling power, I just needed a cooler name. Fortune is mainly centered around risk and reward, where your abilities benefit you"
                + " but also have costs. You have a hidden luck stat that builds when hitting entities and can be enhanced by this stat, so to a degree, the longer you're"
                + " in a fight the more effective you are.";
    }

    @Override
    public String getPassiveName() {
        return "Growing Odds";
    }

    @Override
    public String getPassiveDescription() {
        return "You have a hidden luck stat that increases when hitting entities and decreases when out of combat. This stat improves chances for jackpots on ability uses or hits. This stat is spent when a jackpot triggers, and you will receive an actionbar alert to what jackpot was used.\n\n"
                + "On-hit Jackpots:\n"
                + "• Favour: Grants you Speed and Absorption.\n"
                + "• Lucky Shot: Deals extra damage to the target.\n"
                + "• Bad Beat: Inflicts Weakness on the target.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Damage yourself and gain Strength and Speed. You have a chance of hitting 1 of 3 jackpots (odds scale with your luck passive).\n\n"
                + "Primary Jackpots:\n"
                + "• Raise: Upgrades your buff to Strength II.\n"
                + "• Draw No Bet: Grants you brief Regeneration.\n"
                + "• Double Time: Doubles the duration of your base buffs.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Shoot a beam that on hit, will put you into a soft duel with the target; you will both deal 50% more damage "
                + "to each other and take 50% less damage from other sources.";
    }

    @Override
    public String getUltimateDescription() {
        return "Summon a magical casino cage that quickly builds from your feet, trapping anything inside for a short time. Players can't build out of this but can break/teleport out. Every few seconds, a new House Rule is rolled.\n\n"
                + "House Rules:\n"
                + "• Loaded Dice: Owner gains Strength.\n"
                + "• Free Drinks: Everyone gains Regeneration.\n"
                + "• VIP Pass: Owner gains Regeneration.\n"
                + "• No Running: Everyone gets Slowness.\n"
                + "• No Fighting: Everyone gets Weakness.\n"
                + "• Double or Nothing: Everyone deals and receives increased damage.\n"
                + "• Shuffle: Everyone swaps positions.\n"
                + "• Roulette: A random entity is damaged.\n"
                + "• Lightning Round: Everyone takes periodic damage.\n"
                + "• Hot Seat: Someone is marked; hit someone to pass it on before it detonates.\n"
                + "• Chip Toss: Some people are launched into the air.\n"
                + "• Wildcards: Random hostile entities are spawned.\n"
                + "• Smoke Machine: Everyone is blinded.\n"
                + "• Spotlight: Someone is marked to glow and takes extra damage.\n"
                + "• Jackpot: The next hit in the room is heavily amplified.\n"
                + "• Card Counter: Owner's luck is instantly set to the maximum.";
    }
}