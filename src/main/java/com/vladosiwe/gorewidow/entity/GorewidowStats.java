package com.vladosiwe.gorewidow.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.Locale;

/**
 * Level-based stats. Every level is a point in one stat. Wild points are rolled on spawn (and as
 * the taming bonus); tamed points are spent by the owner as the creature levels up from kills.
 */
public final class GorewidowStats {
    public enum Stat {
        HEALTH(70, 0.10, true),
        STAMINA(150, 0.10, true),
        FOOD(100, 0.10, true),
        WEIGHT(300, 0.04, true),
        MELEE(8, 0.05, true),
        SPEED(0.34, 0.015, false);

        public final double base;
        /** Fraction of the base added per point. */
        public final double perPoint;
        /** Whether wild creatures can roll points in it (speed is tame-only). */
        public final boolean wildRollable;

        Stat(double base, double perPoint, boolean wildRollable) {
            this.base = base;
            this.perPoint = perPoint;
            this.wildRollable = wildRollable;
        }

        public String key() {
            return "stat.gorewidow." + this.name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Stat[] STATS = Stat.values();
    public static final int MAX_WILD_LEVEL = 30;
    public static final int MAX_TAMED_LEVELS = 75;

    private final int[] wild = new int[STATS.length];
    private final int[] tamed = new int[STATS.length];
    private int unspent;
    private float xp;

    public int level() {
        return 1 + Arrays.stream(this.wild).sum() + Arrays.stream(this.tamed).sum();
    }

    public int wildLevel() {
        return 1 + Arrays.stream(this.wild).sum();
    }

    public int tamedLevels() {
        return Arrays.stream(this.tamed).sum() + this.unspent;
    }

    public int wildPoints(Stat stat) {
        return this.wild[stat.ordinal()];
    }

    public int tamedPoints(Stat stat) {
        return this.tamed[stat.ordinal()];
    }

    public int unspent() {
        return this.unspent;
    }

    public float xp() {
        return this.xp;
    }

    public double value(Stat stat) {
        int points = this.wild[stat.ordinal()] + this.tamed[stat.ordinal()];
        return stat.base * (1.0 + stat.perPoint * points);
    }

    /** Experience needed to go from the current level to the next one. */
    public float xpToNext() {
        return 20.0F + this.level() * 6.0F;
    }

    public boolean canLevelUp() {
        return Arrays.stream(this.tamed).sum() + this.unspent < MAX_TAMED_LEVELS;
    }

    /** Adds experience; returns how many levels were gained. */
    public int addXp(float amount) {
        int gained = 0;
        this.xp += amount;
        while (this.canLevelUp() && this.xp >= this.xpToNext()) {
            this.xp -= this.xpToNext();
            this.unspent++;
            gained++;
        }
        if (!this.canLevelUp()) {
            this.xp = 0;
        }
        return gained;
    }

    public boolean spend(Stat stat) {
        if (this.unspent <= 0) {
            return false;
        }
        this.unspent--;
        this.tamed[stat.ordinal()]++;
        return true;
    }

    /** Rolls a wild level (weighted toward low levels) and spreads its points randomly. */
    public void rollWild(RandomSource random) {
        Arrays.fill(this.wild, 0);
        double r = random.nextDouble();
        int level = 1 + (int) Math.floor(Math.pow(r, 1.6) * MAX_WILD_LEVEL);
        this.addWildPoints(level - 1, random);
    }

    /** Spreads points over the stats a wild creature can roll (also used for the taming bonus). */
    public void addWildPoints(int points, RandomSource random) {
        for (int i = 0; i < points; i++) {
            Stat stat;
            do {
                stat = STATS[random.nextInt(STATS.length)];
            } while (!stat.wildRollable);
            this.wild[stat.ordinal()]++;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Wild", this.wild.clone());
        tag.putIntArray("Tamed", this.tamed.clone());
        tag.putInt("Unspent", this.unspent);
        tag.putFloat("Xp", this.xp);
        return tag;
    }

    public void load(CompoundTag tag) {
        copy(tag.getIntArray("Wild"), this.wild);
        copy(tag.getIntArray("Tamed"), this.tamed);
        this.unspent = tag.getInt("Unspent");
        this.xp = tag.getFloat("Xp");
    }

    private static void copy(int[] src, int[] dst) {
        Arrays.fill(dst, 0);
        System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
    }
}
