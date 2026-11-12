package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Owner -> server: a whistle order for every owned Gorewidow in earshot (or just the one being
 * looked at). {@code targetId} is the creature looked at for ATTACK, or the Gorewidow singled out.
 */
public record WhistlePayload(Order order, int targetId, net.minecraft.core.BlockPos pos) implements CustomPacketPayload {
    /** MOVE: go to {@code pos} (the block looked at); CALL: come and pick the owner up. */
    public enum Order { FOLLOW, STAY, ATTACK, MOVE, CALL }

    public static final double RANGE = 40.0;
    public static final Type<WhistlePayload> TYPE = new Type<>(Gorewidow.id("whistle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WhistlePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Order.values()[Math.floorMod(i, Order.values().length)], Order::ordinal),
            WhistlePayload::order,
            ByteBufCodecs.VAR_INT, WhistlePayload::targetId,
            net.minecraft.core.BlockPos.STREAM_CODEC, WhistlePayload::pos,
            WhistlePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
