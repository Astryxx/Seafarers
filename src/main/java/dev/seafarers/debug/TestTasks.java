package dev.seafarers.debug;

import dev.seafarers.Seafarers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/** Runs test actions a number of server ticks later. */
@EventBusSubscriber(modid = Seafarers.MODID)
public final class TestTasks {
    private static final List<Object[]> PENDING = new ArrayList<>();

    public static void after(int ticks, Runnable action) {
        PENDING.add(new Object[]{ticks, action});
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        List<Runnable> due = new ArrayList<>();
        PENDING.removeIf(task -> {
            int left = (int) task[0] - 1;
            task[0] = left;
            if (left <= 0) {
                due.add((Runnable) task[1]);
                return true;
            }
            return false;
        });
        due.forEach(Runnable::run);
    }

    private TestTasks() {}
}
