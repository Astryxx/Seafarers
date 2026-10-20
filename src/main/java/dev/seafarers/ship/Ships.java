package dev.seafarers.ship;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Lookups between blocks in a ship's plot, the ship (Sable sub-level) and its helm (the ship's state). */
public final class Ships {

    /** @return the sub-level whose plot contains this block position, or null if it is in the normal world */
    @Nullable
    public static SubLevel shipAt(Level level, BlockPos plotPos) {
        return Sable.HELPER.getContaining(level, plotPos);
    }

    /** @return the helm of the ship containing this block, or null if it is not on a ship with a helm */
    @Nullable
    public static HelmBlockEntity helmOf(Level level, BlockPos plotPos) {
        SubLevel ship = shipAt(level, plotPos);
        return ship == null ? null : helmOf(ship);
    }

    @Nullable
    public static HelmBlockEntity helmOf(SubLevel ship) {
        for (BlockEntitySubLevelActor actor : ship.getPlot().getBlockEntityActors()) {
            if (actor instanceof HelmBlockEntity helm && !helm.isRemoved()) {
                return helm;
            }
        }
        return null;
    }

    /** Plot-space block center to world space. Blocks that are not on a ship are already in world space. */
    public static Vec3 toWorld(Level level, BlockPos plotPos) {
        return toWorld(level, Vec3.atCenterOf(plotPos));
    }

    public static Vec3 toWorld(Level level, Vec3 plotPoint) {
        SubLevel ship = Sable.HELPER.getContaining(level, plotPoint);
        return ship == null ? plotPoint : ship.logicalPose().transformPosition(plotPoint);
    }

    /** Plot-space direction to world space. */
    public static Vec3 toWorldNormal(Level level, BlockPos plotPos, Vec3 dir) {
        SubLevel ship = shipAt(level, plotPos);
        return ship == null ? dir : ship.logicalPose().transformNormal(dir);
    }

    /** World-space direction into the plot space of the ship containing plotPos. */
    public static Vec3 toPlotNormal(Level level, BlockPos plotPos, Vec3 dir) {
        SubLevel ship = shipAt(level, plotPos);
        return ship == null ? dir : ship.logicalPose().transformNormalInverse(dir);
    }

    /** World velocity (m/s) of the ship at a plot position, or zero off-ship. */
    public static Vec3 velocityAt(Level level, BlockPos plotPos) {
        return Sable.HELPER.getVelocity(level, Vec3.atCenterOf(plotPos));
    }

    private Ships() {}
}
