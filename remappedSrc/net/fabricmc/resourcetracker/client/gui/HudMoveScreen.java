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

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
        super(Component.translatable("gui.resourcetracker.move.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.done"), b -> {
            TrackerConfig.save();
            minecraft.setScreen(parent);
        }).bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // No-op: we draw our own semi-transparent background in render()
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // Render a darkened background
        context.fill(0, 0, this.width, this.height, 0xA0000000);

        context.drawCenteredString(font, Component.translatable("gui.resourcetracker.move.drag_hint"), width / 2, 10, 0xFFFFFFFF);

        handleMouseInput(mouseX, mouseY);

        // Render all visible tracking lists
        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;
            renderScaledList(context, list);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderScaledList(GuiGraphics context, TrackerConfig.TrackingList list) {
        VersionCompat.push(context);
        VersionCompat.translate(context, (float) list.x, (float) list.y);
        VersionCompat.scale(context, list.scale, list.scale);

        BoxSize baseSize = calculateBaseSize(list);
        int borderColor = (list == draggingList) ? 0xFF00FF00 : 0xFFFFFFFF;
        int padding = 4;

        renderBorder(context, -padding - 1, -padding - 1, baseSize.width + 2, baseSize.height + 2, borderColor);
        context.fill(-padding, -padding, baseSize.width - padding, baseSize.height - padding, list.backgroundColor);
        context.drawString(font, list.name, 0, 0, list.nameColor);

        int headerHeight = 14;
        int itemRowHeight = list.showIcons ? 24 : 12;
        int currentY = headerHeight;
        int iconOffset = list.showIcons ? 20 : 2;

        int validItems = 0;
        for (TrackerConfig.TrackedItem ti : list.items) {
            if (ti.isValid()) validItems++;
        }

        int[] layout = RenderUtils.calculateColumnLayout(list, validItems,
                minecraft.getWindow().getGuiScaledHeight(), headerHeight, padding, itemRowHeight);
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int colWidth = (baseSize.width - (padding * (numColumns - 1))) / numColumns;
        if (numColumns == 1) colWidth = baseSize.width;

        int currentColumn = 0;
        int columnOffsetX = 0;
        int drawn = 0;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;

            if (drawn > 0 && drawn % itemsPerColumn == 0) {
                currentColumn++;
                columnOffsetX = currentColumn * (colWidth + padding);
                currentY = headerHeight;
            }

            if (list.showIcons) {
                context.renderItem(trackedItem.getStack(), columnOffsetX, currentY + 1);
            }

            int currentCount = trackedItem.cachedCount;
            boolean isDone = currentCount >= trackedItem.targetCount;
            
            int itemColor = list.textColor;
            int countColor = isDone ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF);

            if (list.showIcons) {
                int availableWidth = colWidth - iconOffset;
                String itemName = RenderUtils.shortenText(font, trackedItem.getDisplayName(), availableWidth);
                context.drawString(font, itemName, columnOffsetX + iconOffset, currentY, itemColor);

                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
                context.drawString(font, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);
            } else {
                String namePart = trackedItem.getDisplayName() + ": ";
                int nw = font.width(namePart);
                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
                
                context.drawString(font, namePart, columnOffsetX + iconOffset, currentY + 2, itemColor);
                context.drawString(font, countLine, columnOffsetX + iconOffset + nw, currentY + 2, countColor);
            }

            currentY += itemRowHeight;
            drawn++;
        }

        VersionCompat.pop(context);
    }

    private BoxSize calculateBaseSize(TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemRowHeight = list.showIcons ? 24 : 12;
        int maxTextWidth = font.width(list.name);
        int iconOffset = list.showIcons ? 20 : 2;

        int validItems = 0;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;
            validItems++;

            String countText = RenderUtils.getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining);
            
            int entryWidth;
            if (list.showIcons) {
                int nameWidth = font.width(trackedItem.getDisplayName());
                int countWidth = font.width(countText);
                entryWidth = iconOffset + Math.max(nameWidth, countWidth);
            } else {
                String combined = trackedItem.getDisplayName() + ": " + countText;
                entryWidth = iconOffset + font.width(combined);
            }

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
        }

        int columnWidth = maxTextWidth + (padding * 2);

        int[] layout = RenderUtils.calculateColumnLayout(list, validItems,
                minecraft.getWindow().getGuiScaledHeight(), headerHeight, padding, itemRowHeight);
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int width = (columnWidth * numColumns) + (padding * (numColumns - 1));
        int height = headerHeight + (itemsPerColumn * itemRowHeight) + padding;

        return new BoxSize(width, height);
    }

    private void renderBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /**
     * Handles mouse input to detect clicks on lists and manage dragging logic.
     */
    private void handleMouseInput(int mouseX, int mouseY) {
        long windowHandle = minecraft.getWindow().handle();
        boolean isMouseDown = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (isMouseDown && !wasMouseDown) {
            // Mouse Clicked: Check if any list was hit
            for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
                if (!list.isVisible) continue;

                BoxSize baseSize = calculateBaseSize(list);

                // The box is rendered at translate(list.x, list.y) with fill starting at -padding,
                // so the actual screen rect starts at list.x - padding*scale.
                // Width/height remain baseSize * scale (padding is absorbed into both sides).
                int padding = 4;
                int hitX = list.x - (int) (padding * list.scale);
                int hitY = list.y - (int) (padding * list.scale);
                int hitW = (int) (baseSize.width * list.scale);
                int hitH = (int) (baseSize.height * list.scale);

                if (mouseX >= hitX && mouseX <= hitX + hitW &&
                        mouseY >= hitY && mouseY <= hitY + hitH) {
                    this.draggingList = list;
                    this.dragOffsetX = mouseX - list.x;
                    this.dragOffsetY = mouseY - list.y;
                    break;
                }
            }
        } else if (isMouseDown && wasMouseDown) {
            // Mouse Dragging: Update position
            if (draggingList != null) {
                int newX = mouseX - dragOffsetX;
                int newY = mouseY - dragOffsetY;

                BoxSize baseSize = calculateBaseSize(draggingList);
                int scaledWidth = (int) (baseSize.width * draggingList.scale);
                int scaledHeight = (int) (baseSize.height * draggingList.scale);

                draggingList.x = Math.max(0, Math.min(newX, this.width - scaledWidth));
                draggingList.y = Math.max(0, Math.min(newY, this.height - scaledHeight));
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