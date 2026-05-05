package net.fabricmc.resourcetracker.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

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
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "key.categories.misc"
        ));
    }

    public static KeyMapping registerToggleHudKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "key.categories.misc"
        ));
    }

    public static Item getItem(String itemId) {
        ResourceLocation id = parseItemId(itemId);
        return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
    }

    public static boolean isValidItemId(String itemId) {
        ResourceLocation id = parseItemId(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    public static String getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static ResourceLocation parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        try {
            return ResourceLocation.parse(itemId);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getItemName(Item item) {
        return item.getName().getString();
    }

    public static long getWindowHandle(Window window) {
        return window.getWindow();
    }

    public static void setTooltip(GuiGraphics context, Font font, Component text, int mouseX, int mouseY) {
        context.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }
}
