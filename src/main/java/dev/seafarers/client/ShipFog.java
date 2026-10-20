package dev.seafarers.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Below the waterline in a ship's hold the camera is, as far as the game knows, in the sea the ship floats in, so
 * it would draw underwater fog: a dark, night-like sky through the hatch and a murky lower deck. Inside the dry hold
 * the fog is drawn as it is in the open air instead.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class ShipFog {

    /** Whether the camera is in the sea by the game's reckoning, but inside a ship's dry hold. */
    private static boolean inDryHold(Camera camera) {
        if (camera.getFluidInCamera() != FogType.WATER) {
            return false;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        Vec3 p = camera.getPosition();
        for (SubLevel s : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(p.x - 0.1, p.y - 0.1, p.z - 0.1, p.x + 0.1, p.y + 0.1, p.z + 0.1))) {
            if (s instanceof ClientSubLevel ship) {
                HelmBlockEntity helm = Ships.helmOf(ship);
                Vec3 local = ship.renderPose().transformPositionInverse(p);
                if (helm != null && helm.isDry(BlockPos.containing(local))) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        if (inDryHold(event.getCamera())) {
            // as the game draws fog in the open air
            float far = Minecraft.getInstance().gameRenderer.getRenderDistance();
            event.setNearPlaneDistance(far - Mth.clamp(far / 10f, 4f, 64f));
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inDryHold(event.getCamera())) {
            return;
        }
        // the open-air fog: the biome's fog colour lit by the time of day, blended toward the sky as the game does
        ClientLevel level = Minecraft.getInstance().level;
        float pt = (float) event.getPartialTick();
        Vec3 pos = event.getCamera().getPosition();
        float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(pt) * (float) (Math.PI * 2)) * 2f + 0.5f, 0f, 1f);
        Vec3 fog = level.effects().getBrightnessDependentFogColor(
                Vec3.fromRGB24(level.getBiome(BlockPos.containing(pos)).value().getFogColor()), daylight);
        Vec3 sky = level.getSkyColor(pos, pt);
        float blend = 1f - (float) Math.pow(0.25 + 0.75 * Minecraft.getInstance().options.getEffectiveRenderDistance() / 32.0, 0.25);
        event.setRed((float) (fog.x + (sky.x - fog.x) * blend));
        event.setGreen((float) (fog.y + (sky.y - fog.y) * blend));
        event.setBlue((float) (fog.z + (sky.z - fog.z) * blend));
    }

    private ShipFog() {}
}
