package com.yourname.loopypowers.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PointedDripstoneBlock;
import net.minecraft.block.enums.Thickness;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;
import org.joml.Vector3f;

public class IceSpikeBlock extends PointedDripstoneBlock {

    public IceSpikeBlock(Settings settings) {
        super(settings);
        this.setDefaultState(
                this.getDefaultState()
                        .with(Properties.VERTICAL_DIRECTION, Direction.UP)
                        .with(Properties.THICKNESS, Thickness.TIP)
        );
    }
    private static final DustParticleEffect ICE_SHIMMER =
            new DustParticleEffect(new Vector3f(0.55f, 0.90f, 1.00f), 0.9f);

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // never be placed on ceiling
        if (ctx.getSide() == Direction.DOWN) return null;

        World w = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockPos down = pos.down();

        // Must have block under it OR another spike under it
        if (!w.getBlockState(down).isSideSolidFullSquare(w, down, Direction.UP)
                && !w.getBlockState(down).isOf(this)) {
            return null;
        }

        // Start as TIP; we'll fix the entire column right after placement
        return this.getDefaultState()
                .with(Properties.VERTICAL_DIRECTION, Direction.UP)
                .with(Properties.THICKNESS, Thickness.TIP);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         net.minecraft.entity.LivingEntity placer, net.minecraft.item.ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        if (!world.isClient) {
            updateColumn((WorldAccess) world, pos, this);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);

        if (!world.isClient && !state.isOf(newState.getBlock())) {
            // Update column above and below when a spike breaks
            updateColumn((WorldAccess) world, pos.down(), this);
            updateColumn((WorldAccess) world, pos.up(), this);
        }
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos down = pos.down();
        BlockState below = world.getBlockState(down);

        // can stack on itself
        if (below.isOf(this)) return true;

        // or solid-top block
        return below.isSideSolidFullSquare(world, down, Direction.UP);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state,
                                                Direction direction,
                                                BlockState neighborState,
                                                WorldAccess world,
                                                BlockPos pos,
                                                BlockPos neighborPos) {
        // If support disappears break
        if (!this.canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }

        // Force UP always
        if (state.get(Properties.VERTICAL_DIRECTION) != Direction.UP) {
            state = state.with(Properties.VERTICAL_DIRECTION, Direction.UP);
        }

        // Recompute thickness for this column (server only)
        if (!world.isClient()) {
            updateColumn(world, pos, this);
        }

        // IMPORTANT: don't call super here (vanilla dripstone logic can fight you)
        return state;
    }

    private static void updateColumn(WorldAccess world, BlockPos start, Block spikeBlock) {
        // Find an actual spike block near the start
        BlockPos p = start;

        if (!world.getBlockState(p).isOf(spikeBlock)) {
            if (world.getBlockState(p.up()).isOf(spikeBlock)) p = p.up();
            else if (world.getBlockState(p.down()).isOf(spikeBlock)) p = p.down();
            else return;
        }

        // Find bottom
        BlockPos bottom = p;
        while (world.getBlockState(bottom.down()).isOf(spikeBlock)) bottom = bottom.down();

        // Find top
        BlockPos top = p;
        while (world.getBlockState(top.up()).isOf(spikeBlock)) top = top.up();

        int total = (top.getY() - bottom.getY()) + 1;

        for (int i = 0; i < total; i++) {
            BlockPos at = bottom.up(i);
            BlockState s = world.getBlockState(at);
            if (!s.isOf(spikeBlock)) continue;

            Thickness th;
            if (total <= 1) th = Thickness.TIP;
            else if (total == 2) th = (i == 0) ? Thickness.FRUSTUM : Thickness.TIP;
            else if (total == 3) th = (i == 0) ? Thickness.BASE : (i == 1 ? Thickness.FRUSTUM : Thickness.TIP);
            else th = (i == 0) ? Thickness.BASE : (i == 1 ? Thickness.FRUSTUM : (i == total - 1 ? Thickness.TIP : Thickness.MIDDLE));

            BlockState ns = s.with(Properties.VERTICAL_DIRECTION, Direction.UP)
                    .with(Properties.THICKNESS, th);

            // Use flag 2 to update clients without causing neighbor-update recursion
            if (ns != s) world.setBlockState(at, ns, 2);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        // client side ice particles
        if (random.nextInt(3) != 0) return; // 33% chance each tick

        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.25;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.25;

        // bias particles higher if a tip
        Thickness th = state.get(Properties.THICKNESS);
        double yBase = switch (th) {
            case TIP, TIP_MERGE -> 0.65;
            case MIDDLE -> 0.55;
            case FRUSTUM -> 0.40;
            case BASE -> 0.20;
        };

        double y = pos.getY() + yBase + random.nextDouble() * 0.15;

        /*
        world.addParticle(ParticleTypes.END_ROD,
                x, y, z,
                (random.nextDouble() - 0.5) * 0.01,
                0.01 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.01
        ); */

        // dust
        if (random.nextBoolean()) {
            world.addParticle(ICE_SHIMMER,
                    x, y, z,
                    0.0, 0.01, 0.0
            );
        }
    }
}