package dev.seafarers.ship.block;

import dev.seafarers.Seafarers;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** A gunner fired out of a cannon: a smoke trail on the way, and no fall damage on landing. */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class CannonFlight {
    public static final String TAG = "seafarers_cannon_flight";

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level() instanceof ServerLevel level && p.getPersistentData().contains(TAG)) {
            long since = level.getGameTime() - p.getPersistentData().getLong(TAG);
            if (since > 4 && (p.onGround() || p.isInWater() || since > 200)) {
                p.getPersistentData().remove(TAG);
                Seafarers.LOGGER.info("PROBE cannon flight landed after {} ticks", since);
            } else {
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity().getPersistentData().contains(TAG)) {
            event.setCanceled(true);
            event.getEntity().getPersistentData().remove(TAG);
        }
    }

    private CannonFlight() {}
}
