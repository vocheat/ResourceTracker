package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and reuses immutable HUD draw models until the list or its counts are invalidated.
 */
public final class HudRenderCache {
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 14;
    private static final Map<TrackerConfig.TrackingList, Entry> CACHE = new IdentityHashMap<>();

    private HudRenderCache() {
    }

    public static HudRenderModel get(TrackerConfig.TrackingList list, Font font, int guiScaledHeight) {
        Entry entry = CACHE.get(list);
        if (entry != null && entry.font == font && entry.guiScaledHeight == guiScaledHeight) {
            return entry.model;
        }

        String needPrefix = Component.translatable("gui.resourcetracker.overlay.need").getString();
        HudRenderModel model = buildModel(list, font, guiScaledHeight, needPrefix);
        CACHE.put(list, new Entry(font, guiScaledHeight, model));
        return model;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static HudRenderModel buildModel(
            TrackerConfig.TrackingList list,
            Font font,
            int guiScaledHeight,
            String needPrefix
    ) {
        String title = list.name == null ? "" : list.name;
        int itemRowHeight = list.showIcons ? 24 : 12;

        List<TrackerConfig.TrackedItem> validItems = new ArrayList<>();
        if (list.items != null) {
            for (TrackerConfig.TrackedItem trackedItem : list.items) {
                if (trackedItem != null && trackedItem.isValid()) {
                    validItems.add(trackedItem);
                }
            }
        }

        if (validItems.isEmpty()) {
            int boxWidth = font.width(title) + (PADDING * 2);
            int boxHeight = HEADER_HEIGHT + PADDING;
            return new HudRenderModel(
                    title,
                    list.nameColor,
                    list.backgroundColor,
                    -PADDING,
                    -PADDING,
                    boxWidth - PADDING,
                    boxHeight - PADDING,
                    list.showIcons,
                    1,
                    0,
                    List.of()
            );
        }

        int maxTextWidth = font.width(title);
        int iconOffset = list.showIcons ? 20 : 2;
        List<ItemText> texts = new ArrayList<>(validItems.size());

        for (TrackerConfig.TrackedItem trackedItem : validItems) {
            String displayName = trackedItem.getDisplayName();
            String countText = getCountText(trackedItem.cachedCount, trackedItem.targetCount, list.showRemaining, needPrefix);

            int entryWidth;
            int namePartWidth = 0;
            if (list.showIcons) {
                int nameWidth = font.width(displayName);
                int countWidth = font.width(countText);
                entryWidth = iconOffset + Math.max(nameWidth, countWidth);
            } else {
                String namePart = displayName + ": ";
                namePartWidth = font.width(namePart);
                entryWidth = iconOffset + namePartWidth + font.width(countText);
            }

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
            texts.add(new ItemText(displayName, countText, namePartWidth));
        }

        int columnWidth = maxTextWidth + (PADDING * 2);
        int[] layout = RenderUtils.calculateColumnLayout(
                list,
                validItems.size(),
                guiScaledHeight,
                HEADER_HEIGHT,
                PADDING,
                itemRowHeight
        );
        int numColumns = layout[0];
        int itemsPerColumn = layout[1];

        int totalWidth = (columnWidth * numColumns) + (PADDING * (numColumns - 1));
        int boxHeight = HEADER_HEIGHT + (itemsPerColumn * itemRowHeight) + PADDING;
        List<HudRenderModel.Row> rows = new ArrayList<>(validItems.size());

        int currentY = HEADER_HEIGHT;
        int currentColumn = 0;
        int columnOffsetX = 0;

        for (int drawn = 0; drawn < validItems.size(); drawn++) {
            if (drawn > 0 && itemsPerColumn > 0 && drawn % itemsPerColumn == 0) {
                currentColumn++;
                columnOffsetX = currentColumn * (columnWidth + PADDING);
                currentY = HEADER_HEIGHT;
            }

            TrackerConfig.TrackedItem trackedItem = validItems.get(drawn);
            ItemText text = texts.get(drawn);
            int itemColor = list.textColor;
            int countColor = trackedItem.cachedCount >= trackedItem.targetCount
                    ? (itemColor & 0xFF000000) | 0x0055FF55
                    : itemColor;

            if (list.showIcons) {
                int availableWidth = maxTextWidth - iconOffset;
                String itemName = RenderUtils.shortenText(font, text.displayName, availableWidth);
                rows.add(new HudRenderModel.Row(
                        trackedItem.getStack(),
                        columnOffsetX,
                        currentY + 1,
                        itemName,
                        columnOffsetX + iconOffset,
                        currentY,
                        itemColor,
                        text.countText,
                        columnOffsetX + iconOffset,
                        currentY + 10,
                        countColor
                ));
            } else {
                String namePart = text.displayName + ": ";
                rows.add(new HudRenderModel.Row(
                        null,
                        0,
                        0,
                        namePart,
                        columnOffsetX + iconOffset,
                        currentY + 2,
                        itemColor,
                        text.countText,
                        columnOffsetX + iconOffset + text.namePartWidth,
                        currentY + 2,
                        countColor
                ));
            }

            currentY += itemRowHeight;
        }

        return new HudRenderModel(
                title,
                list.nameColor,
                list.backgroundColor,
                -PADDING,
                -PADDING,
                totalWidth - PADDING,
                boxHeight - PADDING,
                list.showIcons,
                numColumns,
                itemsPerColumn,
                rows
        );
    }

    private static String getCountText(int current, int target, boolean showRemaining, String needPrefix) {
        if (current >= target) {
            return "[\u2713] " + current + "/" + target;
        }
        if (showRemaining) {
            return needPrefix + (target - current);
        }
        return current + " / " + target;
    }

    private record Entry(Font font, int guiScaledHeight, HudRenderModel model) {
    }

    private record ItemText(String displayName, String countText, int namePartWidth) {
    }
}
