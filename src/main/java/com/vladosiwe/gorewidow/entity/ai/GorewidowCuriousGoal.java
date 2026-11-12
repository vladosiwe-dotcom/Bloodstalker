package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowMind;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/** A wild Gorewidow that has come to trust a player drops the act: it approaches openly and watches. */
public class GorewidowCuriousGoal extends Goal {
    private final GorewidowEntity widow;
    @Nullable
    private Player friend;

    public GorewidowCuriousGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.widow.isTame() || this.widow.getTarget() != null || this.widow.isVehicle() || !this.widow.isOnFloor()
                || this.widow.getTameFeeder() != null) {
            return false;
        }
        Player nearest = this.widow.level().getNearestPlayer(this.widow, 20);
        if (nearest == null || nearest.isSpectator() || this.widow.getMind().trust(nearest) < GorewidowMind.CURIOUS) {
            return false;
        }
        this.friend = nearest;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.friend != null && this.friend.isAlive() && this.widow.distanceTo(this.friend) < 24 && this.widow.getTarget() == null
                && !this.widow.isTame() && this.widow.getTameFeeder() == null
                && this.widow.getMind().trust(this.friend) >= GorewidowMind.CURIOUS;
    }

    @Override
    public void stop() {
        this.friend = null;
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.friend == null) {
            return;
        }
        this.widow.setMood(GorewidowMind.Mood.CURIOUS);
        this.widow.getLookControl().setLookAt(this.friend, 30, 30);
        if (this.widow.distanceTo(this.friend) > 5.0F) {
            this.widow.getNavigation().moveTo(this.friend, 0.75);
        } else {
            this.widow.getNavigation().stop();
            if (this.widow.getRandom().nextInt(160) == 0) {
                this.widow.playSound(ModSounds.AMBIENT.get(), 0.5F, 1.25F);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
