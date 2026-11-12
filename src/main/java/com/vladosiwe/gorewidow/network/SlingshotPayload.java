package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.joml.Vector3f;

/** Rider -> server: the client-simulated mount fired a two-line slingshot from these anchors. */
public record SlingshotPayload(Vector3f a, Vector3f b) implements CustomPacketPayload {
    public static final Type<SlingshotPayload> TYPE = new Type<>(Gorewidow.id("slingshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SlingshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VECTOR3F, SlingshotPayload::a,
            ByteBufCodecs.VECTOR3F, SlingshotPayload::b,
            SlingshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
