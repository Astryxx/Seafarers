package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import dev.seafarers.network.StationPayload;
import dev.seafarers.ship.block.SailRopeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The sail ropes as stations, as in Sea of Thieves: use one to take hold of it (you stand at its cleat), then W lets
 * the sail down and S hauls it up (the length cleat), or A and D swing it round (the angle pulleys). Holding a key
 * keeps pulling, at the same pace as holding use on the rope. Sneak lets go.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class RopeStations {
    private record Hold(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, Hold> HOLDING = new HashMap<>();
    /** The keys each holder is pressing: [steer (-1 A .. +1 D), forward (+1 W .. -1 S)]. */
    private static final Map<UUID, float[]> INPUT = new HashMap<>();

    public static void take(ServerPlayer player, BlockPos pos, BlockState state) {
        HOLDING.put(player.getUUID(), new Hold(player.level().dimension(), pos));
        INPUT.put(player.getUUID(), new float[2]);
        Posts.place(player, pos, state.getValue(SailRopeBlock.FACING), 0.9, 20f);
        PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.ROPE, pos));
    }

    public static boolean holding(ServerPlayer player, BlockPos pos) {
        Hold h = HOLDING.get(player.getUUID());
        return h != null && h.pos().equals(pos);
    }

    public static void input(ServerPlayer player, BlockPos pos, float steer, float forward, boolean letGo) {
        if (!holding(player, pos)) {
            return;
        }
        if (letGo) {
            release(player);
            return;
        }
        INPUT.put(player.getUUID(), new float[]{steer, forward});
    }

    private static void release(ServerPlayer player) {
        Hold h = HOLDING.remove(player.getUUID());
        INPUT.remove(player.getUUID());
        if (h != null) {
            PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.NONE, h.pos()));
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (HOLDING.isEmpty() || server.overworld().getGameTime() % 4 != 0) {
            return; // a pull every four ticks, as holding use repeats
        }
        for (Iterator<Map.Entry<UUID, Hold>> it = HOLDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Hold> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            ServerLevel level = server.getLevel(e.getValue().dimension());
            BlockPos pos = e.getValue().pos();
            BlockState state = level == null ? null : level.getBlockState(pos);
            HelmBlockEntity helm = level == null ? null : Ships.helmOf(level, pos);
            if (player == null || player.level() != level || state == null || !(state.getBlock() instanceof SailRopeBlock rope)
                    || helm == null || player.position().distanceTo(Ships.toWorld(level, Vec3.atCenterOf(pos))) > 3.5 || !player.isAlive()) {
                it.remove();
                INPUT.remove(e.getKey());
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new StationPayload(StationPayload.NONE, pos));
                }
                continue;
            }
            float[] in = INPUT.getOrDefault(e.getKey(), new float[2]);
            int mast = state.getValue(SailRopeBlock.MAST);
            if (rope.kind() == SailRopeBlock.Kind.HOIST && in[1] != 0) {
                helm.changeSails(mast, in[1] > 0 ? 1 : -1, player);
            } else if (rope.kind() == SailRopeBlock.Kind.BRACE && in[0] != 0) {
                helm.braceSails(mast, in[0] > 0 ? 1 : -1);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HOLDING.remove(event.getEntity().getUUID());
        INPUT.remove(event.getEntity().getUUID());
    }

    private RopeStations() {}
}
