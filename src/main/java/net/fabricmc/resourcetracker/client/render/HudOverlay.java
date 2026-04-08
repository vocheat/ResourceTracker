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

package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Handles the rendering of the Resource Tracker overlay on the in-game HUD.
 * <p>
 * This class is responsible for drawing the tracking lists, items, counts,
 * and handling layout logic (such as multi-column rendering if the list is too long).
 * </p>
 *
 * @author vocheat
 */
public class HudOverlay implements HudRenderCallback {

    private static final int ITEM_ROW_HEIGHT = 24;
    private static final int MAX_TEXT_WIDTH = 200;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) list.x, (float) list.y);
            context.getMatrices().scale(list.scale, list.scale);

            renderListContent(context, client, list);

            context.getMatrices().popMatrix();
        }
    }

    private void renderListContent(DrawContext context, MinecraftClient client, TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemCount = list.items.size();

        if (itemCount == 0) {
            int boxWidth = client.textRenderer.getWidth(list.name) + (padding * 2);
            int boxHeight = headerHeight + padding;
            context.fill(-padding, -padding, boxWidth - padding, boxHeight - padding, list.backgroundColor);
            context.drawTextWithShadow(client.textRenderer, list.name, 0, 0, list.nameColor);
            return;
        }

        int maxTextWidth = client.textRenderer.getWidth(list.name);
        int iconOffset = list.showIcons ? 20 : 2;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (!trackedItem.isValid()) continue;

            if (trackedItem.cachedCount == -1) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, trackedItem.getItem());
            }

            String countText = RenderUtils.getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining);
            int nameWidth = client.textRenderer.getWidth(trackedItem.getDisplayName());
            int countWidth = client.textRenderer.getWidth(countText);
            int entryWidth = iconOffset + Math.min(Math.max(nameWidth, countWidth), MAX_TEXT_WIDTH);

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
        }

        int columnWidth = maxTextWidth + (padding * 2);
        int singleColumnHeight = headerHeight + (itemCount * ITEM_ROW_HEIGHT) + padding;

        int screenHeight = client.getWindow().getScaledHeight();
        boolean useDoubleColumn = (list.y + (int) (singleColumnHeight * list.scale)) > screenHeight;

        int itemsPerColumn;
        int totalWidth;
        int boxHeight;

        if (useDoubleColumn) {
            itemsPerColumn = (itemCount + 1) / 2;
            totalWidth = (columnWidth * 2) + padding;
            boxHeight = headerHeight + (itemsPerColumn * ITEM_ROW_HEIGHT) + padding;
        } else {
            itemsPerColumn = itemCount;
            totalWidth = columnWidth;
            boxHeight = singleColumnHeight;
        }

        context.fill(-padding, -padding, totalWidth - padding, boxHeight - padding, list.backgroundColor);
        context.drawTextWithShadow(client.textRenderer, list.name, 0, 0, list.nameColor);

        int currentY = headerHeight;
        int currentColumn = 0;
        int columnOffsetX = 0;

        for (int i = 0; i < list.items.size(); i++) {
            TrackerConfig.TrackedItem trackedItem = list.items.get(i);
            if (!trackedItem.isValid()) continue;

            if (trackedItem.cachedCount == -1) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, trackedItem.getItem());
            }
            int currentCount = trackedItem.cachedCount;
            boolean isDone = currentCount >= trackedItem.targetCount;

            if (useDoubleColumn && i >= itemsPerColumn && currentColumn == 0) {
                columnOffsetX = columnWidth + padding;
                currentY = headerHeight;
                currentColumn = 1;
            }

            if (list.showIcons) {
                context.drawItem(trackedItem.getStack(), columnOffsetX, currentY + 1);
            }

            int availableWidth = maxTextWidth - iconOffset;
            String itemName = RenderUtils.shortenText(client.textRenderer, trackedItem.getDisplayName(), availableWidth);

            int itemColor = list.textColor;
            context.drawTextWithShadow(client.textRenderer, itemName, columnOffsetX + iconOffset, currentY, itemColor);

            String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
            int countColor = isDone ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF);
            context.drawTextWithShadow(client.textRenderer, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);

            currentY += ITEM_ROW_HEIGHT;
        }
    }
}