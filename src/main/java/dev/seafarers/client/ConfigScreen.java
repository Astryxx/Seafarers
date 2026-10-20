package dev.seafarers.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** The Config button on the Mods screen: NeoForge's own settings screen, with every setting named and explained. */
public final class ConfigScreen {
    public static void register(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private ConfigScreen() {}
}
