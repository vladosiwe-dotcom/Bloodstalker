package com.vladosiwe.gorewidow.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.network.MountActionPayload;
import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModParticles;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mod(value = Gorewidow.MOD_ID, dist = Dist.CLIENT)
public final class GorewidowClient {
    private static final String CATEGORY = "key.categories.gorewidow";
    public static final KeyMapping KEY_WEB = new KeyMapping("key.gorewidow.web", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping KEY_BITE = new KeyMapping("key.gorewidow.bite", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping KEY_REEL_IN = new KeyMapping("key.gorewidow.reel_in", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
    public static final KeyMapping KEY_REEL_OUT = new KeyMapping("key.gorewidow.reel_out", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
    public static final KeyMapping KEY_WHISTLE_FOLLOW = new KeyMapping("key.gorewidow.whistle_follow", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping KEY_WHISTLE_STAY = new KeyMapping("key.gorewidow.whistle_stay", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping KEY_WHISTLE_ATTACK = new KeyMapping("key.gorewidow.whistle_attack", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    public static final KeyMapping KEY_SLINGSHOT = new KeyMapping("key.gorewidow.slingshot", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);
    public static final KeyMapping KEY_VISION = new KeyMapping("key.gorewidow.vision", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
    public static final KeyMapping KEY_STANCE = new KeyMapping("key.gorewidow.stance", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    private static boolean lastSprint;
    private static boolean manualStance;
    private static boolean lastStance;
    private static boolean lastGlide;
    private static boolean spiderVision = true;
    private static final Set<net.minecraft.world.entity.Entity> OUTLINED = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Players whose render pose we pushed in RenderPlayerEvent.Pre and must pop in Post. */
    private static final Set<Player> POSED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static GorewidowEntity lastMount;

    public GorewidowClient(IEventBus modBus) {
        modBus.addListener(GorewidowClient::registerRenderers);
        modBus.addListener(GorewidowClient::registerParticles);
        modBus.addListener(GorewidowClient::registerKeys);
        modBus.addListener(GorewidowClient::registerScreens);
        modBus.addListener(GorewidowClient::registerGuiLayers);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onInteractionKey);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onRenderPlayerPre);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onRenderPlayerPost);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onCameraDistance);
        NeoForge.EVENT_BUS.addListener(GorewidowClient::onLogout);
        if (Boolean.getBoolean("gorewidow.showcase")) {
            com.vladosiwe.gorewidow.client.dev.DevShowcase.enable();
        }
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GOREWIDOW.get(), GorewidowRenderer::new);
        event.registerEntityRenderer(ModEntities.WEB_SHOT.get(), WebShotRenderer::new);
        event.registerEntityRenderer(ModEntities.EGG.get(), GorewidowEggRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WEB_FIBER.get(), ModParticleProviders.WebFiberProvider::new);
        event.registerSpriteSet(ModParticles.BLOOD_DROP.get(), ModParticleProviders.BloodDropProvider::new);
        event.registerSpriteSet(ModParticles.BLOOD_STREAM.get(), ModParticleProviders.BloodStreamProvider::new);
        event.registerSpriteSet(ModParticles.IMPACT.get(), ModParticleProviders.ImpactProvider::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_WEB);
        event.register(KEY_BITE);
        event.register(KEY_REEL_IN);
        event.register(KEY_REEL_OUT);
        event.register(KEY_WHISTLE_FOLLOW);
        event.register(KEY_WHISTLE_STAY);
        event.register(KEY_WHISTLE_ATTACK);
        event.register(KEY_SLINGSHOT);
        event.register(KEY_VISION);
        event.register(KEY_STANCE);
    }

    /** Ranged weapons raise the rider into the weapon stance on their own. */
    private static boolean isRangedWeapon(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        net.minecraft.world.item.UseAnim anim = stack.getUseAnimation();
        return stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem
                || anim == net.minecraft.world.item.UseAnim.BOW || anim == net.minecraft.world.item.UseAnim.CROSSBOW
                || anim == net.minecraft.world.item.UseAnim.SPEAR || stack.is(com.vladosiwe.gorewidow.registry.ModTags.MOUNTED_WEAPONS);
    }

    private static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.vladosiwe.gorewidow.registry.ModMenus.GOREWIDOW.get(), com.vladosiwe.gorewidow.client.gui.GorewidowInventoryScreen::new);
    }

    private static void registerGuiLayers(net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) {
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CROSSHAIR, Gorewidow.id("creature_info"),
                com.vladosiwe.gorewidow.client.gui.GorewidowHud::render);
    }

    /** Sneak + use on an owned Gorewidow opens the option wheel instead of interacting. */
    private static void onInteractionKey(net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!event.isUseItem() || player == null || !player.isShiftKeyDown() || player.isPassenger()) {
            return;
        }
        if (mc.crosshairPickEntity instanceof GorewidowEntity widow && widow.isTame() && widow.isOwnedBy(player)) {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(event.getHand());
            if (held.is(net.minecraft.world.item.Items.SHEARS)) {
                return;
            }
            event.setCanceled(true);
            event.setSwingHand(false);
            mc.setScreen(new com.vladosiwe.gorewidow.client.gui.RadialMenuScreen(widow));
        }
    }

