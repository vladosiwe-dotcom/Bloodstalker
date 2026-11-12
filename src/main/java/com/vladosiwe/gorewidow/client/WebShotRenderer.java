package com.vladosiwe.gorewidow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.WebShotEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A spinning, camera-facing silk glob. The line behind it is drawn by the Gorewidow renderer. */
public class WebShotRenderer extends EntityRenderer<WebShotEntity> {
    private static final ResourceLocation TEXTURE = Gorewidow.id("textures/entity/web_glob.png");

    public WebShotRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(WebShotEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        poseStack.translate(0, entity.getBbHeight() * 0.5, 0);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 40));
        float s = 0.45F;
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        vertex(vc, pose, -s, -s, 0, 1, light);
        vertex(vc, pose, s, -s, 1, 1, light);
        vertex(vc, pose, s, s, 1, 0, light);
        vertex(vc, pose, -s, s, 0, 0, light);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        vc.addVertex(pose, x, y, 0).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(WebShotEntity entity) {
        return TEXTURE;
    }
}
