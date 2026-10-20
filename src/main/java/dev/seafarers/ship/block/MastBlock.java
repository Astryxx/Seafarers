package dev.seafarers.ship.block;

import dev.seafarers.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The mast. Shots damage it; a broken mast can't hold a sail until it is patched with planks.
 * DECK: where the mast passes through a deck, this block also draws that deck's planking around it.
 */
public class MastBlock extends Block {
    public static final MapCodec<MastBlock> CODEC = simpleCodec(MastBlock::new);
    public static final BooleanProperty DECK = BooleanProperty.create("deck");
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty MAST = ShipProps.MAST;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape DECKED = Shapes.or(SHAPE, Block.box(0, 12, 0, 16, 16, 16));

    public MastBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(DECK, false).setValue(MAST, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DECK, MAST);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(DECK) ? DECKED : SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.PLANKS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        HelmBlockEntity helm = Ships.helmOf(level, pos);
        if (helm == null || !helm.mastDamaged(state.getValue(MAST))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            helm.repairMast(state.getValue(MAST));
            dev.seafarers.ship.Feats.award(player, "mend");
            stack.consume(1, player);
            level.playSound(null, pos, ModSounds.PLANK_HAMMER.get(), SoundSource.BLOCKS, 1f, 0.9f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
