package dev.seafarers.debug;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs a sub-level's pose at a fixed interval so tests can be read from the server log
 * (lines start with "PROBE"). This is the oracle for every sailing test.
 */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class Probe {
    private static final List<Probe> ACTIVE = new ArrayList<>();

    private final String name;
    private final SubLevel subLevel;
    private final int interval;
    private int ticksLeft;
    private int age;

    private Probe(String name, SubLevel subLevel, int interval, int duration) {
        this.name = name;
        this.subLevel = subLevel;
        this.interval = interval;
        this.ticksLeft = duration;
    }

    public static void start(String name, SubLevel subLevel, int interval, int duration) {
        ACTIVE.add(new Probe(name, subLevel, interval, duration));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ACTIVE.removeIf(Probe::tick);
    }

    private boolean tick() {
        if (this.subLevel.isRemoved()) {
            Seafarers.LOGGER.info("PROBE {} t={} removed", this.name, this.age);
            return true;
        }
        if (this.age % this.interval == 0) {
            log(this.name, this.age, this.subLevel);
        }
        this.age++;
        return --this.ticksLeft < 0;
    }

    public static void log(String name, int age, SubLevel subLevel) {
        Pose3d pose = subLevel.logicalPose();
        Vector3d euler = pose.orientation().getEulerAnglesYXZ(new Vector3d());
        Seafarers.LOGGER.info("PROBE {} t={} pos=({}, {}, {}) yaw={} pitch={} roll={}",
                name, age,
                fmt(pose.position().x()), fmt(pose.position().y()), fmt(pose.position().z()),
                fmt(Math.toDegrees(euler.y)), fmt(Math.toDegrees(euler.x)), fmt(Math.toDegrees(euler.z)));
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }
}
