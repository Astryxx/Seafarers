package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: where your crew's ship is (for map mods' waypoints), or that you have none. */
public record ShipPosPayload(boolean has, ResourceLocation dimension, BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<ShipPosPayload> TYPE = new Type<>(Seafarers.id("ship_pos"));
    public static final StreamCodec<ByteBuf, ShipPosPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ShipPosPayload::has, ResourceLocation.STREAM_CODEC, ShipPosPayload::dimension,
            BlockPos.STREAM_CODEC, ShipPosPayload::pos, ByteBufCodecs.stringUtf8(64), ShipPosPayload::name, ShipPosPayload::new);

    public static ShipPosPayload none() {
        return new ShipPosPayload(false, ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
