package com.yourname.loopypowers.power;

import com.yourname.loopypowers.effect.ModEffects;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class FlightPower implements Power {
    private static final Random RNG = new Random();

    /* ============================================================
       TAGS N TIMERS
       ============================================================ */

    // markers for flight states
    private static final String FLIGHT_ACTIVE = "fl_flight_active";      // is flying

    //timer stuff
    private static final String FLIGHT_STALL_TICKS = "fl_stall_";        // stall

    // easter egg stuff
    private static final String HECANFLY_WINDOW = "fl_hecanfly_window_"; // countdown from assignment
    private static final String HECANFLY_USED = "fl_hecanfly_used";      // stop spam

    // Loop timers for FX cadence
    private static final String TRAIL_STEP = "fl_trail_step_";           // trail
    private static final String SOUND_STEP = "fl_sound_step_";           // sound loop

    /* ============================================================
       CONSTANTS
       ============================================================ */

    // FLIGHT STOP WHEN HURT
    private static final int HURT_LOCK_DURATION = 20 * 2; // ground after hit
    private static final float HURT_KNOCKOUT_MIN_YVEL = -1.15f; // y level drop when hurt

    // Trail + sound cadence
    private static final int TRAIL_INTERVAL = 2;   // particle trail delay in flight
    private static final int SOUND_INTERVAL = 10;  // sound loop delay

    // hah
    private static final int HECANFLY_TICKS = 20 * 30; // 30 seconds

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        // see? i do use these!
        equipWings(player);

        // start the timer for the funny
        setSingleTimerTag(player, HECANFLY_WINDOW, HECANFLY_TICKS);
        player.getCommandTags().remove(HECANFLY_USED);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        unequipWings(player); // stops from dropping
        player.getCommandTags().removeIf(tag -> tag.startsWith("fl_"));
        player.removeStatusEffect(ModEffects.GROUNDED);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    private static void equipWings(ServerPlayerEntity player) { // adds wings and should put chestplate in inventory
        ItemStack chest = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);

        // if already wearing them
        if (chest.isOf(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR)) return;

        // move chestplate into inventory
        if (!chest.isEmpty()) {
            boolean inserted = player.getInventory().insertStack(chest.copy());
            if (!inserted) player.dropItem(chest.copy(), true);
            player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        }

        ItemStack wings = new ItemStack(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR); // adds wings
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, wings);
    }

    private static void unequipWings(ServerPlayerEntity player) { // removes wings
        ItemStack chest = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        if (!chest.isOf(com.yourname.loopypowers.item.ModItems.WINGS_OF_VALOR)) return;

        // remove from chest slot
        player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
    }

    private static final String WING_ENFORCE_STEP = "fl_wing_enforce_"; // countdown tag before wing check

    @Override
    public void onTick(ServerPlayerEntity player) {
        // timers
        tickSingleTimer(player, FLIGHT_STALL_TICKS);
        tickSingleTimer(player, HECANFLY_WINDOW);

        // enforce wings
        int t = tickSingleTimer(player, WING_ENFORCE_STEP);
        if (t <= 0) {
            setSingleTimerTag(player, WING_ENFORCE_STEP, 20); // every 20 ticks
            equipWings(player);
        }

        tickSonicBoomUltimate(player);

        // while flying tick
        tickPassiveFlight(player);

        // speed boost after dash
        tickGustEmpowerment(player);

        // particles while flying
        if (player.isFallFlying()) {
            tickFlightFx(player);
        } else {
            removeTagPrefix(player, TRAIL_STEP);
            removeTagPrefix(player, SOUND_STEP);
        }
    }

    public void onDamaged(ServerPlayerEntity player) {
        // Force them downward + lock re-glide
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.min(v.y, HURT_KNOCKOUT_MIN_YVEL), v.z);
        player.velocityModified = true;

        // Apply visual Grounded effect instead of command tags
        player.addStatusEffect(new StatusEffectInstance(ModEffects.GROUNDED, HURT_LOCK_DURATION, 0, false, false, true));

        // Clear flight marker
        player.getCommandTags().remove(FLIGHT_ACTIVE);
        removeTagPrefix(player, TRAIL_STEP);
        removeTagPrefix(player, SOUND_STEP);

        // particles
        ServerWorld w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(),
                8, 0.35, 0.35, 0.35, 0.02);
        w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP,
                player.getSoundCategory(), 0.7f, 0.9f);
    }

    /* ============================================================
       ABILITIES
       ============================================================ */

    // PRIMARY
    // Gust
    private static final String GUST_EMPOWERMENT_TICKS = "fl_gust_after_"; // timer tag
    private static final int GUST_EMPOWERMENT_DURATION = 40; // how long movement boost after gust is active
    private static final double GUST_BURST_STRENGTH = 1.0; // strength of boost
    private static final double GUST_EMPOWERMENT_PUSH = 0.06; // how much speed is boosted
    private static final double GUST_MAX_HORIZ_SPEED = 2.2; // limit just in case

    @Override
    public boolean tryActivatePrimary(ServerPlayerEntity player) { // fails if person is grounded.
        if (player.hasStatusEffect(ModEffects.GROUNDED)) {
            player.sendMessage(net.minecraft.text.Text.literal("§7You're grounded."), true);
            return false;
        }
        activatePrimary(player);
        return true;
    }

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        // the meme
        if (player.isFallFlying() && hasTagPrefix(player, HECANFLY_WINDOW) && !player.getCommandTags().contains(HECANFLY_USED)) {
            player.getServerWorld().playSound(null, player.getBlockPos(), ModSounds.HECANFLY, player.getSoundCategory(), 1.2f, 1.0f);
            player.getCommandTags().add(HECANFLY_USED);
        }

        // force flight to start
        player.getCommandTags().add(GLIDE_REQUEST);

        // direction
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z);

        if (dir.lengthSquared() < 1.0e-6) return;
        dir = dir.normalize();

        // apply boost
        Vec3d v = player.getVelocity();
        Vec3d boosted = v.add(dir.multiply(GUST_BURST_STRENGTH));

        // send mainly horizontal (doesn't really matter cus it gives them momentum to use vertically anyway.)
        Vec3d horiz = new Vec3d(boosted.x, 0, boosted.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3d clampedHoriz = horiz.normalize().multiply(GUST_MAX_HORIZ_SPEED);
            boosted = new Vec3d(clampedHoriz.x, boosted.y, clampedHoriz.z);
        }

        player.setVelocity(boosted);
        player.velocityModified = true;

        // Start speed boost timer
        setSingleTimerTag(player, GUST_EMPOWERMENT_TICKS, GUST_EMPOWERMENT_DURATION);

        // extra particles
        ServerWorld w = player.getServerWorld();
        w.spawnParticles(ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.8, player.getZ(),
                12, 0.25, 0.15, 0.25, 0.02
        );
        w.playSound(null, player.getBlockPos(),
                ModSounds.GUST,
                player.getSoundCategory(),
                0.9f, 1.6f
        );
    }

    private void tickGustEmpowerment(ServerPlayerEntity player) {
        int left = tickSingleTimer(player, GUST_EMPOWERMENT_TICKS);
        if (left <= 0) return;

        // only boost when flying
        if (!player.isFallFlying()) return;

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir = dir.normalize();

        Vec3d v = player.getVelocity();
        Vec3d next = v.add(dir.multiply(GUST_EMPOWERMENT_PUSH));

        // limit again
        Vec3d horiz = new Vec3d(next.x, 0, next.z);
        double hLen = horiz.length();
        if (hLen > GUST_MAX_HORIZ_SPEED) {
            Vec3d clampedHoriz = horiz.normalize().multiply(GUST_MAX_HORIZ_SPEED);
            next = new Vec3d(clampedHoriz.x, next.y, clampedHoriz.z);
        }

        player.setVelocity(next);
        player.velocityModified = true;

        // extra flight trail
        if (left % 3 == 0) {
            player.getServerWorld().spawnParticles(
                    ParticleTypes.FIREWORK,
                    player.getX(), player.getY() + 0.6, player.getZ(),
                    2, 0.10, 0.06, 0.10, 0.005
            );
        }
    }

    // SECONDARY
    @Override
    public boolean tryActivateSecondary(ServerPlayerEntity player) { // must be grounded and unbound to use updraft
        if (player.hasStatusEffect(ModEffects.GROUNDED)) {
            player.sendMessage(net.minecraft.text.Text.literal("§7You're grounded."), true);
            return false;
        }
        activateSecondary(player);
        return true;
    }

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        // particle before kickoff
        ServerWorld w = player.getServerWorld();
        w.spawnParticles(
                ParticleTypes.EXPLOSION,
                player.getX(), player.getY() + 0.3, player.getZ(),
                20, 0.35, 0.25, 0.35, 0.03
        );
        w.playSound(
                null,
                player.getBlockPos(),
                ModSounds.UPDRAFT,
                player.getSoundCategory(),
                1.1f,
                1.3f
        );

        // send them in the air
        Vec3d v = player.getVelocity();
        double up = 2.5; // how far
        player.setVelocity(v.x, Math.max(v.y, 0.0) + up, v.z);
        player.velocityModified = true;

        // force flight to start after boost
        player.getCommandTags().add(GLIDE_REQUEST);
    }

    // Ultimate

    // Sonic Boom Ult
    private static final String BOOM_WINDUP = "fl_boom_windup_";
    private static final String BOOM_DASH   = "fl_boom_dash_";
    private static final String BOOM_INVULN = "fl_boom_invuln";

    // directions
    private static final String BOOM_DX = "fl_boom_dx_";
    private static final String BOOM_DY = "fl_boom_dy_";
    private static final String BOOM_DZ = "fl_boom_dz_";

    // I LOVE CONSTANTS
    private static final int BOOM_WINDUP_TICKS = 30; // how long windup
    private static final int BOOM_DASH_TICKS = 18;   //
    private static final double BOOM_SPEED = 4.0;    // forward push per tick
    private static final double BOOM_RADIUS = 3.0;   // knock radius during
    private static final double BOOM_IMPACT_RADIUS = 6.0; // radius if collision explosion
    private static final float  BOOM_IMPACT_DAMAGE = 6.0f; // damage if in boom
    private static final double BOOM_IMPACT_KB = 2.2; //knockback from boom
    private static final String BOOM_YAW = "fl_boom_yaw_"; // angle
    private static final int BOOM_YAW_SCALE = 10; //
    private static final int BOOM_KNOCKOUT_DURATION = 20 * 3; // how long they're knocked out of flight if collided

    @Override
    public boolean tryActivateUltimate(ServerPlayerEntity player) { // can't be grounded or submerged
        if (player.isOnGround() || player.isTouchingWater()) {
            player.sendMessage(net.minecraft.text.Text.literal("§7You must be airborne to use sonic boom."), true);
            return false;
        }
        activateUltimate(player);
        return true;
    }

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        // must be airborne-ish
        if (player.isOnGround() || player.isTouchingWater()) return;

        // start windup
        setSingleTimerTag(player, BOOM_WINDUP, BOOM_WINDUP_TICKS);
        removeTagPrefix(player, BOOM_DASH);
        player.getCommandTags().remove(BOOM_INVULN);

        // stop movement
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.fallDistance = 0;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, //
                player.getSoundCategory(), 1.5f, 1.0f);

        w.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                player.getX(), player.getY() + 1.0, player.getZ(),
                1, 0, 0, 0, 0);
    }

    private void tickSonicBoomUltimate(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;

        ServerWorld world = player.getServerWorld();

        // windup
        if (hasTagPrefix(player, BOOM_WINDUP)) {
            int left = tickSingleTimer(player, BOOM_WINDUP);

            // camerashake
            CameraShake.shakeNearby(player,
                    4,
                    10,
                    0.40f);

            // stop movement (again)
            player.setVelocity(0, 0, 0);
            player.velocityModified = true;
            player.fallDistance = 0;

            // charge particles
            if (left % 2 == 0) {
                world.spawnParticles(ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        6, 0.35, 0.45, 0.35, 0.01);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        8, 0.45, 0.55, 0.45, 0.02);
            }

            // windup done, start silly push
            if (left <= 0) {
                // lock direction and push
                Vec3d dir = player.getRotationVec(1.0f).normalize();
                float yaw = player.getYaw(); // degrees
                setStaticIntTag(player, BOOM_YAW, Math.round(yaw * BOOM_YAW_SCALE));

                // start dash timer + invuln
                setSingleTimerTag(player, BOOM_DASH, BOOM_DASH_TICKS);
                player.getCommandTags().add(BOOM_INVULN);

                // launch
                Vec3d launch = dir.multiply(BOOM_SPEED);
                player.setVelocity(launch.x, Math.max(launch.y, 0.05), launch.z);
                player.velocityModified = true;

                world.playSound(null, player.getBlockPos(),
                        SoundEvents.ENTITY_WARDEN_SONIC_BOOM,
                        player.getSoundCategory(), 1.6f, 1.0f);
            }

            return; // don’t do ult while winding up
        }
        // BIG PUSH
        if (hasTagPrefix(player, BOOM_DASH)) {
            int left = tickSingleTimer(player, BOOM_DASH);

            float yaw = getBoomYaw(player);
            float pitch = player.getPitch(); // allows verticality

            // convert yaw to actual direction
            float yawRad = (float) Math.toRadians(yaw);
            float pitchRad = (float) Math.toRadians(pitch);

            double x = -Math.sin(yawRad) * Math.cos(pitchRad);
            double y = -Math.sin(pitchRad);
            double z =  Math.cos(yawRad) * Math.cos(pitchRad);

            Vec3d dir = new Vec3d(x, y, z).normalize();

            // me trying to prevent being too vertical
            double maxUp = 0.75;
            double maxDown = -0.85;
            dir = new Vec3d(dir.x, Math.max(maxDown, Math.min(maxUp, dir.y)), dir.z).normalize();

            player.setVelocity(dir.multiply(BOOM_SPEED));
            player.velocityModified = true;
            player.fallDistance = 0;
            player.startFallFlying(); // helps keep elytra state stable

            // tunnel particles
            spawnBoomTunnel(world, player, dir);

            // knock things around nearby
            Box box = player.getBoundingBox().expand(BOOM_RADIUS);
            List<LivingEntity> nearby = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != player);

            for (LivingEntity e : nearby) {
                Vec3d away = e.getPos().subtract(player.getPos());
                if (away.lengthSquared() < 0.0001) continue;
                Vec3d knock = away.normalize().multiply(0.9).add(0, 0.15, 0);
                e.addVelocity(knock.x, knock.y, knock.z);
                e.velocityModified = true;
            }

            // tests if player has actually collided during boom
            boolean collided =
                    player.horizontalCollision
                            || player.verticalCollision
                            || player.isOnGround()
                            || boomHitsBlock(world, player);

            if (collided) {
                doBoomImpact(world, player);
                clearBoomState(player);

                if (player.isFallFlying()) player.stopFallFlying();
                return;
            }

            // no collision, just make them keep speed
            if (left <= 0) {
                clearBoomState(player);
            }
        }
    }


    /* ============================================================
       PASSIVE TICK SECTIONS
       ============================================================ */
    private static final String GLIDE_REQUEST = "fl_glide_req"; // marker tag (no number)

    private void tickPassiveFlight(ServerPlayerEntity player) {
        if (player.isCreative() || player.isSpectator()) return;

        // force flight cancel if hurt
        if (player.hasStatusEffect(ModEffects.GROUNDED)) {
            if (player.isFallFlying()) player.stopFallFlying();
            player.getCommandTags().remove(FLIGHT_ACTIVE);
            return;
        }

        // crouching stops flight
        if (player.isFallFlying() && player.isSneaking()) {
            player.stopFallFlying();
            player.getCommandTags().remove(FLIGHT_ACTIVE);
            removeTagPrefix(player, TRAIL_STEP);
            removeTagPrefix(player, SOUND_STEP);
            return;
        }

        if (player.getCommandTags().contains(GLIDE_REQUEST)) {
            player.getCommandTags().remove(GLIDE_REQUEST);

            if (!player.isOnGround() && !player.isTouchingWater() && !player.isFallFlying()) {
                player.startFallFlying();
                player.getCommandTags().add(FLIGHT_ACTIVE);
            }
        }

        // make particles for flight
        if (player.isFallFlying()) {
            player.getCommandTags().add(FLIGHT_ACTIVE);
        } else {
            player.getCommandTags().remove(FLIGHT_ACTIVE);
        }
    }

    private void tickFlightFx(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        int t = tickSingleTimer(player, TRAIL_STEP);
        if (t <= 0) {
            setSingleTimerTag(player, TRAIL_STEP, TRAIL_INTERVAL);

            // spawn trail slightly behind
            Vec3d vel = player.getVelocity();
            Vec3d back = vel.lengthSquared() > 0.001 ? vel.normalize().multiply(-0.6) : new Vec3d(0, 0, 0);
            double x = player.getX() + back.x;
            double y = player.getY() + 0.6;
            double z = player.getZ() + back.z;

            w.spawnParticles(ParticleTypes.CLOUD, x, y, z, // particle
                    2, 0.08, 0.06, 0.08, 0.005);
        }

        int s = tickSingleTimer(player, SOUND_STEP);
        if (s <= 0) {
            setSingleTimerTag(player, SOUND_STEP, SOUND_INTERVAL);
            w.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, player.getSoundCategory(), //sound
                    0.35f, 1.35f);
        }
    }

    /* ============================================================
       COOLDOWNS
       ============================================================ */

    @Override public long getPrimaryCooldownMs() { return 4_000; }   // Gust
    @Override public long getSecondaryCooldownMs() { return 10_000; } // Updraft
    @Override public long getUltimateCooldownMs() { return 5_000; }  // Sonic Boom

    /* ============================================================
       HELPERS
       ============================================================ */

    private static boolean hasTagPrefix(ServerPlayerEntity p, String prefix) {
        for (String tag : p.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return; // remove ONE (matches your “single timer” convention)
            }
        }
    }

    private static void setSingleTimerTag(ServerPlayerEntity p, String prefix, int ticks) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + ticks);
    }

    private static void setStaticIntTag(ServerPlayerEntity p, String prefix, int value) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + value);
    }

    private static int getStaticIntTag(ServerPlayerEntity p, String prefix) {
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(prefix)) {
                try {
                    return Integer.parseInt(tag.substring(prefix.length()));
                } catch (NumberFormatException e) {
                    return Integer.MIN_VALUE;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int tickSingleTimer(ServerPlayerEntity p, String prefix) {
        String found = null;
        for (String tag : p.getCommandTags()) {
            if (tag.startsWith(prefix)) { found = tag; break; }
        }
        if (found == null) return -1;

        p.getCommandTags().remove(found);

        int ticks;
        try {
            ticks = Integer.parseInt(found.substring(prefix.length())) - 1;
        } catch (NumberFormatException e) {
            return -1;
        }

        if (ticks > 0) p.getCommandTags().add(prefix + ticks);
        return ticks;
    }

    private float getBoomYaw(ServerPlayerEntity p) { // helper: grabs the direction of the tunnel so the player can't just move
        int raw = getStaticIntTag(p, BOOM_YAW);
        if (raw == Integer.MIN_VALUE) return p.getYaw();
        return raw / (float)BOOM_YAW_SCALE;
    }

    private void spawnBoomTunnel(ServerWorld w, ServerPlayerEntity p, Vec3d dir) { // to be honest this is kind of a useless method
        Vec3d pos = p.getPos().add(0, 1.0, 0); // but it pushes things in the path away (allegedly)
        Vec3d back = dir.multiply(-1.0);

        for (int i = 0; i < 6; i++) {
            Vec3d pt = pos.add(back.multiply(i * 0.7));

            w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                    pt.x, pt.y, pt.z,
                    1, 0.25, 0.25, 0.25, 0.01);

            if (RNG.nextFloat() < 0.4f) {
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                        pt.x, pt.y, pt.z,
                        1, 0, 0, 0, 0);
            }
        }
    }

    private boolean boomHitsBlock(ServerWorld world, ServerPlayerEntity player) {
        Vec3d vel = player.getVelocity(); // uses raycast shenanigans to test if they will hit a block
        if (vel.lengthSquared() < 1.0e-4) return false;

        Vec3d start = player.getPos().add(0, 1.0, 0);
        Vec3d end = start.add(vel.normalize().multiply(2.4)); // slightly longer

        HitResult hit = world.raycast(new RaycastContext(
                start, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        return hit.getType() == HitResult.Type.BLOCK;
    }

    private void doBoomImpact(ServerWorld world, ServerPlayerEntity player) {
        Vec3d c = player.getPos();

        // particles
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        world.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 1.2f, 0.9f);

        // explosion power
        float explodepower = 1.8f; // 1.5–2.5 feels “small but noticeable”

        world.createExplosion(
                player,                       // entity source
                player.getX(), player.getY(), player.getZ(),
                explodepower,                 // power
                false,                        // makes fire
                World.ExplosionSourceType.BLOCK // damages blocks
        );

        // does things in radius
        Box box = new Box(c, c).expand(BOOM_IMPACT_RADIUS);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        // camerashake
        CameraShake.shakeNearby(player,
                14,
                18,
                0.50f);

        for (LivingEntity e : targets) {
            // damage
            e.damage(com.yourname.loopypowers.damage.ModDamageTypes.sonic(player.getWorld(), player), BOOM_IMPACT_DAMAGE);

            // knockback
            Vec3d away = e.getPos().subtract(c);
            if (away.lengthSquared() < 0.0001) away = new Vec3d(0, 0, 1);
            Vec3d kb = away.normalize().multiply(BOOM_IMPACT_KB).add(0, 0.45, 0);
            e.addVelocity(kb.x, kb.y, kb.z);
            e.velocityModified = true;
        }
        // knock them out of flight
        player.addStatusEffect(new StatusEffectInstance(ModEffects.GROUNDED, BOOM_KNOCKOUT_DURATION, 0, false, false, true));
        // stop all speed
        player.setVelocity(0, Math.min(player.getVelocity().y, -0.25), 0);
        player.velocityModified = true;
    }

    private void clearBoomState(ServerPlayerEntity player) {
        removeTagPrefix(player, BOOM_DASH);
        player.getCommandTags().remove(BOOM_INVULN);
        removeTagPrefix(player, BOOM_YAW);
        removeTagPrefix(player, BOOM_DX);
        removeTagPrefix(player, BOOM_DY);
        removeTagPrefix(player, BOOM_DZ);
    }

    public boolean isBoomInvulnerable(ServerPlayerEntity player) {
        return player.getCommandTags().contains(BOOM_INVULN);
    }

    /* ============================================================
       DISPLAY
       ============================================================ */

    @Override public String getName() { return "Flight"; }
    @Override public String getPrimaryName() { return "Gust"; }
    @Override public String getSecondaryName() { return "Updraft"; }
    @Override public String getUltimateName() { return "Sonic Boom"; }

    @Override
    public String getOverviewDescription() {
        return "Flight revolves around mobility, with very little to offer outside of that. Both abilities grant you heightened mobility and the" +
                "ultimate can be used both for mobility and damage. It is useful for survival but you may have limited tools in combat compared to other powers.";
    }

    @Override
    public String getPassiveName() {
        return "Wings Of Valor";
    }

    @Override
    public String getPassiveDescription() {
        return "You permanently have wings as a chestplate that grant you elytra flight. When damaged, any forms of flight are blocked" +
                "for a few seconds. You cannot remove these wings and they cannot break.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Can only be used while flying. Gain a burst of momentum in the direction that you're looking and gain a brief speed boost after.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Cannot be used while flying. Shoot yourself into the air and enter flight. If you are grounded this ability cannot be used.";
    }

    @Override
    public String getUltimateDescription() {
        return "Can only be used while flying. Stop all movement and briefly charge up. Once charged, shoot off in the direction you're looking with high speed." +
                "During flight, nearby entities will be pushed away and if you collide with a solid block, explode nearby entites with high knockback and become grounded" +
                "for a few seconds.";
    }
}