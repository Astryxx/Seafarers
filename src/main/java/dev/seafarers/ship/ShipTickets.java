package dev.seafarers.ship;

import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.seafarers.Seafarers;
import net.minecraft.util.Unit;

/**
 * Sable unloads ships no player is near. These tickets keep one loaded anyway: a sinking ship until she is
 * wrecked (so her crew still gets their new ship), and ships under test.
 */
public final class ShipTickets {
    public static final SubLevelLoadingTicketType<Unit> SINKING = SubLevelLoadingTicketType.create(Seafarers.id("sinking"), Unit.CODEC);
    /** A ship with a crew: kept loaded so they can respawn aboard wherever she is. */
    public static final SubLevelLoadingTicketType<Unit> CREW = SubLevelLoadingTicketType.create(Seafarers.id("crew"), Unit.CODEC);
    public static final SubLevelLoadingTicketType<Unit> TEST = SubLevelLoadingTicketType.create(Seafarers.id("test"), Unit.CODEC);

    /** Called from the mod constructor so the types exist before saved tickets are read. */
    public static void init() {
    }

    private ShipTickets() {}
}
