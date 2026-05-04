package net.fabricmc.resourcetracker.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Compatibility layer for Minecraft 26.1+.
 * Mojang-native names (no Yarn). Java 25. No obfuscation in game JAR.
 */
public class VersionCompat {

    public static void push(GuiGraphics ctx) {
        ctx.pose().pushMatrix();
    }

    public static void pop(GuiGraphics ctx) {
        ctx.pose().popMatrix();
    }

    public static void translate(GuiGraphics ctx, float x, float y) {
        ctx.pose().translate(x, y);
    }

    public static void scale(GuiGraphics ctx, float sx, float sy) {
        ctx.pose().scale(sx, sy);
    }

    public static KeyMapping registerOpenKey() {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.resourcetracker.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KeyMapping.Category.MISC
        ));
    }

    public static KeyMapping registerToggleHudKey() {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.resourcetracker.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KeyMapping.Category.MISC
        ));
    }
}
