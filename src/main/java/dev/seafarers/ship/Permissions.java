package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes, through NeoForge's permission API, so a permissions mod (LuckPerms and the like) can grant or
 * take them away. Without one, the defaults below apply:
 * <ul>
 *   <li>{@code seafarers.command.crew}: the player /crew commands (everyone)</li>
 *   <li>{@code seafarers.admin}: /crew admin and /crew reset (operators)</li>
 *   <li>{@code seafarers.bypass.locks}: use any ship, locked or not (operators, if operatorsIgnoreLocks is on)</li>
 *   <li>{@code seafarers.bypass.limits}: launch past the server's ship limit and inside others' land claims (operators)</li>
 * </ul>
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Permissions {
    public static final PermissionNode<Boolean> CREW = new PermissionNode<>(Seafarers.MODID, "command.crew", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true);
    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(Seafarers.MODID, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));
    public static final PermissionNode<Boolean> BYPASS_LOCKS = new PermissionNode<>(Seafarers.MODID, "bypass.locks", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2) && SeafarersConfig.OPS_IGNORE_LOCKS.get());
    public static final PermissionNode<Boolean> BYPASS_LIMITS = new PermissionNode<>(Seafarers.MODID, "bypass.limits", PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2));

    @SubscribeEvent
    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(CREW, ADMIN, BYPASS_LOCKS, BYPASS_LIMITS);
    }

    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        return PermissionAPI.getPermission(player, node);
    }

    /** For a command's requires(): the console and command blocks always may; players need the node. */
    public static boolean has(CommandSourceStack source, PermissionNode<Boolean> node) {
        ServerPlayer player = source.getPlayer();
        return player == null ? source.hasPermission(2) : has(player, node);
    }

    private Permissions() {}
}
