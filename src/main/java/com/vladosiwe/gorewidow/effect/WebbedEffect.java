package com.vladosiwe.gorewidow.effect;

import com.vladosiwe.gorewidow.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Wrapped in silk: slowed and barely able to jump (attribute modifiers are added at registration).
 * The ambient effect particles are loose web fibres instead of the usual swirl.
 */
public class WebbedEffect extends MobEffect {
    public WebbedEffect() {
        super(MobEffectCategory.HARMFUL, 0xE8E4DA);
    }

    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance instance) {
        // Resolved lazily: particle types may register after mob effects.
        return ModParticles.WEB_FIBER.get();
    }
}
