package dev.seafarers.client;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hull paint, client side. Block colours are worked out on the chunk-building threads, which mustn't touch the
 * ships themselves, so each ship's plot area and paint colour are kept here, refreshed from its helm on the
 * render thread; a change rebuilds the ship's blocks.
 */
public final class Liveries {
    private record Area(int x0, int y0, int z0, int x1, int y1, int z1, int color) {
        boolean contains(BlockPos p) {
            return p.getX() >= this.x0 && p.getX() <= this.x1 && p.getY() >= this.y0 && p.getY() <= this.y1
                    && p.getZ() >= this.z0 && p.getZ() <= this.z1;
        }
    }

    private static final Map<BlockPos, Area> AREAS = new ConcurrentHashMap<>();

    /** Called each frame the helm is drawn: picks up a new paint colour (or a ship that's just appeared). */
    public static void sync(HelmBlockEntity helm) {
        Area known = AREAS.get(helm.getBlockPos());
        if (known != null && known.color == helm.hullColor()) {
            return;
        }
        var ship = Ships.shipAt(helm.getLevel(), helm.getBlockPos());
        if (ship == null) {
            return;
        }
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        AREAS.put(helm.getBlockPos().immutable(), new Area(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), helm.hullColor()));
        if (known != null || helm.hullColor() != 0xFFFFFF) {
            Minecraft.getInstance().levelRenderer.setBlocksDirty(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
        }
    }

    public static void forget(BlockPos helm) {
        AREAS.remove(helm);
    }

    /** The paint on the hull at a plot position (white: bare wood). */
    public static int hull(BlockPos pos) {
        for (Area a : AREAS.values()) {
            if (a.contains(pos)) {
                return a.color;
            }
        }
        return 0xFFFFFF;
    }

    private Liveries() {}
}
