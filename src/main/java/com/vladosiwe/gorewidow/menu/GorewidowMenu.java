package com.vladosiwe.gorewidow.menu;

import com.vladosiwe.gorewidow.entity.AggressionMode;
import com.vladosiwe.gorewidow.entity.GorewidowCommand;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowInventory;
import com.vladosiwe.gorewidow.entity.GorewidowStats;
import com.vladosiwe.gorewidow.registry.ModItems;
import com.vladosiwe.gorewidow.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Gorewidow's inventory and stat sheet: a harness slot, 15 cargo slots, and buttons
 * (sent through vanilla menu button clicks) to spend level points and give orders.
 */
public class GorewidowMenu extends AbstractContainerMenu {
    public static final int BUTTON_STAT = 0;         // + stat ordinal
    public static final int BUTTON_AGGRESSION = 10;  // + aggression ordinal
    public static final int BUTTON_COMMAND = 20;     // + command ordinal
    public static final int BUTTON_RIDE = 30;

    public static final int HARNESS_X = 180;
    public static final int HARNESS_Y = 20;
    public static final int CARGO_X = 218;
    public static final int CARGO_Y = 20;
    public static final int PLAYER_X = 182;
    public static final int PLAYER_Y = 92;

    @Nullable
    private final GorewidowEntity widow;
    private final Container container;

    public GorewidowMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, playerInventory.player.level().getEntity(buf.readVarInt()) instanceof GorewidowEntity w ? w : null);
    }

    public GorewidowMenu(int id, Inventory playerInventory, @Nullable GorewidowEntity widow) {
        super(ModMenus.GOREWIDOW.get(), id);
        this.widow = widow;
        this.container = widow != null ? widow.getInventory() : new SimpleContainer(GorewidowInventory.SIZE);
        this.container.startOpen(playerInventory.player);

        this.addSlot(new Slot(this.container, GorewidowInventory.HARNESS_SLOT, HARNESS_X, HARNESS_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.HARNESS.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                this.addSlot(new Slot(this.container, 1 + row * 5 + col, CARGO_X + col * 18, CARGO_Y + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !stack.is(ModItems.HARNESS.get());
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, PLAYER_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_X + col * 18, PLAYER_Y + 58));
        }
    }

    @Nullable
    public GorewidowEntity getWidow() {
        return this.widow;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.widow == null || !this.widow.isOwnedBy(player)) {
            return false;
        }
        if (id >= BUTTON_STAT && id < BUTTON_STAT + GorewidowStats.STATS.length) {
            return this.widow.getCare().spendPoint(GorewidowStats.STATS[id - BUTTON_STAT]);
        }
        if (id >= BUTTON_AGGRESSION && id < BUTTON_AGGRESSION + AggressionMode.values().length) {
            this.widow.setAggression(AggressionMode.values()[id - BUTTON_AGGRESSION]);
            return true;
        }
        if (id >= BUTTON_COMMAND && id < BUTTON_COMMAND + GorewidowCommand.values().length) {
            this.widow.setCommand(GorewidowCommand.values()[id - BUTTON_COMMAND]);
            this.widow.getNavigation().stop();
            return true;
        }
        if (id == BUTTON_RIDE) {
            player.closeContainer();
            return this.widow.tryMount(player);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int creatureSlots = GorewidowInventory.SIZE;
        if (index < creatureSlots) {
            if (!this.moveItemStackTo(stack, creatureSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.HARNESS.get()) && !this.slots.get(0).hasItem()) {
            if (!this.moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 1, creatureSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.widow != null && this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
