package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.power.CosmicPower;
import com.yourname.loopypowers.sound.ModSounds;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

public class BlackHoleEntity extends Entity {

    private ServerPlayerEntity owner;
    private int life;
    private Vec3d travelDirection = Vec3d.ZERO;

    /* ============================================================
       Constants
       ============================================================ */

    public static final int   LIFESPAN         = 200;  // ticks

    // movement speed per tick
    private static final double TRAVEL_SPEED   = 0.03;

    // chance per tick to spawn a cow if bored
    private static final float MOOVIN_CHANCE   = 0.0003f;

    public static final double OUTER_RADIUS    = 25.0;
    public static final double MID_RADIUS      = 16.0;
    public static final double INNER_RADIUS    = 4.0;

    // Pull strengths per ring
    public static final double OUTER_PULL      = 0.02;
    public static final double MID_PULL        = 0.7;
    public static final double INNER_PULL      = 0.17;

    // velocity cap
    private static final double MAX_PULL_SPEED = 0.45;

    // Higher = more circular orbit, Lower = More direct pull
    private static final double ORBIT_TANGENT_MIX = 0.55;

    // Damage applied per tick in inner ring
    public static final float INNER_DAMAGE_PER_TICK = 1.3f;

    // Particles (OPTIMIZED)
    private static final int   CORE_RINGS       = 5;    // Reduced from 8
    private static final int   DISC_ARMS        = 3;    // Reduced from 4
    private static final float DISC_ARM_RADIUS  = 6.0f; // disk size
    private static final int   OUTER_WISP_COUNT = 8;    // Reduced from 12

    // particles, last float is size, more central stuff should be bigger
    private static final DustParticleEffect BLACK =
            new DustParticleEffect(new Vector3f(0.02f, 0.0f, 0.05f), 2.8f);

    private static final DustParticleEffect PURPLE =
            new DustParticleEffect(new Vector3f(0.25f, 0.0f, 0.4f), 1.5f);

