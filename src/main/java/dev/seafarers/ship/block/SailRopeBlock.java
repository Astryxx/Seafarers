package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rope controls on the deck. A player uses one to take hold of it (see RopeStations): at the length cleat (HOIST)
 * W lets the sail down and S hauls it up; at an angle pulley (BRACE) A and D swing the sail round.
 * (The test harness's stand-in player still pulls once per use: HOIST lets the sail down a step, sneak-use hauls it
 * up; BRACE swings the sail toward the side its FACING points at, out over the rail.)
 */
public class SailRopeBlock extends HorizontalDirectionalBlock {
    public enum Kind { HOIST, BRACE }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty MAST = ShipProps.MAST;
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    private final Kind kind;

    public SailRopeBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MAST, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new SailRopeBlock(this.kind, p));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MAST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // the model's own boxes (see FittingShapes), turned to face the way the block does
        return FittingShapes.of(this, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), SHAPE);
    }

    /** A dye on a sail rope dyes the ship's sails. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.DyeItem dye)) {
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        HelmBlockEntity helm = level.isClientSide ? null : Ships.helmOf(level, pos);
        if (helm != null) {
            helm.paint(dye.getDyeColor(), true, player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
            stack.consume(1, player);
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        HelmBlockEntity helm = Ships.helmOf(level, pos);
        if (helm == null) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.rope_no_ship"));
            return InteractionResult.CONSUME;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            if (!dev.seafarers.ship.RopeStations.holding(sp, pos)) {
                dev.seafarers.ship.RopeStations.take(sp, pos, state);
            }
            return InteractionResult.CONSUME;
        }
        if (this.kind == Kind.HOIST) {
            helm.changeSails(state.getValue(MAST), player.isShiftKeyDown() ? -1 : 1,
                    player instanceof net.minecraft.server.level.ServerPlayer sp ? sp : null);
        } else {
            helm.braceSails(state.getValue(MAST), sideOf(state.getValue(FACING), helm));
        }
        return InteractionResult.CONSUME;
    }

    public Kind kind() {
        return this.kind;
    }

    /** +1 if the rope faces the starboard (right-hand) side of the ship, -1 for port. */
    private static int sideOf(Direction ropeFacing, HelmBlockEntity helm) {
        Direction bow = helm.getBlockState().getValue(HelmBlock.FACING);
        return ropeFacing == bow.getClockWise() ? 1 : ropeFacing == bow.getCounterClockWise() ? -1 : 0;
    }
}
