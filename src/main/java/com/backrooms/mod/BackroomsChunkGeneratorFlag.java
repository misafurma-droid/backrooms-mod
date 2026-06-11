package com.backrooms.mod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class BackroomsChunkGenerator extends ChunkGenerator {

    public static final int FLOOR_Y = 0;
    public static final int CEILING_Y = 4;
    public static final int AIR_BOTTOM = 1;
    public static final int AIR_TOP = 3;
    public static final int CELL_SIZE = 4;

    public static final Codec<BackroomsChunkGenerator> CODEC =
        RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
        ).apply(i, BackroomsChunkGenerator::new));

    public BackroomsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, Blender blender,
            NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        return CompletableFuture.supplyAsync(() -> {
            generateChunk(chunk);
            return chunk;
        }, executor);
    }

    private void generateChunk(Chunk chunk) {
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState bedrock = Blocks.BEDROCK.getDefaultState();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                chunk.setBlockState(new BlockPos(startX+x, FLOOR_Y-1, startZ+z), bedrock, false);
                for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                    chunk.setBlockState(new BlockPos(startX+x, y, startZ+z), stone, false);
                }
            }
        }

        int cellMinX = Math.floorDiv(startX, CELL_SIZE) - 1;
        int cellMaxX = Math.floorDiv(startX + 15, CELL_SIZE) + 1;
        int cellMinZ = Math.floorDiv(startZ, CELL_SIZE) - 1;
        int cellMaxZ = Math.floorDiv(startZ + 15, CELL_SIZE) + 1;

        for (int cx = cellMinX; cx <= cellMaxX; cx++) {
            for (int cz = cellMinZ; cz <= cellMaxZ; cz++) {
                int rx = cx * CELL_SIZE;
                int rz = cz * CELL_SIZE;
                for (int dx = 0; dx < 3; dx++)
                    for (int dz = 0; dz < 3; dz++)
                        carveAir(chunk, startX, startZ, rx+dx, rz+dz);
                if (corridorEast(cx, cz))
                    for (int dz = 0; dz < 3; dz++)
                        carveAir(chunk, startX, startZ, rx+3, rz+dz);
                if (corridorSouth(cx, cz))
                    for (int dx = 0; dx < 3; dx++)
                        carveAir(chunk, startX, startZ, rx+dx, rz+3);
            }
        }
    }

    private void carveAir(Chunk chunk, int startX, int startZ, int wx, int wz) {
        int lx = wx - startX, lz = wz - startZ;
        if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) return;
        for (int y = AIR_BOTTOM; y <= AIR_TOP; y++)
            chunk.setBlockState(new BlockPos(wx, y, wz), Blocks.AIR.getDefaultState(), false);
    }

    private boolean corridorEast(int cx, int cz) { return (hash(cx,cz,1)&0xFF)<178; }
    private boolean corridorSouth(int cx, int cz) { return (hash(cx,cz,2)&0xFF)<178; }

    private long hash(int x, int z, int s) {
        long h = 2166136261L^(long)(x*1000003+z*999983+s*1234567);
        h*=16777619L; h^=(h>>>17); h*=0x85ebca6bL; h^=(h>>>13);
        return h;
    }

    @Override
    public void carve(ChunkRegion r, long seed, NoiseConfig nc, BiomeAccess ba,
                      StructureAccessor sa, Chunk c, GenerationStep.Carver cs) {}
    @Override
    public void buildSurface(ChunkRegion r, StructureAccessor sa, NoiseConfig nc, Chunk c) {}
    @Override
    public void populateEntities(ChunkRegion r) {}
    @Override
    public int getWorldHeight() { return 64; }
    @Override
    public int getSeaLevel() { return 0; }
    @Override
    public int getMinimumY() { return -64; }
    @Override
    public int getHeight(int x, int z, Heightmap.Type t, HeightLimitView w, NoiseConfig nc) { return CEILING_Y; }
    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView w, NoiseConfig nc) {
        BlockState[] s = new BlockState[w.getHeight()];
        Arrays.fill(s, Blocks.AIR.getDefaultState());
        for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
            int i = y - w.getBottomY();
            if (i >= 0 && i < s.length) s[i] = Blocks.STONE.getDefaultState();
        }
        return new VerticalBlockSample(w.getBottomY(), s);
    }
    @Override
    public void getDebugHudText(List<String> t, NoiseConfig nc, BlockPos p) {
        t.add("Backrooms Generator");
    }
}
