package dev.seafarers.client;

import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** What grog does to your head: the view rolls and sways, and your feet wander. Stronger the drunker you are. */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class DrunkClient {

    private static float level() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        MobEffectInstance drunk = player.getEffect(ModEffects.DRUNK);
        return drunk == null ? 0 : (drunk.getAmplifier() + 1) / 5f;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float k = level();
        if (k <= 0 || !dev.seafarers.SeafarersConfig.DRUNK_CAMERA.get()) {
            return;
        }
        double t = Minecraft.getInstance().player.tickCount + event.getPartialTick();
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 0.07) * 9 * k));
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 0.045) * 4 * k));
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 0.06 + 1) * 3 * k));
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        float k = level();
        if (k <= 0 || StationClient.operating() || !dev.seafarers.SeafarersConfig.DRUNK_CAMERA.get()) {
            return;
        }
        double t = Minecraft.getInstance().player.tickCount;
        var input = event.getInput();
        if (input.forwardImpulse != 0 || input.leftImpulse != 0) {
            input.leftImpulse = Mth.clamp(input.leftImpulse + (float) Math.sin(t * 0.09) * 0.7f * k, -1f, 1f);
        }
    }

    private DrunkClient() {}
}
