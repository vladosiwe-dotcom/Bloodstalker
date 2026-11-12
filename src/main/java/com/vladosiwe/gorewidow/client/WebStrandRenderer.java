package com.vladosiwe.gorewidow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a silk line as two crossed ribbons along a sagging, vibrating curve, plus a faint wide
 * sheen ribbon so the strand catches light. Coordinates are relative to the current pose origin.
 */
public final class WebStrandRenderer {
    public static final ResourceLocation STRAND = Gorewidow.id("textures/entity/web_strand.png");
    private static final int SEGMENTS = 24;

    private WebStrandRenderer() {}

    /**
     * @param sag     downward sag at the midpoint in blocks (slack line)
     * @param twang   vibration amplitude in blocks (just pulled taut)
     * @param time    animation time in ticks
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, float sag, float twang,
                              float time, int light, float alpha) {
        render(poseStack, buffers, from, to, sag, twang, time, light, alpha, 1.0F);
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 from, Vec3 to, float sag, float twang,
                              float time, int light, float alpha, float thickness) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 0.05) {
            return;
        }
        Vec3 axis = dir.scale(1.0 / length);
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4) {
            side = axis.cross(new Vec3(1, 0, 0));
        }
        side = side.normalize();
        Vec3 side2 = axis.cross(side).normalize();

        Vec3[] pts = new Vec3[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = i / (float) SEGMENTS;
            Vec3 p = from.lerp(to, t);
            p = p.add(0, -sag * 4 * t * (1 - t), 0);
            if (twang > 0.001F) {
                double wave = Math.sin(Math.PI * t * 3) * Math.sin(time * 2.6) * twang * Math.sin(Math.PI * t);
                p = p.add(side.scale(wave));
            }
            pts[i] = p;
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(STRAND));
        PoseStack.Pose pose = poseStack.last();
        ribbon(vc, pose, pts, side, 0.035F * thickness, 0.0F, 0.5F, light, alpha, length);
        ribbon(vc, pose, pts, side2, 0.035F * thickness, 0.0F, 0.5F, light, alpha, length);
        ribbon(vc, pose, pts, side, 0.11F * thickness, 0.5F, 1.0F, light, alpha * 0.35F, length);
    }

    private static void ribbon(VertexConsumer vc, PoseStack.Pose pose, Vec3[] pts, Vec3 side, float halfWidth,
                               float u0, float u1, int light, float alpha, double length) {
        float vScale = (float) (length * 1.5) / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            Vec3 a = pts[i];
            Vec3 b = pts[i + 1];
            float fadeA = edgeFade(i / (float) SEGMENTS);
            float fadeB = edgeFade((i + 1) / (float) SEGMENTS);
            Vec3 o = side.scale(halfWidth);
            Vec3 n = b.subtract(a).cross(side);
            if (n.lengthSqr() < 1.0E-8) {
                n = new Vec3(0, 1, 0);
            }
            n = n.normalize();
            int ca = color(alpha * fadeA);
            int cb = color(alpha * fadeB);
            vertex(vc, pose, a.subtract(o), u0, i * vScale, ca, light, n);
            vertex(vc, pose, a.add(o), u1, i * vScale, ca, light, n);
            vertex(vc, pose, b.add(o), u1, (i + 1) * vScale, cb, light, n);
            vertex(vc, pose, b.subtract(o), u0, (i + 1) * vScale, cb, light, n);
        }
    }

    private static float edgeFade(float t) {
        return Mth.clamp(Math.min(t, 1 - t) * 12.0F, 0.55F, 1.0F);
    }

    private static int color(float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        return (a << 24) | 0xF4F0E6;
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, float u, float v, int color, int light, Vec3 n) {
        vc.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
    }
}
