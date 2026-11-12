package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The wild hunting routine: web up into the canopy or onto a cave ceiling, go still (optionally
 * dangling on a thread), watch prey pass below and mark it for the pounce.
 */
public class GorewidowAmbushGoal extends Goal {
    private enum Phase { ASCEND, PERCH, DANGLE }

    private static final int MAX_WAIT = 2400;
    private final GorewidowEntity widow;
    private Phase phase = Phase.ASCEND;
    private int ticks;
    private float dangleLength;

    public GorewidowAmbushGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.widow.isBaby()) {
            return false;
        }
        if (this.widow.isTame() || this.widow.getTarget() != null || this.widow.isVehicle() || this.widow.isLeashed()
                || this.widow.getWebMode() != WebMode.NONE || !this.widow.isOnFloor() || !this.widow.onGround()
                || this.widow.getWebCooldown() > 0 || this.widow.getRandom().nextInt(200) != 0) {
            return false;
        }
        BlockHitResult perch = WebTargeting.findAnchor(this.widow, new Vec3(0, 1, 0), 0.55, 4.0, 18.0, 3.5, 20);
        if (perch == null || perch.getDirection() != Direction.DOWN) {
            return false;
        }
        this.phase = Phase.ASCEND;
        this.ticks = 0;
        this.widow.shootWeb(perch.getLocation(), GorewidowEntity.WebPurpose.PERCH);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.widow.getTarget() != null || this.widow.isVehicle() || this.ticks > MAX_WAIT) {
            return false;
        }
        return switch (this.phase) {
            case ASCEND -> this.widow.getWebMode() != WebMode.NONE || !this.widow.isOnFloor();
            case PERCH -> !this.widow.isOnFloor();
            case DANGLE -> this.widow.getWebMode() == WebMode.HANG;
        };
    }

    @Override
    public void tick() {
        this.ticks++;
        switch (this.phase) {
            case ASCEND -> {
                if (this.widow.getWebMode().isRope()) {
                    this.widow.setAiGrabSurfaces(true);
                    this.widow.setAiReel(1.0F);
                }
                if (this.widow.getSurface() == Direction.UP) {
                    this.widow.setAiReel(0);
                    this.widow.setPerched(true);
                    if (this.widow.getRandom().nextBoolean()) {
                        // drop on a thread and dangle above the trail
                        this.dangleLength = 3.0F + this.widow.getRandom().nextFloat() * 3.0F;
                        Vec3 top = new Vec3(this.widow.getX(), this.widow.getBoundingBox().maxY, this.widow.getZ());
                        this.widow.startHang(top, Direction.DOWN, 1.0F);
                        this.widow.setPerched(true);
                        this.phase = Phase.DANGLE;
                    } else {
                        this.phase = Phase.PERCH;
                    }
                } else if (this.ticks > 160) {
                    this.ticks = MAX_WAIT + 1;
                }
            }
            case PERCH -> this.watchForPrey();
            case DANGLE -> {
                this.widow.setAiReel(this.widow.getRopeLength() < this.dangleLength ? -1.0F : 0.0F);
                this.watchForPrey();
            }
        }
    }

    private void watchForPrey() {
        LivingEntity prey = this.widow.level().getNearestEntity(LivingEntity.class,
                net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(20).selector(this::isPrey),
                this.widow, this.widow.getX(), this.widow.getY(), this.widow.getZ(), this.widow.getBoundingBox().inflate(20, 24, 20));
        if (prey == null) {
            return;
        }
        this.widow.getLookControl().setLookAt(prey, 20, 20);
        Vec3 d = prey.position().subtract(this.widow.position());
        if (d.y < -1.5 && d.horizontalDistance() < 9 && this.widow.hasLineOfSight(prey)) {
            this.widow.setTarget(prey);
        }
    }

    private boolean isPrey(LivingEntity entity) {
        if (entity instanceof GorewidowEntity) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && !(this.widow.isPacified());
        }
        return entity instanceof Animal && entity.getBbWidth() < 2.0F;
    }

    @Override
    public void stop() {
        this.widow.setAiReel(0);
        if (this.widow.getTarget() == null) {
            this.widow.setPerched(false);
            if (this.widow.getWebMode() != WebMode.NONE) {
                this.widow.releaseWeb(true);
            }
            this.widow.setSurface(Direction.DOWN);
        }
        this.widow.setWebCooldown(Math.max(this.widow.getWebCooldown(), 100));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
