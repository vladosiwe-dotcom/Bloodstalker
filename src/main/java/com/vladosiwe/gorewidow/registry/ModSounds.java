package com.vladosiwe.gorewidow.registry;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Gorewidow.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT = register("entity.gorewidow.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = register("entity.gorewidow.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = register("entity.gorewidow.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = register("entity.gorewidow.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> BITE = register("entity.gorewidow.bite");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREECH = register("entity.gorewidow.screech");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRAIN = register("entity.gorewidow.drain");
    public static final DeferredHolder<SoundEvent, SoundEvent> PURR = register("entity.gorewidow.purr");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAP = register("entity.gorewidow.leap");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND = register("entity.gorewidow.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEB_SHOOT = register("entity.gorewidow.web_shoot");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEB_HIT = register("entity.gorewidow.web_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEB_RETRACT = register("entity.gorewidow.web_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEB_STRAIN = register("entity.gorewidow.web_strain");

    private ModSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Gorewidow.id(name)));
    }
}
