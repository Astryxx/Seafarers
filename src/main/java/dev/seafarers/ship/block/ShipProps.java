package dev.seafarers.ship.block;

import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Block state properties shared by ship parts. */
public final class ShipProps {
    /** Which mast a part belongs to: 0 is the mainmast; the Brigantine and Galleon have more. */
    public static final IntegerProperty MAST = IntegerProperty.create("mast", 0, 2);

    private ShipProps() {}
}
