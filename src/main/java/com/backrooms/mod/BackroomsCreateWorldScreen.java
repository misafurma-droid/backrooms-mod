package com.backrooms.mod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;

import java.util.Random;

public class BackroomsCreateWorldScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget worldNameField;

    public BackroomsCreateWorldScreen(Screen parent) {
        super(Text.literal("Backrooms - Vytvoř svět"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Pole pro název světa
        this.worldNameField = new TextFieldWidget(
                this.textRenderer,
                centerX - 100, centerY - 40,
                200, 20,
                Text.literal("Název světa")
        );
        this.worldNameField.setText("Backrooms_" + new Random().nextInt(9999));
        this.addSelectableChild(this.worldNameField);

        // Tlačítko "Vstoupit do Backrooms"
        this.addDrawableChild(new ButtonWidget(
                centerX - 100, centerY,
                200, 20,
                Text.literal("Vstoupit do Backrooms"),
                button -> this.createWorld()
        ));

        // Tlačítko zpět
        this.addDrawableChild(new ButtonWidget(
                centerX - 100, centerY + 26,
                200, 20,
                Text.literal("Zpět"),
                button -> this.client.setScreen(this.parent)
        ));
    }

    private void createWorld() {
        // Spustíme tvorbu světa na pozadí
        // Seed je fixní pro konzistentní Backrooms generaci
        String worldName = this.worldNameField.getText().trim();
        if (worldName.isEmpty()) worldName = "Backrooms";

        // Nastavíme flag aby BackroomsChunkGenerator věděl že má generovat
        BackroomsChunkGeneratorFlag.ENABLED = true;

        // Otevřeme standardní CreateWorldScreen s předvyplněnými hodnotami
        // a přesměrujeme generaci přes mixin
        CreateWorldScreen.create(this.client, this.parent);
    }

    @Override
    public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 20, 0xFFFF00);
        drawCenteredText(matrices, this.textRenderer,
                Text.literal("Generace: Backrooms chodbičky z kamene, výška 3 bloky"),
                this.width / 2, 50, 0xAAAAAA);
        drawTextWithShadow(matrices, this.textRenderer, Text.literal("Název světa:"), this.width / 2 - 100, this.height / 2 - 55, 0xFFFFFF);
        this.worldNameField.render(matrices, mouseX, mouseY, delta);
        super.render(matrices, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.worldNameField.isFocused()) {
            return this.worldNameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.worldNameField.isFocused()) {
            return this.worldNameField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }
}
