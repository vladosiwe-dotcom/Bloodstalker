package com.vladosiwe.gorewidow.client;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/** Client-only registry of procedural rigs, keyed weakly by entity so unloaded creatures are dropped. */
public final class GorewidowRigs {
    private static final Map<GorewidowEntity, GorewidowRig> RIGS = new WeakHashMap<>();

    private GorewidowRigs() {}

    public static GorewidowRig get(GorewidowEntity widow) {
        return RIGS.computeIfAbsent(widow, w -> new GorewidowRig());
    }

    public static GorewidowRig peek(GorewidowEntity widow) {
        return RIGS.get(widow);
    }

    static void tickAll() {
        for (GorewidowEntity widow : new ArrayList<>(RIGS.keySet())) {
            if (widow.isRemoved()) {
                RIGS.remove(widow);
            } else if (GorewidowRig.shouldTick(widow)) {
                RIGS.get(widow).tick(widow);
            }
        }
    }

    static void clear() {
        RIGS.clear();
    }
}
