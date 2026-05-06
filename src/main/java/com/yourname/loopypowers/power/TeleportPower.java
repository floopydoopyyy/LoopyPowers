package com.yourname.loopypowers.power;

import com.yourname.loopypowers.CooldownUI;
import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.Entity;
import com.yourname.loopypowers.network.RenderPackets;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class TeleportPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, TeleportState> ACTIVE_STATES = new HashMap<>();

    private static class TeleportState {
        int blinkCharges = PRIMARY_MAX_CHARGES;
        int blinkRechargeTicks = -1;
        int blinkLockTicks = 0;

        int dodgeCdTicks = 0;
        int phaseTicks = 0;

        int frenzyTicks = 0;
        int frenzyStep = 0;
    }

    private static TeleportState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new TeleportState());
    }

    /* ============================================================
       CONSTANTS - PASSIVE
       ============================================================ */
    private static final float PASSIVE_DODGE_CHANCE = 0.18f; // percentage chance to dodge
    private static final int PASSIVE_HIDE_TICKS = 12;        // how long dodge invisibility effect is
    private static final int PASSIVE_IFRAME_TICKS = 18;      // window of invincibility
    private static final int PASSIVE_COOLDOWN_TICKS = 160;   // dodge cooldown in ticks

    /* ============================================================
       CONSTANTS - PRIMARY
       ============================================================ */
    private static final int PRIMARY_MAX_CHARGES = 3;
    private static final int PRIMARY_RECHARGE_TICKS = 120;           // 6 seconds per charge
    private static final int PRIMARY_LOCK_TICKS = 5;                 // tiny anti-spam delay between blinks
    private static final double PRIMARY_BLINK_DIST = 14.0;           // distance of the blink
    private static final double PRIMARY_AIR_LOOK_THRESHOLD = 0.55;   // decides if angle is high enough for air teleport

    /* ============================================================
       CONSTANTS - SECONDARY
       ============================================================ */
    private static final long SECONDARY_COOLDOWN_MS = 16_000;
    private static final double SECONDARY_RANGE = 24.0;
    private static final double SECONDARY_HIT_MARGIN = 2.0;          // How generous the hitbox is

    /* ============================================================
       CONSTANTS - ULTIMATE
       ============================================================ */
    private static final long ULTIMATE_COOLDOWN_MS = 420_000;
    private static final int ULTIMATE_DURATION_TICKS = 120;       // 6 seconds
    private static final int ULTIMATE_ATTACK_STEP_INITIAL = 4;    // startup delay before first swing
    private static final int ULTIMATE_ATTACK_STEP_ONGOING = 6;    // ticks between each swing
    private static final double ULTIMATE_SEARCH_RADIUS = 7.0;     // how far to look for targets
    private static final double ULTIMATE_TELEPORT_OFFSET = -1.5;  // how far behind victim to teleport
    private static final double ULTIMATE_AURA_RADIUS = 11.0;      // visual ring radius

    // Ultimate Custom Particles
    private static final DustParticleEffect FRENZY_DUST = new DustParticleEffect(new Vector3f(0.55f, 0.0f, 0.85f), 1.5f); // Deep purple
    private static final DustParticleEffect FRENZY_RING = new DustParticleEffect(new Vector3f(0.85f, 0.2f, 1.0f), 1.2f);  // Bright magenta

    private static final Random RNG = new Random();

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tp_")); // Clean legacy tags
        ACTIVE_STATES.put(player.getUuid(), new TeleportState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("tp_"));
        ACTIVE_STATES.remove(player.getUuid());
        player.removeStatusEffect(StatusEffects.HASTE);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        TeleportState state = getState(player);

        // Primary Charges
        if (state.blinkLockTicks > 0) state.blinkLockTicks--;
        tickBlinkRecharge(state);
        updateBlinkCooldownUI(player, state);

        // tick timers
        if (state.dodgeCdTicks > 0) state.dodgeCdTicks--;
        if (state.phaseTicks > 0) state.phaseTicks--;

        if (state.frenzyTicks > 0) {
            tickFrenzy(player, state);
        }
    }

    // =========================
    // PASSIVE
    // =========================

    @Override
    public boolean onDamaged(ServerPlayerEntity victim, DamageSource source, float amount) {
        return !tryDodge(victim);
    }

    /**
     * Return true if we dodged (meaning: cancel the damage).
     */
    public boolean tryDodge(ServerPlayerEntity player) {
        // do not dodge if passive off
        if (!PassiveManager.isEnabled(player)) return false;

        TeleportState state = getState(player);

        // leave if cooldown is active
        if (state.dodgeCdTicks > 0) return false;

        if (RNG.nextFloat() > PASSIVE_DODGE_CHANCE) return false; // dice roll

        // request packet to hide player
        RenderPackets.hidePlayerFromOthers(player, PASSIVE_HIDE_TICKS);

        // window of invincibility (otherwise it would just hit again)
        state.phaseTicks = PASSIVE_IFRAME_TICKS;

        // cooldown setting
        state.dodgeCdTicks = PASSIVE_COOLDOWN_TICKS;

        // particles
        var w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY()+1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.08);
        // player is removed from people's client to go invisible
        w.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, player.getSoundCategory(), 1.0f, 1.2f);

        return true; // tells server to cancel damage
    }

    // =========================
    // PRIMARY
    // =========================

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        TeleportState state = getState(player);
        if (state.blinkLockTicks > 0) return;
        if (state.blinkCharges <= 0) return;

        // consume charge
        state.blinkCharges--;
        state.blinkLockTicks = PRIMARY_LOCK_TICKS;

        // start recharge if not running
        if (state.blinkRechargeTicks < 0) {
            state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
        }

        blinkForward(player, PRIMARY_BLINK_DIST);
    }

    private static void tickBlinkRecharge(TeleportState state) {
        // if full, exit
        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            state.blinkRechargeTicks = -1;
            return;
        }

        // tick timer
        if (state.blinkRechargeTicks >= 0) {
            state.blinkRechargeTicks--;

            // when hit 0, give a charge
            if (state.blinkRechargeTicks <= 0) {
                state.blinkCharges++;

                // If still not full, start next recharge window
                if (state.blinkCharges < PRIMARY_MAX_CHARGES) {
                    state.blinkRechargeTicks = PRIMARY_RECHARGE_TICKS;
                } else {
                    state.blinkRechargeTicks = -1;
                }
            }
        }
    }

    private static void updateBlinkCooldownUI(ServerPlayerEntity player, TeleportState state) {
        String key = "Teleport:PRIMARY";

        // hide ui when at full charge
        if (state.blinkCharges >= PRIMARY_MAX_CHARGES) {
            CooldownUI.clearCooldown(player, key);
            return;
        }

        // show progress towards nearest charge
        int leftTicks = state.blinkRechargeTicks;
        if (leftTicks < 0) leftTicks = PRIMARY_RECHARGE_TICKS;

        long endMs = System.currentTimeMillis() + (leftTicks * 50L);

        String suffix = CooldownUI.makeChargeSuffix(
                state.blinkCharges, PRIMARY_MAX_CHARGES, leftTicks, PRIMARY_RECHARGE_TICKS
        );

        CooldownUI.setCooldownEnd(player, key, endMs, suffix);
    }

    private static void blinkForward(ServerPlayerEntity player, double maxDistance) {
        ServerWorld world = player.getServerWorld();

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);

        // Scale distance based on pitch (looking down reduces distance)
        // look.y ranges from 1.0 (straight up) to -1.0 (straight down)
        double distanceMultiplier = 1.0;
        if (look.y < 0) {
            // If looking down, scale distance linearly (e.g., -1.0 pitch = 20% distance)
            distanceMultiplier = Math.max(0.2, 1.0 + look.y);
        }

        double actualDistance = maxDistance * distanceMultiplier;

        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;
        if (!allowAir) {
            look = new Vec3d(look.x, 0.0, look.z);
            if (look.lengthSquared() < 1.0e-6) return;
            look = look.normalize();
        }

        Vec3d desired = eye.add(look.multiply(actualDistance));

        // keep them grounded if possible
        if (!allowAir) {
            desired = new Vec3d(desired.x, player.getY(), desired.z);
        }

        Vec3d safe = findSafeTeleportSpot(world, player, desired);

        Vec3d origin = player.getPos();

        // Trail
        spawnBlinkTrail(world, origin, safe);

        // Origin
        world.spawnParticles(ParticleTypes.PORTAL, origin.x, origin.y + 1, origin.z, 30, 0.4, 0.7, 0.4, 0.1);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 1.0f, 1.2f);

        // Sound
        world.playSound(null, player.getBlockPos(),
                ModSounds.TELEPORTSNAP,
                player.getSoundCategory(),
                0.7f, 1.4f);

        // Teleport
        safeTeleport(player, safe.x, safe.y, safe.z);

        // Destination
        world.spawnParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1, safe.z, 30, 0.4, 0.7, 0.4, 0.1);

        // briefly give haste to refresh attack cooldown instantly
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.HASTE, 5, 50, true, false, false
        ));
    }

    private static Vec3d findSafeTeleportSpot(ServerWorld world, ServerPlayerEntity player, Vec3d desired) {
        BlockPos base = BlockPos.ofFloored(desired.x, desired.y, desired.z);

        // If looking up enough, allow air placement
        Vec3d look = player.getRotationVec(1.0f);
        boolean allowAir = look.y > PRIMARY_AIR_LOOK_THRESHOLD;

        // Try Y-offsets to find a safe spot
        int[] order = new int[] { 0, 1, -1, 2, -2, 3, -3 };

        for (int dy : order) {
            BlockPos feet = base.up(dy);
            BlockPos head = feet.up();

            // Check if space is physically empty (no blocks)
            boolean feetEmpty = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
            boolean headEmpty = world.getBlockState(head).getCollisionShape(world, head).isEmpty();

            // Check for dangerous fluids (lava)
            boolean feetSafeFluid = world.getFluidState(feet).isEmpty() || world.getFluidState(feet).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            boolean headSafeFluid = world.getFluidState(head).isEmpty() || world.getFluidState(head).isIn(net.minecraft.registry.tag.FluidTags.WATER);

            if (!feetEmpty || !headEmpty || !feetSafeFluid || !headSafeFluid) continue;

            if (!allowAir) {
                BlockPos below = feet.down();
                boolean hasFloor = !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
                boolean floorSafeFluid = world.getFluidState(below).isEmpty() || world.getFluidState(below).isIn(net.minecraft.registry.tag.FluidTags.WATER);
                if (!hasFloor || !floorSafeFluid) continue;
            }

            return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }

        // If no completely safe spot found near desired, try raycasting to find the last safe spot *before* hitting a wall
        HitResult hit = world.raycast(new RaycastContext(
                player.getEyePos(),
                desired,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            // Step back slightly from the wall
            Vec3d hitPos = hit.getPos();
            Vec3d direction = desired.subtract(player.getEyePos()).normalize();
            return hitPos.subtract(direction.multiply(0.5));
        }

        return desired;
    }

    @Override
    public long getPrimaryCooldownMs() { return 0; } // Handled by charge system

    // =========================
    // SECONDARY
    // =========================

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        Entity target = getLookedAtEntity(player, SECONDARY_RANGE);

        // if whiff do a thing
        if (!(target instanceof LivingEntity living)) {
            SecondaryWhiffFx(player, world, SECONDARY_RANGE);
            return;
        }

        // spawn beam to show where aimed
        Vec3d beamStart = player.getEyePos();
        Vec3d beamEnd = living.getPos().add(0, living.getHeight() * 0.5, 0); // mid-body looks nice
        spawnAimBeam(world, beamStart, beamEnd);
        world.spawnParticles(ParticleTypes.PORTAL, beamEnd.x, beamEnd.y, beamEnd.z, 18, 0.25, 0.25, 0.25, 0.06);

        Vec3d pPos = player.getPos();
        Vec3d tPos = target.getPos();

        // Swap directly to coordinates without safety checks
        player.teleport(world, tPos.x, tPos.y, tPos.z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;

        target.requestTeleport(pPos.x, pPos.y, pPos.z);
        faceEntity(player, living);

        // particles
        spawnBlinkTrail(world, pPos, tPos); // player -> target
        spawnBlinkTrail(world, tPos, pPos); // target -> player

        world.spawnParticles(ParticleTypes.PORTAL, pPos.x, pPos.y+1, pPos.z, 50, 0.5, 0.8, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.PORTAL, tPos.x, tPos.y+1, tPos.z, 50, 0.5, 0.8, 0.5, 0.1);

        world.playSound(null, player.getBlockPos(), ModSounds.TELEPORTCLAP, player.getSoundCategory(), 0.7f, 1.4f);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, player.getSoundCategory(), 0.7f, 1.4f);

        // Instantly refresh attack cooldown
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.HASTE, 5, 50, true, false, false
        ));
    }

    @Override
    public long getSecondaryCooldownMs() { return SECONDARY_COOLDOWN_MS; }

    private static void SecondaryWhiffFx(ServerPlayerEntity player, ServerWorld world, double range) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(range));

        HitResult hr = world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d hitPos = (hr.getType() == HitResult.Type.MISS) ? end : hr.getPos();

        // beam
        spawnAimBeam(world, start, hitPos);
        world.spawnParticles(ParticleTypes.PORTAL, hitPos.x, hitPos.y, hitPos.z, 18, 0.25, 0.25, 0.25, 0.06);

        // sound
        world.playSound(null, player.getBlockPos(),
                ModSounds.TELEPORTCLAP,
                player.getSoundCategory(),
                0.7f, 1.4f);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT,
                player.getSoundCategory(),
                0.7f, 1.4f);
    }

    private static void spawnAimBeam(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 18), 10, 140);
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) {
            world.spawnParticles(
                    ParticleTypes.REVERSE_PORTAL,
                    p.x, p.y, p.z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
            p = p.add(step);
        }
    }

    // =========================
    // ULTIMATE
    // =========================

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        TeleportState state = getState(player);
        state.frenzyTicks = ULTIMATE_DURATION_TICKS;
        state.frenzyStep = ULTIMATE_ATTACK_STEP_INITIAL;

        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, player.getSoundCategory(), 0.8f, 1.4f);
    }

    @Override
    public long getUltimateCooldownMs() { return ULTIMATE_COOLDOWN_MS; }

    private void tickFrenzy(ServerPlayerEntity player, TeleportState state) {
        state.frenzyTicks--;
        if (state.frenzyTicks <= 0) return;

        ServerWorld world = player.getServerWorld();
        spawnFrenzyRadius(world, player, ULTIMATE_AURA_RADIUS);
        spawnFrenzyAura(world, player);

        if (state.frenzyStep > 0) {
            state.frenzyStep--;
            return;
        }

        state.frenzyStep = ULTIMATE_ATTACK_STEP_ONGOING;

        Box box = new Box(player.getPos(), player.getPos()).expand(ULTIMATE_SEARCH_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(RNG.nextInt(targets.size()));

        // calculate desired spot behind target
        Vec3d behind = target.getPos().add(target.getRotationVec(1.0f).multiply(ULTIMATE_TELEPORT_OFFSET));
        // force the Y coordinate to match the target's feet to prevent hovering if target is on a slope

        // use the safe spot finder
        Vec3d safePos = findSafeTeleportSpot(world, player, behind);

        // Safety Fallback: If the safe spot finder returned a location that is still suffocating
        // just teleport to the target's exact position.
        BlockPos feetPos = BlockPos.ofFloored(safePos);
        BlockPos headPos = feetPos.up();
        if (!world.getBlockState(feetPos).getCollisionShape(world, feetPos).isEmpty() ||
                !world.getBlockState(headPos).getCollisionShape(world, headPos).isEmpty()) {
            safePos = target.getPos();
        }

        safeTeleport(player, safePos.x, safePos.y, safePos.z);
        faceEntity(player, target);

        forceAttack(player, target);

        // strike particles
        world.spawnParticles(FRENZY_DUST, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.08);
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, player.getSoundCategory(), 0.6f, 1.5f);
    }

    private static void spawnFrenzyRadius(ServerWorld world, ServerPlayerEntity player, double radius) {
        Vec3d center = player.getPos();

        int points = 80; // more points makes the circle more circly
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;

            world.spawnParticles(
                    FRENZY_RING,
                    x,
                    center.y + 0.1,
                    z,
                    1,
                    0.02, 0.02, 0.02,
                    0.0
            );
        }
    }
    private static void spawnFrenzyAura(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();


        world.spawnParticles(
                FRENZY_DUST,
                pos.x,
                pos.y + 1.0,
                pos.z,
                3,
                0.6,
                1.0,
                0.6,
                0.02
        );

        world.spawnParticles(
                ParticleTypes.REVERSE_PORTAL, //
                pos.x,
                pos.y + 0.5,
                pos.z,
                20,
                0.3,
                0.6,
                0.3,
                0.01
        );
    }

    // =========================
    // RAYCAST ENTITY HELPER
    // =========================

    private static Entity getLookedAtEntity(ServerPlayerEntity player, double range) {
        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(range));

        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(SECONDARY_HIT_MARGIN);

        var hit = ProjectileUtil.raycast(
                player,
                start,
                end,
                box,
                e -> e instanceof LivingEntity && e != player,
                range * range
        );

        return hit != null ? hit.getEntity() : null;
    }

    // =========================
    // TELEPORT HELPERS
    // =========================

    private static void safeTeleport(ServerPlayerEntity player, double x, double y, double z) {
        ServerWorld w = player.getServerWorld();
        player.teleport(w, x, y, z, player.getYaw(), player.getPitch());
        player.fallDistance = 0;
    }

    // =========================
    // HELPER METHODS
    // =========================

    private static void forceAttack(ServerPlayerEntity player, LivingEntity target) { // this is just used for the ult, otherwise the attack cooldown would just tickle
        float baseDamage = (float) player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);

        // adds enchants on sword to damage
        float enchantBonus = net.minecraft.enchantment.EnchantmentHelper.getAttackDamage(
                player.getMainHandStack(),
                target.getGroup()
        );

        float totalDamage = (baseDamage + enchantBonus); //this is very unbalanced

        player.swingHand(Hand.MAIN_HAND, true);

        DamageSource frenzySrc = ModDamageTypes.frenzy(player.getWorld(), player);
        target.damage(frenzySrc, totalDamage);
    }

    private static void faceEntity(ServerPlayerEntity player, Entity target) { // makes player face entity
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        Vec3d diff = targetPos.subtract(playerPos);

        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(MathHelper.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(MathHelper.atan2(dy, horizontalDist) * (180F / Math.PI)));

        player.setYaw(yaw);
        player.setPitch(pitch);

        player.networkHandler.requestTeleport(
                player.getX(),
                player.getY(),
                player.getZ(),
                yaw,
                pitch
        );
    }

    private static void spawnBlinkTrail(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from); // i would try to explain this but even i don't know. I was looking at a forum
        double len = delta.length();
        if (len < 0.001) return;

        int steps = MathHelper.clamp((int)(len * 12), 8, 80); // len is how many particles between origin and destination
        Vec3d step = delta.multiply(1.0 / steps);

        Vec3d p = from;
        for (int i = 0; i <= steps; i++) { // particle details; heh... this is easy..!
            world.spawnParticles(
                    ParticleTypes.PORTAL,
                    p.x, p.y + 1.0, p.z,
                    1,            // count per step
                    0.02, 0.15, 0.02,  // spread
                    0.0
            );
            p = p.add(step);
        }
    }

    // names
    @Override public String getName() { return "Teleportation"; }
    @Override public String getPrimaryName() { return "Blink"; }
    @Override public String getSecondaryName() { return "Boogie Woogie"; }
    @Override public String getUltimateName() { return "Frenzy"; }

    @Override
    public String getOverviewDescription() {
        return "Teleportation is focussed on being confusing in battle, where you're more of a mosquito in a fight since you have little direct combat tools and just move around." +
                " The abilities revolve around manipulation and being hard to hit, while also being able to do well in setup.";
    }

    @Override
    public String getPassiveName() {
        return "Slippy";
    }

    @Override
    public String getPassiveDescription() {
        return "You have a small chance to dodge an instance of ANY damage, this will then go on a short cooldown before being available again.";
    }

    @Override
    public String getPrimaryDescription() {
        return "You have 3 charges of a teleport that moves you a moderate distance in the direction you're looking. This ability goes on cooldown per charge and " +
                "prioritises bringing you to a safe location (e.g. putting you on the ground instead of the air). A lingering trail is left between the locations of the teleport and each" +
                " teleport will reset your attack cooldown, allowing for multi-hit combos.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Swap places with the entity you're looking at and reset your attack cooldown. The entity will be facing whatever direction they were facing before the teleport, but" +
                " you will be facing the entity you swapped with. Missing this will still consume the cooldown.";
    }

    @Override
    public String getUltimateDescription() {
        return "Become very angry for a period of time and teleport behind a random nearby living entity and swing your weapon repeatedly." +
                " The damage inflicted uses the damage from your weapon in your main hand, but enchantments that don't directly edit the weapon" +
                " damage are not applied (e.g. fire aspect, knockback). The radius of this ultimate is indicated by the particles surrounding and" +
                " if no entities are nearby, you can just walk around normally.";
    }
}