package com.vladosiwe.gorewidow.entity;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** How a tamed Gorewidow picks fights. */
public enum AggressionMode {
    /** Hunts any hostile or wild creature that comes close (never players or allies). */
    AGGRESSIVE,
    /** Defends itself and its owner. */
    NEUTRAL,
    /** Never starts or answers a fight. */
    PASSIVE;

    private static final AggressionMode[] VALUES = values();

    public Component label() {
        return Component.translatable("message.gorewidow.aggression." + this.name().toLowerCase(Locale.ROOT));
    }

    public static AggressionMode byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NEUTRAL;
    }
}
