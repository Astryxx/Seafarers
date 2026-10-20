package dev.seafarers.ship.block;

import dev.seafarers.registry.ModSounds;
import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A hole knocked in the hull. Patch it by using planks on it; it turns back into the hull it was
 * (tarred bottom planking or the upper hull).
 */
public class HullHoleBlock extends Block {
    public static final MapCodec<HullHoleBlock> CODEC = simpleCodec(HullHoleBlock::new);
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");

    public HullHoleBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(BOTTOM, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOTTOM);
    }

    public static BlockState holeFor(BlockState hull) {
        return ModBlocks.HULL_HOLE.get().defaultBlockState().setValue(BOTTOM, hull.is(ModBlocks.HULL_BOTTOM.get())
                || hull.is(ModBlocks.HULL_BOTTOM_STAIRS.get()));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ItemTags.PLANKS)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            Block hull = state.getValue(BOTTOM) ? ModBlocks.HULL_BOTTOM.get() : ModBlocks.HULL_PLANKS.get();
            level.setBlock(pos, hull.defaultBlockState(), 3);
            level.playSound(null, pos, ModSounds.PLANK_HAMMER.get(), SoundSource.BLOCKS, 1f, 1f);
            level.playSound(null, pos, SoundEvents.ANVIL_STEP, SoundSource.BLOCKS, 0.5f, 1.4f);
            server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.1);
            stack.consume(1, player);
            dev.seafarers.ship.Feats.award(player, "patch");
            HelmBlockEntity helm = Ships.helmOf(level, pos);
            if (helm != null) {
                helm.forceRescan();
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
