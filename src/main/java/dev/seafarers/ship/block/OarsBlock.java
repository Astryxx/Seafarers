package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rowboat's rowing bench. It is a helm (the same block entity): take the oars, W/S to row, A/D to turn, sneak
 * to let go. HelmRenderer draws the oars working.
 */
public class OarsBlock extends HelmBlock {
    public static final MapCodec<OarsBlock> CODEC = simpleCodec(OarsBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 5, 16, 9, 11);

    public OarsBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // the model's own boxes (see FittingShapes), turned to face the way the block does
        return FittingShapes.of(this, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), SHAPE);
    }
}
