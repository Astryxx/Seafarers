package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.seafarers.ship.HarpoonEntity;
import dev.seafarers.ship.Ships;
import dev.seafarers.ship.block.CannonBlockEntity;
import dev.seafarers.ship.block.HarpoonBlock;
import dev.seafarers.ship.block.HarpoonBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** The harpoon gun (aimed like a cannon) and the harpoon in flight or stuck, with its line back to the gun. */
public final class HarpoonRenderers {

    /** The gun on its post: the stock swings where the gunner looks; the harpoon sits in it until fired. */
    public static class Gun implements BlockEntityRenderer<HarpoonBlockEntity> {
        public Gun(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(HarpoonBlockEntity gun, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            BlockState state = gun.getBlockState();
            pose.pushPose();
            pose.translate(0.5, HarpoonBlockEntity.PIVOT_Y, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-CannonBlockEntity.facingYRot(state.getValue(HarpoonBlock.FACING))));
            pose.translate(0, 0, -HarpoonBlockEntity.FORWARD);   // the pivot, forward of the block's centre
            pose.mulPose(Axis.YP.rotationDegrees(-gun.aimYaw()));
            pose.mulPose(Axis.XP.rotationDegrees(gun.aimPitch()));
            pose.translate(-0.5, -HarpoonBlockEntity.PIVOT_Y, -0.5 + HarpoonBlockEntity.FORWARD);
            PartRenderer.render(PartRenderer.HARPOON_GUN, state, pose, buffers, light, overlay);
            if (gun.loaded()) {
                pose.translate(0, 0, -HarpoonBlockEntity.FORWARD); // the bolt model isn't shifted forward like the gun's
                PartRenderer.render(PartRenderer.HARPOON_BOLT, state, pose, buffers, light, overlay);
            }
            pose.popPose();
        }
    }

    /** The harpoon itself (the same model as the one in the gun) and a rope from it to the gun's muzzle. */
    public static class Bolt extends EntityRenderer<HarpoonEntity> {
        public Bolt(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public boolean shouldRender(HarpoonEntity harpoon, Frustum frustum, double x, double y, double z) {
            return true; // the line can be in view when the harpoon isn't
        }

        @Override
        public void render(HarpoonEntity harpoon, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, harpoon.yRotO, harpoon.getYRot()) + 180f));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, harpoon.xRotO, harpoon.getXRot())));
            // the bolt model points north with its tip at z -13 px, 12 px up: put the tip on the entity
            pose.translate(-0.5, -12 / 16.0, 13 / 16.0);
            PartRenderer.render(PartRenderer.HARPOON_BOLT, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), pose, buffers,
                    light, OverlayTexture.NO_OVERLAY);
            pose.popPose();

            if (harpoon.level().getBlockEntity(harpoon.gun()) instanceof HarpoonBlockEntity gun) {
                Vec3 muzzle = Ships.toWorld(harpoon.level(), gun.muzzlePlot());
                Vec3 here = harpoon.getPosition(partialTick);
                Vec3 d = muzzle.subtract(here);
                VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
                TextureAtlasSprite rope = SailRenderer.sprite("block/rope");
                // a little sag while it's paid out, straight when it's hauling
                int segments = 12;
                double len = d.length();
                double sag = harpoon.stuck() ? 0.05 : 0.15 * len / 10;
                Vector3f prev = new Vector3f();
                for (int i = 1; i <= segments; i++) {
                    float t = i / (float) segments;
                    Vector3f p = new Vector3f((float) (d.x * t), (float) (d.y * t - sag * Math.sin(Math.PI * t)), (float) (d.z * t));
                    SailRenderer.line(vc, pose, rope, prev, p, 0.03f, light);
                    prev = p;
                }
            }
        }

        @Override
        public ResourceLocation getTextureLocation(HarpoonEntity harpoon) {
            return InventoryMenu.BLOCK_ATLAS;
        }
    }

    private HarpoonRenderers() {}
}
