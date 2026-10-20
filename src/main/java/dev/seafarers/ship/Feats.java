package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Feats for the advancements (data/seafarers/advancement, made by tools/gen_advancements.py). The events:
 * start, launch, full_sail, anchor_turn, crew, voyage, chart, patch, bail, douse, mend, hit_ship, curse,
 * sink_enemy, human_cannonball, went_down, grog, vomit, salvage, harpoon, brig, big_ship, mermaid.
 */
public final class Feats {
    public static void award(Entity who, String event) {
        if (who instanceof ServerPlayer player && !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
            ModTriggers.FEAT.get().trigger(player, event);
            Seafarers.LOGGER.debug("feat {} {}", player.getGameProfile().getName(), event);
        }
    }

    private Feats() {}
}
