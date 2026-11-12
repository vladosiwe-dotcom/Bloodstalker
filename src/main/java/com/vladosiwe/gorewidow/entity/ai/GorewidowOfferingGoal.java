package com.vladosiwe.gorewidow.entity.ai;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowMind;
import com.vladosiwe.gorewidow.registry.ModTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Bottled blood left on the ground is irresistible. A wild Gorewidow only comes for it when nobody is
 * standing over it (crouching quietly nearby is fine), and drinking it warms it to whoever left it.
 * A hungry tamed one drinks offerings too.
 */
public class GorewidowOfferingGoal extends Goal {
    private final GorewidowEntity widow;
    @Nullable
    private ItemEntity offering;
    private int timeout;
    private int scanCooldown;

    public GorewidowOfferingGoal(GorewidowEntity widow) {
        this.widow = widow;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // canUse runs on alternating ticks depending on the entity id, so throttle with a counter
        if (--this.scanCooldown > 0) {
            return false;
        }
        this.scanCooldown = 5;
        if (this.widow.getTarget() != null || this.widow.isVehicle() || this.widow.isDraining()
                || this.widow.getTameFeeder() != null || this.widow.isInSittingPose()) {
            return false;
        }
        if (this.widow.isTame() && this.widow.getFood() > this.widow.getMaxFood() - 20) {
            return false;
        }
        List<ItemEntity> items = this.widow.level().getEntitiesOfClass(ItemEntity.class, this.widow.getBoundingBox().inflate(14),
                e -> e.isAlive() && e.getItem().is(ModTags.BLOOD_FOOD) && e.onGround() && e.getAge() > 20);
        for (ItemEntity item : items) {
            if (this.safeToApproach(item)) {
                this.offering = item;
                this.timeout = 200;
                return true;
            }
        }
        return false;
    }

    private boolean safeToApproach(ItemEntity item) {
        if (this.widow.isTame()) {
            return true;
        }
        Entity thrower = item.getOwner();
        if (thrower instanceof Player p && this.widow.getMind().trust(p) <= GorewidowMind.HOSTILE) {
            return false;
        }
        for (Player player : this.widow.level().getEntitiesOfClass(Player.class, item.getBoundingBox().inflate(5),
                p -> p.isAlive() && !p.isSpectator() && p.distanceTo(item) < 5.0F)) {
            if (!player.isShiftKeyDown() || this.widow.getMind().isWatchedBy(player, 0.97)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return this.offering != null && this.offering.isAlive() && --this.timeout > 0 && this.widow.getTarget() == null
                && (this.widow.isTame() || this.safeToApproach(this.offering));
    }

    @Override
    public void stop() {
        this.offering = null;
        this.widow.getNavigation().stop();
    }

    @Override
    public void tick() {
        ItemEntity item = this.offering;
        if (item == null) {
            return;
        }
        this.widow.getLookControl().setLookAt(item, 30, 30);
        if (this.widow.distanceTo(item) > 2.0F) {
            if (this.widow.isOnFloor()) {
                this.widow.getNavigation().moveTo(item.getX(), item.getY(), item.getZ(), this.widow.isTame() ? 1.0 : 0.7);
            } else {
                this.widow.getMoveControl().setWantedPosition(item.getX(), item.getY(), item.getZ(), 0.8);
            }
            return;
        }
        ItemStack stack = item.getItem();
        UUID thrower = item.getOwner() != null ? item.getOwner().getUUID() : null;
        stack.shrink(1);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
        }
        this.widow.spawnAtLocation(new ItemStack(Items.GLASS_BOTTLE));
        this.widow.triggerAnim("action", "bite");
        this.widow.getCare().setFood(this.widow.getFood() + 35.0F);
        this.widow.heal(4.0F);
        this.widow.getMind().onOfferingEaten(thrower);
        this.offering = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
