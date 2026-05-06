package com.yourname.loopypowers.block;

import com.yourname.loopypowers.manager.PowerManager;
import com.yourname.loopypowers.power.NaturePower;
import com.yourname.loopypowers.power.Power;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.shape.VoxelShapes;
import com.yourname.loopypowers.damage.ModDamageTypes;

public class ThornVineBlock extends Block {

    // top-variant
    public static final BooleanProperty TOP = BooleanProperty.of("top");

    // shape
    private static final VoxelShape SHAPE = Block.createCuboidShape(2, 0, 2, 14, 16, 14);

    // tuning
    private static final int DAMAGE_INTERVAL_TICKS = 8; // tick delay per damage
    private static final float DAMAGE_AMOUNT = 2.5f;     // damage
    private static final int SLOWNESS_AMP = 2;           // Slowness amp
    private static final int SLOWNESS_TICKS = 20;        // refresh rate

    public ThornVineBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(TOP, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    // Make sure the placed block instantly chooses top/body correctly
    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        boolean isTop = !world.getBlockState(pos.up()).isOf(this);
        return this.getDefaultState().with(TOP, isTop);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return VoxelShapes.empty(); // walk through
    }

    /**
     * PLACEMENT:
     * - stack on itself
     * - optionally "like grass" (dirt-ish blocks) AND/OR solid-top blocks
     */
    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos downPos = pos.down();
        BlockState below = world.getBlockState(downPos);

        // stack on itself
        if (below.isOf(this)) return true;

        // sound type
        if (below.isIn(BlockTags.DIRT)) return true;

        // placed on most blocks
        return below.isSideSolidFullSquare(world, downPos, Direction.UP);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state,
                                                Direction direction,
                                                BlockState neighborState,
                                                WorldAccess world,
                                                BlockPos pos,
                                                BlockPos neighborPos) {
        if (!state.canPlaceAt(world, pos)) {
            return Blocks.AIR.getDefaultState();
        }

        // recompute TOP whenever neighbors change
        boolean isTop = !world.getBlockState(pos.up()).isOf(this);
        return state.with(TOP, isTop);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {

        // Ignore people with the nature power
        if (entity instanceof ServerPlayerEntity player) {
            Power p = PowerManager.getPower(player);
            if (p instanceof NaturePower) return;
        }

        entity.slowMovement(state, new Vec3d(0.70D, 0.75D, 0.70D));

        if (!(entity instanceof LivingEntity living)) return;
        if (world.isClient) return;

        living.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SLOWNESS, SLOWNESS_TICKS, SLOWNESS_AMP, true, false
        ));

        if (entity.age % DAMAGE_INTERVAL_TICKS == 0 && world instanceof ServerWorld sw) {
            boolean didHurt = living.damage(ModDamageTypes.thorn(sw), DAMAGE_AMOUNT);
            if (didHurt) {
                sw.playSound(
                        null,
                        pos,
                        SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH,
                        SoundCategory.BLOCKS,
                        0.8f,
                        1.0f
                );
            }
        }
    }
}