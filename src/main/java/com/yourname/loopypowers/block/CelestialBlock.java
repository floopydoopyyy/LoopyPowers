package com.yourname.loopypowers.block;

import com.yourname.loopypowers.manager.PowerManager;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;

public class CelestialBlock extends Block {

    // how much cooldown to remove per tick (in ms)
    private static final long COOLDOWN_REDUCTION_PER_TICK = 70; // about 25-30% increase

    public CelestialBlock(AbstractBlock.Settings settings) {
        super(settings);
    }

    /* ============================================================
       COOLDOWN EFFECT
       ============================================================ */

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        super.onSteppedOn(world, pos, state, entity);

        if (world.isClient) return;

        if (entity instanceof ServerPlayerEntity player) {

            if (PowerManager.getPower(player) == null) return;

            PowerManager.reduceAllCooldowns(player, COOLDOWN_REDUCTION_PER_TICK);

            spawnCooldownParticles(player);
        }
    }

    private void spawnCooldownParticles(ServerPlayerEntity player) {

        ServerWorld world = (ServerWorld) player.getWorld();

        // throttle (BIG difference)
        if (player.age % 3 != 0) return;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        DustParticleEffect PURPLE =
                new DustParticleEffect(new Vector3f(0.7f, 0.3f, 1.0f), 0.8f);

        int points = 4; // fewer particles

        for (int i = 0; i < points; i++) {

            double angle = (player.age * 0.08) + (i * Math.PI * 2 / points); // slower spin

            double radius = 0.35; // tighter circle

            double x = px + Math.cos(angle) * radius;
            double z = pz + Math.sin(angle) * radius;

            double y = py + 0.2 + (i * 0.08); // less vertical stretch

            double vx = Math.cos(angle) * 0.01;
            double vz = Math.sin(angle) * 0.01;

            world.spawnParticles(
                    PURPLE,
                    x, y, z,
                    1,
                    vx, 0.02, vz,
                    0
            );
        }
    }

    /* ============================================================
       PARTICLES
       ============================================================ */

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {

        Direction dir = Direction.random(random);

        double x = pos.getX() + 0.5 + dir.getOffsetX() * 0.55;
        double y = pos.getY() + 0.5 + dir.getOffsetY() * 0.55;
        double z = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.55;

        x += (random.nextDouble() - 0.5) * 0.3;
        y += (random.nextDouble() - 0.5) * 0.3;
        z += (random.nextDouble() - 0.5) * 0.3;

        double vx = dir.getOffsetX() * 0.05;
        double vy = dir.getOffsetY() * 0.05;
        double vz = dir.getOffsetZ() * 0.05;

        if (random.nextFloat() < 0.15f) {
            world.addParticle(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    vx, vy + 0.02, vz
            );
        }

        if (random.nextFloat() < 0.8f) {
            world.addParticle(
                    new DustParticleEffect(new Vector3f(0.9f, 0.3f, 1.0f), 1.2f),
                    x, y, z,
                    vx * 0.5, vy * 0.5 + 0.01, vz * 0.5
            );
        }
    }
}