package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Thin keyboard adapter; native navigation and narration stay with Screen. */
abstract class EditorInputScreen extends Screen {
    protected EditorInputScreen(Component title) { super(title); }
    protected boolean editorKeyPressed(int keyCode) { return false; }

    @FunctionalInterface
    protected interface BackgroundFill {
        void draw(int left, int top, int right, int bottom, int color);
    }

    protected boolean drawEditorBackground(BackgroundFill fill) { return false; }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (!drawEditorBackground(context::fill)) super.extractBackground(context, mouseX, mouseY, delta);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        // SDL supplies physical scancodes; the shared editor uses legacy command codes.
        int command = switch (event.key()) {
            case InputConstants.KEY_SPACE -> 32;
            case InputConstants.KEY_RETURN -> 257;
            case InputConstants.KEY_NUMPADENTER -> 335;
            default -> -1;
        };
        return (command != -1 && editorKeyPressed(command)) || super.keyPressed(event);
    }
}
