package com.vladosiwe.gorewidow.entity;

/** What the Gorewidow's web line is currently doing. Synced to clients as a byte. */
public enum WebMode {
    /** No web line. */
    NONE,
    /** A web shot is in flight; the line runs to the projectile. */
    SHOT,
    /** Anchored to a block and swinging on it (pendulum physics, reel in / out). */
    SWING,
    /** Anchored above and dangling still, used for ambushes. Same physics as SWING with heavy damping. */
    HANG,
    /** Stuck to a living entity and pulling it in. */
    TETHER;

    private static final WebMode[] VALUES = values();

    public boolean isRope() {
        return this == SWING || this == HANG;
    }

    public boolean hasLine() {
        return this != NONE;
    }

    public static WebMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
