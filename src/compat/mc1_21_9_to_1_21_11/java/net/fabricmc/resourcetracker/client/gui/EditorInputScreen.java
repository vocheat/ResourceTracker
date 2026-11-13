package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Thin keyboard adapter; native navigation and narration stay with Screen. */
abstract class EditorInputScreen extends Screen {
    protected EditorInputScreen(Component title) { super(title); }
    protected boolean editorKeyPressed(int keyCode) { return false; }

    @FunctionalInterface
    protected interface BackgroundFill {
        void draw(int left, int top, int right, int bottom, int color);
    }

    protected boolean drawEditorBackground(BackgroundFill fill) { return false; }

    @Override public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (!drawEditorBackground(context::fill)) super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return editorKeyPressed(event.key()) || super.keyPressed(event);
    }
}
