package dev.seafarers.client;

import dev.seafarers.Seafarers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** The aiming ring shown while you man a cannon. */
@EventBusSubscriber(modid = Seafarers.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CannonHud {

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Seafarers.id("cannon_reticle"), CannonHud::draw);
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        if (!StationClient.atCannon() || !dev.seafarers.SeafarersConfig.CANNON_HUD.get()) {
            return;
        }
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        int col = 0xC0F0E0B0, dark = 0x80201810;
        // a broken ring with tick marks, like a gunner's sight
        int r = 14;
        for (int a = 0; a < 360; a += 6) {
            if (a % 90 < 18 || a % 90 > 72) {
                continue;
            }
            double rad = Math.toRadians(a);
            int x = cx + (int) Math.round(Math.cos(rad) * r), y = cy + (int) Math.round(Math.sin(rad) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
        g.fill(cx - 22, cy, cx - 16, cy + 1, col);
        g.fill(cx + 17, cy, cx + 23, cy + 1, col);
        g.fill(cx, cy + 17, cx + 1, cy + 23, col);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, dark);
        g.fill(cx, cy, cx + 1, cy + 1, col);
    }

    private CannonHud() {}
}
