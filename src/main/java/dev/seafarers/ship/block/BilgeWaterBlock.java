package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Water sloshing in the lower deck of a flooding ship. Purely visual: the helm places and lowers it from the
 * ship's flood level, so the crew can see how bad it is. Wading through it slows you down.
 */
public class BilgeWaterBlock extends Block {
    public static final MapCodec<BilgeWaterBlock> CODEC = simpleCodec(BilgeWaterBlock::new);
    /** Height of the water surface in eighths of a block. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 8);

    public BilgeWaterBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 8));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Block.box(0, 0, 0, 16, state.getValue(LEVEL) * 2, 16);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity.getY() < pos.getY() + state.getValue(LEVEL) / 8.0) {
            entity.makeStuckInBlock(state, new Vec3(0.75, 0.9, 0.75));
        }
    }
}
