package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The flag box: use it with a banner to hoist that banner at the masthead (the one flying comes back to you);
 * use it with an empty hand to strike the flag and take it back.
 */
public class FlagBoxBlock extends DecorBlock {
    public FlagBoxBlock(Properties props) {
        super(props, Block.box(2, 0, 3, 14, 9, 13));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FlagBoxBlock::new);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BannerItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            FlagBlock.Flag flag = flagOf(server, pos);
            if (flag == null) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.no_flagstaff"));
                return ItemInteractionResult.FAIL;
            }
            ItemStack old = flag.hoist(stack);
            stack.consume(1, player);
            if (!old.isEmpty() && !player.addItem(old)) {
                player.drop(old, false);
            }
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 0.8f);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.flag_raised", flag.banner().getHoverName()));
            Seafarers.LOGGER.info("PROBE flag raised {}", flag.banner().getItem());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            FlagBlock.Flag flag = flagOf(server, pos);
            if (flag == null || flag.banner().isEmpty()) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.flag_hint"));
                return InteractionResult.CONSUME;
            }
            ItemStack old = flag.hoist(ItemStack.EMPTY);
            if (!player.addItem(old)) {
                player.drop(old, false);
            }
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.flag_lowered"));
            Seafarers.LOGGER.info("PROBE flag lowered");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The flagstaff of the ship this box is on (the highest, if there are several). */
    @Nullable
    public static FlagBlock.Flag flagOf(ServerLevel level, BlockPos box) {
        SubLevel ship = Ships.shipAt(level, box);
        if (ship == null) {
            return null;
        }
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        FlagBlock.Flag best = null;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = b.maxY(); y >= b.minY() && best == null; y--) {
            for (int x = b.minX(); x <= b.maxX() && best == null; x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    if (level.getBlockState(p.set(x, y, z)).is(ModBlocks.FLAG.get()) && level.getBlockEntity(p) instanceof FlagBlock.Flag flag) {
                        best = flag;
                        break;
                    }
                }
            }
        }
        return best;
    }
}
