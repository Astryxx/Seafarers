package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The rowboat's bow post and hook: use it next to another ship's ladder to dock there; use it again to cast off. */
public class RowboatHookBlock extends DecorBlock {
    /** How close the hook must be to a ladder's bottom rung to dock. */
    public static double RANGE = 5.0;

    public RowboatHookBlock(Properties props) {
        super(props, Block.box(6, 0, 6, 10, 14, 10));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(RowboatHookBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            HelmBlockEntity helm = Ships.helmOf(server, pos);
            if (helm != null) {
                helm.toggleDock(server, pos, sp, RANGE);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
