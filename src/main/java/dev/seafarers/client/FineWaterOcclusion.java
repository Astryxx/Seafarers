package dev.seafarers.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ryanhcode.sable.SableClient;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegion;
import dev.ryanhcode.sable.render.region.SimpleCulledRenderRegionBuilder;
import dev.ryanhcode.sable.render.water_occlusion.WaterOcclusionRenderer;
import dev.ryanhcode.sable.sublevel.water_occlusion.WaterOcclusionRegion;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.HelmBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Sable hides the sea inside a ship by drawing the dry region's surface into depth buffers and dropping water
 * fragments that fall inside it. Its own mesh is made of whole blocks, so where a hull curves through a block
 * the sea is either cut back from the hull or shows inside it. This swaps the mesh Sable draws for a ship's
 * hold for the hold's exact shape (boxes on the hull's quarter-block voxel grid, from the ship layout), so the
 * sea meets the planking exactly.
 *
 * Only what is drawn changes: Sable's own block region stays in place for everything else (swimming, physics),
 * and it manages the swapped mesh's lifetime as its own. Uses Sable's public renderer and region classes; the
 * two private fields it needs are looked up once, and if a future Sable has changed them it logs once and leaves
 * Sable's own mesh alone.
 */
public final class FineWaterOcclusion {
    private static final Map<WaterOcclusionRegion, Boolean> SWAPPED = new WeakHashMap<>();
    private static Field regionsField;
    private static Field renderRegionField;
    private static boolean unavailable;

