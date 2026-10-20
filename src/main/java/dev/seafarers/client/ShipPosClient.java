package dev.seafarers.client;

import dev.seafarers.network.ShipPosPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Where the server last said this player's ship is; map mods' integrations listen for it. */
public final class ShipPosClient {
    private static ShipPosPayload latest = ShipPosPayload.none();
    private static final List<Consumer<ShipPosPayload>> LISTENERS = new ArrayList<>();

    public static void receive(ShipPosPayload payload) {
        latest = payload;
        for (Consumer<ShipPosPayload> l : LISTENERS) {
            l.accept(payload);
        }
    }

    public static void listen(Consumer<ShipPosPayload> listener) {
        LISTENERS.add(listener);
        listener.accept(latest);
    }

    private ShipPosClient() {}
}
