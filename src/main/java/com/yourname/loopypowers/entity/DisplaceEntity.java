package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.effect.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.List;
import net.minecraft.world.World;
import net.minecraft.particle.DustParticleEffect;
import org.joml.Vector3f;
import net.minecraft.nbt.NbtCompound;

public class DisplaceEntity extends Entity {

    private ServerPlayerEntity owner;
    private int life;
    private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();

    public DisplaceEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayerEntity owner) {
        this.owner = owner;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) return;

        life++;
        if (life > 70) {
            this.discard();
            return;
        }

        Vec3d prev = this.getPos();
        Vec3d next = prev.add(this.getVelocity());

        this.setPos(next.x, next.y, next.z);

        // particles
        if (this.getWorld() instanceof ServerWorld sw) {

            Vec3d vel = this.getVelocity();
            double speed = vel.length();
            Vec3d dir = speed > 1.0e-6 ? vel.normalize() : new Vec3d(0, 0, 1);

            // colours (sizes scaled down for a tighter look)
            DustParticleEffect darkBlue = new DustParticleEffect(new Vector3f(0.05f, 0.1f, 0.4f), 0.8f);
            DustParticleEffect midBlue = new DustParticleEffect(new Vector3f(0.2f, 0.4f, 1.0f), 0.6f);
            DustParticleEffect brightBlue = new DustParticleEffect(new Vector3f(0.6f, 0.8f, 1.0f), 0.4f);

            // core (reduced count and spread)
            sw.spawnParticles(brightBlue, this.getX(), this.getY(), this.getZ(), 2, 0.02, 0.02, 0.02, 0.01);
            sw.spawnParticles(midBlue, this.getX(), this.getY(), this.getZ(), 3, 0.05, 0.05, 0.05, 0.02);

            int zaps = 1 + sw.random.nextInt(2); // 1 to 2 distinct lightning arcs per tick

            for (int z = 0; z < zaps; z++) {
                // arc slightly off-centre
                Vec3d startPos = this.getPos().add(
                        (sw.random.nextDouble() - 0.5) * 0.1,
                        (sw.random.nextDouble() - 0.5) * 0.1,
                        (sw.random.nextDouble() - 0.5) * 0.1
                );

                //  random direction
                Vec3d baseDir = new Vec3d(
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian()
                ).normalize();

                // bias opposite to velocity
                if (baseDir.dotProduct(dir) > 0.3) {
                    baseDir = baseDir.multiply(-0.5)
                            .add(sw.random.nextGaussian() * 0.5, sw.random.nextGaussian() * 0.5, sw.random.nextGaussian() * 0.5)
                            .normalize();
                }

                Vec3d current = startPos;
                int segments = 2 + sw.random.nextInt(3); // 2 to 4 segments per zap

                for (int i = 0; i < segments; i++) {
                    // jitter
                    Vec3d stepDir = baseDir.add(
                            sw.random.nextGaussian() * 0.3,
                            sw.random.nextGaussian() * 0.3,
                            sw.random.nextGaussian() * 0.3
                    ).normalize().multiply(0.1 + sw.random.nextDouble() * 0.2); // Shorter step length

                    Vec3d nextStep = current.add(stepDir);

                    // interpolate particles along the segment
                    double dist = current.distanceTo(nextStep);
                    int subSteps = (int) Math.max(2, Math.ceil(dist / 0.15));

                    for (int s = 0; s <= subSteps; s++) {
                        double t = (double) s / subSteps;
                        double px = current.x + (nextStep.x - current.x) * t;
                        double py = current.y + (nextStep.y - current.y) * t;
                        double pz = current.z + (nextStep.z - current.z) * t;

                        // Mix bright and mid blues for the arc
                        DustParticleEffect sparkCol = sw.random.nextFloat() < 0.4f ? brightBlue : midBlue;
                        sw.spawnParticles(sparkCol, px, py, pz, 1, 0, 0, 0, 0);

                        // Throw out occasional dark matter sparks (less often)
                        if (sw.random.nextFloat() < 0.1f) {
                            sw.spawnParticles(darkBlue, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                        }
                    }

                    current = nextStep;
                    baseDir = stepDir.normalize(); // carry momentum to next segment
                }
            }
        }

        // hit
        Box sweep = new Box(prev, next).expand(0.7);

        List<LivingEntity> targets = this.getWorld().getEntitiesByClass(
                LivingEntity.class,
                sweep,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity t : targets) {
            if (hit.contains(t.getUuid())) continue;

            var opt = t.getBoundingBox().expand(0.25).raycast(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUuid());

            // apply effect
            t.addStatusEffect(new StatusEffectInstance(ModEffects.DISPLACED, 120, 0, false, false, false));
        }
    }

    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}