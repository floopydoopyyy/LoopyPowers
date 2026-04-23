package com.yourname.loopypowers.block;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public class CasinoBarsBlock extends PaneBlock {

    // blockstate flag so you can turn particles on/off
    public static final BooleanProperty ACTIVE = BooleanProperty.of("active");

    public CasinoBarsBlock(AbstractBlock.Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(ACTIVE, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<net.minecraft.block.Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(ACTIVE);
    }

    /**
     * Client-side sparkle FX (called on the client for visible blocks).
     */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        // this method is only called client-side
        if (!world.isClient) return;

        // only sparkle when active
        //if (!state.get(ACTIVE)) return;

        // tune frequency
        if (random.nextInt(35) != 0) return;

        int count = 1 + random.nextInt(2);

        for (int i = 0; i < count; i++) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double y = pos.getY() + 0.2 + random.nextDouble() * 0.8;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;

            double vx = (random.nextDouble() - 0.5) * 0.02;
            double vy = 0.02 + random.nextDouble() * 0.02;
            double vz = (random.nextDouble() - 0.5) * 0.02;

            world.addParticle(ParticleTypes.ENCHANT, x, y, z, vx, vy, vz);
        }
    }
}