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

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the rendering of the Resource Tracker overlay on the in-game HUD.
 * <p>
 * This class is responsible for drawing the tracking lists, items, counts,
 * and handling layout logic (such as multi-column rendering if the list is too long).
 * </p>
 *
 * @author vocheat
 */
public class HudOverlay {



    public void render(GuiGraphics context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            VersionCompat.push(context);
            VersionCompat.translate(context, (float) list.x, (float) list.y);
            VersionCompat.scale(context, list.scale, list.scale);

            renderListContent(context, client, list);

            VersionCompat.pop(context);
        }
    }

    private void renderListContent(GuiGraphics context, Minecraft client, TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemRowHeight = list.showIcons ? 24 : 12;

        List<TrackerConfig.TrackedItem> validItems = new ArrayList<>();
        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (trackedItem.isValid()) validItems.add(trackedItem);
        }

        int itemCount = validItems.size();
        if (itemCount == 0) {
            int boxWidth = client.font.width(list.name) + (padding * 2);
            int boxHeight = headerHeight + padding;
            context.fill(-padding, -padding, boxWidth - padding, boxHeight - padding, list.backgroundColor);
            context.drawString(client.font, list.name, 0, 0, list.nameColor);
            return;
        }

        int maxTextWidth = client.font.width(list.name);
        int iconOffset = list.showIcons ? 20 : 2;

        for (TrackerConfig.TrackedItem trackedItem : validItems) {
            String countText = RenderUtils.getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining);

            int entryWidth;
            if (list.showIcons) {
                int nameWidth = client.font.width(trackedItem.getDisplayName());
                int countWidth = client.font.width(countText);
                entryWidth = iconOffset + Math.max(nameWidth, countWidth);
            } else {
                String combined = trackedItem.getDisplayName() + ": " + countText;
                entryWidth = iconOffset + client.font.width(combined);
            }

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
        }

        int columnWidth = maxTextWidth + (padding * 2);

        int[] layout = RenderUtils.calculateColumnLayout(list, itemCount,
                client.getWindow().getGuiScaledHeight(), headerHeight, padding, itemRowHeight);
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int totalWidth = (columnWidth * numColumns) + (padding * (numColumns - 1));
        int boxHeight = headerHeight + (itemsPerColumn * itemRowHeight) + padding;

        context.fill(-padding, -padding, totalWidth - padding, boxHeight - padding, list.backgroundColor);
        context.drawString(client.font, list.name, 0, 0, list.nameColor);

        int currentY = headerHeight;
        int currentColumn = 0;
        int columnOffsetX = 0;

        for (int i = 0; i < validItems.size(); i++) {
            TrackerConfig.TrackedItem trackedItem = validItems.get(i);
            int currentCount = trackedItem.cachedCount;
            boolean isDone = currentCount >= trackedItem.targetCount;

            if (i > 0 && itemsPerColumn > 0 && i % itemsPerColumn == 0) {
                currentColumn++;
                columnOffsetX = currentColumn * (columnWidth + padding);
                currentY = headerHeight;
            }

            int itemColor = list.textColor;
            int countColor = isDone ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF);

            if (list.showIcons) {
                context.renderItem(trackedItem.getStack(), columnOffsetX, currentY + 1);

                int availableWidth = maxTextWidth - iconOffset;
                String itemName = RenderUtils.shortenText(client.font, trackedItem.getDisplayName(), availableWidth);
                context.drawString(client.font, itemName, columnOffsetX + iconOffset, currentY, itemColor);

                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
                context.drawString(client.font, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);
            } else {
                String namePart = trackedItem.getDisplayName() + ": ";
                int nw = client.font.width(namePart);
                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);

                context.drawString(client.font, namePart, columnOffsetX + iconOffset, currentY + 2, itemColor);
                context.drawString(client.font, countLine, columnOffsetX + iconOffset + nw, currentY + 2, countColor);
            }

            currentY += itemRowHeight;
        }
    }
}
