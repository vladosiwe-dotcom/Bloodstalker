package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.menu.GorewidowMenu;
import com.vladosiwe.gorewidow.registry.ModDamageTypes;
import com.vladosiwe.gorewidow.registry.ModItems;
import com.vladosiwe.gorewidow.registry.ModParticles;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * The "living animal" systems of a Gorewidow, ARK-style:
 * <ul>
 *   <li>passive taming: the creature latches onto a crouching player and drinks Blood Vials from
 *   their inventory, or the player's own blood when they run out; damage lowers taming
 *   effectiveness, which decides the bonus levels gained on taming;</li>
 *   <li>food that drains over time and is eaten automatically from the creature's inventory;</li>
 *   <li>stamina spent by sprinting, climbing, leaping and web shots while ridden;</li>
 *   <li>experience from kills, level-ups and stat points spent by the owner.</li>
 * </ul>
 */
public final class GorewidowCare {
    public static final int FEED_INTERVAL = 60;
    private static final int GIVE_UP_TICKS = 600;
    private static final float VIAL_FOOD = 35.0F;
    private static final float MEAT_FOOD = 15.0F;

    private final GorewidowEntity widow;
    private int feedTimer;
    private int pauseTicks;
    private int staminaRegenDelay;
    private Vec3 lastPos = Vec3.ZERO;

    GorewidowCare(GorewidowEntity widow) {
        this.widow = widow;
    }

    // ================================================================== persistence
    void save(CompoundTag tag) {
        tag.putFloat("Food", this.widow.getFood());
        tag.putFloat("StaminaValue", this.widow.getStamina());
        tag.putInt("TameEffectiveness", Math.round(this.widow.getTamingEffectiveness() * 10));
        tag.putByte("Aggression", (byte) this.widow.getAggression().ordinal());
    }

    void load(CompoundTag tag) {
        if (tag.contains("Food")) {
            this.widow.setFoodRaw(tag.getFloat("Food"));
            this.widow.setStaminaRaw(tag.getFloat("StaminaValue"));
            this.widow.setTamingEffectiveness(tag.getInt("TameEffectiveness"));
            this.widow.setAggression(AggressionMode.byId(tag.getByte("Aggression")));
        }
    }

    /** Recomputes attributes from the stats. {@code refill} tops health, food and stamina up. */
    public void applyStats(boolean refill) {
        GorewidowStats stats = this.widow.stats;
        float oldMax = this.widow.getMaxHealth();
        setBase(this.widow.getAttribute(Attributes.MAX_HEALTH), stats.value(GorewidowStats.Stat.HEALTH));
        setBase(this.widow.getAttribute(Attributes.ATTACK_DAMAGE), stats.value(GorewidowStats.Stat.MELEE));
        setBase(this.widow.getAttribute(Attributes.MOVEMENT_SPEED), stats.value(GorewidowStats.Stat.SPEED));
        float newMax = this.widow.getMaxHealth();
        if (refill) {
            this.widow.setHealth(newMax);
            this.widow.setFoodRaw(this.widow.getMaxFood());
            this.widow.setStaminaRaw(this.widow.getMaxStamina());
        } else {
            if (newMax > oldMax && this.widow.isAlive()) {
                this.widow.setHealth(this.widow.getHealth() + (newMax - oldMax));
            }
            this.widow.setHealth(Math.min(this.widow.getHealth(), newMax));
            this.widow.setFoodRaw(Math.min(this.widow.getFood(), this.widow.getMaxFood()));
            this.widow.setStaminaRaw(Math.min(this.widow.getStamina(), this.widow.getMaxStamina()));
        }
        this.widow.syncStats();
    }

    private static void setBase(@Nullable AttributeInstance attribute, double value) {
        if (attribute != null && Math.abs(attribute.getBaseValue() - value) > 1.0E-6) {
            attribute.setBaseValue(value);
        }
    }

    // ================================================================== tick
    void serverTick() {
        this.tickTaming();
        this.tickStamina();
        this.tickFood();
    }

    // ================================================================== taming
    /** Blood Vials needed for a full tame at this creature's wild level. */
    public static int requiredVials(int wildLevel) {
        return 4 + wildLevel / 5;
    }

    public int requiredVials() {
        return requiredVials(this.widow.stats.wildLevel());
    }

