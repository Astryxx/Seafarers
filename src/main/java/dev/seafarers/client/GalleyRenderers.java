package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.seafarers.ship.block.FlagBlock;
import dev.seafarers.ship.block.StoveBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/** Things set on ship fittings: a banner flying from the flagstaff, food on the stove. */
public final class GalleyRenderers {

    /**
     * A hoisted banner, drawn with vanilla's banner model and patterns turned on its side: the hoist runs down the
     * staff and the fly streams downwind, flapping.
     */
    public static class Flag implements BlockEntityRenderer<FlagBlock.Flag> {
        private final ModelPart flag;

        public Flag(BlockEntityRendererProvider.Context ctx) {
            this.flag = ctx.bakeLayer(ModelLayers.BANNER).getChild("flag");
        }

        @Override
        public void render(FlagBlock.Flag be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            ItemStack banner = be.banner();
            if (!(banner.getItem() instanceof BannerItem item)) {
                return;
            }
            float t = (be.getLevel() == null ? 0 : be.getLevel().getGameTime()) + partialTick;
            pose.pushPose();
            pose.translate(0.5, 0.35, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(be.stream() + 4f * Mth.sin(t * 0.07f)));
            pose.mulPose(Axis.ZP.rotationDegrees(90));
            pose.scale(1.0f, -1.0f, -1.0f);   // a full-sized ensign: 1.25 blocks at the hoist, 2.5 on the fly
            this.flag.y = 0;
            this.flag.xRot = 0.06f * Mth.sin(t * 0.35f) + 0.03f * Mth.sin(t * 0.9f + 1.3f);
            BannerPatternLayers patterns = banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
            BannerRenderer.renderPatterns(pose, buffers, light, OverlayTexture.NO_OVERLAY, this.flag, ModelBakery.BANNER_BASE, true,
                    item.getColor(), patterns);
            pose.popPose();
        }

        @Override
        public boolean shouldRenderOffScreen(FlagBlock.Flag be) {
            return true; // the fly reaches well outside the staff's block
        }
    }

    /** Whatever is cooking, in the frying pan: raw at first, cooked once it's done, charred when it's burnt. */
    public static class Stove implements BlockEntityRenderer<StoveBlock.Stove> {
        public Stove(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(StoveBlock.Stove stove, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            ItemStack food = stove.food();
            if (food.isEmpty()) {
                return;
            }
            food = switch (stove.doneness()) {
                case COOKED -> stove.result().isEmpty() ? food : stove.result();
                case BURNT -> new ItemStack(dev.seafarers.registry.ModItems.CHARRED_FOOD.get());
                default -> food;
            };
            pose.pushPose();
            // the pan is on the front left of the stove top (model space, facing north), so turn with the stove
            float yaw = switch (stove.getBlockState().getValue(StoveBlock.FACING)) {
                case SOUTH -> 180;
                case WEST -> 90;
                case EAST -> 270;
                default -> 0;
            };
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-0.5, 0, -0.5);
            pose.translate(6.5 / 16, 14.25 / 16, 6 / 16);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.42f, 0.42f, 0.42f);
            Minecraft.getInstance().getItemRenderer().renderStatic(food, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, stove.getLevel(), (int) stove.getBlockPos().asLong());
            pose.popPose();
        }
    }

    /** The voyages put down on the voyage table, side by side. */
    public static class Voyages implements BlockEntityRenderer<dev.seafarers.ship.block.VoyageTableBlock.Table> {
        public Voyages(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(dev.seafarers.ship.block.VoyageTableBlock.Table table, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
            var proposals = table.proposals();
            // laid out along the table, which runs two blocks east of this one in its own frame
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-dev.seafarers.ship.block.CannonBlockEntity.facingYRot(
                    table.getBlockState().getValue(dev.seafarers.ship.block.DecorBlock.FACING))));
            pose.translate(-0.5, 0, -0.5);
            for (int i = 0; i < proposals.size(); i++) {
                pose.pushPose();
                pose.translate(0.6 + 0.45 * i, 13.6 / 16 + 0.01 * i, 0.55);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees(-12 + 9 * i));
                pose.scale(0.38f, 0.38f, 0.38f);
                Minecraft.getInstance().getItemRenderer().renderStatic(proposals.get(i).voyage(), ItemDisplayContext.FIXED, light,
                        OverlayTexture.NO_OVERLAY, pose, buffers, table.getLevel(), i);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private GalleyRenderers() {}
}
