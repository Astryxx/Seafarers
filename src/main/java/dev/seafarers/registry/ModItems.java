package dev.seafarers.registry;

import dev.seafarers.Seafarers;
import dev.seafarers.ship.Ammo;
import dev.seafarers.ship.item.AmmoItem;
import dev.seafarers.ship.item.ShipDeedItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Seafarers.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Seafarers.MODID);

    public static final DeferredItem<AmmoItem> CANNONBALL = ITEMS.register("cannonball",
            () -> new AmmoItem(Ammo.CANNONBALL, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<AmmoItem> CHAIN_SHOT = ITEMS.register("chain_shot",
            () -> new AmmoItem(Ammo.CHAIN_SHOT, new Item.Properties().stacksTo(16)));
    /** Firebombs and the cursed cannonballs, one item each. */
    public static final java.util.Map<Ammo, DeferredItem<AmmoItem>> AMMO = new java.util.EnumMap<>(Ammo.class);

    static {
        for (Ammo ammo : Ammo.values()) {
            if (ammo == Ammo.FIREBOMB || ammo.cursed()) {
                AMMO.put(ammo, ITEMS.register(ammo.name().toLowerCase(), () -> new AmmoItem(ammo, new Item.Properties().stacksTo(16))));
            }
        }
    }
    public static final DeferredItem<dev.seafarers.ship.item.ShipBucketItem> SHIP_BUCKET = ITEMS.register("ship_bucket",
            () -> new dev.seafarers.ship.item.ShipBucketItem(dev.seafarers.ship.item.ShipBucketItem.Content.EMPTY, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.seafarers.ship.item.ShipBucketItem> SHIP_BUCKET_WATER = ITEMS.register("ship_bucket_water",
            () -> new dev.seafarers.ship.item.ShipBucketItem(dev.seafarers.ship.item.ShipBucketItem.Content.WATER, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.seafarers.ship.item.ShipBucketItem> SHIP_BUCKET_VOMIT = ITEMS.register("ship_bucket_vomit",
            () -> new dev.seafarers.ship.item.ShipBucketItem(dev.seafarers.ship.item.ShipBucketItem.Content.VOMIT, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<dev.seafarers.ship.item.GrogItem> GROG = ITEMS.register("grog",
            () -> new dev.seafarers.ship.item.GrogItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<dev.seafarers.ship.item.GrogItem> GROG_BUCKET = ITEMS.register("grog_bucket",
            () -> new dev.seafarers.ship.item.GrogItem(new Item.Properties().stacksTo(1), 2, () -> net.minecraft.world.item.Items.BUCKET));
    public static final DeferredItem<Item> BANANA = ITEMS.register("banana",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.6f)
                    .fast().alwaysEdible().build())));
    /** What's left of food left too long on the stove: edible, barely. */
    public static final DeferredItem<Item> CHARRED_FOOD = ITEMS.register("charred_food",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f)
                    .effect(() -> new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 20 * 15, 0), 0.6f)
                    .build())));
    public static final DeferredItem<ShipDeedItem> SLOOP_DEED = ITEMS.register("sloop_deed",
            () -> new ShipDeedItem("sloop", new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<ShipDeedItem> BRIGANTINE_DEED = ITEMS.register("brigantine_deed",
            () -> new ShipDeedItem("brigantine", new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC)));
    public static final DeferredItem<ShipDeedItem> GALLEON_DEED = ITEMS.register("galleon_deed",
            () -> new ShipDeedItem("galleon", new Item.Properties().stacksTo(1).fireResistant().rarity(net.minecraft.world.item.Rarity.EPIC)));
    /** The brig's door is two blocks tall, placed like a door. */
    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> BRIG_DOOR = ITEMS.register("brig_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.BRIG_DOOR.get(), new Item.Properties()));
    /** Points to the ship you crew. */
    public static final DeferredItem<dev.seafarers.ship.item.ShipCompassItem> SHIPS_COMPASS = ITEMS.register("ships_compass",
            () -> new dev.seafarers.ship.item.ShipCompassItem(new Item.Properties().stacksTo(1)));
    /** A rowboat: launched on the water in front of you; nobody's ship, anyone can row it. */
    public static final DeferredItem<ShipDeedItem> ROWBOAT = ITEMS.register("rowboat",
            () -> new ShipDeedItem("rowboat", false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ShipDeedItem> ROWBOAT_CANNON = ITEMS.register("rowboat_cannon",
            () -> new ShipDeedItem("rowboat_cannon", false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ShipDeedItem> ROWBOAT_HARPOON = ITEMS.register("rowboat_harpoon",
            () -> new ShipDeedItem("rowboat_harpoon", false, new Item.Properties().stacksTo(1)));

    static {
        for (var block : java.util.List.of(ModBlocks.HULL_PLANKS, ModBlocks.HULL_BOTTOM, ModBlocks.HULL_BOTTOM_STAIRS,
                ModBlocks.HULL_TRIM, ModBlocks.DECK_PLANKS, ModBlocks.DECK_STAIRS, ModBlocks.RAILING, ModBlocks.KEEL,
                ModBlocks.MAST, ModBlocks.SPAR, ModBlocks.BUCKET_RACK, ModBlocks.SHIP_LANTERN, ModBlocks.BOWSPRIT, ModBlocks.DECK_SLAB, ModBlocks.ROPE_LADDER, ModBlocks.HELM, ModBlocks.SAIL_YARD, ModBlocks.SAIL_ROPE, ModBlocks.BRACE_ROPE,
                ModBlocks.ANCHOR, ModBlocks.CANNON, ModBlocks.SUPPLY_BARREL, ModBlocks.HARPOON, ModBlocks.MAP_TABLE,
                ModBlocks.VOYAGE_TABLE, ModBlocks.BINNACLE, ModBlocks.MATTRESS, ModBlocks.FIGUREHEAD, ModBlocks.FLAG,
                ModBlocks.FLAG_BOX, ModBlocks.STOVE, ModBlocks.OARS, ModBlocks.ROWBOAT_HOOK,
                ModBlocks.SEA_CHEST, ModBlocks.CASK, ModBlocks.SHIP_BELL, ModBlocks.WATER_BARREL)) {
            ITEMS.registerSimpleBlockItem(block);
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.seafarers"))
            .icon(() -> new ItemStack(SLOOP_DEED.get()))
            .displayItems((params, out) -> ITEMS.getEntries().forEach(item -> out.accept(item.get())))
            .build());

    private ModItems() {}
}
