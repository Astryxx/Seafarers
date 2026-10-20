package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A ship's supply barrel: cannonballs, planks or food, as an ordinary barrel's worth of storage; or the grog
 * barrel, which pours grog into a glass bottle or a bucket.
 * Stock follows the Sloop's numbers on the wiki: 40 cannonballs a barrel, 35 planks, 16 food.
 */
public class SupplyBarrelBlock extends BaseEntityBlock {
    public enum Supply implements StringRepresentable {
        CANNONBALLS(40, 5, () -> ModItems.CANNONBALL.get()),
        PLANKS(35, 5, () -> net.minecraft.world.item.Items.OAK_PLANKS),
        FOOD(16, 1, () -> ModItems.BANANA.get()),
        GROG(0, 0, () -> ModItems.GROG.get());

        public final int capacity;
        public final int grab;
        private final Supplier<Item> item;

        Supply(int capacity, int grab, Supplier<Item> item) {
            this.capacity = capacity;
            this.grab = grab;
            this.item = item;
        }

        public Item item() {
            return this.item.get();
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase();
        }
    }

    public static final MapCodec<SupplyBarrelBlock> CODEC = simpleCodec(SupplyBarrelBlock::new);
    public static final EnumProperty<Supply> SUPPLY = EnumProperty.create("supply", Supply.class);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    public SupplyBarrelBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(SUPPLY, Supply.CANNONBALLS));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SUPPLY);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /**
     * The grog barrel pours grog into what you hold out to it; the others open like a chest. The grog barrel never
     * runs dry (Sea of Thieves' doesn't either).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(SUPPLY) == Supply.GROG) {
            if (!level.isClientSide) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.grog_hint"));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SupplyBarrelBlockEntity barrel) {
            player.openMenu(barrel);
            level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(SUPPLY) != Supply.GROG) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // opens the barrel
        }
        ItemStack filled;
        if (stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
            filled = new ItemStack(ModItems.GROG.get());
        } else if (stack.is(net.minecraft.world.item.Items.BUCKET)) {
            filled = new ItemStack(ModItems.GROG_BUCKET.get());
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            player.setItemInHand(hand, net.minecraft.world.item.ItemUtils.createFilledResult(stack, player, filled));
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 0.7f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof net.minecraft.world.Container c) {
            net.minecraft.world.Containers.dropContents(level, pos, c);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyBarrelBlockEntity(pos, state);
    }
}
