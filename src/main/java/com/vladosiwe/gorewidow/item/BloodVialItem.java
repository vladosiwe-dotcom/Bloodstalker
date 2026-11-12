package com.vladosiwe.gorewidow.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Congealed blood in a bottle. Used on a Gorewidow to pacify and tame it, or to heal a tamed one. */
public class BloodVialItem extends Item {
    public BloodVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gorewidow.blood_vial.tooltip").withStyle(ChatFormatting.DARK_RED));
    }
}
