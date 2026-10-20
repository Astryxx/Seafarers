package dev.seafarers.ship.deed;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.ChoicePayloads;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.item.ShipDeedItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Iterator;
import java.util.UUID;

/**
 * A player's first visit (the ship choice screen), and keeping their deed safe: it stays with them when they die
 * (held in {@link Sailors} until they respawn, so not even a grave mod takes it), and if they join without it,
 * they're offered a replacement.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class DeedEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Sailors sailors = Sailors.get(player.server);
        for (Iterator<ItemEntity> it = event.getDrops().iterator(); it.hasNext(); ) {
            ItemStack stack = it.next().getItem();
            if (ShipDeedItem.protectedDeed(stack)) {
                sailors.hold(player.getUUID(), stack, player.registryAccess());
                it.remove();
                Seafarers.LOGGER.info("PROBE deed kept for {} on death", player.getGameProfile().getName());
            }
        }
    }

    private static void giveBack(ServerPlayer player) {
        for (ItemStack stack : Sailors.get(player.server).takeHeld(player.getUUID(), player.registryAccess())) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            giveBack(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isAlive()) {
            giveBack(player);
        }
        Sailors sailors = Sailors.get(player.server);
        UUID me = player.getUUID();
        if (sailors.choice(me) == Sailors.Choice.NONE) {
            Crews crews = Crews.get(player.serverLevel());
            if (crews.captainedBy(me) != null) {
                sailors.setChoice(me, Sailors.Choice.SHIP);   // sailing since before the choice screen
            } else if (crews.shipOf(me) != null) {
                sailors.setChoice(me, Sailors.Choice.CREW);
            } else if (SeafarersConfig.SHOW_SHIP_CHOICE.get()) {
                ShipChoice.open(player, ChoicePayloads.FIRST);
                return;
            }
        }
        UUID deed = sailors.deed(me);
        if (deed != null && SeafarersConfig.ALLOW_DEED_RECLAIM.get() && ShipChoice.whereIs(player, deed) == null) {
            player.sendSystemMessage(ShipChoice.reclaimPrompt());
        }
    }

    private DeedEvents() {}
}
