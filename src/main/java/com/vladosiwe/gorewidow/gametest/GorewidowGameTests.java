package com.vladosiwe.gorewidow.gametest;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebMode;
import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side game tests proving the Gorewidow's mechanics actually work (not just animate):
 * surface clinging, wall climbing, web swing physics, tethering prey, taming, riding underneath
 * and blood draining. Run with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(Gorewidow.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GorewidowGameTests {
    /** Relative Y of the arena floor's walkable surface (the structure sits one block above the test origin). */
    private static final int FLOOR = 2;

    private GorewidowGameTests() {}

    private static GorewidowEntity spawnInert(GameTestHelper helper, Vec3 relative) {
        GorewidowEntity widow = helper.spawn(ModEntities.GOREWIDOW.get(), relative);
        widow.clearGoalsForTesting();
        return widow;
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void clingsToCeilingAndFallsWhenItIsBroken(GameTestHelper helper) {
        for (int x = 3; x <= 12; x++) {
            for (int z = 3; z <= 12; z++) {
                helper.setBlock(new BlockPos(x, FLOOR + 5, z), Blocks.STONE);
            }
        }
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR + 5 - 1.8, 8));
        widow.setSurface(Direction.UP);
        double startY = widow.getY();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(widow.getSurface() == Direction.UP, "should still cling to the ceiling, surface=" + widow.getSurface());
            helper.assertTrue(Math.abs(widow.getY() - startY) < 0.3, "should not fall while clinging, dy=" + (widow.getY() - startY));
            for (int x = 3; x <= 12; x++) {
                for (int z = 3; z <= 12; z++) {
                    helper.setBlock(new BlockPos(x, FLOOR + 5, z), Blocks.AIR);
                }
            }
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(widow.getSurface() == Direction.DOWN, "should let go once the ceiling is gone");
                helper.assertTrue(widow.onGround() && widow.getY() < helper.absoluteVec(new Vec3(0, FLOOR + 0.5, 0)).y,
                        "should have fallen to the floor: y=" + (widow.getY() - helper.absoluteVec(Vec3.ZERO).y) + " onGround=" + widow.onGround()
                                + " v=" + widow.getDeltaMovement() + " noGravity=" + widow.isNoGravity());
                helper.succeed();
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 300)
    public static void climbsAWallToReachAHigherSpot(GameTestHelper helper) {
        for (int x = 1; x <= 14; x++) {
            for (int y = FLOOR; y < FLOOR + 5; y++) {
                helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
            }
        }
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 6));
        Vec3 goal = helper.absoluteVec(new Vec3(8, FLOOR + 8, 14));
        double topY = helper.absoluteVec(new Vec3(0, FLOOR + 5, 0)).y;
        boolean[] climbed = {false};
        boolean[] reachedTop = {false};
        helper.onEachTick(() -> {
            widow.getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, 1.0);
            if (widow.getSurface() == Direction.SOUTH) {
                climbed[0] = true;
            }
            if (widow.getY() >= topY - 0.05) {
                reachedTop[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(climbed[0], "never attached to the wall");
            helper.assertTrue(reachedTop[0], "never reached the top of the wall");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void webSwingKeepsTheRopeLength(GameTestHelper helper) {
        for (int x = 6; x <= 10; x++) {
            for (int z = 6; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 13, z), Blocks.STONE);
            }
        }
        GorewidowEntity widow = spawnInert(helper, new Vec3(8.5, FLOOR, 3));
        Vec3 anchor = helper.absoluteVec(new Vec3(8.5, 13, 8.5));
        helper.assertTrue(widow.shootWeb(anchor, GorewidowEntity.WebPurpose.TRAVERSE), "web shot refused");
        int[] swingTicks = {0};
        double[] maxStretch = {0};
        int[] worstTick = {0};
        Vec3[] start = {null};
        helper.onEachTick(() -> {
            if (widow.getWebMode() == WebMode.SWING) {
                if (start[0] == null) {
                    start[0] = widow.position();
                }
                swingTicks[0]++;
                // reel in to lift off the floor, then let the pendulum swing freely
                widow.setAiReel(swingTicks[0] < 16 ? 1.0F : 0.0F);
                double stretch = widow.getBodyCenter().distanceTo(widow.getAnchor()) - widow.getRopeLength();
                // the constraint is applied by the body's own movement, from its first travel step on
                if (swingTicks[0] > 1 && stretch > maxStretch[0]) {
                    maxStretch[0] = stretch;
                    worstTick[0] = swingTicks[0];
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(swingTicks[0] >= 40, "not swinging yet (" + swingTicks[0] + " ticks)");
            helper.assertTrue(maxStretch[0] < 0.35, "rope over-stretched by " + maxStretch[0] + " at swing tick " + worstTick[0]);
            helper.assertTrue(start[0] != null && widow.position().distanceTo(start[0]) > 1.0, "the swing did not move the body");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160)
    public static void webTetherReelsInPrey(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 2));
        Pig pig = helper.spawn(EntityType.PIG, new Vec3(8, FLOOR, 13));
        double startDistance = pig.distanceTo(widow);
        // a wandering pig can dodge a shot; re-fire like the AI would until the line sticks
        helper.onEachTick(() -> {
            if (widow.getWebMode() == WebMode.NONE && pig.distanceTo(widow) > 4.0) {
                Vec3 aim = pig.position().add(0, pig.getBbHeight() * 0.5, 0);
                double flight = widow.getSpinneretPos().distanceTo(aim) / GorewidowEntity.WEB_SHOT_SPEED;
                widow.shootWeb(aim.add(pig.getDeltaMovement().scale(flight)), GorewidowEntity.WebPurpose.ATTACK);
            }
        });
        helper.succeedWhen(() -> {
            double now = pig.distanceTo(widow);
            helper.assertTrue(now < startDistance - 4.0, "pig only moved from " + startDistance + " to " + now + ", web=" + widow.getWebMode() + " link=" + widow.getLinkedEntity() + " spinneret=" + widow.getSpinneretPos().subtract(widow.position()));
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void bloodVialsTameIt(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLOOD_VIAL.get(), 16));
        int feeds = 0;
        while (!widow.isTame() && feeds < 10) {
            widow.mobInteract(player, InteractionHand.MAIN_HAND);
            feeds++;
        }
        helper.assertTrue(widow.isTame(), "not tamed after " + feeds + " vials (progress " + widow.getTamingProgress() + ")");
        helper.assertTrue(player.getUUID().equals(widow.getOwnerUUID()), "wrong owner");
        helper.assertTrue(feeds >= 4, "tamed too easily: " + feeds + " vials");
        helper.assertTrue(player.getMainHandItem().getCount() == 16 - feeds, "vials were not consumed");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void riderHangsUnderTheBelly(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(widow.getX() + 2, widow.getY(), widow.getZ());
        widow.tame(player);
        widow.setHarness(true);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(player.getVehicle() == widow, "player did not mount");
            helper.assertTrue(widow.getControllingPassenger() == player, "player does not control the mount");
            double eye = player.getEyeY();
            double belly = widow.getBodyCenter().y;
            helper.assertTrue(eye < belly, "rider eyes (" + eye + ") should be under the body centre (" + belly + ")");
            helper.assertTrue(eye > widow.getY(), "rider eyes should stay above the ground");
            player.stopRiding();
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void drainingHurtsPreyAndHeals(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(8, FLOOR, 10.5));
        widow.setHealth(30.0F);
        float pigStart = pig.getHealth();
        widow.startDrain(pig);
        helper.assertTrue(widow.isDraining(), "drain did not start");
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(pig.getHealth() < pigStart || !pig.isAlive(), "prey was not drained");
            helper.assertTrue(widow.getHealth() > 30.0F, "draining did not heal, hp=" + widow.getHealth());
            helper.succeed();
        });
    }
}
