package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The capstan. Use once to let the anchor go; hold use to heave it back up (every crewmate pushing makes it
 * faster). FACING is the bow: the anchor hangs from the bow in front of it. CapstanRenderer turns the drum
 * and draws the cable and anchor.
 */
public class AnchorBlock extends BaseEntityBlock {
    public static final MapCodec<AnchorBlock> CODEC = simpleCodec(AnchorBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public AnchorBlock(Properties props) {
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
        // the model's own boxes (see FittingShapes), turned to face the way the block does
        return FittingShapes.of(this, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), SHAPE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        HelmBlockEntity helm = Ships.helmOf(level, pos);
        if (helm == null) {
            dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.capstan_no_ship"));
        } else if (player instanceof ServerPlayer sp) {
            helm.workAnchor(sp);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Capstan(pos, state);
    }

    /**
     * Where the anchor cable runs (rig data from the layout; defaults are the Sloop's), plus the drum's
     * client-side animation state. Offsets are in blocks from the capstan, toward its FACING.
     */
    public static class Capstan extends dev.seafarers.ship.ShipPart.Base {
        public float angle;
        public float cable = -1;

        public Capstan(BlockPos pos, BlockState state) {
            super(ModBlockEntities.CAPSTAN.get(), pos, state);
        }

        /** The hawse, where the cable leaves the hull. */
        public float hawseForward() { return this.get("hawseForward", 5.65f); }
        public float hawseDown() { return this.get("hawseDown", 1.25f); }
        public float hawseSide() { return this.get("hawseSide", 0f); }
        /** Where the cable crosses the rail on its way to the hawse. */
        public float fairleadForward() { return this.get("fairleadForward", 1.6f); }
        public float fairleadUp() { return this.get("fairleadUp", 0.82f); }
        /** How far the anchor drops below the hawse when down. */
        public float depth() { return this.get("depth", 9f); }
        /** Bars through the capstan's head, as drawn (the helm's capstanPegs says how many crew can heave). */
        public int pegs() { return Math.round(this.get("pegs", 2f)); }
    }
}
