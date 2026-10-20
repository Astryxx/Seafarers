package dev.seafarers.network;

import dev.seafarers.Seafarers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The voyage table's screen: what's on the table and the ship's state, and what the player does about it. */
public final class VoyagePayloads {

    /**
     * Server to client: open (or refresh) the voyage table's screen. The state is a tag (see VoyageTableBlock.Table
     * #state): proposals (name, by, votes, mine, myVote), say (how many have a say), crew (name, online, aboard, brig,
     * brigVotes, myBrigVote) and ship (name, layout, water, holes, anchored, masts, crewMax, crewSize, captain,
     * youCaptain, locked, canLock, canSwap, swapWait, hasBrig, brigNeed),
     * nearby (name, uuid: players near the table who aren't crew) and canInvite.
     */
    public record State(BlockPos pos, CompoundTag state) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Seafarers.id("voyage_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, State::pos, ByteBufCodecs.COMPOUND_TAG, State::state, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a button on the voyage table's screen. */
    public record Action(BlockPos pos, int action, int index, String text) implements CustomPacketPayload {
        public static final int REFRESH = 0, VOTE = 1, WITHDRAW = 2, PROPOSE_HELD = 3, RENAME = 4, LEAVE_CREW = 5;
        /** Captain's buttons: text is the player's UUID (KICK, PROMOTE, INVITE); index is 1 to lock, 0 to unlock (LOCK). */
        public static final int KICK = 6, PROMOTE = 7, INVITE = 8, LOCK = 9, SWAP = 10;
        /** Any crewmate: a vote to lock the crewmate (text, a UUID) in the brig, or to let them out if they're in it. */
        public static final int BRIG = 11;
        /** Any crewmate: tow her off the rocks to open water nearby (see Refloat). */
        public static final int FREE = 12;
        public static final Type<Action> TYPE = new Type<>(Seafarers.id("voyage_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Action::pos, ByteBufCodecs.VAR_INT, Action::action, ByteBufCodecs.VAR_INT, Action::index,
                ByteBufCodecs.stringUtf8(48), Action::text, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private VoyagePayloads() {}
}
