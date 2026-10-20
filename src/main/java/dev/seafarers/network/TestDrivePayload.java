package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Development only (sent by /seafarers drive, which only exists with debugCommands on): makes the client's player
 * hold forward for some ticks, optionally crouching or jumping, so walking can be tested against the real game
 * without touching anyone's keyboard.
 */
public record TestDrivePayload(int ticks, boolean sneak, boolean jump) implements CustomPacketPayload {
    public static final Type<TestDrivePayload> TYPE = new Type<>(Seafarers.id("test_drive"));
    public static final StreamCodec<ByteBuf, TestDrivePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TestDrivePayload::ticks, ByteBufCodecs.BOOL, TestDrivePayload::sneak,
            ByteBufCodecs.BOOL, TestDrivePayload::jump, TestDrivePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
