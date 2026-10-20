package dev.seafarers.ship.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Fittings bigger than a block (the map table, the quest table, a cannon on its carriage, the bunk), built like
 * vanilla's beds and doors: one block per cell, each with its PART number. Part 0 is the fitting proper (block
 * entity, the whole model, what you're really using); the others are its other cells, drawn by part 0's model and
 * solid where it is. Using any part uses the fitting; when any part goes, the rest follow (through neighbour shape
 * updates, as a bed's halves do).
 *
 * Part offsets are in model space with the fitting facing north: {east, up, south}, in blocks.
 */
public final class MultiPart {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);

    private MultiPart() {}

    public static Direction facing(BlockState state) {
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }

    /** A part's offset from part 0 for a fitting facing {@code facing} (model space turned clockwise from north). */
    public static BlockPos offset(int[] off, Direction facing) {
        int dx = off[0], dz = off[2];
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < turns; i++) {
            int t = dx;
            dx = -dz;
            dz = t;
        }
        return new BlockPos(dx, off[1], dz);
    }

    /** Where part 0 is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos, int[][] parts) {
        if (!state.hasProperty(PART)) {
            return pos;
        }
        return pos.subtract(offset(parts[state.getValue(PART)], facing(state)));
    }

    /** Whether every other part's cell is free, for placing a fitting with part 0 at the clicked spot. */
    public static boolean canPlace(BlockPlaceContext ctx, Direction facing, int[][] parts) {
        for (int i = 1; i < parts.length; i++) {
            BlockPos q = ctx.getClickedPos().offset(offset(parts[i], facing));
            if (!ctx.getLevel().getBlockState(q).canBeReplaced(ctx) || !ctx.getLevel().isInWorldBounds(q)) {
                return false;
            }
        }
        return true;
    }

    /** After part 0 is placed: puts down the rest. */
    public static void placeRest(Level level, BlockPos origin, BlockState state, int[][] parts) {
        for (int i = 1; i < parts.length; i++) {
            level.setBlock(origin.offset(offset(parts[i], facing(state))), state.setValue(PART, i), 3);
        }
    }

    /**
     * A neighbour changed: if it was one of this part's siblings (or part 0) and it's no longer there, this part
     * goes too. Returns the state this part should become.
     */
    public static BlockState check(BlockState state, LevelAccessor level, BlockPos pos, BlockPos neighborPos, BlockState neighbor,
                                   int[][] parts) {
        if (!state.hasProperty(PART)) {
            return state;
        }
        BlockPos origin = origin(state, pos, parts);
        for (int i = 0; i < parts.length; i++) {
            BlockPos q = origin.offset(offset(parts[i], facing(state)));
            if (q.equals(neighborPos)) {
                boolean intact = neighbor.is(state.getBlock()) && neighbor.getValue(PART) == i && facing(neighbor) == facing(state);
                return intact ? state : Blocks.AIR.defaultBlockState();
            }
        }
        return state;
    }
}
