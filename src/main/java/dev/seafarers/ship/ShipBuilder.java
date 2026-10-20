package dev.seafarers.ship;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModItems;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a ship from its {@link ShipLayout} on the water and turns it into a Sable ship. */
public final class ShipBuilder {
    /** The keel sits this far below the clicked water surface block before the ship settles. */
    private static final int KEEL_DEPTH = 2;

    /**
     * @param waterSurface the top water block under the middle of the ship
     * @return the new ship, or null if there is not enough open water
     */
    @Nullable
    public static ServerSubLevel launch(ServerLevel level, String layoutName, BlockPos waterSurface, Direction bow) {
        ShipLayout layout = ShipLayout.load(level.getServer(), layoutName);
        if (layout == null) {
            return null;
        }
        BlockPos origin = waterSurface.below(KEEL_DEPTH).offset(ShipLayout.offset(0, 0, -layout.length() / 2, bow));

        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        Map<BlockPos, String> loot = new LinkedHashMap<>();
        Map<BlockPos, com.google.gson.JsonObject> partData = new LinkedHashMap<>();
        for (ShipLayout.Cell cell : layout.cells()) {
            BlockPos pos = origin.offset(ShipLayout.offset(cell.x(), cell.y(), cell.z(), bow));
            BlockState state = parse(level, ShipLayout.resolve(cell.state(), bow));
            if (state == null) {
                return null;
            }
            blocks.put(pos, state);
            if (cell.loot() != null) {
                loot.put(pos, cell.loot());
            }
            if (cell.data() != null) {
                partData.put(pos, cell.data());
            }
        }

        // only build where there is water or air, so we never eat the coastline
        int bx0 = Integer.MAX_VALUE, by0 = Integer.MAX_VALUE, bz0 = Integer.MAX_VALUE;
        int bx1 = Integer.MIN_VALUE, by1 = Integer.MIN_VALUE, bz1 = Integer.MIN_VALUE;
        for (BlockPos p : blocks.keySet()) {
            BlockState existing = level.getBlockState(p);
            if (!existing.isAir() && !existing.getFluidState().is(FluidTags.WATER)) {
                return null;
            }
            bx0 = Math.min(bx0, p.getX()); by0 = Math.min(by0, p.getY()); bz0 = Math.min(bz0, p.getZ());
            bx1 = Math.max(bx1, p.getX()); by1 = Math.max(by1, p.getY()); bz1 = Math.max(bz1, p.getZ());
        }
        // nor inside another ship: two hulls launched into each other jam the physics (worst with the big ships)
        if (dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level).queryIntersecting(
                new dev.ryanhcode.sable.companion.math.BoundingBox3d(bx0 - 1, by0 - 1, bz0 - 1, bx1 + 2, by1 + 2, bz1 + 2)).iterator().hasNext()) {
            Seafarers.LOGGER.info("PROBE launch refused: another ship is in the way");
            return null;
        }

