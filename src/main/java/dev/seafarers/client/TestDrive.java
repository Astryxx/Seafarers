package dev.seafarers.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.network.TestDrivePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Development only: walks the player forward for the ticks the server asked (see TestDrivePayload); with a
 * negative count, logs that many frames of the ship underfoot and the camera instead (where each is drawn), to
 * measure how smoothly she moves on screen.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class TestDrive {
    private static int ticks;
    private static boolean sneak, jump;
    private static int frames;

    public static void start(TestDrivePayload p) {
        if (p.ticks() < 0) {
            frames = -p.ticks();
            return;
        }
        ticks = p.ticks();
        sneak = p.sneak();
        jump = p.jump();
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (ticks <= 0 || Minecraft.getInstance().player == null) {
            return;
        }
        ticks--;
        var in = event.getInput();
        in.up = true;
        in.forwardImpulse = sneak ? 0.3f : 1f;
        in.shiftKeyDown = sneak;
        in.jumping = jump;
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (frames <= 0 || mc.player == null || mc.level == null) {
            return;
        }
        frames--;
        SubLevel ship = Sable.HELPER.getTrackingSubLevel(mc.player);
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = mc.player.getEyePosition(pt);
        String s = "none";
        if (ship instanceof ClientSubLevel c) {
            var p = c.renderPose(pt);
            var fwd = p.transformNormal(new Vec3(0, 0, 1));
            s = String.format("%.4f %.4f %.4f %.3f", p.position().x(), p.position().y(), p.position().z(),
                    Math.toDegrees(Math.atan2(-fwd.x, fwd.z)));
        }
        Seafarers.LOGGER.info("PROBE frame {} pt={} ship={} cam={} {} {} camYaw={}", System.nanoTime() / 1000,
                String.format("%.3f", mc.level.getGameTime() + pt), s,
                String.format("%.4f", cam.x), String.format("%.4f", cam.y), String.format("%.4f", cam.z), String.format("%.3f", mc.player.getViewYRot(pt)));
    }

    private TestDrive() {}
}
