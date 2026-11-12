package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Closes long or vertical gaps toward the target by swinging: fire a line at a high anchor ahead,
 * pump the swing toward the target and let go at the forward arc (or reel up to a high target).
 */
public class GorewidowWebTraverseGoal extends Goal {
    private final GorewidowEntity widow;
    private int cooldown;
    private int ticks;
    private boolean climbToAnchor;

    public GorewidowWebTraverseGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.widow.isBaby()) {
            return false;
        }
        LivingEntity target = this.widow.getTarget();
        if (target == null || !target.isAlive() || this.widow.isVehicle() || this.widow.isDraining()
                || this.widow.getWebMode() != WebMode.NONE || this.widow.getWebCooldown() > 0) {
            return false;
        }
        double horizontal = target.position().subtract(this.widow.position()).horizontalDistance();
        double rise = target.getY() - this.widow.getY();
        boolean stuck = this.widow.getNavigation().isStuck() || (this.widow.getNavigation().isDone() && horizontal > 4);
        return (horizontal > 14 || rise > 3.5 || stuck) && this.widow.getRandom().nextInt(10) == 0;
    }

    @Override
    public void start() {
        LivingEntity target = this.widow.getTarget();
        this.ticks = 0;
        if (target == null) {
            return;
        }
        Vec3 toTarget = target.position().subtract(this.widow.position());
        double rise = toTarget.y;
        Vec3 flat = new Vec3(toTarget.x, 0, toTarget.z);
        flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : new Vec3(0, 0, 1);
        Vec3 axis = rise > 3.5 ? flat.scale(0.35).add(0, 1, 0) : flat.scale(0.7).add(0, 0.75, 0);
        BlockHitResult anchor = WebTargeting.findAnchor(this.widow, axis, 0.7, 5, GorewidowEntity.WEB_RANGE - 8, 3.0, 28);
        if (anchor == null) {
            this.cooldown = 40;
            return;
        }
        this.climbToAnchor = rise > 3.5 && anchor.getLocation().y > target.getY() - 2;
        this.widow.getNavigation().stop();
        this.widow.shootWeb(anchor.getLocation(), GorewidowEntity.WebPurpose.TRAVERSE);
    }

    @Override
    public boolean canContinueToUse() {
        WebMode mode = this.widow.getWebMode();
        boolean airborne = !this.widow.onGround() && this.widow.isOnFloor();
        return this.ticks < 120 && !this.widow.isVehicle() && (mode == WebMode.SHOT || mode.isRope() || (airborne && this.ticks > 0));
    }

    @Override
    public void tick() {
        this.ticks++;
        LivingEntity target = this.widow.getTarget();
        if (target == null || !this.widow.getWebMode().isRope()) {
            this.widow.setAiReel(0);
            return;
        }
        this.widow.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
        this.widow.setAiGrabSurfaces(true);
        int rope = this.widow.getRopeTicks();
        if (this.climbToAnchor) {
            this.widow.setAiReel(1.0F);
            return;
        }
        this.widow.setAiReel(rope < 14 ? 0.5F : 0.0F);
        Vec3 v = this.widow.getDeltaMovement();
        Vec3 to = target.position().subtract(this.widow.position());
        Vec3 flatTo = new Vec3(to.x, 0, to.z);
        boolean forwardArc = flatTo.lengthSqr() > 1.0E-4 && new Vec3(v.x, 0, v.z).dot(flatTo.normalize()) > 0.35 && v.y > -0.15;
        Vec3 anchor = this.widow.getAnchor();
        boolean pastAnchor = new Vec3(this.widow.getX() - anchor.x, 0, this.widow.getZ() - anchor.z).dot(flatTo) > 0;
        if ((rope > 10 && forwardArc && pastAnchor) || rope > 80) {
            this.widow.setDeltaMovement(v.add(0, 0.15, 0));
            this.widow.releaseWeb(true);
        }
    }

    @Override
    public void stop() {
        this.widow.setAiReel(0);
        if (this.widow.getWebMode() == WebMode.SHOT) {
            this.widow.releaseWeb(false);
        }
        this.cooldown = 60 + this.widow.getRandom().nextInt(60);
        this.widow.setWebCooldown(Math.max(this.widow.getWebCooldown(), 30));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
