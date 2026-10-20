package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.registry.ModItems;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.item.ShipBucketItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The water barrel: holds water like a cauldron (three levels: a bucket fills or empties all of it, a bottle one
 * level, a ship's bucket one level for throwing on fires) and slowly fills itself again (rain off the sails, as far
 * as anyone asks). How fast is in the server config.
 */
public class WaterBarrelBlock extends DecorBlock implements EntityBlock {
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);

    public WaterBarrelBlock(Properties props) {
        super(props, Block.box(1, 0, 1, 15, 15, 15));
        this.registerDefaultState(this.defaultBlockState().setValue(LEVEL, 3));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(WaterBarrelBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int water = state.getValue(LEVEL);
        ItemStack result = null;
        int next = water;
        if (stack.is(Items.BUCKET) && water == 3) {
            result = new ItemStack(Items.WATER_BUCKET);
            next = 0;
        } else if (stack.is(Items.WATER_BUCKET) && water < 3) {
            result = new ItemStack(Items.BUCKET);
            next = 3;
        } else if (stack.is(Items.GLASS_BOTTLE) && water > 0) {
            result = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            next = water - 1;
        } else if (stack.is(Items.POTION) && water < 3
                && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            result = new ItemStack(Items.GLASS_BOTTLE);
            next = water + 1;
        } else if (stack.getItem() instanceof ShipBucketItem bucket && bucket.content() == ShipBucketItem.Content.EMPTY && water > 0) {
            result = new ItemStack(ModItems.SHIP_BUCKET_WATER.get());
            next = water - 1;
        }
        if (result == null) {
            if (!level.isClientSide && (stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE) || stack.getItem() instanceof ShipBucketItem)) {
                dev.seafarers.ship.Notify.show(player, Component.translatable(water == 0 ? "message.seafarers.water_barrel_empty"
                        : "message.seafarers.water_barrel_level", water));
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            boolean filling = next > water;
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
            level.setBlock(pos, state.setValue(LEVEL, next), Block.UPDATE_ALL);
            level.playSound(null, pos, filling ? SoundEvents.BUCKET_EMPTY : SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            int water = state.getValue(LEVEL);
            dev.seafarers.ship.Notify.show(player, Component.translatable(water == 0 ? "message.seafarers.water_barrel_empty"
                    : "message.seafarers.water_barrel_level", water));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(LEVEL) * 5;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Barrel(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.WATER_BARREL.get() ? null
                : (l, p, s, be) -> {
                    if (Ships.shipAt(l, p) == null) {
                        ((Barrel) be).serverTick();
                    }
                };
    }

    /** Counts toward the next level of water. */
    public static class Barrel extends BlockEntity implements BlockEntitySubLevelActor {
        private int ticks;

        public Barrel(BlockPos pos, BlockState state) {
            super(ModBlockEntities.WATER_BARREL.get(), pos, state);
        }

        @Override
        public void sable$tick(ServerSubLevel subLevel) {
            this.serverTick();
        }

        public void serverTick() {
            if (!(this.level instanceof ServerLevel level) || this.getBlockState().getValue(LEVEL) >= 3) {
                this.ticks = 0;
                return;
            }
            if (++this.ticks < SeafarersConfig.WATER_BARREL_REFILL_SECONDS.get() * 20) {
                return;
            }
            this.ticks = 0;
            BlockPos pos = this.getBlockPos();
            // the block may be on a ship mid-tick: change it once Sable is done with her block entities
            level.getServer().tell(new TickTask(level.getServer().getTickCount(), () -> {
                BlockState s = level.getBlockState(pos);
                if (s.getBlock() instanceof WaterBarrelBlock && s.getValue(LEVEL) < 3) {
                    level.setBlock(pos, s.setValue(LEVEL, s.getValue(LEVEL) + 1), Block.UPDATE_CLIENTS);
                }
            }));
            this.setChanged();
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt("ticks", this.ticks);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.ticks = tag.getInt("ticks");
        }
    }
}
