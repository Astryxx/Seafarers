package dev.seafarers.ship;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player stands to work a station: a fixed spot just behind it (the wheel, a cannon's breech, a rope's
 * cleat), facing the way it does. Taking a station puts them there; it moves with the ship, and their own movement
 * keys are taken by the station, so they stay put until they let go.
 */
public final class Posts {
    /**
     * Puts the player back from a station block by {@code back} blocks, away from the way it faces, standing on its
     * floor and looking the way it faces.
     */
    public static void place(ServerPlayer player, BlockPos stationPos, Direction facing, double back, float pitch) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 plot = Vec3.atBottomCenterOf(stationPos).subtract(facing.getStepX() * back, 0, facing.getStepZ() * back);
        Vec3 world = Ships.toWorld(level, plot);
        Vec3 dir = Ships.toWorldNormal(level, stationPos, Vec3.atLowerCornerOf(facing.getNormal()));
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        player.teleportTo(level, world.x, world.y + 0.02, world.z, yaw, pitch);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private Posts() {}
}
