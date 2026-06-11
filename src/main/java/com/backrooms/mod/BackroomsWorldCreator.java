package com.backrooms.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.GeneratorType;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public class BackroomsWorldCreator {

    /**
     * Vytvoří nový singleplayer svět s Backrooms generací.
     * Používá flat world s vlastním chunk generátorem.
     */
    public static void createAndEnterWorld(MinecraftClient client) {
        // Otevřeme screen pro tvorbu světa s předvyplněnými hodnotami
        // Použijeme vlastní GeneratorType který použije BackroomsChunkGenerator
        client.setScreen(new BackroomsCreateWorldScreen(client.currentScreen));
    }
}
