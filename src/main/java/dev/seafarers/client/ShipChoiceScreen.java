package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.network.ChoicePayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Choose your ship: three cards (sloop, brigantine, galleon) and "Join a crew instead".
 * Shown to a player on their first visit (and it can't be closed until they choose), with /crew choose, and to a
 * captain swapping their ship.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public class ShipChoiceScreen extends Screen {
    private record Ship(String id, int masts, boolean available) {}

    private static final List<Ship> SHIPS = List.of(new Ship("sloop", 1, true), new Ship("brigantine", 2, true), new Ship("galleon", 3, true));
    private static final int W = 360, H = 206, CARD_W = 108, CARD_H = 124;
    private static final int PAPER = 0xFFE8D7AE, PAPER_DARK = 0xFFD2BC8A, INK = 0xFF3B2A1A, FAINT = 0xFF7A6247, FRAME = 0xFF5A3A1E;

    /** An open request that arrived while the world was still loading. */
    @Nullable
    private static ChoicePayloads.Open pending;

    private final ChoicePayloads.Open info;
    private int left, top;

    public ShipChoiceScreen(ChoicePayloads.Open info) {
        super(Component.translatable(info.mode() == ChoicePayloads.SWAP ? "choice.seafarers.title_swap" : "choice.seafarers.title"));
        this.info = info;
    }

    public static void show(ChoicePayloads.Open payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.mode() == ChoicePayloads.CLOSE) {
            pending = null;
            if (mc.screen instanceof ShipChoiceScreen) {
                mc.setScreen(null);
            }
            return;
        }
        if (mc.player == null || mc.screen instanceof ReceivingLevelScreen) {
            pending = payload;
        } else {
            mc.setScreen(new ShipChoiceScreen(payload));
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (pending != null && mc.player != null && mc.level != null && !(mc.screen instanceof ReceivingLevelScreen)) {
            ChoicePayloads.Open p = pending;
            pending = null;
            mc.setScreen(new ShipChoiceScreen(p));
        }
    }

    private void pick(String choice) {
        PacketDistributor.sendToServer(new ChoicePayloads.Pick(this.info.mode(), choice));
        this.minecraft.setScreen(null);
    }

    @Override
    protected void init() {
        this.left = (this.width - W) / 2;
        this.top = (this.height - H) / 2;
        boolean waiting = this.info.waitSeconds() > 0;
        for (int i = 0; i < SHIPS.size(); i++) {
            Ship ship = SHIPS.get(i);
            int x = this.cardX(i);
            Component label = !ship.available() ? Component.translatable("choice.seafarers.coming_soon")
                    : Component.translatable("choice.seafarers.choose");
            Button b = Button.builder(label, btn -> this.choose(ship)).bounds(x + 6, this.cardY() + CARD_H - 22, CARD_W - 12, 18).build();
            b.active = ship.available() && !waiting;
            this.addRenderableWidget(b);
        }
        if (this.info.offerCrew()) {
            this.addRenderableWidget(Button.builder(Component.translatable("choice.seafarers.join_crew"), btn -> this.pick("crew"))
                    .bounds(this.left + 10, this.top + H - 28, 130, 18).build());
        }
        if (this.info.mode() != ChoicePayloads.FIRST) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), btn -> this.onClose())
                    .bounds(this.left + W - 90, this.top + H - 28, 80, 18).build());
        }
    }

    private void choose(Ship ship) {
        if (this.info.mode() != ChoicePayloads.SWAP) {
            this.pick(ship.id());
            return;
        }
        Component current = Component.translatable("choice.seafarers." + (this.info.current().isEmpty() ? "sloop" : this.info.current()));
        this.minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                this.pick(ship.id());
            } else {
                this.minecraft.setScreen(this);
            }
        }, Component.translatable("choice.seafarers.confirm_swap_title", current, Component.translatable("choice.seafarers." + ship.id())),
                Component.translatable("choice.seafarers.confirm_swap").withStyle(ChatFormatting.RED)));
    }

    private int cardX(int i) {
        return this.left + 10 + i * (CARD_W + 8);
    }

    private int cardY() {
        return this.top + 46;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
        g.fill(this.left - 4, this.top - 4, this.left + W + 4, this.top + H + 4, FRAME);
        g.fill(this.left, this.top, this.left + W, this.top + H, PAPER);
        g.fill(this.left, this.top, this.left + W, this.top + 20, PAPER_DARK);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(this.font, this.title, this.left + (W - this.font.width(this.title)) / 2, this.top + 6, INK, false);
        String intro = switch (this.info.mode()) {
            case ChoicePayloads.FIRST -> "choice.seafarers.intro";
            case ChoicePayloads.SWAP -> "choice.seafarers.intro_swap";
            default -> "choice.seafarers.intro_later";
        };
        if (this.info.waitSeconds() > 0) {
            int s = this.info.waitSeconds();
            Component wait = s >= 60 ? Component.translatable("message.seafarers.minutes", (s + 59) / 60)
                    : Component.translatable("message.seafarers.seconds", s);
            g.drawWordWrap(this.font, Component.translatable("choice.seafarers.wait", wait), this.left + 10, this.top + 25, W - 20, 0xFF9A2A1A);
        } else {
            g.drawWordWrap(this.font, Component.translatable(intro), this.left + 10, this.top + 25, W - 20, FAINT);
        }
        for (int i = 0; i < SHIPS.size(); i++) {
            this.drawCard(g, SHIPS.get(i), this.cardX(i), this.cardY());
        }
        int y = this.top + H - 28;
        if (this.info.offerCrew()) {
            g.drawWordWrap(this.font, Component.translatable("choice.seafarers.join_crew_hint"), this.left + 146, y - 2,
                    W - 156 - (this.info.mode() != ChoicePayloads.FIRST ? 86 : 0), FAINT);
        }
    }

    private void drawCard(GuiGraphics g, Ship ship, int x, int y) {
        boolean on = ship.available();
        g.fill(x, y, x + CARD_W, y + CARD_H, on ? 0x30FFFFFF : 0x18000000);
        g.renderOutline(x, y, CARD_W, CARD_H, on ? FRAME : 0x60000000);
        Component name = Component.translatable("choice.seafarers." + ship.id()).withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, name, x + (CARD_W - this.font.width(name)) / 2, y + 5, on ? INK : FAINT, false);
        if (this.info.mode() == ChoicePayloads.SWAP && ship.id().equals(this.info.current())) {
            Component now = Component.translatable("choice.seafarers.current");
            g.drawString(this.font, now, x + (CARD_W - this.font.width(now)) / 2, y + CARD_H - 33, 0xFF8A5A00, false);
        }
        drawShip(g, x + CARD_W / 2, y + 50, ship.masts(), on);
        int index = SHIPS.indexOf(ship);
        int crew = index < this.info.crews().size() ? this.info.crews().get(index) : index + 2;
        Component crewLine = Component.translatable("choice.seafarers.crew_size", crew);
        g.drawString(this.font, crewLine, x + (CARD_W - this.font.width(crewLine)) / 2, y + 54, on ? INK : FAINT, false);
        g.drawWordWrap(this.font, Component.translatable("choice.seafarers." + ship.id() + ".desc"), x + 6, y + 66, CARD_W - 12, FAINT);
    }

    /** A little ship, side on, standing on the waterline at (cx, waterY): hull, masts and square sails. */
    private static void drawShip(GuiGraphics g, int cx, int waterY, int masts, boolean on) {
        int hull = on ? 0xFF6B4A2B : 0xFF8F8478, mast = on ? 0xFF3B2A1A : 0xFF6F665C, sail = on ? 0xFFF4EBD3 : 0xFFC9C2B6;
        int sea = on ? 0xFF3A7A8C : 0xFF9AA4A6;
        int length = 34 + masts * 12;
        // the hull: a wedge, wide at the deck, narrowing to the keel; the bow rakes forward
        for (int r = 0; r < 7; r++) {
            int half = length / 2 - r * 2;
            g.fill(cx - half + r, waterY - 9 + r, cx + half + 3 - r, waterY - 8 + r, hull);
        }
        g.fill(cx + length / 2, waterY - 12, cx + length / 2 + 4, waterY - 10, hull); // the bowsprit's stub
        g.fill(cx - length / 2 - 4, waterY - 2, cx + length / 2 + 8, waterY, sea);
        int spacing = length / (masts + 1);
        for (int m = 1; m <= masts; m++) {
            int mx = cx - length / 2 + m * spacing;
            int height = m == (masts + 1) / 2 ? 28 : 24;   // the mainmast stands tallest
            g.fill(mx, waterY - 9 - height, mx + 1, waterY - 9, mast);
            int sw = 7 + (masts == 1 ? 3 : 0);
            g.fill(mx - sw, waterY - 9 - height + 4, mx + sw + 1, waterY - 9 - height / 2, sail);
            g.fill(mx - sw - 1, waterY - 9 - height / 2 + 2, mx + sw + 2, waterY - 13, sail);
        }
        if (!on) {
            // "?" over a ship that isn't built yet
            g.drawString(Minecraft.getInstance().font, "?", cx - 2, waterY - 28, 0xFF3B2A1A, false);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.info.mode() != ChoicePayloads.FIRST;   // a new player must choose (or join a crew)
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
