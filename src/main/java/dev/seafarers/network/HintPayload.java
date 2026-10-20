package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: show a hint (hint.seafarers.&lt;key&gt;); the client fills in the player's own keys. */
public record HintPayload(String key) implements CustomPacketPayload {
    public static final Type<HintPayload> TYPE = new Type<>(Seafarers.id("hint"));
    public static final StreamCodec<ByteBuf, HintPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(48), HintPayload::key, HintPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
