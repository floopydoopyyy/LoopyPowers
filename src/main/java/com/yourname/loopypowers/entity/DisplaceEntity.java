package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.power.DimensionalPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
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

            // core
            DustParticleEffect core = new DustParticleEffect(
                    new Vector3f(0.3f, 0.6f, 1.0f), // unified blue
                    0.85f
            );

            // tighter trail
            Vec3d back = dir.multiply(-0.2);

            sw.spawnParticles(core,
                    this.getX() + back.x,
                    this.getY() + back.y,
                    this.getZ() + back.z,
                    5,
                    0.03, 0.03, 0.03,
                    0.002
            );

            // forward
            if (speed > 0.01) {
                Vec3d ahead = dir.multiply(0.35);

                sw.spawnParticles(core,
                        this.getX() + ahead.x,
                        this.getY() + ahead.y,
                        this.getZ() + ahead.z,
                        2,
                        0.01, 0.01, 0.01,
                        0.0
                );
            }

            // zap stuff
            if (sw.random.nextFloat() < 0.78f) {

                // random outward direction
                Vec3d baseDir = new Vec3d(
                        sw.random.nextGaussian(),
                        sw.random.nextGaussian() * 0.6,
                        sw.random.nextGaussian()
                ).normalize();

                Vec3d current = this.getPos();

                // create segmented zigzag
                for (int i = 0; i < 4; i++) {

                    // jitter it
                    Vec3d jitter = new Vec3d(
                            sw.random.nextGaussian() * 0.25,
                            sw.random.nextGaussian() * 0.25,
                            sw.random.nextGaussian() * 0.25
                    );

                    Vec3d stepDir = baseDir.add(jitter).normalize().multiply(0.25);

                    current = current.add(stepDir);

                    sw.spawnParticles(
                            core,
                            current.x,
                            current.y,
                            current.z,
                            1,
                            0, 0, 0,
                            0
                    );
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

            // APPLY DISPLACEMENT
            DimensionalPower.removeTagPrefix(t, "int_displaced_");
            t.getCommandTags().add("int_displaced_" + 60);

            // hit effect
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.PORTAL,
                        t.getX(), t.getBodyY(0.5), t.getZ(),
                        10,
                        0.3, 0.5, 0.3,
                        0.05
                );
            }
        }
    }

    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}