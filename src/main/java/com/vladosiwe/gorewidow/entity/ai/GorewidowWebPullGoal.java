package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Snags a distant target with a web line and reels it in for the bite. */
public class GorewidowWebPullGoal extends Goal {
    private final GorewidowEntity widow;
    private int timeout;

    public GorewidowWebPullGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.widow.getTarget();
        if (target == null || !target.isAlive() || this.widow.isVehicle() || this.widow.getWebCooldown() > 0
                || this.widow.getWebMode() != WebMode.NONE || this.widow.isDraining()) {
            return false;
        }
        double dist = this.widow.distanceTo(target);
        return dist > 6.0 && dist < GorewidowEntity.WEB_RANGE - 6 && this.widow.hasLineOfSight(target)
                && this.widow.getRandom().nextInt(12) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        WebMode mode = this.widow.getWebMode();
        return this.timeout > 0 && (mode == WebMode.SHOT || mode == WebMode.TETHER);
    }

    @Override
    public void start() {
        LivingEntity target = this.widow.getTarget();
        if (target == null) {
            return;
        }
        this.widow.getNavigation().stop();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0);
        double flight = this.widow.getSpinneretPos().distanceTo(aim) / GorewidowEntity.WEB_SHOT_SPEED;
        aim = aim.add(target.getDeltaMovement().multiply(1, 0, 1).scale(flight));
        this.widow.getLookControl().setLookAt(aim);
        this.widow.shootWeb(aim, GorewidowEntity.WebPurpose.ATTACK);
        this.timeout = 80;
    }

    @Override
    public void tick() {
        this.timeout--;
        LivingEntity target = this.widow.getTarget();
        if (target != null) {
            this.widow.getLookControl().setLookAt(target, 30, 30);
        }
    }

    @Override
    public void stop() {
        if (this.widow.getWebMode() == WebMode.SHOT) {
            this.widow.releaseWeb(false);
        }
        this.widow.setWebCooldown(70 + this.widow.getRandom().nextInt(60));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
