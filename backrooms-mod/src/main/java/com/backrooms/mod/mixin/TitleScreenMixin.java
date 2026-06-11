package com.backrooms.mod.mixin;

import com.backrooms.mod.BackroomsWorldCreator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addBackroomsButton(CallbackInfo ci) {
        // Najdi existující tlačítka a posuň je
        // Singleplayer je typicky na y=100, Multiplayer na y=124
        // Vložíme Backrooms button mezi ně na y=124 a posuneme Multiplayer níž

        // Posuneme všechna existující tlačítka pod y=100 o 24px dolů
        this.children().forEach(element -> {
            if (element instanceof ButtonWidget btn) {
                // Singleplayer bývá na y ~100, posuneme vše od y=112 dolů
                if (btn.getY() >= 112) {
                    btn.setY(btn.getY() + 24);
                }
            }
        });

        // Přidáme Backrooms tlačítko
        int buttonWidth = 200;
        int centerX = this.width / 2 - buttonWidth / 2;

        this.addDrawableChild(new ButtonWidget(
                centerX, 124,
                buttonWidth, 20,
                Text.literal("Backrooms"),
                button -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    BackroomsWorldCreator.createAndEnterWorld(client);
                }
        ));
    }
}
