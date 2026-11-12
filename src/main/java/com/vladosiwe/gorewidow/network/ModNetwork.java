package com.vladosiwe.gorewidow.network;

import com.vladosiwe.gorewidow.entity.AggressionMode;
import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowInventory;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(MountActionPayload.TYPE, MountActionPayload.STREAM_CODEC, ModNetwork::handleMountAction);
        registrar.playToServer(SurfaceSyncPayload.TYPE, SurfaceSyncPayload.STREAM_CODEC, ModNetwork::handleSurfaceSync);
        registrar.playToServer(CreatureActionPayload.TYPE, CreatureActionPayload.STREAM_CODEC, ModNetwork::handleCreatureAction);
        registrar.playToServer(RenamePayload.TYPE, RenamePayload.STREAM_CODEC, ModNetwork::handleRename);
        registrar.playToServer(WhistlePayload.TYPE, WhistlePayload.STREAM_CODEC, ModNetwork::handleWhistle);
        registrar.playToServer(SlingshotPayload.TYPE, SlingshotPayload.STREAM_CODEC, ModNetwork::handleSlingshot);
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
            case SPRINT_ON -> mount.setRiderSprinting(true);
            case SPRINT_OFF -> mount.setRiderSprinting(false);
            case GLIDE_ON -> mount.setRiderGliding(true);
            case GLIDE_OFF -> mount.setRiderGliding(false);
            case VISION_ON -> mount.setRiderVision(true);
            case VISION_OFF -> mount.setRiderVision(false);
        }
    }

    private static void handleSlingshot(SlingshotPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof GorewidowEntity mount && mount.getControllingPassenger() == player) {
            mount.acceptSlingshot(new net.minecraft.world.phys.Vec3(payload.a()), new net.minecraft.world.phys.Vec3(payload.b()));
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

    /** Resolves an owned Gorewidow the player can reach (or rides). */
    private static GorewidowEntity owned(Player player, int entityId, double range) {
        Entity entity = player.level().getEntity(entityId);
        if (entity instanceof GorewidowEntity widow && widow.isTame() && widow.isOwnedBy(player) && widow.isAlive()
                && (widow.hasPassenger(player) || widow.distanceTo(player) <= range)) {
            return widow;
        }
        return null;
    }

    private static void handleCreatureAction(CreatureActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        GorewidowEntity widow = owned(player, payload.entityId(), 8.0);
        if (widow == null) {
            return;
        }
        boolean obeys = widow.getMind().obeys(player);
        if (!obeys && payload.action() != CreatureActionPayload.Action.PET && payload.action() != CreatureActionPayload.Action.INVENTORY) {
            player.displayClientMessage(Component.translatable("message.gorewidow.ignores_you"), true);
            return;
        }
        switch (payload.action()) {
            case RIDE -> widow.tryMount(player);
            case INVENTORY -> widow.getCare().openInventory(player);
            case FOLLOW -> order(player, widow, GorewidowCommand.FOLLOW);
            case STAY -> order(player, widow, GorewidowCommand.STAY);
            case WANDER -> order(player, widow, GorewidowCommand.WANDER);
            case AGGRESSIVE -> aggression(player, widow, AggressionMode.AGGRESSIVE);
            case NEUTRAL -> aggression(player, widow, AggressionMode.NEUTRAL);
            case PASSIVE -> aggression(player, widow, AggressionMode.PASSIVE);
            case PET -> {
                widow.getMind().onPet(player);
                widow.playSound(ModSounds.PURR.get(), 1.0F, 0.9F + widow.getRandom().nextFloat() * 0.2F);
                widow.triggerAnim("action", "tame");
                widow.level().broadcastEntityEvent(widow, (byte) 7);
            }
            case DROP_HARNESS -> {
                ItemStack harness = widow.getInventory().removeItemNoUpdate(GorewidowInventory.HARNESS_SLOT);
                widow.getInventory().setChanged();
                if (!harness.isEmpty() && !player.getInventory().add(harness)) {
                    player.drop(harness, false);
                }
            }
        }
    }

    private static void order(Player player, GorewidowEntity widow, GorewidowCommand command) {
        widow.setCommand(command);
        widow.getNavigation().stop();
        widow.setTarget(null);
        player.displayClientMessage(Component.translatable("message.gorewidow.command", command.label()), true);
    }

    private static void aggression(Player player, GorewidowEntity widow, AggressionMode mode) {
        widow.setAggression(mode);
        player.displayClientMessage(Component.translatable("message.gorewidow.aggression", mode.label()), true);
    }

    private static void handleRename(RenamePayload payload, IPayloadContext context) {
        Player player = context.player();
        GorewidowEntity widow = owned(player, payload.entityId(), 8.0);
        if (widow == null) {
            return;
        }
        String name = payload.name().strip();
        if (name.length() > RenamePayload.MAX_LENGTH) {
            name = name.substring(0, RenamePayload.MAX_LENGTH);
        }
        widow.setCustomName(name.isEmpty() ? null : Component.literal(name));
    }

    private static void handleWhistle(WhistlePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity looked = level.getEntity(payload.targetId());
        List<GorewidowEntity> pack;
        if (payload.order() != WhistlePayload.Order.ATTACK && looked instanceof GorewidowEntity single && single.isOwnedBy(player)) {
            pack = List.of(single);
        } else {
            pack = level.getEntitiesOfClass(GorewidowEntity.class, player.getBoundingBox().inflate(WhistlePayload.RANGE),
                    w -> w.isTame() && w.isOwnedBy(player) && !w.isVehicle());
        }
        int count = 0;
        for (GorewidowEntity widow : pack) {
            if (!widow.getMind().obeys(player)) {
                continue;
            }
            switch (payload.order()) {
                case FOLLOW -> {
                    widow.setCommand(GorewidowCommand.FOLLOW);
                    widow.setTarget(null);
                }
                case STAY -> {
                    widow.setCommand(GorewidowCommand.STAY);
                    widow.getNavigation().stop();
                    widow.setTarget(null);
                }
                case ATTACK -> {
                    if (!(looked instanceof LivingEntity target) || target == widow || !target.isAlive() || widow.isAlliedTo(target)
                            || target == player || !widow.canAttack(target)) {
                        continue;
                    }
                    if (widow.getCommand() == GorewidowCommand.STAY) {
                        widow.setCommand(GorewidowCommand.FOLLOW);
                    }
                    widow.setTarget(target);
                }
            }
            count++;
        }
        player.displayClientMessage(Component.translatable("message.gorewidow.whistle." + payload.order().name().toLowerCase(java.util.Locale.ROOT), count), true);
    }
}
