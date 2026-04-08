/*
 * MIT License
 *
 * Copyright (c) 2026 vocheat
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Screen that allows users to reposition HUD elements via drag-and-drop.
 * <p>
 * This screen renders the actual tracking lists (applying their specific scales)
 * and handles mouse interactions to update their coordinates in real-time.
 * </p>
 *
 * @author vocheat
 */
public class HudMoveScreen extends Screen {
    private final Screen parent;

    private TrackerConfig.TrackingList draggingList = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean wasMouseDown = false;

    public HudMoveScreen(Screen parent) {
        super(Text.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.done"), b -> {
            TrackerConfig.save();
            client.setScreen(parent);
        }).dimensions(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render a darkened background
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.resourcetracker.move.drag_hint"), width / 2, 10, 0xFFFFFFFF);

        handleMouseInput(mouseX, mouseY);

        // Render all visible tracking lists
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;
            renderScaledList(context, list);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderScaledList(DrawContext context, TrackerConfig.TrackingList list) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float) list.x, (float) list.y);
        context.getMatrices().scale(list.scale, list.scale);

        BoxSize baseSize = calculateBaseSize(list);
        int borderColor = (list == draggingList) ? 0xFF00FF00 : 0xFFFFFFFF;
        int padding = 4;

        renderBorder(context, -padding - 2, -padding - 2, baseSize.width + 4, baseSize.height + 4, borderColor);
        context.fill(-padding, -padding, baseSize.width - padding, baseSize.height - padding, list.backgroundColor);
        context.drawTextWithShadow(textRenderer, list.name, 0, 0, list.nameColor);

        int headerHeight = 14;
        int itemRowHeight = 24;
        int currentY = headerHeight;
        int iconOffset = list.showIcons ? 20 : 2;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) {
                currentY += itemRowHeight;
                continue;
            }

            if (list.showIcons) {
                context.drawItem(trackedItem.getStack(), 0, currentY + 1);
            }

            if (trackedItem.cachedCount == -1 && client.player != null) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, trackedItem.getItem());
            }
            int currentCount = trackedItem.cachedCount;
            String countText = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);

            context.drawTextWithShadow(textRenderer, trackedItem.getDisplayName(), iconOffset, currentY, list.textColor);

            int countColor = (currentCount >= trackedItem.targetCount) ? 0xFF55FF55 : (list.textColor & 0xAAFFFFFF);
            context.drawTextWithShadow(textRenderer, countText, iconOffset, currentY + 10, countColor);

            currentY += itemRowHeight;
        }

        context.getMatrices().popMatrix();
    }

    private BoxSize calculateBaseSize(TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemRowHeight = 24;
        int maxTextWidth = textRenderer.getWidth(list.name);
        int iconOffset = list.showIcons ? 20 : 2;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;

            if (trackedItem.cachedCount == -1 && client.player != null) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, trackedItem.getItem());
            }
            String countText = RenderUtils.getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining);

            int w = iconOffset + Math.max(textRenderer.getWidth(trackedItem.getDisplayName()), textRenderer.getWidth(countText));
            if (w > maxTextWidth) maxTextWidth = w;
        }

        int width = maxTextWidth + (padding * 2);
        int height = headerHeight + (list.items.size() * itemRowHeight) + padding;
        return new BoxSize(width, height);
    }

    private void renderBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * Handles mouse input to detect clicks on lists and manage dragging logic.
     */
    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = client.getWindow().getHandle();
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            // Mouse Clicked: Check if any list was hit
            for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
                if (!list.isVisible) continue;

                BoxSize baseSize = calculateBaseSize(list);

                // Apply scale to the hitbox dimensions
                int scaledWidth = (int) (baseSize.width * list.scale);
                int scaledHeight = (int) (baseSize.height * list.scale);

                if (mouseX >= list.x && mouseX <= list.x + scaledWidth &&
                        mouseY >= list.y && mouseY <= list.y + scaledHeight) {
                    this.draggingList = list;
                    this.dragOffsetX = mouseX - list.x;
                    this.dragOffsetY = mouseY - list.y;
                    break;
                }
            }
        } else if (isMouseDown && wasMouseDown) {
            // Mouse Dragging: Update position
            if (draggingList != null) {
                draggingList.x = mouseX - dragOffsetX;
                draggingList.y = mouseY - dragOffsetY;
            }
        } else if (!isMouseDown && wasMouseDown) {
            // Mouse Released: Stop dragging and save
            if (draggingList != null) {
                draggingList = null;
                TrackerConfig.save();
            }
        }
        this.wasMouseDown = isMouseDown;
    }

    /**
     * Helper record to store calculated dimensions.
     */
    private record BoxSize(int width, int height) {}
}