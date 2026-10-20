package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.block.MapTableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Optional;

/**
 * What the sea chart (the map table's screen and the chart lying on the table) draws: the parchment, a light grid,
 * the crew's marks and the ship's arrow, where the ship really is (her interpolated pose) and heading her real way.
 */
public final class LiveChart {
    public static final ResourceLocation PAPER = Seafarers.id("textures/gui/chart_paper.png");
    public static final ResourceLocation INK = Seafarers.id("textures/gui/chart_ink.png");
    public static final MapDecoration ARROW = new MapDecoration(MapDecorationTypes.PLAYER, (byte) 0, (byte) 0, (byte) 0, Optional.empty());
    public static final MapDecoration CROSS = new MapDecoration(MapDecorationTypes.RED_X, (byte) 0, (byte) 0, (byte) 0, Optional.empty());

    /** Where the table's ship is: world x, z, and her heading (degrees, 0 = north, clockwise). */
    public static double[] ship(MapTableBlock.Table table) {
        Vec3 local = Vec3.atCenterOf(table.getBlockPos());
        Direction bow = MapTableBlock.facing(table.getBlockState());
        Vec3 world = local;
        Vec3 heading = Vec3.atLowerCornerOf(bow.getNormal());
        SubLevel ship = Sable.HELPER.getContaining(table.getLevel(), table.getBlockPos());
        if (ship != null) {
            Pose3dc p = ship instanceof ClientSubLevel c ? c.renderPose() : ship.logicalPose();
            world = p.transformPosition(local);
            heading = p.transformNormal(heading);
        }
        return new double[]{world.x, world.z, Math.toDegrees(Math.atan2(-heading.x, heading.z)) + 180};
    }

    /** A grid step for a chart showing span blocks across: a round number giving 4 to 10 lines. */
    public static int gridStep(float span) {
        int step = 16;
        while (span / step > 10) {
            step *= 2;
        }
        return step;
    }

    /** A map marker (vanilla's sprites) at (px, pz) in some 2D space, turned to an angle, size units across. */
    public static void drawMarker(MapDecoration kind, float px, float pz, float degrees, float size, PoseStack pose, MultiBufferSource buffers, int light) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getMapDecorationTextures().get(kind);
        pose.pushPose();
        pose.translate(px, pz, -0.03f);
        pose.mulPose(Axis.ZP.rotationDegrees(degrees));
        float h = size / 2;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffers.getBuffer(RenderType.text(sprite.atlasLocation()));
        vc.addVertex(m, -h, h, -0.002f).setColor(-1).setUv(sprite.getU0(), sprite.getV1()).setLight(light);
        vc.addVertex(m, h, h, -0.002f).setColor(-1).setUv(sprite.getU1(), sprite.getV1()).setLight(light);
        vc.addVertex(m, h, -h, -0.002f).setColor(-1).setUv(sprite.getU1(), sprite.getV0()).setLight(light);
        vc.addVertex(m, -h, -h, -0.002f).setColor(-1).setUv(sprite.getU0(), sprite.getV0()).setLight(light);
        pose.popPose();
    }

    /** The holder of a decoration type, for callers that only want the type. */
    public static MapDecoration of(Holder<MapDecorationType> type) {
        return new MapDecoration(type, (byte) 0, (byte) 0, (byte) 0, Optional.empty());
    }

    private LiveChart() {}
}
