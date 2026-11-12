package com.vladosiwe.gorewidow.worldgen;

import com.vladosiwe.gorewidow.Gorewidow;
import com.vladosiwe.gorewidow.entity.GorewidowEntity;
import com.vladosiwe.gorewidow.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Carves the lair procedurally from a seed: a winding, descending tunnel from the entrance and a
 * lumpy chamber at the bottom. Every chunk the piece touches re-derives the same shape and writes only
 * its own column, so the lair is seamless across chunk borders.
 */
public class LairPiece extends StructurePiece {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Gorewidow.id("chests/lair"));
    private static final int TUNNEL_STEPS = 26;
    private static final double CHAMBER_RX = 7.5;
    private static final double CHAMBER_RY = 4.5;
    private static final double CHAMBER_RZ = 7.5;

    private final BlockPos entrance;
    private final Direction into;
    private final long seed;
    private final List<Vec3> path = new ArrayList<>();
    private Vec3 chamber = Vec3.ZERO;

    public LairPiece(BlockPos entrance, Direction into, long seed) {
        super(ModStructures.LAIR_PIECE.get(), 0, bounds(entrance));
        this.entrance = entrance;
        this.into = into;
        this.seed = seed;
        this.buildShape();
    }

    public LairPiece(CompoundTag tag) {
        super(ModStructures.LAIR_PIECE.get(), tag);
        this.entrance = new BlockPos(tag.getInt("EX"), tag.getInt("EY"), tag.getInt("EZ"));
        this.into = Direction.from2DDataValue(tag.getInt("Dir"));
        this.seed = tag.getLong("Seed");
        this.buildShape();
    }

    private static BoundingBox bounds(BlockPos e) {
        return new BoundingBox(e.getX() - 40, e.getY() - 26, e.getZ() - 40, e.getX() + 40, e.getY() + 8, e.getZ() + 40);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("EX", this.entrance.getX());
        tag.putInt("EY", this.entrance.getY());
        tag.putInt("EZ", this.entrance.getZ());
        tag.putInt("Dir", this.into.get2DDataValue());
        tag.putLong("Seed", this.seed);
    }

    /** The tunnel centre line: in from the slope, wandering sideways, descending, then the chamber. */
    private void buildShape() {
        RandomSource random = RandomSource.create(this.seed);
        Vec3 forward = Vec3.atLowerCornerOf(this.into.getNormal());
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        double wander = 0;
        double drift = (random.nextDouble() - 0.5) * 0.6;
        Vec3 p = Vec3.atCenterOf(this.entrance).add(0, 0.5, 0).subtract(forward.scale(2));
        this.path.clear();
        for (int i = 0; i < TUNNEL_STEPS; i++) {
            this.path.add(p);
            drift = Mth.clamp(drift + (random.nextDouble() - 0.5) * 0.35, -0.6, 0.6);
            wander += drift;
            double down = i < 4 ? 0.0 : 0.62;
            p = p.add(forward).add(side.scale(drift)).add(0, -down, 0);
        }
        this.chamber = p.add(forward.scale(CHAMBER_RX - 1)).add(0, -1.5, 0);
    }

    /** Smooth deterministic noise in [-1, 1] for lumpy walls. */
    private double noise(int x, int y, int z) {
        long h = this.seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return ((h & 0xFFFF) / 32767.5) - 1.0;
    }

    private boolean inChamber(double x, double y, double z) {
        double dx = (x - this.chamber.x) / CHAMBER_RX;
        double dy = (y - this.chamber.y) / CHAMBER_RY;
        double dz = (z - this.chamber.z) / CHAMBER_RZ;
        return dx * dx + dy * dy * (y < this.chamber.y ? 2.2 : 1.0) + dz * dz < 1.0;
    }

    private boolean inTunnel(double x, double y, double z) {
        for (Vec3 c : this.path) {
            double dx = x - c.x;
            double dy = (y - c.y) / 1.25;
            double dz = z - c.z;
            if (dx * dx + dy * dy + dz * dz < 2.7 * 2.7) {
                return true;
            }
        }
        return false;
    }

    private boolean isCave(int x, int y, int z) {
        double n = this.noise(x >> 1, y >> 1, z >> 1) * 0.35;
        double cx = x + 0.5 + n;
        double cy = y + 0.5 + n;
        double cz = z + 0.5 - n;
        return this.inChamber(cx, cy, cz) || this.inTunnel(cx, cy, cz);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!this.boundingBox.intersects(box)) {
            return;
        }
        BoundingBox area = new BoundingBox(Math.max(this.boundingBox.minX(), box.minX()), Math.max(this.boundingBox.minY(), box.minY()),
                Math.max(this.boundingBox.minZ(), box.minZ()), Math.min(this.boundingBox.maxX(), box.maxX()),
                Math.min(this.boundingBox.maxY(), box.maxY()), Math.min(this.boundingBox.maxZ(), box.maxZ()));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // carve
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    if (!this.isCave(x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState existing = level.getBlockState(pos);
                    if (existing.is(Blocks.BEDROCK) || !existing.getFluidState().isEmpty()) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.CAVE_AIR.defaultBlockState(), 2);
                }
            }
        }
        // decorate the surfaces that are now exposed
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                for (int y = area.minY(); y <= area.maxY(); y++) {
                    if (!this.isCave(x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    this.decorate(level, pos.immutable(), box);
                }
            }
        }
        // the den's furniture and its occupant
        BlockPos centre = BlockPos.containing(this.chamber.x, this.floorY(), this.chamber.z);
        if (box.isInside(centre)) {
            this.furnish(level, centre, box);
            this.spawnOwner(level, centre);
        }
    }

    private double floorY() {
        return this.chamber.y - CHAMBER_RY / Math.sqrt(2.2) + 0.6;
    }

    private void decorate(WorldGenLevel level, BlockPos pos, BoundingBox box) {
        long h = (long) pos.getX() * 341873128712L + (long) pos.getY() * 132897987541L + (long) pos.getZ() * 7L + this.seed;
        RandomSource r = RandomSource.create(h);
        BlockPos below = pos.below();
        BlockPos above = pos.above();
        boolean chamber = this.inChamber(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double entranceDist = Math.sqrt(pos.distSqr(this.entrance));
        // floor: bones, gravel and rot
        if (box.isInside(below) && !this.isCave(below.getX(), below.getY(), below.getZ()) && level.getBlockState(below).isSolidRender(level, below)) {
            float f = r.nextFloat();
            if (chamber && f < 0.10F) {
                level.setBlock(below, Blocks.BONE_BLOCK.defaultBlockState(), 2);
            } else if (f < 0.30F && entranceDist > 4) {
                level.setBlock(below, (r.nextBoolean() ? Blocks.GRAVEL : Blocks.COARSE_DIRT).defaultBlockState(), 2);
            } else if (chamber && f < 0.36F) {
                level.setBlock(below, Blocks.PACKED_MUD.defaultBlockState(), 2);
            }
            if (chamber && r.nextFloat() < 0.03F) {
                level.setBlock(pos, Blocks.SKELETON_SKULL.defaultBlockState().setValue(SkullBlock.ROTATION, r.nextInt(16)), 2);
                return;
            }
        }
        // webs line the walls and ceiling, thicker deeper in
        boolean nearWall = false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (box.isInside(n) && !this.isCave(n.getX(), n.getY(), n.getZ())) {
                nearWall = true;
                break;
            }
        }
        if (nearWall && level.getBlockState(pos).isAir()) {
            float webChance = chamber ? 0.11F : entranceDist < 6 ? 0.18F : 0.05F;
            if (r.nextFloat() < webChance) {
                level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 2);
            } else if (chamber && box.isInside(above) && !this.isCave(above.getX(), above.getY(), above.getZ()) && r.nextFloat() < 0.04F) {
                // a silk cocoon hanging from the ceiling
                level.setBlock(pos, Blocks.WHITE_WOOL.defaultBlockState(), 2);
                BlockPos lower = pos.below();
                if (box.isInside(lower) && level.getBlockState(lower).isAir()) {
                    level.setBlock(lower, Blocks.COBWEB.defaultBlockState(), 2);
                }
            }
        }
    }

    private void furnish(WorldGenLevel level, BlockPos centre, BoundingBox box) {
        RandomSource r = RandomSource.create(this.seed ^ 0x5EEDL);
        BlockPos chest = centre.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
        if (box.isInside(chest) && level.getBlockState(chest).isAir() && level.getBlockState(chest.below()).isSolidRender(level, chest.below())) {
            level.setBlock(chest, Blocks.BARREL.defaultBlockState(), 2);
            net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, r, chest, LOOT);
        }
    }

    private void spawnOwner(WorldGenLevel level, BlockPos centre) {
        GorewidowEntity widow = ModEntities.GOREWIDOW.get().create(level.getLevel());
        if (widow == null) {
            return;
        }
        widow.moveTo(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        widow.finalizeSpawn(level, level.getCurrentDifficultyAt(centre), MobSpawnType.STRUCTURE, null);
        widow.setLair(centre, this.entrance);
        widow.setPersistenceRequired();
        level.addFreshEntityWithPassengers(widow);
    }
}
