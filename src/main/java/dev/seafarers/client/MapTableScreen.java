package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.network.MapTablePayloads;
import dev.seafarers.ship.block.MapTableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * The sea chart unrolled on screen, as on the Sloop's map table: blank parchment with a light grid, the ship's arrow
 * moving as she sails, the crew's marks. Drag to move the chart, scroll to zoom (round the pointer), click to put a
 * mark down, right click to rub one out, C to centre on the ship. The table shows it as you leave it.
 */
public class MapTableScreen extends Screen {
    private static final int FRAME = 0xFF5A3A1E, INK = 0xFF5A3A1E, GRID = 0x305A3A1E;

    private final BlockPos table;
    private int left, top, w, h;
    /** The view: the chart's centre (world x/z) and how many blocks it shows across. */
    private double cx, cz;
    private float span;
    private double pressX, pressY;
    private boolean dragging;

    public MapTableScreen(BlockPos table) {
        super(Component.translatable("block.seafarers.map_table"));
        this.table = table;
    }

    public static void open(MapTablePayloads.Open payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && MapMods.tryOpen()) {
            return; // the installed map mod's world map instead (see the client config)
        }
        if (!(mc.screen instanceof MapTableScreen)) {
            mc.setScreen(new MapTableScreen(payload.pos()));
        }
    }

    private MapTableBlock.Table tableEntity() {
        return this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(this.table) instanceof MapTableBlock.Table t ? t : null;
    }

    @Override
    protected void init() {
        this.left = 20;
        this.top = 16;
        this.w = this.width - 40;
        this.h = this.height - 46;
        MapTableBlock.Table t = this.tableEntity();
        if (this.span == 0) {
            if (t != null && t.viewSet()) {
                this.cx = t.viewX();
                this.cz = t.viewZ();
                this.span = t.viewSpan();
            } else {
                this.centreOnShip();
                this.span = MapTableBlock.DEFAULT_SPAN;
            }
        }
    }

    private void centreOnShip() {
        MapTableBlock.Table t = this.tableEntity();
        if (t != null) {
            double[] s = LiveChart.ship(t);
            this.cx = s[0];
            this.cz = s[1];
        }
    }

    /** Blocks per screen pixel. */
    private double scale() {
        return this.span / this.w;
    }

    private double worldX(double sx) {
        return this.cx + (sx - (this.left + this.w / 2.0)) * this.scale();
    }

    private double worldZ(double sy) {
        return this.cz + (sy - (this.top + this.h / 2.0)) * this.scale();
    }

    private float screenX(double wx) {
        return (float) (this.left + this.w / 2.0 + (wx - this.cx) / this.scale());
    }

    private float screenY(double wz) {
        return (float) (this.top + this.h / 2.0 + (wz - this.cz) / this.scale());
    }

    private boolean onChart(double mx, double my) {
        return mx >= this.left && mx < this.left + this.w && my >= this.top && my < this.top + this.h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.fill(this.left - 5, this.top - 5, this.left + this.w + 5, this.top + this.h + 5, FRAME);
        g.blit(LiveChart.PAPER, this.left, this.top, 0, 0, this.w, this.h, this.w, this.h);
        g.enableScissor(this.left, this.top, this.left + this.w, this.top + this.h);
        // the grid, every round number of blocks, labelled along the edges
        int step = LiveChart.gridStep(this.span);
        for (long gx = (long) Math.floor(this.worldX(this.left) / step) * step; gx <= this.worldX(this.left + this.w); gx += step) {
            int sx = Math.round(this.screenX(gx));
            g.fill(sx, this.top, sx + 1, this.top + this.h, GRID);
            g.drawString(this.font, String.valueOf(gx), sx + 2, this.top + 2, 0x905A3A1E, false);
        }
        for (long gz = (long) Math.floor(this.worldZ(this.top) / step) * step; gz <= this.worldZ(this.top + this.h); gz += step) {
            int sy = Math.round(this.screenY(gz));
            g.fill(this.left, sy, this.left + this.w, sy + 1, GRID);
            g.drawString(this.font, String.valueOf(gz), this.left + 2, sy + 2, 0x905A3A1E, false);
        }
        MapTableBlock.Table t = this.tableEntity();
        if (t != null) {
            // the marks, then the ship on top of everything
            for (int[] m : t.marks()) {
                this.marker(g, LiveChart.CROSS, this.screenX(m[0] + 0.5), this.screenY(m[1] + 0.5), 0, 14);
            }
            double[] s = LiveChart.ship(t);
            this.marker(g, LiveChart.ARROW, this.screenX(s[0]), this.screenY(s[1]), (float) s[2], 16);
        }
        g.disableScissor();
        // a compass point and the scale
        int nx = this.left + this.w - 22, ny = this.top + 26;
        g.drawCenteredString(this.font, "N", nx, ny - 16, INK);
        g.fill(nx, ny - 6, nx + 1, ny + 10, INK);
        g.fill(nx - 3, ny - 3, nx + 4, ny - 2, INK);
        int bar = (int) Math.round(step / this.scale());
        int bx = this.left + 10, by = this.top + this.h - 12;
        g.fill(bx, by, bx + bar, by + 2, INK);
        g.drawString(this.font, Component.translatable("map.seafarers.blocks", step), bx, by - 10, INK, false);
        g.drawCenteredString(this.font, Component.translatable("map.seafarers.hint"), this.width / 2, this.top + this.h + 10, 0xE0D8C0);
    }

    /** A map marker in front of the parchment (raised off it, so the paper never hides it), turned to an angle. */
    private void marker(GuiGraphics g, net.minecraft.world.level.saveddata.maps.MapDecoration kind, float x, float y, float degrees, int size) {
        var sprite = this.minecraft.getMapDecorationTextures().get(kind);
        g.pose().pushPose();
        g.pose().translate(x, y, 100);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(degrees));
        g.blit(-size / 2, -size / 2, 0, size, size, sprite);
        g.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(g);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (this.onChart(mx, my)) {
            if (button == 0) {
                this.pressX = mx;
                this.pressY = my;
                this.dragging = false;
                return true;
            }
            if (button == 1) {
                this.send(MapTablePayloads.Action.UNMARK, this.worldX(mx), this.worldZ(my));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && (this.dragging || Math.abs(mx - this.pressX) + Math.abs(my - this.pressY) > 3)) {
            this.dragging = true;
            this.cx -= dx * this.scale();
            this.cz -= dy * this.scale();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && !this.dragging && this.onChart(mx, my) && this.pressX != 0) {
            this.send(MapTablePayloads.Action.MARK, this.worldX(mx), this.worldZ(my));
        }
        this.dragging = false;
        this.pressX = 0;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY != 0) {
            // zoom round the pointer: the sea under it stays under it
            double wx = this.worldX(mx), wz = this.worldZ(my);
            this.span = Mth.clamp(this.span * (float) Math.pow(0.8, scrollY), MapTableBlock.MIN_SPAN, MapTableBlock.MAX_SPAN);
            this.cx = wx - (mx - (this.left + this.w / 2.0)) * this.scale();
            this.cz = wz - (my - (this.top + this.h / 2.0)) * this.scale();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_C) {
            this.centreOnShip();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void send(int action, double x, double z) {
        PacketDistributor.sendToServer(new MapTablePayloads.Action(this.table, action, x, z, this.span));
    }

    /** The chart stays as it's left, for the table (and everyone looking at it). */
    @Override
    public void removed() {
        this.send(MapTablePayloads.Action.VIEW, this.cx, this.cz);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * The chart lying on the table, seen from anywhere near: the part of the sea it was left showing (or round the
     * ship, if nobody has moved it), with the crew's marks and the ship's arrow drawn live. Like a paper chart, it's
     * laid square on the table: north toward the bow.
     */
    public static class TableRenderer implements BlockEntityRenderer<MapTableBlock.Table> {
        private static final float LENGTH = 2.5f;          // blocks: the chart's length across the table
        private static final float DEPTH = 1.55f;          // blocks: its depth (the table is two blocks deep)
        private static final float UNITS = 128;            // chart units across (the drawing space)
        private static final float BAND = UNITS * DEPTH / LENGTH;

        public TableRenderer(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public boolean shouldRenderOffScreen(MapTableBlock.Table table) {
            return true; // it reaches over the table's other blocks
        }

        @Override
        public void render(MapTableBlock.Table table, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (table.getLevel() == null) {
                return;
            }
            double[] ship = LiveChart.ship(table);
            double cx = table.viewSet() ? table.viewX() : ship[0], cz = table.viewSet() ? table.viewZ() : ship[1];
            float span = table.viewSpan();
            float k = UNITS / span;   // chart units per block of sea
            pose.pushPose();
            pose.translate(0.5, 13.85 / 16, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - MapTableBlock.facing(table.getBlockState()).toYRot()));
            pose.translate(0, 0, -0.5);   // the middle of the two-block-deep top
            pose.mulPose(Axis.XP.rotationDegrees(90));
            float s = LENGTH / UNITS;
            pose.scale(s, s, s);
            pose.translate(-UNITS / 2, -BAND / 2, 0);
            Matrix4f m = pose.last().pose();
            VertexConsumer paper = buffers.getBuffer(RenderType.text(LiveChart.PAPER));
            quad(paper, m, 0, 0, UNITS, BAND, -0.01f, light);
            // the grid
            int step = LiveChart.gridStep(span);
            VertexConsumer ink = buffers.getBuffer(RenderType.text(LiveChart.INK));
            for (long gx = (long) Math.ceil((cx - span / 2) / step) * step; gx <= cx + span / 2; gx += step) {
                float u = (float) ((gx - cx) * k + UNITS / 2);
                quad(ink, m, u - 0.18f, 0, u + 0.18f, BAND, -0.015f, light);
            }
            float zSpan = span * DEPTH / LENGTH;
            for (long gz = (long) Math.ceil((cz - zSpan / 2) / step) * step; gz <= cz + zSpan / 2; gz += step) {
                float v = (float) ((gz - cz) * k + BAND / 2);
                quad(ink, m, 0, v - 0.18f, UNITS, v + 0.18f, -0.015f, light);
            }
            for (int[] mark : table.marks()) {
                float u = (float) ((mark[0] + 0.5 - cx) * k + UNITS / 2), v = (float) ((mark[1] + 0.5 - cz) * k + BAND / 2);
                if (u > 3 && u < UNITS - 3 && v > 3 && v < BAND - 3) {
                    LiveChart.drawMarker(LiveChart.CROSS, u, v, 0, 7, pose, buffers, light);
                }
            }
            float su = (float) ((ship[0] - cx) * k + UNITS / 2), sv = (float) ((ship[1] - cz) * k + BAND / 2);
            if (su > 2 && su < UNITS - 2 && sv > 2 && sv < BAND - 2) {
                LiveChart.drawMarker(LiveChart.ARROW, su, sv, (float) ship[2], 8, pose, buffers, light);
            }
            pose.popPose();
        }

        private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int light) {
            vc.addVertex(m, x0, y1, z).setColor(-1).setUv(0, 1).setLight(light);
            vc.addVertex(m, x1, y1, z).setColor(-1).setUv(1, 1).setLight(light);
            vc.addVertex(m, x1, y0, z).setColor(-1).setUv(1, 0).setLight(light);
            vc.addVertex(m, x0, y0, z).setColor(-1).setUv(0, 0).setLight(light);
        }
    }
}
