package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Chases the target over floors, walls and ceilings and bites when in reach. */
public class GorewidowMeleeGoal extends Goal {
    private final GorewidowEntity widow;
    private int attackCooldown;
    private int repathTicks;

    public GorewidowMeleeGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.widow.getTarget();
        return target != null && target.isAlive() && !this.widow.isVehicle() && !this.widow.getWebMode().isRope()
                && this.widow.getWebMode() != WebMode.SHOT && !this.widow.isDraining() && !this.widow.isInSittingPose();
    }

    @Override
    public void start() {
        this.repathTicks = 0;
    }

    @Override
    public void stop() {
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = this.widow.getTarget();
        if (target == null) {
            return;
        }
        this.widow.getLookControl().setLookAt(target, 30, 30);
        if (this.widow.getWebMode() != WebMode.TETHER) {
            if (this.widow.isOnFloor()) {
                if (--this.repathTicks <= 0) {
                    this.repathTicks = 6 + this.widow.getRandom().nextInt(6);
                    this.widow.getNavigation().moveTo(target, 1.25);
                }
            } else {
                this.widow.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.2);
            }
        }
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        double reach = this.widow.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.4;
        if (this.attackCooldown <= 0 && this.widow.distanceTo(target) <= reach && this.widow.hasLineOfSight(target)) {
            this.attackCooldown = 18;
            this.widow.swing(InteractionHand.MAIN_HAND);
            this.widow.doHurtTarget(target);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
