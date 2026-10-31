package net.fabricmc.resourcetracker.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

public class VersionCompat {
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("resourcetracker", "main"));

    public static void showChatMessage(net.minecraft.client.Minecraft client, Component message) {
        if (client.player != null) client.player.displayClientMessage(message, false);
    }

    public static void showCompletionMessage(net.minecraft.client.Minecraft client, Component message) {
        client.gui.setOverlayMessage(message, false);
    }

    public static boolean isUndoPressed(net.minecraft.client.Minecraft client) {
        long window = getWindowHandle(client.getWindow());
        return (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS)
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_Z) == GLFW.GLFW_PRESS;
    }

    /** Screen navigation across the client GUI API move in 26.3. */
    public static void setScreen(net.minecraft.client.Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
        client.setScreen(screen);
    }

    public static net.minecraft.client.gui.screens.Screen currentScreen(net.minecraft.client.Minecraft client) {
        return client.screen;
    }

    private static final Identifier ICONS_ATLAS = Identifier.fromNamespaceAndPath(
            PngIcons.ATLAS_NAMESPACE,
            PngIcons.ATLAS_PATH
    );

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

    public static void drawPngIcon(GuiGraphics context, PngIcons.Icon icon, int x, int y, int color) {
        if (((color >>> 24) & 0xFF) == 0) return;
        context.blit(
                RenderPipelines.GUI_TEXTURED,
                ICONS_ATLAS,
                x,
                y,
                icon.u(),
                icon.v(),
                icon.width(),
                icon.height(),
                PngIcons.ATLAS_SIZE,
                PngIcons.ATLAS_SIZE,
                color
        );
    }

    public static KeyMapping registerOpenKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KEY_CATEGORY
        ));
    }

    public static KeyMapping registerQuickAddKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.quick_add",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KEY_CATEGORY
        ));
    }

    public static KeyMapping registerAreaSelectionKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.select_area",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                KEY_CATEGORY
        ));
    }

    /** Screen events respect the Controls binding while vanilla inventory input is active. */
    public static void registerQuickAddInput(KeyMapping key) {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
            net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register(
                    (current, event) -> !key.matches(event)
                            || !net.fabricmc.resourcetracker.client.QuickAddController.tryOpen(current));
            net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).register(
                    (current, event) -> !key.matchesMouse(event)
                            || !net.fabricmc.resourcetracker.client.QuickAddController.tryOpen(current));
        });
    }

    public static KeyMapping registerToggleHudKey() {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.resourcetracker.toggle_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));
    }

    public static void registerClientResourceReloadListener(Runnable callback) {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.fromNamespaceAndPath("resourcetracker", "hud_localization");
            }

            @Override
            public void onResourceManagerReload(ResourceManager resourceManager) {
                callback.run();
            }
        });
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
        return item.getName().getString();
    }

    public static long getWindowHandle(Window window) {
        return window.handle();
    }

    public static void setTooltip(GuiGraphics context, Font font, Component text, int mouseX, int mouseY) {
        context.setTooltipForNextFrame(font, text, mouseX, mouseY);
    }
}
