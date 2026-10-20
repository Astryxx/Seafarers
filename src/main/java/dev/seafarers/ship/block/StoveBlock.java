package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.registry.ModItems;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The galley stove, cooked on the way Sea of Thieves does it: put one piece of food on, watch the smoke, take it
 * off in time. Too soon and it's still raw; leave it and it burns. Anything with a smoker or campfire recipe
 * cooks here (vanilla's and other mods'), taking three times its smoker time to cook and as long again to burn.
 */
public class StoveBlock extends BaseEntityBlock {
    public static final MapCodec<StoveBlock> CODEC = simpleCodec(StoveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 14, 15);
    /** Cooking time as a multiple of the recipe's smoker time. */
    /** Test override for the config's stoveCookScale (0: use the config). */
    public static double COOK_SCALE = 0;

    public StoveBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        // the model's own boxes (see FittingShapes), turned to face the way the block does
        return FittingShapes.of(this, state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING), SHAPE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Stove stove) || !stove.food.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (Stove.recipe(level, stack).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            stove.put(stack.copyWithCount(1));
            stack.consume(1, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Stove stove) || stove.food.isEmpty()) {
            if (!level.isClientSide) {
                dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.stove_hint"));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            ItemStack out = stove.take();
            if (!player.addItem(out)) {
                player.drop(out, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Stove stove && !stove.food.isEmpty()) {
            Block.popResource(level, pos, stove.take());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Stove(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.STOVE.get(), (l, p, s, stove) -> {
            if (Ships.shipAt(l, p) == null) {
                stove.serverTick();
            }
        });
    }

    /** Raw, undercooked, cooked or burnt, by how long it has been on. */
    public enum Doneness { RAW, UNDERCOOKED, COOKED, BURNT }

    public static class Stove extends BlockEntity implements BlockEntitySubLevelActor {
        private ItemStack food = ItemStack.EMPTY;
        private ItemStack result = ItemStack.EMPTY;
        private int time;
        private int cookTime = 300;

        public Stove(BlockPos pos, BlockState state) {
            super(ModBlockEntities.STOVE.get(), pos, state);
        }

        /** What the food turns into once cooked (the smoker's recipe), known on both sides. */
        public ItemStack result() {
            return this.result;
        }

        public ItemStack food() {
            return this.food;
        }

        public int time() {
            return this.time;
        }

        public Doneness doneness() {
            if (this.time >= 2 * this.cookTime) return Doneness.BURNT;
            if (this.time >= this.cookTime) return Doneness.COOKED;
            if (this.time >= this.cookTime / 2) return Doneness.UNDERCOOKED;
            return Doneness.RAW;
        }

        /**
         * How to cook this, if it's food: a smoker or campfire recipe (vanilla's and any mod's), or a furnace recipe
         * that makes food (some mods only give their raw foods those). Anything that isn't food, or doesn't cook into
         * food, isn't accepted.
         */
        static Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe(Level level, ItemStack stack) {
            SingleRecipeInput in = new SingleRecipeInput(stack);
            var rm = level.getRecipeManager();
            for (RecipeType<? extends AbstractCookingRecipe> type : java.util.List.<RecipeType<? extends AbstractCookingRecipe>>of(
                    RecipeType.SMOKING, RecipeType.CAMPFIRE_COOKING, RecipeType.SMELTING)) {
                @SuppressWarnings("unchecked")
                Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> r =
                        rm.getRecipeFor((RecipeType<AbstractCookingRecipe>) type, in, level);
                if (r.isPresent() && r.get().value().getResultItem(level.registryAccess()).has(net.minecraft.core.component.DataComponents.FOOD)) {
                    return r;
                }
            }
            return Optional.empty();
        }

        void put(ItemStack stack) {
            var r = recipe(this.level, stack);
            if (r.isEmpty()) {
                return;
            }
            AbstractCookingRecipe recipe = r.get().value();
            this.food = stack;
            this.result = recipe.getResultItem(this.level.registryAccess()).copy();
            // campfire times are long already; smoker times are short
            int base = recipe.getType() == RecipeType.CAMPFIRE_COOKING ? recipe.getCookingTime() / 3
                    : recipe.getType() == RecipeType.SMELTING ? recipe.getCookingTime() / 2 : recipe.getCookingTime();
            this.cookTime = Math.max(40, (int) (base * (COOK_SCALE > 0 ? COOK_SCALE : SeafarersConfig.STOVE_COOK_SCALE.get())));
            this.time = 0;
            this.level.playSound(null, this.getBlockPos(), dev.seafarers.registry.ModSounds.STOVE_SIZZLE.get(), SoundSource.BLOCKS, 1f, 1.1f);
            this.setLit(true);
            this.sync();
            Seafarers.LOGGER.info("PROBE stove cooking {} for {} ticks", stack.getItem(), this.cookTime);
        }

        /** Takes the food off: raw (or undercooked) food comes back as it went on; cooked comes off cooked; burnt is charred. */
        ItemStack take() {
            Doneness d = this.doneness();
            ItemStack out = switch (d) {
                case RAW, UNDERCOOKED -> this.food;
                case COOKED -> this.result;
                case BURNT -> new ItemStack(ModItems.CHARRED_FOOD.get());
            };
            Seafarers.LOGGER.info("PROBE stove took off {} as {}", d, out.getItem());
            this.food = ItemStack.EMPTY;
            this.result = ItemStack.EMPTY;
            this.time = 0;
            this.setLit(false);
            this.sync();
            return out;
        }

        private void setLit(boolean lit) {
            if (this.level != null && this.getBlockState().getValue(LIT) != lit) {
                BlockPos pos = this.getBlockPos();
                Level level = this.level;
                // the stove may be on a ship mid-tick: change the block once Sable is done with it
                if (level instanceof ServerLevel server) {
                    server.getServer().tell(new net.minecraft.server.TickTask(server.getServer().getTickCount(), () -> {
                        BlockState s = level.getBlockState(pos);
                        if (s.getBlock() instanceof StoveBlock) {
                            level.setBlock(pos, s.setValue(LIT, lit), Block.UPDATE_CLIENTS);
                        }
                    }));
                }
            }
        }

        @Override
        public void sable$tick(ServerSubLevel subLevel) {
            this.serverTick();
        }

        public void serverTick() {
            if (!(this.level instanceof ServerLevel level) || this.food.isEmpty()) {
                return;
            }
            Doneness before = this.doneness();
            this.time++;
            Doneness now = this.doneness();
            if (now != before) {
                this.sync(); // the food in the pan changes as it cooks (and burns)
            }
            Vec3 top = Ships.toWorld(level, Vec3.atBottomCenterOf(this.getBlockPos()).add(0, 0.95, 0));
            // the smoke tells the cook how it's going: thin, then a white plume when done, then black
            if (level.getGameTime() % 8 == 0) {
                switch (now) {
                    case RAW, UNDERCOOKED -> level.sendParticles(ParticleTypes.SMOKE, top.x, top.y, top.z, 2, 0.15, 0.05, 0.15, 0.01);
                    case COOKED -> level.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 2, 0.1, 0.05, 0.1, 0.02);
                    case BURNT -> level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y, top.z, 3, 0.15, 0.05, 0.15, 0.02);
                }
            }
            if (level.getGameTime() % 40 == 0) {
                level.playSound(null, top.x, top.y, top.z, dev.seafarers.registry.ModSounds.STOVE_SIZZLE.get(), SoundSource.BLOCKS, 0.6f, 1f);
            }
            if (now != before) {
                if (now == Doneness.COOKED) {
                    level.playSound(null, top.x, top.y, top.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.8f);
                } else if (now == Doneness.BURNT) {
                    level.playSound(null, top.x, top.y, top.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.6f);
                }
                this.sync();
            }
            this.setChanged();
        }

        private void sync() {
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            if (!this.food.isEmpty()) {
                tag.put("food", this.food.save(registries));
                tag.put("result", this.result.save(registries));
            }
            tag.putInt("time", this.time);
            tag.putInt("cookTime", this.cookTime);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.food = tag.contains("food") ? ItemStack.parseOptional(registries, tag.getCompound("food")) : ItemStack.EMPTY;
            this.result = tag.contains("result") ? ItemStack.parseOptional(registries, tag.getCompound("result")) : ItemStack.EMPTY;
            this.time = tag.getInt("time");
            this.cookTime = tag.contains("cookTime") ? tag.getInt("cookTime") : 300;
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            return this.saveWithoutMetadata(registries);
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
