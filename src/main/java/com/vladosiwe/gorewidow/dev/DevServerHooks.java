package com.vladosiwe.gorewidow.dev;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Development-only helpers (enabled by system properties); never active in normal play. */
public final class DevServerHooks {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Structure> LAIR = ResourceKey.create(Registries.STRUCTURE, Gorewidow.id("lair"));

    private DevServerHooks() {}

    public static void register() {
        if (Boolean.getBoolean("gorewidow.autostop")) {
            NeoForge.EVENT_BUS.addListener(DevServerHooks::onStarted);
        }
    }

    /** Finds the nearest lair like /locate does. */
    @Nullable
    public static BlockPos locateLair(ServerLevel level, BlockPos from) {
        Holder<Structure> lair = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(LAIR);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(lair), from, 100, false);
        return found == null ? null : found.getFirst();
    }

    private static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        BlockPos found = locateLair(server.overworld(), server.overworld().getSharedSpawnPos());
        LOGGER.info("gorewidow-dev: nearest lair = {}", found);
        server.halt(false);
    }
}
