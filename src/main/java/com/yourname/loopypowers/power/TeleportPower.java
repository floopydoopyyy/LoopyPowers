package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.Entity;
import com.yourname.loopypowers.network.RenderPackets;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Random;

public class
TeleportPower implements Power {
    //CONSTANTS
    // Primary
    private static final String BLINK_WINDOW = "tp_blink_window_ticks_"; // second press window
    private static final String BLINK_USED_ONCE = "tp_blink_used_once";  // this one is a marker (no number)
    private static final String BLINK_CD_REQUEST = "tp_blink_cd_request"; // used for cooldown for primary

    private static final Random RNG = new Random();

    // =========================
    // PASSIVE
    // =========================

    /**
     * Called from ServerLivingEntityEvents.ALLOW_DAMAGE.
     * Return true if we dodged (meaning: cancel the damage).
     * if this code breaks again im going to jump
     */
    public boolean tryDodge(ServerPlayerEntity player) {
        // do not dodge if passive off
        if (!PassiveManager.isEnabled(player)) return false;

        // leave if cooldown is active
        if (hasTagPrefix(player, "tp_dodge_cd_")) return false;

        float dodgeChance = 0.99f; // percentage chance to dodge
        if (RNG.nextFloat() > dodgeChance) return false; // dice roll

        // request packet to hide player
        RenderPackets.hidePlayerFromOthers(player, 8); // how long dodge effect is

        // window of invincibility (otherwise it would just hit again)
        setSingleTimerTag(player, "tp_phase_", 14); // keep this one higher in the sequence otherwise damage will just happen again

        // cooldown setting
        setSingleTimerTag(player, "tp_dodge_cd_", 100); // dodge cooldown in ticks

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
        // PowerManager will normally call activatePrimaryDoubleBlink directly
        // but if something calls activatePrimary, we still do the blink.
        activatePrimaryDoubleBlink(player);
    }
    private static void blinkForward(ServerPlayerEntity player, double distance) {
        ServerWorld world = player.getServerWorld();

        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);

        // Decide if this blink is allowed to be airborne.
        // If not looking up you should hopefully go straight and vice versa
        boolean allowAir = look.y > 0.55; // decides if angle if high enough for air
        if (!allowAir) {
            look = new Vec3d(look.x, 0.0, look.z);
            if (look.lengthSquared() < 1.0e-6) return;
            look = look.normalize();
        }

        // used to stop at walls, was boring
        Vec3d desired = eye.add(look.multiply(distance));

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
        // particles n sound
        world.playSound(null, player.getBlockPos(),
                ModSounds.TELEPORTSNAP,
                player.getSoundCategory(),
                0.7f, 1.4f);

        // Teleport
        safeTeleport(player, safe.x, safe.y, safe.z);

        // Destination
        world.spawnParticles(ParticleTypes.PORTAL, safe.x, safe.y + 1, safe.z, 30, 0.4, 0.7, 0.4, 0.1);
    }

    private static Vec3d findSafeTeleportSpot(ServerWorld world, ServerPlayerEntity player, Vec3d desired) {
        BlockPos base = BlockPos.ofFloored(desired.x, desired.y, desired.z);

        // If looking up enough, allow air placement
        Vec3d look = player.getRotationVec(1.0f);
        boolean allowAir = look.y > 0.55; // tweak this me

        int[] order = new int[] { 0, -1, -2, -3, 1, 2, 3 };

        for (int dy : order) {
            BlockPos feet = base.up(dy);
            BlockPos head = feet.up();

            boolean feetEmpty = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
            boolean headEmpty = world.getBlockState(head).getCollisionShape(world, head).isEmpty();
            if (!feetEmpty || !headEmpty) continue;

            if (!allowAir) {
                BlockPos below = feet.down();
                boolean hasFloor = !world.getBlockState(below).getCollisionShape(world, below).isEmpty();
                if (!hasFloor) continue;
            }

            return new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        }

        // fallback: accept desired even if it's not perfect
        return desired;
    }

    public boolean activatePrimaryDoubleBlink(ServerPlayerEntity player) {
        // SECOND PRESS
        if (hasTagPrefix(player, BLINK_WINDOW)) {

            removeTagPrefix(player, BLINK_WINDOW);
            player.getCommandTags().remove(BLINK_USED_ONCE);

            blinkForward(player, 6.0); // short blink

            return true; // starts cooldown now
        }

        // First press
        blinkForward(player, 18.0); // long blink

        player.getCommandTags().add(BLINK_USED_ONCE);

        // 1s window for second press
        setSingleTimerTag(player, BLINK_WINDOW, 35);

        return false; // dont start cooldown
    }

    @Override
    public long getPrimaryCooldownMs() { return 6_000; }

    // =========================
    // SECONDARY
    // =========================

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        double range = 24.0;

        Entity target = getLookedAtEntity(player, range);

        // if whiff do a thing
        if (!(target instanceof LivingEntity living)) {
            SecondaryWhiffFx(player, world, range);
            return;
        }

        // spawn beam to show where aimed
        Vec3d beamStart = player.getEyePos();
        Vec3d beamEnd = living.getPos().add(0, living.getHeight() * 0.5, 0); // mid-body looks nice
        spawnAimBeam(world, beamStart, beamEnd);
        world.spawnParticles(ParticleTypes.PORTAL, beamEnd.x, beamEnd.y, beamEnd.z, 18, 0.25, 0.25, 0.25, 0.06);

        Vec3d pPos = player.getPos();
        Vec3d tPos = target.getPos();

        // Swap
        safeTeleport(player, tPos.x, tPos.y, tPos.z);
        target.requestTeleport(pPos.x, pPos.y, pPos.z);
        faceEntity(player, living); // make player face victim

        // particles
        spawnBlinkTrail(world, pPos, tPos); // player -> target
        spawnBlinkTrail(world, tPos, pPos); // target -> player

        world.spawnParticles(ParticleTypes.PORTAL, pPos.x, pPos.y+1, pPos.z, 50, 0.5, 0.8, 0.5, 0.1);
        world.spawnParticles(ParticleTypes.PORTAL, tPos.x, tPos.y+1, tPos.z, 50, 0.5, 0.8, 0.5, 0.1);
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

    @Override
    public long getSecondaryCooldownMs() { return 14_000; }

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
        player.getCommandTags().add("tp_frenzy");
        setSingleTimerTag(player, "tp_frenzy_ticks_", 120); // 6 seconds
        // Attack every N ticks
        setSingleTimerTag(player, "tp_frenzy_step_", 4); // every 4 ticks

        player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, player.getSoundCategory(), 0.8f, 1.4f);
    }

    @Override
    public long getUltimateCooldownMs() { return 10_000; }

    @Override
    public void onAssign(ServerPlayerEntity player) {

    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        int left = tickSingleTimer(player, BLINK_WINDOW);

        // window expired
        if (left == 0 && player.getCommandTags().contains(BLINK_USED_ONCE)) {
            player.getCommandTags().remove(BLINK_USED_ONCE);
            player.getCommandTags().add(BLINK_CD_REQUEST);
        }
        // tick timers
        tickSingleTimer(player, "tp_dodge_cd_");
        tickSingleTimer(player, "tp_phase_");
        tickSingleTimer(player, "tp_frenzy_cd_");

        if (hasTagPrefix(player, "tp_frenzy_ticks_")) {
            tickFrenzy(player);
        }
    }

    private void tickFrenzy(ServerPlayerEntity player) {
        //player.sendMessage(net.minecraft.text.Text.literal("FRENZY TICK"), true);
        // countdown
        int ticksLeft = tickSingleTimer(player, "tp_frenzy_ticks_");
        if (ticksLeft <= 0) {
            return;
        }
        // draw particle circle
        ServerWorld world = player.getServerWorld();
        spawnFrenzyRadius(world, player, 11.0); // keep similar to the actual radius
        spawnFrenzyAura(world, player);

        // step timer
        int stepLeft = tickSingleTimer(player, "tp_frenzy_step_");
        if (stepLeft > 0) return;
        setSingleTimerTag(player, "tp_frenzy_step_", 6);

        world = player.getServerWorld();

        // Find targets nearby
        double radius = 6.0;
        Box box = new Box(player.getPos(), player.getPos()).expand(radius);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player);

        if (targets.isEmpty()) return;

        LivingEntity target = targets.get(RNG.nextInt(targets.size())); //randomly selects entity in radius

        // Teleports a bit behind victim
        Vec3d behind = target.getPos().add(target.getRotationVec(1.0f).multiply(-1.5));
        safeTeleport(player, behind.x, behind.y, behind.z);
        // makes face victim
        faceEntity(player, target);

        /* Attack using held weapon naturally
        player.swingHand(Hand.MAIN_HAND, true);
        player.attack(target); */
        forceAttack(player, target);

        // particles
        world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.08);
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
                    ParticleTypes.PORTAL,
                    x,
                    center.y + 0.1,
                    z,
                    1,
                    0, 0, 0,
                    0
            );
        }
    }
    private static void spawnFrenzyAura(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = player.getPos();

        world.spawnParticles(
                ParticleTypes.PORTAL,
                pos.x,
                pos.y + 1.0,
                pos.z,
                15,
                0.6,
                1.0,
                0.6,
                0.02
        );

        world.spawnParticles(
                ParticleTypes.REVERSE_PORTAL,
                pos.x,
                pos.y + 0.5,
                pos.z,
                6,
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

        Box box = player.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

        var hit = ProjectileUtil.raycast(
                player,
                start,
                end,
                box,
                e -> e instanceof LivingEntity && e != player,
                range * range
        ); // ProjectileUtil.raycast exists in Yarn 1.20.x :contentReference[oaicite:1]{index=1}

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

    public boolean consumeBlinkCooldownRequest(ServerPlayerEntity player) { // this was some bullshit
        if (player.getCommandTags().contains(BLINK_CD_REQUEST)) {
            player.getCommandTags().remove(BLINK_CD_REQUEST);
            return true;
        }
        return false;
    }

    private static boolean hasTagPrefix(ServerPlayerEntity p, String prefix) { // helper to test for a tag
        for (String tag : p.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(ServerPlayerEntity p, String prefix) { // called to remove tags easily in tick stuff
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) {
                it.remove();
                return;
            }
        }
    }

    private static void setSingleTimerTag(ServerPlayerEntity p, String prefix, int ticks) {
        removeTagPrefix(p, prefix);
        p.getCommandTags().add(prefix + ticks);
    }

    // does a thing based on a tag
    private static int tickSingleTimer(ServerPlayerEntity p, String prefix) {
        var it = p.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (!tag.startsWith(prefix)) continue;

            int ticks; // removes a tick
            try {
                ticks = Integer.parseInt(tag.substring(prefix.length())) - 1;
            } catch (NumberFormatException e) {
                it.remove();
                return -1;
            }

            it.remove(); // remove old tag safely

            if (ticks > 0) {
                p.getCommandTags().add(prefix + ticks);
            }
            return ticks;
        }
        return -1;
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
    @Override public String getName() { return "Teleport"; }
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
        return "Teleport a long distance in the direction you're looking and then have an optional shorter teleport that you can use by pressing the" +
                " ability button again. (this will go on cooldown if not used quick enough). These teleports prioritise bringing you to a safe location (e.g. " +
                "putting you on the ground instead of the air) and leave a lingering trail between the location of the teleport.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Swap places with the entity you're looking at. The entity will be facing whatever direction they were facing before the teleport, but" +
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
