package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.block.CannonBlockEntity;
import dev.seafarers.ship.block.SailYardBlock;
import dev.seafarers.ship.block.SailYardBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the yard and the square sail from the ship's state: it swings with the sail angle, lets down and
 * furls smoothly, bellies out when it draws well and flaps when it doesn't.
 * <p>
 * Drawing frame: origin at the mast's centre at yard height, bow toward -z (the blockstate north), +x to
 * starboard; the frame is rotated to the yard block's facing.
 */
public class SailRenderer implements BlockEntityRenderer<SailYardBlockEntity> {
    /** Vertex colour for what's being drawn: the sail colour for canvas, white for everything else (render thread only). */
    private static int tint = 0xFFFFFF;

    private static final int COLUMNS = 8;
    private static final int ROWS = 7;

    public SailRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(SailYardBlockEntity yard, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        HelmBlockEntity helm = Ships.helmOf(yard.getLevel(), yard.getBlockPos());
        HelmBlockEntity.Sail sail = helm == null ? null : helm.sail(yard.mast());
        // where the ropes have set it: drawn moving there at the server's rate, so it rolls and swings continuously
        float targetDeploy = sail == null ? 1f : sail.health() <= 0 ? 0f : sail.deployGoal();
        float targetAngle = sail == null ? 0f : sail.angleGoal();
        float targetFill = sail == null ? 0.6f : sail.fill();
        float health = sail == null || helm == null ? 1f : sail.health() / (float) Math.max(1, helm.mastHealthMax());
        boolean broken = health <= 0;
        // a broken mast lets the yard drop and hang askew; mending hauls it back up
        yard.shownBreak = approach(yard.shownBreak, broken ? 1f : 0f, 0.02f * Minecraft.getInstance().getTimer().getRealtimeDeltaTicks());
        // ease toward the real state so the sail drops and swings instead of snapping
        float frame = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        if (yard.shownDeploy < 0) {
            yard.shownDeploy = targetDeploy;
            yard.shownAngle = targetAngle;
            yard.shownFill = targetFill;
        }
        yard.shownDeploy = approach(yard.shownDeploy, targetDeploy, HelmBlockEntity.HOIST_RATE * 1.15f * frame);
        yard.shownAngle = approach(yard.shownAngle, targetAngle, HelmBlockEntity.BRACE_RATE * 1.15f * frame);
        yard.shownFill += (targetFill - yard.shownFill) * Math.min(1, 0.05f * frame);
        float deploy = yard.shownDeploy;
        float time = (yard.getLevel().getGameTime() + partialTick);

        TextureAtlasSprite cloth = sprite("block/sail_cloth");
        TextureAtlasSprite wood = sprite("block/mast");
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));

        this.rigging(yard, vc, pose, light);
        HelmBlockEntity sailsOf = Ships.helmOf(yard.getLevel(), yard.getBlockPos());
        int sailColor = sailsOf == null ? 0xFFFFFF : sailsOf.sailColor();

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(yard.getBlockState().getValue(SailYardBlock.FACING))));
        pose.translate(0, 0, 1); // the yard block sits one forward of the mast
        if (yard.shownBreak > 0) {
            this.splinters(vc, pose, wood, yard, light);
            pose.translate(0, -3.2f * yard.shownBreak, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(22f * yard.shownBreak));
        }
        pose.mulPose(Axis.YP.rotationDegrees(-yard.shownAngle));

        // the yard: a long spar across the mast, just forward of it
        float yz = -yard.forward() + 0.15f;
        box(vc, pose, wood, -yard.yardHalfLength(), -0.18f, yz - 0.18f, yard.yardHalfLength(), 0.18f, yz + 0.18f, light);

        float w = yard.halfWidth();
        float drop = yard.height() * deploy;
        if (drop > 0.15f) {
            tint = sailColor;
            this.cloth(vc, pose, cloth, w, drop, deploy, yard.shownFill, time, yard.forward(), 1 - health, light);
        }
        // whatever isn't let down stays rolled up under the yard
        float roll = 0.12f + 0.28f * (1 - deploy);
        tint = sailColor;
        box(vc, pose, cloth, -w, -0.18f - roll * 1.2f, yz - roll, w, -0.12f, yz + roll * 0.6f, light);
        tint = 0xFFFFFF;

        pose.popPose();
    }

    /**
     * Running and standing rigging, in the yard block's frame: sheets from the yard ends to the angle pulleys,
     * shrouds from the masthead down to the rails, and the forestay out to the bowsprit.
     */
    private void rigging(SailYardBlockEntity yard, VertexConsumer vc, PoseStack pose, int light) {
        net.minecraft.core.BlockPos at = yard.getBlockPos();
        if (yard.rigScan-- <= 0) {
            yard.rigScan = 200;
            yard.pulleys.clear();
            yard.bowsprit = null;
            var ship = Ships.shipAt(yard.getLevel(), at);
            if (ship != null) {
                var b = ship.getPlot().getBoundingBox();
                net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        for (int z = b.minZ(); z <= b.maxZ(); z++) {
                            var state = yard.getLevel().getBlockState(p.set(x, y, z));
                            if (state.is(dev.seafarers.registry.ModBlocks.BRACE_ROPE.get())
                                    && state.getValue(dev.seafarers.ship.block.SailRopeBlock.MAST) == yard.mast()) {
                                yard.pulleys.add(p.immutable());
                            } else if (state.is(dev.seafarers.registry.ModBlocks.BOWSPRIT.get())) {
                                yard.bowsprit = p.immutable();
                            }
                        }
                    }
                }
            }
        }
        TextureAtlasSprite rope = sprite("block/rope");
        net.minecraft.core.Direction bow = yard.getBlockState().getValue(SailYardBlock.FACING);
        Vector3f bowV = new Vector3f(bow.getStepX(), 0, bow.getStepZ());
        Vector3f stbd = new Vector3f(bow.getClockWise().getStepX(), 0, bow.getClockWise().getStepZ());
        // yard ends, swung with the sail
        Matrix4f yardFrame = new Matrix4f().translate(0.5f, 0.5f, 0.5f)
                .rotateY((float) Math.toRadians(-CannonBlockEntity.facingYRot(bow)))
                .translate(0, 0, 1)
                .rotateY((float) Math.toRadians(-yard.shownAngle));
        float yz = -yard.forward() + 0.15f;
        float tip = yard.yardHalfLength() - 0.2f;
        for (int side = -1; side <= 1; side += 2) {
            Vector3f end = yardFrame.transformPosition(new Vector3f(side * tip, 0, yz));
            Vector3f best = null;
            for (var p : yard.pulleys) {
                Vector3f c = new Vector3f(p.getX() - at.getX() + 0.5f, p.getY() - at.getY() + 0.7f, p.getZ() - at.getZ() + 0.5f);
                if (Math.signum(c.dot(stbd)) == side && (best == null || c.y > best.y)) {
                    best = c;
                }
            }
            if (best != null) {
                line(vc, pose, rope, end, best, 0.035f, light);
            }
        }
        // the masthead sits above the mast, one block aft of the yard block
        Vector3f masthead = new Vector3f(0.5f, yard.masthead(), 0.5f).sub(bowV);
        Vector3f mastFoot = new Vector3f(0.5f, 0.5f - yard.shroudDrop(), 0.5f).sub(bowV);
        Vector3f nest = new Vector3f(0.5f, yard.shroudTop(), 0.5f).sub(bowV);
        for (int side = -1; side <= 1; side += 2) {
            // two shrouds a side, from the rim of the crow's nest down to the rail cap aft of the mast (clear of the sail)
            Vector3f[] tops = new Vector3f[2], feet = new Vector3f[2];
            float[] alongs = {-0.7f, -1.9f};
            for (int k = 0; k < 2; k++) {
                tops[k] = new Vector3f(nest).add(new Vector3f(stbd).mul(side * yard.shroudTopSpread()))
                        .add(new Vector3f(bowV).mul(alongs[k] * 0.4f));
                feet[k] = new Vector3f(mastFoot).add(new Vector3f(stbd).mul(side * yard.shroudSpread()))
                        .add(new Vector3f(bowV).mul(alongs[k]));
                line(vc, pose, rope, tops[k], feet[k], 0.03f, light);
            }
            // ratlines: rungs lashed across each pair of shrouds, a step apart, up to the crow's nest
            float height = tops[0].y - feet[0].y;
            int rungs = (int) (height / 0.45f);
            for (int r = 1; r < rungs; r++) {
                float t = r / (float) rungs;
                Vector3f a = new Vector3f(feet[0]).lerp(tops[0], t), b = new Vector3f(feet[1]).lerp(tops[1], t);
                line(vc, pose, rope, a, b, 0.018f, light);
            }
        }
        if (yard.bowsprit != null && yard.masthead() > 0
                && yard.getLevel().getBlockEntity(yard.bowsprit) instanceof dev.seafarers.ship.block.BowspritBlock.Heel heelBe) {
            float r = (float) Math.toRadians(heelBe.raise());
            float len = heelBe.length() + 1.2f;
            Vector3f heel = new Vector3f(yard.bowsprit.getX() - at.getX() + 0.5f, yard.bowsprit.getY() - at.getY() + 0.25f,
                    yard.bowsprit.getZ() - at.getZ() + 0.5f);
            Vector3f tipOut = new Vector3f(heel).add(new Vector3f(bowV).mul((float) Math.cos(r) * len))
                    .add(0, (float) Math.sin(r) * len, 0);
            line(vc, pose, rope, masthead, tipOut, 0.035f, light);
        }
    }

    /** A thin square rope from a to b (both in the current pose's frame). */
    static void line(VertexConsumer vc, PoseStack pose, TextureAtlasSprite tex, Vector3f a, Vector3f b, float half, int light) {
        Vector3f dir = new Vector3f(b).sub(a);
        float len = dir.length();
        if (len < 1e-3f) {
            return;
        }
        pose.pushPose();
        pose.translate(a.x, a.y, a.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 0, -1), dir.div(len)));
        box(vc, pose, tex, -half, -half, -len, half, half, 0, light);
        pose.popPose();
    }

    /** The canvas: a grid that bellies forward with the wind and ripples when it luffs. */
    private void cloth(VertexConsumer vc, PoseStack pose, TextureAtlasSprite tex, float halfWidth, float drop, float deploy,
                       float fill, float time, float forward, float damage, int light) {
        float baseZ = -forward;
        float belly = 0.25f + 1.1f * fill;
        float luff = Mth.clamp(0.55f - fill, 0, 0.55f) * deploy;
        Vector3f[][] grid = new Vector3f[COLUMNS + 1][ROWS + 1];
        for (int i = 0; i <= COLUMNS; i++) {
            float u = i / (float) COLUMNS;
            for (int j = 0; j <= ROWS; j++) {
                float v = j / (float) ROWS;
                float x = -halfWidth + 2 * halfWidth * u;
                float y = -0.15f - drop * v;
                // pinned along the yard, free at the foot: the belly peaks low in the middle
                float shape = Mth.sin((float) Math.PI * u) * Mth.sin((float) Math.PI * Math.min(1, v * 0.9f + 0.05f) * 0.85f);
                float z = baseZ - belly * deploy * shape;
                z += luff * 0.35f * Mth.sin(time * 0.9f + u * 7f + v * 4f) * v;
                grid[i][j] = new Vector3f(x, y, z);
            }
        }
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < COLUMNS; i++) {
            for (int j = 0; j < ROWS; j++) {
                if (damage > 0 && torn(i, j, damage)) {
                    continue;
                }
                Vector3f a = grid[i][j], b = grid[i + 1][j], c = grid[i + 1][j + 1], d = grid[i][j + 1];
                // lit mostly from above whichever side you see it from: sailcloth glows in daylight rather than going
                // dark on its lee side (the quads are drawn both sides with one normal)
                Vector3f n = new Vector3f(c).sub(a).cross(new Vector3f(b).sub(d)).normalize().mul(0.35f).add(0, 1, 0).normalize();
                // one texture square per block of canvas
                float u0 = tex.getU0(), u1 = tex.getU1();
                float v0 = tex.getV0(), v1 = tex.getV(Math.min(1f, drop / ROWS));
                quad(vc, pose, m, a, b, c, d, u0, u1, v0, v1, n, light);
            }
        }
        // bolt ropes sewn round the edges: down both leeches and along the foot
        tint = 0xFFFFFF;
        TextureAtlasSprite rope = sprite("block/rope");
        for (int j = 0; j < ROWS; j++) {
            line(vc, pose, rope, grid[0][j], grid[0][j + 1], 0.035f, light);
            line(vc, pose, rope, grid[COLUMNS][j], grid[COLUMNS][j + 1], 0.035f, light);
        }
        for (int i = 0; i < COLUMNS; i++) {
            if (damage <= 0 || !torn(i, ROWS - 1, damage)) {
                line(vc, pose, rope, grid[i][ROWS], grid[i + 1][ROWS], 0.035f, light);
            }
        }
    }

    /** Which canvas panels a damaged sail has lost: a fixed scatter, more panels as damage grows (never the yard row). */
    private static boolean torn(int i, int j, float damage) {
        if (j == 0) {
            return false;
        }
        int h = (i * 73 + j * 151 + 17) % 97;
        return h < damage * 30;
    }

    /** A splintered break halfway down the mast, shown while it is broken. */
    private void splinters(VertexConsumer vc, PoseStack pose, TextureAtlasSprite wood, SailYardBlockEntity yard, int light) {
        float y = -yard.shroudDrop() * 0.45f;
        float k = yard.shownBreak;
        pose.pushPose();
        pose.translate(0, y, -1);
        for (int n = 0; n < 6; n++) {
            float ang = n * 60f;
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.mulPose(Axis.XP.rotationDegrees(25f * k));
            box(vc, pose, wood, -0.06f, 0, 0.25f, 0.06f, 0.5f * k + 0.05f, 0.33f, light);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, PoseStack pose, Matrix4f m, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                             float u0, float u1, float v0, float v1, Vector3f n, int light) {
        vertex(vc, pose, a, u0, v0, n, light);
        vertex(vc, pose, b, u1, v0, n, light);
        vertex(vc, pose, c, u1, v1, n, light);
        vertex(vc, pose, d, u0, v1, n, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack pose, Vector3f p, float u, float v, Vector3f n, int light) {
        vc.addVertex(pose.last(), p.x, p.y, p.z).setColor(0xFF000000 | tint).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose.last(), n.x, n.y, n.z);
    }

    /** An axis-aligned box with the sprite on every face. */
    static void box(VertexConsumer vc, PoseStack pose, TextureAtlasSprite tex, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        Matrix4f m = pose.last().pose();
        float u0 = tex.getU0(), u1 = tex.getU1(), v0 = tex.getV0(), v1 = tex.getV1();
        Vector3f[] c = {
                new Vector3f(x0, y0, z0), new Vector3f(x1, y0, z0), new Vector3f(x1, y1, z0), new Vector3f(x0, y1, z0),
                new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1), new Vector3f(x1, y1, z1), new Vector3f(x0, y1, z1)};
        int[][] faces = {{0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}};
        float[][] normals = {{0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int f = 0; f < 6; f++) {
            Vector3f n = new Vector3f(normals[f][0], normals[f][1], normals[f][2]);
            quad(vc, pose, m, c[faces[f][0]], c[faces[f][1]], c[faces[f][2]], c[faces[f][3]], u0, u1, v0, v1, n, light);
        }
    }

    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Seafarers.id(path));
    }

    private static float approach(float from, float to, float step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    @Override
    public boolean shouldRenderOffScreen(SailYardBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(SailYardBlockEntity be) {
        float r = Math.max(be.yardHalfLength(), be.height()) + 4;
        return new AABB(be.getBlockPos()).inflate(r + 2, Math.max(r, be.shroudDrop() + 2), r + 4);
    }
}