    /** Bonus levels the creature gets on taming at the current effectiveness. */
    public static int bonusLevels(int wildLevel, float effectivenessPercent) {
        return (int) Math.floor(wildLevel * 0.5 * effectivenessPercent / 100.0);
    }

    /** A player offers to let the wild Gorewidow feed on them (sneak + use). */
    public void offerToFeed(Player player) {
        if (this.widow.isTame()) {
            return;
        }
        if (this.widow.tamerId != null && !this.widow.tamerId.equals(player.getUUID()) && this.widow.getTameFeeder() != null) {
            player.displayClientMessage(Component.translatable("message.gorewidow.busy"), true);
            return;
        }
        if (findVial(player.getInventory()) < 0) {
            player.displayClientMessage(Component.translatable("message.gorewidow.needs_vials"), true);
            return;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.translatable("message.gorewidow.sneak_to_feed"), true);
            return;
        }
        boolean fresh = this.widow.tamerId == null || !this.widow.tamerId.equals(player.getUUID());
        if (fresh && this.widow.getTamingProgress() == 0) {
            this.widow.setTamingEffectiveness(1000);
        }
        this.widow.tamerId = player.getUUID();
        this.widow.pacifiedTicks = GIVE_UP_TICKS;
        this.pauseTicks = 0;
        this.feedTimer = FEED_INTERVAL / 2;
        if (this.widow.getTarget() == player) {
            this.widow.setTarget(null);
        }
        this.widow.stopDrain();
        if (this.widow.getWebMode() == WebMode.TETHER && this.widow.getLinkedEntity() == player) {
            this.widow.releaseWeb(true);
        }
        this.widow.playSound(ModSounds.PURR.get(), 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.gorewidow.latched"), true);
    }

    private void tickTaming() {
        if (this.widow.isTame()) {
            this.widow.setTameFeeder(null);
            this.widow.tamerId = null;
            return;
        }
        if (this.widow.tamerId == null) {
            this.widow.setTameFeeder(null);
            if (this.widow.getTamingProgress() > 0 && this.widow.tickCount % 40 == 0) {
                this.widow.setTamingProgress(this.widow.getTamingProgress() - 5);
            }
            return;
        }
        Player tamer = this.widow.level().getPlayerByUUID(this.widow.tamerId);
        boolean feeding = tamer != null && tamer.isAlive() && !tamer.isSpectator() && tamer.isShiftKeyDown()
                && this.widow.distanceTo(tamer) < 4.0F && !this.widow.isDraining();
        this.widow.setTameFeeder(feeding ? tamer : null);
        if (feeding) {
            this.pauseTicks = 0;
            this.widow.pacifiedTicks = GIVE_UP_TICKS;
            this.widow.getNavigation().stop();
            this.widow.getLookControl().setLookAt(tamer, 60, 60);
            if (this.widow.getTarget() == tamer) {
                this.widow.setTarget(null);
            }
            if (++this.feedTimer >= FEED_INTERVAL) {
                this.feedTimer = 0;
                this.feedOnce(tamer);
            }
        } else if (++this.pauseTicks > GIVE_UP_TICKS) {
            // left alone too long: it loses interest
            this.widow.tamerId = null;
            this.widow.pacifiedTicks = 0;
        } else {
            this.widow.pacifiedTicks = GIVE_UP_TICKS - this.pauseTicks;
        }
    }

    private void feedOnce(Player tamer) {
        int perVial = Mth.ceil(1000.0F / this.requiredVials());
        int gain;
        int slot = findVial(tamer.getInventory());
        if (slot >= 0) {
            if (!tamer.getAbilities().instabuild) {
                tamer.getInventory().getItem(slot).shrink(1);
                giveOrDrop(tamer, new ItemStack(Items.GLASS_BOTTLE));
            }
            gain = perVial;
        } else {
            // out of vials: it drinks the player instead, which it likes far less
            tamer.hurt(ModDamageTypes.bloodDrain(this.widow), 3.0F);
            gain = perVial / 2;
            this.widow.setTamingEffectiveness(this.widow.getEntityData().get(GorewidowEntity.DATA_TAME_EFFECTIVENESS) - 30);
            tamer.displayClientMessage(Component.translatable("message.gorewidow.drinking_you"), true);
        }
        this.widow.triggerAnim("action", "bite");
        this.widow.playSound(ModSounds.DRAIN.get(), 1.0F, 1.1F);
        if (this.widow.level() instanceof ServerLevel server) {
            Vec3 m = this.widow.getMouthPos();
            server.sendParticles(ModParticles.BLOOD_DROP.get(), m.x, m.y, m.z, 6, 0.15, 0.1, 0.15, 0.05);
        }
        int progress = this.widow.getTamingProgress() + gain;
        this.widow.setTamingProgress(progress);
        if (progress >= 1000) {
            this.completeTaming(tamer);
        }
    }

