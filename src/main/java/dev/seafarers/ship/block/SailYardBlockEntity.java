package dev.seafarers.ship.block;

import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.ship.ShipPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A mast's yard and square sail. Its size and rigging come from the ship layout (rig data, defaults are the
 * Sloop's); the sail's state (set, angle, fill) lives in the helm, per mast. Offsets are in blocks from this
 * block, which sits one block forward of the mast.
 */
public class SailYardBlockEntity extends ShipPart.Base {
    // smoothed values the renderer eases toward the helm's state
    public float shownDeploy = -1;
    public float shownAngle;
    public float shownFill;
    /** 0 = mast standing, 1 = broken (eased for the animation). */
    public float shownBreak;
    // rigging anchor points, found by scanning the ship now and then (client)
    public int rigScan;
    public final List<BlockPos> pulleys = new ArrayList<>();
    @Nullable
    public BlockPos bowsprit;

    public SailYardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAIL_YARD.get(), pos, state);
    }

    public int mast() {
        return this.getBlockState().getValue(SailYardBlock.MAST);
    }

    /** Half the sail's width across the ship. */
    public float halfWidth() { return this.get("halfWidth", 5.0f); }
    /** How far the fully set sail hangs below the yard. */
    public float height() { return this.get("height", 10.0f); }
    public float yardHalfLength() { return this.get("yardHalfLength", 5.7f); }
    /** How far forward of the mast's centre the sail hangs. */
    public float forward() { return this.get("forward", 0.75f); }
    /** Shrouds: from the crow's nest rim (top/topSpread) down to the rail (drop/spread), relative to this block. */
    public float shroudTop() { return this.get("shroudTop", 2.6f); }
    public float shroudTopSpread() { return this.get("shroudTopSpread", 1.3f); }
    public float shroudDrop() { return this.get("shroudDrop", 12.75f); }
    public float shroudSpread() { return this.get("shroudSpread", 3.25f); }
    /** Where the forestay leaves the mast, above this block; 0 = no forestay. */
    public float masthead() { return this.get("masthead", 8.8f); }
}
