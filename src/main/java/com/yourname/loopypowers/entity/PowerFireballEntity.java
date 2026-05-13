package com.yourname.loopypowers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import com.yourname.loopypowers.network.CameraShake;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

public class PowerFireballEntity extends FireballEntity {
    private float directHitDamage = 6.0f;
    private float extraExplosionDamage = 0.0f;
    private Vec3d lastTrailPos = null;
    private int myExplosionPower = 1;
    private Vec3d constantVelocity = Vec3d.ZERO;
    private Vec3d lastServerPos = null;
    private int stuckTicks = 0;

    public PowerFireballEntity(net.minecraft.entity.EntityType<PowerFireballEntity> type, net.minecraft.world.World world) {
        super(type, world);
    }

    public PowerFireballEntity(
            net.minecraft.entity.EntityType<PowerFireballEntity> type,
            World world,
            LivingEntity owner,
            double vx, double vy, double vz,
            int explosionPower
    ) {
        super(type, world);

        this.setOwner(owner);
        this.myExplosionPower = explosionPower;

        // no acceleration
        this.setVelocity(vx, vy, vz);
        this.constantVelocity = new Vec3d(vx, vy, vz);
    }

    @Override
    public void tick() {
        super.tick();

        // server should make it move
        if (!this.getWorld().isClient) {

            // Keep constant velocity
            this.setVelocity(constantVelocity);

            // if it is detected to be stuck for too many ticks force it to explode
            Vec3d p = this.getPos();
            if (lastServerPos != null && p.squaredDistanceTo(lastServerPos) < 1.0e-8) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }
            lastServerPos = p;

            if (stuckTicks >= 2) { // 2 ticks stuck then boom
                this.onCollision(BlockHitResult.createMissed(
                        this.getPos(),
                        this.getHorizontalFacing(),
                        this.getBlockPos()
                ));
                return;
            }

            // Explode in ANY fluid to prevent lag machines
            FluidState fs = this.getWorld().getFluidState(this.getBlockPos());
            if (!fs.isEmpty()) {
                this.onCollision(BlockHitResult.createMissed(
                        this.getPos(),
                        this.getHorizontalFacing(),
                        this.getBlockPos()
                ));
                return;
            }

            return; // server doesn't do the client particle trail
        }

        // client side particles
        var world = this.getWorld();
        Vec3d now = this.getPos();

        if (lastTrailPos == null) {
            lastTrailPos = now;
            return;
        }

        Vec3d delta = now.subtract(lastTrailPos);
        int steps = 6;

        for (int s = 0; s <= steps; s++) {
            double t = s / (double) steps;

            double px = lastTrailPos.x + delta.x * t;
            double py = lastTrailPos.y + delta.y * t;
            double pz = lastTrailPos.z + delta.z * t;

            double ox = (this.random.nextDouble() - 0.5) * 0.08;
            double oy = (this.random.nextDouble() - 0.5) * 0.08;
            double oz = (this.random.nextDouble() - 0.5) * 0.08;

            world.addParticle(ParticleTypes.FLAME, px + ox, py + oy, pz + oz, 0, 0, 0);
            world.addParticle(ParticleTypes.SMOKE, px + ox, py + oy, pz + oz, 0, 0, 0);
            world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, px + ox, py + oy, pz + oz, 0, 0, 0);
        }

        lastTrailPos = now;
    }

    @Override
    protected float getDrag() {
        return 1.0f; // no slowdown
    }

    // all custom parameters
    public void setDamageValues(float directHitDamage, float extraExplosionDamage) {
        this.directHitDamage = directHitDamage;
        this.extraExplosionDamage = extraExplosionDamage;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (this.getWorld().isClient) return;

        Entity directTarget = null;
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            directTarget = ((EntityHitResult) hitResult).getEntity();
        }

        if (this.getWorld() instanceof ServerWorld serverWorld) {
            Entity owner = this.getOwner();
            DamageSource directSrc = this.getDamageSources().fireball(this, owner);
            DamageSource splashSrc = this.getDamageSources().explosion(this, owner);

            // 1. Create the Vanilla Explosion (For visual effects and block breaking)
            serverWorld.createExplosion(
                    this,
                    splashSrc,
                    null,
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (float) myExplosionPower,
                    true,
                    World.ExplosionSourceType.MOB
            );

            // damage calc (calc is short for calculation BTW)
            float radius = Math.max(3.0f, extraExplosionDamage * 0.4f);
            Box box = new Box(this.getPos(), this.getPos()).expand(radius);

            for (Entity e : serverWorld.getOtherEntities(this, box)) {
                if (!(e instanceof LivingEntity living)) continue;
                if (owner != null && e == owner) continue;

                // Strip vanilla i-frames so my damage overrides the vanilla explosion damage
                living.timeUntilRegen = 0;

                if (e == directTarget) {
                    // direct hits to splash + collision damage
                    living.damage(directSrc, directHitDamage + extraExplosionDamage);
                    living.setOnFireFor(5);

                    if (owner instanceof ServerPlayerEntity ownerPlayer) {
                        CameraShake.shakeNearby(ownerPlayer, 12.0, 8, 0.8f);
                    }
                } else {
                    // indirect hits take scaled splash damage based on distance
                    double dist = e.getPos().distanceTo(this.getPos());
                    double falloff = 1.0 - (dist / radius);

                    if (falloff > 0) {
                        living.damage(splashSrc, (float) (extraExplosionDamage * falloff));
                        living.setOnFireFor(3);
                    }
                }
            }
        }

        this.discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("DirectHitDamage", directHitDamage);
        nbt.putFloat("ExtraExplosionDamage", extraExplosionDamage);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("DirectHitDamage")) directHitDamage = nbt.getFloat("DirectHitDamage");
        if (nbt.contains("ExtraExplosionDamage")) extraExplosionDamage = nbt.getFloat("ExtraExplosionDamage");
    }
}