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

    public PowerFireballEntity(net.minecraft.entity.EntityType<? extends FireballEntity> type, World world) {
        super(type, world);
    }

    public PowerFireballEntity(
            net.minecraft.entity.EntityType<? extends FireballEntity> type,
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

        // disable drift
        this.powerX = 0.0;
        this.powerY = 0.0;
        this.powerZ = 0.0;
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

            // Explode in ANY
            FluidState fs = this.getWorld().getFluidState(this.getBlockPos());
            if (!fs.isEmpty()) {
                explodeInWater();
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
        // what the fuck is a client particle im k
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
        return 1.0f; // no slowdown i think
    }
    // all custom parameters
    public void setDamageValues(float directHitDamage, float extraExplosionDamage) {
        this.directHitDamage = directHitDamage;
        this.extraExplosionDamage = extraExplosionDamage;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);

        Entity hit = entityHitResult.getEntity();
        Entity owner = this.getOwner();

        // death message stuff
        DamageSource src = this.getDamageSources().fireball(this, owner);

        // direct hit damage
        hit.damage(src, this.directHitDamage);

        // Camera shake if it would FUCKING WIORK
        if (!this.getWorld().isClient) {
            float strength = 0.8f; // tweak
            double radius = 12.0;
            int ticks = 8;

            // Use the hit entity if it's a player, otherwise use the owner if it's a player
            if (hit instanceof ServerPlayerEntity hitPlayer) {
                CameraShake.shakeNearby(hitPlayer, radius, ticks, strength);
            } else if (owner instanceof ServerPlayerEntity ownerPlayer) {
                CameraShake.shakeNearby(ownerPlayer, radius, ticks, strength);
            }
        }

        // IGNITE HIT
        if (hit instanceof LivingEntity living) {
            living.setOnFireFor(4);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (this.getWorld().isClient) {
            super.onCollision(hitResult);
            return;
        }

        // explosion power stiff
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            Entity owner = this.getOwner();

            DamageSource explosionSource = this.getDamageSources().explosion(this, owner);

            serverWorld.createExplosion(
                    this,                 // entity that exploded
                    explosionSource,      // damage source
                    null,                 // idfk
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    (float) myExplosionPower,
                    true,                 // creates fire
                    World.ExplosionSourceType.MOB
            );
        }

        // Extra AOE
        if (extraExplosionDamage > 0.0f && this.getWorld() instanceof ServerWorld serverWorld) {
            float radius = Math.max(2.0f, extraExplosionDamage * 0.35f);
            Box box = new Box(this.getPos(), this.getPos()).expand(radius);

            Entity owner = this.getOwner();
            DamageSource src = this.getDamageSources().explosion(this, owner);

            for (Entity e : serverWorld.getOtherEntities(this, box)) {
                if (!(e instanceof LivingEntity living)) continue;
                if (owner != null && e == owner) continue;

                double dist = e.squaredDistanceTo(this);
                double max = radius * radius;
                float scale = (float) Math.max(0.0, 1.0 - (dist / max));

                living.damage(src, extraExplosionDamage * scale);
            }
        }

        // Remove projectile after impact
        this.discard();
    }

    private void explodeInWater() { // only purpose of this is to stop lag machines

        if (!(this.getWorld() instanceof ServerWorld serverWorld)) return;

        Entity owner = this.getOwner();

        DamageSource explosionSource =
                this.getDamageSources().explosion(this, owner);

        serverWorld.createExplosion(
                this,
                explosionSource,
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                (float) myExplosionPower,
                false, // usually better: no fire underwater
                World.ExplosionSourceType.MOB
        );

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