package dev.seafarers.client;

import com.mojang.math.Axis;
import dev.seafarers.Seafarers;
import dev.seafarers.network.ShipHudPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * The ship's status, top left, while you're aboard: her name and speed, a wind dial (her bow up, the arrow where the
 * wind blows), each sail (how far it's let down, its angle, how well it's drawing, the mast's health), the water
 * aboard and her holes, fire, the anchor, and the wheel. The server sends it a few times a second (ShipHudPayload);
 * it fades away when you leave her. Client config shipHud turns it off.
 */
@EventBusSubscriber(modid = Seafarers.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShipHud {
    private static final int W = 214, ROW = 10, DIAL = 15;
    private static final int BG = 0x90101418, EDGE = 0x50FFFFFF, TEXT = 0xFFE8E0CC, FAINT = 0xFF9A9384, GOLD = 0xFFE0B040,
            GOOD = 0xFF7CD67C, WARN = 0xFFE6C34A, BAD = 0xFFE05A4A, WATER = 0xFF4A8FE0, SAIL = 0xFFF2EAD6;

    @Nullable
    private static CompoundTag state;
    private static long receivedAt;

    public static void receive(ShipHudPayload payload) {
        state = payload.state();
        receivedAt = System.currentTimeMillis();
    }

    /** Whether the panel has something to show (aboard, and the setting on). */
    public static boolean showing() {
        return state != null && System.currentTimeMillis() - receivedAt < 1500 && dev.seafarers.SeafarersConfig.SHIP_HUD.get();
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT, Seafarers.id("ship_status"), ShipHud::draw);
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!showing() || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        CompoundTag t = state;
        Font font = mc.font;
        ListTag masts = t.getList("masts", Tag.TAG_COMPOUND);
        boolean rowboat = t.getBoolean("rowboat");
        int extra = (t.getInt("holes") > 0 || t.getFloat("flood") > 0 ? 1 : 0) + (t.getInt("fires") > 0 ? 1 : 0)
                + (t.getBoolean("anchored") ? 1 : 0) + (t.getBoolean("sinking") ? 1 : 0);
        int rows = Math.max(4, masts.size() + 1 + extra + (rowboat ? 1 : 0));   // (at least the wind dial's height)
        int x = 4, y = 4, h = 16 + rows * ROW + 4;
        g.fill(x, y, x + W, y + h, BG);
        g.renderOutline(x, y, W, h, EDGE);

        // name and speed
        String layout = t.getString("layout");
        String name = t.getString("name");
        Component kind = Component.translatable("ship.seafarers." + (layout.isEmpty() ? "unknown" : layout));
        int tx = x + 4;
        if (!name.isEmpty() && !name.equalsIgnoreCase(kind.getString())) {
            String shown = font.plainSubstrByWidth(name, W - 110);
            g.drawString(font, shown, tx, y + 4, GOLD, false);
            tx += font.width(shown) + 5;
            g.drawString(font, kind, tx, y + 4, FAINT, false);
        } else {
            g.drawString(font, kind, tx, y + 4, GOLD, false);
        }
        String knots = String.format("%.1f kn", Math.abs(t.getFloat("speed")) * 1.944f);
        g.drawString(font, knots, x + W - 4 - font.width(knots), y + 4, TEXT, false);

        // wind dial on the left, rows on the right
        int cx = x + 4 + DIAL, cy = y + 16 + DIAL + 2;
        dial(g, cx, cy, t.getFloat("wind"));
        g.drawCenteredString(font, Component.translatable("hud.seafarers.wind", t.getString("windFrom")), cx, cy + DIAL + 3, FAINT);

        int rx = x + 4 + DIAL * 2 + 8, ry = y + 17;
        int mastMax = Math.max(1, t.getInt("mastMax"));
        for (int i = 0; i < masts.size(); i++, ry += ROW) {
            sailRow(g, font, rx, ry, masts.size() == 1 ? "only" : i == 0 ? "main" : i == 1 ? "fore" : "mizzen", masts.getCompound(i), mastMax);
        }
        if (rowboat) {
            g.drawString(font, Component.translatable("hud.seafarers.rowboat"), rx, ry, FAINT, false);
            ry += ROW;
        }
        // the wheel: where the rudder is
        g.drawString(font, Component.translatable("hud.seafarers.wheel"), rx, ry, FAINT, false);
        bar(g, rx + 38, ry + 2, 60, 5, 0.5f + t.getFloat("wheel") * 0.5f, true, GOLD);
        ry += ROW;
        float flood = t.getFloat("flood");
        int holes = t.getInt("holes");
        if (holes > 0 || flood > 0) {
            g.drawString(font, Component.translatable("hud.seafarers.water"), rx, ry, FAINT, false);
            bar(g, rx + 38, ry + 2, 40, 5, flood / 100f, false, flood > 50 ? BAD : WATER);
            Component holeText = holes > 0 ? Component.translatable(holes == 1 ? "hud.seafarers.hole" : "hud.seafarers.holes", holes)
                    : Component.literal(Math.round(flood) + "%");
            g.drawString(font, holeText, rx + 84, ry, holes > 0 ? BAD : TEXT, false);
            ry += ROW;
        }
        if (t.getInt("fires") > 0) {
            boolean blink = System.currentTimeMillis() / 400 % 2 == 0;
            g.drawString(font, Component.translatable("hud.seafarers.fire"), rx, ry, blink ? BAD : WARN, false);
            ry += ROW;
        }
        if (t.getBoolean("anchored")) {
            int raise = t.getInt("raise");
            g.drawString(font, raise > 0 ? Component.translatable("hud.seafarers.heaving", raise) : Component.translatable("hud.seafarers.anchor_down"),
                    rx, ry, raise > 0 ? WARN : TEXT, false);
            ry += ROW;
        }
        if (t.getBoolean("sinking")) {
            g.drawString(font, Component.translatable("hud.seafarers.sinking"), rx, ry, BAD, false);
        }
    }

    /** One sail: its name, how far it's let down, its angle, how it's drawing, and the mast's health as pips. */
    private static void sailRow(GuiGraphics g, Font font, int x, int y, String which, CompoundTag m, int mastMax) {
        float deploy = m.getFloat("d"), angle = m.getFloat("a"), fill = m.getFloat("f");
        int health = m.getInt("h");
        g.drawString(font, Component.translatable("mast.seafarers." + which), x, y, FAINT, false);
        bar(g, x + 38, y + 2, 26, 5, deploy, false, SAIL);
        String ang = String.format("%+.0f°", angle);
        g.drawString(font, ang, x + 92 - font.width(ang), y, TEXT, false);   // right-aligned against the state
        String key;
        int col;
        if (health <= 0) {
            key = "broken";
            col = BAD;
        } else if (deploy < 0.02f) {
            key = "furled";
            col = FAINT;
        } else if (fill > 0.7f) {
            key = "full";
            col = GOOD;
        } else if (fill > 0.4f) {
            key = "luffing";
            col = WARN;
        } else {
            key = "slack";
            col = FAINT;
        }
        Component state = Component.translatable("hud.seafarers.sail." + key);
        int sx = x + 97;
        g.drawString(font, state, sx, y, col, false);
        // the mast's health: a pip per hit it can take, at the right
        int pips = Math.min(mastMax, 8);
        for (int i = 0; i < pips; i++) {
            int px = x + 164 - (pips - i) * 4;
            g.fill(px, y + 3, px + 3, y + 6, i < health ? (health == mastMax ? 0xA0C8B89A : WARN) : 0x60FFFFFF);
        }
    }

    /** A bar filled to f (0..1); centred, it's a track with a marker (the wheel). */
    private static void bar(GuiGraphics g, int x, int y, int w, int h, float f, boolean marker, int col) {
        g.fill(x, y, x + w, y + h, 0x60000000);
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0x40FFFFFF);
        f = Math.max(0, Math.min(1, f));
        if (marker) {
            g.fill(x + w / 2, y - 1, x + w / 2 + 1, y + h + 1, 0x80FFFFFF);
            int mx = x + Math.round(f * (w - 2));
            g.fill(mx, y - 1, mx + 2, y + h + 1, col);
        } else {
            g.fill(x, y, x + Math.round(f * w), y + h, col);
        }
    }

    /**
     * The wind dial: a ring, the ship from above with her bow up (and her rudder showing the wheel), and an arrow the
     * way the wind blows. Wind dead astern points straight up, along her course.
     */
    private static void dial(GuiGraphics g, int cx, int cy, float wind) {
        for (int a = 0; a < 360; a += 10) {
            double r = Math.toRadians(a);
            int px = cx + (int) Math.round(Math.sin(r) * DIAL), py = cy - (int) Math.round(Math.cos(r) * DIAL);
            g.fill(px, py, px + 1, py + 1, a % 90 == 0 ? 0xC0FFFFFF : 0x50FFFFFF);
        }
        // the ship: a little hull, bow up
        int hull = 0xFFB08050;
        g.fill(cx - 1, cy - 7, cx + 1, cy - 5, hull);
        g.fill(cx - 2, cy - 5, cx + 2, cy + 5, hull);
        g.fill(cx - 1, cy + 5, cx + 1, cy + 6, hull);
        g.fill(cx - 1, cy - 2, cx + 1, cy + 2, 0xFF5A3A20);   // the mast
        // the wind arrow: from the ring's far side, through the middle, the way the wind blows
        var pose = g.pose();
        pose.pushPose();
        pose.translate(cx + 0.5f, cy + 0.5f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(wind));
        int c = 0xE07EC8F0;
        for (int i = -DIAL + 3; i < DIAL - 4; i += 3) {
            g.fill(-1, -i - 1, 1, -i + 1, c);   // a dashed shaft (drawn tail to head, the head toward -y)
        }
        g.fill(-3, -DIAL + 4, 3, -DIAL + 5, c);
        g.fill(-2, -DIAL + 3, 2, -DIAL + 4, c);
        g.fill(-1, -DIAL + 2, 1, -DIAL + 3, c);
        pose.popPose();
    }

    private ShipHud() {}
}
