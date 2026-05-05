package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.power.PsychicPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
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

public class PuppetryEntity extends Entity {

    private ServerPlayerEntity owner;
    private int life;

    /* ============================================================
       Particles
       ============================================================ */

    private static final DustParticleEffect MAIN_PINK =
            new DustParticleEffect(new Vec3d(0.9, 0.2, 0.6).toVector3f(), 1.1f);

    private static final DustParticleEffect ACCENT_LIGHT =
            new DustParticleEffect(new Vec3d(1.0, 0.6, 0.9).toVector3f(), 0.8f);

    private static final DustParticleEffect ACCENT_DARK =
            new DustParticleEffect(new Vec3d(0.5, 0.0, 0.6).toVector3f(), 0.9f);

    private static final DustParticleEffect DEEP_PURPLE =
            new DustParticleEffect(new Vec3d(0.4, 0.0, 0.8).toVector3f(), 1.3f);

    private static final DustParticleEffect MID_PURPLE =
            new DustParticleEffect(new Vec3d(0.7, 0.1, 0.9).toVector3f(), 1.0f);

    private static final DustParticleEffect BRIGHT_PINK =
            new DustParticleEffect(new Vec3d(1.0, 0.3, 0.9).toVector3f(), 0.7f);

    /* ============================================================
       Constants
       ============================================================ */

    // Lifespan
    static final int MAX_LIFE = 100;

    // Movement
    public static final double SPEED     = 0.7;
    static final double STEERING         = 0.25;

    // Targeting
    static final double TARGET_DISTANCE    = 50.0;  // further = more vertical control
    static final double DOWNWARD_SOFTENING = 0.90;  // gentle — only slightly resists diving

    // Collision
    static final double ENTITY_HIT_EXPAND    = 0.4;
    static final double PROJECTILE_BOX_EXPAND = 0.6;

    // Particles
    static final float PARTICLE_RADIUS     = 0.25f;  // outer spiral radius
    static final float PARTICLE_INNER_RADIUS = 0.08f; // inner core spiral radius
    static final float PARTICLE_LENGTH     = 0.25f;  // spacing between spiral rings
    static final float PARTICLE_SPEED      = 1.9f;   // spin rate
    static final int   PARTICLE_SEGMENTS   = 6;      // rings in outer spiral
    static final int   PARTICLE_CORE_COUNT = 4;      // rings in inner core spiral

    /* ============================================================
       Constructor
       ============================================================ */

