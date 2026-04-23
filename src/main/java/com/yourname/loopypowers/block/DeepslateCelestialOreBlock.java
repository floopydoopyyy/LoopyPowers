package com.yourname.loopypowers.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class DeepslateCelestialOreBlock extends ExperienceDroppingBlock {

    public DeepslateCelestialOreBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {

        if (random.nextFloat() > 0.3f) return;

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5);
        double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5);
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5);

        world.addParticle(
                ParticleTypes.END_ROD,
                x, y, z,
                0, 0.02, 0
        );
    }
}