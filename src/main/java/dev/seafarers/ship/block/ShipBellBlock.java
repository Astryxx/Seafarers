package dev.seafarers.ship.block;

import com.mojang.serialization.MapCodec;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.Crews;
import dev.seafarers.ship.Ships;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The ship's bell: a vanilla bell (its block entity, its swing, its ring), hung from the mast by a beam long enough
 * to reach the mast (see the ship_bell model). Ringing it calls the crew: everyone aboard and every crew member near
 * the ship gets a message naming who rang it.
 */
public class ShipBellBlock extends BellBlock {
    public static final MapCodec<BellBlock> CODEC = simpleCodec(ShipBellBlock::new);

    public ShipBellBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.defaultBlockState().setValue(ATTACHMENT, BellAttachType.SINGLE_WALL));
    }

    @Override
    public MapCodec<BellBlock> codec() {
        return CODEC;
    }

    /** Rung from anywhere on it (vanilla's bell only rings when its body is hit). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (this.attemptToRing(player, level, pos, hit.getDirection().getAxis().isHorizontal() ? hit.getDirection() : state.getValue(FACING).getClockWise())) {
            if (level instanceof ServerLevel server) {
                callCrew(server, pos, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    private static void callCrew(ServerLevel server, BlockPos pos, Player player) {
        Vec3 at = Ships.toWorld(server, Vec3.atCenterOf(pos));
        Set<UUID> told = new HashSet<>();
        var ship = Ships.shipAt(server, pos);
        if (ship != null) {
            for (UUID id : Crews.get(server).crewOf(ship.getUniqueId())) {
                ServerPlayer crew = server.getServer().getPlayerList().getPlayer(id);
                if (crew != null && crew.level() == server && crew.position().distanceTo(at) < 160) {
                    told.add(id);
                }
            }
        }
        for (ServerPlayer near : server.getEntitiesOfClass(ServerPlayer.class, new AABB(at, at).inflate(48))) {
            told.add(near.getUUID());
        }
        for (UUID id : told) {
            ServerPlayer p = server.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                dev.seafarers.ship.Notify.show(p, Component.translatable("message.seafarers.bell", player.getDisplayName())
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        Seafarers.LOGGER.info("PROBE bell rung, {} told", told.size());
    }
}
