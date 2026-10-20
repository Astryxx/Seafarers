package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Messages above the hotbar. The ship's status line (wind, speed, sails) is drawn in the same place several
 * times a second for everyone aboard; a message shown through here keeps the spot for as long as the config's
 * messageSeconds says, so it can be read, and the status line comes back after. The game's own action bar fades
 * after about three seconds, so a longer message is shown again until its time is up.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Notify {
    private static final String UNTIL = "seafarers:message_until";
    /** Ticks between showings of a long message (before the action bar starts to fade). */
    private static final int REFRESH = 40;
    private static final Map<UUID, Component> SHOWING = new HashMap<>();

    public static void show(Player player, Component message) {
        player.displayClientMessage(message, true);
        player.getPersistentData().putLong(UNTIL, player.level().getGameTime() + Math.round(SeafarersConfig.MESSAGE_SECONDS.get() * 20));
        if (player instanceof ServerPlayer) {
            SHOWING.put(player.getUUID(), message);
        }
    }

    /** Whether a message is still being shown to this player (the status line waits). */
    public static boolean busy(Player player) {
        return player.getPersistentData().getLong(UNTIL) > player.level().getGameTime();
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Component message = SHOWING.get(player.getUUID());
        if (message == null) {
            return;
        }
        long left = player.getPersistentData().getLong(UNTIL) - player.level().getGameTime();
        if (left <= 0) {
            SHOWING.remove(player.getUUID());
        } else if (left > 30 && left % REFRESH == 0) {
            player.displayClientMessage(message, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SHOWING.remove(event.getEntity().getUUID());
    }

    private Notify() {}
}
