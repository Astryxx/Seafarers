package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, a few times a second while you're aboard: the ship's state for the status panel
 * (dev.seafarers.client.ShipHud). The tag: name, layout, speed (m/s, ahead), wind (degrees from astern to where the
 * wind blows, + to starboard), windFrom (compass name), flood (0..100), holes, fires, anchored, raise (0..100),
 * sinking, wheel (-1..1), mastMax, masts (a list of d deploy, a angle, h health, f fill).
 */
public record ShipHudPayload(CompoundTag state) implements CustomPacketPayload {
    public static final Type<ShipHudPayload> TYPE = new Type<>(Seafarers.id("ship_hud"));
    public static final StreamCodec<ByteBuf, ShipHudPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG, ShipHudPayload::state, ShipHudPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
