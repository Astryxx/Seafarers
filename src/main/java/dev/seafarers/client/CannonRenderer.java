package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.seafarers.ship.block.CannonBlock;
import dev.seafarers.ship.block.CannonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Points the barrel where the gunner aims; the carriage is the block's own model. */
public class CannonRenderer implements BlockEntityRenderer<CannonBlockEntity> {
    public CannonRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CannonBlockEntity cannon, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, CannonBlockEntity.PIVOT_Y, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(cannon.getBlockState().getValue(CannonBlock.FACING))));
        pose.mulPose(Axis.YP.rotationDegrees(-cannon.aimYaw()));
        pose.mulPose(Axis.XP.rotationDegrees(cannon.aimPitch()));
        pose.translate(-0.5, -CannonBlockEntity.PIVOT_Y, -0.5);
        PartRenderer.render(PartRenderer.CANNON_BARREL, cannon.getBlockState(), pose, buffers, light, overlay);
        pose.popPose();
    }
}