    private void completeTaming(Player tamer) {
        int wildLevel = this.widow.stats.wildLevel();
        int bonus = bonusLevels(wildLevel, this.widow.getTamingEffectiveness());
        this.widow.stats.addWildPoints(bonus, this.widow.getRandom());
        this.widow.tame(tamer);
        this.widow.tamerId = null;
        this.widow.pacifiedTicks = 0;
        this.widow.setTameFeeder(null);
        this.widow.setTarget(null);
        this.widow.getNavigation().stop();
        this.widow.setCommand(GorewidowCommand.FOLLOW);
        this.widow.setAggression(AggressionMode.NEUTRAL);
        this.widow.setPersistenceRequired();
        this.applyStats(true);
        this.widow.triggerAnim("action", "tame");
        this.widow.playSound(ModSounds.PURR.get(), 1.5F, 1.0F);
        this.widow.level().broadcastEntityEvent(this.widow, (byte) 7);
        tamer.displayClientMessage(Component.translatable("message.gorewidow.tamed_level", this.widow.getLevel(), bonus), false);
    }

    void onHurt(@Nullable Entity attacker, float amount) {
        if (this.widow.isTame() || this.widow.tamerId == null) {
            return;
        }
        if (attacker instanceof Player player && player.getUUID().equals(this.widow.tamerId)) {
            // betrayal while being tamed
            this.widow.tamerId = null;
            this.widow.pacifiedTicks = 0;
            this.widow.setTameFeeder(null);
            this.widow.setTamingProgress(this.widow.getTamingProgress() - 300);
            this.widow.playSound(ModSounds.SCREECH.get(), 1.2F, 0.8F);
            this.widow.setTarget(player);
            return;
        }
        int eff = this.widow.getEntityData().get(GorewidowEntity.DATA_TAME_EFFECTIVENESS);
        this.widow.setTamingEffectiveness(Math.max(100, eff - 40 - Math.round(amount * 2)));
    }

    // ================================================================== food
    private void tickFood() {
        if (!this.widow.isTame()) {
            this.widow.setFoodRaw(this.widow.getMaxFood());
            return;
        }
        if (this.widow.tickCount % 20 == 0) {
            float drain = 0.05F + (this.widow.isRiderSprinting() ? 0.08F : 0.0F);
            this.widow.setFoodRaw(Math.max(0, this.widow.getFood() - drain));
            this.autoEat();
        }
        if (this.widow.getFood() <= 0 && this.widow.tickCount % 40 == 0) {
            this.widow.hurt(this.widow.damageSources().starve(), 1.0F);
        } else if (this.widow.getFood() > this.widow.getMaxFood() * 0.5F && this.widow.tickCount % 100 == 0
                && this.widow.getHealth() < this.widow.getMaxHealth()) {
            this.widow.heal(1.0F);
        }
    }

