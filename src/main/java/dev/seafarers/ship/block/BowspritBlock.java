package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** The heel of the bowsprit, on top of the stem. The spar itself is drawn rising forward at an angle. */
public class BowspritBlock extends BaseEntityBlock {
    public static final MapCodec<BowspritBlock> CODEC = simpleCodec(BowspritBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    public BowspritBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Heel(pos, state);
    }

    /** The bowsprit's dimensions (rig data from the layout; defaults are the Sloop's). */
    public static class Heel extends dev.seafarers.ship.ShipPart.Base {
        public Heel(BlockPos pos, BlockState state) {
            super(ModBlockEntities.BOWSPRIT.get(), pos, state);
        }

        public float length() { return this.get("length", 9.0f); }
        /** Degrees the spar rises above horizontal. */
        public float raise() { return this.get("raise", 16f); }
        /** Where the bobstay meets the stem, from the heel's pivot: down, and forward. */
        public float bobstayDown() { return this.get("bobstayDown", 1.65f); }
        public float bobstayForward() { return this.get("bobstayForward", 0.55f); }
    }
}
