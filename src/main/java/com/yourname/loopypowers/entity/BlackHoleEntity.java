package com.yourname.loopypowers.entity;

import com.yourname.loopypowers.damage.ModDamageTypes;
import com.yourname.loopypowers.network.payload.BlackHoleParticlePayload;
import com.yourname.loopypowers.power.CosmicPower;
import com.yourname.loopypowers.sound.ModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class BlackHoleEntity extends Entity {

    private ServerPlayerEntity owner;
    private int life;
    private Vec3d travelDirection = Vec3d.ZERO;

    /* ============================================================
       Constants
       ============================================================ */

    public static final int   LIFESPAN         = 200;  // ticks
    private static final double TRAVEL_SPEED   = 0.03;
    private static final float MOOVIN_CHANCE   = 0.0003f;

    public static final double OUTER_RADIUS    = 25.0;
    public static final double MID_RADIUS      = 16.0;
    public static final double INNER_RADIUS    = 4.0;

    public static final double OUTER_PULL      = 0.02;
    public static final double MID_PULL        = 0.7;
    public static final double INNER_PULL      = 0.17;

    private static final double MAX_PULL_SPEED   = 0.45;
    private static final double ORBIT_TANGENT_MIX = 0.55;

    public static final float INNER_DAMAGE_PER_TICK = 1.3f;

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

        BlackHoleParticlePayload bhPayload = new BlackHoleParticlePayload(this.getId(), lifeProgress);
        PlayerLookup.tracking(this).forEach(p -> ServerPlayNetworking.send(p, bhPayload));

        if (life % 30 == 0) {
            playBlackHoleLoop(world, center);
        }
    }

    /* ============================================================
       Pull
       ============================================================ */

    private boolean pullAndDamageEntities(ServerWorld world, Vec3d center) {
        boolean dealtDamage = false;

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

    private void applyOrbitalPull(Entity entity, Vec3d toCenter, double dist, double strength) {
        Vec3d inward = toCenter.normalize();
        Vec3d tangent = new Vec3d(-inward.z, 0, inward.x).normalize();

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
            entity.damage(ModDamageTypes.blackHole(world, owner), INNER_DAMAGE_PER_TICK);
        }
        CosmicPower.drainFateTimer(entity);
    }

    /* ============================================================
       Sounds
       ============================================================ */

    private void playBlackHoleLoop(ServerWorld world, Vec3d center) {
        double soundRadiusSq = OUTER_RADIUS * OUTER_RADIUS;

        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.squaredDistanceTo(center) <= soundRadiusSq) {
                world.playSound(null, p.getBlockPos(),
                        ModSounds.DARKNESSLOOP, net.minecraft.sound.SoundCategory.PLAYERS,
                        0.9f, 0.7f);
            }
        }
    }

    /* ============================================================
       Data (1.21.1)
       ============================================================ */

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.life = nbt.getInt("Life");
        double dx = nbt.getDouble("DirX");
        double dy = nbt.getDouble("DirY");
        double dz = nbt.getDouble("DirZ");
        this.travelDirection = new Vec3d(dx, dy, dz);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.life);
        nbt.putDouble("DirX", this.travelDirection.x);
        nbt.putDouble("DirY", this.travelDirection.y);
        nbt.putDouble("DirZ", this.travelDirection.z);
    }
}
