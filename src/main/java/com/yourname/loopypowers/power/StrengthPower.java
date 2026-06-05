package com.yourname.loopypowers.power;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.network.payload.*;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class StrengthPower implements Power {

    /* ============================================================
       STATE STORAGE (OPTIMIZED)
       ============================================================ */

    private static final Map<UUID, StrengthState> ACTIVE_STATES = new HashMap<>();

    private static class StrengthState {
        int rushTicks = 0;
        Vec3d rushDir = null;
        int rushHitLock = 0;
        int rushCancelLock = 0;

        int rageTicks = 0;
        int rageFxBurst = 0;
        int rageAuraStep = 0;
        int rageHbStep = 0;
    }

    private static StrengthState getState(ServerPlayerEntity player) {
        return ACTIVE_STATES.computeIfAbsent(player.getUuid(), k -> new StrengthState());
    }

    /* ============================================================
       EGG
       ============================================================ */
    public static boolean onePunchDebugEnabled = false;
    private static final float ONE_PUNCH_CHANCE = 0.02f;

    /* ============================================================
       TUNING
       ============================================================ */

    // Bullrush
    private static final int    RUSH_TICKS               = 28;
    private static final double RUSH_SPEED               = 1.25;
    private static final double RUSH_HIT_RADIUS          = 1.3;
    private static final double RUSH_MAX_TURN_DEG        = 4.5;
    private static final int    RUSH_CANCEL_COOLDOWN_TICKS = 4;
    private static final float  RUSH_HIT_DAMAGE          = 13.5f;
    private static final float  RUSH_HIT_KNOCKUP         = 0.95f;
    private static final double RUSH_WALL_CHECK_DIST     = 0.75;
    private static final int    RUSH_WALL_MAX_BLOCKS     = 18;
    private static final float  RUSH_WALL_MAX_HARDNESS   = 3.5f;
    private static final float  RUSH_WALL_BREAK_CHANCE   = 0.80f;
    private static final float  RUSH_CRASH_SELF_DAMAGE   = 4.0f;
    private static final float  RUSH_CRASH_AOE_DAMAGE    = 6.0f;
    private static final double RUSH_CRASH_AOE_RADIUS    = 4.5;

    // Rage
    private static final int RAGE_TICKS     = 240;
    private static final int RAGE_STR_AMP   = 1;
    private static final int RAGE_RES_AMP   = 0;
    private static final int RAGE_SPEED_AMP = 0;

    // Slam
    private static final int    SLAM_BLOCK_RADIUS      = 3;
    private static final int    SLAM_MAX_BLOCKS_BROKEN = 22;
    private static final float  SLAM_MAX_HARDNESS      = 2.2f;
    private static final float  SLAM_BLOCK_BREAK_CHANCE = 0.55f;
    private static final float  SLAM_ENTITY_DAMAGE     = 14.0f;
    private static final float  SLAM_OUT               = 0.35f;
    private static final float  SLAM_FRONT_DOT         = 0.35f;
    private static final double SLAM_FRONT_OFFSET      = 1.4;
    private static final double SLAM_RADIUS            = 5.5;
    private static final float  SLAM_UP                = 1.55f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("st_"));
        ACTIVE_STATES.put(player.getUuid(), new StrengthState());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("st_"));
        ACTIVE_STATES.remove(player.getUuid());

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
        if (!player.isAlive()) return;

        StrengthState state = getState(player);

        if (!PassiveManager.isEnabled(player)) return;

        StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
        if (strength == null || strength.getDuration() < 5) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0, true, false));
        }

        if (state.rushHitLock > 0) state.rushHitLock--;
        if (state.rushCancelLock > 0) state.rushCancelLock--;

        if (state.rushTicks > 0) {
            state.rushTicks--;
            tickBullrush(player, state);
        }

        if (state.rageTicks > 0) {
            state.rageTicks--;
            tickRageBuffs(player);
            tickRageFX(player, state);
        }
    }

    @Override
    public boolean onAttack(ServerPlayerEntity attacker, LivingEntity target, DamageSource source, float amount) {
        if (!PassiveManager.isEnabled(attacker)) return true;

        if (amount >= 9999f) return true;

        // ONE PUNCH EASTER EGG
        if (source.getSource() == attacker && attacker.getMainHandStack().isEmpty()) {
            boolean isUnarmoredPlayer = (target instanceof ServerPlayerEntity) && (target.getArmor() == 0);
            if (onePunchDebugEnabled || (isUnarmoredPlayer && attacker.getWorld().random.nextFloat() < ONE_PUNCH_CHANCE)) {
                ServerWorld w = attacker.getServerWorld();

                w.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), ModSounds.ONEPUNCH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

                Vec3d dir = attacker.getRotationVec(1.0f).normalize();
                target.setVelocity(dir.x * 25.0, 4.0, dir.z * 25.0);
                target.velocityModified = true;

                if (target instanceof ServerPlayerEntity spTarget) {
                    spTarget.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(spTarget));
                }

                Vec3d pos = target.getPos();
                StrengthOnePunchPayload fx = new StrengthOnePunchPayload(pos.x, pos.y, pos.z, dir.x, dir.y, dir.z);
                PlayerLookup.tracking(w, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));

                target.damage(ModDamageTypes.onePunch(w, attacker), 9999f);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onHit(ServerPlayerEntity attacker, LivingEntity target) {
        StrengthState state = getState(attacker);
        if (state.rageTicks <= 0) return;

        ServerWorld w = attacker.getServerWorld();

        Vec3d d = target.getPos().subtract(attacker.getPos());
        Vec3d horiz = new Vec3d(d.x, 0.0, d.z);
        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
        Vec3d dir = horiz.normalize();

        double out = 0.95;
        double lift = target.isOnGround() ? 0.18 : 0.08;
        double maxUp = 0.55;

        target.setAttacker(attacker);
        target.addVelocity(dir.x * out, lift, dir.z * out);
        Vec3d tv = target.getVelocity();
        target.setVelocity(tv.x, Math.min(maxUp, Math.max(tv.y, 0.06)), tv.z);
        target.velocityModified = true;

        if (target instanceof ServerPlayerEntity sp) {
            sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
        }

        StrengthRageHitPayload fx = new StrengthRageHitPayload(target.getId());
        PlayerLookup.tracking(w, target.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, fx));

        w.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, attacker.getSoundCategory(), 0.8f, 0.85f);
    }

    /* ============================================================
       PRIMARY
       ============================================================ */

    @Override
    public void activatePrimary(ServerPlayerEntity player) {
        ServerWorld w = player.getServerWorld();
        Vec3d pos = player.getPos();

        boolean casterGrounded = isNearGround(player, w);

        Vec3d forward = player.getRotationVec(1.0f);
        Vec3d slamPos = pos.add(forward.x, 0.0, forward.z);

        if (!casterGrounded) {
            applySlamDrag(player);
        }

        BlockHitResult wallHit = findWallSlamHit(w, player, forward);
        boolean isWallSlam = (wallHit != null);

        if (!casterGrounded && !isWallSlam) {
            doGroundSlam(player, w, pos, forward, slamPos, false);
            return;
        }

        if (isWallSlam) {
            doWallSlam(player, w, pos, forward, slamPos, wallHit);
        } else {
            doGroundSlam(player, w, pos, forward, slamPos, true);
        }
    }

    private static void doGroundSlam(ServerPlayerEntity player, ServerWorld w, Vec3d pos, Vec3d forward, Vec3d slamPos, boolean casterGrounded) {
        BlockPos ground = player.getBlockPos().down();
        BlockState groundState = w.getBlockState(ground);

        BlockPos slamGround = BlockPos.ofFloored(slamPos).down();
        BlockState slamGroundState = w.getBlockState(slamGround);
        if (!slamGroundState.isAir()) {
            ground = slamGround;
            groundState = slamGroundState;
        }

        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SLAM, player.getSoundCategory(),
                casterGrounded ? 1.00f : 0.35f, casterGrounded ? 0.85f : 1.10f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(),
                casterGrounded ? 1.00f : 0.35f, casterGrounded ? 0.85f : 1.10f);

        StrengthSlamPayload slamFx = new StrengthSlamPayload(
                slamPos.x, pos.y, slamPos.z,
                ground.getX(), ground.getY(), ground.getZ(),
                casterGrounded);
        sendToViewers(w, player, slamFx);

        if (casterGrounded) {
            CameraShake.shakeNearby(player, 6.0, 10, 1.05f);
        } else {
            CameraShake.shakeNearby(player, 4.0, 6, 0.35f);
        }

        Box box = new Box(slamPos, slamPos).expand(SLAM_RADIUS, 2.5, SLAM_RADIUS);
        List<LivingEntity> targets = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        for (LivingEntity t : targets) {
            Vec3d d = t.getPos().subtract(slamPos);
            Vec3d horiz = new Vec3d(d.x, 0.0, d.z);
            if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
            Vec3d dir = horiz.normalize();

            double dist = Math.sqrt(horiz.lengthSquared());
            float falloff = 1.0f - (float) MathHelper.clamp(dist / SLAM_RADIUS, 0.0, 1.0);

            double upRaw  = SLAM_UP  * (0.85 + 0.95 * falloff);
            double outRaw = SLAM_OUT * (0.35 + 1.0  * falloff);

            double kbMult  = casterGrounded ? 1.0 : 0.15;
            double dmgMult = casterGrounded ? 1.0 : 0.15;

            double maxUp = 1.15;
            double minUpGround = 0.65;
            double maxOut = 0.55;

            double up  = MathHelper.clamp(upRaw  * kbMult, 0.0, maxUp);
            double out = MathHelper.clamp(outRaw * kbMult, 0.0, maxOut);

            if (t.isOnGround()) {
                up = Math.max(up, minUpGround * kbMult);
            }

            t.setAttacker(player);

            float dmg = (float) (SLAM_ENTITY_DAMAGE * (0.6f + 0.6f * falloff) * dmgMult);
            if (dmg > 0.0f) {
                t.damage(ModDamageTypes.slam(w, player), dmg);
            }

            Vec3d v = t.getVelocity();
            t.setVelocity(v.x + dir.x * out, Math.max(v.y, up), v.z + dir.z * out);
            t.velocityModified = true;

            if (t instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
            }

            if (casterGrounded && w.random.nextFloat() < 0.35f) {
                StrengthSlamTargetPayload targetFx = new StrengthSlamTargetPayload(t.getId());
                PlayerLookup.tracking(w, t.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, targetFx));
            }
        }

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
        doGroundSlam(player, w, pos, forward, slamPos, true);

        int wallMaxBreak = 14;
        float wallMaxHardness = 3.5f;
        int broken = 0;

        BlockPos hitPos = wallHit.getBlockPos();
        Vec3d n = Vec3d.of(wallHit.getSide().getVector());

        int halfW = 1, halfH = 1, depth = 2;
        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= wallMaxBreak) break;

                    BlockPos p = xWall
                            ? hitPos.add((int)(-n.x * d), y, xz)
                            : hitPos.add(xz, y, (int)(-n.z * d));

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getHardness(w, p);
                    if (hardness < 0) continue;
                    if (hardness > wallMaxHardness) continue;

                    if (w.random.nextFloat() > 0.75f) continue;

                    if (w.breakBlock(p, true, player)) broken++;
                }
            }
        }
    }

    private static BlockHitResult findWallSlamHit(ServerWorld w, ServerPlayerEntity player, Vec3d forward) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(forward.normalize().multiply(2.2));

        BlockHitResult bhr = w.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        if (bhr.getType() != HitResult.Type.BLOCK) return null;
        BlockState s = w.getBlockState(bhr.getBlockPos());
        if (s.isAir()) return null;
        return bhr;
    }

    private static void applySlamDrag(ServerPlayerEntity player) {
        Vec3d v = player.getVelocity();
        double newY = v.y > 0.0 ? -0.12 : Math.min(v.y, -0.12);
        player.setVelocity(v.x, newY, v.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static boolean isNearGround(ServerPlayerEntity player, ServerWorld w) {
        if (player.isOnGround()) return true;

        Vec3d start = player.getPos().add(0.0, 0.05, 0.0);
        Vec3d end   = start.add(0.0, -1.60, 0.0);

        BlockHitResult hr = w.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        if (hr.getType() != HitResult.Type.BLOCK) return false;
        return !w.getBlockState(hr.getBlockPos()).isAir();
    }

    /* ============================================================
       SECONDARY
       ============================================================ */

    @Override
    public void activateSecondary(ServerPlayerEntity player) {
        StrengthState state = getState(player);
        state.rushTicks = RUSH_TICKS;
        state.rushDir = player.getRotationVec(1.0f).normalize();
        state.rushHitLock = 0;
        state.rushCancelLock = RUSH_CANCEL_COOLDOWN_TICKS;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.LUNGESTART, player.getSoundCategory(), 1.2f, 1.0f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BULLRUSH, player.getSoundCategory(), 1.5f, 1.0f);

        enableRushStepUp(player, true);
        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBullrush(ServerPlayerEntity player, StrengthState state) {
        if (state.rushTicks <= 0) {
            enableRushStepUp(player, false);
            return;
        }

        // CANCEL
        if (state.rushCancelLock <= 0 && player.isSneaking()) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);

            ServerWorld w = player.getServerWorld();
            w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BULLRUSH, player.getSoundCategory(), 0.6f, 0.9f);

            StrengthRushCancelPayload fx = new StrengthRushCancelPayload(player.getX(), player.getY() + 0.2, player.getZ());
            sendToViewers(w, player, fx);
            return;
        }

        Vec3d dir = state.rushDir;
        if (dir == null) dir = player.getRotationVec(1.0f).normalize();

        // CONTINUOUS STEERING — max RUSH_MAX_TURN_DEG degrees per tick
        {
            double maxTurn = Math.toRadians(RUSH_MAX_TURN_DEG);
            Vec3d look = player.getRotationVec(1.0f);
            Vec3d a = new Vec3d(dir.x, 0.0, dir.z);
            Vec3d b = new Vec3d(look.x, 0.0, look.z);

            if (a.lengthSquared() > 1e-5 && b.lengthSquared() > 1e-5) {
                a = a.normalize();
                b = b.normalize();
                double dot = MathHelper.clamp(a.x * b.x + a.z * b.z, -1.0, 1.0);
                double ang = Math.acos(dot);
                if (ang > maxTurn) {
                    // Use 2D cross-product Y component to determine turn direction
                    double crossY = a.z * b.x - a.x * b.z;
                    double sign = crossY >= 0 ? -1.0 : 1.0;
                    double clampedAng = sign * maxTurn;
                    double cos = Math.cos(clampedAng);
                    double sin = Math.sin(clampedAng);
                    dir = new Vec3d(a.x * cos - a.z * sin, look.y, a.x * sin + a.z * cos).normalize();
                } else {
                    dir = look;
                }
                state.rushDir = dir;
            }
        }

        ServerWorld w = player.getServerWorld();

        // CLEAR DECORATIVE BLOCKS in path
        BlockPos basePos = player.getBlockPos();
        for (BlockPos bPos : BlockPos.iterate(basePos.add(-1, 0, -1), basePos.add(1, 1, 1))) {
            BlockState bs = w.getBlockState(bPos);
            if (!bs.isAir()) {
                if (bs.isReplaceable()
                        || bs.isIn(BlockTags.LEAVES)
                        || bs.isIn(BlockTags.FLOWERS)
                        || bs.isIn(BlockTags.SMALL_FLOWERS)
                        || bs.isIn(BlockTags.TALL_FLOWERS)) {
                    w.breakBlock(bPos, true, player);
                }
            }
        }

        // WALL CHECKS
        BlockHitResult wallHit = findRushWallHit(w, player, dir);
        if (wallHit != null) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);
            doRushCrash(player, w, wallHit);
            return;
        }

        // MOVEMENT
        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);

        if (!tryRushStepUp(player, w, horiz)) {
            player.setVelocity(horiz.x * RUSH_SPEED, player.getVelocity().y, horiz.z * RUSH_SPEED);
            player.velocityModified = true;
        }
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        // TRAIL FX + STEP SOUND
        {
            boolean playStep = w.getTime() % 2 == 0;
            BlockPos under = player.getBlockPos().down();
            BlockState underState = w.getBlockState(under);
            boolean hasGround = !underState.isAir();

            Vec3d horizNorm = horiz.normalize();
            boolean showCrit = w.random.nextFloat() < 0.60f;

            StrengthRushTrailPayload trail = new StrengthRushTrailPayload(
                    player.getX(), player.getY(), player.getZ(),
                    under.getX(), under.getY(), under.getZ(),
                    hasGround,
                    (float) dir.x, (float) dir.z,
                    showCrit);
            sendToViewers(w, player, trail);

            if (playStep) {
                w.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_WARDEN_STEP, player.getSoundCategory(), 0.5f, 0.8f);
            }
        }

        // ENTITY COLLISION
        if (state.rushHitLock <= 0) {
            Vec3d horizNorm = horiz.normalize();
            Vec3d p = player.getPos().add(horizNorm.multiply(0.9));
            Box hitBox = new Box(p, p).expand(RUSH_HIT_RADIUS, 1.2, RUSH_HIT_RADIUS);

            List<LivingEntity> hits = w.getEntitiesByClass(LivingEntity.class, hitBox, e -> e.isAlive() && e != player);

            if (!hits.isEmpty()) {
                for (LivingEntity t : hits) {
                    t.setAttacker(player);
                    t.damage(ModDamageTypes.rushCollision(w, player), RUSH_HIT_DAMAGE);

                    Vec3d tv = t.getVelocity();
                    t.setVelocity(tv.x, Math.max(tv.y, RUSH_HIT_KNOCKUP), tv.z);
                    t.velocityModified = true;

                    if (t instanceof ServerPlayerEntity sp) {
                        sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
                    }

                    StrengthRushEntityHitPayload hitFx = new StrengthRushEntityHitPayload(t.getId());
                    PlayerLookup.tracking(w, t.getBlockPos()).forEach(sp -> ServerPlayNetworking.send(sp, hitFx));
                }

                w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.ENTITYSLAM, player.getSoundCategory(), 0.9f, 0.9f);
                state.rushHitLock = 6;
            }
        }
    }

    private static BlockHitResult findRushWallHit(ServerWorld w, ServerPlayerEntity player, Vec3d dir) {
        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) return null;

        Vec3d fwd = horiz.normalize().multiply(RUSH_WALL_CHECK_DIST);

        Vec3d lowStart = player.getPos().add(0.0, 0.20, 0.0);
        HitResult low = w.raycast(new RaycastContext(lowStart, lowStart.add(fwd),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        Vec3d highStart = player.getPos().add(0.0, player.getHeight() * 0.90, 0.0);
        BlockHitResult high = w.raycast(new RaycastContext(highStart, highStart.add(fwd),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        boolean lowBlock  = (low.getType()  == HitResult.Type.BLOCK);
        boolean highBlock = (high.getType() == HitResult.Type.BLOCK);

        if (lowBlock && !highBlock) return null;
        if (!highBlock) return null;
        return high;
    }

    private static void doRushCrash(ServerPlayerEntity player, ServerWorld w, BlockHitResult wallHit) {
        player.setVelocity(0, player.getVelocity().y * 0.25, 0);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        Vec3d impact = wallHit.getPos();
        BlockPos hitPos = wallHit.getBlockPos();

        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.WALLSLAM, player.getSoundCategory(), 1.0f, 1.00f);
        w.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, player.getSoundCategory(), 0.8f, 0.85f);

        StrengthRushCrashPayload fx = new StrengthRushCrashPayload(impact.x, impact.y, impact.z);
        sendToViewers(w, player, fx);

        int broken = 0;
        Vec3d n = Vec3d.of(wallHit.getSide().getVector());
        int halfW = 1, halfH = 1, depth = 2;
        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= RUSH_WALL_MAX_BLOCKS) break;

                    BlockPos p = xWall
                            ? hitPos.add((int)(-n.x * d), y, xz)
                            : hitPos.add(xz, y, (int)(-n.z * d));

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

        player.damage(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_SELF_DAMAGE);

        Box box = new Box(impact, impact).expand(RUSH_CRASH_AOE_RADIUS, 2.0, RUSH_CRASH_AOE_RADIUS);
        List<LivingEntity> victims = w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != player);

        for (LivingEntity t : victims) {
            t.setAttacker(player);
            t.damage(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_AOE_DAMAGE);

            Vec3d tv = t.getVelocity();
            t.setVelocity(tv.x, Math.max(tv.y, 0.65), tv.z);
            t.velocityModified = true;

            if (t instanceof ServerPlayerEntity sp) {
                sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
            }
        }

        CameraShake.shakeNearby(player, 8.0, 10, 1.15f);
    }

    private static void enableRushStepUp(ServerPlayerEntity player, boolean enable) {
        var attr = player.getAttributeInstance(EntityAttributes.GENERIC_STEP_HEIGHT);
        if (attr != null) attr.setBaseValue(enable ? 1.05 : 0.6);
    }

    private static boolean tryRushStepUp(ServerPlayerEntity player, ServerWorld w, Vec3d horizDir) {
        if (!player.isOnGround() && player.getVelocity().y > 0.12) return false;

        Vec3d fwd = horizDir.normalize();
        if (fwd.lengthSquared() < 1.0e-6) return false;

        Vec3d feet = player.getPos().add(0.0, 0.05, 0.0);
        Vec3d ahead = feet.add(fwd.multiply(0.8));

        BlockPos front   = BlockPos.ofFloored(ahead);
        BlockPos frontUp = front.up();

        BlockState sFront = w.getBlockState(front);
        var shape = sFront.getCollisionShape(w, front);
        if (shape.isEmpty()) return false;
        if (shape.getMax(net.minecraft.util.math.Direction.Axis.Y) <= 0.56) return false;

        BlockState sFrontUp = w.getBlockState(frontUp);
        if (!sFrontUp.getCollisionShape(w, frontUp).isEmpty()) return false;

        if (player.getVelocity().y > 0.30) return false;

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.52), v.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        return true;
    }

    /* ============================================================
       ULT
       ============================================================ */

    private static final int RAGE_FX_RADIUS   = 10;
    private static final int RAGE_AURA_INTERVAL = 2;
    private static final int RAGE_HB_INTERVAL   = 14;

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        StrengthState state = getState(player);
        state.rageTicks    = RAGE_TICKS;
        state.rageFxBurst  = 2;
        state.rageAuraStep = 0;
        state.rageHbStep   = 0;

        ServerWorld w = player.getServerWorld();
        w.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RAGE, player.getSoundCategory(), 0.8f, 1.00f);

        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
        PowerManager.clearAbilityCooldown(player, AbilityTypes.SECONDARY);
    }

    private static void tickRageBuffs(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,   10, RAGE_STR_AMP,   true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,  10, RAGE_RES_AMP,   true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,       10, RAGE_SPEED_AMP, true, false));
    }

    public static boolean isRaging(ServerPlayerEntity player) {
        return ACTIVE_STATES.getOrDefault(player.getUuid(), new StrengthState()).rageTicks > 0;
    }

    private static void tickRageFX(ServerPlayerEntity player, StrengthState state) {
        ServerWorld w = player.getServerWorld();

        if (state.rageFxBurst >= 0) {
            StrengthRagePulsePayload pulse = new StrengthRagePulsePayload(player.getX(), player.getY(), player.getZ());
            sendToViewers(w, player, pulse);
            CameraShake.shakeNearby(player, RAGE_FX_RADIUS, 18, 1.85f);
            state.rageFxBurst = -1;
        }

        if (state.rageAuraStep <= 0) {
            state.rageAuraStep = RAGE_AURA_INTERVAL;
            StrengthRageAuraPayload aura = new StrengthRageAuraPayload(player.getX(), player.getY() + 0.95, player.getZ());
            sendToViewers(w, player, aura);
        } else {
            state.rageAuraStep--;
        }

        if (state.rageHbStep <= 0) {
            state.rageHbStep = RAGE_HB_INTERVAL;
            w.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WARDEN_HEARTBEAT, net.minecraft.sound.SoundCategory.PLAYERS, 0.95f, 0.95f);
        } else {
            state.rageHbStep--;
        }
    }

    private static <T extends net.minecraft.network.packet.CustomPayload> void sendToViewers(
            ServerWorld w, ServerPlayerEntity player, T payload) {
        Set<ServerPlayerEntity> viewers = new HashSet<>();
        PlayerLookup.tracking(w, player.getBlockPos()).forEach(viewers::add);
        viewers.add(player);
        viewers.forEach(sp -> ServerPlayNetworking.send(sp, payload));
    }

    /* ============================================================
       COOLDOWNS / DISPLAY
       ============================================================ */

    @Override public String getName()              { return Text.translatable("power.loopypowers.strength.name").getString(); }
    @Override public String getPrimaryName()       { return Text.translatable("power.loopypowers.strength.primary_name").getString(); }
    @Override public String getSecondaryName()     { return Text.translatable("power.loopypowers.strength.secondary_name").getString(); }
    @Override public String getUltimateName()      { return Text.translatable("power.loopypowers.strength.ultimate_name").getString(); }
    @Override public long getPrimaryCooldownMs()   { return 12_500; }
    @Override public long getSecondaryCooldownMs() { return 18_500; }
    @Override public long getUltimateCooldownMs()  { return 280_000; }
    @Override public String getOverviewDescription() { return Text.translatable("power.loopypowers.strength.description.overview").getString(); }
    @Override public String getPassiveName()         { return Text.translatable("power.loopypowers.strength.passive_name").getString(); }
    @Override public String getPassiveDescription()  { return Text.translatable("power.loopypowers.strength.description.passive").getString(); }
    @Override public String getPrimaryDescription()  { return Text.translatable("power.loopypowers.strength.description.primary").getString(); }
    @Override public String getSecondaryDescription(){ return Text.translatable("power.loopypowers.strength.description.secondary").getString(); }
    @Override public String getUltimateDescription() { return Text.translatable("power.loopypowers.strength.description.ultimate").getString(); }
}
