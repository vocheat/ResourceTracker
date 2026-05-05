package net.fabricmc.resourcetracker.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class VersionCompat {
    public static void push(GuiGraphicsExtractor ctx) {
        ctx.pose().pushMatrix();
    }

    public static void pop(GuiGraphicsExtractor ctx) {
        ctx.pose().popMatrix();
    }

    public static void translate(GuiGraphicsExtractor ctx, float x, float y) {
        ctx.pose().translate(x, y);
    }

    public static void scale(GuiGraphicsExtractor ctx, float sx, float sy) {
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

    public static Item getItem(String itemId) {
        Identifier id = parseItemId(itemId);
        return id == null ? null : BuiltInRegistries.ITEM.getValue(id);
    }

    public static boolean isValidItemId(String itemId) {
        Identifier id = parseItemId(itemId);
        return id != null && BuiltInRegistries.ITEM.containsKey(id);
    }

    public static String getItemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private static Identifier parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return null;
        try {
            return Identifier.parse(itemId);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String getItemName(Item item) {
        return item.getName(new ItemStack(item)).getString();
    }

    public static long getWindowHandle(Window window) {
        return window.handle();
    }

    public static void setTooltip(GuiGraphicsExtractor context, Font font, Component text, int mouseX, int mouseY) {
        context.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }
}
