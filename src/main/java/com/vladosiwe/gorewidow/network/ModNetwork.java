package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(MountActionPayload.TYPE, MountActionPayload.STREAM_CODEC, ModNetwork::handleMountAction);
        registrar.playToServer(SurfaceSyncPayload.TYPE, SurfaceSyncPayload.STREAM_CODEC, ModNetwork::handleSurfaceSync);
    }

    private static void handleMountAction(MountActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.getVehicle() instanceof GorewidowEntity mount) || mount.getControllingPassenger() != player) {
            return;
        }
        switch (payload.action()) {
            case WEB -> mount.riderWeb(player);
            case BITE -> mount.riderBite(player);
            case RELEASE_WEB -> mount.releaseWeb(true);
        }
    }

    private static void handleSurfaceSync(SurfaceSyncPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.getVehicle() instanceof GorewidowEntity mount) || mount.getId() != payload.entityId()
                || mount.getControllingPassenger() != player) {
            return;
        }
        mount.acceptClientSurface(Direction.from3DDataValue(payload.surface()));
    }
}
