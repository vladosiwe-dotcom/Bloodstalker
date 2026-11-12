package com.vladosiwe.gorewidow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEggEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A leathery, veined egg built from stacked boxes; it pulses faster as it nears hatching and shivers when cold. */
public class GorewidowEggRenderer extends EntityRenderer<GorewidowEggEntity> {
    private static final ResourceLocation TEXTURE = Gorewidow.id("textures/entity/gorewidow_egg.png");
    /** {halfWidth, bottom, top} in pixels. */
    private static final float[][] LAYERS = {{2.5F, 0, 1}, {3.5F, 1, 3}, {4.0F, 3, 6}, {3.5F, 6, 8}, {2.5F, 8, 9.5F}, {1.5F, 9.5F, 10.5F}};

    public GorewidowEggRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.25F;
    }

    @Override
    public void render(GorewidowEggEntity egg, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        float time = egg.tickCount + partialTick;
        float progress = egg.getProgress();
        float pulse = 1.0F + Mth.sin(time * (0.08F + progress * 0.4F)) * 0.03F * (0.3F + progress);
        if (!egg.isWarm()) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 1.7F) * 1.5F));
        }
        poseStack.scale(pulse, pulse, pulse);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (float[] layer : LAYERS) {
            box(vc, pose, layer[0] / 16, layer[1] / 16, layer[2] / 16, light);
        }
        poseStack.popPose();
        super.render(egg, yaw, partialTick, poseStack, buffers, light);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, float h, float y0, float y1, int light) {
        float v0 = 1 - y1 / (10.5F / 16);
        float v1 = 1 - y0 / (10.5F / 16);
        // four sides
        quad(vc, pose, -h, y0, -h, h, y0, -h, h, y1, -h, -h, y1, -h, v0, v1, 0, 0, -1, light);
        quad(vc, pose, h, y0, h, -h, y0, h, -h, y1, h, h, y1, h, v0, v1, 0, 0, 1, light);
        quad(vc, pose, -h, y0, h, -h, y0, -h, -h, y1, -h, -h, y1, h, v0, v1, -1, 0, 0, light);
        quad(vc, pose, h, y0, -h, h, y0, h, h, y1, h, h, y1, -h, v0, v1, 1, 0, 0, light);
        // caps
        quad(vc, pose, -h, y1, -h, h, y1, -h, h, y1, h, -h, y1, h, 0.0F, 0.15F, 0, 1, 0, light);
        quad(vc, pose, -h, y0, h, h, y0, h, h, y0, -h, -h, y0, -h, 0.85F, 1.0F, 0, -1, 0, light);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float v0, float v1,
                             float nx, float ny, float nz, int light) {
        vertex(vc, pose, x0, y0, z0, 0, v1, nx, ny, nz, light);
        vertex(vc, pose, x1, y1, z1, 1, v1, nx, ny, nz, light);
        vertex(vc, pose, x2, y2, z2, 1, v0, nx, ny, nz, light);
        vertex(vc, pose, x3, y3, z3, 0, v0, nx, ny, nz, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int light) {
        vc.addVertex(pose, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }

    @Override
    public ResourceLocation getTextureLocation(GorewidowEggEntity egg) {
        return TEXTURE;
    }
}
