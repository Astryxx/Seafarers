package dev.seafarers.ship;

import net.minecraft.server.level.ServerPlayer;

/** A block entity a player can take over: the helm, the oars, a cannon or a harpoon gun. */
public interface Station {
    void onStationInput(ServerPlayer player, float steer, boolean fire, boolean letGo);

    /** With forward (W +1 .. S -1) for the stations that use it: the oars, and the harpoon's reel. */
    default void onStationInput(ServerPlayer player, float steer, float forward, boolean fire, boolean letGo) {
        this.onStationInput(player, steer, fire, letGo);
    }
}
