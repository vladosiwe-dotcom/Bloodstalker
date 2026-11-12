package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowMind;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * The tamed opposite of stalking: when its owner settles down it comes close, stays in plain sight,
 * faces them and shows affection. A neglected one keeps its distance and sulks. It greets an owner
 * who has been away.
 */
public class GorewidowBondGoal extends Goal {
    private final GorewidowEntity widow;
    private Player owner;
    private int ownerIdleTicks;
    private int affectionCooldown;
    private long lastSeenOwner;
    private Vec3 lastOwnerPos = Vec3.ZERO;

    public GorewidowBondGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!this.widow.isTame() || this.widow.isVehicle() || this.widow.getTarget() != null || this.widow.isOrderedToSit()
                || this.widow.getCommand() != GorewidowCommand.FOLLOW) {
            return false;
        }
        LivingEntity o = this.widow.getOwner();
        if (!(o instanceof Player player) || player.isSpectator() || player.isPassenger() || this.widow.distanceTo(player) > 10) {
            this.trackOwner(null);
            return false;
        }
        this.trackOwner(player);
        if (this.ownerIdleTicks < 40) {
            return false;
        }
        this.owner = player;
        return true;
    }

    /** Keeps tabs on the owner's comings and goings for the greeting and idleness checks. */
    private void trackOwner(Player player) {
        long now = this.widow.level().getGameTime();
        if (player == null) {
            this.ownerIdleTicks = 0;
            return;
        }
        if (now - this.lastSeenOwner > 1200 && this.lastSeenOwner != 0) {
            this.greet(player);
        }
        this.lastSeenOwner = now;
        Vec3 pos = player.position();
        this.ownerIdleTicks = pos.distanceToSqr(this.lastOwnerPos) < 0.01 ? this.ownerIdleTicks + 1 : 0;
        this.lastOwnerPos = pos;
    }

    private void greet(Player player) {
        if (this.widow.getMind().trust(player) < 0) {
            return;
        }
        this.widow.triggerAnim("action", "leap");
        this.widow.playSound(ModSounds.PURR.get(), 1.3F, 1.2F);
        this.hearts(6);
    }

    @Override
    public boolean canContinueToUse() {
        return this.owner != null && this.owner.isAlive() && this.widow.getTarget() == null && !this.widow.isVehicle()
                && this.widow.distanceTo(this.owner) < 12 && this.owner.position().distanceToSqr(this.lastOwnerPos) < 0.04
                && this.widow.getCommand() == GorewidowCommand.FOLLOW && !this.widow.isOrderedToSit();
    }

    @Override
    public void stop() {
        this.owner = null;
        this.ownerIdleTicks = 0;
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.owner == null) {
            return;
        }
        this.lastOwnerPos = this.owner.position();
        float bond = this.widow.getMind().trust(this.owner);
        double dist = this.widow.distanceTo(this.owner);
        if (bond < 0) {
            // sulking: keeps away, will not look at you
            this.widow.setMood(GorewidowMind.Mood.SULKING);
            if (dist < 5) {
                Vec3 away = this.widow.position().subtract(this.owner.position()).normalize().scale(4).add(this.widow.position());
                this.widow.getNavigation().moveTo(away.x, away.y, away.z, 0.8);
            }
            return;
        }
        this.widow.setMood(bond >= 60 ? GorewidowMind.Mood.PLAYFUL : GorewidowMind.Mood.BONDED);
        this.widow.getLookControl().setLookAt(this.owner, 30, 30);
        if (dist > 2.6) {
            this.widow.getNavigation().moveTo(this.owner, 0.8);
        } else {
            this.widow.getNavigation().stop();
            if (--this.affectionCooldown <= 0) {
                this.affectionCooldown = 160 + this.widow.getRandom().nextInt(200);
                this.widow.triggerAnim("action", "tame");
                this.widow.playSound(ModSounds.PURR.get(), 0.9F, 1.0F + this.widow.getRandom().nextFloat() * 0.2F);
                this.hearts(bond >= 60 ? 4 : 2);
            }
        }
    }

    private void hearts(int count) {
        if (this.widow.level() instanceof ServerLevel server) {
            Vec3 p = this.widow.getBodyCenter();
            server.sendParticles(ParticleTypes.HEART, p.x, p.y + 0.8, p.z, count, 0.6, 0.3, 0.6, 0.05);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
