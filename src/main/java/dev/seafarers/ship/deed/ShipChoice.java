package dev.seafarers.ship.deed;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.ChoicePayloads;
import dev.seafarers.registry.ModDataComponents;
import dev.seafarers.registry.ModItems;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Getting a ship of your own: the choice screen a new player sees, choosing again later, swapping a ship for
 * another, and replacing a lost deed. Each player gets one deed at a time, recorded in {@link Sailors}.
 */
public final class ShipChoice {
    /** The ships that can be chosen. */
    public static final List<String> AVAILABLE = List.of("sloop", "brigantine", "galleon");

    public static Item deedFor(String layout) {
        return switch (layout) {
            case "brigantine" -> ModItems.BRIGANTINE_DEED.get();
            case "galleon" -> ModItems.GALLEON_DEED.get();
            default -> ModItems.SLOOP_DEED.get();
        };
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Seconds until this player can get another free ship (0 if now). */
    public static int waitSeconds(ServerPlayer player) {
        long last = Sailors.get(player.server).lastShip(player.getUUID());
        if (last < 0) {
            return 0;
        }
        long ready = last + SeafarersConfig.NEW_SHIP_COOLDOWN_MINUTES.get() * 1200L;
        return (int) Math.max(0, (ready - now(player.server) + 19) / 20);
    }

    public static Component formatWait(int seconds) {
        return seconds >= 60 ? Component.translatable("message.seafarers.minutes", (seconds + 59) / 60)
                : Component.translatable("message.seafarers.seconds", seconds);
    }

    public static void open(ServerPlayer player, int mode) {
        String current = "";
        if (mode == ChoicePayloads.SWAP) {
            HelmBlockEntity helm = helmOf(player.server, Crews.get(player.serverLevel()).captainedBy(player.getUUID()));
            current = helm == null ? "" : helm.layout();
        }
        PacketDistributor.sendToPlayer(player, new ChoicePayloads.Open(mode,
                mode != ChoicePayloads.SWAP && SeafarersConfig.OFFER_JOIN_CREW.get(),
                List.of(SeafarersConfig.SLOOP_CREW.get(), SeafarersConfig.BRIGANTINE_CREW.get(), SeafarersConfig.GALLEON_CREW.get()),
                current, waitSeconds(player)));
    }

    /** Why this player can't choose a ship now (as a message), or null if they can. */
    @Nullable
    public static Component whyNot(ServerPlayer player) {
        Sailors sailors = Sailors.get(player.server);
        if (sailors.deed(player.getUUID()) != null) {
            return Component.translatable("message.seafarers.deed_unused");
        }
        if (SeafarersConfig.ONE_SHIP_PER_PLAYER.get() && Crews.get(player.serverLevel()).captainedBy(player.getUUID()) != null) {
            return Component.translatable("message.seafarers.already_captain");
        }
        int wait = waitSeconds(player);
        if (wait > 0) {
            return Component.translatable("message.seafarers.ship_wait", formatWait(wait));
        }
        return null;
    }

    /** The player's pick on the choice screen; the screen is closed after, whatever came of it. */
    public static void pick(ServerPlayer player, int mode, String choice) {
        decide(player, mode, choice);
        PacketDistributor.sendToPlayer(player, new ChoicePayloads.Open(ChoicePayloads.CLOSE, false, List.of(), "", 0));
    }

    private static void decide(ServerPlayer player, int mode, String choice) {
        Sailors sailors = Sailors.get(player.server);
        if (choice.equals("crew")) {
            if (mode != ChoicePayloads.SWAP && sailors.choice(player.getUUID()) == Sailors.Choice.NONE) {
                sailors.setChoice(player.getUUID(), Sailors.Choice.CREW);
                giveHandbook(player);
                dev.seafarers.ship.Feats.award(player, "start");
                player.sendSystemMessage(Component.translatable("message.seafarers.chose_crew").withStyle(ChatFormatting.AQUA));
            }
            return;
        }
        if (!AVAILABLE.contains(choice)) {
            return;
        }
        if (mode == ChoicePayloads.SWAP) {
            swap(player, choice);
            return;
        }
        if (mode == ChoicePayloads.FIRST && sailors.choice(player.getUUID()) != Sailors.Choice.NONE) {
            return; // already chose (a second click, or a stale screen)
        }
        Component why = whyNot(player);
        if (why != null) {
            player.sendSystemMessage(why.copy().withStyle(ChatFormatting.RED));
            return;
        }
        boolean first = sailors.lastShip(player.getUUID()) < 0;
        boolean newPlayer = sailors.choice(player.getUUID()) == Sailors.Choice.NONE;
        sailors.setChoice(player.getUUID(), Sailors.Choice.SHIP);
        giveDeed(player, choice, null, true);   // first, so it's in the first free slot, ready to use
        dev.seafarers.ship.Feats.award(player, "start");
        if (first && SeafarersConfig.GIVE_COMPASS.get()) {
            give(player, new ItemStack(ModItems.SHIPS_COMPASS.get()));
        }
        if (newPlayer) {
            giveHandbook(player);
        }
        Seafarers.LOGGER.info("PROBE {} chose a {}", player.getGameProfile().getName(), choice);
    }

    /** A captain trades their ship for a new deed; her crew follow them to the new ship when it's launched. */
    public static void swap(ServerPlayer player, String layout) {
        Crews crews = Crews.get(player.serverLevel());
        if (!SeafarersConfig.ALLOW_SWAP.get()) {
            player.sendSystemMessage(Component.translatable("message.seafarers.swap_off").withStyle(ChatFormatting.RED));
            return;
        }
        UUID shipId = crews.captainedBy(player.getUUID());
        if (shipId == null) {
            player.sendSystemMessage(Component.translatable("message.seafarers.not_captain").withStyle(ChatFormatting.RED));
            return;
        }
        int wait = waitSeconds(player);
        if (wait > 0) {
            player.sendSystemMessage(Component.translatable("message.seafarers.ship_wait", formatWait(wait)).withStyle(ChatFormatting.RED));
            return;
        }
        ServerSubLevel ship = shipOf(player.server, shipId);
        HelmBlockEntity helm = ship == null ? null : Ships.helmOf(ship);
        if (helm == null) {
            player.sendSystemMessage(Component.translatable("message.seafarers.swap_far").withStyle(ChatFormatting.RED));
            return;
        }
        if (helm.sinking()) {
            player.sendSystemMessage(Component.translatable("message.seafarers.swap_sinking").withStyle(ChatFormatting.RED));
            return;
        }
        String name = ship.getName() == null ? "" : ship.getName();
        for (UUID id : crews.crewOf(shipId)) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(id);
            if (member != null && member != player) {
                member.sendSystemMessage(Component.translatable("message.seafarers.swapped_crew", player.getDisplayName(), name)
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        crews.dropCaptaincy(shipId);
        SubLevelContainer.getContainer((ServerLevel) ship.getLevel()).removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
        Seafarers.LOGGER.info("PROBE {} swapped {} {} for a {}", player.getGameProfile().getName(), name, shipId, layout);
        giveDeed(player, layout, shipId, true);
    }

    /** A new deed for a player whose unused deed is lost; the lost one stops working. */
    public static void reclaim(ServerPlayer player, dev.seafarers.ship.Reply reply) {
        Sailors sailors = Sailors.get(player.server);
        if (!SeafarersConfig.ALLOW_DEED_RECLAIM.get()) {
            reply.fail("message.seafarers.reclaim_off");
            return;
        }
        UUID valid = sailors.deed(player.getUUID());
        if (valid == null) {
            reply.fail("message.seafarers.reclaim_none");
            return;
        }
        String where = whereIs(player, valid);
        if (where != null) {
            reply.ok(Component.translatable(where).withStyle(ChatFormatting.GOLD));
            return;
        }
        giveDeed(player, sailors.deedLayout(player.getUUID()), sailors.deedReplaces(player.getUUID()), false);
        reply.ok(Component.translatable("message.seafarers.reclaimed").withStyle(ChatFormatting.AQUA));
        Seafarers.LOGGER.info("PROBE {} reclaimed a deed", player.getGameProfile().getName());
    }

    /** Where the player's valid deed is, as a message key, if they're carrying it or it's in their ender chest. */
    @Nullable
    public static String whereIs(ServerPlayer player, UUID deedId) {
        if (contains(player.getInventory(), deedId)) {
            return "message.seafarers.deed_in_inventory";
        }
        if (contains(player.getEnderChestInventory(), deedId)) {
            return "message.seafarers.deed_in_ender_chest";
        }
        return null;
    }

    private static boolean contains(Container c, UUID deedId) {
        for (int i = 0; i < c.getContainerSize(); i++) {
            Deed d = c.getItem(i).get(ModDataComponents.DEED.get());
            if (d != null && d.id().equals(deedId)) {
                return true;
            }
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** The Captain's Handbook (Patchouli's book item with our book), if Patchouli is installed and the config allows. */
    public static void giveHandbook(ServerPlayer player) {
        ItemStack book = handbook(player.server);
        if (!book.isEmpty() && SeafarersConfig.GIVE_HANDBOOK.get()) {
            give(player, book);
        }
    }

    public static ItemStack handbook(MinecraftServer server) {
        if (!net.neoforged.fml.ModList.get().isLoaded("patchouli")) {
            return ItemStack.EMPTY;
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "patchouli:guide_book");
        tag.putInt("count", 1);
        net.minecraft.nbt.CompoundTag components = new net.minecraft.nbt.CompoundTag();
        components.putString("patchouli:book", Seafarers.MODID + ":handbook");
        tag.put("components", components);
        return ItemStack.parse(server.registryAccess(), tag).orElse(ItemStack.EMPTY);
    }

    public static void giveDeed(ServerPlayer player, String layout, @Nullable UUID replaces, boolean startsCooldown) {
        UUID id = UUID.randomUUID();
        ItemStack stack = new ItemStack(deedFor(layout));
        stack.set(ModDataComponents.DEED.get(), new Deed(id, player.getUUID(), player.getGameProfile().getName(), Optional.ofNullable(replaces)));
        Sailors.get(player.server).issue(player.getUUID(), id, layout, replaces, now(player.server), startsCooldown);
        Component name = stack.getHoverName();   // before it goes in the inventory (which empties this stack)
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false); // a full inventory: at their feet (deeds can't be lost, see DeedEvents)
        }
        player.sendSystemMessage(Component.translatable("message.seafarers.deed_given", name).withStyle(ChatFormatting.AQUA));
    }

    /** The reminder a player with an unused deed they aren't carrying gets when they join. */
    public static Component reclaimPrompt() {
        return Component.translatable("message.seafarers.deed_missing").withStyle(ChatFormatting.GOLD).append(" ")
                .append(Component.translatable("message.seafarers.deed_missing_button").withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crew reclaim"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.seafarers.deed_missing_hover")))));
    }

    @Nullable
    public static ServerSubLevel shipOf(MinecraftServer server, @Nullable UUID shipId) {
        if (shipId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            SubLevelContainer container = SubLevelContainer.getContainer(level);
            SubLevel ship = container == null ? null : container.getSubLevel(shipId);
            if (ship instanceof ServerSubLevel s) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public static HelmBlockEntity helmOf(MinecraftServer server, @Nullable UUID shipId) {
        ServerSubLevel ship = shipOf(server, shipId);
        return ship == null ? null : Ships.helmOf(ship);
    }

    private ShipChoice() {}
}
