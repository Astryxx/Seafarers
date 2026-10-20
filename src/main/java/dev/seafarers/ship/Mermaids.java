package dev.seafarers.ship;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.SeafarersConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mermaids, as in Sea of Thieves: a crewmate swimming far from their ship (mermaidDistance) for a few seconds
 * (mermaidDelaySeconds) is met by a mermaid, who takes them back aboard (see MermaidEntity). Her crew's ship must
 * still be afloat; a sailor with no ship is on their own.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Mermaids {
    /** Ticks each sailor has been adrift, far from their ship. */
    private static final Map<UUID, Integer> ADRIFT = new HashMap<>();
    /** The mermaid waiting for each sailor (by entity id). */
    private static final Map<UUID, Integer> WAITING = new HashMap<>();
    /** Close enough to swim back by themselves: she leaves them to it. */
    private static final double NEAR = 12;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld.getGameTime() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            boolean far = SeafarersConfig.MERMAIDS.get() && adrift(player, true)
                    && distanceToShip(player) > SeafarersConfig.MERMAID_DISTANCE.get();
            if (!far) {
                ADRIFT.remove(id);
                continue;
            }
            int t = ADRIFT.merge(id, 10, Integer::sum);
            if (t >= SeafarersConfig.MERMAID_SECONDS.get() * 20 && !waiting(player)) {
                summon(player);
            }
        }
    }

    /**
     * In the water, not aboard or near their ship (which is afloat). She comes only for a sailor out of their depth
     * (deep); once she's there she waits for them as long as they're in the water at all, bobbing at the surface or not.
     */
    public static boolean adrift(ServerPlayer player, boolean deep) {
        if (player.isSpectator() || !player.isAlive() || player.isPassenger() || !player.isInWater()
                || deep && player.getFluidHeight(FluidTags.WATER) < 1.0 && !player.isUnderWater()) {
            return false;
        }
        HelmBlockEntity helm = helmOf(player);
        return helm != null && !helm.sinking() && distanceToShip(player) > NEAR;
    }

    @Nullable
    private static HelmBlockEntity helmOf(ServerPlayer player) {
        UUID ship = Crews.get(player.serverLevel()).shipOf(player.getUUID());
        return ship == null ? null : CrewEvents.helmOfShip(player.serverLevel(), ship);
    }

    /** Blocks from the player to the nearest edge of their ship (her whole bounding box), or infinity. */
    private static double distanceToShip(ServerPlayer player) {
        UUID ship = Crews.get(player.serverLevel()).shipOf(player.getUUID());
        SubLevel sub = ship == null ? null : SubLevelContainer.getContainer(player.serverLevel()).getSubLevel(ship);
        if (sub == null || sub.isRemoved()) {
            return Double.POSITIVE_INFINITY;
        }
        AABB box = sub.boundingBox().toMojang();
        Vec3 p = player.position();
        double dx = Math.max(0, Math.max(box.minX - p.x, p.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - p.y, p.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - p.z, p.z - box.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean waiting(ServerPlayer player) {
        Integer entity = WAITING.get(player.getUUID());
        return entity != null && player.serverLevel().getEntity(entity) instanceof MermaidEntity m && !m.isRemoved();
    }

    /** Brings her up a few blocks ahead of the sailor, at the surface, facing them. */
    private static void summon(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 at = null;
        for (int turn = 0; turn < 8 && at == null; turn++) {
            Vec3 dir = look.yRot((float) Math.toRadians(turn * 45));
            at = surface(level, player.position().add(dir.scale(3.5)));
        }
        if (at == null) {
            return;   // nowhere for her to come up: try again next time
        }
        MermaidEntity mermaid = MermaidEntity.of(level, at, player);
        level.addFreshEntity(mermaid);
        WAITING.put(player.getUUID(), mermaid.getId());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DOLPHIN_PLAY, SoundSource.NEUTRAL, 1.2f, 1.3f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 30, 0.5, 0.2, 0.5, 0.15);
        Notify.show(player, Component.translatable("message.seafarers.mermaid_here").withStyle(ChatFormatting.AQUA));
        Seafarers.LOGGER.info("PROBE mermaid came for {} at {}", player.getGameProfile().getName(), BlockPos.containing(at).toShortString());
    }

    /** Where she rises near a point: the water's surface there (her waist at the waterline), or null if it's not water. */
    @Nullable
    private static Vec3 surface(ServerLevel level, Vec3 near) {
        BlockPos.MutableBlockPos p = BlockPos.containing(near).mutable();
        for (int i = 0; i < 6 && !level.getFluidState(p).is(FluidTags.WATER); i++) {
            p.move(0, -1, 0);
        }
        if (!level.getFluidState(p).is(FluidTags.WATER)) {
            return null;
        }
        for (int i = 0; i < 16 && level.getFluidState(p.above()).is(FluidTags.WATER); i++) {
            p.move(0, 1, 0);
        }
        if (!level.getBlockState(p.above()).isAir()) {
            return null;
        }
        return new Vec3(near.x, p.getY() + 0.92, near.z);   // her waist at the surface
    }

    /** Where the sailor comes back aboard: the brig, if that's where they belong, else one of her respawn spots. */
    @Nullable
    public static Vec3 destination(ServerLevel level, ServerPlayer player) {
        Vec3 brig = Brig.respawnPoint(level, player.getUUID());
        if (brig != null) {
            return brig;
        }
        HelmBlockEntity helm = helmOf(player);
        return helm == null || helm.sinking() ? null : helm.respawnWorldPos(level.random);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ADRIFT.remove(event.getEntity().getUUID());
        WAITING.remove(event.getEntity().getUUID());
    }

    private Mermaids() {}
}
