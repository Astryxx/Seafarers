package dev.seafarers.ship;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.seafarers.Seafarers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A ship design, read from data/seafarers/ships/&lt;name&gt;.json (written by tools/ships/*.py). respawns are the spots
 * crew come back aboard at (one picked at random each time; respawn is the first); brig, if she has one, is the
 * brig's two inside corners and its door.
 * Cells are in ship coordinates: x across the beam (+ = starboard), y up from the keel, z from stern to bow.
 * Block states may contain facing tokens ({bow}, {stern}, {port}, {starboard}) and axis tokens ({along},
 * {across}), resolved for the direction the ship is launched in.
 */
public record ShipLayout(String name, ShipStats stats, int masts, BlockPos respawn, int holdMin, int holdMax,
                         List<BlockPos> bilge, List<BlockPos> dry, List<Cell> cells, List<float[]> dryMesh,
                         List<BlockPos> respawns, List<BlockPos> brig) {

    /** A block of the ship. loot names a chest's stock; data configures a {@link ShipPart} (sail size etc.). */
    public record Cell(int x, int y, int z, String state, @Nullable String loot, @Nullable JsonObject data) {}

    @Nullable
    public static ShipLayout load(MinecraftServer server, String name) {
        Optional<net.minecraft.server.packs.resources.Resource> res = server.getResourceManager()
                .getResource(Seafarers.id("ships/" + name + ".json"));
        if (res.isEmpty()) {
            Seafarers.LOGGER.error("No ship layout {}", name);
            return null;
        }
        try (Reader reader = res.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray r = json.getAsJsonArray("respawn");
            List<Cell> cells = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("cells")) {
                JsonArray c = e.getAsJsonArray();
                JsonObject extra = c.size() > 4 ? c.get(4).getAsJsonObject() : new JsonObject();
                String loot = extra.has("loot") ? extra.get("loot").getAsString() : null;
                JsonObject data = extra.has("data") ? extra.getAsJsonObject("data") : null;
                cells.add(new Cell(c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt(), c.get(3).getAsString(), loot, data));
            }
            JsonArray hold = json.getAsJsonArray("hold");
            List<BlockPos> bilge = positions(json, "bilge");
            List<BlockPos> dry = positions(json, "dry");
            ShipStats stats = ShipStats.fromJson(json.has("stats") ? json.getAsJsonObject("stats") : null);
            if (json.has("crew")) {
                stats.crew = json.get("crew").getAsInt();
            }
            int masts = json.has("masts") ? json.get("masts").getAsInt() : 1;
            // the brig, if she has one: its two inside corners and then its door (the door's lower half)
            List<BlockPos> brig = positions(json, "brig");
            return new ShipLayout(json.get("name").getAsString(), stats, masts,
                    new BlockPos(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt()),
                    hold.get(0).getAsInt(), hold.get(1).getAsInt(), bilge, dry, cells, boxes(json, "dryMesh"),
                    positions(json, "respawns"), brig.size() == 3 ? brig : List.of());
        } catch (Exception e) {
            Seafarers.LOGGER.error("Bad ship layout {}", name, e);
            return null;
        }
    }

    /** Boxes [x0, y0, z0, x1, y1, z1] in cell-index space (cell x covers [x, x+1)). */
    private static List<float[]> boxes(JsonObject json, String key) {
        List<float[]> out = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                JsonArray b = e.getAsJsonArray();
                float[] box = new float[6];
                for (int i = 0; i < 6; i++) {
                    box[i] = b.get(i).getAsFloat();
                }
                out.add(box);
            }
        }
        return out;
    }

    private static List<BlockPos> positions(JsonObject json, String key) {
        List<BlockPos> out = new ArrayList<>();
        if (json.has(key)) {
            for (JsonElement e : json.getAsJsonArray(key)) {
                JsonArray b = e.getAsJsonArray();
                out.add(new BlockPos(b.get(0).getAsInt(), b.get(1).getAsInt(), b.get(2).getAsInt()));
            }
        }
        return out;
    }

    /** Fills in facing and axis tokens for a ship whose bow points {@code bow}. */
    public static String resolve(String state, Direction bow) {
        Direction starboard = bow.getClockWise();
        return state.replace("{bow}", bow.getName())
                .replace("{stern}", bow.getOpposite().getName())
                .replace("{starboard}", starboard.getName())
                .replace("{port}", starboard.getOpposite().getName())
                .replace("{along}", bow.getAxis().getName())
                .replace("{across}", starboard.getAxis().getName());
    }

    /** Ship coordinates to a world offset for a ship whose bow points {@code bow}. */
    public static BlockPos offset(int x, int y, int z, Direction bow) {
        Direction starboard = bow.getClockWise();
        return new BlockPos(starboard.getStepX() * x + bow.getStepX() * z, y, starboard.getStepZ() * x + bow.getStepZ() * z);
    }

    public int length() {
        int max = 0;
        for (Cell c : this.cells) {
            max = Math.max(max, c.z());
        }
        return max + 1;
    }
}
