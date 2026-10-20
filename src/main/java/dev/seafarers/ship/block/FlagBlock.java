package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.Wind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The masthead flagstaff. With nothing hoisted it flies the ship's pennant (part of the block model); a banner
 * hoisted from the flag box flies in its place, streaming downwind (FlagRenderer). Any banner works, with any
 * patterns, from vanilla or other mods.
 */
public class FlagBlock extends DecorBlock implements EntityBlock {
    /** A banner is hoisted: the model shows only the staff. */
    public static final BooleanProperty FLYING = BooleanProperty.create("flying");

    public FlagBlock(Properties props) {
        super(props, Block.box(7, 0, 7, 9, 16, 9));
        this.registerDefaultState(this.defaultBlockState().setValue(FLYING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(FlagBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FLYING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Flag(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != ModBlockEntities.FLAG.get() ? null
                : (l, p, s, be) -> {
                    if (Ships.shipAt(l, p) == null) {
                        ((Flag) be).serverTick();
                    }
                };
    }

    /** What flies from the staff, and which way the wind blows it (degrees, in the block's own frame). */
    public static class Flag extends BlockEntity implements BlockEntitySubLevelActor {
        private ItemStack banner = ItemStack.EMPTY;
        private float stream;

        public Flag(BlockPos pos, BlockState state) {
            super(ModBlockEntities.FLAG.get(), pos, state);
        }

        public ItemStack banner() {
            return this.banner;
        }

        public float stream() {
            return this.stream;
        }

        /** Hoists a banner (one item) and returns whatever flew before. */
        public ItemStack hoist(ItemStack banner) {
            ItemStack old = this.banner;
            this.banner = banner.copyWithCount(1);
            if (this.level != null) {
                this.level.setBlock(this.getBlockPos(), this.getBlockState().setValue(FLYING, !this.banner.isEmpty()), Block.UPDATE_ALL);
                this.sync();
            }
            return old;
        }

        @Override
        public void sable$tick(ServerSubLevel subLevel) {
            this.serverTick();
        }

        /** Every couple of seconds: turn with the wind, as seen from aboard (the ship turns under it). */
        public void serverTick() {
            if (!(this.level instanceof ServerLevel level) || level.getGameTime() % 40 != Math.floorMod(this.getBlockPos().hashCode(), 40)) {
                return;
            }
            Vec3 down = Wind.get(level).direction(); // where the wind blows toward
            Vec3 local = Ships.toPlotNormal(level, this.getBlockPos(), down);
            float yaw = (float) Math.toDegrees(Math.atan2(-local.z, local.x));
            if (Math.abs(Mth.wrapDegrees(yaw - this.stream)) > 4) {
                this.stream = yaw;
                this.sync();
            }
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
            if (!this.banner.isEmpty()) {
                tag.put("banner", this.banner.save(registries));
            }
            tag.putFloat("stream", this.stream);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.banner = tag.contains("banner") ? ItemStack.parseOptional(registries, tag.getCompound("banner")) : ItemStack.EMPTY;
            this.stream = tag.getFloat("stream");
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
