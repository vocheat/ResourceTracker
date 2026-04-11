package net.fabricmc.resourcetracker.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Compatibility layer for Minecraft 1.21.5–1.21.6.
 * DrawContext.getMatrices() returns net.minecraft.client.util.math.MatrixStack.
 * KeyBinding constructor takes a String category.
 */
public class VersionCompat {

    public static void push(DrawContext ctx) {
        ctx.getMatrices().push();
    }

    public static void pop(DrawContext ctx) {
        ctx.getMatrices().pop();
    }

    public static void translate(DrawContext ctx, float x, float y) {
        ctx.getMatrices().translate(x, y, 0f);
    }

    public static void scale(DrawContext ctx, float sx, float sy) {
        ctx.getMatrices().scale(sx, sy, 1f);
    }

    public static KeyBinding registerOpenKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resourcetracker.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.categories.misc"
        ));
    }
}
