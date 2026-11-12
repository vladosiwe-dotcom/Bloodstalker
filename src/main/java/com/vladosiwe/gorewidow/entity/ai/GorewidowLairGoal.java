package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The daily round of a Gorewidow born in a mountain lair: by day it goes back down the tunnel to its
 * chamber and rests on the ceiling; when night falls it climbs out and heads for open ground to hunt.
 */
public class GorewidowLairGoal extends Goal {
    private final GorewidowEntity widow;
    @Nullable
    private Vec3 destination;
    private boolean outing;
    private boolean wentOutTonight;
    private int ticks;
    private int repath;

    public GorewidowLairGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean free() {
        return this.widow.hasLair() && !this.widow.isTame() && this.widow.getTarget() == null && !this.widow.isVehicle()
                && this.widow.getWebMode() == WebMode.NONE && !this.widow.isDraining() && this.widow.getTameFeeder() == null;
    }

    @Override
    public boolean canUse() {
        if (!this.free()) {
            return false;
        }
        BlockPos home = this.widow.getLairHome();
        BlockPos entrance = this.widow.getLairEntrance();
        boolean night = this.widow.level().isNight();
        if (!night) {
            this.wentOutTonight = false;
            if (this.widow.distanceToSqr(Vec3.atBottomCenterOf(home)) > 7 * 7) {
                this.outing = false;
                this.destination = Vec3.atBottomCenterOf(home);
                return true;
            }
            return false;
        }
        if (!this.wentOutTonight && this.widow.distanceToSqr(Vec3.atBottomCenterOf(home)) < 24 * 24) {
            // pick a hunting ground outside the mouth of the cave
            double angle = this.widow.getRandom().nextDouble() * Math.PI * 2;
            double dist = 14 + this.widow.getRandom().nextDouble() * 24;
            int x = (int) Math.floor(entrance.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(entrance.getZ() + Math.sin(angle) * dist);
            int y = this.widow.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            this.destination = new Vec3(x + 0.5, y, z + 0.5);
            this.outing = true;
            this.wentOutTonight = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.free() || this.destination == null || this.ticks > 1200) {
            return false;
        }
        boolean night = this.widow.level().isNight();
        if (this.outing != night) {
            return false;
        }
        return this.widow.position().distanceToSqr(this.destination) > 3.0 * 3.0;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.repath = 0;
        this.widow.setPerched(false);
    }

    @Override
    public void stop() {
        this.destination = null;
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.destination == null) {
            return;
        }
        // go via the tunnel mouth: the den and the outside are only joined through it
        Vec3 waypoint = this.destination;
        BlockPos entrance = this.widow.getLairEntrance();
        if (entrance != null) {
            Vec3 mouth = Vec3.atBottomCenterOf(entrance);
            boolean inside = this.widow.position().distanceToSqr(Vec3.atBottomCenterOf(this.widow.getLairHome()))
                    < this.widow.position().distanceToSqr(mouth);
            boolean targetInside = !this.outing;
            if (inside != targetInside && this.widow.position().distanceToSqr(mouth) > 4.0 * 4.0) {
                waypoint = mouth;
            }
        }
        if (this.widow.isOnFloor()) {
            if (--this.repath <= 0) {
                this.repath = 20;
                this.widow.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, this.outing ? 1.0 : 1.15);
            }
        } else {
            this.widow.getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, 1.0);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
