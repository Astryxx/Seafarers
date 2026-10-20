package dev.seafarers.client;

import dev.seafarers.network.VoyagePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The voyage table: the ship's ledger. Tabs for the voyages on the table (propose the map you're holding, vote,
 * take yours back), the crew (who's aboard; the captain picks a player to invite, remove or hand the ship to), and
 * the ship herself (her state and her name; the captain locks her or swaps her for another). More tabs (trading companies,
 * reputation, emissaries) will hang off this table as the mod grows.
 */
public class VoyageTableScreen extends Screen {
    private enum Tab { VOYAGES, CREW, SHIP, COMPANIES }

    private static final int W = 300, H = 200;
    private static final int PAPER = 0xFFE8D7AE, PAPER_DARK = 0xFFD2BC8A, INK = 0xFF3B2A1A, FAINT = 0xFF7A6247, FRAME = 0xFF5A3A1E;

    private final BlockPos table;
    private CompoundTag state;
    private Tab tab = Tab.VOYAGES;
    private int left, top;
    private EditBox nameBox;
    /** The player picked on the crew tab: a crew member or someone nearby (by UUID), or null. */
    private String selected;
    private boolean selectedIsCrew;
    private static final int ROW = 13, CREW_X = 8, NEAR_X = 168, ROWS_Y = 46;

    public VoyageTableScreen(BlockPos table, CompoundTag state) {
        super(Component.translatable("block.seafarers.voyage_table"));
        this.table = table;
        this.state = state;
    }

    /** Opens the screen, or refreshes it if it's already open for this table. */
    public static void show(VoyagePayloads.State payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VoyageTableScreen open && open.table.equals(payload.pos())) {
            open.state = payload.state();
            open.rebuild();
        } else {
            mc.setScreen(new VoyageTableScreen(payload.pos(), payload.state()));
        }
    }

    private void send(int action, int index, String text) {
        PacketDistributor.sendToServer(new VoyagePayloads.Action(this.table, action, index, text));
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        this.rebuild();
    }

    private void rebuild() {
        this.clearWidgets();
        this.nameBox = null;
        int x = this.left + 6;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.translatable("voyage.seafarers.tab." + t.name().toLowerCase()), p -> {
                this.tab = t;
                this.rebuild();
            }).bounds(x, this.top - 20, 70, 18).build();
            b.active = t != this.tab && t != Tab.COMPANIES;
            this.addRenderableWidget(b);
            x += 72;
        }
        switch (this.tab) {
            case VOYAGES -> this.voyageButtons();
            case CREW -> this.crewButtons();
            case SHIP -> {
                CompoundTag ship = this.state.getCompound("ship");
                this.nameBox = new EditBox(this.font, this.left + 70, this.top + 30, 150, 16, Component.translatable("voyage.seafarers.ship_name"));
                this.nameBox.setMaxLength(32);
                this.nameBox.setValue(ship.getString("name"));
                this.addRenderableWidget(this.nameBox);
                this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.rename"),
                        p -> this.send(VoyagePayloads.Action.RENAME, 0, this.nameBox.getValue())).bounds(this.left + 226, this.top + 29, 64, 18).build());
                if (ship.getBoolean("canLock")) {
                    boolean locked = ship.getBoolean("locked");
                    this.addRenderableWidget(Button.builder(Component.translatable(locked ? "voyage.seafarers.unlock" : "voyage.seafarers.lock"),
                            p -> this.send(VoyagePayloads.Action.LOCK, locked ? 0 : 1, "")).bounds(this.left + 8, this.top + H - 26, 110, 18)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("voyage.seafarers.lock_hint"))).build());
                }
                if (ship.getBoolean("canSwap")) {
                    int wait = ship.getInt("swapWait");
                    Component label = wait > 0 ? Component.translatable("voyage.seafarers.swap_wait", (wait + 59) / 60)
                            : Component.translatable("voyage.seafarers.swap");
                    Button swap = Button.builder(label, p -> this.send(VoyagePayloads.Action.SWAP, 0, "")).bounds(this.left + 124, this.top + H - 26, 110, 18)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("voyage.seafarers.swap_hint"))).build();
                    swap.active = wait <= 0;
                    this.addRenderableWidget(swap);
                }
                if (ship.getBoolean("canFree")) {
                    this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.free"),
                            p -> this.send(VoyagePayloads.Action.FREE, 0, "")).bounds(this.left + 240, this.top + H - 26, 52, 18)
                            .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("voyage.seafarers.free_hint"))).build());
                }
            }
            default -> {
            }
        }
    }

    private void crewButtons() {
        this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.leave_crew"),
                p -> this.send(VoyagePayloads.Action.LEAVE_CREW, 0, "")).bounds(this.left + W - 90, this.top + H - 26, 82, 18).build());
        if (this.selected == null) {
            return;
        }
        String who = this.selected;
        boolean captain = this.state.getCompound("ship").getBoolean("youCaptain");
        CompoundTag picked = this.selectedCrew();
        if (this.selectedIsCrew && picked != null && this.state.getCompound("ship").getBoolean("hasBrig")) {
            // anyone of the crew can vote a crewmate into the brig (or out of it); voting again takes the vote back
            boolean in = picked.getBoolean("brig");
            Component label = Component.translatable(picked.getBoolean("myBrigVote") ? "voyage.seafarers.brig_voted"
                    : in ? "voyage.seafarers.brig_release" : "voyage.seafarers.brig_vote",
                    picked.getInt("brigVotes"), this.state.getCompound("ship").getInt("brigNeed"));
            this.addRenderableWidget(Button.builder(label, p -> this.send(VoyagePayloads.Action.BRIG, 0, who))
                    .bounds(this.left + 8, this.top + H - 48, 176, 18)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("voyage.seafarers.brig_hint"))).build());
        }
        if (this.selectedIsCrew && captain) {
            this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.make_captain"),
                    p -> this.send(VoyagePayloads.Action.PROMOTE, 0, who)).bounds(this.left + 8, this.top + H - 26, 86, 18).build());
            this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.remove"),
                    p -> this.send(VoyagePayloads.Action.KICK, 0, who)).bounds(this.left + 98, this.top + H - 26, 86, 18).build());
        } else if (!this.selectedIsCrew && this.state.getBoolean("canInvite")) {
            this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.invite"),
                    p -> this.send(VoyagePayloads.Action.INVITE, 0, who)).bounds(this.left + 8, this.top + H - 26, 86, 18).build());
        }
    }

    /** The crew entry picked on the crew tab, or null. */
    @org.jetbrains.annotations.Nullable
    private CompoundTag selectedCrew() {
        ListTag crew = this.state.getList("crew", Tag.TAG_COMPOUND);
        for (int i = 0; i < crew.size(); i++) {
            if (crew.getCompound(i).getString("uuid").equals(this.selected)) {
                return crew.getCompound(i);
            }
        }
        return null;
    }

    /** Picks a crew member or a nearby player on the crew tab by clicking their name. */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (this.tab != Tab.CREW || button != 0) {
            return false;
        }
        int row = (int) Math.floor((my - (this.top + ROWS_Y - 2)) / ROW);
        boolean crewSide = mx >= this.left + CREW_X && mx < this.left + NEAR_X - 6;
        boolean nearSide = mx >= this.left + NEAR_X && mx < this.left + W - 8;
        ListTag list = this.state.getList(crewSide ? "crew" : "nearby", Tag.TAG_COMPOUND);
        if ((crewSide || nearSide) && row >= 0 && row < Math.min(list.size(), 8)) {
            CompoundTag t = list.getCompound(row);
            boolean you = crewSide && t.getBoolean("you");
            this.selected = you ? null : t.getString("uuid");
            this.selectedIsCrew = crewSide;
            this.rebuild();
            return true;
        }
        return false;
    }

    private void voyageButtons() {
        ListTag props = this.state.getList("proposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < props.size(); i++) {
            CompoundTag p = props.getCompound(i);
            int y = this.top + 30 + i * 34;
            final int index = i;
            Button vote = Button.builder(Component.translatable(p.getBoolean("myVote") ? "voyage.seafarers.voted" : "voyage.seafarers.vote"),
                    b -> this.send(VoyagePayloads.Action.VOTE, index, "")).bounds(this.left + W - 116, y + 4, 52, 18).build();
            vote.active = !p.getBoolean("myVote");
            this.addRenderableWidget(vote);
            if (p.getBoolean("mine")) {
                this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.take_back"),
                        b -> this.send(VoyagePayloads.Action.WITHDRAW, 0, "")).bounds(this.left + W - 60, y + 4, 52, 18).build());
            }
        }
        this.addRenderableWidget(Button.builder(Component.translatable("voyage.seafarers.propose"),
                b -> this.send(VoyagePayloads.Action.PROPOSE_HELD, 0, "")).bounds(this.left + 8, this.top + H - 26, 150, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // the background (with the parchment) and the buttons
        Component title = Component.translatable("voyage.seafarers.tab." + this.tab.name().toLowerCase());
        g.drawString(this.font, title, this.left + 10, this.top + 7, INK, false);
        switch (this.tab) {
            case VOYAGES -> this.drawVoyages(g);
            case CREW -> this.drawCrew(g);
            case SHIP -> this.drawShip(g);
            case COMPANIES -> g.drawWordWrap(this.font, Component.translatable("voyage.seafarers.coming_soon"),
                    this.left + 10, this.top + 32, W - 20, FAINT);
        }
    }

    /** The darkened world, then the ledger's parchment; the buttons and text go on top. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(this.left - 4, this.top - 4, this.left + W + 4, this.top + H + 4, FRAME);
        g.fill(this.left, this.top, this.left + W, this.top + H, PAPER);
        g.fill(this.left, this.top, this.left + W, this.top + 22, PAPER_DARK);
    }

    private void drawVoyages(GuiGraphics g) {
        ListTag props = this.state.getList("proposals", Tag.TAG_COMPOUND);
        int say = this.state.getInt("say");
        if (props.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("voyage.seafarers.none"), this.left + 10, this.top + 32, W - 20, FAINT);
        }
        for (int i = 0; i < props.size(); i++) {
            CompoundTag p = props.getCompound(i);
            int y = this.top + 30 + i * 34;
            g.fill(this.left + 6, y, this.left + W - 6, y + 30, i % 2 == 0 ? 0x20000000 : 0x10000000);
            ItemStack item = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), p.getCompound("item"));
            g.renderItem(item, this.left + 10, y + 7);
            g.drawString(this.font, this.font.plainSubstrByWidth(p.getString("name"), 140), this.left + 32, y + 5, INK, false);
            g.drawString(this.font, Component.translatable("voyage.seafarers.by_votes", p.getString("by"), p.getInt("votes"), say),
                    this.left + 32, y + 17, FAINT, false);
        }
        g.drawWordWrap(this.font, Component.translatable("voyage.seafarers.agree"), this.left + 166, this.top + H - 25, W - 172, FAINT);
    }

    private void drawCrew(GuiGraphics g) {
        ListTag crew = this.state.getList("crew", Tag.TAG_COMPOUND);
        ListTag nearby = this.state.getList("nearby", Tag.TAG_COMPOUND);
        CompoundTag ship = this.state.getCompound("ship");
        g.drawString(this.font, Component.translatable("voyage.seafarers.crew_count", crew.size(), ship.getInt("crewMax")),
                this.left + CREW_X + 2, this.top + 30, INK, false);
        g.drawString(this.font, Component.translatable("voyage.seafarers.nearby"), this.left + NEAR_X + 2, this.top + 30, INK, false);
        g.fill(this.left + NEAR_X - 5, this.top + 28, this.left + NEAR_X - 4, this.top + H - 50, 0x40000000);
        if (crew.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("voyage.seafarers.no_crew"), this.left + CREW_X + 2, this.top + ROWS_Y,
                    NEAR_X - CREW_X - 12, FAINT);
        }
        for (int i = 0; i < Math.min(crew.size(), 8); i++) {
            CompoundTag c = crew.getCompound(i);
            int y = this.top + ROWS_Y + i * ROW;
            if (c.getString("uuid").equals(this.selected) && this.selectedIsCrew) {
                g.fill(this.left + CREW_X, y - 2, this.left + NEAR_X - 6, y + ROW - 2, 0x30000000);
            }
            String where = c.getBoolean("aboard") ? "voyage.seafarers.aboard" : c.getBoolean("online") ? "voyage.seafarers.ashore" : "voyage.seafarers.offline";
            String name = this.font.plainSubstrByWidth(c.getString("name"), 78);
            g.drawString(this.font, name, this.left + CREW_X + 4, y, c.getBoolean("you") ? 0xFF1E4E8C : INK, false);
            if (c.getBoolean("captain")) {
                g.drawString(this.font, "\u2605", this.left + CREW_X + 6 + this.font.width(name), y, 0xFFB8860B, false);
            }
            if (c.getBoolean("brig")) {
                where = "voyage.seafarers.in_brig";
            }
            g.drawString(this.font, Component.translatable(where), this.left + CREW_X + 98, y, c.getBoolean("brig") ? 0xFF9A2A1A : FAINT, false);
        }
        if (nearby.isEmpty()) {
            g.drawWordWrap(this.font, Component.translatable("voyage.seafarers.nobody_near"), this.left + NEAR_X + 2, this.top + ROWS_Y,
                    W - NEAR_X - 12, FAINT);
        }
        for (int i = 0; i < Math.min(nearby.size(), 8); i++) {
            CompoundTag c = nearby.getCompound(i);
            int y = this.top + ROWS_Y + i * ROW;
            if (c.getString("uuid").equals(this.selected) && !this.selectedIsCrew) {
                g.fill(this.left + NEAR_X, y - 2, this.left + W - 8, y + ROW - 2, 0x30000000);
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(c.getString("name"), W - NEAR_X - 20), this.left + NEAR_X + 4, y, INK, false);
        }
        String captain = ship.getString("captain");
        Component hint = ship.getBoolean("youCaptain") ? Component.translatable("voyage.seafarers.captain_hint")
                : this.state.getBoolean("canInvite") ? Component.translatable("voyage.seafarers.crew_hint")
                : captain.isEmpty() ? Component.translatable("voyage.seafarers.no_captain")
                : Component.translatable("voyage.seafarers.captain_is", captain);
        if (!(this.selectedIsCrew && this.selectedCrew() != null && ship.getBoolean("hasBrig"))) {   // (the brig button's there)
            g.drawWordWrap(this.font, hint, this.left + 10, this.top + H - 46, W - 20, FAINT);
        }
    }

    private void drawShip(GuiGraphics g) {
        CompoundTag ship = this.state.getCompound("ship");
        g.drawString(this.font, Component.translatable("voyage.seafarers.ship_name"), this.left + 10, this.top + 34, INK, false);
        int y = this.top + 58;
        String layout = ship.getString("layout");
        line(g, "voyage.seafarers.class", y, Component.translatable("ship.seafarers." + (layout.isEmpty() ? "unknown" : layout)));
        float water = ship.getFloat("water");
        line(g, "voyage.seafarers.water", y += 14, Component.literal(String.format("%.0f%%", water))
                .withStyle(water > 40 ? ChatFormatting.DARK_RED : water > 0 ? ChatFormatting.GOLD : ChatFormatting.DARK_GREEN));
        int holes = ship.getInt("holes");
        line(g, "voyage.seafarers.holes", y += 14, Component.literal(String.valueOf(holes))
                .withStyle(holes > 0 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN));
        int[] masts = ship.getIntArray("masts");
        int max = Math.max(1, ship.getInt("mastMax"));
        for (int m = 0; m < masts.length; m++) {
            Component health = masts[m] <= 0 ? Component.translatable("voyage.seafarers.mast_broken").withStyle(ChatFormatting.DARK_RED)
                    : Component.literal(masts[m] * 100 / max + "%");
            Component which = Component.translatable("mast.seafarers." + (m == 0 ? "main" : m == 1 ? "fore" : "mizzen") + "_long");
            line(g, masts.length > 1 ? "voyage.seafarers.mast_n" : "voyage.seafarers.mast", y += 14, health, which);
        }
        line(g, "voyage.seafarers.anchor", y += 14, Component.translatable(ship.getBoolean("anchored") ? "voyage.seafarers.down" : "voyage.seafarers.up"));
        line(g, "voyage.seafarers.speed", y + 14, Component.literal(String.format("%.1f kn", Math.abs(ship.getFloat("speed")) * 1.944)));
        // who she answers to, on the right
        String captain = ship.getString("captain");
        int rx = this.left + 176;
        g.drawString(this.font, Component.translatable("voyage.seafarers.captain"), rx, this.top + 58, FAINT, false);
        g.drawString(this.font, captain.isEmpty() ? Component.translatable("voyage.seafarers.nobody") : Component.literal(captain),
                rx + 54, this.top + 58, INK, false);
        g.drawString(this.font, Component.translatable("voyage.seafarers.lock_state"), rx, this.top + 72, FAINT, false);
        g.drawString(this.font, Component.translatable(ship.getBoolean("locked") ? "voyage.seafarers.locked" : "voyage.seafarers.open")
                .withStyle(ship.getBoolean("locked") ? ChatFormatting.DARK_GREEN : ChatFormatting.GOLD), rx + 54, this.top + 72, INK, false);
    }

    private void line(GuiGraphics g, String key, int y, Component value, Object... args) {
        g.drawString(this.font, Component.translatable(key, args), this.left + 14, y, FAINT, false);
        g.drawString(this.font, value, this.left + 120, y, INK, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // keep the ship's state live while the screen is open
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.level.getGameTime() % 20 == 0
                && (this.nameBox == null || !this.nameBox.isFocused())) {
            this.send(VoyagePayloads.Action.REFRESH, 0, "");
        }
    }
}