    private void autoEat() {
        float missing = this.widow.getMaxFood() - this.widow.getFood();
        GorewidowInventory inv = this.widow.inventory;
        for (int i = 1; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.BLOOD_VIAL.get()) && missing >= VIAL_FOOD) {
                stack.shrink(1);
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!inv.canAddItem(bottle) || !inv.addItem(bottle).isEmpty()) {
                    this.widow.spawnAtLocation(bottle);
                }
                this.eat(VIAL_FOOD, 4.0F);
                return;
            }
        }
        for (int i = 1; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Tags.Items.FOODS_RAW_MEAT) && missing >= MEAT_FOOD) {
                stack.shrink(1);
                inv.setChanged();
                this.eat(MEAT_FOOD, 2.0F);
                return;
            }
        }
    }

    private void eat(float food, float heal) {
        this.widow.setFoodRaw(Math.min(this.widow.getMaxFood(), this.widow.getFood() + food));
        this.widow.heal(heal);
        this.widow.playSound(ModSounds.DRAIN.get(), 0.6F, 1.3F);
        this.widow.inventory.setChanged();
    }

    public void setFood(float food) {
        this.widow.setFoodRaw(Mth.clamp(food, 0, this.widow.getMaxFood()));
    }

    /** The owner feeds it by hand: heals and fills its belly. */
    public void feedFromHand(Player player, ItemStack stack) {
        boolean vial = stack.is(ModItems.BLOOD_VIAL.get());
        this.widow.setFoodRaw(Math.min(this.widow.getMaxFood(), this.widow.getFood() + (vial ? VIAL_FOOD : MEAT_FOOD)));
        this.widow.heal(vial ? 16.0F : 6.0F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (vial) {
                giveOrDrop(player, new ItemStack(Items.GLASS_BOTTLE));
            }
        }
        this.widow.playSound(ModSounds.PURR.get(), 1.0F, 1.0F);
        this.widow.level().broadcastEntityEvent(this.widow, (byte) 7);
    }

    // ================================================================== stamina
    /** Spends stamina; returns false (and spends nothing) if there is not enough. */
    public boolean useStamina(float amount) {
        if (this.widow.getStamina() < amount) {
            return false;
        }
        this.widow.setStaminaRaw(this.widow.getStamina() - amount);
        this.staminaRegenDelay = 30;
        return true;
    }

    private void tickStamina() {
        Vec3 pos = this.widow.position();
        double moved = pos.distanceTo(this.lastPos);
        this.lastPos = pos;
        if (this.widow.getControllingPassenger() instanceof Player) {
            float cost = 0;
            if (this.widow.isRiderSprinting() && moved > 0.15) {
                cost += 0.6F;
            }
            if (!this.widow.isOnFloor() && moved > 0.03) {
                cost += 0.15F;
            }
            if (cost > 0) {
                this.widow.setStaminaRaw(Math.max(0, this.widow.getStamina() - cost));
                this.staminaRegenDelay = 20;
                return;
            }
        } else {
            this.widow.setRiderSprinting(false);
        }
        if (this.staminaRegenDelay > 0) {
            this.staminaRegenDelay--;
        } else if (this.widow.getStamina() < this.widow.getMaxStamina()) {
            this.widow.setStaminaRaw(Math.min(this.widow.getMaxStamina(), this.widow.getStamina() + 0.6F));
        }
    }

    // ================================================================== experience
    /** Experience for a kill or other achievement; levels up a tamed creature. */
    public void grantXp(float amount) {
        if (!this.widow.isTame() || amount <= 0) {
            return;
        }
        int gained = this.widow.stats.addXp(amount);
        this.widow.syncStats();
        if (gained > 0) {
            this.widow.heal(this.widow.getMaxHealth() * 0.1F);
            this.widow.playSound(ModSounds.PURR.get(), 1.2F, 1.3F);
            if (this.widow.level() instanceof ServerLevel server) {
                Vec3 p = this.widow.getBodyCenter();
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y + 0.6, p.z, 20, 0.8, 0.5, 0.8, 0.1);
            }
            if (this.widow.getOwner() instanceof Player owner) {
                owner.displayClientMessage(Component.translatable("message.gorewidow.level_up", this.widow.getDisplayName(),
                        this.widow.getLevel() + this.widow.stats.unspent()), false);
            }
        }
    }

    /** Spends one unspent level on a stat (owner action from the inventory screen). */
    public boolean spendPoint(GorewidowStats.Stat stat) {
        if (!this.widow.stats.spend(stat)) {
            return false;
        }
        this.applyStats(false);
        return true;
    }

    // ================================================================== menus
    public void openInventory(Player player) {
        if (!this.widow.isTame() || !this.widow.isOwnedBy(player)) {
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new GorewidowMenu(id, inv, this.widow), this.widow.getDisplayName()),
                buf -> buf.writeVarInt(this.widow.getId()));
    }

    // ================================================================== helpers
    static int findVial(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(ModItems.BLOOD_VIAL.get())) {
                return i;
            }
        }
        return -1;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
