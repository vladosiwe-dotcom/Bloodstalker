package com.vladosiwe.gorewidow.entity;

import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;

/**
 * Ground movement is vanilla. Off the floor (walls, ceilings, web lines) the wanted position is
 * turned into a world-space steering vector that {@link GorewidowEntity#travel} projects onto
 * the current surface or uses to pump a swing.
 */
public class GorewidowMoveControl extends MoveControl {
    private final GorewidowEntity widow;

    public GorewidowMoveControl(GorewidowEntity widow) {
        super(widow);
        this.widow = widow;
    }

    @Override
    public void tick() {
        Vec3 wish = Vec3.ZERO;
        if (this.operation == Operation.MOVE_TO) {
            Vec3 d = new Vec3(this.wantedX - this.mob.getX(), this.wantedY - this.mob.getY(), this.wantedZ - this.mob.getZ());
            if (d.lengthSqr() > 0.09) {
                wish = d.normalize().scale(Math.min(1.5, this.speedModifier));
            }
        }
        this.widow.setAiMoveDir(wish);
        if (!this.widow.isOnFloor() || this.widow.getWebMode().isRope()) {
            this.operation = Operation.WAIT;
            this.mob.setZza(0);
            this.mob.setXxa(0);
            return;
        }
        super.tick();
    }
}
