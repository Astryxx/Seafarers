package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The map table's chart: opened on the client, and what's done on it sent back. */
public final class MapTablePayloads {
    /** Server to client: unroll the chart of the table at pos (the marks and view come with the table's own data). */
    public record Open(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Seafarers.id("map_table_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Open::pos, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client to server, in world coordinates: MARK puts a mark at (x, z), UNMARK rubs out the one nearest (within a
     * reach that grows with the zoom, span), VIEW leaves the chart centred on (x, z) showing span blocks across.
     */
    public record Action(BlockPos pos, int action, double x, double z, float span) implements CustomPacketPayload {
        public static final int MARK = 1, UNMARK = 2, VIEW = 3;
        public static final Type<Action> TYPE = new Type<>(Seafarers.id("map_table_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos, ByteBufCodecs.VAR_INT, Action::action,
                ByteBufCodecs.DOUBLE, Action::x, ByteBufCodecs.DOUBLE, Action::z, ByteBufCodecs.FLOAT, Action::span, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MapTablePayloads() {}
}
