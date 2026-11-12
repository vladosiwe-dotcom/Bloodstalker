package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** Data-driven damage types (see data/gorewidow/damage_type). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BLOOD_DRAIN = ResourceKey.create(Registries.DAMAGE_TYPE, Gorewidow.id("blood_drain"));

    private ModDamageTypes() {}

    public static DamageSource bloodDrain(Entity attacker) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BLOOD_DRAIN), attacker);
    }
}
