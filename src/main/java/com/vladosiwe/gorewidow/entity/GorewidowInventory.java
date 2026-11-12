package com.vladosiwe.gorewidow.entity;

import com.vladosiwe.gorewidow.registry.ModItems;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Slot 0 holds the harness; slots 1..15 are cargo. Everything carried has weight. */
public class GorewidowInventory extends SimpleContainer {
    public static final int HARNESS_SLOT = 0;
    public static final int CARGO_SLOTS = 15;
    public static final int SIZE = 1 + CARGO_SLOTS;

    private final GorewidowEntity widow;

    public GorewidowInventory(GorewidowEntity widow) {
        super(SIZE);
        this.widow = widow;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.widow.onInventoryChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot != HARNESS_SLOT || stack.is(ModItems.HARNESS.get());
    }

    @Override
    public boolean stillValid(Player player) {
        return this.widow.isAlive() && this.widow.isTame() && this.widow.isOwnedBy(player) && player.distanceTo(this.widow) < 8.0F;
    }

    public float totalWeight() {
        float total = 0;
        for (int i = 0; i < this.getContainerSize(); i++) {
            ItemStack stack = this.getItem(i);
            total += stack.getCount() * itemWeight(stack);
        }
        return total;
    }

    /** Weight of a single item: blocks are heavy, the harness is bulky, everything else is light. */
    public static float itemWeight(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (stack.is(ModItems.HARNESS.get())) {
            return 8.0F;
        }
        if (stack.getItem() instanceof BlockItem) {
            return 1.0F;
        }
        return stack.getMaxStackSize() == 1 ? 3.0F : 0.5F;
    }
}
