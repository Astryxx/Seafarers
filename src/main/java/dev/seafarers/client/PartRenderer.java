package dev.seafarers.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.seafarers.Seafarers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Draws a separately-loaded block model part (a wheel, a barrel) so it can be moved by a renderer. */
public final class PartRenderer {
    public static final ModelResourceLocation HELM_WHEEL = ModelResourceLocation.standalone(Seafarers.id("block/helm_wheel"));
    public static final ModelResourceLocation CANNON_BARREL = ModelResourceLocation.standalone(Seafarers.id("block/cannon_barrel"));
    public static final ModelResourceLocation HARPOON_GUN = ModelResourceLocation.standalone(Seafarers.id("block/harpoon_gun"));
    public static final ModelResourceLocation HARPOON_BOLT = ModelResourceLocation.standalone(Seafarers.id("block/harpoon_bolt"));

    public static void render(ModelResourceLocation part, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(part);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                buffers.getBuffer(Sheets.cutoutBlockSheet()), state, model, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }

    private PartRenderer() {}
}
