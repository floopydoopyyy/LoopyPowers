package com.yourname.loopypowers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker; // ADDED
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import com.yourname.loopypowers.power.BloodPower;

// projectile that shoots when using blood clot ability
// has no model/texture, just uses particles
public class BloodClotEntity extends ProjectileEntity {

    private int life = 0;
    private int maxLife = 60; // makes sure
    private int slowTicks = 70;
    private int weakTicks = 70;
    private float hitDamage = 2.0f; // damage on hit

    private static final DustParticleEffect BLOOD_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.2f);

    public BloodClotEntity(EntityType<? extends BloodClotEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true); // ensures it flies completely straight
    }

    public void setTuning(int maxLife, int slowTicks, int weakTicks, float hitDamage) {
        this.maxLife = maxLife;
        this.slowTicks = slowTicks;
        this.weakTicks = weakTicks;
        this.hitDamage = hitDamage;
    }

    @Override
    public void tick() {
        Vec3d start = this.getPos();
        Vec3d v = this.getVelocity();
        Vec3d end = start.add(v);

        super.tick();

        if (this.isRemoved()) return;

        // particles
        if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
            Vec3d p = this.getPos();

            // main particle
            sw.spawnParticles(BLOOD_DUST,
                    p.x, p.y, p.z,
                    4,
                    0.05, 0.05, 0.05,
                    0.0
            );

            // extra core in middle
            sw.spawnParticles(BLOOD_DUST,
                    p.x, p.y, p.z,
                    2,
                    0.02, 0.02, 0.02,
                    0.0
            );

            // outer effects
            if (this.random.nextFloat() < 0.25f) {
                sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        p.x, p.y, p.z,
                        1,
                        0.05, 0.05, 0.05,
                        0.0
                );
            }
        }

        // lifetime
        life++;
        if (life >= maxLife) {
            splat();
            return;
        }

        Box sweepBox = new Box(start, end).expand(1.2);
        LivingEntity hitTarget = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : this.getWorld().getOtherEntities(this, sweepBox, this::canHitEntity)) {
            double d = start.squaredDistanceTo(e.getPos());
            if (d < closestDist) {
                closestDist = d;
                hitTarget = (LivingEntity) e;
            }
        }

        // if anything was in the box, hit the closest one immediately
        if (hitTarget != null) {
            onEntityHit(new EntityHitResult(hitTarget));
            return;
        }

        // hits block
        HitResult blockHit = this.getWorld().raycast(new net.minecraft.world.RaycastContext(
                start,
                end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                this
        ));

        if (blockHit.getType() == HitResult.Type.BLOCK) {
            splat();
            return;
        }

        this.setPos(end.x, end.y, end.z);
    }

    private boolean canHitEntity(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (!e.isAlive() || e.isSpectator()) return false;

        // don't hit owner
        Entity owner = this.getOwner();
        return owner == null || e != owner;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);

        if (hitResult instanceof EntityHitResult ehr) {
            onEntityHit(ehr);
        } else {
            // block hit
            splat();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult ehr) {

        if (!(ehr.getEntity() instanceof LivingEntity target)) {
            splat();
            return;
        }

        final LivingEntity owner = (this.getOwner() instanceof LivingEntity le) ? le : null;

        // hit damage
        if (hitDamage > 0.0f) {
            DamageSource src = (owner != null)
                    ? owner.getDamageSources().magic()
                    : target.getDamageSources().magic();

            target.damage(src, hitDamage);
        }

        if (owner instanceof ServerPlayerEntity sp && !this.getWorld().isClient()) {
            if (BloodPower.isBleeding(target)) {
                // target is bleeding - pop them
                BloodPower.popBleed(target, sp);
            } else {
                // target is NOT bleeding - apply normal debuffs
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, 2, true, true));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, weakTicks, 1, true, true));
                BloodPower.applyBleedFromProjectile(owner, target, BloodPower.CLOT_BLEED_DAMAGE, BloodPower.CLOT_BLEED_DURATION, BloodPower.CLOT_BLEED_INTERVAL);
            }
        }

        splat();
    }

    private void splat() {
        if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.DAMAGE_INDICATOR,
                    this.getX(), this.getY(), this.getZ(),
                    8, 0.25, 0.20, 0.25, 0.02);
        }
        this.discard();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {} // 1.21.1 FIXED

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        life = nbt.getInt("Life");
        maxLife = nbt.getInt("MaxLife");
        slowTicks = nbt.getInt("SlowTicks");
        weakTicks = nbt.getInt("WeakTicks");
        hitDamage = nbt.getFloat("HitDamage");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", life);
        nbt.putInt("MaxLife", maxLife);
        nbt.putInt("SlowTicks", slowTicks);
        nbt.putInt("WeakTicks", weakTicks);
        nbt.putFloat("HitDamage", hitDamage);
    }
}