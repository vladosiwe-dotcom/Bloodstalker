package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Rider -> server: the client-simulated mount switched the surface it clings to. */
public record SurfaceSyncPayload(int entityId, byte surface) implements CustomPacketPayload {
    public static final Type<SurfaceSyncPayload> TYPE = new Type<>(Gorewidow.id("surface_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SurfaceSyncPayload::entityId,
            ByteBufCodecs.BYTE, SurfaceSyncPayload::surface,
            SurfaceSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
