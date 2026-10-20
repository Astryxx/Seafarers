package dev.seafarers.compat.emi;

import dev.seafarers.Seafarers;
import dev.seafarers.compat.ItemInfo;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.List;

/** EMI (optional): info pages for the mod's items. Only loaded when EMI is installed. */
@EmiEntrypoint
public class SeafarersEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        for (ItemInfo.Entry e : ItemInfo.all()) {
            List<EmiIngredient> stacks = e.items().stream().<EmiIngredient>map(s -> EmiStack.of(s.get())).toList();
            registry.addRecipe(new EmiInfoRecipe(stacks, List.of(e.text()), Seafarers.id("info/" + e.key())));
        }
        Seafarers.LOGGER.info("PROBE emi info pages {}", ItemInfo.all().size());
    }
}
