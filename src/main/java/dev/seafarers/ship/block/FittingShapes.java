package dev.seafarers.ship.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.seafarers.Seafarers;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Collision and outline shapes for ship fittings, made from their models: assets/seafarers/fitting_shapes.json,
 * written by tools/gen_resources.py from the same boxes the block models (and their renderers' parts, at rest)
 * are drawn with. So a fitting is solid exactly where it looks solid: you can stand beside the helm's pedestal,
 * between a table's legs or next to a cleat's post. Read from the mod jar, so client and server agree.
 */
public final class FittingShapes {
    private static final Map<String, VoxelShape[]> SHAPES = load();

    /** The shape of a block state: its facing, and for a fitting bigger than a block, which part. */
    public static VoxelShape of(Block block, net.minecraft.world.level.block.state.BlockState state, VoxelShape fallback) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (state.hasProperty(MultiPart.PART)) {
            name += "#" + state.getValue(MultiPart.PART);
        }
        VoxelShape[] turned = SHAPES.get(name);
        return turned == null ? fallback : turned[state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).get2DDataValue()];
    }

    /** The block's shape turned to face {@code facing}, or the fallback if it has none in the file. */
    public static VoxelShape of(Block block, Direction facing, VoxelShape fallback) {
        VoxelShape[] turned = SHAPES.get(BuiltInRegistries.BLOCK.getKey(block).getPath());
        return turned == null ? fallback : turned[facing.get2DDataValue()];
    }

    private static Map<String, VoxelShape[]> load() {
        Map<String, VoxelShape[]> out = new HashMap<>();
        try (InputStream in = FittingShapes.class.getResourceAsStream("/assets/seafarers/fitting_shapes.json")) {
            if (in == null) {
                Seafarers.LOGGER.error("fitting_shapes.json missing; run tools/gen_resources.py");
                return out;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                VoxelShape[] turned = new VoxelShape[4];
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    VoxelShape shape = Shapes.empty();
                    for (JsonElement b : e.getValue().getAsJsonArray()) {
                        shape = Shapes.or(shape, turn((JsonArray) b, facing));
                    }
                    turned[facing.get2DDataValue()] = shape.optimize();
                }
                out.put(e.getKey(), turned);
            }
        } catch (Exception ex) {
            Seafarers.LOGGER.error("Couldn't read fitting_shapes.json", ex);
        }
        return out;
    }

    /** A model box (pixels, facing north) turned as the blockstate turns the model: clockwise seen from above. */
    private static VoxelShape turn(JsonArray b, Direction facing) {
        double x0 = b.get(0).getAsDouble(), y0 = b.get(1).getAsDouble(), z0 = b.get(2).getAsDouble();
        double x1 = b.get(3).getAsDouble(), y1 = b.get(4).getAsDouble(), z1 = b.get(5).getAsDouble();
        int quarterTurns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        for (int i = 0; i < quarterTurns; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
        }
        return Block.box(x0, y0, z0, x1, y1, z1);
    }

    private FittingShapes() {}
}
