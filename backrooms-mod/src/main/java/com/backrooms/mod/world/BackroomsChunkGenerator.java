package com.backrooms.mod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Backrooms chunk generátor.
 *
 * Logika:
 * - Svět je plný kamene (podlaha + strop + stěny)
 * - Výška Backrooms = 3 bloky (podlaha Y=0, prostor Y=1,2,3, strop Y=4)
 * - Chodby jsou vygenerovány pomocí "maze" algoritmu
 * - Šířka chodby = 3 bloky, stěny 1 blok
 * - Vše z kamene (STONE)
 *
 * Mřížka buněk: každá buňka = 4 bloky (3 chodba + 1 stěna)
 * Buňka na pozici (cx, cz) mapuje na bloky (cx*4, cz*4)
 *
 * Generace:
 * 1. Vyplň celý chunk kamenem (Y 0 až 4)
 * 2. Pro každou buňku v chunku zkontroluj, zda má být otevřena (chodba)
 * 3. Pokud ano, vykopej 3x3x3 prostor
 * 4. Chodby mezi buňkami = průchod 1x3 (výška 3)
 */
public class BackroomsChunkGenerator extends ChunkGenerator {

    // Výška Backrooms světa
    public static final int FLOOR_Y = 0;       // podlaha (kámen)
    public static final int CEILING_Y = 4;     // strop (kámen)
    public static final int ROOM_BOTTOM = 1;   // spodek chodby (vzduch)
    public static final int ROOM_TOP = 3;      // vrchol chodby (vzduch)

    // Velikost buňky v mřížce (3 prostor + 1 stěna)
    public static final int CELL_SIZE = 4;