        List<BlockPos> wasWater = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            if (level.getFluidState(p).is(FluidTags.WATER)) {
                wasWater.add(p);
            }
            level.setBlock(p, e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        // let stairs round their corners and rails join up, now that every neighbour is in place
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockState s = e.getValue();
            if (s.getBlock() instanceof StairBlock || s.getBlock() instanceof CrossCollisionBlock) {
                BlockState shaped = Block.updateFromNeighbourShapes(s, level, e.getKey());
                if (shaped != s && !shaped.isAir()) {
                    level.setBlock(e.getKey(), shaped, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
        if (dev.seafarers.SeafarersConfig.NEW_SHIPS_STOCKED.get()) {
            for (BlockPos p : blocks.keySet()) {
                if (level.getBlockEntity(p) instanceof dev.seafarers.ship.block.SupplyBarrelBlockEntity barrel) {
                    // a new ship comes with her stores: the layout may say how many ("stock:30"), else the barrel's usual
                    String kind = loot.get(p);
                    barrel.stockUp(kind != null && kind.startsWith("stock:") ? Integer.parseInt(kind.substring(6)) : -1);
                }
            }
            loot.forEach((pos, kind) -> stock(level, pos, kind));
        }
        partData.forEach((pos, data) -> {
            if (level.getBlockEntity(pos) instanceof ShipPart part) {
                part.configure((net.minecraft.nbt.CompoundTag) com.mojang.serialization.JsonOps.INSTANCE
                        .convertTo(net.minecraft.nbt.NbtOps.INSTANCE, data));
            } else {
                Seafarers.LOGGER.warn("Ship layout {}: data for {} which is not a ship part", layoutName, level.getBlockState(pos));
            }
        });

        BlockPos anchor = origin.offset(ShipLayout.offset(0, layout.holdMin(), layout.length() / 2, bow));
        ServerSubLevel ship = SubLevelAssemblyHelper.assembleBlocks(level, anchor, new ArrayList<>(blocks.keySet()),
                new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ));
        ship.setName(layout.name());

        // assembly leaves air where the hull sat in the sea; put the sea back
        for (BlockPos p : wasWater) {
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        // tell the helm about its ship: where the crew respawns and which cells of the hold flood
        BlockPos toPlot = ship.getPlot().getCenterBlock().subtract(anchor);
        HelmBlockEntity helm = Ships.helmOf(ship);
        if (helm != null) {
            BlockPos respawn = origin.offset(ShipLayout.offset(layout.respawn().getX(), layout.respawn().getY(),
                    layout.respawn().getZ(), bow)).offset(toPlot);
            List<BlockPos> bilge = new ArrayList<>();
            for (BlockPos cell : layout.bilge().isEmpty() ? holdCells(layout) : layout.bilge()) {
                bilge.add(origin.offset(ShipLayout.offset(cell.getX(), cell.getY(), cell.getZ(), bow)).offset(toPlot));
            }
            List<BlockPos> dry = new ArrayList<>();
            for (BlockPos cell : layout.dry()) {
                dry.add(origin.offset(ShipLayout.offset(cell.getX(), cell.getY(), cell.getZ(), bow)).offset(toPlot));
            }
            // the exact dry volume, turned with the ship and moved into her plot (cell 0's centre stays put)
            BlockPos base = origin.offset(toPlot);
            Direction starboard = bow.getClockWise();
            float[] mesh = new float[layout.dryMesh().size() * 6];
            int n = 0;
            for (float[] b : layout.dryMesh()) {
                float[] p = new float[6];
                for (int corner = 0; corner < 2; corner++) {
                    float u = b[corner * 3] - 0.5f, y = b[corner * 3 + 1], w = b[corner * 3 + 2] - 0.5f;
                    // relative to base: plot coordinates are around 20 million, beyond a float's whole-block precision
                    p[corner * 3] = starboard.getStepX() * u + bow.getStepX() * w + 0.5f;
                    p[corner * 3 + 1] = y;
                    p[corner * 3 + 2] = starboard.getStepZ() * u + bow.getStepZ() * w + 0.5f;
                }
                for (int a = 0; a < 3; a++) {
                    mesh[n + a] = Math.min(p[a], p[a + 3]);
                    mesh[n + a + 3] = Math.max(p[a], p[a + 3]);
                }
                n += 6;
            }
            List<BlockPos> respawns = new ArrayList<>();
            for (BlockPos cell : layout.respawns()) {
                respawns.add(origin.offset(ShipLayout.offset(cell.getX(), cell.getY(), cell.getZ(), bow)).offset(toPlot));
            }
            List<BlockPos> brig = new ArrayList<>();
            for (BlockPos cell : layout.brig()) {
                brig.add(origin.offset(ShipLayout.offset(cell.getX(), cell.getY(), cell.getZ(), bow)).offset(toPlot));
            }
            helm.initShip(layout.name(), layout.stats(), layout.masts(), respawn, bilge, dry, base, mesh, respawns, brig);
        } else {
            Seafarers.LOGGER.warn("Ship layout {} has no helm", layout.name());
        }
        Seafarers.LOGGER.info("Launched {} {} at {} facing {}", layout.name(), ship.getUniqueId(), waterSurface.toShortString(), bow);
        return ship;
    }

    /**
     * Empty cells of the lower deck that are enclosed by the hull: something solid on both sides along the beam
     * and something below. These are where bilge water shows.
     */
    private static List<BlockPos> holdCells(ShipLayout layout) {
        Set<BlockPos> solid = new HashSet<>();
        for (ShipLayout.Cell c : layout.cells()) {
            solid.add(new BlockPos(c.x(), c.y(), c.z()));
        }
        List<BlockPos> out = new ArrayList<>();
        int length = layout.length();
        for (int y = layout.holdMin(); y <= layout.holdMax(); y++) {
            for (int z = 0; z < length; z++) {
                for (int x = -8; x <= 8; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (solid.contains(p)) {
                        continue;
                    }
                    if (hasSolid(solid, x, y, z, -1, 0) && hasSolid(solid, x, y, z, 1, 0) && hasSolidBelow(solid, x, y, z)) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    private static boolean hasSolid(Set<BlockPos> solid, int x, int y, int z, int dx, int dz) {
        for (int i = 1; i <= 8; i++) {
            if (solid.contains(new BlockPos(x + dx * i, y, z + dz * i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSolidBelow(Set<BlockPos> solid, int x, int y, int z) {
        for (int i = 1; i <= 4; i++) {
            if (solid.contains(new BlockPos(x, y - i, z))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockState parse(ServerLevel level, String state) {
        try {
            return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), state, false).blockState();
        } catch (CommandSyntaxException e) {
            Seafarers.LOGGER.error("Bad block state in ship layout: {}", state, e);
            return null;
        }
    }

    /** Fills a ship chest. "ammo" is the special-ammunition chest. */
    private static void stock(ServerLevel level, BlockPos pos, String kind) {
        if (level.getBlockEntity(pos) instanceof Container chest && "ammo".equals(kind)) {
            chest.setItem(0, new ItemStack(ModItems.CHAIN_SHOT.get(), 8));
            chest.setItem(1, new ItemStack(ModItems.CANNONBALL.get(), 10));
        }
    }

    private ShipBuilder() {}
}
