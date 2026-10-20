package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, from whoever operates a station: steer is A/D (-1 port .. +1 starboard), forward is W/S
 * (+1 .. -1), fire is a left click, letGo is sneak.
 */
public record StationInputPayload(BlockPos pos, float steer, float forward, boolean fire, boolean letGo) implements CustomPacketPayload {
    public static final Type<StationInputPayload> TYPE = new Type<>(Seafarers.id("station_input"));
    public static final StreamCodec<ByteBuf, StationInputPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StationInputPayload::pos,
            ByteBufCodecs.FLOAT, StationInputPayload::steer,
            ByteBufCodecs.FLOAT, StationInputPayload::forward,
            ByteBufCodecs.BOOL, StationInputPayload::fire,
            ByteBufCodecs.BOOL, StationInputPayload::letGo,
            StationInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
