package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.power.PsychicPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker; // ADDED
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.List;

public class CompelEntity extends Entity {

    private ServerPlayerEntity owner;
    private int life;

    /* ============================================================
       Particle creation
       ============================================================ */

    private static final DustParticleEffect MAIN_PINK =
            new DustParticleEffect(
                    new Vec3d(0.9, 0.2, 0.6).toVector3f(), // strong pink
                    1.1f
            );

    private static final DustParticleEffect ACCENT_LIGHT =
            new DustParticleEffect(
                    new Vec3d(1.0, 0.6, 0.9).toVector3f(), // soft glow pink
                    0.8f
            );

    private static final DustParticleEffect ACCENT_DARK =
            new DustParticleEffect(
                    new Vec3d(0.5, 0.0, 0.6).toVector3f(), //  magenta
                    0.9f
            );

    // CONSTANTS
    // Lifespan
    static final int MAX_LIFE = 55;

    // Movement
    static final double SPEED = 1.1;
    static final double STEERING = 0.18;

    // Wobble
    static final double WOBBLE_STRENGTH = 0.03;
    static final double WOBBLE_FREQUENCY = 0.3;

    // Targeting
    static final double TARGET_DISTANCE = 40.0;  // further = more vertical control
    static final double DOWNWARD_SOFTENING = 0.85; // gentle - only slightly resists diving

    // Collision
    static final double ENTITY_HIT_EXPAND = 0.3;
    static final double PROJECTILE_BOX_EXPAND = 0.5;

    // Particles
    static final float PARTICLE_RADIUS = 0.12f;
    static final float PARTICLE_LENGTH = 0.4f;
    static final float PARTICLE_SPEED = 0.6f;
    static final int PARTICLE_SEGMENTS = 3;

    /* ============================================================
       CONSTRUCTOR
       ============================================================ */

    public CompelEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public void setOwner(ServerPlayerEntity owner) {
        this.owner = owner;
    }

    // getter
    public ServerPlayerEntity getOwner() {
        return this.owner;
    }

    /* ============================================================
       TICK
       ============================================================ */

