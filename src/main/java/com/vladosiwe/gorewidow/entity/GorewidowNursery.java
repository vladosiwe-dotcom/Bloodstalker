package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.item.GorewidowEggItem;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Breeding and raising, ARK-style:
 * <ul>
 *   <li>Each Gorewidow is male or female. A tamed female with mating enabled, near a tamed male that
 *   also has mating enabled, mates with him and lays a fertilized egg carrying the child's
 *   inherited stats and mutations.</li>
 *   <li>A hatchling grows from a tiny baby into an adult. While it grows it asks for care at
 *   intervals (a cuddle, a meal, a walk). Every request its imprinter answers in time raises the
 *   imprint, which boosts all stats (+20% at 100%) and makes it fight harder and protect its
 *   imprinter when ridden by them.</li>
 * </ul>
 */
public final class GorewidowNursery {
    /** Time for a hatchling to grow up (ticks). */
    public static final int MATURATION_TICKS = 48000;
    public static final int CARE_REQUESTS = 8;
    public static final int CARE_INTERVAL = MATURATION_TICKS / CARE_REQUESTS;
    public static final int MATING_TICKS = 600;
    public static final int MATING_COOLDOWN = 12000;
    public static final double WALK_DISTANCE = 30.0;

    public enum Care {
        NONE, CUDDLE, FEED, WALK;

        private static final Care[] VALUES = values();

        public Component label() {
            return Component.translatable("care.gorewidow." + this.name().toLowerCase(Locale.ROOT));
        }

