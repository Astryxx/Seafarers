package dev.seafarers.ship;

import dev.seafarers.Seafarers;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * One wind direction per world that drifts toward a new target every few minutes, like the open sea.
 * Angle is the compass heading the wind blows TOWARD, in degrees (0 = south/+Z, 90 = west/-X, Minecraft yaw convention).
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Wind extends SavedData {
    private static final String NAME = Seafarers.MODID + "_wind";
    /** Ticks between picking a new target direction. */
    /** Degrees per tick the wind turns toward its target. */
    private static final float TURN_RATE = 0.02f;

    private float angle;
    private float target;
    private int timer;
    private boolean locked;

    /** Ticks between changes of wind: the config's minutes, give or take a quarter. */
    private static int shiftInterval() {
        int base = dev.seafarers.SeafarersConfig.WIND_CHANGE_MINUTES.get() * 20 * 60;
        return base * 3 / 4 + java.util.concurrent.ThreadLocalRandom.current().nextInt(base / 2 + 1);
    }

    private Wind(float angle) {
        this.angle = angle;
        this.target = angle;
        this.timer = shiftInterval();
    }

    public static Wind get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new Wind(level.random.nextFloat() * 360f), Wind::load, null), NAME);
    }

    /** Horizontal unit vector the wind blows toward. */
    public Vec3 direction() {
        float rad = this.angle * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    public float angle() {
        return this.angle;
    }

    /** Fix the wind for testing; a null angle unlocks it. */
    public void set(Float angle) {
        this.locked = angle != null;
        if (angle != null) {
            this.angle = this.target = Mth.wrapDegrees(angle);
        }
        this.setDirty();
    }

    private void tick(ServerLevel level) {
        if (this.locked) {
            return;
        }
        if (--this.timer <= 0) {
            this.timer = shiftInterval();
            this.target = Mth.wrapDegrees(this.angle + (level.random.nextFloat() - 0.5f) * 180f);
        }
        float diff = Mth.wrapDegrees(this.target - this.angle);
        if (Math.abs(diff) > 0.01f) {
            this.angle = Mth.wrapDegrees(this.angle + Mth.clamp(diff, -TURN_RATE, TURN_RATE));
            this.setDirty();
        }
    }

    /** Compass name of where the wind is coming FROM (sailors name winds by their source). */
    public String fromName() {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        // wind blowing toward yaw `angle` comes from the opposite heading; yaw 180 = north
        float from = Mth.wrapDegrees(this.angle + 180f);
        float compass = Mth.positiveModulo(from + 180f, 360f); // 0 = N, 90 = E
        return names[Math.round(compass / 45f) % 8];
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level == level.getServer().overworld()) {
            get(level).tick(level);
        }
    }

    private static Wind load(CompoundTag tag, HolderLookup.Provider registries) {
        Wind wind = new Wind(tag.getFloat("angle"));
        wind.target = tag.getFloat("target");
        wind.timer = tag.getInt("timer");
        wind.locked = tag.getBoolean("locked");
        return wind;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putFloat("angle", this.angle);
        tag.putFloat("target", this.target);
        tag.putInt("timer", this.timer);
        tag.putBoolean("locked", this.locked);
        return tag;
    }
}
