package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Rider -> server: an ability the rider triggered with a key binding. */
public record MountActionPayload(Action action) implements CustomPacketPayload {
    public enum Action {
        /** Shoot a web line along the crosshair, or let go of the current line. */
        WEB,
        /** Bite what the rider looks at; on a helpless target this starts draining. */
        BITE,
        /** The riding client already let go of its line (jump-release / landed on a surface). */
        RELEASE_WEB,
        /** Sprint key pressed / released while riding. */
        SPRINT_ON,
        SPRINT_OFF,
        /** Holding jump in the air: silk parachute. */
        GLIDE_ON,
        GLIDE_OFF,
        /** Spider vision toggle (night vision + threat outlines). */
        VISION_ON,
        VISION_OFF,
        /** Weapon stance (rear up, hold the rider forward and up so they can use weapons and items). */
        STANCE_ON,
        STANCE_OFF
    }

    public static final Type<MountActionPayload> TYPE = new Type<>(Gorewidow.id("mount_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MountActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            MountActionPayload::action,
            MountActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
