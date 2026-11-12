package com.vladosiwe.gorewidow.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A Gorewidow lair: a tunnel bored into a steep mountainside that winds down into a deep, dark
 * chamber where the creature sleeps by day. Placed only where the ground rises sharply, so the mouth
 * opens out of a slope and the chamber lies under solid rock.
 */
public class LairStructure extends Structure {
    public static final MapCodec<LairStructure> CODEC = simpleCodec(LairStructure::new);

    public LairStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();
        int base = height(context, cx, cz);
        if (base < context.chunkGenerator().getSeaLevel() + 4) {
            return Optional.empty();
        }
        Direction best = null;
        int bestRise = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int rise = Math.min(height(context, cx + dir.getStepX() * 14, cz + dir.getStepZ() * 14),
                    height(context, cx + dir.getStepX() * 24, cz + dir.getStepZ() * 24)) - base;
            if (rise > bestRise) {
                bestRise = rise;
                best = dir;
            }
        }
        if (best == null || bestRise < 12) {
            return Optional.empty();
        }
        BlockPos entrance = new BlockPos(cx, base, cz);
        Direction into = best;
        long seed = context.random().nextLong();
        return Optional.of(new GenerationStub(entrance, builder -> builder.addPiece(new LairPiece(entrance, into, seed))));
    }

    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.LAIR.get();
    }
}
