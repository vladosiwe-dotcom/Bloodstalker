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
        com.vladosiwe.gorewidow.registry.ModMenus.MENUS.register(modBus);
        com.vladosiwe.gorewidow.worldgen.ModStructures.TYPES.register(modBus);
        com.vladosiwe.gorewidow.worldgen.ModStructures.PIECES.register(modBus);

        modBus.addListener(Gorewidow::onAttributes);
        modBus.addListener(Gorewidow::onSpawnPlacements);
        modBus.addListener(ModNetwork::register);
        NeoForge.EVENT_BUS.addListener(Gorewidow::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Gorewidow::onLivingDeath);
        com.vladosiwe.gorewidow.dev.DevServerHooks.register();
    }

    /** Kills made by a tamed Gorewidow, or by its rider, feed it experience. */
    private static void onLivingDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        net.minecraft.world.entity.Entity killer = event.getSource().getEntity();
        GorewidowEntity widow = null;
        if (killer instanceof GorewidowEntity w) {
            widow = w;
        } else if (killer != null && killer.getVehicle() instanceof GorewidowEntity w) {
            widow = w;
        }
        if (widow != null && widow.isTame() && widow != event.getEntity()) {
            widow.getCare().grantXp(Math.max(2.0F, event.getEntity().getMaxHealth() * 0.6F));
        }
        // Gorewidows remember who kills their kind
        if (event.getEntity() instanceof GorewidowEntity victim && killer instanceof net.minecraft.world.entity.player.Player player
                && !victim.level().isClientSide) {
            for (GorewidowEntity witness : victim.level().getEntitiesOfClass(GorewidowEntity.class, victim.getBoundingBox().inflate(32),
                    w -> w != victim && w.isAlive())) {
                witness.getMind().addTrust(player, -50.0F);
            }
        }
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
        // imprint bonus: an imprinted Gorewidow ridden by its imprinter hits harder and takes less
        if (event.getSource().getEntity() instanceof GorewidowEntity attacker
                && attacker.getNursery().isImprinter(attacker.getControllingPassenger())) {
            event.setAmount(event.getAmount() * (1.0F + attacker.getNursery().imprintBonus()));
        }
        if (event.getEntity() instanceof GorewidowEntity victim && victim.getNursery().isImprinter(victim.getControllingPassenger())) {
            event.setAmount(event.getAmount() * (1.0F - victim.getNursery().imprintBonus()));
        }
        if (event.getEntity().getVehicle() instanceof GorewidowEntity mount) {
            if (event.getSource().is(DamageTypes.IN_WALL) || event.getSource().is(DamageTypes.FALL)) {
                event.setCanceled(true);
            } else if (mount.hasHarness() && event.getSource().getEntity() != mount) {
                // a harness straps the rider in tight against the belly plates
                event.setAmount(event.getAmount() * 0.65F);
            }
        }
    }
}
