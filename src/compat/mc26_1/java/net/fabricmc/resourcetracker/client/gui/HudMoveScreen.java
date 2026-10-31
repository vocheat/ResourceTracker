package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.client.render.HudRenderer;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.HudPlacement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Screen for repositioning HUD lists by dragging their rendered cards. */
public class HudMoveScreen extends Screen {
    private final Screen parent;
    private TrackerConfig.TrackingList draggingList;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean wasMouseDown;

    public HudMoveScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(width / 2 - 50, height - 30, 100, 20).primary().build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // Keep the game world visible while the player positions cards.
        context.fill(0, 0, width, height, 0x38000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        handleMouseInput(mouseX, mouseY);

        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;
            HudRenderModel model = HudRenderCache.preview(list, font, guiHeight);
            HudPlacement placement = placement(list, model, list.x, list.y);
            boolean hovered = placement.contains(mouseX, mouseY);

            VersionCompat.push(context);
            VersionCompat.translate(context, placement.x(), placement.y());
            VersionCompat.scale(context, placement.scale(), placement.scale());
            HudRenderer.render(context, font, model);
            int edge = list == draggingList ? UiTheme.ACCENT : hovered ? UiTheme.TEXT : UiTheme.BORDER;
            int edgeAlpha = list == draggingList || hovered ? edge : (edge & 0x00FFFFFF) | 0x90000000;
            outline(context, model.backgroundMinX - 1, model.backgroundMinY - 1,
                    model.backgroundMaxX + 1, model.backgroundMaxY + 1, edgeAlpha);
            VersionCompat.pop(context);
        }

        // Paint help after the cards so a card at the upper edge cannot cover it.
        Component hint = Component.translatable("gui.resourcetracker.move.drag_hint");
        int hintWidth = Math.min(Math.max(1, width - 16), font.width(hint) + 20);
        int hintX = (width - hintWidth) / 2;
        context.fill(hintX, 7, hintX + hintWidth, 27, UiTheme.PANEL);
        context.fill(hintX, 7, hintX + hintWidth, 8, UiTheme.BORDER);
        Component fittedHint = Component.literal(net.fabricmc.resourcetracker.util.RenderUtils.shortenText(
                font, hint.getString(), Math.max(1, hintWidth - 12)));
        context.centeredText(font, fittedHint, width / 2, 13, UiTheme.TEXT);

        if (draggingList != null) {
            context.centeredText(font, Component.literal(draggingList.x + ", " + draggingList.y),
                    width / 2, height - 43, UiTheme.MUTED);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        long handle = VersionCompat.getWindowHandle(minecraft.getWindow());
        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasMouseDown) {
            if (mouseY >= height - 34 && mouseX >= width / 2 - 55 && mouseX <= width / 2 + 55) {
                wasMouseDown = true;
                return;
            }
            int guiHeight = minecraft.getWindow().getGuiScaledHeight();
            for (int i = TrackerConfig.INSTANCE.lists.size() - 1; i >= 0; i--) {
                TrackerConfig.TrackingList list = TrackerConfig.INSTANCE.lists.get(i);
                if (!list.isVisible) continue;
                HudRenderModel model = HudRenderCache.preview(list, font, guiHeight);
                HudPlacement placement = placement(list, model, list.x, list.y);
                if (placement.contains(mouseX, mouseY)) {
                    draggingList = list;
                    dragOffsetX = mouseX - placement.x();
                    dragOffsetY = mouseY - placement.y();
                    break;
                }
            }
        } else if (down && wasMouseDown && draggingList != null) {
            HudRenderModel model = HudRenderCache.preview(draggingList, font, minecraft.getWindow().getGuiScaledHeight());
            HudPlacement placement = placement(draggingList, model, mouseX - dragOffsetX, mouseY - dragOffsetY);
            draggingList.x = Math.round(placement.x());
            draggingList.y = Math.round(placement.y());
        } else if (!down && wasMouseDown && draggingList != null) {
            draggingList = null;
            TrackerConfig.saveAllActiveContextLists();
        }
        wasMouseDown = down;
    }

    private HudPlacement placement(TrackerConfig.TrackingList list, HudRenderModel model, double x, double y) {
        return model.placement(x, y, TrackerConfig.clampScale(list.scale), width, height);
    }

    private static void outline(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top + 1, left + 1, bottom - 1, color);
        context.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    @Override
    public void onClose() {
        TrackerConfig.saveAllActiveContextLists();
        ResourceTrackerClient.invalidateTargetItemCache();
        if (minecraft != null) minecraft.setScreen(parent);
    }

}
