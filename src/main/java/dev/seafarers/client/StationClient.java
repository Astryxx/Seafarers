package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.network.StationInputPayload;
import dev.seafarers.network.StationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * While the player operates a station they stay put: at the helm A/D turn the wheel; at the oars W/S row and
 * A/D turn; at a cannon or harpoon gun the mouse aims and left click fires (the harpoon reels in with W).
 * Sneak lets go. Only changes in input are sent to the server.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class StationClient {
    private static int kind = StationPayload.NONE;
    @Nullable
    private static BlockPos pos;
    private static float lastSteer;
    private static float lastForward;
    /** Recoil kick of the view after firing, decaying to 0. */
    private static float kick;

    public static void onStation(StationPayload payload) {
        if (payload.kind() == StationPayload.NONE && pos != null && !pos.equals(payload.pos())) {
            return; // a station they'd already left letting go late: they're at another one now
        }
        kind = payload.kind();
        pos = kind == StationPayload.NONE ? null : payload.pos();
        lastSteer = 0;
        lastForward = 0;
        gunCamera(kind == StationPayload.CANNON || kind == StationPayload.HARPOON);
    }

    /** The camera the player had before taking a gun (null while not at one). */
    @Nullable
    private static net.minecraft.client.CameraType before;

    /**
     * At a cannon or harpoon gun the view moves out behind the gunner's shoulder: as the gun turns to follow the
     * mouse, the camera swings with it, looking along the barrel. Their own camera setting comes back after.
     */
    private static void gunCamera(boolean on) {
        var options = Minecraft.getInstance().options;
        if (on && before == null && dev.seafarers.SeafarersConfig.GUN_CAMERA.get()) {
            before = options.getCameraType();
            options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        } else if (!on && before != null) {
            options.setCameraType(before);
            before = null;
        }
    }

    @SubscribeEvent
    public static void onCameraDistance(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent event) {
        if (before != null && pos != null) {
            event.setDistance(2.2f);
        }
    }

    /** In the gun camera the gunner's own body would sit right over the sights: it isn't drawn (others still see it). */
    @SubscribeEvent
    public static void onRenderPlayer(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre event) {
        if (before != null && pos != null && event.getEntity() == Minecraft.getInstance().player) {
            event.setCanceled(true);
        }
    }

    public static boolean operating() {
        return pos != null;
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (pos == null) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            pos = null;
            return;
        }
        Input input = event.getInput();
        if (input.shiftKeyDown) {
            PacketDistributor.sendToServer(new StationInputPayload(pos, 0, 0, false, true));
            pos = null;
            gunCamera(false);
            input.shiftKeyDown = false;
            return;
        }
        if (kind == StationPayload.HELM || kind == StationPayload.OARS || kind == StationPayload.HARPOON || kind == StationPayload.ROPE) {
            // leftImpulse is +1 for A (port) and -1 for D (starboard)
            float steer = kind == StationPayload.HARPOON ? 0 : -input.leftImpulse;
            float forward = kind == StationPayload.HELM ? 0 : input.forwardImpulse;
            if (kind == StationPayload.ROPE && !(Minecraft.getInstance().level.getBlockState(pos).getBlock() instanceof dev.seafarers.ship.block.SailRopeBlock)) {
                pos = null; // the rope's gone
                return;
            }
            if (steer != lastSteer || forward != lastForward) {
                lastSteer = steer;
                lastForward = forward;
                PacketDistributor.sendToServer(new StationInputPayload(pos, steer, forward, false, false));
            }
        }
        input.leftImpulse = 0;
        input.forwardImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
    }

    /** At a cannon, left click fires instead of punching. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (pos != null && (kind == StationPayload.CANNON || kind == StationPayload.HARPOON) && event.isAttack()) {
            PacketDistributor.sendToServer(new StationInputPayload(pos, 0, lastForward, true, false));
            kick = kind == StationPayload.CANNON ? 1f : 0.3f;
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /** Firing a cannon throws your view up a little, then it settles. */
    @SubscribeEvent
    public static void onCamera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (kick > 0.001f) {
            event.setPitch(event.getPitch() - kick * 6f);
            event.setRoll(event.getRoll() + kick * 1.5f);
            kick *= 0.85f;
        }
    }

    /** At a cannon or a harpoon gun: the aiming reticle shows. */
    /** Whether this player is at the helm (or the oars) at pos: their own keys turn its wheel. */
    public static boolean operatingHelm(BlockPos at) {
        return pos != null && (kind == StationPayload.HELM || kind == StationPayload.OARS) && pos.equals(at);
    }

    /** The steering this player is giving right now (-1 port .. +1 starboard). */
    public static float steer() {
        return lastSteer;
    }

    public static boolean atCannon() {
        return pos != null && (kind == StationPayload.CANNON || kind == StationPayload.HARPOON);
    }

    private StationClient() {}
}