        public static Care byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
        }
    }

    public enum FollowDistance {
        NEAR(4.0F, 8.0F), MEDIUM(8.0F, 16.0F), FAR(14.0F, 28.0F);

        public final float stop;
        public final float start;

        FollowDistance(float stop, float start) {
            this.stop = stop;
            this.start = start;
        }

        public FollowDistance next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        public Component label() {
            return Component.translatable("follow.gorewidow." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    private final GorewidowEntity widow;
    private int matingProgress;
    private int mateCooldown;
    private int careDeadline;
    private int nextCareIn = 1200;
    private double walked;
    private Vec3 lastPos = Vec3.ZERO;
    @Nullable
    private UUID imprinter;

    GorewidowNursery(GorewidowEntity widow) {
        this.widow = widow;
    }

    // ================================================================== persistence
    void save(CompoundTag tag) {
        tag.putInt("MateCooldown", this.mateCooldown);
        tag.putInt("NextCare", this.nextCareIn);
        tag.putInt("CareDeadline", this.careDeadline);
        tag.putDouble("Walked", this.walked);
        if (this.imprinter != null) {
            tag.putUUID("Imprinter", this.imprinter);
        }
    }

    void load(CompoundTag tag) {
        this.mateCooldown = tag.getInt("MateCooldown");
        this.nextCareIn = tag.contains("NextCare") ? tag.getInt("NextCare") : 1200;
        this.careDeadline = tag.getInt("CareDeadline");
        this.walked = tag.getDouble("Walked");
        this.imprinter = tag.hasUUID("Imprinter") ? tag.getUUID("Imprinter") : null;
    }

    @Nullable
    public UUID imprinter() {
        return this.imprinter;
    }

    public boolean isImprinter(@Nullable LivingEntity entity) {
        return entity != null && this.imprinter != null && this.imprinter.equals(entity.getUUID());
    }

    /** 0 for a fresh hatchling, 1 for an adult. */
    public float maturity() {
        int age = this.widow.getAge();
        return age >= 0 ? 1.0F : Math.max(0.0F, 1.0F - (-age) / (float) MATURATION_TICKS);
    }

    // ================================================================== tick
    void serverTick() {
        if (this.mateCooldown > 0) {
            this.mateCooldown--;
        }
        if (this.widow.isBaby()) {
            this.tickCare();
            if (this.widow.tickCount % 20 == 0) {
                this.widow.syncMaturity(this.maturity());
            }
            if (this.widow.tickCount % 100 == 0) {
                this.widow.refreshDimensions();
            }
        } else {
            this.tickMating();
        }
    }

    // ================================================================== mating
    private void tickMating() {
        if (!this.widow.isTame() || !this.widow.isFemale() || !this.widow.isMatingEnabled() || this.mateCooldown > 0
                || this.widow.isVehicle()) {
            this.matingProgress = 0;
            return;
        }
        GorewidowEntity partner = null;
        double best = 64;
        for (GorewidowEntity other : this.widow.level().getEntitiesOfClass(GorewidowEntity.class, this.widow.getBoundingBox().inflate(8),
                o -> o != this.widow && o.isAlive() && o.isTame() && !o.isBaby() && !o.isFemale() && o.isMatingEnabled() && !o.isVehicle())) {
            double d = other.distanceToSqr(this.widow);
            if (d < best) {
                best = d;
                partner = other;
            }
        }
        if (partner == null) {
            this.matingProgress = Math.max(0, this.matingProgress - 2);
            return;
        }
        this.widow.getLookControl().setLookAt(partner, 30, 30);
        partner.getLookControl().setLookAt(this.widow, 30, 30);
        if (best > 4 * 4) {
            this.widow.getNavigation().moveTo(partner, 0.8);
        }
        if (++this.matingProgress % 20 == 0 && this.widow.level() instanceof ServerLevel server) {
            Vec3 p = this.widow.getBodyCenter().lerp(partner.getBodyCenter(), 0.5);
            server.sendParticles(ParticleTypes.HEART, p.x, p.y + 0.8, p.z, 2, 0.6, 0.3, 0.6, 0.05);
        }
        if (this.matingProgress >= MATING_TICKS) {
            this.matingProgress = 0;
            this.mateCooldown = MATING_COOLDOWN;
            ItemStack egg = GorewidowEggItem.create(this.widow, partner);
            this.widow.spawnAtLocation(egg);
            this.widow.playSound(ModSounds.PURR.get(), 1.2F, 0.8F);
            if (this.widow.getOwner() instanceof Player owner) {
                owner.displayClientMessage(Component.translatable("message.gorewidow.egg_laid", this.widow.getDisplayName()), false);
            }
        }
    }

    public int mateCooldown() {
        return this.mateCooldown;
    }

    public float matingProgress() {
        return this.matingProgress / (float) MATING_TICKS;
    }

    // ================================================================== babies and imprinting
    /** Called on a freshly hatched baby. */
    public void setupNewborn() {
        this.widow.setAge(-MATURATION_TICKS);
        this.nextCareIn = 1200;
        this.careDeadline = 0;
        this.imprinter = null;
        this.widow.syncMaturity(0);
        this.widow.setCareRequest(Care.NONE);
    }

    private void tickCare() {
        Care care = this.widow.getCareRequest();
        if (care == Care.NONE) {
            if (--this.nextCareIn <= 0 && this.widow.getStats().imprint() < 0.999F) {
                Care[] options = {Care.CUDDLE, Care.FEED, Care.WALK};
                this.widow.setCareRequest(options[this.widow.getRandom().nextInt(options.length)]);
                this.careDeadline = CARE_INTERVAL;
                this.walked = 0;
                this.lastPos = this.widow.position();
                this.widow.playSound(ModSounds.AMBIENT.get(), 0.8F, 1.6F);
                if (this.widow.getOwner() instanceof Player owner && owner.distanceTo(this.widow) < 48) {
                    owner.displayClientMessage(Component.translatable("message.gorewidow.care_request", this.widow.getDisplayName(),
                            this.widow.getCareRequest().label()), false);
                }
            }
            return;
        }
        if (--this.careDeadline <= 0) {
            // missed this one
            this.widow.setCareRequest(Care.NONE);
            this.nextCareIn = 1;
            return;
        }
        if (care == Care.WALK) {
            Player walker = this.carer();
            Vec3 pos = this.widow.position();
            if (walker != null && walker.distanceTo(this.widow) < 8) {
                this.walked += Math.sqrt(pos.subtract(this.lastPos).horizontalDistanceSqr());
                if (this.walked >= WALK_DISTANCE) {
                    this.fulfil(walker);
                }
            }
            this.lastPos = pos;
        }
    }

    @Nullable
    private Player carer() {
        if (this.imprinter != null) {
            return this.widow.level().getPlayerByUUID(this.imprinter);
        }
        return this.widow.getOwner() instanceof Player owner ? owner : null;
    }

    /** Test hook: raise a specific care request right away. */
    public void forceNextRequest(Care care) {
        this.widow.setCareRequest(care);
        this.careDeadline = CARE_INTERVAL;
        this.walked = 0;
        this.lastPos = this.widow.position();
    }

    public double walkedFraction() {
        return Math.min(1.0, this.walked / WALK_DISTANCE);
    }

    public int careTicksLeft() {
        return this.careDeadline;
    }

    /** The player cuddles the baby (option wheel: pet). */
    public void onCuddle(Player player) {
        if (this.widow.getCareRequest() == Care.CUDDLE) {
            this.fulfil(player);
        }
    }

    /** The player hand-feeds the baby. */
    public void onFed(Player player) {
        if (this.widow.getCareRequest() == Care.FEED) {
            this.fulfil(player);
        }
    }

    private void fulfil(Player player) {
        if (this.imprinter != null && !this.imprinter.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.gorewidow.not_imprinter"), true);
            return;
        }
        this.imprinter = player.getUUID();
        GorewidowStats stats = this.widow.getStats();
        stats.setImprint(stats.imprint() + 1.0F / CARE_REQUESTS);
        this.widow.getCare().applyStats(false);
        this.widow.setCareRequest(Care.NONE);
        this.nextCareIn = Math.max(200, this.careDeadline);
        this.careDeadline = 0;
        this.widow.getMind().addTrust(player, 8.0F);
        this.widow.playSound(ModSounds.PURR.get(), 1.0F, 1.4F);
        if (this.widow.level() instanceof ServerLevel server) {
            Vec3 p = this.widow.getBodyCenter();
            server.sendParticles(ParticleTypes.HEART, p.x, p.y + 0.5, p.z, 6, 0.4, 0.3, 0.4, 0.05);
        }
        player.displayClientMessage(Component.translatable("message.gorewidow.imprinted",
                Math.round(stats.imprint() * 100)), true);
    }

    void onGrownUp() {
        this.widow.setCareRequest(Care.NONE);
        this.widow.syncMaturity(1.0F);
        this.widow.refreshDimensions();
        this.widow.getCare().applyStats(false);
        if (this.widow.getOwner() instanceof Player owner) {
            owner.displayClientMessage(Component.translatable("message.gorewidow.grown_up", this.widow.getDisplayName(),
                    Math.round(this.widow.getStats().imprint() * 100)), false);
        }
    }

    /** Damage multiplier for fights fought with its imprinter on its back. */
    public float imprintBonus() {
        return 0.3F * this.widow.getStats().imprint();
    }
}
