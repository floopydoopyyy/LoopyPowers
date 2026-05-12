package com.yourname.loopypowers.block;

import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.joml.Vector3f;
import net.minecraft.util.math.intprovider.UniformIntProvider;

public class CelestialOreBlock extends ExperienceDroppingBlock {

    public CelestialOreBlock(Settings settings) {
        // specified amount
        super(UniformIntProvider.create(3, 7), settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {

        // pick a face of block
        Direction dir = Direction.random(random);

        double x = pos.getX() + 0.5 + dir.getOffsetX() * 0.55;
        double y = pos.getY() + 0.5 + dir.getOffsetY() * 0.55;
        double z = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.55;

        // face spread
        x += (random.nextDouble() - 0.5) * 0.3;
        y += (random.nextDouble() - 0.5) * 0.3;
        z += (random.nextDouble() - 0.5) * 0.3;

        // outward motion
        double vx = dir.getOffsetX() * 0.05;
        double vy = dir.getOffsetY() * 0.05;
        double vz = dir.getOffsetZ() * 0.05;

        // sparkle
        if (random.nextFloat() < 0.1f) {
            world.addParticle(
                    ParticleTypes.END_ROD,
                    x, y, z,
                    vx, vy + 0.02, vz
            );
        }

        // dust
        if (random.nextFloat() < 0.5f) {
            world.addParticle(
                    new DustParticleEffect(new Vector3f(0.9f, 0.3f, 1.0f), 1.2f),
                    x, y, z,
                    vx * 0.5, vy * 0.5 + 0.01, vz * 0.5
            );
        }
    }
}