package com.vladosiwe.gorewidow.client.dev;

import com.mojang.logging.LogUtils;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.registry.ModEntities;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Development-only scripted showcase (enabled with -Dgorewidow.showcase=true, see the runShowcase
 * Gradle run). Builds small scenes in a flat test world, poses Gorewidows in each of their states,
 * frames them with the camera and saves screenshots, then quits. Never active in normal play.
 */
public final class DevShowcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<Scene> SCENES = new ArrayList<>();
    private static int sceneIndex = -1;
    private static int ticks;
    private static int globalTicks;
    private static BlockPos origin;

    private record Scene(String name, int settleTicks, Consumer<Ctx> setup, Consumer<Ctx> perTick, Vec3 camera, Vec3 lookAt, boolean thirdPerson) {}

    private static Ctx currentCtx;

    private record Ctx(ServerLevel level, ServerPlayer player, BlockPos base, List<Mob> spawned) {
        Vec3 at(double x, double y, double z) {
            return Vec3.atBottomCenterOf(this.base).add(x, y, z);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        this.level.setBlockAndUpdate(this.base.offset(x, y, z), state);
                    }
                }
            }
        }

        GorewidowEntity widow(double x, double y, double z, float yaw) {
            GorewidowEntity w = ModEntities.GOREWIDOW.get().create(this.level);
            Vec3 p = this.at(x, y, z);
            w.moveTo(p.x, p.y, p.z, yaw, 0);
            w.setYBodyRot(yaw);
            w.setYHeadRot(yaw);
            w.clearGoalsForTesting();
            w.setPersistenceRequired();
            this.level.addFreshEntity(w);
            this.spawned.add(w);
            return w;
        }

        /** A Gorewidow with its full AI, for behaviour scenes. */
        GorewidowEntity livingWidow(double x, double y, double z, float yaw) {
            GorewidowEntity w = ModEntities.GOREWIDOW.get().create(this.level);
            Vec3 p = this.at(x, y, z);
            w.moveTo(p.x, p.y, p.z, yaw, 0);
            w.setPersistenceRequired();
            this.level.addFreshEntity(w);
            this.spawned.add(w);
            return w;
        }

        GorewidowEntity first() {
            return (GorewidowEntity) this.spawned.get(0);
        }
    }

    private DevShowcase() {}

    public static void enable() {
        LOGGER.info("Gorewidow showcase enabled");
        defineScenes();
        if (Boolean.getBoolean("gorewidow.showcase.ui")) {
            SCENES.clear();
            defineUiScenes();
        }
        if (Boolean.getBoolean("gorewidow.showcase.family")) {
            SCENES.clear();
            defineFamilyScenes();
        }
        if (Boolean.getBoolean("gorewidow.showcase.ride")) {
            SCENES.clear();
            defineRideScenes();
        }
        if (Boolean.getBoolean("gorewidow.showcase.mind")) {
            SCENES.clear();
            defineMindScenes();
        }
        NeoForge.EVENT_BUS.addListener(DevShowcase::onClientTick);
    }

    private static void defineScenes() {
        SCENES.add(new Scene("portrait", 40, c -> c.widow(0, 0, 0, 200), c -> {}, new Vec3(3.2, 2.4, -4.6), new Vec3(0, 1.0, 0), false));
        SCENES.add(new Scene("side", 30, c -> c.widow(0, 0, 0, 90), c -> {}, new Vec3(0, 1.6, -6.5), new Vec3(0, 1.0, 0), false));
        SCENES.add(new Scene("walking", 26, c -> c.widow(-6, 0, 0, 270), c -> {
            Vec3 goal = c.at(12, 0, 0);
            c.first().getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 0.7);
        }, new Vec3(1, 2.2, -6), new Vec3(-1.5, 1.0, 0), false));
        SCENES.add(new Scene("wall_climb", 50, c -> {
            c.fill(3, 0, -4, 3, 9, 4, Blocks.STONE_BRICKS.defaultBlockState());
            GorewidowEntity w = c.widow(1.55, 1.5, 0, 270);
            w.setSurface(Direction.EAST);
        }, c -> {
            Vec3 goal = c.at(2.1, 12, 0);
            c.first().getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 0.5);
        }, new Vec3(-5.5, 4.2, -4.5), new Vec3(2.2, 3.4, 0), false));
        SCENES.add(new Scene("ceiling", 40, c -> {
            c.fill(-4, 6, -4, 4, 6, 4, Blocks.DEEPSLATE_TILES.defaultBlockState());
            c.fill(4, 0, 4, 4, 5, 4, Blocks.DEEPSLATE_TILES.defaultBlockState());
            GorewidowEntity w = c.widow(0, 6 - 2.4, 0, 30);
            w.setSurface(Direction.UP);
        }, c -> {}, new Vec3(-4.5, 1.6, -5.0), new Vec3(0, 4.6, 0), false));
        SCENES.add(new Scene("web_swing", 40, c -> {
            c.fill(-1, 15, -1, 1, 15, 1, Blocks.SPRUCE_LOG.defaultBlockState());
            c.fill(0, 0, 3, 0, 14, 3, Blocks.SPRUCE_LOG.defaultBlockState());
            c.fill(0, 15, 2, 0, 15, 2, Blocks.SPRUCE_LOG.defaultBlockState());
            GorewidowEntity w = c.widow(0.5, 0, -9, 0);
            w.shootWeb(c.at(0.5, 15, 0), GorewidowEntity.WebPurpose.TRAVERSE);
        }, c -> c.first().setAiReel(ticks < 26 ? 1.0F : 0.0F), new Vec3(-13, 6, -1), new Vec3(0, 7.5, -1), false));
        SCENES.add(new Scene("web_tether", 12, c -> {
            GorewidowEntity w = c.widow(-5, 0, 0, 270);
            Pig pig = EntityType.PIG.create(c.level);
            Vec3 p = c.at(8, 0, 0);
            pig.moveTo(p.x, p.y, p.z, 90, 0);
            c.level.addFreshEntity(pig);
            c.spawned.add(pig);
            w.shootWeb(p.add(0, 0.5, 0), GorewidowEntity.WebPurpose.ATTACK);
        }, c -> {}, new Vec3(1, 3.5, -8), new Vec3(1, 1.0, 0), false));
        SCENES.add(new Scene("hang", 70, c -> {
            c.fill(-2, 10, -2, 2, 10, 2, Blocks.OAK_LEAVES.defaultBlockState());
            GorewidowEntity w = c.widow(0, 10 - 2.4, 0, 0);
            w.startHang(c.at(0, 10, 0), Direction.DOWN, 1.0F);
        }, c -> c.first().setAiReel(c.first().getRopeLength() < 4.5F ? -1.0F : 0.0F),
                new Vec3(-5, 3.5, -5), new Vec3(0, 6.0, 0), false));
        SCENES.add(new Scene("drain", 30, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 0);
            Pig pig = EntityType.PIG.create(c.level);
            Vec3 p = c.at(0, 0, 2.3);
            pig.moveTo(p.x, p.y, p.z, 180, 0);
            pig.setNoAi(true);
            c.level.addFreshEntity(pig);
            c.spawned.add(pig);
            w.startDrain(pig);
        }, c -> {}, new Vec3(4.5, 2.4, 3.5), new Vec3(0, 0.8, 1.2), false));
        SCENES.add(new Scene("riding", 30, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 0);
            c.player.setGameMode(GameType.CREATIVE);
            w.tame(c.player);
            w.setHarness(true);
            c.player.moveTo(w.getX() + 1.5, w.getY(), w.getZ(), 0, 0);
            c.player.startRiding(w, true);
        }, c -> {}, new Vec3(0, 0, 0), new Vec3(0, 0, 0), true));
        SCENES.add(new Scene("riding_wall_pov", 70, c -> {
            // an ore wall: the rider hangs under the mount as it clings to it and mines with a pickaxe
            c.fill(3, 0, -5, 3, 9, 5, Blocks.STONE.defaultBlockState());
            c.fill(3, 2, -1, 3, 3, 1, Blocks.IRON_ORE.defaultBlockState());
            c.fill(3, 4, 1, 3, 4, 2, Blocks.DIAMOND_ORE.defaultBlockState());
            c.fill(3, 3, -3, 3, 4, -2, Blocks.COAL_ORE.defaultBlockState());
            GorewidowEntity w = c.widow(1.55, 2.5, 0, 270);
            w.setSurface(Direction.EAST);
            c.player.setGameMode(GameType.SURVIVAL);
            w.tame(c.player);
            w.setHarness(true);
            c.player.moveTo(w.getX() - 1.0, w.getY(), w.getZ(), 0, 0);
            c.player.startRiding(w, true);
            c.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
        }, c -> {
            net.minecraft.client.player.LocalPlayer local = Minecraft.getInstance().player;
            if (ticks % 10 == 0) {
                GorewidowEntity sw = c.first();
                net.minecraft.world.entity.Entity cw = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.getEntity(sw.getId());
                LOGGER.info("wallpov t={} server surface={} pos={} client surface={} pos={} riding={}", ticks, sw.getSurface(), sw.position(),
                        cw instanceof GorewidowEntity g ? g.getSurface() : null, cw == null ? null : cw.position(), local == null ? null : local.getVehicle());
            }
            if (local != null) {
                // looking along the wall at the ore, so the view banks with the mount
                local.setYRot(-75);
                local.setXRot(-5);
                local.yRotO = -75;
                local.xRotO = -5;
            }
        }, new Vec3(0, 0, 0), new Vec3(0, 0, 0), true));
        SCENES.add(new Scene("riding_pov", 30, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 0);
            c.player.setGameMode(GameType.CREATIVE);
            w.tame(c.player);
            w.setHarness(true);
            c.player.moveTo(w.getX() + 1.5, w.getY(), w.getZ(), 0, 0);
            c.player.startRiding(w, true);
        }, c -> {}, new Vec3(0, 0, 0), new Vec3(0, 0, 0), true));
    }

    private static ServerPlayer rider;
    private static volatile boolean slingFired;

    /** A second, server-side player to show the rider from the outside (same trick the game tests use). */
    private static ServerPlayer makeRider(ServerLevel level) {
        if (rider != null && !rider.isRemoved()) {
            return rider;
        }
        net.minecraft.server.network.CommonListenerCookie cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Rider"), false);
        rider = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, rider, cookie);
        rider.setGameMode(GameType.SURVIVAL);
        return rider;
    }

    private static void mountScene(String name, int settle) {
        SCENES.add(new Scene(name, settle, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 90);
            ServerPlayer r = makeRider(c.level);
            r.stopRiding();
            Vec3 p = c.at(-2.2, 0, 0.3);
            r.teleportTo(c.level, p.x, p.y, p.z, 90, 0);
            w.tame(r);
            w.setHarness(true);
        }, c -> {
            if (ticks == 10) {
                c.first().tryMount(rider);
            }
        }, new Vec3(-4.5, 1.6, -3.6), new Vec3(-1.0, 0.9, 0.2), false));
    }

    private static void defineRideScenes() {
        mountScene("ride_mount_a", 10 + 9);
        mountScene("ride_mount_b", 10 + 20);
        mountScene("ride_mount_c", 10 + 44);
        SCENES.add(new Scene("ride_held", 10 + 44, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 90);
            ServerPlayer r = makeRider(c.level);
            r.stopRiding();
            Vec3 p = c.at(-2.2, 0, 0.3);
            r.teleportTo(c.level, p.x, p.y, p.z, 90, 0);
            w.tame(r);
            w.setHarness(true);
        }, c -> {
            if (ticks == 10) {
                c.first().tryMount(rider);
            }
        }, new Vec3(0.4, 0.25, -2.4), new Vec3(0.2, 1.05, 0.3), false));
        SCENES.add(new Scene("ride_stance", 10 + 50, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 90);
            ServerPlayer r = makeRider(c.level);
            r.stopRiding();
            Vec3 p = c.at(-2.2, 0, 0.3);
            r.teleportTo(c.level, p.x, p.y, p.z, 90, 0);
            r.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            w.tame(r);
        }, c -> {
            if (ticks == 10) {
                c.first().tryMount(rider);
            }
            if (ticks == 10 + GorewidowEntity.MOUNT_TICKS) {
                c.first().setWeaponStance(true);
            }
        }, new Vec3(-1.5, 2.0, -6.5), new Vec3(-0.8, 1.6, 0), false));
        SCENES.add(new Scene("ride_slingshot", 15, c -> {
            c.fill(-8, 0, 8, 8, 12, 8, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
            c.widow(0, 0, -4, 0);
            slingFired = false;
        }, c -> {
            if (ticks >= 10 && !slingFired) {
                slingFired = true;
                Vec3[] shot = c.first().launchSlingshot(new Vec3(0, 0.3, 1));
                LOGGER.info("showcase slingshot: {}", shot == null ? "no anchors" : shot[2]);
                if (shot != null) {
                    c.first().acceptSlingshot(shot[0], shot[1]);
                }
            }
        }, new Vec3(-12, 4, 1), new Vec3(0, 4, 2), false));
        SCENES.add(new Scene("ride_glide", 40, c -> {
            GorewidowEntity w = c.widow(0, 9, 0, 30);
            ServerPlayer r = makeRider(c.level);
            r.stopRiding();
            Vec3 rp = c.at(0, 9, 0);
            r.teleportTo(c.level, rp.x, rp.y, rp.z, 0, 0);
            w.tame(r);
            w.tryMount(r);
            w.setRiderGliding(true);
        }, c -> {
            c.first().setRiderGliding(true);
            c.first().setDeltaMovement(c.first().getDeltaMovement().multiply(1, 0, 1).add(0, -0.1, 0));
        }, new Vec3(-6, 7, -6), new Vec3(0, 9, 0), false));
    }

    private static void defineFamilyScenes() {
        SCENES.add(new Scene("ui_family_eggs", 40, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            GorewidowEntity mother = c.widow(-6, 0, 6, 0);
            GorewidowEntity father = c.widow(6, 0, 6, 0);
            mother.tame(c.player);
            father.tame(c.player);
            net.minecraft.world.item.ItemStack egg = com.vladosiwe.gorewidow.item.GorewidowEggItem.create(mother, father);
            mother.discard();
            father.discard();
            c.fill(-1, 0, 3, -1, 0, 3, Blocks.CAMPFIRE.defaultBlockState());
            for (int i = 0; i < 2; i++) {
                com.vladosiwe.gorewidow.entity.GorewidowEggEntity e = new com.vladosiwe.gorewidow.entity.GorewidowEggEntity(
                        com.vladosiwe.gorewidow.registry.ModEntities.EGG.get(), c.level);
                e.setEggData(com.vladosiwe.gorewidow.item.GorewidowEggItem.data(egg));
                Vec3 p = c.at(i == 0 ? 0.2 : 1.1, 0, 2.6 + i * 0.7);
                e.moveTo(p.x, p.y, p.z, 0, 0);
                c.level.addFreshEntity(e);
            }
        }, c -> {}, new Vec3(1.8, 1.3, 0.6), new Vec3(0.3, 0.2, 3.0), false));
        SCENES.add(new Scene("mind_family_baby", 30, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            GorewidowEntity baby = c.widow(0, 0, 3, 200);
            baby.tame(c.player);
            baby.setFemale(true);
            baby.getStats().addWildPoints(9, baby.getRandom());
            baby.getNursery().setupNewborn();
            baby.setAge(-com.vladosiwe.gorewidow.entity.GorewidowNursery.MATURATION_TICKS / 3);
            baby.getNursery().forceNextRequest(com.vladosiwe.gorewidow.entity.GorewidowNursery.Care.CUDDLE);
            baby.getStats().setImprint(0.375F);
            baby.getCare().applyStats(true);
            baby.setCustomName(net.minecraft.network.chat.Component.literal("Ink"));
            uiEntityId = baby.getId();
            GorewidowEntity mom = c.widow(-3.5, 0, 6.5, 160);
            mom.tame(c.player);
            mom.setFemale(true);
        }, c -> {}, new Vec3(1.5, 1.6, 0), new Vec3(-0.5, 0.4, 3), false));
        SCENES.add(new Scene("ui_family_wheel", 30, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            c.player.setShiftKeyDown(false);
            GorewidowEntity w = c.widow(0, 0, 0, 180);
            w.tame(c.player);
            w.setFemale(true);
            w.setMatingEnabled(true);
            w.setCustomName(net.minecraft.network.chat.Component.literal("Velvet"));
            uiEntityId = w.getId();
        }, c -> {}, new Vec3(0, 1.6, 3.2), new Vec3(0, 0.9, 0), false));
    }

    private static void defineMindScenes() {
        SCENES.add(new Scene("mind_stalking", 34, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            c.player.setShiftKeyDown(false);
            uiEntityId = c.livingWidow(-7, 0, 9, 200).getId();
        }, c -> {}, new Vec3(0, 1.6, 0), new Vec3(-2.5, 1.0, 9), false));
        SCENES.add(new Scene("mind_frozen", 16, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            c.player.setShiftKeyDown(false);
            uiEntityId = c.livingWidow(0, 0, 9, 180).getId();
        }, c -> {}, new Vec3(0, 1.6, 0), new Vec3(0, 0.9, 9), false));
        SCENES.add(new Scene("mind_affection", 110, c -> {
            c.player.setGameMode(GameType.SURVIVAL);
            c.player.setShiftKeyDown(false);
            GorewidowEntity w = c.livingWidow(0, 0, 6, 180);
            uiEntityId = w.getId();
            w.tame(c.player);
            w.getMind().addTrust(c.player, 70);
            w.setCustomName(net.minecraft.network.chat.Component.literal("Velvet"));
        }, c -> {}, new Vec3(0, 1.6, 0), new Vec3(0, 0.6, 3), false));
    }

    private static void defineUiScenes() {
        SCENES.add(new Scene("ui_taming", 200, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 180);
            w.getStats().rollWild(w.getRandom());
            w.getStats().addWildPoints(14, w.getRandom());
            w.getCare().applyStats(true);
            c.player.setGameMode(GameType.SURVIVAL);
            c.player.getInventory().clearContent();
            c.player.getInventory().add(new net.minecraft.world.item.ItemStack(com.vladosiwe.gorewidow.registry.ModItems.BLOOD_VIAL.get(), 16));
            c.player.setShiftKeyDown(true);
            w.mobInteract(c.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }, c -> {
            c.player.setShiftKeyDown(true);
            if (ticks == 120) {
                c.first().hurt(c.level.damageSources().generic(), 3.0F);
            }
        }, new Vec3(0, 1.4, 2.6), new Vec3(0, 0.9, 0), false));
        SCENES.add(new Scene("ui_wheel", 30, c -> {
            c.player.setShiftKeyDown(false);
            GorewidowEntity w = c.widow(0, 0, 0, 180);
            c.player.setGameMode(GameType.SURVIVAL);
            w.tame(c.player);
            w.setHarness(true);
            w.setCustomName(net.minecraft.network.chat.Component.literal("Velvet"));
            uiEntityId = w.getId();
        }, c -> {}, new Vec3(0, 1.6, 3.2), new Vec3(0, 0.9, 0), false));
        SCENES.add(new Scene("ui_inventory", 30, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 180);
            c.player.setGameMode(GameType.SURVIVAL);
            w.tame(c.player);
            w.setHarness(true);
            w.setCustomName(net.minecraft.network.chat.Component.literal("Velvet"));
            w.getStats().addWildPoints(18, w.getRandom());
            w.getCare().applyStats(true);
            w.getCare().grantXp(400);
            w.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(com.vladosiwe.gorewidow.registry.ModItems.BLOOD_VIAL.get(), 12));
            w.getInventory().setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEEF, 20));
            w.getInventory().setItem(3, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STRING, 34));
            w.getInventory().setItem(6, new net.minecraft.world.item.ItemStack(Blocks.SPRUCE_LOG, 64));
            w.getCare().setFood(71.0F);
        }, c -> {
            if (ticks == 8) {
                c.first().getCare().openInventory(c.player);
            }
        }, new Vec3(0, 1.6, 3.2), new Vec3(0, 0.9, 0), false));
        SCENES.add(new Scene("ui_mount", 40, c -> {
            GorewidowEntity w = c.widow(0, 0, 0, 0);
            c.player.setGameMode(GameType.CREATIVE);
            w.tame(c.player);
            w.setHarness(true);
            w.setCustomName(net.minecraft.network.chat.Component.literal("Velvet"));
            w.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Blocks.COBBLESTONE, 64));
            c.player.setShiftKeyDown(false);
            c.player.moveTo(w.getX() + 1.5, w.getY(), w.getZ(), 0, 0);
            c.player.startRiding(w, true);
        }, c -> {}, new Vec3(0, 0, 0), new Vec3(0, 0, 0), false));
    }

    private static int uiEntityId = -1;

    // ------------------------------------------------------------------ lair tour (-Dgorewidow.showcase.lair=true)
    private static int lairStage;
    private static int lairWait;
    private static BlockPos lairChunk;
    private static GorewidowEntity lairWidow;
    private static Vec3 lairHomeSeen;

    private static void lookFrom(ServerPlayer player, ServerLevel level, Vec3 cam, Vec3 at) {
        Vec3 d = at.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
        player.teleportTo(level, cam.x, cam.y - player.getEyeHeight(), cam.z, yaw, pitch);
    }

    private static void lairTour(Minecraft mc, MinecraftServer server) {
        lairWait++;
        mc.options.hideGui = true;
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        switch (lairStage) {
            case 0 -> server.execute(() -> {
                player.setGameMode(GameType.SPECTATOR);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.setDayTime(6000);
                lairChunk = com.vladosiwe.gorewidow.dev.DevServerHooks.locateLair(level, BlockPos.ZERO);
                LOGGER.info("showcase lair at {}", lairChunk);
                player.teleportTo(level, lairChunk.getX() + 8, 200, lairChunk.getZ() + 8, 0, 90);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 100000, 0, false, false));
                lairStage = 1;
                lairWait = 0;
            });
            case 1 -> {
                if (lairWait % 20 == 0) {
                    server.execute(() -> {
                        for (GorewidowEntity w : level.getEntitiesOfClass(GorewidowEntity.class,
                                new net.minecraft.world.phys.AABB(lairChunk).inflate(80, 400, 80), GorewidowEntity::hasLair)) {
                            lairWidow = w;
                        }
                        if (lairWidow != null && lairStage == 1) {
                            BlockPos e = lairWidow.getLairEntrance();
                            Vec3 home = Vec3.atCenterOf(lairWidow.getLairHome());
                            lairHomeSeen = home;
                            Vec3 out = Vec3.atCenterOf(e).subtract(home).multiply(1, 0, 1).normalize();
                            LOGGER.info("showcase lair: entrance {} home {} widow at {}", e, lairWidow.getLairHome(), lairWidow.blockPosition());
                            lookFrom(player, level, Vec3.atCenterOf(e).add(out.scale(9)).add(0, 4, 0), Vec3.atCenterOf(e).add(0, -1, 0));
                            lairStage = 2;
                            lairWait = 0;
                        }
                    });
                }
                if (lairWait > 1200) {
                    LOGGER.info("showcase lair: no Gorewidow found");
                    mc.stop();
                }
            }
            case 2 -> {
                if (lairWait == 120) {
                    Screenshot.grab(mc.gameDirectory, "gorewidow_lair_00_entrance.png", mc.getMainRenderTarget(), m -> {});
                    server.execute(() -> {
                        Vec3 w = lairWidow.position();
                        Vec3 cam = lairHomeSeen.add(5, 1.5, 3);
                        lookFrom(player, level, cam, w.add(0, 1, 0));
                        lairStage = 3;
                        lairWait = 0;
                    });
                }
            }
            case 3 -> {
                if (lairWait == 60) {
                    Screenshot.grab(mc.gameDirectory, "gorewidow_lair_01_den.png", mc.getMainRenderTarget(), m -> {});
                    server.execute(() -> {
                        level.setDayTime(14000);
                        LOGGER.info("showcase lair: night falls, widow at {}", lairWidow.blockPosition());
                        lairStage = 4;
                        lairWait = 0;
                    });
                }
            }
            case 4 -> {
                if (lairWait % 100 == 0) {
                    server.execute(() -> LOGGER.info("showcase lair: night t+{} widow at {} ({} from home)", lairWait, lairWidow.blockPosition(),
                            Math.round(lairWidow.position().distanceTo(lairHomeSeen))));
                }
                if (lairWait == 900) {
                    server.execute(() -> {
                        Vec3 w = lairWidow.position();
                        lookFrom(player, level, w.add(6, 4, 6), w.add(0, 1, 0));
                        lairStage = 5;
                        lairWait = 0;
                    });
                }
            }
            case 5 -> {
                if (lairWait == 40) {
                    Screenshot.grab(mc.gameDirectory, "gorewidow_lair_02_night_hunt.png", mc.getMainRenderTarget(), m -> {});
                }
                if (lairWait == 45) {
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (Boolean.getBoolean("gorewidow.showcase.lair")) {
            globalTicks++;
            if (globalTicks > 60) {
                lairTour(mc, server);
            }
            return;
        }
        globalTicks++;
        if (sceneIndex < 0) {
            if (globalTicks < 60) {
                return;
            }
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerLevel level = server.overworld();
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                level.setDayTime(5000);
                level.setWeatherParameters(6000, 0, false, false);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
                origin = new BlockPos(0, y, 0);
            });
            startScene(mc, server, 0);
            return;
        }
        ticks++;
        Scene scene = SCENES.get(sceneIndex);
        if (currentCtx != null) {
            Ctx ctx = currentCtx;
            server.execute(() -> scene.perTick().accept(ctx));
        }
        mc.options.hideGui = !scene.name().startsWith("ui_") && !scene.name().startsWith("mind_");
        if ((scene.name().equals("ui_wheel") || scene.name().equals("ui_family_wheel")) && ticks == 12 && mc.level.getEntity(uiEntityId) instanceof GorewidowEntity w) {
            mc.setScreen(new com.vladosiwe.gorewidow.client.gui.RadialMenuScreen(w));
        }
        if (ticks == scene.settleTicks() - 2 && mc.screen != null && scene.name().endsWith("wheel")) {
            // park the pointer over an option so the hover highlight shows
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() / 2.0 + 120, mc.getWindow().getScreenHeight() / 2.0 - 60);
        }
        if (scene.name().startsWith("mind_") && ticks >= scene.settleTicks() - 3 && ticks < scene.settleTicks() && mc.level.getEntity(uiEntityId) instanceof GorewidowEntity w) {
            // frame the creature exactly so the info panel shows
            Vec3 d = w.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        }
        if (ticks == scene.settleTicks()) {
            net.minecraft.world.entity.Entity cr = rider == null ? null : mc.level.getEntity(rider.getId());
            LOGGER.info("showcase {}: rider={} clientRider={} clientVehicle={} prog={}", scene.name(), rider == null ? null : rider.position(), cr == null ? null : cr.position(), cr == null ? null : cr.getVehicle(), cr != null && cr.getVehicle() instanceof GorewidowEntity gw ? gw.getMountProgress(0) : -1);
            String file = "gorewidow_" + String.format("%02d", sceneIndex) + "_" + scene.name() + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> LOGGER.info("showcase: {}", msg.getString()));
        }
        if (ticks >= scene.settleTicks() + 4) {
            if (sceneIndex + 1 < SCENES.size()) {
                startScene(mc, server, sceneIndex + 1);
            } else {
                LOGGER.info("Gorewidow showcase finished");
                mc.stop();
            }
        }
    }

    private static void startScene(Minecraft mc, MinecraftServer server, int index) {
        sceneIndex = index;
        ticks = 0;
        Scene scene = SCENES.get(index);
        mc.options.setCameraType(scene.thirdPerson() && !scene.name().endsWith("_pov") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            if (currentCtx != null) {
                player.stopRiding();
                player.closeContainer();
                currentCtx.spawned().forEach(m -> m.discard());
            }
            BlockPos base = origin.offset(index * 48, 0, 0);
            Ctx ctx = new Ctx(level, player, base, new ArrayList<>());
            ctx.fill(-8, 0, -8, 8, 16, 8, Blocks.AIR.defaultBlockState());
            ctx.fill(-8, -1, -8, 8, -1, 8, Blocks.GRASS_BLOCK.defaultBlockState());
            player.setGameMode(GameType.SPECTATOR);
            Vec3 cam = ctx.at(scene.camera().x, scene.camera().y, scene.camera().z);
            Vec3 look = ctx.at(scene.lookAt().x, scene.lookAt().y, scene.lookAt().z);
            Vec3 d = look.subtract(cam);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
            player.teleportTo(level, cam.x, cam.y - player.getEyeHeight(), cam.z, yaw, pitch);
            scene.setup().accept(ctx);
            if (scene.thirdPerson()) {
                // camera behind and slightly below the belly line, looking at the strapped rider
                player.setYRot(-30);
                player.setXRot(scene.name().equals("riding_pov") ? 4 : -28);
            }
            currentCtx = ctx;
        });
    }
}
