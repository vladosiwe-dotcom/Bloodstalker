package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Ray sampling helpers for picking web anchor points. */
public final class WebTargeting {
    private WebTargeting() {}

    /**
     * Casts rays in a cone around {@code axis} and returns the best block hit that lies within
     * [minDist, maxDist] and at least {@code minRise} above the spinneret.
     */
    @Nullable
    public static BlockHitResult findAnchor(GorewidowEntity widow, Vec3 axis, double coneRadians, double minDist, double maxDist,
                                            double minRise, int samples) {
        RandomSource random = widow.getRandom();
        Vec3 from = widow.getSpinneretPos();
        Vec3 a = axis.normalize();
        Vec3 ref = Math.abs(a.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(ref).normalize();
        Vec3 v = a.cross(u).normalize();
        BlockHitResult best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < samples; i++) {
            double theta = random.nextDouble() * coneRadians;
            double phi = random.nextDouble() * Mth.TWO_PI;
            Vec3 dir = a.scale(Math.cos(theta)).add(u.scale(Math.sin(theta) * Math.cos(phi))).add(v.scale(Math.sin(theta) * Math.sin(phi)));
            Vec3 end = from.add(dir.scale(maxDist));
            BlockHitResult hit = widow.level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, widow));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            double dist = hit.getLocation().distanceTo(from);
            double rise = hit.getLocation().y - from.y;
            if (dist < minDist || rise < minRise) {
                continue;
            }
            double score = rise * 0.6 + dir.dot(a) * 4.0 - Math.abs(dist - (minDist + maxDist) * 0.5) * 0.15;
            if (hit.getDirection() == Direction.DOWN) {
                score += 2.0;
            }
            if (score > bestScore) {
                bestScore = score;
                best = hit;
            }
        }
        return best;
    }
}
