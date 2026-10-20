package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.ship.ShipBeds;
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

/** A ship's bunk: use it to make it your respawn point aboard, and at night to sleep in (see ShipBeds). Either end works. */
public class MattressBlock extends MultiDecorBlock {
    /** A bunk two blocks long: the head (pillow) end, then the foot. */
    public static final int[][] PARTS = {{0, 0, 0}, {0, 0, 1}};

    public MattressBlock(Properties props) {
        super(props, Block.box(0, 0, 0, 16, 9, 16), PARTS);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(MattressBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            ShipBeds.use(server, this.origin(state, pos), sp);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
