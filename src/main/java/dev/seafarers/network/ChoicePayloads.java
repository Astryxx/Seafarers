package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The ship choice screen: shown to a new player (or one choosing again, or a captain swapping), and their pick. */
public final class ChoicePayloads {
    /** A player's first visit: the screen can't be closed until they choose. */
    public static final int FIRST = 0;
    /** Choosing a ship later (/crew choose). */
    public static final int LATER = 1;
    /** A captain swapping their ship for another. */
    public static final int SWAP = 2;
    /** Close the screen if it's open (the pick went through, whichever way it was made). */
    public static final int CLOSE = 3;

    /**
     * Server to client: open the screen. {@code current} is the layout of the ship being swapped (else empty);
     * {@code waitSeconds} is how long until a new ship can be had (0 if now); {@code crews} is the crew size of each
     * ship on the screen (sloop, brigantine, galleon), as this server has them.
     */
    public record Open(int mode, boolean offerCrew, java.util.List<Integer> crews, String current, int waitSeconds) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(Seafarers.id("choice_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Open::mode, ByteBufCodecs.BOOL, Open::offerCrew,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Open::crews,
                ByteBufCodecs.stringUtf8(32), Open::current, ByteBufCodecs.VAR_INT, Open::waitSeconds, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the player's pick, a ship layout ("sloop") or "crew". */
    public record Pick(int mode, String choice) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(Seafarers.id("choice_pick"));
        public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pick::mode, ByteBufCodecs.stringUtf8(32), Pick::choice, Pick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private ChoicePayloads() {}
}
