package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.ship.HelmBlockEntity;
import dev.seafarers.ship.block.CannonBlockEntity;
import dev.seafarers.ship.block.HelmBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Vector3f;

/** Spins the wheel with the rudder (the Sloop's one turn from lock to lock, the Galleon's two each way); on a rowboat, works the oars. */
public class HelmRenderer implements BlockEntityRenderer<HelmBlockEntity> {
    /** Oarlocks: this far out from the bench's middle and up from its floor, in blocks. */
    private static final float LOCK_OUT = 1.3f, LOCK_UP = 0.95f;

    public HelmRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(HelmBlockEntity helm, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float frame = net.minecraft.client.Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        boolean mine = StationClient.operatingHelm(helm.getBlockPos());
        helm.shownWheel = helm.clientWheel(partialTick, frame, StationClient.steer(), mine);
        helm.refreshDryHoldOnce();
        FineWaterOcclusion.apply(helm);
        Liveries.sync(helm);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(helm.getBlockState().getValue(HelmBlock.FACING))));
        pose.translate(-0.5, 0, -0.5);
        if (helm.rowboat()) {
            this.oars(helm, pose, buffers, light);
        } else {
            // the wheel turns about its axle, which runs fore and aft through (8, 9.5) in model pixels
            pose.translate(0.5, 9.5 / 16, 0.5);
            pose.mulPose(Axis.ZP.rotationDegrees(-helm.shownWheel * 360f * helm.wheelTurns()));   // the bigger ships' wheels spin further
            pose.translate(-0.5, -9.5 / 16, -0.5);
            PartRenderer.render(PartRenderer.HELM_WHEEL, helm.getBlockState(), pose, buffers, light, overlay);
        }
        pose.popPose();
    }

    /**
     * Two oars pivoting in their oarlocks. Each sweeps aft through the water and comes forward lifted clear; the
     * one on the side she turns away from pulls harder, the other backs water. At rest they lie level, blades out.
     */
    private void oars(HelmBlockEntity helm, PoseStack pose, MultiBufferSource buffers, int light) {
        helm.shownRow += (helm.row() - helm.shownRow) * 0.2f;
        float pull = Math.max(Math.abs(helm.shownRow), Math.abs(helm.shownWheel));
        if (pull > 0.02f) {
            helm.oarPhase += 0.09f + 0.05f * pull;
        } else {
            helm.oarPhase += (Math.round(helm.oarPhase / Mth.TWO_PI) * Mth.TWO_PI - helm.oarPhase) * 0.1f; // settle
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite wood = SailRenderer.sprite("block/helm_wood");
        TextureAtlasSprite blade = SailRenderer.sprite("block/hull_planks");
        for (int side = -1; side <= 1; side += 2) {
            // starboard (+x) pulls harder turning to port; backing water reverses the stroke
            float effort = Mth.clamp(helm.shownRow + side * -helm.shownWheel * 0.8f, -1f, 1f);
            float sweep = Mth.sin(helm.oarPhase) * 32f * Math.signum(effort) * Math.min(1f, Math.abs(effort) * 1.5f);
            float lift = (Mth.cos(helm.oarPhase) * 0.5f + 0.5f) * Math.min(1f, Math.abs(effort) * 1.5f);
            pose.pushPose();
            pose.translate(0.5 + side * LOCK_OUT, LOCK_UP, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(side * sweep));
            pose.mulPose(Axis.ZP.rotationDegrees(side * (-18f + 12f * lift)));   // blade down into the water, lifted on the return
            Vector3f handle = new Vector3f(-side * 1.05f, 0, 0);
            Vector3f tip = new Vector3f(side * 2.3f, 0, 0);
            SailRenderer.line(vc, pose, wood, handle, tip, 0.045f, light);
            pose.translate(side * 2.3f, 0, 0);
            SailRenderer.box(vc, pose, blade, side > 0 ? 0 : -0.75f, -0.03f, -0.13f, side > 0 ? 0.75f : 0, 0.03f, 0.13f, light);
            pose.popPose();
        }
    }
}
