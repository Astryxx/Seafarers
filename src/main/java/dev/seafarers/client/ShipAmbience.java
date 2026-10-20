package dev.seafarers.client;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.registry.ModSounds;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToDoubleFunction;

/**
 * Looping sounds of the ship you are on: timbers creaking (louder with speed), a slack sail flapping, and water
 * rushing in through holes. Each fades in and out with what drives it.
 */
@EventBusSubscriber(modid = Seafarers.MODID, value = Dist.CLIENT)
public final class ShipAmbience {
    @Nullable
    private static HelmBlockEntity aboard;
    private static Loop creak;
    private static Loop luff;
    private static Loop leak;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused() || !dev.seafarers.SeafarersConfig.SHIP_AMBIENCE.get()) {
            aboard = null;
            return;
        }
        SubLevel ship = Sable.HELPER.getTrackingSubLevel(mc.player);
        aboard = ship == null ? null : Ships.helmOf(ship);
        if (aboard == null) {
            return;
        }
        creak = keep(creak, ModSounds.HULL_CREAK.get(), h -> 0.25 + 0.1 * Math.min(1, Math.abs(h.speed()) / 6));
        luff = keep(luff, ModSounds.SAIL_LUFF.get(), h -> {
            HelmBlockEntity.Sail s = h.sail(0);
            return s == null || s.deploy() < 0.1f ? 0 : 0.5 * s.deploy() * Mth.clamp(0.6 - s.fill(), 0, 0.6) / 0.6;
        });
        leak = keep(leak, ModSounds.WATER_RUSH.get(), h -> h.holeCount() > 0 && !h.sinking() ? 0.6 : 0);
    }

    private static Loop keep(@Nullable Loop loop, SoundEvent sound, ToDoubleFunction<HelmBlockEntity> volume) {
        if (loop == null || loop.isStopped()) {
            loop = new Loop(sound, volume);
            Minecraft.getInstance().getSoundManager().play(loop);
        }
        return loop;
    }

    /** A loop that follows the ship and fades toward the volume its source asks for; stops when you go ashore. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final ToDoubleFunction<HelmBlockEntity> target;

        Loop(SoundEvent sound, ToDoubleFunction<HelmBlockEntity> target) {
            super(sound, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.target = target;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = false;
        }

        @Override
        public void tick() {
            if (aboard == null || aboard.isRemoved()) {
                this.volume -= 0.05f;
                if (this.volume <= 0) {
                    this.stop();
                }
                return;
            }
            float want = (float) this.target.applyAsDouble(aboard);
            this.volume = Math.max(0.001f, this.volume + Mth.clamp(want - this.volume, -0.04f, 0.04f));
            Vec3 at = Minecraft.getInstance().player.position();
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private ShipAmbience() {}
}
