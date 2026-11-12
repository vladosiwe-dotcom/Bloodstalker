package com.vladosiwe.gorewidow.item;

import com.vladosiwe.gorewidow.entity.GorewidowEggEntity;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.GorewidowStats;
import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A fertilized Gorewidow egg. It already carries the child's inherited stats and mutations; place it
 * near a heat source (campfire, fire, lava, magma, torch or lantern) to incubate it.
 */
public class GorewidowEggItem extends Item {
    public GorewidowEggItem(Properties properties) {
        super(properties);
    }

    /** Builds the egg a mother lays after mating with a father. */
    public static ItemStack create(GorewidowEntity mother, GorewidowEntity father) {
        GorewidowStats child = GorewidowStats.inherit(mother.getStats(), father.getStats(), mother.getRandom());
        CompoundTag data = new CompoundTag();
        data.put("Child", child.save());
        if (mother.getOwnerUUID() != null) {
            data.putUUID("Owner", mother.getOwnerUUID());
        }
        data.putString("Mother", mother.getDisplayName().getString());
        data.putString("Father", father.getDisplayName().getString());
        data.putFloat("Progress", 0.0F);
        ItemStack stack = new ItemStack(ModItems.EGG.get());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        if (!level.isClientSide) {
            GorewidowEggEntity egg = new GorewidowEggEntity(ModEntities.EGG.get(), level);
            egg.setEggData(data(context.getItemInHand()));
            egg.moveTo(Vec3.atBottomCenterOf(pos));
            level.addFreshEntity(egg);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = data(stack);
        if (data.contains("Child")) {
            GorewidowStats child = new GorewidowStats();
            child.load(data.getCompound("Child"));
            tooltip.add(Component.translatable("item.gorewidow.gorewidow_egg.parents", data.getString("Mother"), data.getString("Father"))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.gorewidow.gorewidow_egg.level", child.level(), child.mutations(), child.generation())
                    .withStyle(ChatFormatting.DARK_RED));
            float progress = data.getFloat("Progress");
            if (progress > 0) {
                tooltip.add(Component.translatable("item.gorewidow.gorewidow_egg.progress", Math.round(progress * 100)).withStyle(ChatFormatting.GOLD));
            }
        }
        tooltip.add(Component.translatable("item.gorewidow.gorewidow_egg.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