    @Override
    public void tick() {
        // delete if owner gone
        if (owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        super.tick();

        if (this.getWorld().isClient()) return;

        life++;
        if (life > MAX_LIFE) {
            this.discard();
            return;
        }

        ServerWorld world = (ServerWorld) this.getWorld();

        Vec3d prev = this.getPos();
        Vec3d vel = this.getVelocity();
        Vec3d next = prev.add(vel);

    /* =============================
       BLOCK COLLISION
       ============================= */

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

    /* =============================
       MOVE
       ============================= */

        Vec3d look = owner.getRotationVec(1.0f);
        Vec3d targetPos = owner.getEyePos().add(look.multiply(TARGET_DISTANCE));

        Vec3d toTarget = targetPos.subtract(this.getPos());
        double dist = toTarget.length();
        if (dist > 0.001) toTarget = toTarget.normalize();

        Vec3d currentVel = this.getVelocity();

        // Constant steering — blend current direction toward target direction
        Vec3d newVel = currentVel.multiply(1.0 - STEERING)
                .add(toTarget.multiply(STEERING));

        // Wobble
        double wobbleX = Math.sin(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        double wobbleZ = Math.cos(life * WOBBLE_FREQUENCY) * WOBBLE_STRENGTH;
        newVel = newVel.add(wobbleX, 0, wobbleZ);

        // Normalize to constant speed
        newVel = newVel.normalize().multiply(SPEED);

        // Only gently resist extreme downward angles, not all downward movement
        if (newVel.y < -0.5) {
            newVel = new Vec3d(newVel.x, -0.5 + (newVel.y + 0.5) * DOWNWARD_SOFTENING, newVel.z);
            newVel = newVel.normalize().multiply(SPEED);
        }

        this.setVelocity(newVel);

        // speed
        newVel = newVel.normalize().multiply(SPEED);

        // soften downward
        if (newVel.y < -0.2) {
            newVel = new Vec3d(
                    newVel.x,
                    newVel.y * DOWNWARD_SOFTENING,
                    newVel.z
            );
        }

        this.setVelocity(newVel);

        this.setPos(
                this.getX() + newVel.x,
                this.getY() + newVel.y,
                this.getZ() + newVel.z
        );

    /* =============================
       PARTICLES)
       ============================= */

        Vec3d dir = newVel.normalize();
        Vec3d side = new Vec3d(-dir.z, 0, dir.x).normalize();
        Vec3d up = new Vec3d(0, 1, 0);

        float radius = PARTICLE_RADIUS;
        // Removed redundant 'length' and 'pspeed' variables here to fix IDE warnings

        // spiral wrapped around velocity direction
        for (int i = 0; i < PARTICLE_SEGMENTS; i++) {

            float angle = (life * PARTICLE_SPEED) + (i * 2.0f);

            double sideOffset = Math.cos(angle) * radius;
            double upOffset = Math.sin(angle) * radius;

            double backOffset = -i * PARTICLE_LENGTH;

            Vec3d pos = this.getPos()
                    .add(dir.multiply(backOffset))
                    .add(side.multiply(sideOffset))
                    .add(up.multiply(upOffset));

            world.spawnParticles(MAIN_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ACCENT_LIGHT, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
        }

        // core line
        for (int i = 0; i < 3; i++) {
            Vec3d pos = this.getPos().subtract(dir.multiply(i * 0.25));

            world.spawnParticles(
                    MAIN_PINK,
                    pos.x, pos.y, pos.z,
                    1,
                    0, 0, 0,
                    0
            );
        }

    /* =============================
       ENTITY COLLISION
       ============================= */

        Box box = new Box(prev, next).expand(PROJECTILE_BOX_EXPAND);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {

            var hit = target.getBoundingBox()
                    .expand(ENTITY_HIT_EXPAND)
                    .raycast(prev, next);
            if (hit.isEmpty()) continue;

            onHit(target, world);
            this.setVelocity(Vec3d.ZERO);
            this.discard();
            return;
        }
    }

    /* ============================================================
       HIT LOGIC
       ============================================================ */

    private void onHit(LivingEntity target, ServerWorld world) {

        if (owner == null) return;

        // FIXED: Passing the owner to the new optimized method signature
        PsychicPower.applyCompel(this.owner, target);

        float radius = 0.25f;
        // Using PARTICLE_SPEED directly here as well to fix IDE warning

        // spiral burst around target
        for (int i = 0; i < 6; i++) {

            float angle = (life * PARTICLE_SPEED) + (i * (float) Math.PI / 3);

            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            world.spawnParticles(
                    MAIN_PINK,
                    target.getX() + offsetX,
                    target.getBodyY(0.5),
                    target.getZ() + offsetZ,
                    1,
                    0, 0, 0,
                    0
            );

            double offsetX2 = Math.cos(angle + 0.5) * (radius * 0.6);
            double offsetZ2 = Math.sin(angle + 0.5) * (radius * 0.6);

            world.spawnParticles(
                    ACCENT_LIGHT,
                    target.getX() + offsetX2,
                    target.getBodyY(0.5) + 0.1,
                    target.getZ() + offsetZ2,
                    1,
                    0, 0, 0,
                    0
            );
        }

        // impact burst
        world.spawnParticles(
                ACCENT_DARK,
                target.getX(),
                target.getBodyY(0.5),
                target.getZ(),
                15,
                0.3, 0.4, 0.3,
                0.02
        );

        // Sound at the hit entity
        world.playSound(
                null,
                target.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                target.getSoundCategory(),
                0.9f,
                1.2f + world.random.nextFloat() * 0.2f
        );

        // Sound at the caster
        world.playSound(
                null,
                owner.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                owner.getSoundCategory(),
                0.7f,
                0.9f + world.random.nextFloat() * 0.2f
        );

        /* =============================
           FX
           ============================= */

        for (int i = 0; i < 4; i++) {

            float angle = (life * PARTICLE_SPEED) + (i * (float) Math.PI / 2);

            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            // main pink spiral
            world.spawnParticles(
                    MAIN_PINK,
                    this.getX() + offsetX,
                    this.getY() + 0.1,
                    this.getZ() + offsetZ,
                    1,
                    0, 0, 0,
                    0
            );

            // layered accent
            double offsetX2 = Math.cos(angle + 0.6) * (radius * 0.7);
            double offsetZ2 = Math.sin(angle + 0.6) * (radius * 0.7);

            world.spawnParticles(
                    ACCENT_LIGHT,
                    this.getX() + offsetX2,
                    this.getY() + 0.12,
                    this.getZ() + offsetZ2,
                    1,
                    0, 0, 0,
                    0
            );
        }

        world.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                owner.getSoundCategory(),
                0.7f,
                1.3f + (world.random.nextFloat() * 0.2f));
    }

    /* ============================================================
       DATA
       ============================================================ */

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {} // 1.21.1 FIXED

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
    }
}