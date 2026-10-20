package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bowsprit to stand on: the spar is drawn by the heel's renderer, rising forward; these invisible blocks along
 * it give it a narrow top to walk out on, each at the spar's height where it passes through (TOP, in sixteenths).
 */
public class BowspritSparBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<BowspritSparBlock> CODEC = simpleCodec(BowspritSparBlock::new);
    public static final IntegerProperty TOP = IntegerProperty.create("top", 1, 16);
    private static final VoxelShape[] ALONG_Z = new VoxelShape[17];
    private static final VoxelShape[] ALONG_X = new VoxelShape[17];

    static {
        for (int t = 1; t <= 16; t++) {
            int bottom = Math.max(0, t - 5);
            ALONG_Z[t] = Block.box(5, bottom, 0, 11, t, 16);
            ALONG_X[t] = Block.box(0, bottom, 5, 16, t, 11);
        }
    }

    public BowspritSparBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TOP, 8));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TOP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int t = state.getValue(TOP);
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z[t] : ALONG_X[t];
    }
}
