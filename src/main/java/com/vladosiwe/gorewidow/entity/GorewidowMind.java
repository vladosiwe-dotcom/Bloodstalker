package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a Gorewidow thinks of each player, and the mood that drives its behaviour.
 *
 * <p>Trust runs from -100 (hatred) to 100 (devotion). A wild Gorewidow starts wary: it stalks,
 * freezes when stared at and slinks into cover. Trust is earned by leaving bottled blood on the
 * ground and backing off, by patiently crouching nearby without staring, and later by feeding and
 * petting; it is lost by hitting it, charging at it, staring it down or killing its kin. A
 * Gorewidow only lets a player it trusts feed it to the point of taming. Once tamed, trust becomes
 * its bond with the owner: affectionate and open when high, sulky and disobedient when neglected.
 */
public final class GorewidowMind {
    public static final float HOSTILE = -30.0F;
    public static final float CURIOUS = 20.0F;
    /** Trust a wild Gorewidow needs before it will latch on and feed from a player. */
    public static final float TAMING_TRUST = 40.0F;
    /** Patient sitting alone only earns this much; the rest must be earned with offerings. */
    private static final float PATIENCE_CAP = 25.0F;

    public enum Mood {
        /** Wild and wary: creeping after players from cover. */
        STALKING,
        /** Caught being watched: frozen still, then gone into hiding. */
        HIDING,
        /** Trusts someone enough to come closer and watch openly. */
        CURIOUS,
        /** Hunting prey or answering a threat. */
        HUNTING,
        /** Tamed and close to its owner. */
        BONDED,
        /** Tamed, feeling good and showing it. */
        PLAYFUL,
        /** Tamed but neglected or mistreated. */
        SULKING;

        private static final Mood[] VALUES = values();

        public Component label() {
            return Component.translatable("mood.gorewidow." + this.name().toLowerCase(Locale.ROOT));
        }

