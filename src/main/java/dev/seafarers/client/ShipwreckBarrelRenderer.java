package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.seafarers.ship.ShipwreckBarrelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A shipwreck barrel: vanilla's barrel on its side, bobbing and rolling a little on the swell. */
public class ShipwreckBarrelRenderer extends EntityRenderer<ShipwreckBarrelEntity> {
    private static final BlockState BARREL = Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.NORTH);

    public ShipwreckBarrelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(ShipwreckBarrelEntity barrel, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float t = barrel.tickCount + partialTick + barrel.getId() * 13;
        pose.pushPose();
        pose.translate(0, 0.05 + Mth.sin(t * 0.09f) * 0.05, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-barrel.getYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.07f) * 8f));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.05f + 1f) * 5f));
        pose.scale(0.85f, 0.85f, 0.85f);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(BARREL, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(barrel, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ShipwreckBarrelEntity barrel) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
