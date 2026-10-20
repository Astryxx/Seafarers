package dev.seafarers.compat;

import dev.seafarers.registry.ModBlocks;
import dev.seafarers.registry.ModItems;
import dev.seafarers.ship.Ammo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The info pages recipe viewers (JEI, EMI) show for the mod's items: how you get the ones that can't be crafted,
 * and what the odd ones do. Text in the lang file under info.seafarers.&lt;key&gt;.
 */
public final class ItemInfo {
    public record Entry(List<Supplier<? extends ItemLike>> items, String key) {
        public Component text() {
            return Component.translatable("info.seafarers." + this.key);
        }
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(List.of(ModItems.SLOOP_DEED, ModItems.BRIGANTINE_DEED, ModItems.GALLEON_DEED), "deed"));
        out.add(new Entry(List.of(ModItems.SHIPS_COMPASS), "compass"));
        out.add(new Entry(List.of(ModItems.BRIG_DOOR), "brig"));
        out.add(new Entry(List.of(ModItems.ROWBOAT, ModItems.ROWBOAT_CANNON, ModItems.ROWBOAT_HARPOON), "rowboat"));
        out.add(new Entry(List.of(ModItems.CANNONBALL), "cannonball"));
        out.add(new Entry(List.of(ModItems.CHAIN_SHOT), "chain_shot"));
        List<Supplier<? extends ItemLike>> cursed = new ArrayList<>();
        for (Ammo ammo : Ammo.values()) {
            if (ammo.cursed()) {
                cursed.add(ModItems.AMMO.get(ammo));
            }
        }
        out.add(new Entry(List.of(ModItems.AMMO.get(Ammo.FIREBOMB)), "firebomb"));
        out.add(new Entry(cursed, "cursed"));
        out.add(new Entry(List.of(ModItems.SHIP_BUCKET), "bucket"));
        out.add(new Entry(List.of(ModItems.GROG, ModItems.GROG_BUCKET), "grog"));
        out.add(new Entry(List.of(ModItems.BANANA), "banana"));
        out.add(new Entry(List.of(ModBlocks.HELM, ModBlocks.HULL_PLANKS, ModBlocks.DECK_PLANKS, ModBlocks.MAST, ModBlocks.SAIL_YARD,
                ModBlocks.SAIL_ROPE, ModBlocks.BRACE_ROPE, ModBlocks.ANCHOR, ModBlocks.KEEL), "ship_part"));
        return out;
    }

    private ItemInfo() {}
}
