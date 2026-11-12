package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Orders whistled from outside the saddle:
 * <ul>
 *   <li><b>Move</b>: go to the spot the owner pointed at. It walks there when it can, climbs walls and
 *   ceilings on the way, and if it gets stuck below a ledge it fires a line at the spot and reels
 *   itself up. Once there it waits (stay).</li>
 *   <li><b>Call</b>: come to the owner and take them up into the riding hold.</li>
 * </ul>
 */
public class GorewidowOrderGoal extends Goal {
    private static final int GIVE_UP_TICKS = 900;
    private final GorewidowEntity widow;
    private int ticks;
    private int stuckTicks;
    private int repath;
    private Vec3 lastPos = Vec3.ZERO;

    public GorewidowOrderGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!this.widow.isTame() || this.widow.isVehicle() || this.widow.isDeadOrDying()) {
            return false;
        }
        return this.widow.getMoveOrder() != null || this.widow.getCallOrder() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse() && this.ticks < GIVE_UP_TICKS;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.stuckTicks = 0;
        this.repath = 0;
        this.lastPos = this.widow.position();
        this.widow.setTarget(null);
        this.widow.stopDrain();
        this.widow.playSound(ModSounds.AMBIENT.get(), 0.7F, 1.3F);
    }

    @Override
    public void stop() {
        this.widow.getNavigation().stop();
        if (this.ticks >= GIVE_UP_TICKS) {
            this.widow.clearOrders();
        }
        this.widow.setAiReel(0.0F);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        this.ticks++;
        Player caller = this.widow.getCallOrder();
        BlockPos spot = this.widow.getMoveOrder();
        Vec3 goal;
        double arriveAt;
        if (caller != null) {
            if (!caller.isAlive() || caller.isPassenger() || caller.distanceTo(this.widow) > 96) {
                this.widow.clearOrders();
                return;
            }
            goal = caller.position();
            arriveAt = 3.2;
            this.widow.getLookControl().setLookAt(caller, 30, 30);
        } else if (spot != null) {
            goal = Vec3.atCenterOf(spot);
            arriveAt = 2.2;
            this.widow.getLookControl().setLookAt(goal.x, goal.y, goal.z, 30, 30);
        } else {
            return;
        }

        Vec3 body = this.widow.getBodyCenter();
        double distance = body.distanceTo(goal);
        if (distance < arriveAt || (caller == null && this.widow.getBoundingBox().inflate(1.2).contains(goal))) {
            this.arrive(caller);
            return;
        }

        // on a line already: reel toward the spot and let go when close
        if (this.widow.getWebMode().isRope()) {
            this.widow.setAiReel(1.0F);
            if (this.widow.getAnchor().distanceTo(body) < 3.0 || this.ticks % 200 == 0) {
                this.widow.releaseWeb(false);
            }
            return;
        }
        this.widow.setAiReel(0.0F);

        // the climbing navigation walks there when it can and otherwise steers straight at the spot;
        // walls and ceilings on the way are climbed by the surface movement itself
        if (--this.repath <= 0) {
            this.repath = 20;
            this.widow.getNavigation().moveTo(goal.x, goal.y, goal.z, 1.15);
        }
        if (this.widow.getNavigation().isDone()) {
            this.widow.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.15);
        }

        // stuck under a ledge or across a gap: fire a line at the spot and reel up to it
        Vec3 pos = this.widow.position();
        this.stuckTicks = pos.distanceToSqr(this.lastPos) < 0.01 ? this.stuckTicks + 1 : Math.max(0, this.stuckTicks - 2);
        this.lastPos = pos;
        boolean far = goal.y - pos.y > 3.5 || pos.subtract(goal).horizontalDistance() > 16;
        if ((this.stuckTicks > 30 || far && this.ticks % 60 == 30) && this.widow.getWebMode() == WebMode.NONE
                && this.widow.getWebCooldown() <= 0 && body.distanceTo(goal) < GorewidowEntity.WEB_RANGE && !this.widow.isBaby()) {
            this.widow.shootWeb(goal, GorewidowEntity.WebPurpose.TRAVERSE);
            this.stuckTicks = 0;
        }
    }

    private void arrive(Player caller) {
        this.widow.getNavigation().stop();
        this.widow.clearOrders();
        if (caller != null) {
            this.widow.tryMount(caller);
        } else {
            this.widow.setCommand(GorewidowCommand.STAY);
            this.widow.playSound(ModSounds.PURR.get(), 0.6F, 1.2F);
        }
    }
}
