package net.fabricmc.resourcetracker.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.client.render.HudRenderer;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Screen for repositioning HUD lists by dragging their rendered cards. */
public class HudMoveScreen extends Screen {
    private final Screen parent;
    private TrackerConfig.TrackingList draggingList;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean wasMouseDown;
    private boolean mouseDown;

    public HudMoveScreen(Screen parent) {
        super(Component.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        mouseDown = false;
        wasMouseDown = false;
        draggingList = null;
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
        context.fill(width / 2 - 118, 7, width / 2 + 118, 27, 0xD020231F);
        context.fill(width / 2 - 118, 7, width / 2 + 118, 8, UiTheme.BORDER);
        context.centeredText(font, Component.translatable("gui.resourcetracker.move.drag_hint"), width / 2, 13, UiTheme.TEXT);

        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;
            HudRenderModel model = HudRenderCache.preview(list, font, guiHeight);
            float scale = TrackerConfig.clampScale(list.scale);
            Bounds bounds = bounds(model, scale);
            boolean hovered = contains(list, bounds, mouseX, mouseY);

            VersionCompat.push(context);
            VersionCompat.translate(context, (float) list.x, (float) list.y);
            VersionCompat.scale(context, scale, scale);
            HudRenderer.render(context, font, model);
            int edge = list == draggingList ? UiTheme.ACCENT : hovered ? UiTheme.TEXT : UiTheme.BORDER;
            int edgeAlpha = list == draggingList || hovered ? edge : (edge & 0x00FFFFFF) | 0x90000000;
            outline(context, model.backgroundMinX - 1, model.backgroundMinY - 1,
                    model.backgroundMaxX + 1, model.backgroundMaxY + 1, edgeAlpha);
            VersionCompat.pop(context);
        }

        if (draggingList != null) {
            context.centeredText(font, Component.literal(draggingList.x + ", " + draggingList.y),
                    width / 2, height - 43, UiTheme.MUTED);
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void handleMouseInput(int mouseX, int mouseY) {
        boolean down = mouseDown;
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
                if (contains(list, bounds(model, TrackerConfig.clampScale(list.scale)), mouseX, mouseY)) {
                    draggingList = list;
                    dragOffsetX = mouseX - list.x;
                    dragOffsetY = mouseY - list.y;
                    break;
                }
            }
        } else if (down && wasMouseDown && draggingList != null) {
            float scale = TrackerConfig.clampScale(draggingList.scale);
            HudRenderModel model = HudRenderCache.preview(draggingList, font, minecraft.getWindow().getGuiScaledHeight());
            Bounds b = bounds(model, scale);
            int x = mouseX - dragOffsetX;
            int y = mouseY - dragOffsetY;
            draggingList.x = clamp(x, (int) Math.ceil(-b.left), (int) Math.floor(width - b.right));
            draggingList.y = clamp(y, (int) Math.ceil(-b.top), (int) Math.floor(height - b.bottom));
        } else if (!down && wasMouseDown && draggingList != null) {
            draggingList = null;
            TrackerConfig.saveAllActiveContextLists();
        }
        wasMouseDown = down;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        mouseDown = true;
        handleMouseInput((int) event.x(), (int) event.y());
        return draggingList != null;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            mouseDown = false;
            handleMouseInput((int) event.x(), (int) event.y());
        }
        return super.mouseReleased(event);
    }

    private static Bounds bounds(HudRenderModel model, float scale) {
        return new Bounds((model.backgroundMinX - 1) * scale, (model.backgroundMinY - 1) * scale,
                (model.backgroundMaxX + 1) * scale, (model.backgroundMaxY + 1) * scale);
    }

    private static boolean contains(TrackerConfig.TrackingList list, Bounds b, int x, int y) {
        return x >= list.x + b.left && x <= list.x + b.right && y >= list.y + b.top && y <= list.y + b.bottom;
    }

    private static void outline(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int color) {
        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom - 1, right, bottom, color);
        context.fill(left, top + 1, left + 1, bottom - 1, color);
        context.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private static int clamp(int value, int min, int max) {
        return max < min ? min : Math.max(min, Math.min(value, max));
    }

    @Override
    public void onClose() {
        TrackerConfig.saveAllActiveContextLists();
        ResourceTrackerClient.invalidateTargetItemCache();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private record Bounds(double left, double top, double right, double bottom) {}
}
