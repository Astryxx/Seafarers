package dev.seafarers.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** A fitting several blocks big (see {@link MultiPart}): the map table, the quest table, the bunk. */
public abstract class MultiDecorBlock extends DecorBlock {
    protected final int[][] parts;

    protected MultiDecorBlock(Properties props, VoxelShape shape, int[][] parts) {
        super(props, shape);
        this.parts = parts;
        this.registerDefaultState(this.defaultBlockState().setValue(MultiPart.PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MultiPart.PART);
    }

    /** Part 0's position, from any part: what to use, and where the block entity is. */
    protected BlockPos origin(BlockState state, BlockPos pos) {
        return MultiPart.origin(state, pos, this.parts);
    }

    protected static boolean isMain(BlockState state) {
        return state.getValue(MultiPart.PART) == 0;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state = super.getStateForPlacement(ctx);
        return state != null && MultiPart.canPlace(ctx, state.getValue(FACING), this.parts) ? state : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            MultiPart.placeRest(level, pos, state, this.parts);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return MultiPart.check(state, level, pos, neighborPos, neighbor, this.parts);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FittingShapes.of(this, state, this.shape);
    }
}
