package dev.seafarers.ship.block;

import dev.seafarers.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A supply barrel's contents: an ordinary 27-slot container (hoppers, comparators and storage mods all work with
 * it). A ship's barrels are filled with their supplies when she's launched; a barrel placed by hand starts empty.
 */
public class SupplyBarrelBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public SupplyBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPLY_BARREL.get(), pos, state);
    }

    private SupplyBarrelBlock.Supply supply() {
        return this.getBlockState().getValue(SupplyBarrelBlock.SUPPLY);
    }

    /** Fills the barrel with a new ship's stock of its supply (what's already in it stays). */
    public void stockUp() {
        this.stockUp(-1);
    }

    /** Puts {@code amount} of its supply in (-1: the supply's usual stock, which the layout can change per barrel). */
    public void stockUp(int amount) {
        SupplyBarrelBlock.Supply supply = this.supply();
        int left = amount >= 0 ? amount : supply.capacity;
        if (left <= 0) {
            return;
        }
        for (int i = 0; i < this.items.size() && left > 0; i++) {
            if (this.items.get(i).isEmpty()) {
                ItemStack stack = new ItemStack(supply.item(), Math.min(left, supply.item().getDefaultMaxStackSize()));
                this.items.set(i, stack);
                left -= stack.getCount();
            }
        }
        this.setChanged();
    }

    /** How many of its own supply the barrel holds. */
    public int stock() {
        int n = 0;
        for (ItemStack s : this.items) {
            if (s.is(this.supply().item())) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.seafarers.supply_barrel." + this.supply().getSerializedName());
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
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return 27;
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
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, this.items, registries);
        }
    }
}