    public PuppetryEntity(EntityType<?> type, World world) {
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
       Tick
       ============================================================ */

    @Override
    public void tick() {
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

    /* =============================
       Block collision
       ============================= */

        Vec3d vel = this.getVelocity();
        Vec3d next = prev.add(vel);

        var blockHit = world.raycast(new RaycastContext(
                prev, next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            this.discard();
            return;
        }

    /* =============================
       Movement — steers toward crosshair
       ============================= */

        Vec3d look      = owner.getRotationVec(1.0f);
        Vec3d targetPos = owner.getEyePos().add(look.multiply(TARGET_DISTANCE));
        Vec3d toTarget  = targetPos.subtract(this.getPos());

        if (toTarget.length() > 0.001) toTarget = toTarget.normalize();

        Vec3d newVel = this.getVelocity()
                .multiply(1.0 - STEERING)
                .add(toTarget.multiply(STEERING))
                .normalize()
                .multiply(SPEED);

        // Only gently resist extreme downward angles
        if (newVel.y < -0.5) {
            newVel = new Vec3d(newVel.x, -0.5 + (newVel.y + 0.5) * DOWNWARD_SOFTENING, newVel.z)
                    .normalize().multiply(SPEED);
        }

        this.setVelocity(newVel);
        this.setPos(
                this.getX() + newVel.x,
                this.getY() + newVel.y,
                this.getZ() + newVel.z
        );

    /* =============================
       Particles — grand spiral bolt
       ============================= */

        Vec3d dir  = newVel.normalize();
        Vec3d side = new Vec3d(-dir.z, 0, dir.x).normalize();
        Vec3d up   = dir.crossProduct(side).normalize(); // true perpendicular, not world-up

        // two thick spirals winding around the bolt
        for (int i = 0; i < PARTICLE_SEGMENTS; i++) {
            float base  = (life * PARTICLE_SPEED) + (i * (float)(Math.PI * 2.0 / PARTICLE_SEGMENTS));
            double back = -i * (double) PARTICLE_LENGTH;

            // Helix strand
            Vec3d posA = this.getPos()
                    .add(dir.multiply(back))
                    .add(side.multiply(Math.cos(base) * PARTICLE_RADIUS))
                    .add(up.multiply(Math.sin(base) * PARTICLE_RADIUS));

            // Helix strand
            double baseB = base + Math.PI;
            Vec3d posB = this.getPos()
                    .add(dir.multiply(back))
                    .add(side.multiply(Math.cos(baseB) * PARTICLE_RADIUS))
                    .add(up.multiply(Math.sin(baseB) * PARTICLE_RADIUS));

            world.spawnParticles(DEEP_PURPLE,  posA.x, posA.y, posA.z, 1, 0, 0, 0, 0);
            world.spawnParticles(MID_PURPLE,   posA.x, posA.y, posA.z, 1, 0.02, 0.02, 0.02, 0);
            world.spawnParticles(DEEP_PURPLE,  posB.x, posB.y, posB.z, 1, 0, 0, 0, 0);
            world.spawnParticles(ACCENT_LIGHT, posB.x, posB.y, posB.z, 1, 0.02, 0.02, 0.02, 0);
        }

        // Mid layer spiral
        for (int i = 0; i < PARTICLE_SEGMENTS; i++) {
            float base  = -(life * PARTICLE_SPEED * 0.7f) + (i * (float)(Math.PI * 2.0 / PARTICLE_SEGMENTS));
            double back = -i * (double) PARTICLE_LENGTH * 0.9;
            double midRadius = PARTICLE_RADIUS * 0.6;

            Vec3d pos = this.getPos()
                    .add(dir.multiply(back))
                    .add(side.multiply(Math.cos(base) * midRadius))
                    .add(up.multiply(Math.sin(base) * midRadius));

            world.spawnParticles(BRIGHT_PINK, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0);
            world.spawnParticles(MID_PURPLE,  pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        // Inner tight core
        for (int i = 0; i < PARTICLE_CORE_COUNT; i++) {
            float base  = (life * PARTICLE_SPEED * 2.5f) + (i * (float)(Math.PI / 2.0));
            double back = -i * (double) PARTICLE_LENGTH * 0.5;

            Vec3d pos = this.getPos()
                    .add(dir.multiply(back))
                    .add(side.multiply(Math.cos(base) * PARTICLE_INNER_RADIUS))
                    .add(up.multiply(Math.sin(base) * PARTICLE_INNER_RADIUS));

            world.spawnParticles(MAIN_PINK,  pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(BRIGHT_PINK, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        // Core line
        for (int i = 0; i < 6; i++) {
            Vec3d pos = this.getPos().subtract(dir.multiply(i * 0.22));
            world.spawnParticles(MAIN_PINK,   pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticles(DEEP_PURPLE, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }

        // Leading tip
        Vec3d tip = this.getPos().add(dir.multiply(0.4));
        world.spawnParticles(DEEP_PURPLE,  tip.x, tip.y, tip.z, 2, 0.08, 0.08, 0.08, 0.01);
        world.spawnParticles(MID_PURPLE,   tip.x, tip.y, tip.z, 1, 0.12, 0.12, 0.12, 0.02);
        world.spawnParticles(BRIGHT_PINK,  tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.01);

    /* =============================
       Entity collision
       ============================= */

        Box box = new Box(prev, next).expand(PROJECTILE_BOX_EXPAND);

        List<LivingEntity> targets = world.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != owner
        );

        for (LivingEntity target : targets) {
            var hit = target.getBoundingBox().expand(ENTITY_HIT_EXPAND).raycast(prev, next);
            if (hit.isEmpty()) continue;

            onHit(target, world);
            this.setVelocity(Vec3d.ZERO);
            this.discard();
            return;
        }
    }

    /* ============================================================
       Hit logic
       ============================================================ */

    private void onHit(LivingEntity target, ServerWorld world) {
        if (owner == null) return;

        // Apply the ultimate control tag via PsychicPower
        PsychicPower.applyUltimateControl(target);

        // Spiral burst around target
        float radius = 0.25f;
        float pspeed = 0.6f;

        for (int i = 0; i < 6; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 3);

            world.spawnParticles(MAIN_PINK,
                    target.getX() + Math.cos(angle) * radius,
                    target.getBodyY(0.5),
                    target.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);

            world.spawnParticles(ACCENT_LIGHT,
                    target.getX() + Math.cos(angle + 0.5) * (radius * 0.6),
                    target.getBodyY(0.5) + 0.1,
                    target.getZ() + Math.sin(angle + 0.5) * (radius * 0.6),
                    1, 0, 0, 0, 0);
        }

        // Impact burst
        world.spawnParticles(ACCENT_DARK,
                target.getX(), target.getBodyY(0.5), target.getZ(),
                15, 0.3, 0.4, 0.3, 0.02);

        // FX ring at projectile position
        for (int i = 0; i < 4; i++) {
            float angle = (life * pspeed) + (i * (float) Math.PI / 2);

            world.spawnParticles(MAIN_PINK,
                    this.getX() + Math.cos(angle) * radius,
                    this.getY() + 0.1,
                    this.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);

            world.spawnParticles(ACCENT_LIGHT,
                    this.getX() + Math.cos(angle + 0.6) * (radius * 0.7),
                    this.getY() + 0.12,
                    this.getZ() + Math.sin(angle + 0.6) * (radius * 0.7),
                    1, 0, 0, 0, 0);
        }

        // Sound at hit entity
        world.playSound(null, target.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                target.getSoundCategory(),
                0.9f, 1.2f + world.random.nextFloat() * 0.2f);

        // Sound at caster
        world.playSound(null, owner.getBlockPos(),
                SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                owner.getSoundCategory(),
                0.7f, 0.9f + world.random.nextFloat() * 0.2f);
    }

    /* ============================================================
       Data
       ============================================================ */

    @Override protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
    }
}