package dev.seafarers.ship.deed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

/**
 * A player's own deed, from the ship choice screen (deeds from the Creative menu have none of this). {@code id}
 * matches the player's entry in {@link Sailors}: when a lost deed is reclaimed the entry gets a new id and the old
 * deed stops working. {@code replaces} is the ship it was swapped for, whose crew moves to the new ship.
 */
public record Deed(UUID id, UUID owner, String ownerName, Optional<UUID> replaces) {
    public static final Codec<Deed> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Deed::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Deed::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(Deed::ownerName),
            UUIDUtil.CODEC.optionalFieldOf("replaces").forGetter(Deed::replaces)
    ).apply(i, Deed::new));
    public static final StreamCodec<ByteBuf, Deed> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Deed::id, UUIDUtil.STREAM_CODEC, Deed::owner, ByteBufCodecs.STRING_UTF8, Deed::ownerName,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Deed::replaces, Deed::new);
}