    private static final DustParticleEffect ORANGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.45f, 0.0f), 1.5f);

    private static final DustParticleEffect HOT_ORANGE =
            new DustParticleEffect(new Vector3f(1.0f, 0.65f, 0.1f), 1.55f);

    private static final DustParticleEffect LIGHT_PURPLE =
            new DustParticleEffect(new Vector3f(0.5f, 0.0f, 0.7f), 1.55f);

    /* ============================================================
       Constructor
       ============================================================ */

    public BlackHoleEntity(EntityType<?> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public void setOwner(ServerPlayerEntity owner) {
        this.owner = owner;
    }

    public ServerPlayerEntity getOwner() {
        return owner;
    }

    public void setTravelDirection(Vec3d dir) {
        this.travelDirection = dir.normalize();
    }

    /* ============================================================
       Tick
       ============================================================ */

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient()) return;

        life++;
        if (life > LIFESPAN || owner == null || owner.isRemoved()) {
            this.discard();
            return;
        }

        // Move slowly in the assigned direction
        Vec3d current = this.getPos();
        Vec3d newPos = current.add(this.travelDirection.multiply(TRAVEL_SPEED));
        this.setPos(newPos.x, newPos.y, newPos.z);

        ServerWorld world = (ServerWorld) this.getWorld();
        Vec3d center = this.getPos();
        float lifeProgress = (float) life / LIFESPAN;

        boolean doingDamage = pullAndDamageEntities(world, center);

        // -- EGG --
        if (!doingDamage && world.random.nextFloat() < MOOVIN_CHANCE) {
            net.minecraft.entity.passive.CowEntity moovin = EntityType.COW.create(world);
            if (moovin != null) {
                double ox = (world.random.nextDouble() - 0.5) * 12.0;
                double oy = (world.random.nextDouble() - 0.5) * 12.0;
                double oz = (world.random.nextDouble() - 0.5) * 12.0;
                moovin.refreshPositionAndAngles(center.x + ox, center.y + oy, center.z + oz, world.random.nextFloat() * 360f, 0);
                moovin.setCustomName(net.minecraft.text.Text.literal("Moovin"));
                world.spawnEntity(moovin);
            }
        }

        spawnAllParticles(world, center, lifeProgress);

        // Loop Ambient Sound
        if (life % 30 == 0) {
            playBlackHoleLoop(world, center);
        }
    }

    /* ============================================================
       pull stuff
       ============================================================ */

    private boolean pullAndDamageEntities(ServerWorld world, Vec3d center) { // applied to closer entities
        boolean dealtDamage = false;

        // Scan for all entities
        List<Entity> nearby = world.getEntitiesByClass(
                Entity.class,
                new net.minecraft.util.math.Box(center, center).expand(OUTER_RADIUS),
                e -> e.isAlive() && e != owner && e != this && !e.isSpectator()
        );

        for (Entity e : nearby) {
            Vec3d toCenter = center.subtract(e.getPos());
            double dist = toCenter.length();
            if (dist < 0.01) continue;

            if (dist <= INNER_RADIUS) {
                applyOrbitalPull(e, toCenter, dist, INNER_PULL);

                // Only damage and apply effects if it's actually alive
                if (e instanceof LivingEntity living) {
                    applyInnerRingEffects(world, living);
                    dealtDamage = true;
                }

            } else if (dist <= MID_RADIUS) {
                applyOrbitalPull(e, toCenter, dist, MID_PULL);

            } else {
                applyOrbitalPull(e, toCenter, dist, OUTER_PULL);
            }
        }

        return dealtDamage;
    }

    private void applyOrbitalPull(Entity entity, Vec3d toCenter, double dist, double strength) { // applied to further entities
        Vec3d inward = toCenter.normalize();

        // tangent — perpendicular to inward on the horizontal plane
        Vec3d tangent = new Vec3d(-inward.z, 0, inward.x).normalize();

        // blend - mostly inward close up, more orbital further out
        double tangentMix = ORBIT_TANGENT_MIX * MathHelper.clamp(dist / MID_RADIUS, 0, 1);
        Vec3d pullDir = inward.multiply(1.0 - tangentMix).add(tangent.multiply(tangentMix)).normalize();

        Vec3d vel = entity.getVelocity();
        Vec3d newVel = vel.add(pullDir.multiply(strength));

        if (newVel.length() > MAX_PULL_SPEED) {
            newVel = newVel.normalize().multiply(MAX_PULL_SPEED);
        }

        entity.setVelocity(newVel);
        entity.velocityModified = true;

        if (entity.getWorld() instanceof ServerWorld sw) {
            sw.getChunkManager().sendToNearbyPlayers(entity,
                    new net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket(entity));
        }
    }

    private void applyInnerRingEffects(ServerWorld world, LivingEntity entity) {
        if (owner != null) {
            entity.damage(
                    ModDamageTypes.blackHole(world, owner),
                    INNER_DAMAGE_PER_TICK
            );
        }

        // drain fate
        CosmicPower.drainFateTimer(entity);
    }

    /* ============================================================
       Sounds
       ============================================================ */

    private void playBlackHoleLoop(ServerWorld world, Vec3d center) {
        double soundRadiusSq = OUTER_RADIUS * OUTER_RADIUS;

        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(center) <= soundRadiusSq) {
                world.playSound(
                        null,
                        p.getBlockPos(),
                        ModSounds.DARKNESSLOOP,
                        net.minecraft.sound.SoundCategory.PLAYERS,
                        0.9f,
                        0.7f
                );
            }
        }
    }

    /* ============================================================
       Particles
       ============================================================ */

    private void spawnAllParticles(ServerWorld world, Vec3d center, float lifeProgress) {
        long time = world.getTime();

        spawnEventHorizon(world, center, time);
        spawnAccretionDisk(world, center, time, lifeProgress);
        spawnInnerVortex(world, center, time);
        spawnOuterWisps(world, center, time);

        // extra stuff when close to despawning
        if (lifeProgress > 0.75f) {
            spawnCollapseFlare(world, center, lifeProgress, time);
        }
    }

    private void spawnEventHorizon(ServerWorld world, Vec3d center, long time) {
        for (int ring = 0; ring < CORE_RINGS; ring++) {
            // Spaced out slightly more to cover the same volume with fewer rings
            double ringRadius = 0.5 + ring * 0.6;
            int pointsInRing = 6 + ring * 2;
            double rotOffset = time * (0.08 + ring * 0.015) * (ring % 2 == 0 ? 1 : -1);

            for (int j = 0; j < pointsInRing; j++) {
                double angle = rotOffset + (j * Math.PI * 2.0 / pointsInRing);
                double tiltY = Math.sin(angle * 0.5 + ring) * 0.25;

                double x = center.x + Math.cos(angle) * ringRadius;
                double y = center.y + 1.0 + tiltY;
                double z = center.z + Math.sin(angle) * ringRadius;

                DustParticleEffect color = (ring < 2) ? BLACK : PURPLE;
                world.spawnParticles(color, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private void spawnAccretionDisk(ServerWorld world, Vec3d center, long time, float lifeProgress) {
        double baseSpeed = 0.06 + lifeProgress * 0.04; // spins faster near end

        for (int arm = 0; arm < DISC_ARMS; arm++) {
            double armOffset = arm * (Math.PI * 2.0 / DISC_ARMS);

            // Shorter trail length to save particles
            int trailLength = 10;
            for (int t = 0; t < trailLength; t++) {
                double trailFraction = (double) t / trailLength;

                // Spiral - angle increases, radius increases with trail position
                double angle = time * baseSpeed + armOffset - (t * 0.28);
                double r = 0.9 + trailFraction * (DISC_ARM_RADIUS - 0.9);

                // Slight vertical wave for depth
                double waveY = Math.sin(angle * 2 + arm) * 0.18 * (1.0 - trailFraction);

                double x = center.x + Math.cos(angle) * r;
                double y = center.y + 1.0 + waveY;
                double z = center.z + Math.sin(angle) * r;

                // orange near core, purple at edges
                DustParticleEffect diskColor = (trailFraction < 0.4) ? ORANGE
                        : (trailFraction < 0.7) ? HOT_ORANGE
                        : LIGHT_PURPLE;

                world.spawnParticles(diskColor, x, y, z, 1, 0, 0, 0, 0);

                // Reduced spark density
                if (t < 3 && world.random.nextFloat() < 0.15f) {
                    world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                            x, y, z, 1,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            (world.random.nextDouble() - 0.5) * 0.05,
                            0.01);
                }
            }
        }
    }

    private void spawnInnerVortex(ServerWorld world, Vec3d center, long time) {
        int points = 8; // Reduced from 12
        for (int i = 0; i < points; i++) {
            // Counter-rotates relative to the disk
            double angle = -(time * 0.14) + (i * Math.PI * 2.0 / points);
            double r = 0.55 + Math.sin(time * 0.1 + i) * 0.1; // slight pulse

            double x = center.x + Math.cos(angle) * r;
            double y = center.y + 1.0 + Math.sin(angle * 3) * 0.1;
            double z = center.z + Math.sin(angle) * r;

            world.spawnParticles(PURPLE, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    private void spawnOuterWisps(ServerWorld world, Vec3d center, long time) {
        // spawns at tick offset
        int wispIndex = (int)(time % OUTER_WISP_COUNT);

        double angle = time * 0.03 + (wispIndex * Math.PI * 2.0 / OUTER_WISP_COUNT);
        double r = OUTER_RADIUS * 0.6 + world.random.nextDouble() * OUTER_RADIUS * 0.3;

        double x = center.x + Math.cos(angle) * r;
        double y = center.y + 1.0 + (world.random.nextDouble() - 0.5) * 3.0;
        double z = center.z + Math.sin(angle) * r;

        // Velocity directed slightly inward
        double dx = (center.x - x) * 0.01;
        double dz = (center.z - z) * 0.01;

        world.spawnParticles(LIGHT_PURPLE, x, y, z, 1, dx, 0.005, dz, 0.008);
    }

    private void spawnCollapseFlare(ServerWorld world, Vec3d center, float lifeProgress, long time) {
        float intensity = (lifeProgress - 0.75f) / 0.25f; // 0 → 1 in final quarter

        // despawn particles
        if (time % 3 == 0) {
            int burstCount = (int)(3 + intensity * 8);
            for (int i = 0; i < burstCount; i++) {
                double vx = (world.random.nextDouble() - 0.5) * 0.3 * intensity;
                double vy = (world.random.nextDouble() - 0.5) * 0.2 * intensity;
                double vz = (world.random.nextDouble() - 0.5) * 0.3 * intensity;
                world.spawnParticles(ParticleTypes.END_ROD,
                        center.x, center.y + 1.0, center.z,
                        1, vx, vy, vz, 0.05);
            }
        }

        // Reduced corona ring
        int coronaCount = (int)(intensity * 4);
        for (int i = 0; i < coronaCount; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = 0.5 + world.random.nextDouble() * 0.8;
            world.spawnParticles(ORANGE,
                    center.x + Math.cos(angle) * r,
                    center.y + 1.0 + (world.random.nextDouble() - 0.5) * 0.4,
                    center.z + Math.sin(angle) * r,
                    1, 0, 0, 0, 0);
        }
    }

    /* ============================================================
       Data
       ============================================================ */

    @Override protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");

        // restore trajectory if chunk reloads
        double dx = nbt.getDouble("DirX");
        double dy = nbt.getDouble("DirY");
        double dz = nbt.getDouble("DirZ");
        this.travelDirection = new Vec3d(dx, dy, dz);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);

        // save trajectory
        nbt.putDouble("DirX", this.travelDirection.x);
        nbt.putDouble("DirY", this.travelDirection.y);
        nbt.putDouble("DirZ", this.travelDirection.z);
    }
}