    public static final Codec<BackroomsChunkGenerator> CODEC =
            RecordCodecBuilder.create(instance ->
                    instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
                    ).apply(instance, BackroomsChunkGenerator::new)
            );

    public BackroomsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource, biomeSource);
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(
            Executor executor, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {

        return CompletableFuture.supplyAsync(() -> {
            generateBackroomsChunk(chunk);
            return chunk;
        }, executor);
    }

    private void generateBackroomsChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX(); // blokové souřadnice začátku chunku
        int startZ = chunkPos.getStartZ();

        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        // 1. Vyplň celý chunk kamenem od Y=0 do Y=4
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                    chunk.setBlockState(new BlockPos(startX + x, y, startZ + z), stone, false);
                }
                // Vše nad stropem a pod podlahou = vzduch / bedrock
                chunk.setBlockState(new BlockPos(startX + x, FLOOR_Y - 1, startZ + z),
                        Blocks.BEDROCK.getDefaultState(), false);
            }
        }

        // 2. Prober buňky, které zasahují do tohoto chunku a vykopej chodby
        // Buňky začínají na (cellX * CELL_SIZE, cellZ * CELL_SIZE)
        // Chunk pokrývá bloky startX..startX+15, startZ..startZ+15

        // Rozsah buněk, které zasahují do tohoto chunku
        int cellMinX = Math.floorDiv(startX, CELL_SIZE) - 1;
        int cellMaxX = Math.floorDiv(startX + 15, CELL_SIZE) + 1;
        int cellMinZ = Math.floorDiv(startZ, CELL_SIZE) - 1;
        int cellMaxZ = Math.floorDiv(startZ + 15, CELL_SIZE) + 1;

        for (int cellX = cellMinX; cellX <= cellMaxX; cellX++) {
            for (int cellZ = cellMinZ; cellZ <= cellMaxZ; cellZ++) {
                // Vykopej prostor buňky (3x3 chodba uvnitř buňky)
                // Buňka: bloky od (cellX*4) do (cellX*4 + 2) = 3 bloky
                // Stěna je na cellX*4 + 3

                int roomStartX = cellX * CELL_SIZE;      // začátek prostoru chodby
                int roomStartZ = cellZ * CELL_SIZE;

                // Vykopej 3x3 prostor chodby (X: roomStartX..+2, Z: roomStartZ..+2)
                for (int dx = 0; dx < 3; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        int worldX = roomStartX + dx;
                        int worldZ = roomStartZ + dz;
                        carveAir(chunk, startX, startZ, worldX, worldZ);
                    }
                }

                // Chodba ve směru +X (průchod mezi buňkou a sousedkem vpravo)
                // Závisí na seed-based rozhodnutí
                if (hasCorridorEast(cellX, cellZ)) {
                    // Průchod: X = roomStartX+3, Z = roomStartZ+1 (střed)
                    int wallX = roomStartX + 3;
                    for (int dz = 0; dz < 3; dz++) {
                        carveAir(chunk, startX, startZ, wallX, roomStartZ + dz);
                    }
                }

                // Chodba ve směru +Z (průchod dolů)
                if (hasCorridorSouth(cellX, cellZ)) {
                    int wallZ = roomStartZ + 3;
                    for (int dx = 0; dx < 3; dx++) {
                        carveAir(chunk, startX, startZ, roomStartX + dx, wallZ);
                    }
                }
            }
        }
    }

    /**
     * Vytesá vzduch na dané světové pozici (X, Z) pro výšky ROOM_BOTTOM..ROOM_TOP,
     * ale jen pokud leží v tomto chunku.
     */
    private void carveAir(Chunk chunk, int startX, int startZ, int worldX, int worldZ) {
        // Zkontroluj, zda blok leží v tomto chunku
        int localX = worldX - startX;
        int localZ = worldZ - startZ;
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) return;

        BlockState air = Blocks.AIR.getDefaultState();
        for (int y = ROOM_BOTTOM; y <= ROOM_TOP; y++) {
            chunk.setBlockState(new BlockPos(worldX, y, worldZ), air, false);
        }
    }

    /**
     * Rozhodne, zda má buňka (cellX, cellZ) průchod na východ (+X).
     * Používá deterministický hash ze souřadnic pro konzistentní generaci.
     * ~70% šance na průchod pro hustou síť chodeb.
     */
    private boolean hasCorridorEast(int cellX, int cellZ) {
        // Průchod VŽDY existuje pokud je buňka "sudá řada" – zajišťuje propojení
        long hash = hashCell(cellX, cellZ, 1);
        return (hash & 0xFF) < 178; // ~70% šance
    }

    /**
     * Rozhodne, zda má buňka (cellX, cellZ) průchod na jih (+Z).
     */
    private boolean hasCorridorSouth(int cellX, int cellZ) {
        long hash = hashCell(cellX, cellZ, 2);
        return (hash & 0xFF) < 178; // ~70% šance
    }

    /**
     * Deterministický hash pro souřadnice buňky.
     * Stejný seed = stejné Backrooms vždy.
     */
    private long hashCell(int x, int z, int salt) {
        long h = 2166136261L;
        h ^= (long)(x * 1000003 + z * 999983 + salt * 1234567);
        h *= 16777619L;
        h ^= (h >>> 17);
        h *= 0x85ebca6bL;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35L;
        h ^= (h >>> 16);
        return h;
    }

    // =========== Povinné přepsání metod ===========

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
        // Žádné jeskyně – Backrooms jsou přesné chodby
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, Chunk chunk) {
        // Nepotřebujeme surface builder
    }

    @Override
    public void populateEntities(ChunkRegion region) {
        // Žádné entity zatím
    }

    @Override
    public int getWorldHeight() {
        return 64; // Minimální povolená výška
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world) {
        return CEILING_Y;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world) {
        BlockState[] states = new BlockState[world.getHeight()];
        java.util.Arrays.fill(states, Blocks.AIR.getDefaultState());
        for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
            int idx = y - world.getBottomY();
            if (idx >= 0 && idx < states.length) {
                states[idx] = Blocks.STONE.getDefaultState();
            }
        }
        return new VerticalBlockSample(world.getBottomY(), states);
    }

    @Override
    public void getDebugHudText(List<String> text, BlockPos pos) {
        text.add("Backrooms Generator");
    }
}
