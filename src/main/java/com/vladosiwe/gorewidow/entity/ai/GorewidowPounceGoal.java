package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Leaps onto the target in a ballistic arc: from the ground at mid range, or by dropping out of an
 * ambush on a ceiling, wall or dangling line.
 */
public class GorewidowPounceGoal extends Goal {
    private static final double G = 0.08;
    private final GorewidowEntity widow;
    private int cooldown;
    private int airTicks;
    private boolean struck;

    public GorewidowPounceGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        if (this.widow.isBaby() || this.widow.hasOpeningPull()) {
            return false;
        }
        LivingEntity target = this.widow.getTarget();
        if (target == null || !target.isAlive() || this.widow.isVehicle() || this.widow.isDraining()) {
            return false;
        }
        Vec3 d = target.position().subtract(this.widow.position());
        double horizontal = d.horizontalDistance();
        boolean fromAbove = !this.widow.isOnFloor() || this.widow.getWebMode() == WebMode.HANG;
        if (fromAbove) {
            return d.y < -1.5 && d.y > -18 && horizontal < 10 && this.widow.hasLineOfSight(target);
        }
        return this.widow.onGround() && this.widow.getWebMode() == WebMode.NONE && horizontal > 3.5 && horizontal < 10
                && Math.abs(d.y) < 4 && this.widow.hasLineOfSight(target) && this.widow.getRandom().nextInt(8) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.airTicks < 40 && !(this.airTicks > 3 && this.widow.onGround()) && !this.struck;
    }

    @Override
    public void start() {
        LivingEntity target = this.widow.getTarget();
        if (target == null) {
            return;
        }
        if (this.widow.getWebMode() != WebMode.NONE) {
            this.widow.releaseWeb(false);
        }
        this.widow.setSurface(Direction.DOWN);
        this.widow.setPerched(false);
        this.widow.getNavigation().stop();

        Vec3 from = this.widow.position();
        Vec3 to = target.position().add(target.getDeltaMovement().scale(6));
        Vec3 d = to.subtract(from);
        double horizontal = d.horizontalDistance();
        int t = Mth.clamp((int) (horizontal / 0.85) + 4, 6, 20);
        double vx = d.x / t;
        double vz = d.z / t;
        double vy = (d.y + 0.5 * G * t * t) / t;
        Vec3 v = new Vec3(vx, Mth.clamp(vy, -1.0, 1.25), vz);
        this.widow.setDeltaMovement(v);
        this.widow.hasImpulse = true;
        this.widow.setPounceStrike(true);
        this.widow.triggerAnim("action", "screech");
        this.widow.playSound(ModSounds.SCREECH.get(), 1.6F, 0.9F + this.widow.getRandom().nextFloat() * 0.2F);
        this.airTicks = 0;
        this.struck = false;
    }

    @Override
    public void tick() {
        this.airTicks++;
        LivingEntity target = this.widow.getTarget();
        if (target == null) {
            return;
        }
        this.widow.getLookControl().setLookAt(target, 60, 60);
        if (!this.struck && this.widow.getBoundingBox().inflate(0.6).intersects(target.getBoundingBox())) {
            this.struck = true;
            this.widow.doHurtTarget(target);
        }
    }

    @Override
    public void stop() {
        this.cooldown = 50 + this.widow.getRandom().nextInt(50);
        this.widow.setPounceStrike(false);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
