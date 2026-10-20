package dev.seafarers.ship;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import dev.seafarers.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Getting a ship off the rocks: any of her crew can have a ship that has run aground towed to open water nearby
 * (/crew free, or the voyage table's Ship tab). She comes up level, at rest, with her bow as it was, and the crew
 * aboard come with her. Once in freeShipCooldownMinutes per ship; never for a ship that's sinking.
 */
public final class Refloat {
    /** Game time each ship was last freed. */
    private static final Map<UUID, Long> FREED = new HashMap<>();

    public static boolean free(ServerPlayer player, Reply reply) {
        if (!SeafarersConfig.ALLOW_FREEING.get()) {
            reply.fail("message.seafarers.free_off");
            return false;
        }
        ServerLevel level = player.serverLevel();
        UUID shipId = Crews.get(level).shipOf(player.getUUID());
        if (shipId == null) {
            reply.fail("message.seafarers.no_ship");
            return false;
        }
        SubLevel sub = SubLevelContainer.getContainer(level).getSubLevel(shipId);
        HelmBlockEntity helm = sub == null ? null : Ships.helmOf(sub);
        if (!(sub instanceof ServerSubLevel ship) || helm == null) {
            reply.fail("message.seafarers.brig_far");
            return false;
        }
        if (helm.sinking()) {
            reply.fail("message.seafarers.free_sinking");
            return false;
        }
        long now = level.getGameTime();
        long wait = FREED.getOrDefault(shipId, -1_000_000L) + SeafarersConfig.FREE_COOLDOWN_MINUTES.get() * 1200L - now;
        if (wait > 0) {
            reply.fail("message.seafarers.free_wait", (wait + 1199) / 1200);
            return false;
        }
        if (!aground(level, ship)) {
            reply.fail("message.seafarers.free_afloat");
            return false;
        }
        Pose3dc pose = ship.logicalPose();
        Vector3d from = new Vector3d(pose.position());   // (the pose is live: it moves with the tow)
        AABB box = ship.boundingBox().toMojang();
        double reach = Math.max(box.getXsize(), box.getZsize()) / 2 + 4;
        BlockPos spot = openWater(level, BlockPos.containing(pose.position().x(), pose.position().y(), pose.position().z()), reach);
        if (spot == null) {
            reply.fail("message.seafarers.free_nowhere");
            return false;
        }
        List<ServerPlayer> aboard = level.getEntitiesOfClass(ServerPlayer.class, box.inflate(2));
        // level, at rest, her bow as it was, riding at about her usual height on the water
        Quaterniond q = new Quaterniond(pose.orientation());
        Vector3d up = q.transform(new Vector3d(0, 1, 0));
        Quaterniond level0 = new Quaterniond().rotationTo(up, new Vector3d(0, 1, 0)).mul(q);
        double y = spot.getY() + 1 + 2.0;
        RigidBodyHandle handle = RigidBodyHandle.of(ship);
        handle.teleport(new Vector3d(spot.getX() + 0.5, y, spot.getZ() + 0.5), level0);
        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(), handle.getAngularVelocity(new Vector3d()).negate());
        FREED.put(shipId, now);
        int dist = (int) Math.sqrt(spot.distToCenterSqr(from.x, spot.getY() + 0.5, from.z));
        // her crew aboard come with her, once she's there
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 2, () -> {
            for (ServerPlayer p : aboard) {
                Vec3 to = Mermaids.destination(level, p);
                if (to != null && p.isAlive()) {
                    p.teleportTo(level, to.x, to.y, to.z, p.getYRot(), p.getXRot());
                }
            }
            Vec3 at = helm.worldPos();
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 2f, 0.7f);
        }));
        Component said = Component.translatable("message.seafarers.freed", player.getDisplayName(), dist).withStyle(ChatFormatting.AQUA);
        for (UUID id : Crews.get(level).crewOf(shipId)) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
            if (p != null) {
                p.sendSystemMessage(said);
            }
        }
        Seafarers.LOGGER.info("PROBE freed {} {} blocks to {}", ship.getName(), dist, spot.toShortString());
        return true;
    }

    /** Whether any of her hull or fittings is pressed against land (not water, not another ship). */
    public static boolean aground(ServerLevel level, ServerSubLevel ship) {
        BoundingBox3ic b = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        double[][] dirs = {{0.8, 0, 0}, {-0.8, 0, 0}, {0, -0.8, 0}, {0, 0, 0.8}, {0, 0, -0.8}};
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    BlockState state = level.getBlockState(p.set(x, y, z));
                    if (state.isAir() || state.is(ModBlocks.BILGE_WATER.get())) {
                        continue;
                    }
                    Vec3 w = Ships.toWorld(level, Vec3.atCenterOf(p));
                    for (double[] d : dirs) {
                        BlockState there = level.getBlockState(BlockPos.containing(w.x + d[0], w.y + d[1], w.z + d[2]));
                        if (!there.isAir() && there.getFluidState().isEmpty() && there.blocksMotion()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** The nearest patch of open sea, deep enough and wide enough for her, within ~150 blocks; its top water block. */
    @Nullable
    private static BlockPos openWater(ServerLevel level, BlockPos from, double reach) {
        for (int dist = (int) reach + 8; dist <= 150; dist += 8) {
            for (int a = 0; a < 16; a++) {
                double ang = a * Math.PI / 8;
                int x = from.getX() + (int) Math.round(Math.cos(ang) * dist), z = from.getZ() + (int) Math.round(Math.sin(ang) * dist);
                BlockPos top = clearSea(level, x, z, reach);
                if (top != null) {
                    return top;
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos clearSea(ServerLevel level, int cx, int cz, double reach) {
        int r = (int) Math.ceil(reach / 4) * 4;   // (a multiple of 4, so the middle is one of the points tried)
        BlockPos centre = null;
        for (int dx = -r; dx <= r; dx += 4) {
            for (int dz = -r; dz <= r; dz += 4) {
                int x = cx + dx, z = cz + dz;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                    level.getChunk(x >> 4, z >> 4);
                }
                int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockPos top = new BlockPos(x, topY, z);
                if (!level.getFluidState(top).is(FluidTags.WATER) || !level.getFluidState(top.below(5)).is(FluidTags.WATER)) {
                    return null;
                }
                if (dx == 0 && dz == 0) {
                    centre = top;
                }
            }
        }
        // nor another ship in the way
        if (centre != null && SubLevelContainer.getContainer(level).queryIntersecting(new dev.ryanhcode.sable.companion.math.BoundingBox3d(
                cx - reach, centre.getY() - 8, cz - reach, cx + reach, centre.getY() + 40, cz + reach)).iterator().hasNext()) {
            return null;
        }
        return centre;
    }

    private Refloat() {}
}
