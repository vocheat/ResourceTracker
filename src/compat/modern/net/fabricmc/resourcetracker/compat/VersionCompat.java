package net.fabricmc.resourcetracker.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Compatibility layer for Minecraft 1.21.7–1.21.11.
 * DrawContext.getMatrices() returns org.joml.Matrix3x2fStack.
 * KeyBinding constructor takes KeyBinding.Category enum.
 */
public class VersionCompat {

    public static void push(DrawContext ctx) {
        ctx.getMatrices().pushMatrix();
    }

    public static void pop(DrawContext ctx) {
        ctx.getMatrices().popMatrix();
    }

    public static void translate(DrawContext ctx, float x, float y) {
        ctx.getMatrices().translate(x, y);
    }

    public static void scale(DrawContext ctx, float sx, float sy) {
        ctx.getMatrices().scale(sx, sy);
    }

    public static KeyBinding registerOpenKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resourcetracker.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyBinding.Category.MISC
        ));
    }
}
