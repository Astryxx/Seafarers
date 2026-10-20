package dev.seafarers.compat.journeymap;

import dev.seafarers.Seafarers;
import dev.seafarers.client.ShipPosClient;
import dev.seafarers.network.ShipPosPayload;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;

/**
 * JourneyMap (optional): a waypoint marks your crew's ship and follows her as she sails. It's the mod's own, not
 * saved in your waypoint list, and goes when you have no ship. Only loaded when JourneyMap is installed.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class SeafarersJourneyMapPlugin implements IClientPlugin {
    private IClientAPI api;
    private Waypoint shown;

    @Override
    public String getModId() {
        return Seafarers.MODID;
    }

    @Override
    public void initialize(IClientAPI api) {
        this.api = api;
        ShipPosClient.listen(this::update);
    }

    private void update(ShipPosPayload ship) {
        if (this.api == null) {
            return;
        }
        try {
            if (this.shown != null) {
                this.api.removeWaypoint(Seafarers.MODID, this.shown);
                this.shown = null;
            }
            if (ship.has()) {
                String name = Component.translatable("waypoint.seafarers.ship", ship.name()).getString();
                Waypoint wp = WaypointFactory.createClientWaypoint(Seafarers.MODID, ship.pos(), name,
                        ResourceKey.create(Registries.DIMENSION, ship.dimension()), false);
                wp.setColor(0x3AA0E0);
                this.api.addWaypoint(Seafarers.MODID, wp);
                this.shown = wp;
                Seafarers.LOGGER.info("PROBE journeymap waypoint {} at {}", name, ship.pos().toShortString());
            }
        } catch (Throwable t) {
            Seafarers.LOGGER.warn("Couldn't update the JourneyMap waypoint: {}", t.toString());
        }
    }
}
