package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.Seafarers;
import dev.seafarers.ship.MermaidEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The mermaid: a sailor's head and arms above the water, a scaled tail and fin below it. Her waist is at the
 * entity's position (the waterline); the tail sways as she treads water and she waves the sailor over.
 */
public class MermaidRenderer extends EntityRenderer<MermaidEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Seafarers.id("mermaid"), "main");
    private static final ResourceLocation TEXTURE = Seafarers.id("textures/entity/mermaid.png");
    private final Model model;

    public MermaidRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.model = new Model(ctx.bakeLayer(LAYER));
        this.shadowRadius = 0;
    }

    @Override
    public void render(MermaidEntity mermaid, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = mermaid.tailPhase + partialTick * 0.12f;
        pose.pushPose();
        pose.translate(0, Mth.sin(t * 0.5f) * 0.06f, 0);   // treading water
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partialTick, mermaid.yRotO, mermaid.getYRot())));
        pose.scale(-1, -1, 1);
        this.model.animate(t);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(mermaid, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(MermaidEntity mermaid) {
        return TEXTURE;
    }

    /** Model space: the waist at the origin, y down (so up is negative), 16 pixels to a block. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4, -12, -2, 8, 12, 4), PartPose.ZERO);
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8),
                PartPose.offset(0, -12, 0));
        head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, 4, 8, 14, 1), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.5f, -1, -1.5f, 3, 11, 3),
                PartPose.offset(-5.5f, -11, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.5f, -1, -1.5f, 3, 11, 3),
                PartPose.offset(5.5f, -11, 0));
        PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 32).addBox(-4, 0, -2, 8, 8, 4), PartPose.ZERO);
        PartDefinition tail = hips.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(24, 32).addBox(-3, 0, -2, 6, 8, 4),
                PartPose.offset(0, 8, 0));
        PartDefinition tip = tail.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(44, 32).addBox(-2, 0, -1.5f, 4, 6, 3),
                PartPose.offset(0, 8, 0));
        tip.addOrReplaceChild("fin", CubeListBuilder.create().texOffs(0, 48).addBox(-5, 0, -0.5f, 10, 4, 1), PartPose.offset(0, 6, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static class Model extends EntityModel<MermaidEntity> {
        private final ModelPart root, head, rightArm, leftArm, hips, tail, tip, fin;

        Model(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.rightArm = root.getChild("right_arm");
            this.leftArm = root.getChild("left_arm");
            this.hips = root.getChild("hips");
            this.tail = this.hips.getChild("tail");
            this.tip = this.tail.getChild("tip");
            this.fin = this.tip.getChild("fin");
        }

        /** The tail ripples from the hips to the fin; one arm beckons, the other sculls. */
        void animate(float t) {
            this.hips.xRot = Mth.sin(t) * 0.12f;
            this.tail.xRot = Mth.sin(t - 0.7f) * 0.28f;
            this.tip.xRot = Mth.sin(t - 1.4f) * 0.38f;
            this.fin.xRot = Mth.sin(t - 2.1f) * 0.5f;
            this.rightArm.zRot = 2.6f + Mth.sin(t * 1.6f) * 0.35f;   // raised, waving
            this.rightArm.xRot = -0.2f;
            this.leftArm.zRot = -0.35f + Mth.sin(t * 0.8f) * 0.15f;
            this.leftArm.xRot = Mth.sin(t * 0.8f) * 0.3f;
            this.head.yRot = Mth.sin(t * 0.3f) * 0.15f;
        }

        @Override
        public void setupAnim(MermaidEntity mermaid, float limbSwing, float limbSwingAmount, float age, float headYaw, float headPitch) {
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
            this.root.render(pose, vc, light, overlay, color);
        }
    }
}
