package com.vladosiwe.gorewidow.entity;

import net.minecraft.network.chat.Component;

/** Orders a tamed Gorewidow can be given by sneak-using it with an empty hand. */
public enum GorewidowCommand {
    FOLLOW,
    STAY,
    WANDER;

    private static final GorewidowCommand[] VALUES = values();

    public GorewidowCommand next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public Component label() {
        return Component.translatable("message.gorewidow.command." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public static GorewidowCommand byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : FOLLOW;
    }
}
