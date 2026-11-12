package com.vladosiwe.gorewidow.worldgen;

import com.vladosiwe.gorewidow.Gorewidow;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Gorewidow.MOD_ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Gorewidow.MOD_ID);

    public static final DeferredHolder<StructureType<?>, StructureType<LairStructure>> LAIR = TYPES.register("lair", () -> () -> LairStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> LAIR_PIECE = PIECES.register("lair",
            () -> (StructurePieceType.ContextlessType) LairPiece::new);

    private ModStructures() {}
}
