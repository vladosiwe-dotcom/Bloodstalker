package com.vladosiwe.gorewidow;

import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.network.ModNetwork;
import com.vladosiwe.gorewidow.registry.ModEffects;
import com.vladosiwe.gorewidow.registry.ModEntities;
import com.vladosiwe.gorewidow.registry.ModItems;
import com.vladosiwe.gorewidow.registry.ModParticles;
import com.vladosiwe.gorewidow.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@Mod(Gorewidow.MOD_ID)
public final class Gorewidow {
    public static final String MOD_ID = "gorewidow";

    public Gorewidow(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModItems.TABS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModEffects.EFFECTS.register(modBus);

        modBus.addListener(Gorewidow::onAttributes);
        modBus.addListener(Gorewidow::onSpawnPlacements);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(Gorewidow::onIncomingDamage);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.GOREWIDOW.get(), GorewidowEntity.createAttributes().build());
    }

    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntities.GOREWIDOW.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                GorewidowEntity::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** A rider strapped under the belly is shielded from suffocation and fall damage of its mount's acrobatics. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().getVehicle() instanceof GorewidowEntity
                && (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALL))) {
            event.setCanceled(true);
        }
    }
}
