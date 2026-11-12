package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.effect.WebbedEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Gorewidow.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> WEBBED = EFFECTS.register("webbed", () ->
            new WebbedEffect()
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, Gorewidow.id("effect.webbed.speed"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, Gorewidow.id("effect.webbed.jump"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private ModEffects() {}
}
