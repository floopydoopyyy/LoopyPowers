package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.RenderPackets;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.yourname.loopypowers.entity.ShadowStepEntity;

import java.util.*;

import static com.yourname.loopypowers.entity.ModEntities.SHADOW_STEP;

public class DarknessPower implements Power {

    /* ============================================================
       STATE STORAGE
       ============================================================ */

    private static final Map<UUID, Integer> ACTIVE_MISTS = new HashMap<>();
    private boolean applyingDarknessDamage = false;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        removeDarknessTags(player);
        removeBlackoutNow(player.getServer(), player.getUuid());
        ACTIVE_MISTS.remove(player.getUuid());
        player.setNoGravity(false);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeDarknessTags(player);
        removeBlackoutNow(player.getServer(), player.getUuid());
        ACTIVE_MISTS.remove(player.getUuid());
        player.setNoGravity(false);
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;
        handleMistForm(player);
        tickBlackoutsWorld(player.getServerWorld());
    }

    /* ============================================================
       DAMAGE HOOKS
       ============================================================ */

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        if (ACTIVE_MISTS.containsKey(victim.getUuid())) return false;
        return true;
    }

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0) return true;
        if (this.applyingDarknessDamage) return true;

        float mult = 1.0f;
        boolean didBackstab = false;
        boolean didExposed  = false;
        DamageSource finalSource = source;
        ServerWorld w = attacker.getServerWorld();

        if (isBehindTarget(attacker, target)) {
            mult *= BACKSTAB_BONUS_MULT;
            didBackstab = true;
            finalSource = ModDamageTypes.darknessBackstab(w, attacker);
        }

        if (target.hasStatusEffect(Registries.STATUS_EFFECT.getEntry(ModEffects.EXPOSED))) {
            mult *= EXPOSED_DAMAGE_MULT;
            didExposed = true;
            finalSource = ModDamageTypes.darkUlt(w, attacker);
        }

        if (!didBackstab && !didExposed) return true;

        float newAmount = amount * mult;

        this.applyingDarknessDamage = true;
        target.damage(finalSource, newAmount);
        this.applyingDarknessDamage = false;

        // Visuals → client
        double vx = target.getX(), vy = target.getBodyY(0.5), vz = target.getZ();
        Vec3d dir = attacker.getRotationVec(1.0f).normalize();

        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, target.getBlockPos()).forEach(viewers::add);
        viewers.add(attacker);

        if (didBackstab) {
            DarknessBackstabFxPayload bsPay = new DarknessBackstabFxPayload(vx, vy, vz, dir.x, dir.z);
            viewers.forEach(p -> ServerPlayNetworking.send(p, bsPay));
            w.playSound(null, target.getBlockPos(), ModSounds.BACKSTAB, attacker.getSoundCategory(), 0.7f, 1.0f);
        }
        if (didExposed) {
            DarknessExposedFxPayload exPay = new DarknessExposedFxPayload(vx, vy, vz, dir.x, dir.z);
            viewers.forEach(p -> ServerPlayNetworking.send(p, exPay));
            w.playSound(null, target.getBlockPos(), ModSounds.BIGSTAB, attacker.getSoundCategory(), 0.5f, 1.2f);
        }
        if (didBackstab && didExposed) {
            DarknessComboFxPayload comboPay = new DarknessComboFxPayload(vx, vy, vz);
            viewers.forEach(p -> ServerPlayNetworking.send(p, comboPay));
            w.playSound(null, target.getBlockPos(), ModSounds.BIGSTAB, attacker.getSoundCategory(), 0.9f, 0.8f);
            if (target instanceof ServerPlayerEntity targetPlayer) {
                CameraShake.shakeNearby(targetPlayer, 3, 10, 0.06f);
            }
        }

        return false;
    }

    /* ============================================================
       PASSIVE
       ============================================================ */

    private static final float BACKSTAB_BONUS_MULT = 1.40f;

    private static boolean isBehindTarget(LivingEntity attacker, LivingEntity victim) {
        if (attacker instanceof ServerPlayerEntity player) {
            if (!PassiveManager.isEnabled(player)) return false;
        }

        Vec3d victimForward = victim.getRotationVec(1.0f);
        Vec3d toAttacker    = attacker.getPos().subtract(victim.getPos());

        victimForward = new Vec3d(victimForward.x, 0.0, victimForward.z);
        toAttacker    = new Vec3d(toAttacker.x, 0.0, toAttacker.z);

        if (victimForward.lengthSquared() < 1.0e-4 || toAttacker.lengthSquared() < 1.0e-4) return false;

        return victimForward.normalize().dotProduct(toAttacker.normalize()) < -0.35;
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        ShadowStepEntity proj = new ShadowStepEntity(SHADOW_STEP, w);
        proj.setOwner(player);

        Vec3d look = player.getRotationVec(1.0f);
        proj.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
        proj.setVelocity(look.multiply(1.2));

        w.spawnEntity(proj);

        w.playSound(null, player.getBlockPos(),
                ModSounds.DARKNESSTELEPORT, player.getSoundCategory(), 0.6f, 1.4f);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    private static final int MIST_DURATION = 80;

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        RenderPackets.hidePlayerFromOthers(player, MIST_DURATION);

        w.playSound(null, player.getBlockPos(), ModSounds.MISTENTER, player.getSoundCategory(), 1.0f, 1.0f);

        // mist enter burst → client
        double mx = player.getX(), my = player.getBodyY(0.5), mz = player.getZ();
        DarknessMistEnterPayload enterPay = new DarknessMistEnterPayload(mx, my, mz);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, enterPay));

        ACTIVE_MISTS.put(player.getUuid(), MIST_DURATION);
        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void handleMistForm(ServerPlayerEntity player) {
        Integer ticks = ACTIVE_MISTS.get(player.getUuid());
        if (ticks == null) return;

        ticks--;
        if (ticks <= 0) {
            ACTIVE_MISTS.remove(player.getUuid());
            player.setNoGravity(false);
            return;
        }
        ACTIVE_MISTS.put(player.getUuid(), ticks);

        // mist trail → client
        ServerWorld w = player.getServerWorld();
        double mx = player.getX(), my = player.getBodyY(0.5), mz = player.getZ();
        DarknessMistTrailPayload trailPay = new DarknessMistTrailPayload(mx, my, mz);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, trailPay));

        if (ticks % 18 == 0) {
            w.playSound(null, player.getBlockPos(),
                    com.yourname.loopypowers.sound.ModSounds.MISTLOOP,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 2.0f);
        }

        player.removeStatusEffect(StatusEffects.SLOWNESS);

        Vec3d look = player.getRotationVec(1.0f);
        player.setVelocity(look.multiply(0.6));
        player.velocityModified = true;

        player.setOnGround(false);
        player.fallDistance = 0;
        player.setNoGravity(true);

        if (!player.getMainHandStack().isEmpty()) {
            player.getItemCooldownManager().set(player.getMainHandStack().getItem(), 5);
        }
        player.stopUsingItem();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS,    20, 5, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 50, 0, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,        50, 2, true, true));
    }

    /* ============================================================
       ULTIMATE
       ============================================================ */

    private static final int    BLACKOUT_RADIUS           = 16;
    private static final double BLACKOUT_HEIGHT           = 13.0;
    private static final int    BLACKOUT_DURATION_TICKS   = 20 * 10;
    private static final int    BLACKOUT_APPLY_EVERY_TICKS = 5;
    private static final int    BLACKOUT_FX_EVERY_TICKS   = 3;
    private static final float  EXPOSED_DAMAGE_MULT       = 1.60f;
    private static final float  FUNNY_SOUND_CHANCE        = 0.0005f;

    private static final Map<UUID, BlackoutState> ACTIVE_BLACKOUTS = new HashMap<>();
    private static final Map<RegistryKey<World>, Long> BLACKOUT_LAST_TICK = new HashMap<>();

    private static final class BlackoutState {
        final UUID owner;
        final RegistryKey<World> worldKey;
        final Vec3d center;
        int ticksLeft;

        BlackoutState(UUID owner, RegistryKey<World> worldKey, Vec3d center, int ticksLeft) {
            this.owner    = owner;
            this.worldKey = worldKey;
            this.center   = center;
            this.ticksLeft = ticksLeft;
        }
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        removeBlackoutNow(player.getServer(), player.getUuid());

        BlackoutState st = new BlackoutState(
                player.getUuid(), w.getRegistryKey(), player.getPos(), BLACKOUT_DURATION_TICKS);

        ACTIVE_BLACKOUTS.put(player.getUuid(), st);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.9f, 0.6f);

        // ult activate burst → client
        double cx = st.center.x, cy = st.center.y, cz = st.center.z;
        DarknessUltActivatePayload actPay = new DarknessUltActivatePayload(cx, cy, cz);
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(p -> ServerPlayNetworking.send(p, actPay));

        player.swingHand(Hand.MAIN_HAND, true);
    }

    public static void tickBlackoutsWorld(ServerWorld w) {
        long now = w.getTime();
        RegistryKey<World> key = w.getRegistryKey();

        Long last = BLACKOUT_LAST_TICK.get(key);
        if (last != null && last == now) return;
        BLACKOUT_LAST_TICK.put(key, now);

        if (ACTIVE_BLACKOUTS.isEmpty()) return;

        List<UUID> toRemove = new ArrayList<>();

        for (var entry : new ArrayList<>(ACTIVE_BLACKOUTS.entrySet())) {
            UUID owner = entry.getKey();
            BlackoutState st = entry.getValue();

            if (!st.worldKey.equals(key)) continue;

            st.ticksLeft--;
            if (st.ticksLeft <= 0) { toRemove.add(owner); continue; }

            if ((now % BLACKOUT_APPLY_EVERY_TICKS) == 0L) {
                applyBlackoutEffects(w, st);
            }

            if ((now % BLACKOUT_FX_EVERY_TICKS) == 0L) {
                // blackout sphere fx → client (send to all players within radius + nearby)
                DarknessBlackoutFxPayload fxPay = new DarknessBlackoutFxPayload(st.center.x, st.center.y, st.center.z);
                PlayerLookup.tracking(w, BlockPos.ofFloored(st.center)).forEach(p ->
                        ServerPlayNetworking.send(p, fxPay));
            }

            if ((now % 25L) == 0L) {
                playDarknessLoop(w, st);
            }

            if (w.random.nextFloat() < FUNNY_SOUND_CHANCE) {
                tryPlayFunnySound(w, st);
            }
        }

        for (UUID owner : toRemove) {
            removeBlackoutNow(w.getServer(), owner);
        }
    }

    private static void applyBlackoutEffects(ServerWorld w, BlackoutState st) {
        Box box = new Box(
                st.center.x - BLACKOUT_RADIUS, st.center.y - BLACKOUT_HEIGHT, st.center.z - BLACKOUT_RADIUS,
                st.center.x + BLACKOUT_RADIUS, st.center.y + BLACKOUT_HEIGHT, st.center.z + BLACKOUT_RADIUS
        );

        double radiusSq = (double) BLACKOUT_RADIUS * BLACKOUT_RADIUS;

        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (e.getUuid().equals(st.owner)) continue;

            double dx = e.getX() - st.center.x;
            double dz = e.getZ() - st.center.z;
            if (dx * dx + dz * dz > radiusSq) continue;

            e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,  40, 0, true, false));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING,    40, 0, true, false));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,   40, 0, true, false));
            e.addStatusEffect(new StatusEffectInstance(Registries.STATUS_EFFECT.getEntry(ModEffects.EXPOSED), 45, 0, true, false));
        }
    }

    private static void removeBlackoutNow(net.minecraft.server.MinecraftServer server, UUID owner) {
        if (server == null) return;

        BlackoutState st = ACTIVE_BLACKOUTS.remove(owner);
        if (st == null) return;

        ServerWorld w = server.getWorld(st.worldKey);
        if (w == null) return;

        w.playSound(null, BlockPos.ofFloored(st.center),
                SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 0.65f);

        // blackout end burst → client
        DarknessBlackoutEndPayload endPay = new DarknessBlackoutEndPayload(st.center.x, st.center.y, st.center.z);
        PlayerLookup.tracking(w, BlockPos.ofFloored(st.center)).forEach(p ->
                ServerPlayNetworking.send(p, endPay));
    }

    private static void playDarknessLoop(ServerWorld world, BlackoutState st) {
        double radiusSq = (double) BLACKOUT_RADIUS * BLACKOUT_RADIUS;
        for (ServerPlayerEntity p : world.getPlayers()) {
            double dx = p.getX() - st.center.x;
            double dz = p.getZ() - st.center.z;
            if ((dx * dx + dz * dz) > radiusSq) continue;
            world.playSound(null, p.getBlockPos(),
                    ModSounds.DARKNESSLOOP, p.getSoundCategory(), 0.6f, 1.0f);
        }
    }

    private static void tryPlayFunnySound(ServerWorld w, BlackoutState st) {
        List<ServerPlayerEntity> playersInside = new ArrayList<>();
        double radiusSq = (double) BLACKOUT_RADIUS * BLACKOUT_RADIUS;
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.squaredDistanceTo(st.center) <= radiusSq) playersInside.add(p);
        }
        if (!playersInside.isEmpty()) {
            ServerPlayerEntity victim = playersInside.get(w.random.nextInt(playersInside.size()));
            w.playSound(null, victim.getBlockPos(),
                    ModSounds.FUNNYFNAF, SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return Text.translatable("power.loopypowers.darkness.name").getString(); }
    @Override public String getPassiveName() { return Text.translatable("power.loopypowers.darkness.passive_name").getString(); }
    @Override public String getPrimaryName() { return Text.translatable("power.loopypowers.darkness.primary_name").getString(); }
    @Override public String getSecondaryName() { return Text.translatable("power.loopypowers.darkness.secondary_name").getString(); }
    @Override public String getUltimateName() { return Text.translatable("power.loopypowers.darkness.ultimate_name").getString(); }

    @Override public long getPrimaryCooldownMs()   { return 14_000; }
    @Override public long getSecondaryCooldownMs() { return 25_000; }
    @Override public long getUltimateCooldownMs()  { return 320_000; }

    @Override public String getOverviewDescription()  { return Text.translatable("power.loopypowers.darkness.description.overview").getString(); }
    @Override public String getPassiveDescription()   { return Text.translatable("power.loopypowers.darkness.description.passive").getString(); }
    @Override public String getPrimaryDescription()   { return Text.translatable("power.loopypowers.darkness.description.primary").getString(); }
    @Override public String getSecondaryDescription() { return Text.translatable("power.loopypowers.darkness.description.secondary").getString(); }
    @Override public String getUltimateDescription()  { return Text.translatable("power.loopypowers.darkness.description.ultimate").getString(); }

    /* ============================================================
       TAG HELPERS
       ============================================================ */

    private static void removeDarknessTags(Entity e) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith("dk_")) { it.remove(); return; }
        }
    }
}
