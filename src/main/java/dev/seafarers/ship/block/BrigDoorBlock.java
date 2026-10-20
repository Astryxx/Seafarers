package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.ship.Brig;
import dev.seafarers.ship.Notify;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The brig's barred door. Anyone opens and shuts it by hand while the brig is empty; with one of her crew locked
 * in, it's shut and won't open for anybody until the crew votes them out (see {@link Brig}). Redstone doesn't work
 * it either way.
 */
public class BrigDoorBlock extends DoorBlock {
    public static final MapCodec<BrigDoorBlock> CODEC = simpleCodec(BrigDoorBlock::new);

    public BrigDoorBlock(Properties props) {
        super(BlockSetType.IRON, props);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && Brig.doorLocked(server, pos)) {
            Notify.show(player, Component.translatable("message.seafarers.brig_door_locked").withStyle(ChatFormatting.RED));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.8f);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide) {
            this.setOpen(player, level, state, pos, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Redstone doesn't open the brig. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, BlockPos from, boolean moving) {
    }
}
