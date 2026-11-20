package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Rider -> server: how far the power jump is charged (0..100). The rider's client runs the charge
 * (input is local); the server keeps it in synced entity data so every other player sees the
 * creature crouch and gather itself, and uses it to price and animate the launch.
 */
public record JumpChargePayload(int charge) implements CustomPacketPayload {
    public static final Type<JumpChargePayload> TYPE = new Type<>(Gorewidow.id("jump_charge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JumpChargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JumpChargePayload::charge,
            JumpChargePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
