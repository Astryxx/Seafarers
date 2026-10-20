package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.network.HintPayload;
import dev.seafarers.registry.ModBlocks;
import dev.seafarers.ship.block.SupplyBarrelBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Hints: the first few times a player uses something aboard (the wheel, a cannon, the capstan...), a line above
 * the hotbar says how it works, with the player's own keys. The server counts how often each hint has been
 * shown to each player (config hintTimes); the client shows it unless the player turned hints off.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Hints {
    private static final String SHOWN = "seafarers:hints";
    /** How long a hint holds the line above the hotbar, in ticks. */
    public static final int HOLD = 120;

    private static final Map<Supplier<? extends Block>, String> BLOCKS = Map.ofEntries(
            Map.entry(ModBlocks.HELM, "helm"), Map.entry(ModBlocks.SAIL_ROPE, "sail_rope"), Map.entry(ModBlocks.BRACE_ROPE, "brace_rope"),
            Map.entry(ModBlocks.ANCHOR, "capstan"), Map.entry(ModBlocks.CANNON, "cannon"), Map.entry(ModBlocks.HARPOON, "harpoon"),
            Map.entry(ModBlocks.OARS, "oars"), Map.entry(ModBlocks.STOVE, "stove"), Map.entry(ModBlocks.WATER_BARREL, "water_barrel"),
            Map.entry(ModBlocks.MAP_TABLE, "map_table"), Map.entry(ModBlocks.VOYAGE_TABLE, "quest_table"), Map.entry(ModBlocks.MATTRESS, "bunk"),
            Map.entry(ModBlocks.FLAG_BOX, "flag_box"), Map.entry(ModBlocks.SHIP_BELL, "bell"), Map.entry(ModBlocks.ROWBOAT_HOOK, "rowboat_hook"),
            Map.entry(ModBlocks.BUCKET_RACK, "bucket_rack"), Map.entry(ModBlocks.HULL_HOLE, "hull_hole"));

    @Nullable
    private static String hintFor(BlockState state) {
        if (state.is(ModBlocks.SUPPLY_BARREL.get())) {
            return state.getValue(SupplyBarrelBlock.SUPPLY) == SupplyBarrelBlock.Supply.GROG ? "grog_barrel" : null;
        }
        for (Map.Entry<Supplier<? extends Block>, String> e : BLOCKS.entrySet()) {
            if (state.is(e.getKey().get())) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Shows a hint to this player, if they haven't seen it the config's number of times yet. */
    public static void offer(ServerPlayer player, String hint) {
        int times = SeafarersConfig.HINT_TIMES.get();
        CompoundTag shown = player.getPersistentData().getCompound(SHOWN);
        if (times <= 0 || shown.getInt(hint) >= times) {
            return;
        }
        shown.putInt(hint, shown.getInt(hint) + 1);
        player.getPersistentData().put(SHOWN, shown);
        PacketDistributor.sendToPlayer(player, new HintPayload(hint));
        Seafarers.LOGGER.info("PROBE hint {} to {} ({} of {})", hint, player.getGameProfile().getName(), shown.getInt(hint), times);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        String hint = hintFor(event.getLevel().getBlockState(event.getPos()));
        if (hint != null) {
            offer(player, hint);
        }
    }

    /** The counts carry over when a player respawns (the game makes a new player object). */
    @SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        CompoundTag shown = event.getOriginal().getPersistentData().getCompound(SHOWN);
        if (!shown.isEmpty()) {
            event.getEntity().getPersistentData().put(SHOWN, shown.copy());
        }
    }

    private Hints() {}
}
