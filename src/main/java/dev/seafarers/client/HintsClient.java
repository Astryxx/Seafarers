package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.HintPayload;
import dev.seafarers.ship.Hints;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Shows a hint on its own line just above the hotbar's message line (so the ship's status and blocks' own
 * messages never push it off), with the player's own keys: "A / D turn the wheel" says whatever they've bound.
 * It fades after a few seconds.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class HintsClient {
    /** The keys each hint names, in the order its text uses them (%1$s, %2$s...). */
    private static final Map<String, String[]> KEYS = Map.ofEntries(
            Map.entry("helm", new String[]{"key.left", "key.right", "key.sneak"}),
            Map.entry("sail_rope", new String[]{"key.forward", "key.back", "key.sneak"}),
            Map.entry("brace_rope", new String[]{"key.left", "key.right", "key.sneak"}),
            Map.entry("capstan", new String[]{"key.use"}),
            Map.entry("cannon", new String[]{"key.use", "key.attack", "key.sneak"}),
            Map.entry("harpoon", new String[]{"key.attack", "key.forward", "key.sneak"}),
            Map.entry("oars", new String[]{"key.forward", "key.back", "key.left", "key.right", "key.sneak"}),
            Map.entry("map_table", new String[]{"key.sneak", "key.use"}),
            Map.entry("bucket_rack", new String[]{"key.use"}),
            Map.entry("hull_hole", new String[]{"key.use"}));

    @Nullable
    private static Component showing;
    private static int left;

    public static void show(HintPayload payload) {
        if (!SeafarersConfig.SHOW_HINTS.get()) {
            return;
        }
        String[] keys = KEYS.getOrDefault(payload.key(), new String[0]);
        Object[] args = new Object[keys.length];
        for (int i = 0; i < keys.length; i++) {
            args[i] = Component.keybind(keys[i]).withStyle(ChatFormatting.YELLOW);
        }
        showing = Component.translatable("hint.seafarers." + payload.key(), args);
        left = Hints.HOLD;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (showing != null && (--left <= 0 || Minecraft.getInstance().player == null)) {
            showing = null;
        }
    }

    @EventBusSubscriber(modid = Seafarers.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Layer {
        @SubscribeEvent
        public static void register(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.OVERLAY_MESSAGE, Seafarers.id("hint"), HintsClient::draw);
        }
    }

    private static void draw(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (showing == null || mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        List<FormattedCharSequence> lines = font.split(showing, Math.min(320, g.guiWidth() - 40));
        float fade = Mth.clamp((left - delta.getGameTimeDeltaPartialTick(false)) / 20f, 0f, 1f);
        int alpha = (int) (fade * 255);
        if (alpha < 8) {
            return;
        }
        int lineHeight = font.lineHeight + 2;
        int y = g.guiHeight() - 84 - lines.size() * lineHeight;   // above the action bar's line (which sits at -68)
        for (FormattedCharSequence line : lines) {
            int w = font.width(line);
            int x = (g.guiWidth() - w) / 2;
            g.fill(x - 3, y - 2, x + w + 3, y + font.lineHeight + 1, (alpha * 2 / 5) << 24);
            g.drawString(font, line, x, y, 0xFFFFFF | (alpha << 24), true);
            y += lineHeight;
        }
    }

    private HintsClient() {}
}
