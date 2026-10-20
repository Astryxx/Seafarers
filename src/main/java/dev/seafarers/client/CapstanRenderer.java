package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.block.AnchorBlock;
import dev.seafarers.ship.block.CannonBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/**
 * The capstan's drum turns as the crew heave the anchor up and spins back when it is let go. The cable runs
 * forward to the hawse at the bow and down into the sea; with the anchor up, the anchor hangs at the bow.
 */
public class CapstanRenderer implements BlockEntityRenderer<AnchorBlock.Capstan> {

    public CapstanRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(AnchorBlock.Capstan cap, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        HelmBlockEntity helm = Ships.helmOf(cap.getLevel(), cap.getBlockPos());
        boolean down = helm != null && helm.anchored();
        float raise = helm == null ? 0 : helm.clientRaise(partialTick);
        float frame = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();

        // heaving turns the drum steadily; letting go, the ratchet lets it spin back as the cable runs out
        float target = down ? cap.depth() * (1 - raise / (float) HelmBlockEntity.ANCHOR_RAISE_MAX) : 0;
        if (cap.cable < 0) {
            cap.cable = target;
        }
        float before = cap.cable;
        float rate = target > cap.cable ? 0.45f : 0.3f;  // (it follows the heave closely; a drop runs out fast)
        cap.cable += Math.max(-rate * frame, Math.min(rate * frame, target - cap.cable));
        cap.angle += (cap.cable - before) * -40f;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite wood = SailRenderer.sprite("block/helm_wood");
        TextureAtlasSprite brass = SailRenderer.sprite("block/brass");
        TextureAtlasSprite iron = SailRenderer.sprite("block/cannon_iron");
        TextureAtlasSprite chain = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(net.minecraft.resources.ResourceLocation.withDefaultNamespace("block/chain"));

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(cap.getBlockState().getValue(AnchorBlock.FACING))));

        // the turning head: drum cap with its bars through it and brass bands
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(cap.angle));
        SailRenderer.box(vc, pose, wood, -0.28f, 0.2f, -0.28f, 0.28f, 0.72f, 0.28f, light);
        SailRenderer.box(vc, pose, brass, -0.3f, 0.3f, -0.3f, 0.3f, 0.36f, 0.3f, light);
        SailRenderer.box(vc, pose, brass, -0.3f, 0.6f, -0.3f, 0.3f, 0.66f, 0.3f, light);
        SailRenderer.box(vc, pose, wood, -0.34f, 0.72f, -0.34f, 0.34f, 0.84f, 0.34f, light);
        // the bars: the Sloop's two cross the drum, the Brigantine's three and the Galleon's four make a wheel of handles
        int bars = Math.max(2, cap.pegs());
        for (int i = 0; i < bars; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(i * 180f / bars));
            SailRenderer.box(vc, pose, wood, -1.15f, 0.76f, -0.06f, 1.15f, 0.84f, 0.06f, light);
            if (i == 0) {
                SailRenderer.box(vc, pose, brass, -1.2f, 0.74f, -0.08f, -1.1f, 0.86f, 0.08f, light);
                SailRenderer.box(vc, pose, brass, 1.1f, 0.74f, -0.08f, 1.2f, 0.86f, 0.08f, light);
            }
            pose.popPose();
        }
        pose.popPose();

        // the cable: off the drum, over the rail (fairlead), across to the hawse, then straight down
        org.joml.Vector3f drum = new org.joml.Vector3f(0, 0.45f, -0.3f);
        org.joml.Vector3f fair = new org.joml.Vector3f(cap.hawseSide(), cap.fairleadUp(), -cap.fairleadForward());
        org.joml.Vector3f hawse = new org.joml.Vector3f(cap.hawseSide(), -cap.hawseDown(), -cap.hawseForward());
        float drop = cap.cable;
        org.joml.Vector3f end = new org.joml.Vector3f(hawse).add(0, -drop - 0.05f, 0);
        SailRenderer.line(vc, pose, chain, drum, fair, 0.09f, light);
        SailRenderer.line(vc, pose, chain, fair, hawse, 0.09f, light);
        SailRenderer.line(vc, pose, chain, hawse, end, 0.09f, light);
        // the anchor at the end of the cable (stowed against the hull when weighed)
        pose.translate(end.x, end.y - 1.5f, end.z);
        drawAnchor(vc, pose, iron, light);
        pose.popPose();
    }

    /** A blocky fisherman's anchor, 1.5 blocks tall, ring at the top. */
    private static void drawAnchor(VertexConsumer vc, PoseStack pose, TextureAtlasSprite iron, int light) {
        SailRenderer.box(vc, pose, iron, -0.08f, 0.0f, -0.08f, 0.08f, 1.35f, 0.08f, light);   // shank
        SailRenderer.box(vc, pose, iron, -0.45f, 1.05f, -0.06f, 0.45f, 1.15f, 0.06f, light);  // stock
        SailRenderer.box(vc, pose, iron, -0.12f, 1.35f, -0.12f, 0.12f, 1.5f, 0.12f, light);   // ring
        SailRenderer.box(vc, pose, iron, -0.55f, 0.0f, -0.08f, 0.55f, 0.14f, 0.08f, light);   // crown
        SailRenderer.box(vc, pose, iron, -0.62f, 0.0f, -0.1f, -0.48f, 0.42f, 0.1f, light);    // arms
        SailRenderer.box(vc, pose, iron, 0.48f, 0.0f, -0.1f, 0.62f, 0.42f, 0.1f, light);
        SailRenderer.box(vc, pose, iron, -0.72f, 0.3f, -0.14f, -0.48f, 0.5f, 0.14f, light);   // flukes
        SailRenderer.box(vc, pose, iron, 0.48f, 0.3f, -0.14f, 0.72f, 0.5f, 0.14f, light);
    }

    @Override
    public boolean shouldRenderOffScreen(AnchorBlock.Capstan be) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AnchorBlock.Capstan be) {
        return new AABB(be.getBlockPos()).inflate(6, 12, 6);
    }
}
