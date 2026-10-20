package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rowboats wait along the world's coasts, as in Sea of Thieves: when a new stretch of coast is generated, it may
 * get one, tied up in the shallows with her bow to the open sea. They're nobody's: anyone can row them. Ships
 * are sub-levels and can't be built while the world is generating, so new chunks are queued and the rowboats
 * launched a moment later on the server thread.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Rowboats {
    private record Pending(ResourceKey<Level> dimension, ChunkPos chunk) {}

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    private static final int MAX_QUEUE = 512;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level) || !SeafarersConfig.NATURAL_ROWBOATS.get()
                || QUEUE.size() >= MAX_QUEUE || level.random.nextDouble() >= SeafarersConfig.ROWBOAT_CHANCE.get()) {
            return;
        }
        QUEUE.add(new Pending(level.dimension(), event.getChunk().getPos()));
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        for (int i = 0; i < 2 && !QUEUE.isEmpty(); i++) {
            Pending p = QUEUE.poll();
            ServerLevel level = event.getServer().getLevel(p.dimension());
            if (level != null && level.hasChunk(p.chunk().x, p.chunk().z)) {
                place(level, p.chunk());
            }
        }
    }

    /** Ties a rowboat up somewhere along the coast in this chunk, if it has any. Returns her, or null. */
    @Nullable
    public static ServerSubLevel place(ServerLevel level, ChunkPos chunk) {
        return place(level, chunk, level.getSeaLevel());
    }

    /** As place, with the sea at a given height (the test sea isn't at the world's sea level). */
    @Nullable
    public static ServerSubLevel place(ServerLevel level, ChunkPos chunk, int sea) {
        RandomSource rng = level.random;
        // the middle of the chunk only, so she's built entirely in it
        int start = rng.nextInt(4);
        for (int i = 0; i < 64; i++) {
            int x = chunk.getMinBlockX() + 4 + (start + i * 3) % 8;
            int z = chunk.getMinBlockZ() + 4 + (start + i * 5) % 8;
            BlockPos surface = surfaceWater(level, x, z);
            if (surface == null || Math.abs(surface.getY() - (sea - 1)) > 1) {
                continue;
            }
            Direction toLand = land(level, surface);
            if (toLand == null || !openWater(level, surface, toLand.getOpposite())) {
                continue;
            }
            String layout = switch (rng.nextInt(10)) {
                case 0 -> "rowboat_cannon";
                case 1 -> "rowboat_harpoon";
                default -> "rowboat";
            };
            ServerSubLevel boat = ShipBuilder.launch(level, layout, surface, toLand.getOpposite());
            if (boat != null) {
                Seafarers.LOGGER.info("PROBE natural {} at {}", layout, surface.toShortString());
                return boat;
            }
        }
        return null;
    }

    /** The top water block at x, z if the column's surface is water at least two deep there. */
    @Nullable
    private static BlockPos surfaceWater(ServerLevel level, int x, int z) {
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        if (!level.getFluidState(top).isSource() || !level.getFluidState(top).is(FluidTags.WATER)
                || !level.getFluidState(top.below()).is(FluidTags.WATER)) {
            return null;
        }
        return top;
    }

    /** Which way the shore is: dry land at sea level within a few blocks. */
    @Nullable
    private static Direction land(ServerLevel level, BlockPos at) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int dist = 3; dist <= 5; dist++) {
                BlockPos p = at.relative(d, dist);
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()) - 1;
                if (h >= at.getY() && !level.getFluidState(new BlockPos(p.getX(), h, p.getZ())).is(FluidTags.WATER)) {
                    return d;
                }
            }
        }
        return null;
    }

    /** Water ahead, so she isn't wedged in a puddle. */
    private static boolean openWater(ServerLevel level, BlockPos at, Direction ahead) {
        for (int dist = 1; dist <= 6; dist++) {
            if (surfaceWater(level, at.relative(ahead, dist).getX(), at.relative(ahead, dist).getZ()) == null) {
                return false;
            }
        }
        return true;
    }

    private Rowboats() {}
}
