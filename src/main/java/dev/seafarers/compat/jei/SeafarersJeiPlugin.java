package dev.seafarers.compat.jei;

import dev.seafarers.Seafarers;
import dev.seafarers.compat.ItemInfo;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** JEI (optional): info pages for the mod's items. Only loaded when JEI is installed. */
@JeiPlugin
public class SeafarersJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Seafarers.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (ItemInfo.Entry e : ItemInfo.all()) {
            List<ItemStack> stacks = e.items().stream().map(s -> new ItemStack(s.get())).toList();
            registration.addItemStackInfo(stacks, e.text());
        }
        Seafarers.LOGGER.info("PROBE jei info pages {}", ItemInfo.all().size());
    }
}
