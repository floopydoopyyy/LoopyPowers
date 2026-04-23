package com.yourname.loopypowers.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.particle.ParticleTypes;
import java.util.List;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.MathHelper;

public class ShadowStepEntity extends net.minecraft.entity.Entity {

    private ServerPlayerEntity owner;
    private int life;

    public ShadowStepEntity(EntityType<?> type, World world) {
        super(type, world);
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
        if (life > 40) { // lifespan
            this.discard();
            return;
        }

        Vec3d prev = this.getPos();
        Vec3d vel = this.getVelocity();
        Vec3d next = prev.add(vel);

        ServerWorld world = (ServerWorld) this.getWorld();

        // block collision
        var blockHit = world.raycast(new RaycastContext(
                prev,
                next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.discard();
            return;
        }

        // move
        this.setPos(next.x, next.y, next.z);

        // particles
        float radius = 0.3f;
        float speed = 0.3f;

        // swirl
        for (int i = 0; i < 4; i++) {
            float angle = (life * speed) + (i * (float)Math.PI / 2);

            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            world.spawnParticles(
                    new DustParticleEffect(new Vec3d(0.05, 0.05, 0.05).toVector3f(), 1.0f), // dark grey/black
                    this.getX() + offsetX,
                    this.getY() + 0.1,
                    this.getZ() + offsetZ,
                    1,
                    0, 0, 0,
                    0
            );
        }

        // entity hit
        Box box = new Box(prev, next).expand(0.5);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {

            var hit = target.getBoundingBox().expand(0.3).raycast(prev, next);
            if (hit.isEmpty()) continue;

            // on hit logic:
            // TELEPORT
            teleportBehind(target);

            // damage
            target.damage(
                    world.getDamageSources().playerAttack(owner),
                    6.0f
            );
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, true, false));

            // FX
            world.playSound(null, target.getBlockPos(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                    owner.getSoundCategory(),
                    0.8f, 1.2f);

            world.spawnParticles(
                    ParticleTypes.LARGE_SMOKE,
                    target.getX(),
                    target.getBodyY(0.5),
                    target.getZ(),
                    20,
                    0.4, 0.5, 0.4,
                    0.02
            );

            this.discard();
            return;
        }
    }

    // teleport logic - ideally should be safe
    private void teleportBehind(LivingEntity target) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;

        Vec3d forward = target.getRotationVec(1.0f).normalize();

        // position behind target
        Vec3d behind = target.getPos().subtract(forward.multiply(1.5));

        BlockPos base = BlockPos.ofFloored(behind);

        // find safe spot (check 3 heights)
        for (int y = -1; y <= 1; y++) {
            BlockPos pos = base.up(y);

            if (isSafe(world, pos)) {
                owner.teleport(
                        world,
                        pos.getX() + 0.5,
                        pos.getY(),
                        pos.getZ() + 0.5,
                        owner.getYaw(),
                        owner.getPitch()
                );

                // face target
                faceTarget(owner, target);

                return;
            }
        }

        // fallback: teleport slightly above
        owner.teleport(
                world,
                behind.x,
                behind.y + 1,
                behind.z,
                owner.getYaw(),
                owner.getPitch()
        );

    }

    private boolean isSafe(ServerWorld world, BlockPos pos) {
        return world.getBlockState(pos).isAir()
                && world.getBlockState(pos.up()).isAir();
    }

    private void faceTarget(ServerPlayerEntity player, LivingEntity target) {
        Vec3d playerPos = player.getPos();
        Vec3d targetPos = target.getPos().add(0, target.getHeight() * 0.5, 0);

        Vec3d diff = targetPos.subtract(playerPos);

        double dx = diff.x;
        double dy = diff.y;
        double dz = diff.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        float pitch = (float)(-(Math.atan2(dy, horizontalDist) * (180F / Math.PI)));

        player.setYaw(yaw);
        player.setPitch(pitch);

        // sync with client
        player.networkHandler.requestTeleport(
                player.getX(),
                player.getY(),
                player.getZ(),
                yaw,
                pitch
        );
    }

    @Override protected void initDataTracker() {}
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}
}