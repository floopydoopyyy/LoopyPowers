package com.yourname.loopypowers.entity;
import com.yourname.loopypowers.power.SoundPower;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.event.EntityPositionSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.event.BlockPositionSource;
import java.util.List;

public class SonicBoltEntity extends net.minecraft.entity.Entity {
    private ServerPlayerEntity owner;
    private int life;
    private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();

    public SonicBoltEntity(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
        this.noClip = true;     // pierce blocks
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
        if (life > 60) { // how long it lives
            this.discard();
            return;
        }

        Vec3d prev = this.getPos();
        Vec3d vel = this.getVelocity();
        Vec3d next = prev.add(vel);

        // move before raycast
        this.setPos(next.x, next.y, next.z);

        // particles
        if (this.getWorld() instanceof ServerWorld sw) {

            Vec3d v = this.getVelocity();
            if (v.lengthSquared() > 1.0e-6) {

                Vec3d forward = v.normalize().multiply(11.0); // speed of projectile
                BlockPos dst = BlockPos.ofFloored(this.getPos().add(forward));

                // travel time
                VibrationParticleEffect vib = new VibrationParticleEffect(
                        new BlockPositionSource(dst),
                        14 //
                );

                // Spawn every tick
                sw.spawnParticles(
                        vib,
                        this.getX(),
                        this.getY() + 0.05,
                        this.getZ(),
                        6,
                        0.0, 0.0, 0.0,
                        0.0
                );
                     // i didnt think that particle would be so hard to see so I added another tracer
                    sw.spawnParticles(
                            ParticleTypes.SCULK_CHARGE_POP,
                            this.getX(),
                            this.getY() + 0.05, //
                            this.getZ(),
                            4,
                            0.02, 0.02, 0.02,
                            0.0
                    );

            }
        }

        // hit entities it touches
        Box sweep = new Box(prev, next).expand(0.75);

        List<LivingEntity> targets = this.getWorld().getEntitiesByClass(
                LivingEntity.class,
                sweep,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity t : targets) {
            if (hit.contains(t.getUuid())) continue;

            if (this.getWorld() instanceof ServerWorld sw) { // this is how it is displayed in world
                // 8 ticks travel time
                VibrationParticleEffect vib = new VibrationParticleEffect(
                        new EntityPositionSource(t, t.getHeight() * 1.1f),
                        20
                );

                sw.spawnParticles(
                        vib,
                        this.getX(), this.getY() + 0.1, this.getZ(),
                        1,
                        0.0, 0.0, 0.0,
                        0.0
                );
            }

            // something about the hitbox
            var opt = t.getBoundingBox().expand(0.25).raycast(prev, next);
            if (opt.isEmpty()) continue;

            hit.add(t.getUuid());

            // apply base effects
            //float dmg = 3.0f; handled by burst method in soundpower.
            float kb  = 1.0f;

            Vec3d dir = next.subtract(prev);
            dir = new Vec3d(dir.x, 0.0, dir.z);
            if (dir.lengthSquared() < 1.0e-6) dir = new Vec3d(0, 0, 1);
            dir = dir.normalize();

            t.addVelocity(dir.x * kb, 0.15, dir.z * kb);
            t.velocityModified = true;

            SoundPower.applyAbilityHit(owner, t, 3.0f, true); // allowburst (the last parameter) is what allows bonus damage using passive

            // yay particles
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SONIC_BOOM,
                        t.getX(), t.getY() + t.getHeight() * 0.6, t.getZ(),
                        1, 0, 0, 0, 0);
            }
        }
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {}
}