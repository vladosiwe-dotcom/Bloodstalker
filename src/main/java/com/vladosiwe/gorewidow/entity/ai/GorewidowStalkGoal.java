package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowMind;
import com.vladosiwe.gorewidow.entity.WebMode;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * A wary wild Gorewidow shadows players: it creeps after them, crouched and silent, keeping behind
 * them. Catch it in your gaze and it freezes, then slinks off into cover where you cannot see it.
 * Turn your back while hurt and it strikes.
 */
public class GorewidowStalkGoal extends Goal {
    private final GorewidowEntity widow;
    @Nullable
    private Player prey;
    private int watchedTicks;
    @Nullable
    private Vec3 hideSpot;
    private int hideTicks;
    private int repath;

    public GorewidowStalkGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean wary(Player player) {
        float trust = this.widow.getMind().trust(player);
        return trust > GorewidowMind.HOSTILE && trust < GorewidowMind.CURIOUS;
    }

    @Override
    public boolean canUse() {
        if (this.widow.isTame() || this.widow.getTarget() != null || this.widow.isVehicle() || this.widow.isPerched()
                || this.widow.getWebMode() != WebMode.NONE || !this.widow.isOnFloor() || this.widow.isDraining()
                || this.widow.getTameFeeder() != null) {
            return false;
        }
        Player nearest = this.widow.level().getNearestPlayer(this.widow, 28);
        if (nearest == null || nearest.isCreative() || nearest.isSpectator() || !this.wary(nearest)) {
            return false;
        }
        this.prey = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.prey != null && this.prey.isAlive() && !this.prey.isSpectator() && this.widow.distanceTo(this.prey) < 36
                && this.widow.getTarget() == null && !this.widow.isTame() && this.wary(this.prey) && !this.widow.isDraining()
                && this.widow.getTameFeeder() == null && this.widow.getWebMode() == WebMode.NONE;
    }

    @Override
    public void start() {
        this.watchedTicks = 0;
        this.hideSpot = null;
        this.hideTicks = 0;
    }

    @Override
    public void stop() {
        this.prey = null;
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        Player player = this.prey;
        if (player == null) {
            return;
        }
        double dist = this.widow.distanceTo(player);
        if (this.hideTicks > 0) {
            this.hideTicks--;
            this.widow.setMood(GorewidowMind.Mood.HIDING);
            if (this.hideSpot != null && --this.repath <= 0) {
                this.repath = 10;
                this.widow.getNavigation().moveTo(this.hideSpot.x, this.hideSpot.y, this.hideSpot.z, 1.35);
            }
            if (this.hideSpot == null || this.widow.position().distanceTo(this.hideSpot) < 1.5) {
                this.hideTicks = Math.min(this.hideTicks, 30);
                this.widow.getLookControl().setLookAt(player, 20, 20);
            }
            return;
        }

        boolean watched = this.widow.getMind().isWatchedBy(player, 0.93);
        if (watched) {
            // caught: go perfectly still... then vanish
            this.watchedTicks++;
            this.widow.getNavigation().stop();
            this.widow.getLookControl().setLookAt(player, 10, 10);
            this.widow.setMood(GorewidowMind.Mood.HIDING);
            if (this.watchedTicks > 22) {
                this.hideSpot = this.findHideSpot(player);
                this.hideTicks = 90;
                this.watchedTicks = 0;
                this.repath = 0;
                this.widow.playSound(ModSounds.STEP.get(), 0.4F, 0.7F);
            }
            return;
        }
        this.watchedTicks = Math.max(0, this.watchedTicks - 2);
        this.widow.setMood(GorewidowMind.Mood.STALKING);
        this.widow.getLookControl().setLookAt(player, 15, 15);

        // creep up behind the player, keeping just out of easy reach
        if (dist > 9) {
            if (--this.repath <= 0) {
                this.repath = 15;
                Vec3 look = player.getViewVector(1.0F);
                Vec3 flat = new Vec3(look.x, 0, look.z);
                flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
                Vec3 behind = player.position().subtract(flat.scale(8.0));
                this.widow.getNavigation().moveTo(behind.x, behind.y, behind.z, 0.6);
            }
        } else {
            this.widow.getNavigation().stop();
        }

        // a predator first: strike the unwary when they are weak
        boolean vulnerable = player.getHealth() < player.getMaxHealth() * 0.4F || this.widow.getRandom().nextInt(90) == 0;
        if (dist < 8 && vulnerable && this.widow.level().getDifficulty() != Difficulty.PEACEFUL
                && this.widow.getMind().trust(player) < 0) {
            this.widow.setTarget(player);
            this.widow.setMood(GorewidowMind.Mood.HUNTING);
        }
    }

    /** A spot away from the player that the player cannot see. */
    @Nullable
    private Vec3 findHideSpot(Player player) {
        Vec3 fallback = null;
        for (int i = 0; i < 12; i++) {
            Vec3 pos = LandRandomPos.getPosAway(this.widow, 14, 6, player.position());
            if (pos == null) {
                continue;
            }
            if (fallback == null) {
                fallback = pos;
            }
            Vec3 eye = player.getEyePosition();
            HitResult hit = this.widow.level().clip(new ClipContext(eye, pos.add(0, 1.0, 0), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return pos;
            }
        }
        return fallback;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