        public static Mood byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : STALKING;
        }
    }

    private final GorewidowEntity widow;
    private final Map<UUID, Float> trust = new HashMap<>();
    private boolean dirty;
    private int petCooldown;

    GorewidowMind(GorewidowEntity widow) {
        this.widow = widow;
    }

    // ================================================================== trust
    public float trust(@Nullable Entity player) {
        return player == null ? 0 : this.trust.getOrDefault(player.getUUID(), 0.0F);
    }

    public float trust(UUID id) {
        return this.trust.getOrDefault(id, 0.0F);
    }

    public void addTrust(Player player, float amount) {
        this.addTrust(player.getUUID(), amount);
    }

    public void addTrust(UUID id, float amount) {
        float before = this.trust(id);
        float after = Mth.clamp(before + amount, -100.0F, 100.0F);
        if (after != before) {
            this.trust.put(id, after);
            this.dirty = true;
        }
    }

    /** Patience can only lift trust up to a point; it never carries a Gorewidow into taming by itself. */
    private void addPatience(Player player, float amount) {
        float current = this.trust(player);
        if (current < PATIENCE_CAP) {
            this.addTrust(player, Math.min(amount, PATIENCE_CAP - current));
        }
    }

    public static Component trustLabel(float trust) {
        String key = trust <= HOSTILE ? "hostile" : trust < CURIOUS ? "wary" : trust < TAMING_TRUST ? "curious" : trust < 75 ? "trusting" : "devoted";
        return Component.translatable("trust.gorewidow." + key);
    }

    // ================================================================== events
    void onHurt(@Nullable Entity attacker) {
        if (attacker instanceof Player player) {
            this.addTrust(player, this.widow.isTame() ? -25.0F : -35.0F);
        }
    }

    /** A player killed one of its kind nearby. */
    void onKinKilled(Player killer) {
        this.addTrust(killer, -50.0F);
    }

    /** Ate bottled blood that a player left on the ground. */
    public void onOfferingEaten(@Nullable UUID thrower) {
        if (thrower != null) {
            this.addTrust(thrower, this.widow.isTame() ? 3.0F : 12.0F);
        }
        this.widow.playSound(ModSounds.PURR.get(), 1.0F, 1.1F);
        this.hearts(3);
    }

    public void onFedByHand(Player player) {
        this.addTrust(player, this.widow.isTame() ? 3.0F : 6.0F);
    }

    /** Petting from the option wheel; once a minute it means something. */
    public void onPet(Player player) {
        if (this.petCooldown <= 0) {
            this.addTrust(player, 4.0F);
            this.petCooldown = 1200;
        }
        this.hearts(5);
    }

    private void hearts(int count) {
        if (this.widow.level() instanceof ServerLevel server) {
            Vec3 p = this.widow.getBodyCenter();
            server.sendParticles(ParticleTypes.HEART, p.x, p.y + 0.8, p.z, count, 0.6, 0.3, 0.6, 0.05);
        }
    }

    // ================================================================== tick
    void serverTick() {
        if (this.petCooldown > 0) {
            this.petCooldown--;
        }
        if (this.widow.tickCount % 5 == 0) {
            this.observePlayers();
        }
        if (this.widow.isTame()) {
            this.tickBond();
        }
        if (this.dirty && this.widow.tickCount % 10 == 0) {
            this.dirty = false;
            this.widow.syncTrust(this.save());
        }
    }

    /** How the players around it behave shapes what it thinks of them (every 5 ticks). */
    private void observePlayers() {
        for (Player player : this.widow.level().getEntitiesOfClass(Player.class, this.widow.getBoundingBox().inflate(16),
                p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
            if (this.widow.isTame() && !this.widow.isOwnedBy(player)) {
                continue;
            }
            float dist = player.distanceTo(this.widow);
            boolean staring = this.isWatchedBy(player, 0.95);
            Vec3 toward = this.widow.position().subtract(player.position()).normalize();
            double approach = player.getDeltaMovement().dot(toward);
            if (!this.widow.isTame()) {
                if (player.isSprinting() && approach > 0.15 && dist < 12) {
                    this.addTrust(player, -0.6F);               // charging at it
                } else if (staring && !player.isShiftKeyDown() && dist < 10) {
                    this.addTrust(player, -0.15F);              // being stared down
                } else if (player.isShiftKeyDown() && dist < 12 && !staring && this.widow.getTarget() != player) {
                    this.addPatience(player, 0.12F);            // quiet, patient company
                }
            } else if (this.widow.isOwnedBy(player) && dist < 8 && player.isShiftKeyDown()) {
                this.addTrust(player, 0.01F);                   // quiet time with its owner
            }
        }
    }

    private void tickBond() {
        if (this.widow.tickCount % 200 != 0) {
            return;
        }
        Entity owner = this.widow.getOwner();
        if (owner == null) {
            return;
        }
        // neglect wears the bond down; good care builds it slowly
        boolean starving = this.widow.getFood() < this.widow.getMaxFood() * 0.15F;
        boolean wounded = this.widow.getHealth() < this.widow.getMaxHealth() * 0.3F;
        if (starving) {
            this.addTrust(owner.getUUID(), -1.5F);
        } else if (!wounded && this.widow.getFood() > this.widow.getMaxFood() * 0.6F) {
            this.addTrust(owner.getUUID(), 0.3F);
        }
    }

    /** True if the player is looking more or less straight at it with a clear line of sight. */
    public boolean isWatchedBy(Player player, double minDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = this.widow.getBodyCenter().subtract(eye);
        double len = to.length();
        if (len < 1.0E-3 || len > 48) {
            return false;
        }
        return player.getViewVector(1.0F).dot(to.scale(1.0 / len)) > minDot && player.hasLineOfSight(this.widow);
    }

    /** Whether a tamed Gorewidow is willing to follow orders from its owner right now. */
    public boolean obeys(Player owner) {
        float bond = this.trust(owner);
        return bond >= 0 || this.widow.getRandom().nextFloat() < 0.5F + bond / 200.0F;
    }

    /** A freshly tamed Gorewidow starts out trusting the one who tamed it. */
    void onTamed(Player owner) {
        this.trust.put(owner.getUUID(), Math.max(this.trust(owner), 60.0F));
        this.dirty = true;
    }

    // ================================================================== persistence
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        this.trust.forEach((id, value) -> tag.putFloat(id.toString(), value));
        return tag;
    }

    public void load(CompoundTag tag) {
        this.trust.clear();
        for (String key : tag.getAllKeys()) {
            try {
                this.trust.put(UUID.fromString(key), tag.getFloat(key));
            } catch (IllegalArgumentException ignored) {
                // not a uuid key
            }
        }
    }
}
