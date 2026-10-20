package dev.seafarers.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ladders on a moving ship. Vanilla only climbs a ladder the player is standing in, and on a ship that sways and
 * drifts under you the player keeps slipping out of the ladder's block, so climbs stall partway. This keeps the
 * player on any climbable block of a ship their body touches, behaving as a ladder does: forward (or pushing into
 * it) climbs, sneaking holds on, otherwise a slow slide down, and no fall damage. Player movement is the client's,
 * so this runs on the client.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class ShipLadders {
    private static final double CLIMB = 0.2, SLIDE = -0.15;

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.isSpectator() || player.getAbilities().flying) {
            return;
        }
        if (!touchingShipLadder(player)) {
            return;
        }
        Vec3 v = player.getDeltaMovement();
        double y;
        if (player.input.up || player.horizontalCollision || player.input.jumping) {
            y = CLIMB;
        } else if (player.isShiftKeyDown()) {
            y = Math.max(v.y, 0);
        } else {
            y = Math.max(v.y, SLIDE);
        }
        player.setDeltaMovement(v.x, y, v.z);
        player.resetFallDistance();
    }

    /** Whether the player's body touches a climbable block on a ship (checked at feet, waist and head height). */
    private static boolean touchingShipLadder(LocalPlayer player) {
        AABB box = player.getBoundingBox().inflate(0.15, 0, 0.15);
        for (SubLevel ship : Sable.HELPER.getAllIntersecting(player.level(), new BoundingBox3d(box))) {
            for (double dy : new double[]{0.1, 0.9, 1.6}) {
                for (double dx : new double[]{-0.4, 0, 0.4}) {
                    for (double dz : new double[]{-0.4, 0, 0.4}) {
                        Vec3 local = ship.logicalPose().transformPositionInverse(player.position().add(dx, dy, dz));
                        if (player.level().getBlockState(BlockPos.containing(local)).is(BlockTags.CLIMBABLE)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private ShipLadders() {}
}
