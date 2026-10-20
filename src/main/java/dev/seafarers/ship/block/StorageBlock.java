package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ship's storage, shaped like Sea of Thieves' (a banded sea chest of 54 slots, a cask of 27): ordinary containers in every
 * other way, so hoppers, loot tables and other mods' storage tools all work with them as with a vanilla chest.
 */
public class StorageBlock extends DecorBlock implements EntityBlock {
    private final boolean cask;

    public StorageBlock(Properties props, VoxelShape shape, boolean cask) {
        super(props, shape);
        this.cask = cask;
    }

    public static StorageBlock chest(Properties props) {
        return new StorageBlock(props, Block.box(2, 0, 3, 14, 12, 13), false);
    }

    public static StorageBlock cask(Properties props) {
        return new StorageBlock(props, Block.box(2, 0, 2, 14, 15, 14), true);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(this.cask ? StorageBlock::cask : StorageBlock::chest);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof Storage storage) {
            player.openMenu(storage);
            level.playSound(null, pos, this.cask ? SoundEvents.BARREL_OPEN : SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 0.9f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof Container c) {
                Containers.dropContents(level, pos, c);
            }
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
        return new Storage(pos, state);
    }

    public static class Storage extends RandomizableContainerBlockEntity {
        private NonNullList<ItemStack> items = NonNullList.withSize(54, ItemStack.EMPTY);

        public Storage(BlockPos pos, BlockState state) {
            super(ModBlockEntities.STORAGE.get(), pos, state);
        }

        @Override
        protected Component getDefaultName() {
            return this.getBlockState().getBlock().getName();
        }

        @Override
        protected NonNullList<ItemStack> getItems() {
            return this.items;
        }

        @Override
        protected void setItems(NonNullList<ItemStack> items) {
            this.items = items;
        }

        @Override
        protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
            return this.chest() ? ChestMenu.sixRows(id, inventory, this) : ChestMenu.threeRows(id, inventory, this);
        }

        /** A sea chest holds a double chest's worth; a cask, a barrel's. */
        private boolean chest() {
            return this.getBlockState().getBlock() instanceof StorageBlock b && !b.cask;
        }

        @Override
        public int getContainerSize() {
            return this.chest() ? 54 : 27;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!this.trySaveLootTable(tag)) {
                ContainerHelper.saveAllItems(tag, this.items, registries);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.items = NonNullList.withSize(54, ItemStack.EMPTY);
            if (!this.tryLoadLootTable(tag)) {
                ContainerHelper.loadAllItems(tag, this.items, registries);
            }
        }
    }
}
