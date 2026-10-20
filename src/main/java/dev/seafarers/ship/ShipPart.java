package dev.seafarers.ship;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity whose dimensions come from the ship layout (sail size, bowsprit length, where the anchor
 * cable runs): the layout cell's "data" object is passed to {@link #configure} at launch. The settings are
 * saved and sent to clients, which draw the part from them.
 */
public interface ShipPart {
    void configure(CompoundTag data);

    CompoundTag rig();

    /** Shared save/load/sync for parts that keep nothing else. */
    abstract class Base extends BlockEntity implements ShipPart {
        protected CompoundTag rig = new CompoundTag();

        protected Base(net.minecraft.world.level.block.entity.BlockEntityType<?> type, net.minecraft.core.BlockPos pos,
                       net.minecraft.world.level.block.state.BlockState state) {
            super(type, pos, state);
        }

        @Override
        public void configure(CompoundTag data) {
            this.rig = data.copy();
            this.setChanged();
        }

        @Override
        public CompoundTag rig() {
            return this.rig;
        }

        protected float get(String key, float fallback) {
            return this.rig.contains(key) ? this.rig.getFloat(key) : fallback;
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.put("rig", this.rig);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.rig = tag.getCompound("rig");
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
