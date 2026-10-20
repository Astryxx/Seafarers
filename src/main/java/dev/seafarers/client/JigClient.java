package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Hit by a jig ball: you dance a jig, spinning on the spot, and can't walk, use or attack until it wears off. */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class JigClient {

    private static boolean jigging() {
        var p = Minecraft.getInstance().player;
        return p != null && p.hasEffect(ModEffects.JIG);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        if (jigging()) {
            var in = event.getInput();
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            in.jumping = Minecraft.getInstance().player.tickCount % 10 == 0;  // a little hop now and then
            in.shiftKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (jigging()) {
            double t = Minecraft.getInstance().player.tickCount + event.getPartialTick();
            event.setYaw((float) (event.getYaw() + t * 12 % 360));
            event.setRoll((float) (Math.sin(t * 0.5) * 6));
        }
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (jigging()) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private JigClient() {}
}
