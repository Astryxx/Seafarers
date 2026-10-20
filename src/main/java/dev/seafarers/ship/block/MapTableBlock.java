package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.Seafarers;
import dev.seafarers.network.MapTablePayloads;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.Ships;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The map table: a blank sea chart, as in Sea of Thieves, with the ship's arrow moving on it as she sails and the
 * crew's marks. Use it to unroll the chart (drag to move it, scroll to zoom, click to mark, right click to rub a
 * mark out); the table shows the chart as it was left. Three blocks across and two deep, in the Sloop's gallery.
 */
public class MapTableBlock extends MultiDecorBlock implements EntityBlock {
    /** Three blocks across and two deep: middle, left, right, then the row in front of them (part 0 is the middle back). */
    public static final int[][] PARTS = {{0, 0, 0}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {-1, 0, -1}, {1, 0, -1}};

    public static final int MAX_MARKS = 20;
    /** How much of the sea the chart shows across when nobody has moved it yet, in blocks. */
    public static final float DEFAULT_SPAN = 512;
    public static final float MIN_SPAN = 48, MAX_SPAN = 16384;

    public MapTableBlock(Properties props) {
        super(props, Block.box(0, 0, 0, 16, 14, 16), PARTS);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(MapTableBlock::new);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        pos = this.origin(state, pos);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof Table table) {
            table.show(server, sp);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isMain(state)) {
            return null;
        }
        return new Table(pos, state);
    }

    public static class Table extends BlockEntity {
        /** The crew's marks, in world x/z. */
        private final List<int[]> marks = new ArrayList<>();
        /** Where the chart was left: its centre (world x/z) and how many blocks it showed across (0: nobody's moved it). */
        private double viewX, viewZ;
        private float viewSpan;

        public Table(BlockPos pos, BlockState state) {
            super(ModBlockEntities.MAP_TABLE.get(), pos, state);
        }

        public List<int[]> marks() {
            return this.marks;
        }

        public boolean viewSet() {
            return this.viewSpan > 0;
        }

        public double viewX() { return this.viewX; }
        public double viewZ() { return this.viewZ; }
        public float viewSpan() { return this.viewSpan > 0 ? this.viewSpan : DEFAULT_SPAN; }

        /** Unrolls the chart on a player's screen. */
        public void show(ServerLevel level, ServerPlayer player) {
            this.changed(); // so they have the marks and the view as they are now
            PacketDistributor.sendToPlayer(player, new MapTablePayloads.Open(this.getBlockPos()));
            var here = Ships.toWorld(level, this.getBlockPos());
            Seafarers.LOGGER.info("PROBE map table shown at {},{} view={} marks={}", (int) here.x, (int) here.z,
                    this.viewSet() ? (int) this.viewX + "," + (int) this.viewZ + "/" + (int) this.viewSpan : "ship", this.marks.size());
        }

        /** A mark put down at world (x, z), or the nearest within reach rubbed out. */
        public void mark(ServerLevel level, ServerPlayer player, double x, double z, boolean remove, float span) {
            if (remove) {
                double reach = Mth.clamp(span, MIN_SPAN, MAX_SPAN) / 30;
                int nearest = -1;
                double best = reach * reach;
                for (int i = 0; i < this.marks.size(); i++) {
                    double dx = this.marks.get(i)[0] - x, dz = this.marks.get(i)[1] - z;
                    if (dx * dx + dz * dz <= best) {
                        best = dx * dx + dz * dz;
                        nearest = i;
                    }
                }
                if (nearest >= 0) {
                    this.marks.remove(nearest);
                }
            } else {
                if (this.marks.size() >= MAX_MARKS) {
                    this.marks.remove(0);
                }
                this.marks.add(new int[]{(int) Math.floor(x), (int) Math.floor(z)});
                dev.seafarers.ship.Feats.award(player, "chart");
                level.playSound(null, this.getBlockPos(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.BLOCKS, 0.6f, 1.2f);
            }
            this.changed();
            Seafarers.LOGGER.info("PROBE map table marks={}", this.marks.size());
        }

        /** Leaves the chart showing this part of the sea, for everyone who looks at the table. */
        public void setView(double x, double z, float span) {
            if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 3e7 || Math.abs(z) > 3e7) {
                return;
            }
            this.viewX = x;
            this.viewZ = z;
            this.viewSpan = Mth.clamp(span, MIN_SPAN, MAX_SPAN);
            this.changed();
        }

        private void changed() {
            this.setChanged();
            if (this.level != null && !this.level.isClientSide) {
                this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        private void write(CompoundTag tag) {
            int[] flat = new int[this.marks.size() * 2];
            for (int i = 0; i < this.marks.size(); i++) {
                flat[2 * i] = this.marks.get(i)[0];
                flat[2 * i + 1] = this.marks.get(i)[1];
            }
            tag.putIntArray("marks", flat);
            tag.putDouble("viewX", this.viewX);
            tag.putDouble("viewZ", this.viewZ);
            tag.putFloat("viewSpan", this.viewSpan);
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            this.write(tag);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.marks.clear();
            int[] flat = tag.getIntArray("marks");
            for (int i = 0; i + 1 < flat.length; i += 2) {
                this.marks.add(new int[]{flat[i], flat[i + 1]});
            }
            this.viewX = tag.getDouble("viewX");
            this.viewZ = tag.getDouble("viewZ");
            this.viewSpan = tag.getFloat("viewSpan");
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            this.write(tag);
            return tag;
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }

    /** Facing used by the client renderer to lay the chart square on the table. */
    public static Direction facing(BlockState state) {
        return state.getValue(FACING);
    }
}
