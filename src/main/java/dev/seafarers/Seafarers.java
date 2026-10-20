package dev.seafarers;

import com.mojang.logging.LogUtils;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.registry.ModEntities;
import dev.seafarers.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Seafarers.MODID)
public class Seafarers {
    public static final String MODID = "seafarers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Seafarers(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, SeafarersConfig.SERVER_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, SeafarersConfig.CLIENT_SPEC);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        dev.seafarers.registry.ModDataComponents.COMPONENTS.register(modBus);
        dev.seafarers.registry.ModTriggers.TRIGGERS.register(modBus);
        ModItems.TABS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ModBlockEntities::bellsOnShips);
        ModEntities.ENTITIES.register(modBus);
        dev.seafarers.registry.ModEffects.EFFECTS.register(modBus);
        dev.seafarers.registry.ModSounds.SOUNDS.register(modBus);
        dev.seafarers.ship.ShipTickets.init();
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            dev.seafarers.client.ConfigScreen.register(container);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