    public static void apply(HelmBlockEntity helm) {
        WaterOcclusionRegion hold = helm.dryHoldRegion();
        float[] mesh = helm.dryMesh();
        if (Boolean.getBoolean("seafarers.blockyWaterMask") || unavailable || hold == null || mesh.length < 6 || SWAPPED.containsKey(hold) || !hold.getClass().getName().endsWith("ClientWaterOcclusionRegion")) {
            return;
        }
        SWAPPED.put(hold, true);
        try {
            if (regionsField == null) {
                regionsField = WaterOcclusionRenderer.class.getDeclaredField("regions");
                regionsField.setAccessible(true);
                renderRegionField = hold.getClass().getDeclaredField("renderRegion"); // ClientWaterOcclusionContainer's region
                renderRegionField.setAccessible(true);
            }
            WaterOcclusionRenderer renderer = SableClient.WATER_OCCLUSION_RENDERER;
            @SuppressWarnings("unchecked")
            Set<SimpleCulledRenderRegion> regions = (Set<SimpleCulledRenderRegion>) regionsField.get(renderer);
            SimpleCulledRenderRegion blocky = (SimpleCulledRenderRegion) renderRegionField.get(hold);
            Exact exact = new Exact(helm.dryMeshBase(), mesh);
            regions.add(exact);
            renderRegionField.set(hold, exact); // Sable frees it with the region, as it would its own
            if (blocky != null) {
                renderer.removeRegion(blocky);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            Seafarers.LOGGER.warn("Can't give ship holds an exact water mask with this version of Sable ({}); using Sable's own", e.toString());
        }
    }

    /** A render region whose mesh is the given boxes, each face drawn both ways round. */
    private static final class Exact extends SimpleCulledRenderRegion {
        private final float[] mesh;
        private final BlockPos base;

        /** mesh: boxes relative to base (a plot block). */
        Exact(BlockPos base, float[] mesh) {
            super(corners(base, mesh));
            this.base = base;
            this.mesh = mesh;
        }

        private static List<BlockPos> corners(BlockPos base, float[] m) {
            float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
            for (int i = 0; i + 5 < m.length; i += 6) {
                x0 = Math.min(x0, m[i]); y0 = Math.min(y0, m[i + 1]); z0 = Math.min(z0, m[i + 2]);
                x1 = Math.max(x1, m[i + 3]); y1 = Math.max(y1, m[i + 4]); z1 = Math.max(z1, m[i + 5]);
            }
            return List.of(base.offset(BlockPos.containing(x0, y0, z0)), base.offset(BlockPos.containing(x1, y1, z1)));
        }

        @Override
        public SimpleCulledRenderRegionBuilder createMeshBuilder(int gridSize) {
            // the origin relative to base: small whole numbers, so the mesh keeps full precision
            Vec3 o = this.getOrigin().subtract(Vec3.atLowerCornerOf(this.base));
            float[] m = this.mesh;
            return new SimpleCulledRenderRegionBuilder(gridSize) {
                @Override
                public void render(Matrix4f pose, VertexConsumer vc) {
                    surface(m, o, pose, vc);
                }
            };
        }

        @Override
        public VertexFormat getVertexFormat() {
            return DefaultVertexFormat.POSITION;
        }

        /** Quarter blocks: the planking's voxel size, so the mask meets the hull exactly. */
        private static final float STEP = 0.25f;

        /**
         * The outer surface of the boxes' union, as Sable's own region builder makes it but on the quarter-block
         * grid: a face wherever a filled cell meets an empty one, wound like Sable's faces (counter-clockwise seen
         * from outside), and none between filled cells. Sable's depth passes rely on both, so the mask also holds
         * with the camera inside the hold.
         */
        private static void surface(float[] m, Vec3 o, Matrix4f pose, VertexConsumer vc) {
            int nx = 0, ny = 0, nz = 0;
            for (int i = 0; i + 5 < m.length; i += 6) {
                nx = Math.max(nx, cell(m[i + 3] - o.x));
                ny = Math.max(ny, cell(m[i + 4] - o.y));
                nz = Math.max(nz, cell(m[i + 5] - o.z));
            }
            java.util.BitSet filled = new java.util.BitSet(nx * ny * nz);
            for (int i = 0; i + 5 < m.length; i += 6) {
                for (int x = cell(m[i] - o.x); x < cell(m[i + 3] - o.x); x++) {
                    for (int y = cell(m[i + 1] - o.y); y < cell(m[i + 4] - o.y); y++) {
                        for (int z = cell(m[i + 2] - o.z); z < cell(m[i + 5] - o.z); z++) {
                            filled.set((x * ny + y) * nz + z);
                        }
                    }
                }
            }
            int[][] dirs = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
            int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
            float[][] c = new float[8][3];
            for (int x = 0; x < nx; x++) {
                for (int y = 0; y < ny; y++) {
                    for (int z = 0; z < nz; z++) {
                        if (!filled.get((x * ny + y) * nz + z)) {
                            continue;
                        }
                        float x0 = x * STEP, y0 = y * STEP, z0 = z * STEP, x1 = x0 + STEP, y1 = y0 + STEP, z1 = z0 + STEP;
                        set(c[0], x0, y0, z0); set(c[1], x1, y0, z0); set(c[2], x1, y1, z0); set(c[3], x0, y1, z0);
                        set(c[4], x0, y0, z1); set(c[5], x1, y0, z1); set(c[6], x1, y1, z1); set(c[7], x0, y1, z1);
                        for (int f = 0; f < 6; f++) {
                            int ax = x + dirs[f][0], ay = y + dirs[f][1], az = z + dirs[f][2];
                            boolean open = ax < 0 || ay < 0 || az < 0 || ax >= nx || ay >= ny || az >= nz
                                    || !filled.get((ax * ny + ay) * nz + az);
                            if (open) {
                                for (int k = 0; k < 4; k++) {
                                    float[] v = c[faces[f][k]];
                                    vc.addVertex(pose, v[0], v[1], v[2]);
                                }
                            }
                        }
                    }
                }
            }
        }

        private static int cell(double v) {
            return (int) Math.round(v / STEP);
        }

        private static void set(float[] v, float x, float y, float z) {
            v[0] = x;
            v[1] = y;
            v[2] = z;
        }
    }

    private FineWaterOcclusion() {}
}
