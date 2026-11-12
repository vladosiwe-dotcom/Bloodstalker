package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.item.BloodVialItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gorewidow.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gorewidow.MOD_ID);

    /** Taming offering and healing food for tamed Gorewidows. */
    public static final DeferredItem<BloodVialItem> BLOOD_VIAL = ITEMS.registerItem("blood_vial", BloodVialItem::new,
            new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));
    /** Rider harness; required to mount a tamed Gorewidow. */
    public static final DeferredItem<Item> HARNESS = ITEMS.registerSimpleItem("gorewidow_harness",
            new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<DeferredSpawnEggItem> SPAWN_EGG = ITEMS.register("gorewidow_spawn_egg", () ->
            new DeferredSpawnEggItem(ModEntities.GOREWIDOW, 0x1E080A, 0xB3121E, new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gorewidow"))
            .icon(() -> new ItemStack(SPAWN_EGG.get()))
            .displayItems((params, output) -> {
                output.accept(SPAWN_EGG.get());
                output.accept(BLOOD_VIAL.get());
                output.accept(HARNESS.get());
            })
            .build());

    private ModItems() {}
}
