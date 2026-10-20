package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class SeafarersTags {
    /** Hull blocks: a cannonball turns them into a hole. */
    public static final TagKey<Block> HULL = TagKey.create(Registries.BLOCK, Seafarers.id("hull"));

    /** Items the voyage table takes as voyages, besides maps with marks on them (empty: for other mods and packs). */
    public static final TagKey<net.minecraft.world.item.Item> VOYAGES = TagKey.create(Registries.ITEM, Seafarers.id("voyages"));

    private SeafarersTags() {}
}
