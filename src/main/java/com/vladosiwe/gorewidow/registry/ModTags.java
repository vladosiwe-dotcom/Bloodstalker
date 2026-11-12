package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    /**
     * Bottled blood a Gorewidow drinks: taming, feeding, offerings. Includes this mod's Blood Vial and,
     * when installed, Iron's Spells 'n Spellbooks' blood vial (optional tag entry, no hard dependency).
     */
    public static final TagKey<Item> BLOOD_FOOD = TagKey.create(Registries.ITEM, Gorewidow.id("blood_food"));

    private ModTags() {}
}
