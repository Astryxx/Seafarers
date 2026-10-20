package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModBlockEntities;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = Seafarers.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SeafarersClient {
    /** Sea-water blue for the bilge, a little greener than open water. */
    private static final int BILGE_COLOR = 0x3A7A8C;

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CANNONBALL.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.5f, false));
        // the splash is drawn with particles only
        event.registerEntityRenderer(ModEntities.SPLASH.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.SAIL_YARD.get(), SailRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HELM.get(), HelmRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CANNON.get(), CannonRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BOWSPRIT.get(), BowspritRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CAPSTAN.get(), CapstanRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HARPOON.get(), HarpoonRenderers.Gun::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FLAG.get(), GalleyRenderers.Flag::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STOVE.get(), GalleyRenderers.Stove::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MAP_TABLE.get(), MapTableScreen.TableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.VOYAGE_TABLE.get(), GalleyRenderers.Voyages::new);
        event.registerEntityRenderer(ModEntities.HARPOON.get(), HarpoonRenderers.Bolt::new);
        event.registerEntityRenderer(ModEntities.SHIPWRECK_BARREL.get(), ShipwreckBarrelRenderer::new);
        event.registerEntityRenderer(ModEntities.MERMAID.get(), MermaidRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MermaidRenderer.LAYER, MermaidRenderer::createLayer);
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(PartRenderer.HELM_WHEEL);
        event.register(PartRenderer.CANNON_BARREL);
        event.register(PartRenderer.HARPOON_GUN);
        event.register(PartRenderer.HARPOON_BOLT);
    }

    /** The Ship's Compass: vanilla's needle, pointed at the ship by her lodestone tracker. */
    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(SeafarersClient::smoothShips);
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(dev.seafarers.registry.ModItems.SHIPS_COMPASS.get(),
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                new net.minecraft.client.renderer.item.CompassItemPropertyFunction((level, stack, entity) -> {
                    var tracker = stack.get(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER);
                    return tracker == null ? null : tracker.target().orElse(null);
                })));
    }

    /**
     * Sable draws ships between the server's updates with a delay of its own; at its default (1.5 ticks) it often
     * runs out of updates and ships judder on screen. Measured on a sailing sloop, 3 ticks keeps her gliding.
     */
    private static void smoothShips() {
        try {
            var delay = dev.ryanhcode.sable.SableClientConfig.INTERPOLATION_DELAY;
            if (dev.seafarers.SeafarersConfig.SMOOTH_SHIPS.get() && delay.get() < 3.0) {
                delay.set(3.0);
                dev.ryanhcode.sable.SableClientConfig.SPEC.save();
                Seafarers.LOGGER.info("Raised Sable's ship interpolation delay to 3 ticks for smoother ships (Seafarers client config: smoothShips)");
            }
        } catch (Throwable t) {
            Seafarers.LOGGER.warn("Couldn't adjust Sable's interpolation delay: {}", t.toString());
        }
    }

    /** The Ship's Compass is brass: vanilla's compass faces, tinted. */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xFFF0C878 : -1, dev.seafarers.registry.ModItems.SHIPS_COMPASS.get());
    }

    @SubscribeEvent
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> BILGE_COLOR, ModBlocks.BILGE_WATER.get());
        // a sinking hull darkens as it takes on water
        // tint 1 is the painted planking (the ship's hull livery); everything darkens as she floods
        event.register((state, level, pos, tint) -> {
            int c = tint == 1 && pos != null ? Liveries.hull(pos) : 0xFFFFFF;
            if (state.getValue(dev.seafarers.ship.block.VoxelHullBlock.FLOODED)) {
                c = ((((c >> 16) & 255) * 7 / 16) << 16) | ((((c >> 8) & 255) * 7 / 16) << 8) | ((c & 255) * 7 / 16);
            }
            return c;
        }, ModBlocks.VOXEL_HULL.get());
        // a lantern that's been put out: the glass goes dark (tint 0 is only on the unlit model's cage)
        event.register((state, level, pos, tint) -> 0x3A3F4A, ModBlocks.SHIP_LANTERN.get());
        // the water in a water barrel: sea blue (a plot has no biome of its own to take the colour from)
        event.register((state, level, pos, tint) -> tint == 0 ? 0x3F76E4 : 0xFFFFFF, ModBlocks.WATER_BARREL.get());
    }

    private SeafarersClient() {}
}
