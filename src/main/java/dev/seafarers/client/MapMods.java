package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * Opens an installed map mod's full-screen world map from the map table, without depending on any of them: each
 * map mod registers a key binding that opens its map, and this presses it (as if you'd pressed the key yourself).
 * Which key bindings to try is in the client config (mapTable.mapModKeys), so a map mod that isn't listed can be
 * added by its key binding name.
 */
public final class MapMods {
    private static Field clickCount;
    private static boolean warned;

    /** The first configured map key binding a loaded mod has registered, or null. */
    @Nullable
    public static KeyMapping find() {
        Minecraft mc = Minecraft.getInstance();
        for (String name : SeafarersConfig.MAP_MOD_KEYS.get()) {
            for (KeyMapping k : mc.options.keyMappings) {
                if (k.getName().equals(name)) {
                    return k;
                }
            }
        }
        return null;
    }

    /**
     * Opens the map mod's world map if there is one and the config allows it (and the player isn't sneaking: a
     * sneak-use always opens the built-in chart, for marking it). Returns whether it did.
     */
    public static boolean tryOpen() {
        SeafarersConfig.MapMode mode = SeafarersConfig.MAP_TABLE_MODE.get();
        Minecraft mc = Minecraft.getInstance();
        if (mode == SeafarersConfig.MapMode.BUILT_IN || mc.player == null || mc.player.isShiftKeyDown()) {
            return false;
        }
        KeyMapping key = find();
        if (key == null) {
            if (mode == SeafarersConfig.MapMode.MAP_MOD && !warned) {
                warned = true;
                Seafarers.LOGGER.warn("Map table set to open a map mod, but none of the key bindings in mapTable.mapModKeys is registered");
            }
            return false;
        }
        try {
            if (clickCount == null) {
                clickCount = KeyMapping.class.getDeclaredField("clickCount");
                clickCount.setAccessible(true);
            }
            clickCount.setInt(key, clickCount.getInt(key) + 1);
        } catch (ReflectiveOperationException | RuntimeException e) {
            KeyMapping.click(key.getKey()); // the same, through whatever key it's bound to
        }
        return true;
    }

    private MapMods() {}
}
