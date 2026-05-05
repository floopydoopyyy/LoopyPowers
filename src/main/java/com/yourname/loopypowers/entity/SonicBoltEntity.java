package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.power.SoundPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.BlockPositionSource;
import java.util.List;

public class SonicBoltEntity extends net.minecraft.entity.Entity {

    /* ============================================================
       TAGS / CONSTANTS
       ============================================================ */

    private ServerPlayerEntity owner;
    private int life;
    private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();

    // ── Entity tuning ────────────────────────────────────────────────────────

    // how long before it dies
    private static final int    MAX_LIFE              = 60;
    // how far ahead to spawn particles
    private static final double PARTICLE_FORWARD_DIST = 11.0;
    private static final int    VIBRATION_TICKS       = 14;
    // hit detection size
    private static final double SWEEP_RADIUS          = 0.75;
    private static final double HITBOX_INFLATION      = 0.25;
    // push strength
    private static final float  KNOCKBACK_STRENGTH    = 1.0f;
    // direct hit damage
    private static final float  BASE_DAMAGE           = 6.5f;

    /* ============================================================
       LIFECYCLE
       ============================================================ */

    public SonicBoltEntity(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayerEntity owner) {
        this.owner = owner;
    }
    // getter
    public ServerPlayerEntity getOwner() {
        return this.owner;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) return;

        life++;
        if (life > MAX_LIFE) {
            this.discard();
            return;
        }

        Vec3d prev = this.getPos();
        Vec3d vel = this.getVelocity();
        Vec3d next = prev.add(vel);

        this.setPos(next.x, next.y, next.z);

        if (this.getWorld() instanceof ServerWorld sw) {
            spawnTravelParticles(sw);
        }

        handleCollisions(prev, next);
    }

    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {}

    /* ============================================================
       HELPERS
       ============================================================ */

    private void spawnTravelParticles(ServerWorld sw) {
        Vec3d v = this.getVelocity();
        if (v.lengthSquared() > 1.0e-6) {
            Vec3d forward = v.normalize().multiply(PARTICLE_FORWARD_DIST);
            BlockPos dst = BlockPos.ofFloored(this.getPos().add(forward));

            sw.spawnParticles(new VibrationParticleEffect(new BlockPositionSource(dst), VIBRATION_TICKS),
                    this.getX(), this.getY() + 0.05, this.getZ(), 6, 0, 0, 0, 0);

            sw.spawnParticles(ParticleTypes.SCULK_CHARGE_POP,
                    this.getX(), this.getY() + 0.05, this.getZ(), 4, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void handleCollisions(Vec3d prev, Vec3d next) {
        Box sweep = new Box(prev, next).expand(SWEEP_RADIUS);
        List<LivingEntity> targets = this.getWorld().getEntitiesByClass(LivingEntity.class, sweep,
                e -> e.isAlive() && e != owner);

        for (LivingEntity t : targets) {
            if (hit.contains(t.getUuid())) continue;

            var opt = t.getBoundingBox().expand(HITBOX_INFLATION).raycast(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUuid());

            // do the hit/stun first so the velocity reset doesn't cancel our bolt knockback
            SoundPower.applyAbilityHit(owner, t, BASE_DAMAGE, true);

            Vec3d dir = next.subtract(prev).multiply(1, 0, 1);
            if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
            dir = dir.normalize();

            t.addVelocity(dir.x * KNOCKBACK_STRENGTH, 0.15, dir.z * KNOCKBACK_STRENGTH);
            t.velocityModified = true;

            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SONIC_BOOM, t.getX(), t.getY() + t.getHeight() * 0.6, t.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }
}