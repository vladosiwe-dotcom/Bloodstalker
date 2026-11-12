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
        // silk that hauls the rider in under the belly, and snaps when they let go
        if (widow.isMounting() && widow.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player rider) {
            float p = widow.getMountProgress(partialTick);
            this.bellyStrands(poseStack, buffers, widow, rig, base, rider.getPosition(partialTick).add(0, 0.9, 0), time, 1.0F - p * 0.6F, 0.12F * (1 - p));
        } else if (widow.isDismounting()) {
            float p = widow.getDismountProgress(partialTick);
            Vec3 snapped = widow.getDismountPoint().lerp(rig.modelToWorld(0, 12, 0), p);
            this.bellyStrands(poseStack, buffers, widow, rig, base, snapped, time, 1.0F - p, 0.2F * (1 - p));
        }
        // the taming feed: threads latched onto the crouching player
        net.minecraft.world.entity.player.Player feeder = widow.getTameFeeder();
        if (feeder != null) {
            this.bellyStrands(poseStack, buffers, widow, rig, base, feeder.getPosition(partialTick).add(0, 1.0, 0), time, 0.8F, 0.06F);
        }
        // slingshot: two lines that fire, pull and snap back
        int sling = widow.ticksSinceSlingshot();
        if (sling >= 0 && sling < 10) {
            float p = (sling + partialTick) / 10.0F;
            for (int i = 0; i < 2; i++) {
                Vec3 anchor = widow.getSlingAnchor(i == 0).subtract(base);
                Vec3 tip = p < 0.3F ? anchor : anchor.lerp(from, (p - 0.3F) / 0.7F);
                this.strand(poseStack, buffers, widow, base, from, tip, 0.05F, 0.15F * (1 - p), time, 1.0F - p * 0.5F);
            }
        }
        // parachute: a fan of silk billowing up from the spinneret
        if (widow.isGliding()) {
            Vec3 upv = rig.up();
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + time * 0.05;
                Vec3 spread = new Vec3(Math.cos(a), 0, Math.sin(a)).scale(1.4);
                Vec3 tip = from.add(upv.scale(2.4)).add(spread).add(0, Math.sin(time * 0.3 + i) * 0.15, 0);
                this.strand(poseStack, buffers, widow, base, from, tip, 0.25F, 0.05F, time + i * 7, 0.6F);
            }
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

    /** A spread of threads from points under the belly to a target point. */
    private void bellyStrands(PoseStack poseStack, MultiBufferSource buffers, GorewidowEntity widow, GorewidowRig rig, Vec3 base,
                              Vec3 targetWorld, float time, float alpha, float twang) {
        float[][] anchors = {{-5, 12, -6}, {5, 12, -6}, {-5, 12, 6}, {5, 12, 6}, {0, 11, 0}};
        Vec3 to = targetWorld.subtract(base);
        for (int i = 0; i < anchors.length; i++) {
            Vec3 a = rig.modelToWorld(anchors[i][0], anchors[i][1], anchors[i][2]).subtract(base);
            Vec3 end = to.add((i % 2 == 0 ? -0.15 : 0.15), (i - 2) * 0.12, 0);
            Vec3 mid = base.add(a.lerp(end, 0.5));
            int light = LevelRenderer.getLightColor(widow.level(), BlockPos.containing(mid));
            WebStrandRenderer.render(poseStack, buffers, a, end, 0.02F, twang, time + i * 5, light, alpha, 1.8F);
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
