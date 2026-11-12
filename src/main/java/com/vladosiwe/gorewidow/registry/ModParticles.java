package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Gorewidow.MOD_ID);

    /** Loose silk fibres: web shot trails, impacts, webbed targets. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WEB_FIBER = PARTICLES.register("web_fiber", () -> new SimpleParticleType(false));
    /** Heavy blood droplet that falls and splats. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_DROP = PARTICLES.register("blood_drop", () -> new SimpleParticleType(false));
    /** Blood drawn toward the Gorewidow's fangs; the particle's velocity is its travel vector per tick. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_STREAM = PARTICLES.register("blood_stream", () -> new SimpleParticleType(true));
    /** Bite / pounce impact flash. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPACT = PARTICLES.register("impact", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
