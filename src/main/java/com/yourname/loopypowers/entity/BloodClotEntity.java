package com.yourname.loopypowers.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
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
    private float hitDamage = 2.0f; // // damage on hit

    private static final DustParticleEffect BLOOD_DUST =
            new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.9f); // particle effects

    public BloodClotEntity(EntityType<? extends BloodClotEntity> type, World world) {
        super(type, world);
    }

    public void setTuning(int maxLife, int slowTicks, int weakTicks, float hitDamage) {
        this.maxLife = maxLife;
        this.slowTicks = slowTicks;
        this.weakTicks = weakTicks;
        this.hitDamage = hitDamage;
    }

    @Override
    public void tick() {
        super.tick();

        // particles
        if (!this.getWorld().isClient() && this.getWorld() instanceof ServerWorld sw) {
            Vec3d p = this.getPos();

            // main particle
            sw.spawnParticles(BLOOD_DUST,
                    p.x, p.y, p.z,
                    10,              // count (more = thicker)
                    0.10, 0.10, 0.10, // spread (bigger = chunkier blob)
                    0.0
            );

            // extra core in middle
            sw.spawnParticles(BLOOD_DUST,
                    p.x, p.y, p.z,
                    4,
                    0.03, 0.03, 0.03,
                    0.0
            );

            // outer effects
            if (this.random.nextFloat() < 0.35f) {
                sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR,
                        p.x, p.y, p.z,
                        2,
                        0.08, 0.08, 0.08,
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

        Vec3d start = this.getPos();
        Vec3d v = this.getVelocity();
        Vec3d end = start.add(v);

        // hits entity
        EntityHitResult ehr = ProjectileUtil.getEntityCollision(
                this.getWorld(),
                this,
                start,
                end,
                this.getBoundingBox().stretch(v).expand(0.3),
                this::canHitEntity
        );

        if (ehr != null) {
            onEntityHit(ehr);
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

        // move and gravity
        this.setVelocity(this.getVelocity().add(0.0, -0.03, 0.0));
        this.move(net.minecraft.entity.MovementType.SELF, v);
        // drag
        this.setVelocity(this.getVelocity().multiply(0.985));
    }

    private boolean canHitEntity(Entity e) {
        if (!(e instanceof LivingEntity)) return false;
        if (!e.isAlive() || e.isSpectator()) return false;

        // don't hit owner
        Entity owner = this.getOwner();
        if (owner != null && e == owner) return false;

        return true;
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

        // apply debuffs
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, slowTicks, 1, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, weakTicks, 0, true, true));

        // hit damage
        if (hitDamage > 0.0f) {
            DamageSource src = (owner != null)
                    ? owner.getDamageSources().magic()
                    : target.getDamageSources().magic();

            target.damage(src, hitDamage);
        }

        // extra bleed on hit
        if (owner != null && !this.getWorld().isClient()) {
            BloodPower.applyBleedFromProjectile(owner, target, 1.5f, 20 * 3, 17);
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
    protected void initDataTracker() {}

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
