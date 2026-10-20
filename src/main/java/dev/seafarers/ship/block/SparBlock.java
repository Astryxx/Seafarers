package dev.seafarers.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A thin spar along its axis, for the bowsprit and other rigging timbers. */
public class SparBlock extends RotatedPillarBlock {
    private static final VoxelShape X = Block.box(0, 5, 5, 16, 11, 11);
    private static final VoxelShape Y = Block.box(5, 0, 5, 11, 16, 11);
    private static final VoxelShape Z = Block.box(5, 5, 0, 11, 11, 16);

    public SparBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(AXIS)) {
            case X -> X;
            case Y -> Y;
            case Z -> Z;
        };
    }
}
