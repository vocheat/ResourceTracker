package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public class HudOverlay {
    public void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;
        if (!TrackerConfig.INSTANCE.hudVisible) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            VersionCompat.push(context);
            VersionCompat.translate(context, list.x, list.y);
            VersionCompat.scale(context, list.scale, list.scale);

            renderListContent(context, client, list);

            VersionCompat.pop(context);
        }
    }

    private void renderListContent(GuiGraphicsExtractor context, Minecraft client, TrackerConfig.TrackingList list) {
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
            context.text(client.font, list.name, 0, 0, list.nameColor);
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
        context.text(client.font, list.name, 0, 0, list.nameColor);

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
                context.item(trackedItem.getStack(), columnOffsetX, currentY + 1);

                int availableWidth = maxTextWidth - iconOffset;
                String itemName = RenderUtils.shortenText(client.font, trackedItem.getDisplayName(), availableWidth);
                context.text(client.font, itemName, columnOffsetX + iconOffset, currentY, itemColor);

                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);
                context.text(client.font, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);
            } else {
                String namePart = trackedItem.getDisplayName() + ": ";
                int nw = client.font.width(namePart);
                String countLine = RenderUtils.getCountText(currentCount, trackedItem.targetCount, list.showRemaining);

                context.text(client.font, namePart, columnOffsetX + iconOffset, currentY + 2, itemColor);
                context.text(client.font, countLine, columnOffsetX + iconOffset + nw, currentY + 2, countColor);
            }

            currentY += itemRowHeight;
        }
    }
}
