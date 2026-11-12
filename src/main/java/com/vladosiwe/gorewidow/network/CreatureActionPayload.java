package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Owner -> server: an option picked on the creature's radial wheel. */
public record CreatureActionPayload(int entityId, Action action) implements CustomPacketPayload {
    public enum Action {
        RIDE, INVENTORY, FOLLOW, STAY, WANDER, AGGRESSIVE, NEUTRAL, PASSIVE, PET, DROP_HARNESS
    }

    public static final Type<CreatureActionPayload> TYPE = new Type<>(Gorewidow.id("creature_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreatureActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CreatureActionPayload::entityId,
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            CreatureActionPayload::action,
            CreatureActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
