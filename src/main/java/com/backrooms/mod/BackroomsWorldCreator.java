package com.backrooms.mod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

@Environment(EnvType.CLIENT)
public class BackroomsWorldCreator {
    public static void createAndEnterWorld(MinecraftClient client) {
        client.setScreen(new BackroomsCreateWorldScreen(client.currentScreen));
    }
}
