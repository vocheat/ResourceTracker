package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

/** Thin keyboard adapter; native navigation and narration stay with Screen. */
abstract class EditorInputScreen extends Screen {
    protected EditorInputScreen(Component title) { super(title); }
    protected boolean editorKeyPressed(int keyCode) { return false; }

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
