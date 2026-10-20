package dev.seafarers.ship;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.seafarers.Seafarers;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * For server operators (the seafarers.admin permission): /crew admin ships lists every ship that's loaded, with her
 * captain, crew, lock and where she is; tp, remove, captain, lock and unlock act on one, named by the short id in
 * that list (or her name, with _ for spaces).
 */
public final class AdminCommands {
    private record Found(ServerSubLevel ship, HelmBlockEntity helm) {}

    private static List<Found> ships(CommandSourceStack source) {
        List<Found> out = new ArrayList<>();
        for (ServerLevel level : source.getServer().getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            if (container == null) {
                continue;
            }
            for (SubLevel s : container.getAllSubLevels()) {
                HelmBlockEntity helm = s instanceof ServerSubLevel ss ? Ships.helmOf(ss) : null;
                if (helm != null && !helm.rowboat()) {
                    out.add(new Found((ServerSubLevel) s, helm));
                }
            }
        }
        return out;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static final SuggestionProvider<CommandSourceStack> SHIP_IDS = (c, b) ->
            SharedSuggestionProvider.suggest(ships(c.getSource()).stream().map(f -> shortId(f.ship().getUniqueId())), b);

    @Nullable
    private static Found find(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "ship").toLowerCase(Locale.ROOT);
        for (Found f : ships(c.getSource())) {
            String name = f.ship().getName() == null ? "" : f.ship().getName().toLowerCase(Locale.ROOT).replace(' ', '_');
            if (f.ship().getUniqueId().toString().startsWith(key) || name.equals(key)) {
                return f;
            }
        }
        c.getSource().sendFailure(Component.translatable("message.seafarers.admin_no_ship", key));
        return null;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("admin").requires(src -> Permissions.has(src, Permissions.ADMIN))
                .then(Commands.literal("ships").executes(AdminCommands::list))
                .then(Commands.literal("tp").then(Commands.argument("ship", StringArgumentType.word()).suggests(SHIP_IDS).executes(c -> {
                    Found f = find(c);
                    ServerPlayer me = c.getSource().getPlayerOrException();
                    if (f == null) {
                        return 0;
                    }
                    Vec3 at = f.helm().respawnWorldPos();
                    me.teleportTo((ServerLevel) f.ship().getLevel(), at.x, at.y + 0.5, at.z, me.getYRot(), me.getXRot());
                    return 1;
                })))
                .then(Commands.literal("remove").then(Commands.argument("ship", StringArgumentType.word()).suggests(SHIP_IDS).executes(c -> {
                    Found f = find(c);
                    if (f == null) {
                        return 0;
                    }
                    String name = Captaincy.shipName(c.getSource().getServer(), f.ship().getUniqueId());
                    Crews.get(c.getSource().getLevel()).forgetShip(f.ship().getUniqueId());
                    SubLevelContainer.getContainer((ServerLevel) f.ship().getLevel()).removeSubLevel(f.ship(), SubLevelRemovalReason.REMOVED);
                    Seafarers.LOGGER.info("PROBE admin removed {}", f.ship().getUniqueId());
                    c.getSource().sendSuccess(() -> Component.translatable("message.seafarers.admin_removed", name), true);
                    return 1;
                })))
                .then(Commands.literal("captain").then(Commands.argument("ship", StringArgumentType.word()).suggests(SHIP_IDS)
                        .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(c -> {
                            Found f = find(c);
                            if (f == null) {
                                return 0;
                            }
                            GameProfile p = GameProfileArgument.getGameProfiles(c, "player").iterator().next();
                            Crews crews = Crews.get(c.getSource().getLevel());
                            UUID id = f.ship().getUniqueId();
                            ServerPlayer online = c.getSource().getServer().getPlayerList().getPlayer(p.getId());
                            if (online != null) {
                                crews.join(online, id, Integer.MAX_VALUE);
                            }
                            crews.setCaptain(id, p.getId());
                            c.getSource().sendSuccess(() -> Component.translatable("message.seafarers.new_captain", p.getName(),
                                    Captaincy.shipName(c.getSource().getServer(), id)), true);
                            return 1;
                        }))))
                .then(Commands.literal("lock").then(Commands.argument("ship", StringArgumentType.word()).suggests(SHIP_IDS)
                        .executes(c -> setLock(c, true))))
                .then(Commands.literal("unlock").then(Commands.argument("ship", StringArgumentType.word()).suggests(SHIP_IDS)
                        .executes(c -> setLock(c, false))));
    }

    private static int setLock(CommandContext<CommandSourceStack> c, boolean lock) {
        Found f = find(c);
        if (f == null) {
            return 0;
        }
        Crews.get(c.getSource().getLevel()).setLocked(f.ship().getUniqueId(), lock);
        c.getSource().sendSuccess(() -> Component.translatable(lock ? "message.seafarers.admin_locked" : "message.seafarers.admin_unlocked",
                Captaincy.shipName(c.getSource().getServer(), f.ship().getUniqueId())), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        Crews crews = Crews.get(c.getSource().getLevel());
        List<Found> all = ships(c.getSource());
        c.getSource().sendSuccess(() -> Component.translatable("message.seafarers.admin_ships", all.size()).withStyle(ChatFormatting.GOLD), false);
        int listed = 0;
        for (Found f : all) {
            UUID id = f.ship().getUniqueId();
            UUID captain = crews.captainOf(id);
            Vec3 at = f.helm().worldPos();
            String sid = shortId(id);
            Component line = Component.literal("[" + sid + "] ").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/crew admin tp " + sid))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.seafarers.admin_click_tp"))))
                    .append(Component.literal(Captaincy.shipName(c.getSource().getServer(), id)).withStyle(ChatFormatting.WHITE))
                    .append(Component.translatable("message.seafarers.admin_ship_line",
                            captain == null ? "-" : Captaincy.name(c.getSource().getServer(), captain),
                            crews.crewOf(id).size(), f.helm().maxCrew(),
                            Component.translatable(crews.locked(id) ? "voyage.seafarers.locked" : "voyage.seafarers.open"),
                            String.format("%.0f%%", f.helm().flood()),
                            (int) at.x, (int) at.y, (int) at.z, f.ship().getLevel().dimension().location().getPath())
                            .withStyle(ChatFormatting.GRAY));
            c.getSource().sendSuccess(() -> line, false);
            listed++;
        }
        int unloaded = crews.captainCount() - (int) all.stream().filter(f -> crews.captainOf(f.ship().getUniqueId()) != null).count();
        if (unloaded > 0) {
            c.getSource().sendSuccess(() -> Component.translatable("message.seafarers.admin_unloaded", unloaded).withStyle(ChatFormatting.GRAY), false);
        }
        Seafarers.LOGGER.info("PROBE admin ships listed={}", listed);
        return listed;
    }

    private AdminCommands() {}
}
