package dev.seafarers.ship;

import com.google.gson.JsonObject;
import dev.seafarers.Seafarers;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * How one kind of ship handles: speed, turning, flooding, crew. Read from the "stats" object of
 * data/seafarers/ships/&lt;name&gt;.json (any field left out keeps the default below), saved with the ship, and
 * changeable live with {@code /seafarers tune}. SI units: metres (= blocks) and seconds.
 */
public class ShipStats {
    // ---- sailing ----
    /** Top speed: all sails set, wind on the quarter, perfect trim. */
    /** 1 for a rowboat: the helm is a pair of oars, rowed with W/S, and there are no sails. */
    public double oars = 0;
    public double maxSpeed = 8.5;
    public double acceleration = 0.45;
    public double coast = 0.25;
    /** Sable's own water drag on this hull, measured (per second) and cancelled so the tuning above is what the crew feels. */
    public double waterDrag = 1.05;
    public double waterYawDrag = 1.0;
    /** How strongly the keel cancels sideways drift, per second. */
    public double keelGrip = 1.2;
    public double maxYawRate = 0.45;
    /** How fast the turn rate builds up or dies away, rad/s per second: low is a heavy ship, slow to answer the helm. */
    public double yawAccel = 0.1;
    public double yawResponse = 1.8;
    /** Turn rate when the anchor bites with the wheel hard over at speed (the anchor turn). */
    public double pivotRate = 1.4;
    /** How much of a sail's pull is left with it at the worst angle to the wind. */
    public double untrimmed = 0.35;
    /** Wheel travel per tick of held input (lock to lock in 2 s). */
    public double wheelSpeed = 0.025;
    /** How many times the wheel spins round from amidships to hard over (drawn; the Galleon's wheel turns most). */
    public double wheelTurns = 0.5;
    /**
     * How much of her top speed the sails give on each point of sail, with perfect trim: wind dead astern (run),
     * on the beam, on the quarter forward of the beam (close: 135 degrees off the stern) and dead ahead (in irons).
     * The Brigantine is quickest with the wind on her beam, the Galleon running before it.
     */
    public double sailRun = 1.0;
    public double sailBeam = 0.9;
    public double sailClose = 0.45;
    public double sailIrons = 0.12;
    // ---- staying upright: ships never capsize ----
    public double righting = 25.0;
    public double rightingDamp = 8.0;
    /** How fast the correction for a steady list (uneven stores) builds up, per second. */
    public double rightingTrim = 0.5;
    /** Extra lift, m/s^2, to set how deep she floats without re-tuning the hull (negative = deeper). */
    public double liftTrim = 0.0;
    // ---- damage ----
    /** Water gained per tick per hole below the waterline (full is 100). */
    public double floodPerHole = 0.05;
    /** Water removed by one bucket. */
    public double bucket = 4.0;
    /** Extra downward pull at full flood, m/s^2: she sits lower as she fills. */
    public double floodSink = 5.0;
    /** How fast a ship past saving goes down, m/s. */
    public double sinkSpeed = 1.2;
    /**
     * Above this much water (fraction of full) the sea has reached her second deck and pours in faster, by
     * floodRush: the Galleon's deep lower deck holds a lot before that happens. 1 = never.
     */
    public double floodRushAbove = 1.0;
    public double floodRush = 1.0;
    /** Hits a mast takes before it breaks (chain shot does 3, a cannonball 1), at the default mastStrength of 6. */
    public int mastHealth = 6;
    // ---- anchor ----
    /** Bars on the capstan: how many crew can heave at once. */
    public int capstanPegs = 2;
    /** How heavy her anchor is: heaving takes this many times as long as the Sloop's. */
    public double anchorWeight = 1.0;
    // ---- crew ----
    public int crew = 2;

    public static ShipStats fromJson(JsonObject json) {
        ShipStats stats = new ShipStats();
        if (json == null) {
            return stats;
        }
        for (String key : json.keySet()) {
            if (!stats.set(key, json.get(key).getAsDouble())) {
                Seafarers.LOGGER.warn("Unknown ship stat {}", key);
            }
        }
        return stats;
    }

    /** Sets a field by name; false if there is no such stat. */
    public boolean set(String name, double value) {
        try {
            Field f = ShipStats.class.getField(name);
            if (Modifier.isStatic(f.getModifiers())) {
                return false;
            }
            if (f.getType() == int.class) {
                f.setInt(this, (int) Math.round(value));
            } else {
                f.setDouble(this, value);
            }
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Field f : ShipStats.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                tag.putDouble(f.getName(), ((Number) f.get(this)).doubleValue());
            } catch (IllegalAccessException ignored) {
            }
        }
        return tag;
    }

    public static ShipStats load(CompoundTag tag) {
        ShipStats stats = new ShipStats();
        for (String key : tag.getAllKeys()) {
            stats.set(key, tag.getDouble(key));
        }
        return stats;
    }
}
