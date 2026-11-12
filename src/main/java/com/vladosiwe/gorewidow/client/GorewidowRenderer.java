package com.vladosiwe.gorewidow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Orients the model to whatever the Gorewidow clings to (floor, wall, ceiling, web line) using the
 * rig's smoothed body frame, and draws its web lines from the tracked spinneret bone.
 */
public class GorewidowRenderer extends GeoEntityRenderer<GorewidowEntity> {
    public GorewidowRenderer(EntityRendererProvider.Context context) {
        super(context, new GorewidowModel());
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 1.3F;
    }

    @Override
    public void preRender(PoseStack poseStack, GorewidowEntity animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        if (!isReRender) {
            model.getBone("spinneret").ifPresent(b -> b.setTrackingMatrices(true));
            model.getBone("head").ifPresent(b -> b.setTrackingMatrices(true));
        }
    }

    @Override
    protected void applyRotations(GorewidowEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick, float nativeScale) {
        GorewidowRig rig = GorewidowRigs.get(animatable);
        Vec3 offset = rig.updateFrame(animatable, partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(rig.orientation());
    }

    @Override
    public void renderFinal(PoseStack poseStack, GorewidowEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                            @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int colour) {
        GorewidowRig rig = GorewidowRigs.get(animatable);
        rig.captureTrackedBones(this.getGeoModel());
        this.renderWebLines(poseStack, animatable, rig, bufferSource, partialTick);
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
    }

    private void renderWebLines(PoseStack poseStack, GorewidowEntity widow, GorewidowRig rig, MultiBufferSource buffers, float partialTick) {
        Vec3 base = widow.getPosition(partialTick);
        Vec3 from = rig.spinneretLocal();
        float time = widow.tickCount + partialTick;
        Vec3 end = rig.currentLineEnd(widow, partialTick);
        WebMode mode = widow.getWebMode();
        if (end != null) {
            Vec3 to = end.subtract(base);
            double dist = to.distanceTo(from);
            float sag;
            float twang = rig.twang();
            if (mode.isRope()) {
                sag = (float) Math.max(0, widow.getRopeLength() - dist) * 0.45F;
            } else if (mode == WebMode.SHOT) {
                sag = (float) dist * 0.015F;
            } else {
                sag = 0;
                twang = Math.max(twang, 0.035F);
            }
            this.strand(poseStack, buffers, widow, base, from, to, sag, twang, time, 1.0F);
        }
        Vec3 retract = rig.retractFrom();
        if (retract != null) {
            float p = rig.retractProgress(partialTick);
            Vec3 start = retract.subtract(base);
            Vec3 tip = start.lerp(from, p);
            float sag = (float) tip.distanceTo(from) * 0.12F * (1 - p);
            this.strand(poseStack, buffers, widow, base, from, tip, sag, 0.12F * (1 - p), time, 1.0F - p * 0.5F);
        }
    }

    private void strand(PoseStack poseStack, MultiBufferSource buffers, GorewidowEntity widow, Vec3 base, Vec3 from, Vec3 to,
                        float sag, float twang, float time, float alpha) {
        Vec3 mid = base.add(from.lerp(to, 0.5));
        int light = LevelRenderer.getLightColor(widow.level(), BlockPos.containing(mid));
        WebStrandRenderer.render(poseStack, buffers, from, to, sag, twang, time, light, alpha);
    }

    @Override
    protected float getDeathMaxRotation(GorewidowEntity animatable) {
        return 0;
    }
}
