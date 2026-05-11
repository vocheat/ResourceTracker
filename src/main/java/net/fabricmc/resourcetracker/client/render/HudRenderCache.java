package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds and reuses immutable HUD draw models keyed by list state and cached counts.
 */
public final class HudRenderCache {
    private static final int PADDING = 4;
    private static final int HEADER_HEIGHT = 14;
    private static final Map<String, Entry> CACHE = new HashMap<>();

    private HudRenderCache() {
    }

    public static HudRenderModel get(TrackerConfig.TrackingList list, Font font, int guiScaledHeight) {
        String needPrefix = Component.translatable("gui.resourcetracker.overlay.need").getString();
        String cacheKey = buildKey(list, guiScaledHeight, needPrefix);
        String cacheId = list.id == null ? "" : list.id;
        Entry entry = CACHE.get(cacheId);
        if (entry != null && entry.key.equals(cacheKey)) {
            return entry.model;
        }

        HudRenderModel model = buildModel(list, font, guiScaledHeight, needPrefix);
        CACHE.put(cacheId, new Entry(cacheKey, model));
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
            int countColor = trackedItem.cachedCount >= trackedItem.targetCount ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF);

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

    private static String buildKey(TrackerConfig.TrackingList list, int guiScaledHeight, String needPrefix) {
        StringBuilder key = new StringBuilder(256);
        key.append(nullSafe(list.id)).append('|')
                .append(nullSafe(list.name)).append('|')
                .append(list.y).append('|')
                .append(TrackerConfig.clampScale(list.scale)).append('|')
                .append(list.showIcons).append('|')
                .append(list.showRemaining).append('|')
                .append(TrackerConfig.clampColumns(list.columns)).append('|')
                .append(list.textColor).append('|')
                .append(list.nameColor).append('|')
                .append(list.backgroundColor).append('|')
                .append(guiScaledHeight).append('|')
                .append(needPrefix).append('|');
        if (list.items != null) {
            for (TrackerConfig.TrackedItem item : list.items) {
                if (item == null) {
                    key.append("<null>;");
                } else {
                    key.append(nullSafe(item.itemId)).append('=')
                            .append(item.targetCount).append(',')
                            .append(item.cachedCount).append(';');
                }
            }
        }
        return key.toString();
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

    private static String nullSafe(String value) {
        return Objects.toString(value, "");
    }

    private record Entry(String key, HudRenderModel model) {
    }

    private record ItemText(String displayName, String countText, int namePartWidth) {
    }
}
