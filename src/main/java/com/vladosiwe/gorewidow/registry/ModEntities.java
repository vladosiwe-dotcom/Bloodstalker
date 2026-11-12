package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.entity.WebShotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Gorewidow.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<GorewidowEntity>> GOREWIDOW = ENTITY_TYPES.register("gorewidow", () ->
            EntityType.Builder.of(GorewidowEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 2.4F)
                    .eyeHeight(2.0F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build(Gorewidow.id("gorewidow").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<WebShotEntity>> WEB_SHOT = ENTITY_TYPES.register("web_shot", () ->
            EntityType.Builder.<WebShotEntity>of(WebShotEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSave()
                    .build(Gorewidow.id("web_shot").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<com.vladosiwe.gorewidow.entity.GorewidowEggEntity>> EGG = ENTITY_TYPES.register("gorewidow_egg", () ->
            EntityType.Builder.<com.vladosiwe.gorewidow.entity.GorewidowEggEntity>of(com.vladosiwe.gorewidow.entity.GorewidowEggEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.6F)
                    .clientTrackingRange(8)
                    .build(Gorewidow.id("gorewidow_egg").toString()));

    private ModEntities() {}
}
