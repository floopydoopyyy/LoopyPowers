package com.yourname.loopypowers.power;

import com.yourname.loopypowers.manager.AbilityTypes;
import com.yourname.loopypowers.manager.PassiveManager;
import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.network.CameraShake;
import com.yourname.loopypowers.sound.ModSounds;
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
import net.minecraft.entity.damage.DamageSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int RUSH_TICKS = 26;     // duration
    private static final double RUSH_SPEED = 1.25;
    private static final double RUSH_HIT_RADIUS = 1.3;
    private static final int RUSH_STEER_TICKS = 7;
    private static final int RUSH_CANCEL_COOLDOWN_TICKS = 4;
    private static final float RUSH_HIT_DAMAGE = 11.5f;
    private static final float RUSH_HIT_KNOCKUP = 0.95f;
    private static final double RUSH_WALL_CHECK_DIST = 0.75;
    private static final int RUSH_WALL_MAX_BLOCKS = 18;
    private static final float RUSH_WALL_MAX_HARDNESS = 3.5f;
    private static final float RUSH_WALL_BREAK_CHANCE = 0.80f;
    private static final float RUSH_CRASH_SELF_DAMAGE = 4.0f;
    private static final float RUSH_CRASH_AOE_DAMAGE = 6.0f;
    private static final double RUSH_CRASH_AOE_RADIUS = 4.5;

    // Rage
    private static final int RAGE_TICKS = 240; // 12s
    private static final int RAGE_STR_AMP = 1;    // Strength II
    private static final int RAGE_RES_AMP = 0;    // Resistance I
    private static final int RAGE_SPEED_AMP = 0;  // Speed I

    // Slam
    private static final int SLAM_BLOCK_RADIUS = 3;
    private static final int SLAM_MAX_BLOCKS_BROKEN = 22;
    private static final float SLAM_MAX_HARDNESS = 2.2f;
    private static final float SLAM_BLOCK_BREAK_CHANCE = 0.55f;
    private static final float SLAM_ENTITY_DAMAGE = 12.0f;
    private static final float SLAM_OUT = 0.35f;
    private static final float SLAM_FRONT_DOT = 0.35f;
    private static final double SLAM_FRONT_OFFSET = 1.4;
    private static final double SLAM_RADIUS = 5.5;
    private static final float SLAM_UP = 1.55f;

    /* ============================================================
       BASIC
       ============================================================ */

    @Override
    public void onAssign(ServerPlayerEntity player) {
        player.getCommandTags().removeIf(tag -> tag.startsWith("st_")); // Clean legacy tags
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
        StrengthState state = getState(player);

        if (!PassiveManager.isEnabled(player)) return;

        StatusEffectInstance strength = player.getStatusEffect(StatusEffects.STRENGTH);
        if (strength == null || strength.getDuration() < 5) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0, true, false));
        }

        // Tick timers
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

        // GUARD CLAUSE - should've remembered this on release lol
        if (amount >= 9999f) return true;

        // ONE PUNCH EASTER EGG
        if (source.getSource() == attacker && attacker.getMainHandStack().isEmpty()) {
            boolean isUnarmoredPlayer = (target instanceof ServerPlayerEntity) && (target.getArmor() == 0);
            if (onePunchDebugEnabled || (isUnarmoredPlayer && attacker.getWorld().random.nextFloat() < ONE_PUNCH_CHANCE)) {
                ServerWorld w = attacker.getServerWorld();

                w.playSound(null, attacker.getBlockPos(), ModSounds.ONEPUNCH, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.0f);

                Vec3d dir = attacker.getRotationVec(1.0f).normalize();
                target.setVelocity(dir.x * 25.0, 4.0, dir.z * 25.0);
                target.velocityModified = true;

                if (target instanceof ServerPlayerEntity spTarget) {
                    spTarget.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(spTarget));
                }

                Vec3d pos = target.getPos();
                w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0, pos.z, 2, 0, 0, 0, 0);
                w.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y + 1.0, pos.z, 5, 1.0, 1.0, 1.0, 0);

                for (int i = 0; i < 35; i++) {
                    double step = i * 3.5;
                    double px = pos.x + dir.x * step;
                    double py = pos.y + 1.0 + dir.y * step;
                    double pz = pos.z + dir.z * step;

                    w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, px, py, pz, 5, 0.5, 0.5, 0.5, 0.1);
                    w.spawnParticles(ParticleTypes.CLOUD, px, py, pz, 10, 3.0, 3.0, 3.0, 0.3);

                    if (i % 3 == 0) {
                        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, px, py, pz, 1, 0, 0, 0, 0);
                        w.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 2, 4.0, 4.0, 4.0, 0);
                    }
                }

                target.damage(ModDamageTypes.onePunch(w, attacker), 9999f);
                return false; // Cancel original punch damage
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

        w.spawnParticles(
                ParticleTypes.CLOUD,
                target.getX(), target.getY() + target.getHeight() * 0.55, target.getZ(),
                14,
                0.18, 0.18, 0.18,
                0.02
        );

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

        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                slamPos.x, pos.y + 0.10, slamPos.z,
                1, 0, 0, 0, 0
        );

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

        if (!groundState.isAir()) {
            int dustCountA = casterGrounded ? 420 : 20;
            int dustCountB = casterGrounded ? 220 : 8;

            w.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                    slamPos.x, ground.getY() + 1.01, slamPos.z,
                    dustCountA,
                    casterGrounded ? 2.2 : 0.5,
                    casterGrounded ? 0.18 : 0.08,
                    casterGrounded ? 2.2 : 0.5,
                    casterGrounded ? 0.75 : 0.08
            );

            w.spawnParticles(
                    new BlockStateParticleEffect(ParticleTypes.BLOCK, groundState),
                    slamPos.x, ground.getY() + 1.01, slamPos.z,
                    dustCountB,
                    casterGrounded ? 0.65 : 0.20,
                    casterGrounded ? 1.10 : 0.25,
                    casterGrounded ? 0.65 : 0.20,
                    casterGrounded ? 1.15 : 0.12
            );

            if (casterGrounded) {
                double[] radii = new double[] { 1.4, 2.8, 4.2 };
                int[] counts   = new int[]    { 24, 32, 42 };
                double[] spreads = new double[]{ 0.25, 0.30, 0.38 };
                double[] speeds  = new double[]{ 0.35, 0.40, 0.45 };

                for (int ri = 0; ri < radii.length; ri++) {
                    double r = radii[ri];
                    int n = counts[ri];

                    for (int i = 0; i < n; i++) {
                        double a = w.random.nextDouble() * (Math.PI * 2.0);
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

        if (casterGrounded) {
            CameraShake.shakeNearby(player, 6.0, 10, 1.05f);
        } else {
            CameraShake.shakeNearby(player, 4.0, 6, 0.35f);
        }

        Box box = new Box(slamPos, slamPos).expand(SLAM_RADIUS, 2.5, SLAM_RADIUS);

        List<LivingEntity> targets = w.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

        for (LivingEntity t : targets) {
            Vec3d d = t.getPos().subtract(slamPos);
            Vec3d horiz = new Vec3d(d.x, 0.0, d.z);
            if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);
            Vec3d dir = horiz.normalize();

            double dist = Math.sqrt(horiz.lengthSquared());
            float falloff = 1.0f - (float) MathHelper.clamp(dist / SLAM_RADIUS, 0.0, 1.0);

            double upRaw  = SLAM_UP  * (0.85 + 0.95 * falloff);
            double outRaw = SLAM_OUT * (0.35 + 1.0  * falloff);

            double kbMult = casterGrounded ? 1.0 : 0.15;
            double dmgMult = casterGrounded ? 1.0 : 0.15;

            double maxUp = 1.15;
            double minUpGround = 0.65;
            double maxOut = 0.55;

            double up = MathHelper.clamp(upRaw * kbMult, 0.0, maxUp);
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
                w.spawnParticles(ParticleTypes.CRIT,
                        t.getX(), t.getY() + t.getHeight() * 0.55, t.getZ(),
                        2, 0.12, 0.12, 0.12, 0.0);
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
        boolean casterGrounded = true;

        doGroundSlam(player, w, pos, forward, slamPos, casterGrounded);

        int wallMaxBreak = 14;
        float wallMaxHardness = 3.5f;
        int broken = 0;

        BlockPos hitPos = wallHit.getBlockPos();
        Vec3d n = Vec3d.of(wallHit.getSide().getVector());

        int halfW = 1;
        int halfH = 1;
        int depth = 2;

        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= wallMaxBreak) break;

                    BlockPos p;
                    if (xWall) {
                        p = hitPos.add((int)(-n.x * d), y, xz);
                    } else {
                        p = hitPos.add(xz, y, (int)(-n.z * d));
                    }

                    BlockState s = w.getBlockState(p);
                    if (s.isAir()) continue;
                    if (w.getBlockEntity(p) != null) continue;

                    float hardness = s.getHardness(w, p);
                    if (hardness < 0) continue;
                    if (hardness > wallMaxHardness) continue;

                    if (w.random.nextFloat() > 0.75f) continue;

                    if (w.breakBlock(p, true, player)) {
                        broken++;
                    }
                }
            }
        }
    }

    private static BlockHitResult findWallSlamHit(ServerWorld w, ServerPlayerEntity player, Vec3d forward) {
        double maxDist = 2.2;

        Vec3d start = player.getEyePos();
        Vec3d end = start.add(forward.normalize().multiply(maxDist));

        BlockHitResult bhr = w.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (bhr.getType() != HitResult.Type.BLOCK) return null;

        BlockState s = w.getBlockState(bhr.getBlockPos());
        if (s.isAir()) return null;

        return bhr;
    }

    private static void applySlamDrag(ServerPlayerEntity player) {
        Vec3d v = player.getVelocity();

        double newY;
        if (v.y > 0.0) {
            newY = -0.12;
        } else {
            newY = Math.min(v.y, -0.12);
        }

        player.setVelocity(v.x, newY, v.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static boolean isNearGround(ServerPlayerEntity player, ServerWorld w) {
        final double leeway = 1.60;

        if (player.isOnGround()) return true;

        Vec3d start = player.getPos().add(0.0, 0.05, 0.0);
        Vec3d end   = start.add(0.0, -leeway, 0.0);

        BlockHitResult hr = w.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hr.getType() != HitResult.Type.BLOCK) return false;

        BlockState s = w.getBlockState(hr.getBlockPos());
        return !s.isAir();
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

        w.playSound(null, player.getBlockPos(),
                ModSounds.LUNGESTART,
                player.getSoundCategory(),
                1.2f, 1.0f);

        w.playSound(null, player.getBlockPos(),
                ModSounds.BULLRUSH,
                player.getSoundCategory(),
                1.5f, 1.0f);

        enableRushStepUp(player, true);

        player.swingHand(Hand.MAIN_HAND, true);
    }

    private static void tickBullrush(ServerPlayerEntity player, StrengthState state) {
        // If we just ended, revert step-up.
        if (state.rushTicks <= 0) {
            enableRushStepUp(player, false);
            return;
        }

        // CANCEL
        if (state.rushCancelLock <= 0 && player.isSneaking()) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);

            ServerWorld w = player.getServerWorld();
            w.playSound(null, player.getBlockPos(), ModSounds.BULLRUSH, player.getSoundCategory(), 0.6f, 0.9f);
            w.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.25, 0.10, 0.25, 0.02);
            return;
        }

        Vec3d dir = state.rushDir;
        if (dir == null) dir = player.getRotationVec(1.0f).normalize();

        // STEERING WINDOW
        int elapsed = Math.max(0, RUSH_TICKS - state.rushTicks);

        if (elapsed < RUSH_STEER_TICKS) {
            double maxTurnDeg = 14.0;
            double maxTurn = Math.toRadians(maxTurnDeg);

            Vec3d look = player.getRotationVec(1.0f);
            Vec3d a = new Vec3d(dir.x, 0.0, dir.z);
            Vec3d b = new Vec3d(look.x, 0.0, look.z);

            if (a.lengthSquared() > 1.0e-6 && b.lengthSquared() > 1.0e-6) {
                a = a.normalize();
                b = b.normalize();

                double dot = MathHelper.clamp(a.x * b.x + a.z * b.z, -1.0, 1.0);
                double ang = Math.acos(dot);

                if (ang > 1.0e-6) {
                    double t = Math.min(1.0, maxTurn / ang);

                    Vec3d blended = new Vec3d(
                            MathHelper.lerp(t, a.x, b.x),
                            0.0,
                            MathHelper.lerp(t, a.z, b.z)
                    );

                    if (blended.lengthSquared() > 1.0e-6) {
                        dir = blended.normalize();
                        state.rushDir = dir; // lock it in once window ends
                    }
                }
            }
        }

        // WALL CHECKS
        BlockHitResult wallHit = findRushWallHit(player.getServerWorld(), player, dir);
        if (wallHit != null) {
            state.rushTicks = 0;
            enableRushStepUp(player, false);

            doRushCrash(player, player.getServerWorld(), wallHit);
            return;
        }

        // MOVEMENT
        Vec3d vel = player.getVelocity();

        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) horiz = new Vec3d(0, 0, 1);

        boolean stepped = tryRushStepUp(player, player.getServerWorld(), horiz);

        Vec3d push = horiz.normalize().multiply(RUSH_SPEED);

        vel = player.getVelocity();

        double newY;
        if (stepped) {
            newY = Math.max(vel.y, 0.52);
        } else {
            newY = MathHelper.clamp(vel.y, -0.35, 0.35);
        }

        player.setVelocity(push.x, newY, push.z);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

        // fx
        ServerWorld w = player.getServerWorld();

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
        boolean canHit = state.rushHitLock <= 0;
        if (canHit) {
            Vec3d p = player.getPos().add(horiz.normalize().multiply(0.9));
            Box hitBox = new Box(p, p).expand(RUSH_HIT_RADIUS, 1.2, RUSH_HIT_RADIUS);

            List<LivingEntity> hits = w.getEntitiesByClass(
                    LivingEntity.class,
                    hitBox,
                    e -> e.isAlive() && e != player
            );

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

                state.rushHitLock = 6;
            }
        }
    }

    private static BlockHitResult findRushWallHit(ServerWorld w, ServerPlayerEntity player, Vec3d dir) {
        Vec3d horiz = new Vec3d(dir.x, 0.0, dir.z);
        if (horiz.lengthSquared() < 1.0e-6) return null;

        Vec3d fwd = horiz.normalize().multiply(RUSH_WALL_CHECK_DIST);

        Vec3d lowStart = player.getPos().add(0.0, 0.20, 0.0);
        Vec3d lowEnd   = lowStart.add(fwd);

        HitResult low = w.raycast(new RaycastContext(
                lowStart, lowEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        Vec3d highStart = player.getPos().add(0.0, player.getHeight() * 0.90, 0.0);
        Vec3d highEnd   = highStart.add(fwd);

        BlockHitResult high = w.raycast(new RaycastContext(
                highStart, highEnd,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        boolean lowBlock  = (low.getType()  == HitResult.Type.BLOCK);
        boolean highBlock = (high.getType() == HitResult.Type.BLOCK);

        if (lowBlock && !highBlock) return null;

        if (!highBlock) return null;
        return high;
    }

    private static void doRushCrash(ServerPlayerEntity player, ServerWorld w, BlockHitResult wallHit) {
        BlockPos hitPos = wallHit.getBlockPos();

        player.setVelocity(0, player.getVelocity().y * 0.25, 0);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));

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

        int broken = 0;

        Vec3d n = Vec3d.of(wallHit.getSide().getVector());

        int halfW = 1;
        int halfH = 1;
        int depth = 2;

        boolean xWall = Math.abs(n.x) > 0.5;

        for (int d = 0; d < depth; d++) {
            for (int y = -halfH; y <= halfH; y++) {
                for (int xz = -halfW; xz <= halfW; xz++) {
                    if (broken >= RUSH_WALL_MAX_BLOCKS) break;

                    BlockPos p;
                    if (xWall) {
                        p = hitPos.add((int) (-n.x * d), y, xz);
                    } else {
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

        player.damage(ModDamageTypes.rushCollision(w, player), RUSH_CRASH_SELF_DAMAGE);

        Vec3d center = impact;
        Box box = new Box(center, center).expand(RUSH_CRASH_AOE_RADIUS, 2.0, RUSH_CRASH_AOE_RADIUS);

        List<LivingEntity> victims = w.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != player
        );

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

        w.spawnParticles(ParticleTypes.CLOUD,
                impact.x, impact.y, impact.z,
                120, 1.0, 0.35, 1.0, 0.12);
        w.spawnParticles(ParticleTypes.CRIT,
                impact.x, impact.y, impact.z,
                50, 0.8, 0.25, 0.8, 0.18);
    }

    private static void enableRushStepUp(ServerPlayerEntity player, boolean enable) {
        player.setStepHeight(enable ? 1.05f : 0.6f);
    }

    private static boolean tryRushStepUp(ServerPlayerEntity player, ServerWorld w, Vec3d horizDir) {
        if (!player.isOnGround() && player.getVelocity().y > 0.12) return false;

        Vec3d fwd = horizDir.normalize();
        if (fwd.lengthSquared() < 1.0e-6) return false;

        Vec3d feet = player.getPos().add(0.0, 0.05, 0.0);
        Vec3d ahead = feet.add(fwd.multiply(0.55));

        BlockPos front = BlockPos.ofFloored(ahead);
        BlockPos frontUp = front.up();

        BlockState sFront = w.getBlockState(front);
        boolean frontBlocks = !sFront.getCollisionShape(w, front).isEmpty();
        if (!frontBlocks) return false;

        BlockState sFrontUp = w.getBlockState(frontUp);
        boolean upBlocks = !sFrontUp.getCollisionShape(w, frontUp).isEmpty();
        if (upBlocks) return false;

        if (player.getVelocity().y > 0.30) return false;

        Vec3d v = player.getVelocity();
        player.setVelocity(v.x, Math.max(v.y, 0.52), v.z);
        player.velocityModified = true;

        return true;
    }

    /* ============================================================
       ULT
       ============================================================ */

    private static final int RAGE_FX_RADIUS = 10;
    private static final int RAGE_AURA_INTERVAL = 2;
    private static final int RAGE_HB_INTERVAL = 14;

    @Override
    public void activateUltimate(ServerPlayerEntity player) {
        StrengthState state = getState(player);
        state.rageTicks = RAGE_TICKS;
        state.rageFxBurst = 2;
        state.rageAuraStep = 0;
        state.rageHbStep = 0;

        ServerWorld w = player.getServerWorld();

        w.playSound(null, player.getBlockPos(),
                ModSounds.RAGE,
                player.getSoundCategory(), 0.8f, 1.00f);

        PowerManager.clearAbilityCooldown(player, AbilityTypes.PRIMARY);
        PowerManager.clearAbilityCooldown(player, AbilityTypes.SECONDARY);
    }

    private static void tickRageBuffs(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10, RAGE_STR_AMP, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 10, RAGE_RES_AMP, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, RAGE_SPEED_AMP, true, false));
    }

    public static boolean isRaging(ServerPlayerEntity player) {
        return ACTIVE_STATES.getOrDefault(player.getUuid(), new StrengthState()).rageTicks > 0;
    }

    private static final DustParticleEffect RAGE_RED_DUST =
            new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.0f), 1.35f);

    private static void tickRageFX(ServerPlayerEntity player, StrengthState state) {
        ServerWorld w = player.getServerWorld();

        if (state.rageFxBurst >= 0) {
            spawnRagePulse(w, player);
            CameraShake.shakeNearby(player, RAGE_FX_RADIUS, 18, 1.85f);
            state.rageFxBurst = -1;
        }

        if (state.rageAuraStep <= 0) {
            state.rageAuraStep = RAGE_AURA_INTERVAL;

            w.spawnParticles(
                    RAGE_RED_DUST,
                    player.getX(), player.getY() + 0.95, player.getZ(),
                    1,
                    0.55, 0.55, 0.55,
                    0.02
            );
        } else {
            state.rageAuraStep--;
        }

        if (state.rageHbStep <= 0) {
            state.rageHbStep = RAGE_HB_INTERVAL;

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
        } else {
            state.rageHbStep--;
        }
    }

    private static void spawnRagePulse(ServerWorld w, ServerPlayerEntity player) {
        double cx = player.getX();
        double cy = player.getY() + 1.0;
        double cz = player.getZ();

        w.spawnParticles(
                RAGE_RED_DUST,
                cx, cy, cz,
                28,
                0.12, 0.18, 0.12,
                0.06
        );

        int points = 24;
        double radius = 3.35;
        double speed = 0.45;
        double y = player.getY() + 0.15;

        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2.0) * (i / (double) points);
            double dx = Math.cos(a);
            double dz = Math.sin(a);

            w.spawnParticles(
                    RAGE_RED_DUST,
                    cx + dx * radius, y, cz + dz * radius,
                    10,
                    dx * speed, 0.03, dz * speed,
                    1.0
            );
        }

        w.spawnParticles(
                ParticleTypes.EXPLOSION_EMITTER,
                cx, player.getY() + 0.35, cz,
                1,
                0, 0, 0,
                0
        );

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
    @Override public String getPrimaryName() { return "Seismic Slam"; }
    @Override public String getSecondaryName() { return "Titan Charge"; }
    @Override public String getUltimateName() { return "Primal Rage"; }

    @Override
    public long getPrimaryCooldownMs() { return 12_500; }

    @Override
    public long getSecondaryCooldownMs() {
        return 18_500;
    }

    @Override
    public long getUltimateCooldownMs() {
        return 280_000;
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