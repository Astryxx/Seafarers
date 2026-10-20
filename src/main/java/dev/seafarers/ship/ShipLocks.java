package dev.seafarers.ship;

import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.UUID;

/**
 * A locked ship only works for her crew: anyone else can walk aboard (and open her doors), but not use her wheel,
 * sails, cannons, storage or bunks, nor break or place blocks on her. Ships with no captain are never locked.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class ShipLocks {

    /** Whether this player is kept off the ship block at pos by her lock. */
    public static boolean barred(ServerPlayer player, Level level, BlockPos pos) {
        if (!SeafarersConfig.ALLOW_LOCKING.get() || !(level instanceof ServerLevel server)) {
            return false;
        }
        SubLevel ship = Ships.shipAt(level, pos);
        if (ship == null) {
            return false;
        }
        Crews crews = Crews.get(server);
        UUID id = ship.getUniqueId();
        if (!crews.locked(id) || crews.crewOf(id).contains(player.getUUID()) || Captaincy.overrides(player)) {
            return false;
        }
        // the captain's party or team counts as crew (Open Parties and Claims, FTB Teams)
        UUID captain = crews.captainOf(id);
        return !(SeafarersConfig.TEAMS_SHARE_SHIPS.get() && captain != null
                && dev.seafarers.compat.claims.Claims.sameTeam(server.getServer(), captain, player.getUUID()));
    }

    private static void refuse(ServerPlayer player, Level level, BlockPos pos) {
        SubLevel ship = Ships.shipAt(level, pos);
        String name = ship == null || ship.getName() == null ? "ship" : ship.getName();
        Notify.show(player, Component.translatable("message.seafarers.ship_locked", name).withStyle(ChatFormatting.RED));
    }

    /** Doors, trapdoors and gates: anyone aboard can get about the ship. */
    private static boolean passage(BlockState state) {
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && !passage(event.getLevel().getBlockState(event.getPos()))
                && barred(player, event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            refuse(player, event.getLevel(), event.getPos());
        }
    }

    /** A ship's blocks can't be broken by anyone (config unbreakableShips), whatever the lock. */
    private static boolean unbreakable(Level level, BlockPos pos) {
        return SeafarersConfig.UNBREAKABLE_SHIPS.get() && Ships.shipAt(level, pos) != null;
    }

    @SubscribeEvent
    public static void onPunch(PlayerInteractEvent.LeftClickBlock event) {
        // on both sides, so the client doesn't start the crack animation either
        if (unbreakable(event.getLevel(), event.getPos())
                || event.getEntity() instanceof ServerPlayer player && barred(player, event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && player.level() instanceof Level level
                && (unbreakable(level, event.getPos()) || barred(player, level, event.getPos()))) {
            event.setCanceled(true);
            if (!unbreakable(level, event.getPos())) {
                refuse(player, level, event.getPos());
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && barred(player, player.level(), event.getPos())) {
            event.setCanceled(true);
            refuse(player, player.level(), event.getPos());
        }
    }

    /** Explosions (creepers, TNT, other mods' bombs) leave a locked ship's blocks alone, if the config says so. */
    @SubscribeEvent
    public static void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!SeafarersConfig.LOCKED_RESIST_EXPLOSIONS.get() || !SeafarersConfig.ALLOW_LOCKING.get()
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Crews crews = Crews.get(level);
        event.getAffectedBlocks().removeIf(pos -> {
            SubLevel ship = Ships.shipAt(level, pos);
            return ship != null && crews.locked(ship.getUniqueId());
        });
    }

    private ShipLocks() {}
}
