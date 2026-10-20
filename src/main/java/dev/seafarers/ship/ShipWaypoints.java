package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.ShipPosPayload;
import dev.seafarers.ship.deed.ShipChoice;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells each player where their crew's ship is, every few seconds when she's moved (and every half minute
 * anyway), so a map mod can show her as a waypoint (see compat.journeymap). Players without a ship are told so once.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class ShipWaypoints {
    private static final Map<UUID, ShipPosPayload> SENT = new HashMap<>();
    private static final Map<UUID, Long> SENT_AT = new HashMap<>();

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % 60 != 0 || !SeafarersConfig.SHIP_WAYPOINTS.get()) {
            return;
        }
        Crews crews = Crews.get(event.getServer().overworld());
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID shipId = crews.shipOf(player.getUUID());
            ServerSubLevel ship = ShipChoice.shipOf(event.getServer(), shipId);
            HelmBlockEntity helm = ship == null ? null : Ships.helmOf(ship);
            ShipPosPayload last = SENT.get(player.getUUID());
            ShipPosPayload next;
            if (helm != null) {
                next = new ShipPosPayload(true, ship.getLevel().dimension().location(), BlockPos.containing(helm.worldPos()),
                        Captaincy.shipName(event.getServer(), shipId));
            } else if (shipId != null && last != null && last.has()) {
                continue; // out of reach: her last known place stands
            } else {
                next = ShipPosPayload.none();
            }
            boolean moved = last == null || last.has() != next.has() || !last.name().equals(next.name())
                    || last.pos().distSqr(next.pos()) > 16 * 16 || now - SENT_AT.getOrDefault(player.getUUID(), 0L) > 600;
            if (moved && (next.has() || last == null || last.has())) {
                PacketDistributor.sendToPlayer(player, next);
                SENT.put(player.getUUID(), next);
                SENT_AT.put(player.getUUID(), now);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
        SENT_AT.remove(event.getEntity().getUUID());
    }

    private ShipWaypoints() {}
}
