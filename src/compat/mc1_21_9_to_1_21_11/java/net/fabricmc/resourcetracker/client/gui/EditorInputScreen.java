package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Thin keyboard adapter; native navigation and narration stay with Screen. */
abstract class EditorInputScreen extends Screen {
    protected EditorInputScreen(Component title) { super(title); }
    protected boolean editorKeyPressed(int keyCode) { return false; }

    @Override public boolean keyPressed(KeyEvent event) {
        return editorKeyPressed(event.key()) || super.keyPressed(event);
    }
}
