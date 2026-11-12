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
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR + 5 - 2.4, 8));
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

    @GameTest(template = "arena", timeoutTicks = 1200)
    public static void feedingOnACrouchingPlayerTamesIt(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        widow.getStats().rollWild(widow.getRandom());
        widow.getCare().applyStats(true);
        int wildLevel = widow.getStats().wildLevel();
        int needed = com.vladosiwe.gorewidow.entity.GorewidowCare.requiredVials(wildLevel);
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(widow.getX() + 2.5, widow.getY(), widow.getZ());
        player.getInventory().add(new ItemStack(ModItems.BLOOD_VIAL.get(), 32));
        player.setShiftKeyDown(true);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(widow.getTameFeeder() == null && widow.getTamingProgress() == 0, "fed before trusting the player");
        widow.getMind().addTrust(player, 45.0F);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.succeedWhen(() -> {
            player.moveTo(widow.getX() + 2.5, widow.getY(), widow.getZ());
            player.setShiftKeyDown(true);
            helper.assertTrue(widow.isTame(), "still taming: " + widow.getTamingPercent() + "%");
            helper.assertTrue(player.getUUID().equals(widow.getOwnerUUID()), "wrong owner");
            int used = 32 - player.getInventory().countItem(ModItems.BLOOD_VIAL.get());
            helper.assertTrue(used == needed, "expected " + needed + " vials for level " + wildLevel + ", used " + used);
            helper.assertTrue(widow.getTamingEffectiveness() >= 99.9F, "effectiveness dropped without damage");
            helper.assertTrue(widow.getLevel() >= wildLevel + com.vladosiwe.gorewidow.entity.GorewidowCare.bonusLevels(wildLevel, 100),
                    "no bonus levels: " + widow.getLevel());
            player.discard();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void damageLowersTamingEffectiveness(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(widow.getX() + 2.5, widow.getY(), widow.getZ());
        player.getInventory().add(new ItemStack(ModItems.BLOOD_VIAL.get(), 8));
        player.setShiftKeyDown(true);
        widow.getMind().addTrust(player, 45.0F);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        widow.hurt(helper.getLevel().damageSources().generic(), 4.0F);
        helper.assertTrue(widow.getTamingEffectiveness() < 100.0F, "effectiveness unchanged: " + widow.getTamingEffectiveness());
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void bloodLeftOnTheGroundEarnsTrust(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(4, FLOOR, 8));
        widow.goalSelector().addGoal(1, new com.vladosiwe.gorewidow.entity.ai.GorewidowOfferingGoal(widow));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(15, FLOOR, 15)));
        net.minecraft.world.entity.item.ItemEntity offering = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),
                helper.absoluteVec(new Vec3(10, FLOOR, 8)).x, helper.absoluteVec(new Vec3(10, FLOOR, 8)).y,
                helper.absoluteVec(new Vec3(10, FLOOR, 8)).z, new ItemStack(ModItems.BLOOD_VIAL.get()));
        offering.setThrower(player);
        helper.getLevel().addFreshEntity(offering);
        helper.succeedWhen(() -> {
            helper.assertTrue(!offering.isAlive(), "the offering was not taken: widow=" + widow.position().subtract(helper.absoluteVec(Vec3.ZERO))
                    + " item=" + offering.position().subtract(helper.absoluteVec(Vec3.ZERO)) + " age=" + offering.getAge() + " ground=" + offering.onGround()
                    + " tag=" + offering.getItem().is(com.vladosiwe.gorewidow.registry.ModTags.BLOOD_FOOD) + " owner=" + offering.getOwner()
                    + " goals=" + widow.goalSelector().getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(g -> g.getGoal().getClass().getSimpleName()).toList());
            helper.assertTrue(widow.getMind().trust(player) >= 12.0F, "trust did not rise: " + widow.getMind().trust(player));
            player.discard();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void hittingItDestroysTrustAndItHuntsYou(GameTestHelper helper) {
        GorewidowEntity widow = helper.spawn(ModEntities.GOREWIDOW.get(), new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(widow.getX() + 3, widow.getY(), widow.getZ());
        widow.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(widow.getMind().trust(player) <= com.vladosiwe.gorewidow.entity.GorewidowMind.HOSTILE,
                "trust after a hit: " + widow.getMind().trust(player));
        helper.succeedWhen(() -> {
            helper.assertTrue(widow.getTarget() == player, "it does not hunt the attacker");
            player.discard();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void slingshotLaunchesBetweenTwoAnchors(GameTestHelper helper) {
        for (int x = 1; x <= 15; x++) {
            for (int y = FLOOR; y <= FLOOR + 10; y++) {
                helper.setBlock(new BlockPos(x, y, 15), Blocks.STONE);
            }
        }
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 3));
        Vec3 start = widow.position();
        Vec3[] shot = widow.launchSlingshot(new Vec3(0, 0.2, 1));
        helper.assertTrue(shot != null, "no anchors found for the slingshot");
        helper.assertTrue(widow.acceptSlingshot(shot[0], shot[1]), "server refused the slingshot");
        helper.assertTrue(widow.getStamina() < widow.getMaxStamina(), "no stamina spent");
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(widow.position().z - start.z > 3.0, "not launched forward: moved " + (widow.position().z - start.z));
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void walksOnWater(GameTestHelper helper) {
        for (int x = 2; x <= 14; x++) {
            for (int z = 2; z <= 14; z++) {
                helper.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.WATER);
            }
        }
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR + 1, 8));
        helper.runAfterDelay(40, () -> {
            // like a strider on lava, it stands on the fluid's half-block surface instead of sinking
            helper.assertTrue(widow.onGround() && widow.getY() >= helper.absoluteVec(new Vec3(0, FLOOR - 0.55, 0)).y,
                    "sank into the water, y=" + (widow.getY() - helper.absoluteVec(Vec3.ZERO).y));
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void badlyHurtMountDrinksFromTheRidersPack(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(widow.getX() + 2, widow.getY(), widow.getZ());
        player.getInventory().add(new ItemStack(ModItems.BLOOD_VIAL.get(), 3));
        widow.tame(player);
        widow.tryMount(player);
        widow.setHealth(widow.getMaxHealth() * 0.2F);
        float hurt = widow.getHealth();
        helper.runAfterDelay(GorewidowEntity.MOUNT_TICKS + 70, () -> {
            helper.assertTrue(player.getInventory().countItem(ModItems.BLOOD_VIAL.get()) < 3, "no vial taken from the rider");
            helper.assertTrue(widow.getHealth() > hurt, "it did not heal");
            player.stopRiding();
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 800)
    public static void tamedPairMatesAndLaysAnEgg(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        GorewidowEntity mother = spawnInert(helper, new Vec3(6, FLOOR, 8));
        GorewidowEntity father = spawnInert(helper, new Vec3(10, FLOOR, 8));
        mother.tame(owner);
        father.tame(owner);
        mother.setFemale(true);
        father.setFemale(false);
        mother.setMatingEnabled(true);
        father.setMatingEnabled(true);
        helper.succeedWhen(() -> {
            java.util.List<net.minecraft.world.entity.item.ItemEntity> eggs = helper.getLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class, mother.getBoundingBox().inflate(6),
                    e -> e.getItem().is(ModItems.EGG.get()));
            helper.assertTrue(!eggs.isEmpty(), "no egg yet");
            net.minecraft.nbt.CompoundTag data = com.vladosiwe.gorewidow.item.GorewidowEggItem.data(eggs.get(0).getItem());
            helper.assertTrue(data.contains("Child") && owner.getUUID().equals(data.getUUID("Owner")), "egg has no child data / owner");
            helper.assertTrue(mother.getNursery().mateCooldown() > 0, "mother has no mating cooldown");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void eggsNeedWarmthAndHatchIntoATamedBaby(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        GorewidowEntity mother = spawnInert(helper, new Vec3(4, FLOOR, 4));
        GorewidowEntity father = spawnInert(helper, new Vec3(12, FLOOR, 12));
        mother.tame(owner);
        father.tame(owner);
        mother.getStats().addWildPoints(10, mother.getRandom());
        ItemStack eggItem = com.vladosiwe.gorewidow.item.GorewidowEggItem.create(mother, father);
        mother.discard();
        father.discard();
        helper.setBlock(new BlockPos(3, FLOOR, 8), Blocks.CAMPFIRE);
        com.vladosiwe.gorewidow.entity.GorewidowEggEntity warm = new com.vladosiwe.gorewidow.entity.GorewidowEggEntity(ModEntities.EGG.get(), helper.getLevel());
        warm.setEggData(com.vladosiwe.gorewidow.item.GorewidowEggItem.data(eggItem));
        warm.moveTo(helper.absoluteVec(new Vec3(4.5, FLOOR, 8.5)));
        helper.getLevel().addFreshEntity(warm);
        com.vladosiwe.gorewidow.entity.GorewidowEggEntity cold = new com.vladosiwe.gorewidow.entity.GorewidowEggEntity(ModEntities.EGG.get(), helper.getLevel());
        cold.setEggData(com.vladosiwe.gorewidow.item.GorewidowEggItem.data(eggItem));
        cold.moveTo(helper.absoluteVec(new Vec3(13.5, FLOOR, 8.5)));
        helper.getLevel().addFreshEntity(cold);
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(warm.isWarm() && warm.getProgress() > 0, "warm egg is not incubating");
            helper.assertTrue(!cold.isWarm() && cold.getProgress() == 0 && cold.getEggHealth() < 100, "cold egg is not suffering");
            GorewidowEntity baby = warm.hatch();
            helper.assertTrue(baby != null && baby.isBaby() && baby.isTame(), "hatchling is not a tamed baby");
            helper.assertTrue(owner.getUUID().equals(baby.getOwnerUUID()), "hatchling has the wrong owner");
            helper.assertTrue(baby.getStats().generation() == 1, "generation not counted");
            helper.assertTrue(baby.getAgeScale() < 0.5F, "baby is not small");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void answeringCareRequestsImprintsTheBaby(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        GorewidowEntity baby = spawnInert(helper, new Vec3(8, FLOOR, 8));
        baby.tame(player);
        baby.getNursery().setupNewborn();
        float health = baby.getMaxHealth();
        helper.assertTrue(!baby.tryMount(player), "a baby could be ridden");
        // force a cuddle request and answer it
        for (int i = 0; i < 1300 && baby.getCareRequest() == com.vladosiwe.gorewidow.entity.GorewidowNursery.Care.NONE; i++) {
            baby.getNursery().forceNextRequest(com.vladosiwe.gorewidow.entity.GorewidowNursery.Care.CUDDLE);
        }
        baby.getNursery().onCuddle(player);
        helper.assertTrue(baby.getStats().imprint() > 0.12F, "imprint did not rise: " + baby.getStats().imprint());
        helper.assertTrue(baby.getNursery().isImprinter(player), "player did not become the imprinter");
        helper.assertTrue(baby.getMaxHealth() > health, "imprint gave no stat bonus");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void webYanksARiderOffTheirMount(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        net.minecraft.world.entity.vehicle.Boat boat = helper.spawn(EntityType.BOAT, new Vec3(12, FLOOR, 8));
        player.moveTo(boat.position());
        player.startRiding(boat, true);
        helper.assertTrue(player.isPassenger(), "player did not board");
        GorewidowEntity widow = spawnInert(helper, new Vec3(3, FLOOR, 8));
        widow.shootWeb(player.position().add(0, 1, 0), GorewidowEntity.WebPurpose.ATTACK);
        widow.onWebTethered(player);
        helper.assertTrue(!player.isPassenger(), "rider was not pulled off");
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void weaponStanceLiftsTheRiderToShootPastTheMount(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(widow.getX() + 2, widow.getY(), widow.getZ());
        widow.tame(player);
        widow.tryMount(player);
        helper.runAfterDelay(GorewidowEntity.MOUNT_TICKS + 2, () -> {
            double prone = player.getEyeY();
            widow.setWeaponStance(true);
            helper.runAfterDelay(15, () -> {
                double raised = player.getEyeY();
                helper.assertTrue(raised > widow.getBodyCenter().y + 0.6, "rider not lifted: eye " + (raised - widow.getY()));
                helper.assertTrue(raised > prone + 1.0, "stance did not raise the rider (" + prone + " -> " + raised + ")");
                float health = widow.getHealth();
                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), player,
                        new ItemStack(net.minecraft.world.item.Items.ARROW), null);
                arrow.setPos(player.getEyePosition());
                Vec3 aim = widow.getBodyCenter().subtract(player.getEyePosition()).normalize();
                arrow.shoot(aim.x, aim.y, aim.z, 2.5F, 0.0F);
                helper.getLevel().addFreshEntity(arrow);
                helper.runAfterDelay(12, () -> {
                    helper.assertTrue(widow.getHealth() >= health, "the rider's own arrow hurt the mount");
                    player.stopRiding();
                    player.discard();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void levelsUpAndSpendsStatPoints(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        widow.tame(player);
        int level = widow.getLevel();
        float maxHealth = widow.getMaxHealth();
        widow.getCare().grantXp(widow.getStats().xpToNext() + 1);
        helper.assertTrue(widow.getStats().unspent() == 1, "no stat point after level up");
        helper.assertTrue(widow.getCare().spendPoint(com.vladosiwe.gorewidow.entity.GorewidowStats.Stat.HEALTH), "could not spend");
        helper.assertTrue(widow.getLevel() == level + 1, "level did not rise");
        helper.assertTrue(widow.getMaxHealth() > maxHealth, "health point did not raise max health");
        helper.assertTrue(!widow.getCare().spendPoint(com.vladosiwe.gorewidow.entity.GorewidowStats.Stat.HEALTH), "spent a point it did not have");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 80)
    public static void heavyCargoEncumbersAndFoodIsEatenFromCargo(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        widow.tame(player);
        for (int i = 1; i <= 8; i++) {
            widow.getInventory().setItem(i, new ItemStack(Blocks.STONE, 64));
        }
        helper.assertTrue(widow.isEncumbered(), "512 stone should exceed " + widow.getMaxWeight() + ", carrying " + widow.getCarriedWeight());
        for (int i = 1; i <= 8; i++) {
            widow.getInventory().setItem(i, ItemStack.EMPTY);
        }
        helper.assertTrue(!widow.isEncumbered(), "still encumbered after unloading");
        widow.getInventory().setItem(5, new ItemStack(ModItems.BLOOD_VIAL.get(), 2));
        widow.getCare().setFood(10.0F);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(widow.getFood() > 40.0F, "did not eat from its cargo, food=" + widow.getFood());
            helper.assertTrue(widow.getInventory().countItem(ModItems.BLOOD_VIAL.get()) == 1, "vial was not consumed");
            helper.assertTrue(widow.getInventory().countItem(net.minecraft.world.item.Items.GLASS_BOTTLE) == 1, "empty bottle not kept");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void riderMinesAtFullSpeed(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(widow.getX() + 2, widow.getY(), widow.getZ());
        widow.tame(player);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(GorewidowEntity.MOUNT_TICKS + 4, () -> {
            helper.assertTrue(player.getVehicle() == widow, "player did not mount");
            player.setItemInHand(InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
            helper.assertFalse(player.onGround(), "a hanging rider is not on the ground");
            // iron pickaxe on stone: 6.0 standing on the ground, vanilla would give 1.2 in the air
            float speed = player.getDigSpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), null);
            helper.assertTrue(speed > 5.9F, "rider mines too slowly: " + speed);
            player.stopRiding();
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void riderHangsUnderTheBelly(GameTestHelper helper) {
        GorewidowEntity widow = spawnInert(helper, new Vec3(8, FLOOR, 8));
        @SuppressWarnings("removal")
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(widow.getX() + 2, widow.getY(), widow.getZ());
        widow.tame(player);
        widow.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(widow.isMounting(), "no mount sequence");
            helper.assertTrue(widow.getControllingPassenger() == null, "rider steers while still being hauled in");
        });
        helper.runAfterDelay(GorewidowEntity.MOUNT_TICKS + 4, () -> {
            helper.assertTrue(player.getVehicle() == widow, "player did not mount");
            helper.assertTrue(widow.getControllingPassenger() == player, "player does not control the mount");
            double eye = player.getEyeY();
            // the body is slung high above the hitbox centre: the rider hangs under the modelled body
            double belly = widow.modelToWorld(0, com.vladosiwe.gorewidow.entity.GorewidowAnatomy.BODY_HEIGHT, 0).y;
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
