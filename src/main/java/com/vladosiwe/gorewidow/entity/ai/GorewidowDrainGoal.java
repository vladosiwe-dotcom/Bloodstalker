package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Holds onto a victim while feeding; the actual draining happens in the entity tick. */
public class GorewidowDrainGoal extends Goal {
    private final GorewidowEntity widow;

    public GorewidowDrainGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return this.widow.isDraining() && !this.widow.isVehicle();
    }

    @Override
    public void start() {
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity victim = this.widow.getDrainTarget();
        if (victim == null) {
            return;
        }
        this.widow.getLookControl().setLookAt(victim, 60, 60);
        if (this.widow.distanceTo(victim) > 2.2) {
            if (this.widow.isOnFloor()) {
                this.widow.getNavigation().moveTo(victim, 1.3);
            } else {
                this.widow.getMoveControl().setWantedPosition(victim.getX(), victim.getY(), victim.getZ(), 1.3);
            }
        } else {
            this.widow.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
