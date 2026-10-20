package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.seafarers.ship.block.BowspritBlock;
import dev.seafarers.ship.block.CannonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/** A long spar rising forward from the stem, with a stay running back down to the cutwater. */
public class BowspritRenderer implements BlockEntityRenderer<BowspritBlock.Heel> {

    public BowspritRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BowspritBlock.Heel be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        var spar = SailRenderer.sprite("block/mast");
        var rope = SailRenderer.sprite("block/rope");
        pose.pushPose();
        pose.translate(0.5, 0.25, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(be.getBlockState().getValue(BowspritBlock.FACING))));
        pose.pushPose();
        float LENGTH = be.length(), RAISE = be.raise();
        pose.mulPose(Axis.XP.rotationDegrees(RAISE));
        SailRenderer.box(vc, pose, spar, -0.17f, -0.17f, -LENGTH, 0.17f, 0.17f, 0.3f, light);
        SailRenderer.box(vc, pose, spar, -0.12f, -0.12f, -LENGTH - 1.5f, 0.12f, 0.12f, -LENGTH, light); // jib-boom
        pose.popPose();
        // bobstay: from near the tip down to the stem, below the figurehead
        float tipY = (float) Math.sin(Math.toRadians(RAISE)) * (LENGTH - 0.6f);
        float tipZ = -(float) Math.cos(Math.toRadians(RAISE)) * (LENGTH - 0.6f);
        SailRenderer.line(vc, pose, rope, new org.joml.Vector3f(0, -be.bobstayDown(), -be.bobstayForward()),
                new org.joml.Vector3f(0, tipY, tipZ), 0.04f, light);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BowspritBlock.Heel be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(BowspritBlock.Heel be) {
        return new AABB(be.getBlockPos()).inflate(9);
    }
}
