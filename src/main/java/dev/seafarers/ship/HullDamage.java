package dev.seafarers.ship;

import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.block.HullHoleBlock;
import dev.seafarers.ship.block.HullPlanksBlock;
import dev.seafarers.ship.block.VoxelHullBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Holes, patches and flooding for both kinds of hull: voxel hulls and the older full-block planking. */
public final class HullDamage {

    public static boolean isHole(BlockState state) {
        return state.is(ModBlocks.HULL_HOLE.get())
                || (state.is(ModBlocks.VOXEL_HULL.get()) && state.getValue(VoxelHullBlock.HOLE) > 0);
    }

    /** How much a hole leaks: a small one half as much as a large one; 0 if not a hole. */
    public static float holeSize(BlockState state) {
        if (state.is(ModBlocks.HULL_HOLE.get())) {
            return 1f;
        }
        if (state.is(ModBlocks.VOXEL_HULL.get())) {
            return state.getValue(VoxelHullBlock.HOLE) * 0.5f;
        }
        return 0f;
    }

    /** @return true if a hole was made */
    public static boolean punch(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.VOXEL_HULL.get())) {
            // a fresh hit makes a small hole; another hit in the same place widens it
            int hole = state.getValue(VoxelHullBlock.HOLE);
            if (hole >= 2) {
                return false;
            }
            level.setBlock(pos, state.setValue(VoxelHullBlock.HOLE, hole + 1), Block.UPDATE_ALL);
            return true;
        }
        if (state.is(SeafarersTags.HULL)) {
            level.setBlock(pos, HullHoleBlock.holeFor(state), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    public static void patch(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.VOXEL_HULL.get())) {
            level.setBlock(pos, state.setValue(VoxelHullBlock.HOLE, 0), Block.UPDATE_ALL);
        } else if (state.is(ModBlocks.HULL_HOLE.get())) {
            level.setBlock(pos, (state.getValue(HullHoleBlock.BOTTOM) ? ModBlocks.HULL_BOTTOM.get() : ModBlocks.HULL_PLANKS.get())
                    .defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** The darker, waterlogged look of a sinking hull. */
    public static void flood(Level level, BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.VOXEL_HULL.get()) && !state.getValue(VoxelHullBlock.FLOODED)) {
            level.setBlock(pos, state.setValue(VoxelHullBlock.FLOODED, true), Block.UPDATE_CLIENTS);
        } else if (state.getBlock() instanceof HullPlanksBlock && !state.getValue(HullPlanksBlock.FLOODED)) {
            level.setBlock(pos, state.setValue(HullPlanksBlock.FLOODED, true), Block.UPDATE_CLIENTS);
        }
    }

    private HullDamage() {}
}