    /** Whatever living thing is under the crosshair within range (blocks occlude). */
    private static net.minecraft.world.entity.Entity pick(Minecraft mc, double range, java.util.function.Predicate<net.minecraft.world.entity.Entity> filter) {
        net.minecraft.world.entity.Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 look = camera.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(range));
        net.minecraft.world.phys.HitResult block = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, camera));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            end = block.getLocation();
        }
        net.minecraft.world.phys.AABB box = camera.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                camera, eye, end, box, e -> e != camera && !e.isSpectator() && e.isPickable() && filter.test(e), eye.distanceToSqr(end));
        return hit != null ? hit.getEntity() : null;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GorewidowRigs.tickAll();
        if (mc.player != null) {
            net.minecraft.world.entity.Entity looked = mc.player.isPassenger() ? null : pick(mc, 16.0, e -> e instanceof GorewidowEntity);
            com.vladosiwe.gorewidow.client.gui.GorewidowHud.setLookedAt(looked instanceof GorewidowEntity w ? w : null);
            while (KEY_WHISTLE_FOLLOW.consumeClick()) {
                whistle(mc, com.vladosiwe.gorewidow.network.WhistlePayload.Order.FOLLOW);
            }
            while (KEY_WHISTLE_STAY.consumeClick()) {
                whistle(mc, com.vladosiwe.gorewidow.network.WhistlePayload.Order.STAY);
            }
            while (KEY_WHISTLE_ATTACK.consumeClick()) {
                whistle(mc, com.vladosiwe.gorewidow.network.WhistlePayload.Order.ATTACK);
            }
        }

        LocalPlayer player = mc.player;
        if (player != null && player.getVehicle() instanceof GorewidowEntity mount && mount.getControllingPassenger() == player) {
            if (mount != lastMount) {
                lastMount = mount;
                player.displayClientMessage(Component.translatable("message.gorewidow.controls",
                        KEY_WEB.getTranslatedKeyMessage(), KEY_BITE.getTranslatedKeyMessage(),
                        KEY_REEL_IN.getTranslatedKeyMessage(), KEY_REEL_OUT.getTranslatedKeyMessage(),
                        mc.options.keyJump.getTranslatedKeyMessage(), KEY_SLINGSHOT.getTranslatedKeyMessage(), KEY_VISION.getTranslatedKeyMessage(), KEY_STANCE.getTranslatedKeyMessage()), true);
            }
            while (KEY_WEB.consumeClick()) {
                if (!mount.isWeaponStance()) {
                    PacketDistributor.sendToServer(new MountActionPayload(MountActionPayload.Action.WEB));
                }
            }
            while (KEY_BITE.consumeClick()) {
                PacketDistributor.sendToServer(new MountActionPayload(MountActionPayload.Action.BITE));
            }
            mount.setRiderReel(KEY_REEL_IN.isDown() ? 1.0F : KEY_REEL_OUT.isDown() ? -1.0F : 0.0F);
            while (KEY_STANCE.consumeClick()) {
                manualStance = !manualStance;
            }
            boolean stance = manualStance || isRangedWeapon(player.getMainHandItem()) || isRangedWeapon(player.getOffhandItem());
            if (stance != lastStance) {
                lastStance = stance;
                mount.setWeaponStance(stance);
                PacketDistributor.sendToServer(new MountActionPayload(stance ? MountActionPayload.Action.STANCE_ON : MountActionPayload.Action.STANCE_OFF));
            }
            while (KEY_SLINGSHOT.consumeClick()) {
                Vec3[] shot = mount.launchSlingshot(player.getLookAngle());
                if (shot != null) {
                    PacketDistributor.sendToServer(new com.vladosiwe.gorewidow.network.SlingshotPayload(shot[0].toVector3f(), shot[1].toVector3f()));
                } else {
                    player.playSound(net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, 0.4F, 1.6F);
                }
            }
            while (KEY_VISION.consumeClick()) {
                spiderVision = !spiderVision;
                mount.setRiderVision(spiderVision);
                PacketDistributor.sendToServer(new MountActionPayload(spiderVision ? MountActionPayload.Action.VISION_ON : MountActionPayload.Action.VISION_OFF));
                player.displayClientMessage(Component.translatable(spiderVision ? "message.gorewidow.vision_on" : "message.gorewidow.vision_off"), true);
            }
            boolean glide = mc.options.keyJump.isDown() && !mount.onGround() && mount.isOnFloor() && !mount.getWebMode().isRope()
                    && mount.getStamina() > 1.0F && mount.getDeltaMovement().y < 0;
            mount.setRiderGliding(glide);
            if (glide != lastGlide) {
                lastGlide = glide;
                PacketDistributor.sendToServer(new MountActionPayload(glide ? MountActionPayload.Action.GLIDE_ON : MountActionPayload.Action.GLIDE_OFF));
            }
            updateThreatOutlines(mc, player, spiderVision);
            boolean sprint = mc.options.keySprint.isDown() && mount.getStamina() > 1.0F;
            mount.setRiderSprinting(sprint);
            if (sprint != lastSprint) {
                lastSprint = sprint;
                PacketDistributor.sendToServer(new MountActionPayload(sprint ? MountActionPayload.Action.SPRINT_ON : MountActionPayload.Action.SPRINT_OFF));
            }
        } else {
            lastSprint = false;
            lastGlide = false;
            lastStance = false;
            manualStance = false;
            while (KEY_STANCE.consumeClick()) { /* not riding */ }
            if (!OUTLINED.isEmpty() && player != null) {
                updateThreatOutlines(mc, player, false);
            }
            while (KEY_SLINGSHOT.consumeClick()) { /* not riding */ }
            while (KEY_VISION.consumeClick()) { /* not riding */ }
            lastMount = null;
            while (KEY_WEB.consumeClick()) { /* not riding: discard */ }
            while (KEY_BITE.consumeClick()) { /* not riding: discard */ }
        }
    }

    /**
     * Straps the rider face-down under the Gorewidow's belly, aligned with its body frame, so the
     * pose follows the mount across floors, walls, ceilings and swings.
     */
    private static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!(player.getVehicle() instanceof GorewidowEntity mount)) {
            return;
        }
        GorewidowRig rig = GorewidowRigs.peek(mount);
        if (rig == null || !rig.isInitialised()) {
            return;
        }
        float partial = event.getPartialTick();
        Vec3 up = rig.up();
        Vec3 fwd = rig.forward();
        Vec3 belly = rig.modelToWorld(0, 9.0F, 2.0F);
        Vec3 feet = belly.subtract(fwd.scale(0.9));
        Vec3 offset = feet.subtract(player.getPosition(partial));

        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(bodyYaw), 0, Mth.cos(bodyYaw));
        Quaternionf rotation = rotationBetweenFrames(facing, new Vec3(0, 1, 0), up.reverse(), fwd);
        // while being hauled in on silk the rider swings from upright to strapped-in
        float t = mount.getMountProgress(partial);
        float e = t * t * (3 - 2 * t);
        float stance = mount.getStanceBlend(partial);
        e *= 1.0F - stance;
        if (e < 1.0F) {
            rotation = new Quaternionf().slerp(rotation, e);
            offset = offset.scale(e);
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        pose.mulPose(rotation);
        POSED.add(player);
    }

    private static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (POSED.remove(event.getEntity())) {
            event.getPoseStack().popPose();
        }
    }

    /** Rotation taking frame (a1, a2, a1 x a2) onto (b1, b2, b1 x b2). */
    private static Quaternionf rotationBetweenFrames(Vec3 a1, Vec3 a2, Vec3 b1, Vec3 b2) {
        Vec3 a3 = a1.cross(a2);
        Vec3 b3 = b1.cross(b2);
        Matrix3f a = new Matrix3f((float) a1.x, (float) a1.y, (float) a1.z, (float) a2.x, (float) a2.y, (float) a2.z,
                (float) a3.x, (float) a3.y, (float) a3.z);
        Matrix3f b = new Matrix3f((float) b1.x, (float) b1.y, (float) b1.z, (float) b2.x, (float) b2.y, (float) b2.z,
                (float) b3.x, (float) b3.y, (float) b3.z);
        return b.mul(a.transpose()).getNormalizedRotation(new Quaternionf());
    }

    /** Spider vision: hostile things around the rider glow through walls (client-side only). */
    private static void updateThreatOutlines(Minecraft mc, Player player, boolean enabled) {
        java.util.Set<net.minecraft.world.entity.Entity> now = Collections.newSetFromMap(new IdentityHashMap<>());
        if (enabled && mc.level != null) {
            for (net.minecraft.world.entity.Entity e : mc.level.getEntities(player, player.getBoundingBox().inflate(40),
                    e -> e instanceof net.minecraft.world.entity.Mob mob && !(e == player.getVehicle())
                            && (e instanceof net.minecraft.world.entity.monster.Enemy || mob.getTarget() == player
                            || (e instanceof GorewidowEntity w && !w.isTame())))) {
                now.add(e);
            }
        }
        for (net.minecraft.world.entity.Entity old : new java.util.ArrayList<>(OUTLINED)) {
            if (!now.contains(old)) {
                old.setGlowingTag(false);
                OUTLINED.remove(old);
            }
        }
        for (net.minecraft.world.entity.Entity e : now) {
            if (!e.hasGlowingTag()) {
                e.setGlowingTag(true);
            }
            OUTLINED.add(e);
        }
    }

    private static void whistle(Minecraft mc, com.vladosiwe.gorewidow.network.WhistlePayload.Order order) {
        net.minecraft.world.entity.Entity target = order == com.vladosiwe.gorewidow.network.WhistlePayload.Order.ATTACK
                ? pick(mc, 40.0, e -> e instanceof net.minecraft.world.entity.LivingEntity && e != mc.player)
                : pick(mc, 24.0, e -> e instanceof GorewidowEntity);
        PacketDistributor.sendToServer(new com.vladosiwe.gorewidow.network.WhistlePayload(order, target != null ? target.getId() : -1));
        if (mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_FLUTE.value(), 0.6F,
                    order == com.vladosiwe.gorewidow.network.WhistlePayload.Order.STAY ? 0.8F : 1.3F);
        }
    }

    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getVehicle() instanceof GorewidowEntity) {
            event.setDistance(event.getDistance() * 1.9F);
        }
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        GorewidowRigs.clear();
        POSED.clear();
        lastMount = null;
    }
}
