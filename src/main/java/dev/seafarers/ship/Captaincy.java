package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.ship.deed.ShipChoice;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What a captain (and their crew) can do: invite and remove crew, hand the ship to someone else, lock her, and
 * leave. Used by the /crew commands and the buttons on the ship's quest table. Each returns whether it worked,
 * having told the player why not.
 */
public final class Captaincy {

    public static String shipName(MinecraftServer server, UUID ship) {
        ServerSubLevel s = ShipChoice.shipOf(server, ship);
        String name = s == null ? null : s.getName();
        return name == null || name.isBlank() ? "ship" : name;
    }

    private static void tell(MinecraftServer server, UUID player, Component message) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            p.sendSystemMessage(message);
        }
    }

    /** The ship this player captains, or null (having told them they don't captain one). */
    @Nullable
    private static UUID captainsShip(ServerPlayer player, Reply reply) {
        UUID ship = Crews.get(player.serverLevel()).captainedBy(player.getUUID());
        if (ship == null) {
            reply.fail("message.seafarers.not_captain");
        }
        return ship;
    }

    public static boolean invite(ServerPlayer from, ServerPlayer to, Reply reply) {
        Crews crews = Crews.get(from.serverLevel());
        UUID ship = crews.shipOf(from.getUUID());
        if (ship == null) {
            reply.fail("message.seafarers.no_ship");
            return false;
        }
        if (SeafarersConfig.ONLY_CAPTAIN_INVITES.get() && !from.getUUID().equals(crews.captainOf(ship))) {
            reply.fail("message.seafarers.only_captain_invites");
            return false;
        }
        if (ship.equals(crews.shipOf(to.getUUID()))) {
            reply.fail("message.seafarers.already_crew", to.getDisplayName());
            return false;
        }
        crews.invite(to.getUUID(), ship);
        to.sendSystemMessage(Component.translatable("message.seafarers.invited", from.getDisplayName(), shipName(from.server, ship))
                .withStyle(ChatFormatting.AQUA).append(" ")
                .append(Component.translatable("message.seafarers.accept_button").withStyle(s -> s.withColor(ChatFormatting.GREEN)
                        .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crew accept"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.seafarers.accept_hover"))))));
        reply.ok(Component.translatable("message.seafarers.invite_sent", to.getDisplayName()));
        return true;
    }

    public static boolean accept(ServerPlayer player, Reply reply) {
        Crews crews = Crews.get(player.serverLevel());
        UUID ship = crews.takeInvite(player.getUUID());
        HelmBlockEntity helm = ShipChoice.helmOf(player.server, ship);
        if (ship == null || helm == null) {
            reply.fail("message.seafarers.no_invite");
            return false;
        }
        if (!crews.join(player, ship, helm.maxCrew())) {
            reply.fail("message.seafarers.crew_full", helm.maxCrew());
            return false;
        }
        for (UUID id : crews.crewOf(ship)) {
            if (!id.equals(player.getUUID())) {
                tell(player.server, id, Component.translatable("message.seafarers.crew_joined", player.getDisplayName()).withStyle(ChatFormatting.AQUA));
            }
            dev.seafarers.ship.Feats.award(player.server.getPlayerList().getPlayer(id), "crew");
        }
        reply.ok(Component.translatable("message.seafarers.joined").withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static boolean kick(ServerPlayer captain, UUID target, Reply reply) {
        UUID ship = captainsShip(captain, reply);
        if (ship == null) {
            return false;
        }
        Crews crews = Crews.get(captain.serverLevel());
        if (target.equals(captain.getUUID()) || !crews.crewOf(ship).contains(target)) {
            reply.fail("message.seafarers.not_in_crew");
            return false;
        }
        crews.leave(target);
        tell(captain.server, target, Component.translatable("message.seafarers.kicked", shipName(captain.server, ship)).withStyle(ChatFormatting.RED));
        reply.ok(Component.translatable("message.seafarers.kicked_done", name(captain.server, target)));
        Seafarers.LOGGER.info("PROBE crew kick {} from {}", target, ship);
        return true;
    }

    public static boolean promote(ServerPlayer captain, UUID target, Reply reply) {
        UUID ship = captainsShip(captain, reply);
        if (ship == null) {
            return false;
        }
        Crews crews = Crews.get(captain.serverLevel());
        if (target.equals(captain.getUUID()) || !crews.crewOf(ship).contains(target)) {
            reply.fail("message.seafarers.not_in_crew");
            return false;
        }
        crews.setCaptain(ship, target);
        for (UUID id : crews.crewOf(ship)) {
            tell(captain.server, id, Component.translatable("message.seafarers.new_captain", name(captain.server, target),
                    shipName(captain.server, ship)).withStyle(ChatFormatting.AQUA));
        }
        Seafarers.LOGGER.info("PROBE crew captain {} of {}", target, ship);
        return true;
    }

    public static boolean lock(ServerPlayer captain, boolean lock, Reply reply) {
        if (!SeafarersConfig.ALLOW_LOCKING.get()) {
            reply.fail("message.seafarers.locking_off");
            return false;
        }
        UUID ship = captainsShip(captain, reply);
        if (ship == null) {
            return false;
        }
        Crews.get(captain.serverLevel()).setLocked(ship, lock);
        reply.ok(Component.translatable(lock ? "message.seafarers.locked_done" : "message.seafarers.unlocked_done")
                .withStyle(ChatFormatting.AQUA));
        Seafarers.LOGGER.info("PROBE crew lock {} {}", ship, lock);
        return true;
    }

    public static boolean leave(ServerPlayer player, Reply reply) {
        Crews crews = Crews.get(player.serverLevel());
        UUID ship = crews.shipOf(player.getUUID());
        if (ship == null) {
            reply.fail("message.seafarers.no_ship");
            return false;
        }
        UUID next = crews.leave(player.getUUID());
        if (next != null) {
            for (UUID id : crews.crewOf(ship)) {
                tell(player.server, id, Component.translatable("message.seafarers.new_captain", name(player.server, next),
                        shipName(player.server, ship)).withStyle(ChatFormatting.AQUA));
            }
        }
        reply.ok(Component.translatable("message.seafarers.left"));
        return true;
    }

    /** Whether this player can use any ship, locked or not (operators by default; the seafarers.bypass.locks permission). */
    public static boolean overrides(ServerPlayer player) {
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer) && Permissions.has(player, Permissions.BYPASS_LOCKS);
    }

    public static String name(MinecraftServer server, UUID player) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            return p.getGameProfile().getName();
        }
        var cached = server.getProfileCache() == null ? null : server.getProfileCache().get(player).orElse(null);
        return cached != null ? cached.getName() : player.toString().substring(0, 8);
    }

    private Captaincy() {}
}
