package com.yourname.loopypowers.power;

import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.RaycastContext;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;
import com.yourname.loopypowers.damage.ModDamageTypes;

import java.util.List;

public class StrengthPower implements Power {

    /* ============================================================
       TAGS / TIMERS
       ============================================================ */

    // Bullrush
    private static final String RUSHING = "st_rushing_";          // st_rushing_<ticks>
    private static final String RUSH_DIR = "st_rush_dir_";        // st_rush_dir_<x>_<y>_<z> (packed)
    private static final String RUSH_HIT_LOCK = "st_rush_hit_";   // st_rush_hit_<ticks>
    private static final String RUSH_CANCEL_LOCK = "st_rush_cancel_"; // marker
    // Rage
    private static final String RAGING = "st_raging_";            // st_raging_<ticks>

    /* ============================================================
       TUNING
       ============================================================ */

    // Passive: keep strength effect alive

    // Bullrush
    private static final int RUSH_TICKS = 26;     // duration
    private static final double RUSH_SPEED = 1.25;
    private static final double RUSH_HIT_RADIUS = 1.3;
    // Rage
    private static final int RAGE_TICKS = 20 * 7; // 7s
    private static final int RAGE_STR_AMP = 1;    // Strength II
    private static final int RAGE_RES_AMP = 0;    // Resistance I
    private static final int RAGE_SPEED_AMP = 0;  // Speed I

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("st_"));
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        // cleanup tags
        player.getCommandTags().removeIf(tag -> tag.startsWith("st_"));

        // cleanup lingering buffs
        player.removeStatusEffect(StatusEffects.STRENGTH);
        player.removeStatusEffect(StatusEffects.RESISTANCE);
        player.removeStatusEffect(StatusEffects.SPEED);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        onRemove(player);
    }

    @Override
    public void onTick(ServerPlayerEntity player) {
        // Keep passive running
        // dodge passive if passive off
        if (!PassiveManager.isEnabled(player)) return;

        StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
        if (strength == null || strength.getDuration() < 5) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0, true, false));
        }

        // Tick timers
        tickSingleTimer(player, RUSHING);
        tickSingleTimer(player, RUSH_HIT_LOCK);
        int ragingLeft = tickSingleTimer(player, RAGING);
        tickSingleTimer(player, RUSH_CANCEL_LOCK);

        // Update rush movement if active
        tickBullrush(player);

        // ultimate
        if (ragingLeft >= 0) {
            tickRageBuffs(player);
            tickRageFX(player);
        }
        tickSingleTimer(player, RAGE_FX_BURST);
        tickSingleTimer(player, RAGE_AURA_STEP);
        tickSingleTimer(player, RAGE_HB_STEP);
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        // only happens when ulting
        if (!isRaging(attacker)) return;

        ServerWorld w = attacker.getServerWorld();

        // direction from attacker -> target (horizontal)
        Vec3d d = target.getPos().subtract(attacker.getPos());
        Vec3d horiz = new Vec3d(d.x, 0.0, d.z);
        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
        Vec3d dir = horiz.normalize();

        // KNOCKBACK VARIABLES
        double out = 0.95;
        double up  = 0.47;

        // kick them up so knockback actually applies
        double lift = target.isOnGround() ? 0.18 : 0.08;   // if on ground
        double maxUp = 0.55;                               // cap so to not send in sun

        target.setAttacker(attacker); // tag em for the kill feed

        target.addVelocity(dir.x * out, lift, dir.z * out);
        Vec3d tv = target.getVelocity();
        target.setVelocity(tv.x, Math.min(maxUp, Math.max(tv.y, 0.06)), tv.z);
        target.velocityModified = true;

        if (target instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp)); // let the server know they zoomin so it stops whining
        }

        // Extra particles
        w.spawnParticles(
                ParticleTypes.CLOUD,
                target.getX(), target.getY() + target.getHeight() * 0.55, target.getZ(),
                14,
                0.18, 0.18, 0.18,
                0.02
        );

        // sound
        w.playSound(
                null,
                target.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                attacker.getSoundCategory(),
                0.8f,
                0.85f
        );
    }

    /* ============================================================
       PASSIVE
       ============================================================ */
    // mining stuff is handled via mixins
    // strength handled by tick

    /* ============================================================
       PRIMARY
       ============================================================ */

    // Slam block damage
    private static final int SLAM_BLOCK_RADIUS = 3;
    private static final int SLAM_MAX_BLOCKS_BROKEN = 22;
    private static final float SLAM_MAX_HARDNESS = 2.2f; // hardness threshold
    private static final float SLAM_BLOCK_BREAK_CHANCE = 0.55f; // chance per candidate
    private static final float SLAM_ENTITY_DAMAGE = 4.0f; // base damage
    private static final float SLAM_OUT = 0.35f; // outward knockback
    private static final float SLAM_FRONT_DOT = 0.35f; // 0=half plane, 1=tiny cone. 0.35 wide cone
    private static final double SLAM_FRONT_OFFSET = 1.4; // where the slam centre is
    private static final double SLAM_RADIUS = 5.5;
    private static final float SLAM_UP = 1.55f; // knockup strength

    @Override
    public void activatePrimary(ServerPlayerEntity player) { // checks players condtions and applies different slam types in seperate methods)
        ServerWorld w = player.getServerWorld();
        Vec3d pos = player.getPos();

        // check if caster on ground
        boolean casterGrounded = isNearGround(player, w);

        // get slam point
        Vec3d forward = player.getRotationVec(1.0f);
        Vec3d slamPos = pos.add(forward.x * 1.0, 0.0, forward.z * 1.0);

        // drag player down slightly. originally this is what stopped slams from not working when sprint jumping but it felt poo.
        if (!casterGrounded) {
            applySlamDrag(player);
        }

        // detect if this is a wall slam or a ground slam
        BlockHitResult wallHit = findWallSlamHit(w, player, forward);
        boolean isWallSlam = (wallHit != null);

        // do normal slam if no wall present
        if (!casterGrounded && !isWallSlam) {
            doGroundSlam(player, w, pos, forward, slamPos, false);
            return;
        }

        // same thing again
        if (isWallSlam) {
            doWallSlam(player, w, pos, forward, slamPos, wallHit);
        } else {
            doGroundSlam(player, w, pos, forward, slamPos, true); // airborne weak slam logic applied here.
        }
    }

    private static void doGroundSlam(ServerPlayerEntity player, ServerWorld w, Vec3d pos, Vec3d forward, Vec3d slamPos, boolean casterGrounded) { // i got carried away.
        // This is also used by the wall slam, wall slam just does wall damage too.
        // get ground under player
        BlockPos ground = player.getBlockPos().down();
        BlockState groundState = w.getBlockState(ground);

        // force slam in front
        BlockPos slamGround = BlockPos.ofFloored(slamPos).down();
        BlockState slamGroundState = w.getBlockState(slamGround);
        if (!slamGroundState.isAir()) {
            ground = slamGround;
            groundState = slamGroundState;
        }

        // fx
        w.playSound(null, player.getBlockPos(),
                ModSounds.SLAM,
                player.getSoundCategory(),
                casterGrounded ? 1.00f : 0.35f,
                casterGrounded ? 0.85f : 1.10f
        );

        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(),
                casterGrounded ? 1.00f : 0.35f,
                casterGrounded ? 0.85f : 1.10f
        );


        // explosion in front
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                slamPos.x, pos.y + 0.10, slamPos.z,
                1, 0, 0, 0, 0
        );

        // more explosions
        if (casterGrounded) {
            w.spawnParticles(ParticleTypes.EXPLOSION,
                    slamPos.x, pos.y + 0.15, slamPos.z,
                    6,
                    0.35, 0.15, 0.35,
                    0.02
            );
            w.spawnParticles(ParticleTypes.POOF,
                    slamPos.x, pos.y + 0.10, slamPos.z,
                    12,
                    0.55, 0.15, 0.55,
                    0.03
            );
        }

        // meant to be rising dust
        if (!groundState.isAir()) {
            // If airborne, don't do much
            int dustCountA = casterGrounded ? 420 : 20;
            int dustCountB = casterGrounded ? 220 : 8;

            // make ring and kick upwards
            w.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                    slamPos.x, ground.getY() + 1.01, slamPos.z,
                    dustCountA,
                    casterGrounded ? 2.2 : 0.5,
                    casterGrounded ? 0.18 : 0.08,
                    casterGrounded ? 2.2 : 0.5,
                    casterGrounded ? 0.75 : 0.08
            );

            // more rising debris
            w.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                    slamPos.x, ground.getY() + 1.01, slamPos.z,
                    dustCountB,
                    casterGrounded ? 0.65 : 0.20,
                    casterGrounded ? 1.10 : 0.25, // taller
                    casterGrounded ? 0.65 : 0.20,
                    casterGrounded ? 1.15 : 0.12
            );

            // particle effects of slam
            if (casterGrounded) {
                // 3 rings
                double[] radii = new double[] { 1.4, 2.8, 4.2 };
                int[] counts   = new int[]    { 24, 32, 42 }; // lowered these so the network thread doesnt commit die
                double[] spreads = new double[]{ 0.25, 0.30, 0.38 };
                double[] speeds  = new double[]{ 0.35, 0.40, 0.45 };

                for (int ri = 0; ri < radii.length; ri++) {
                    double r = radii[ri];
                    int n = counts[ri];

                    for (int i = 0; i < n; i++) {
                        double a = w.random.nextDouble() * (Math.PI * 2.0);
                        // little jitter
                        double jr = (w.random.nextDouble() - 0.5) * 0.35;

                        double x = slamPos.x + Math.cos(a) * (r + jr);
                        double z = slamPos.z + Math.sin(a) * (r + jr);

                        w.spawnParticles(
                                new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                                x, ground.getY() + 1.01, z,
                                1,
                                spreads[ri], 0.08, spreads[ri],
                                speeds[ri]
                        );
                    }
                }

                // extra dust (probably not necesscary)
                w.spawnParticles(
                        ParticleTypes.CLOUD,
                        slamPos.x, ground.getY() + 1.05, slamPos.z,
                        140,
                        1.1, 0.25, 1.1,
                        0.10
                );
                w.spawnParticles(
                        ParticleTypes.CRIT,
                        slamPos.x, ground.getY() + 1.05, slamPos.z,
                        55,
                        0.7, 0.20, 0.7,
                        0.12
                );
            }
        }

        // camera shake
        if (casterGrounded) {
            CameraShake.shakeNearby(player, 6.0, 10, 1.05f);
        } else {
            CameraShake.shakeNearby(player, 4.0, 6, 0.35f);
        }

        // knockup and damage
        Box box = new Box(slamPos, slamPos).expand(SLAM_RADIUS, 2.5, SLAM_RADIUS);

        List<LivingEntity> targets = w.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity t : targets) {
            // distance falloff
            Vec3d d = t.getPos().subtract(slamPos);
            Vec3d horiz = new Vec3d(d.x, 0.0, d.z);
            if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
            Vec3d dir = horiz.normalize();

            double dist = Math.sqrt(horiz.lengthSquared());
            float falloff = 1.0f - (float) MathHelper.clamp(dist / SLAM_RADIUS, 0.0, 1.0);

            // base values
            double upRaw  = SLAM_UP  * (0.85 + 0.95 * falloff);
            double outRaw = SLAM_OUT * (0.35 + 1.0  * falloff);

            // weaker if caster is airborne
            double kbMult = casterGrounded ? 1.0 : 0.15;
            double dmgMult = casterGrounded ? 1.0 : 0.15;

            double maxUp = 1.15;
            double minUpGround = 0.65;
            double maxOut = 0.55;

            double up = MathHelper.clamp(upRaw * kbMult, 0.0, maxUp);
            double out = MathHelper.clamp(outRaw * kbMult, 0.0, maxOut);

            // keep the "works on grounded targets" behavior
            if (t.isOnGround()) {
                up = Math.max(up, minUpGround * kbMult);
            }

            t.setAttacker(player); // tag em for the kill feed

            // on hit damage
            float dmg = (float) (SLAM_ENTITY_DAMAGE * (0.6f + 0.6f * falloff) * dmgMult);
            if (dmg > 0.0f) {
                t.damage(ModDamageTypes.slam(w, player), dmg);
            }

            // update targets velocity
            Vec3d v = t.getVelocity();
            t.setVelocity(v.x + dir.x * out, Math.max(v.y, up), v.z + dir.z * out);
            t.velocityModified = true;

            if (t instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp)); // let the server know they zoomin so it stops whining
            }

            // if grounded, do extra
            if (casterGrounded && w.random.nextFloat() < 0.35f) {
                w.spawnParticles(ParticleTypes.CRIT,
                        t.getX(), t.getY() + t.getHeight() * 0.55, t.getZ(),
                        2, 0.12, 0.12, 0.12, 0.0);
            }
        }

        // block damage
        if (casterGrounded) {
            int broken = 0;

            Vec3d slamCentreVec = pos.add(forward.x * SLAM_FRONT_OFFSET, 0.0, forward.z * SLAM_FRONT_OFFSET);
            BlockPos center = BlockPos.ofFloored(slamCentreVec).down();

            int r = SLAM_BLOCK_RADIUS;

            for (BlockPos p : BlockPos.iterate(center.add(-r, -1, -r), center.add(r, 1, r))) {
                if (broken >= SLAM_MAX_BLOCKS_BROKEN) break;

                BlockState s = w.getBlockState(p);
                if (s.isAir()) continue;
                if (w.getBlockEntity(p) != null) continue;

                float hardness = s.getHardness(w, p);
                if (hardness < 0) continue;
                if (hardness > SLAM_MAX_HARDNESS) continue;

                double dx = p.getX() + 0.5 - pos.x;
                double dz = p.getZ() + 0.5 - pos.z;

                double len = Math.sqrt(dx * dx + dz * dz);
                if (len > 1.0e-6) {
                    double nx = dx / len;
                    double nz = dz / len;

                    Vec3d f = new Vec3d(forward.x, 0.0, forward.z);
                    if (f.lengthSquared() > 1.0e-6) f = f.normalize();

                    double dot = nx * f.x + nz * f.z;
                    if (dot < SLAM_FRONT_DOT) continue;
                }

                double d2 = dx * dx + dz * dz;
                if (d2 > (r + 0.25) * (r + 0.25)) continue;

                if (w.random.nextFloat() > SLAM_BLOCK_BREAK_CHANCE) continue;

                if (w.breakBlock(p, true, player)) {
                    broken++;
                }
            }
        }

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void doWallSlam(ServerPlayerEntity player, ServerWorld w, Vec3d pos, Vec3d forward, Vec3d slamPos, BlockHitResult wallHit) {

        // just make it so they're grounded, because theres still something physical to punch into
        boolean casterGrounded = true;

        // reusing same damage (im lazy)
        doGroundSlam(player, w, pos, forward, slamPos, casterGrounded);

        // this is the extra logic for the wall damage
        int wallMaxBreak = 14;      // separate budget
        float wallMaxHardness = 3.5f; // bit more than ground slam
        int broken = 0;

        BlockPos hitPos = wallHit.getBlockPos();
        Vec3d n = Vec3d.of(wallHit.getSide().getVector()); // face normal

        // get the damage area
        int halfW = 1;
        int halfH = 1;
        int depth = 2;

        // check where they're facing
        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int v = -halfH; v <= halfH; v++) {
                for (int u = -halfW; u <= halfW; u++) {
                    if (broken >= wallMaxBreak) break;

                    BlockPos p;
                    if (xWall) {
                        // plane spans Z/Y
                        p = hitPos.add((int)(-n.x * d), v, u);
                    } else {
                        // plane spans X/Y
                        p = hitPos.add(u, v, (int)(-n.z * d));
                    }

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getHardness(w, p);
                    if (hardness < 0) continue;
                    if (hardness > wallMaxHardness) continue;

                    // random chance to actually break block
                    if (w.random.nextFloat() > 0.75f) continue;

                    if (w.breakBlock(p, true, player)) {
                        broken++;
                    }
                }
            }
        }
    }

    private static BlockHitResult findWallSlamHit(ServerWorld w, ServerPlayerEntity player, Vec3d forward) { // checks if there's a wall in front
        // how far a wall is looked for
        double maxDist = 2.2;

        Vec3d start = player.getEyePos();
        Vec3d end = start.add(forward.normalize().multiply(maxDist));

        HitResult hr = w.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hr.getType() != HitResult.Type.BLOCK) return null;

        BlockHitResult bhr = (BlockHitResult) hr;

        // checks if its an actual block
        BlockState s = w.getBlockState(bhr.getBlockPos());
        if (s.isAir()) return null;

        return bhr;
    }

    private static void applySlamDrag(ServerPlayerEntity player) { // drags down a bit before slam is applied
        Vec3d v = player.getVelocity();

        // try to keep it subtle
        double newY;
        if (v.y > 0.0) {
            newY = -0.12;
        } else {
            // If already falling slow them for a second
            newY = Math.min(v.y, -0.12);
        }

        player.setVelocity(v.x, newY, v.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player)); // let the server know we zoomin so it stops whining
    }

    private static boolean isNearGround(ServerPlayerEntity player, ServerWorld w) {
        // checks if player is close to ground to avoid dodgy stuff when sprint jumping
        // distance from ground
        final double leeway = 1.60;

        // If actually grounded no check needed.
        if (player.isOnGround()) return true;

        // if they are rising fast, no slam allowed
        // if (player.getVelocity().y > 0.18) return false;
        // this was pointless.

        Vec3d start = player.getPos().add(0.0, 0.05, 0.0);   // near feet
        Vec3d end   = start.add(0.0, -leeway, 0.0);          // downwards

        HitResult hr = w.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hr.getType() != HitResult.Type.BLOCK) return false;

        BlockHitResult bhr = (BlockHitResult) hr;
        BlockState s = w.getBlockState(bhr.getBlockPos());
        return !s.isAir();
    }

        /* ============================================================
       SECONDARY
       ============================================================ */

    private static final float RUSH_HIT_DAMAGE = 4.0f; // damage on collision
    private static final float RUSH_HIT_KNOCKUP = 0.95f; // upwards knockback
    private static final double RUSH_WALL_CHECK_DIST = 0.75; // how far ahead we check for a wall
    private static final int RUSH_WALL_MAX_BLOCKS = 18;      // how many blocks wall crash can break
    private static final float RUSH_WALL_MAX_HARDNESS = 3.5f; // max hardness of blocks broken
    private static final float RUSH_WALL_BREAK_CHANCE = 0.80f; // chance for any walls in area to break
    private static final float RUSH_CRASH_SELF_DAMAGE = 4.0f; // boy i wonder
    private static final float RUSH_CRASH_AOE_DAMAGE = 6.0f; // explosion damage
    private static final double RUSH_CRASH_AOE_RADIUS = 4.5; // explosion size
    private static final int RUSH_STEER_TICKS = 7;      // window to adjust direction before locked
    private static final int RUSH_CANCEL_COOLDOWN_TICKS = 4; // time until ability can be cancelled

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        setSingleTimerTag(player, RUSHING, RUSH_TICKS);

        ServerWorld w = player.getServerWorld();

        // sound
        w.playSound(null, player.getBlockPos(),
                ModSounds.LUNGESTART,
                player.getSoundCategory(),
                1.2f, 1.0f);

        w.playSound(null, player.getBlockPos(),
                ModSounds.BULLRUSH,
                player.getSoundCategory(),
                1.5f, 1.0f);

        Vec3d dir = player.getRotationVec(1.0f).normalize();
        setRushDir(player, dir);

        setSingleTimerTag(player, RUSH_HIT_LOCK, 0);
        setSingleTimerTag(player, RUSH_CANCEL_LOCK, RUSH_CANCEL_COOLDOWN_TICKS);

        enableRushStepUp(player, true);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBullrush(ServerPlayerEntity player) {
        int left = getTimerLeft(player, RUSHING);

        // If we just ended, revert step-up.
        if (left <= 0) {
            enableRushStepUp(player, false);
            return;
        }

        // CANCEL
        if (getTimerLeft(player, RUSH_CANCEL_LOCK) <= 0 && player.isSneaking()) {
            // stop rush and revert
            removeTagPrefix(player, RUSHING);
            enableRushStepUp(player, false);

            // tiny feedback
            ServerWorld w = player.getServerWorld();
            w.playSound(null, player.getBlockPos(),
                    ModSounds.BULLRUSH,
                    player.getSoundCategory(),
                    0.6f, 0.9f);

            w.spawnParticles(
                    ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.2, player.getZ(),
                    10,
                    0.25, 0.10, 0.25,
                    0.02
            );

            return;
        }

        // lock direction from stored tag
        Vec3d dir = getRushDir(player);
        if (dir == null) dir = player.getRotationVec(1.0f).normalize();

        // STEERING WINDOW
        int elapsed = Math.max(0, RUSH_TICKS - left);

        if (elapsed < RUSH_STEER_TICKS) {
            double maxTurnDeg = 14.0; // how far they can adjust
            double maxTurn = Math.toRadians(maxTurnDeg);

            Vec3d look = player.getRotationVec(1.0f);
            Vec3d a = new Vec3d(dir.x, 0.0, dir.z);
            Vec3d b = new Vec3d(look.x, 0.0, look.z);

            if (a.lengthSquared() > 1.0e-6 && b.lengthSquared() > 1.0e-6) {
                a = a.normalize();
                b = b.normalize();

                // angle limit
                double dot = MathHelper.clamp(a.x * b.x + a.z * b.z, -1.0, 1.0);
                double ang = Math.acos(dot);

                if (ang > 1.0e-6) {
                    double t = Math.min(1.0, maxTurn / ang);

                    // rotate by moving a fraction toward b
                    Vec3d blended = new Vec3d(
                            MathHelper.lerp(t, a.x, b.x),
                            0.0,
                            MathHelper.lerp(t, a.z, b.z)
                    );

                    if (blended.lengthSquared() > 1.0e-6) {
                        dir = blended.normalize();
                        setRushDir(player, dir); // lock it in once window ends
                    }
                }
            }
        }

        // WALL CHECKS
        BlockHitResult wallHit = findRushWallHit(player.getServerWorld(), player, dir);
        if (wallHit != null) {
            // stop rush and revert
            removeTagPrefix(player, RUSHING);
            enableRushStepUp(player, false);

            doRushCrash(player, player.getServerWorld(), wallHit, dir);
            return;
        }

        // MOVEMENT
        Vec3d vel = player.getVelocity();

        // keep pushing forward
        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);

        // attempt to step up
        boolean stepped = tryRushStepUp(player, player.getServerWorld(), horiz);

        Vec3d push = horiz.normalize().multiply(RUSH_SPEED);

        // re read velocity after step since it was changed
        vel = player.getVelocity();

        // if we stepped, keep the upward momemtum instad of limiting speed
        double newY;
        if (stepped) {
            newY = Math.max(vel.y, 0.52);
        } else {
            newY = MathHelper.clamp(vel.y, -0.35, 0.35);
        }

        player.setVelocity(push.x, newY, push.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player)); // let the server know we zoomin so it stops whining

        // fx
        ServerWorld w = player.getServerWorld();

        // dust at feet
        if (w.random.nextFloat() < 0.85f) {
            BlockPos under = player.getBlockPos().down();
            BlockState underState = w.getBlockState(under);
            if (!underState.isAir()) {
                w.spawnParticles(
                        new BlockStateParticleEffect(ParticleTypes.BLOCK, underState),
                        player.getX(), under.getY() + 1.01, player.getZ(),
                        14,
                        0.35, 0.04, 0.35,
                        0.10
                );
            } else {
                w.spawnParticles(
                        ParticleTypes.CLOUD,
                        player.getX(), player.getY() + 0.15, player.getZ(),
                        6,
                        0.18, 0.06, 0.18,
                        0.02
                );
            }
        }

        // in front
        if (w.random.nextFloat() < 0.60f) {
            Vec3d front = player.getPos().add(horiz.normalize().multiply(0.8)).add(0.0, 1.0, 0.0);
            w.spawnParticles(
                    ParticleTypes.CRIT,
                    front.x, front.y, front.z,
                    2,
                    0.08, 0.08, 0.08,
                    0.0
            );
        }

        // ENTITY COLLISION
        boolean canHit = getTimerLeft(player, RUSH_HIT_LOCK) <= 0;
        if (canHit) {
            // hitbox slightly in front
            Vec3d p = player.getPos().add(horiz.normalize().multiply(0.9));
            Box hitBox = new Box(p, p).expand(RUSH_HIT_RADIUS, 1.2, RUSH_HIT_RADIUS);

            List<LivingEntity> hits = w.getEntitiesByClass(
                    LivingEntity.class,
                    hitBox,
                    e -> e.isAlive() && e != player
            );

            if (!hits.isEmpty()) {
                for (LivingEntity t : hits) {
                    t.setAttacker(player); // tag em for the kill feed

                    // damage
                    t.damage(ModDamageTypes.rushCollision(w, player), RUSH_HIT_DAMAGE);

                    // knockup
                    Vec3d tv = t.getVelocity();
                    t.setVelocity(tv.x, Math.max(tv.y, RUSH_HIT_KNOCKUP), tv.z);
                    t.velocityModified = true;

                    if (t instanceof ServerPlayerEntity sp) {
                        sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp)); // let the server know they zoomin so it stops whining
                    }

                    // feedback
                    w.spawnParticles(ParticleTypes.CRIT,
                            t.getX(), t.getY() + t.getHeight() * 0.55, t.getZ(),
                            8, 0.16, 0.16, 0.16, 0.02);
                    w.spawnParticles(ParticleTypes.CLOUD,
                            t.getX(), t.getY() + 0.15, t.getZ(),
                            14, 0.25, 0.10, 0.25, 0.04);
                }

                w.playSound(null, player.getBlockPos(),
                        ModSounds.ENTITYSLAM,
                        player.getSoundCategory(), 0.9f, 0.9f);

                // avoid spam hits
                setSingleTimerTag(player, RUSH_HIT_LOCK, 6);
            }
        }
    }

    private static BlockHitResult findRushWallHit(ServerWorld w, ServerPlayerEntity player, Vec3d dir) {
        // this also checks if player should step up
        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) return null;

        Vec3d fwd = horiz.normalize().multiply(RUSH_WALL_CHECK_DIST);

        // low ray
        Vec3d lowStart = player.getPos().add(0.0, 0.20, 0.0);
        Vec3d lowEnd   = lowStart.add(fwd);

        HitResult low = w.raycast(new RaycastContext(
                lowStart, lowEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        // high ray
        Vec3d highStart = player.getPos().add(0.0, player.getHeight() * 0.90, 0.0);
        Vec3d highEnd   = highStart.add(fwd);

        HitResult high = w.raycast(new RaycastContext(
                highStart, highEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        boolean lowBlock  = (low.getType()  == HitResult.Type.BLOCK);
        boolean highBlock = (high.getType() == HitResult.Type.BLOCK);

        // if its low, try not to crash
        if (lowBlock && !highBlock) return null;

        // If high ray hit its going to be a wall, crash.
        if (!highBlock) return null;
        return (BlockHitResult) high;
    }

    private static void doRushCrash(ServerPlayerEntity player, ServerWorld w, BlockHitResult wallHit, Vec3d dir) {
        BlockPos hitPos = wallHit.getBlockPos();

        // stop player
        player.setVelocity(0, player.getVelocity().y * 0.25, 0);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player)); // let the server know we zoomin so it stops whining

        // fx
        Vec3d impact = wallHit.getPos();
        w.playSound(null, player.getBlockPos(),
                ModSounds.WALLSLAM,
                player.getSoundCategory(), 1.0f, 1.00f);

        w.playSound(null, player.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE,
                player.getSoundCategory(), 0.8f, 0.85f);

        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                impact.x, impact.y, impact.z,
                1, 0, 0, 0, 0);

        // count blocks broken (probably will remove this)
        int broken = 0;

        // normal points out of the hit face
        Vec3d n = Vec3d.of(wallHit.getSide().getVector());

        int halfW = 1; // width
        int halfH = 1; // height
        int depth = 2; // how deep into wall

        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= RUSH_WALL_MAX_BLOCKS) break;

                    BlockPos p;
                    if (xWall) {
                        // plane spans Z/Y, depth along X
                        p = hitPos.add((int) (-n.x * d), y, xz);
                    } else {
                        // plane spans X/Y, depth along Z
                        p = hitPos.add(xz, y, (int) (-n.z * d));
                    }

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getHardness(w, p);
                    if (hardness < 0) continue;
                    if (hardness > RUSH_WALL_MAX_HARDNESS) continue;

                    if (w.random.nextFloat() > RUSH_WALL_BREAK_CHANCE) continue;

                    if (w.breakBlock(p, true, player)) broken++;
                }
            }
        }

        // self damage
        player.damage(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_SELF_DAMAGE);

        // damage and kb
        Vec3d center = impact;
        Box box = new Box(center, center).expand(RUSH_CRASH_AOE_RADIUS, 2.0, RUSH_CRASH_AOE_RADIUS);

        List<LivingEntity> victims = w.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity t : victims) {
            t.setAttacker(player); // tag em for the kill feed
            t.damage(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_AOE_DAMAGE);

            Vec3d tv = t.getVelocity();
            t.setVelocity(tv.x, Math.max(tv.y, 0.65), tv.z);
            t.velocityModified = true;

            if (t instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp)); // let the server know they zoomin so it stops whining
            }
        }

        // camera shake at impact
        CameraShake.shakeNearby(player, 8.0, 10, 1.15f);

        // extra dust
        w.spawnParticles(ParticleTypes.CLOUD,
                impact.x, impact.y, impact.z,
                120, 1.0, 0.35, 1.0, 0.12);
        w.spawnParticles(ParticleTypes.CRIT,
                impact.x, impact.y, impact.z,
                50, 0.8, 0.25, 0.8, 0.18);
    }

    private static void enableRushStepUp(ServerPlayerEntity player, boolean enable) { // increases step height when rushing to stop getting stuck
        player.setStepHeight(enable ? 1.05f : 0.6f);
    }

    private static boolean tryRushStepUp(ServerPlayerEntity player, ServerWorld w, Vec3d horizDir) { // try to push player up one block when rushing
        // only attempt if basically grounded-ish, otherwise it feels like flying
        if (!player.isOnGround() && player.getVelocity().y > 0.12) return false;

        Vec3d fwd = horizDir.normalize();
        if (fwd.lengthSquared() < 1.0e-6) return false;

        // sample block in front of feet
        Vec3d feet = player.getPos().add(0.0, 0.05, 0.0);
        Vec3d ahead = feet.add(fwd.multiply(0.55));

        BlockPos front = BlockPos.ofFloored(ahead);
        BlockPos frontUp = front.up();

        BlockState sFront = w.getBlockState(front);
        // actual solid block
        boolean frontBlocks = !sFront.getCollisionShape(w, front).isEmpty();
        if (!frontBlocks) return false;

        // need space above to step up
        BlockState sFrontUp = w.getBlockState(frontUp);
        boolean upBlocks = !sFrontUp.getCollisionShape(w, frontUp).isEmpty();
        if (upBlocks) return false;

        // don't step if has upwards velocity
        if (player.getVelocity().y > 0.30) return false;

        // chuck them up
        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.52), v.z);
        player.velocityModified = true;

        return true;
    }

    /* ============================================================
       ULT
       ============================================================ */

    // fx stuff
    // Rage FX markers
    private static final String RAGE_FX_BURST = "st_rage_fx_";          // one-shot on activation
    private static final String RAGE_AURA_STEP = "st_rage_aura_step_";  // cadence for aura particles
    private static final String RAGE_HB_STEP = "st_rage_hb_step_";      // cadence for heartbeat sound
    // Rage FX tuning
    private static final double RAGE_FX_RADIUS = 10.0;     // who gets shake + heartbeat
    private static final int RAGE_AURA_INTERVAL = 2;       // ticks between aura bursts
    private static final int RAGE_HB_INTERVAL = 14;        // ticks between heartbeats (~0.7s)

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        setSingleTimerTag(player, RAGING, RAGE_TICKS);

        // particles
        setSingleTimerTag(player, RAGE_FX_BURST, 2);

        // start delayed stuff
        setSingleTimerTag(player, RAGE_AURA_STEP, 0);
        setSingleTimerTag(player, RAGE_HB_STEP, 0);

        ServerWorld w = player.getServerWorld();

        // sound
        w.playSound(null, player.getBlockPos(),
                ModSounds.RAGE,
                player.getSoundCategory(), 0.8f, 1.00f);

        // reset cooldowns
        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
        PowerManager.clearAbilityCooldown(player, AbilityTypes.SECONDARY);
    }

    private static void tickRageBuffs(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10, RAGE_STR_AMP, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, RAGE_RES_AMP, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, RAGE_SPEED_AMP, true, false));
    }

    private static boolean isRaging(ServerPlayerEntity player) {
        return hasTagPrefix(player, RAGING);
    }

    private static final DustParticleEffect RAGE_RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.0f), 1.35f); // red dust particles

    private static void tickRageFX(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();

        // initial particles
        if (getTimerLeft(player, RAGE_FX_BURST) >= 0) {

            spawnRagePulse(w, player);

            // shake everyone nearby
            CameraShake.shakeNearby(player, RAGE_FX_RADIUS, 18, 1.85f);

            // consume tag
            removeTagPrefix(player, RAGE_FX_BURST);
        }

        // constant particles
        int aura = tickSingleTimer(player, RAGE_AURA_STEP);
        if (aura <= 0) {
            setSingleTimerTag(player, RAGE_AURA_STEP, RAGE_AURA_INTERVAL);

            // aurrraaaaa
            w.spawnParticles(
                    RAGE_RED_DUST,
                    player.getX(), player.getY() + 0.95, player.getZ(),
                    1,
                    0.55, 0.55, 0.55,
                    0.02
            );
        }

        // timed heartbeat sound
        int hb = tickSingleTimer(player, RAGE_HB_STEP);
        if (hb <= 0) {
            setSingleTimerTag(player, RAGE_HB_STEP, RAGE_HB_INTERVAL);

            Vec3d c = player.getPos();
            Box box = new Box(c, c).expand(RAGE_FX_RADIUS, 6.0, RAGE_FX_RADIUS);
            List<ServerPlayerEntity> nearbyPlayers = w.getEntitiesByClass(
                    ServerPlayerEntity.class,
                    box,
                    p -> p.isAlive()
            );

            for (ServerPlayerEntity p : nearbyPlayers) {
                p.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.95f, 0.95f);
            }
        }
    }

    private static void spawnRagePulse(ServerWorld w, ServerPlayerEntity player) {
        double cx = player.getX();
        double cy = player.getY() + 1.0;
        double cz = player.getZ();

        // middle
        w.spawnParticles(
                RAGE_RED_DUST,
                cx, cy, cz,
                28,
                0.12, 0.18, 0.12,
                0.06
        );

        // push particles outward
        int points = 24; // halved this so it doesn't stutter
        double radius = 3.35;          // ring size
        double speed = 0.45;           // how much its pushed
        double y = player.getY() + 0.15;

        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            double dx = Math.cos(a);
            double dz = Math.sin(a);

            w.spawnParticles(
                    RAGE_RED_DUST,
                    cx + dx * radius, y, cz + dz * radius,
                    10,                 // DONT CHANGE!!!
                    dx * speed, 0.03, dz * speed,
                    1.0
            );
        }

        // boom
        w.spawnParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                cx, player.getY() + 0.35, cz,
                1,
                0, 0, 0,
                0
        );

        // ground particles
        w.spawnParticles(
                ParticleTypes.POOF,
                cx, player.getY() + 0.10, cz,
                10,
                0.35, 0.05, 0.35,
                0.05
        );
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName() { return "Strength"; }
    @Override public String getPrimaryName() { return "Slam"; }
    @Override public String getSecondaryName() { return "Bullrush"; }
    @Override public String getUltimateName() { return "Rage"; }

    @Override
    public long getPrimaryCooldownMs() { return 6_000; }

    @Override
    public long getSecondaryCooldownMs() {
        return 9_000;
    }

    @Override
    public long getUltimateCooldownMs() {
        return 20_000;
    }

    /* ============================================================
       HELPERS
       ============================================================ */

    private static boolean hasTagPrefix(Entity e, String prefix) {
        for (String tag : e.getCommandTags()) if (tag.startsWith(prefix)) return true;
        return false;
    }

    private static void removeTagPrefix(Entity e, String prefix) {
        var it = e.getCommandTags().iterator();
        while (it.hasNext()) {
            String tag = it.next();
            if (tag.startsWith(prefix)) { it.remove(); return; }
        }
    }

    private static void setSingleTimerTag(Entity e, String prefix, int ticks) {
        removeTagPrefix(e, prefix);
        e.getCommandTags().add(prefix + ticks);
    }

    private static int tickSingleTimer(Entity e, String prefix) {
        String found = null;
        for (String tag : e.getCommandTags()) {
            if (tag.startsWith(prefix)) { found = tag; break; }
        }
        if (found == null) return -1;

        e.getCommandTags().remove(found);

        int ticks;
        try {
            ticks = Integer.parseInt(found.substring(prefix.length())) - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }

        if (ticks > 0) e.getCommandTags().add(prefix + ticks);
        return ticks;
    }

    private static int getTimerLeft(Entity e, String prefix) {
        for (String tag : e.getCommandTags()) {
            if (!tag.startsWith(prefix)) continue;
            try {
                return Integer.parseInt(tag.substring(prefix.length()));
            } catch (NumberFormatException ex) {
                return -1;
            }
        }
        return -1;
    }

    private static void setRushDir(ServerPlayerEntity p, Vec3d dir) {
        removeTagPrefix(p, RUSH_DIR);

        // pack floats into ints to keep tag short
        int x = (int) Math.round(dir.x * 1000.0);
        int y = (int) Math.round(dir.y * 1000.0);
        int z = (int) Math.round(dir.z * 1000.0);

        p.getCommandTags().add(RUSH_DIR + x + "_" + y + "_" + z);
    }

    private static Vec3d getRushDir(ServerPlayerEntity p) {
        for (String tag : p.getCommandTags()) {
            if (!tag.startsWith(RUSH_DIR)) continue;

            String rest = tag.substring(RUSH_DIR.length());
            String[] parts = rest.split("_");
            if (parts.length != 3) return null;

            try {
                double x = Integer.parseInt(parts[0]) / 1000.0;
                double y = Integer.parseInt(parts[1]) / 1000.0;
                double z = Integer.parseInt(parts[2]) / 1000.0;
                Vec3d v = new Vec3d(x, y, z);
                return (v.lengthSquared() < 1.0e-6) ? null : v.normalize();
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String getOverviewDescription() {
        return "Strength is meant to be a simple, rushdown-type kit that is able to quickly close distances and deal insane damage up close" +
                " but do very little at range. All abilities are meant to compliment the high damage of the normal hits and these abilities are" +
                " destructive to the nearby environment.";
    }

    @Override
    public String getPassiveName() {
        return "Brute Force";
    }

    @Override
    public String getPassiveDescription() {
        return "You have constant strength and can break blocks at the mining power and speed of an iron pickaxe when holding nothing." +
                " You also have a small boost of mining speed when mining anything with a tool.";
    }

    @Override
    public String getPrimaryDescription() {
        return "Slam the ground or wall in front of you. This does high damage to entities closer to you and knocks them upwards. This slam will" +
                "damage any ground/walls in front of you. When there is no terrain around you (e.g. When airborne) slams are significantly less effective.";
    }

    @Override
    public String getSecondaryDescription() {
        return "Gain a burst of speed in the direction you are looking, being able to turn at the beginning but being locked to a direction after that. If" +
                " colliding with an entity, knock them upwards and deal damage. If colliding with a wall, the rush is stopped, the wall is destroyed and you take some" +
                "self damage. You can stop a rush by sneaking.";
    }

    @Override
    public String getUltimateDescription() {
        return "Empower yourself greatly, gaining extra strength, resistance and speed. During this ultimate your ability cooldowns will also" +
                " be greatly decreased and your abilities will be reset on use.";
    }
}