package dev.seafarers.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lantern lashed to the rigging or a beam: it stays put whatever it hangs from (thin decks, rails).
 * DECK: hung under a deck, this block also draws the deck planking it hangs from.
 * LIT: use it with an empty hand to put it out or light it again (a dark ship is harder to spot at night).
 */
public class ShipLanternBlock extends LanternBlock {
    public static final BooleanProperty DECK = BooleanProperty.create("deck");
    public static final BooleanProperty LIT = net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT;
    private static final VoxelShape PLATE = Block.box(0, 12, 0, 16, 16, 16);

    public ShipLanternBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(DECK, false).setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DECK, LIT);
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos,
                                                                  net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean lit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
            level.playSound(null, pos, lit ? net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE : net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1f, lit ? 1.2f : 1f);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape lantern = super.getShape(state, level, pos, ctx);
        return state.getValue(DECK) ? Shapes.or(lantern, PLATE) : lantern;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }
}
