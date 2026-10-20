package dev.seafarers.ship;

import com.mojang.brigadier.CommandDispatcher;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import com.mojang.authlib.GameProfile;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.ChoicePayloads;
import dev.seafarers.ship.deed.ShipChoice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Crews: /crew invite, accept, leave and list; /crew brig and release (votes on the brig, see {@link Brig}); for
 * captains /crew kick, captain (hand over), lock, unlock and swap;
 * /crew choose and reclaim for a ship of your own; /crew reset for operators. When a crew member dies they come
 * back aboard their ship (the Ferry of the Damned comes later).
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class CrewEvents {

    @Nullable
    public static HelmBlockEntity helmOfShip(ServerLevel level, @Nullable UUID shipId) {
        if (shipId == null) {
            return null;
        }
        SubLevel ship = SubLevelContainer.getContainer(level).getSubLevel(shipId);
        return ship == null ? null : Ships.helmOf(ship);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player) || !dev.seafarers.SeafarersConfig.RESPAWN_ABOARD.get()) {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        Vec3 at = ShipBeds.respawnPoint(overworld, player.getUUID());
        if (at == null) {
            return;
        }
        // the new player object is placed at once; the teleport tells the client (the game hands the connection
        // to the new player only after this event, as it does with its own respawn teleport)
        player.moveTo(at.x, at.y, at.z, player.getYRot(), player.getXRot());
        player.teleportTo(overworld, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        dev.seafarers.ship.Notify.show(player, Component.translatable("message.seafarers.back_aboard").withStyle(ChatFormatting.AQUA));
        Seafarers.LOGGER.info("PROBE respawned {} aboard at {}", player.getGameProfile().getName(),
                net.minecraft.core.BlockPos.containing(at).toShortString());
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("crew").requires(src -> Permissions.has(src, Permissions.CREW))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c ->
                        Captaincy.invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player"), Reply.to(c.getSource())) ? 1 : 0)))
                .then(Commands.literal("accept").executes(c -> Captaincy.accept(c.getSource().getPlayerOrException(), Reply.to(c.getSource())) ? 1 : 0))
                .then(Commands.literal("leave").executes(c -> Captaincy.leave(c.getSource().getPlayerOrException(), Reply.to(c.getSource())) ? 1 : 0))
                .then(Commands.literal("kick").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                    int n = 0;
                    for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
                        n += Captaincy.kick(c.getSource().getPlayerOrException(), p.getId(), Reply.to(c.getSource())) ? 1 : 0;
                    }
                    return n;
                })))
                .then(Commands.literal("captain").then(Commands.argument("player", EntityArgument.player()).executes(c ->
                        Captaincy.promote(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player").getUUID(), Reply.to(c.getSource())) ? 1 : 0)))
                .then(Commands.literal("free").executes(c -> dev.seafarers.ship.Refloat.free(c.getSource().getPlayerOrException(), Reply.to(c.getSource())) ? 1 : 0))
                .then(Commands.literal("brig").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                    int n = 0;
                    for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
                        n += Brig.vote(c.getSource().getPlayerOrException(), p.getId(), true, Reply.to(c.getSource())) ? 1 : 0;
                    }
                    return n;
                })))
                .then(Commands.literal("release").then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                    int n = 0;
                    for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
                        n += Brig.vote(c.getSource().getPlayerOrException(), p.getId(), false, Reply.to(c.getSource())) ? 1 : 0;
                    }
                    return n;
                })))
                .then(Commands.literal("lock").executes(c -> Captaincy.lock(c.getSource().getPlayerOrException(), true, Reply.to(c.getSource())) ? 1 : 0))
                .then(Commands.literal("unlock").executes(c -> Captaincy.lock(c.getSource().getPlayerOrException(), false, Reply.to(c.getSource())) ? 1 : 0))
                .then(Commands.literal("choose").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    Component why = ShipChoice.whyNot(me);
                    if (why != null) {
                        c.getSource().sendFailure(why);
                        return 0;
                    }
                    ShipChoice.open(me, ChoicePayloads.LATER);
                    return 1;
                }))
                .then(Commands.literal("swap").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    if (!SeafarersConfig.ALLOW_SWAP.get()) {
                        c.getSource().sendFailure(Component.translatable("message.seafarers.swap_off"));
                        return 0;
                    }
                    if (Crews.get(me.serverLevel()).captainedBy(me.getUUID()) == null) {
                        c.getSource().sendFailure(Component.translatable("message.seafarers.not_captain"));
                        return 0;
                    }
                    ShipChoice.open(me, ChoicePayloads.SWAP);
                    return 1;
                }))
                .then(Commands.literal("reclaim").executes(c -> {
                    ShipChoice.reclaim(c.getSource().getPlayerOrException(), Reply.to(c.getSource()));
                    return 1;
                }))
                .then(AdminCommands.build())
                .then(Commands.literal("reset").requires(src -> Permissions.has(src, Permissions.ADMIN))
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                            int n = 0;
                            for (GameProfile p : GameProfileArgument.getGameProfiles(c, "player")) {
                                dev.seafarers.ship.deed.Sailors.get(c.getSource().getServer()).reset(p.getId());
                                ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(p.getId());
                                if (online != null && SeafarersConfig.SHOW_SHIP_CHOICE.get()) {
                                    ShipChoice.open(online, ChoicePayloads.FIRST);
                                }
                                c.getSource().sendSuccess(() -> Component.translatable("message.seafarers.reset_done", p.getName()), true);
                                n++;
                            }
                            return n;
                        })))
                .then(Commands.literal("list").executes(c -> {
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    Crews crews = Crews.get(c.getSource().getLevel());
                    UUID ship = crews.shipOf(me.getUUID());
                    if (ship == null) {
                        c.getSource().sendFailure(Component.translatable("message.seafarers.no_ship"));
                        return 0;
                    }
                    UUID captain = crews.captainOf(ship);
                    StringBuilder names = new StringBuilder();
                    for (UUID id : crews.crewOf(ship)) {
                        names.append(names.isEmpty() ? "" : ", ").append(Captaincy.name(me.server, id));
                        if (id.equals(captain)) {
                            names.append(" (").append(Component.translatable("voyage.seafarers.captain").getString()).append(")");
                        }
                    }
                    boolean locked = crews.locked(ship);
                    c.getSource().sendSuccess(() -> Component.literal(Captaincy.shipName(me.server, ship) + ": " + names)
                            .append(locked ? Component.translatable("message.seafarers.list_locked") : Component.empty()), false);
                    return 1;
                })));
    }

    private CrewEvents() {}
}
