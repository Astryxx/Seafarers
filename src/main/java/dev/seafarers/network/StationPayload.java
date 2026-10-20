package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: you now operate the station at pos (helm, cannon, harpoon or oars), or none. */
public record StationPayload(int kind, BlockPos pos) implements CustomPacketPayload {
    public static final int NONE = 0, HELM = 1, CANNON = 2, HARPOON = 3, OARS = 4, ROPE = 5;

    public static final Type<StationPayload> TYPE = new Type<>(Seafarers.id("station"));
    public static final StreamCodec<ByteBuf, StationPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StationPayload::kind,
            BlockPos.STREAM_CODEC, StationPayload::pos,
            StationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
