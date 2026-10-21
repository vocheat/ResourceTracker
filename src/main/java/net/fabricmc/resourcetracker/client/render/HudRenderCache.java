package net.fabricmc.resourcetracker.client.render;

import net.fabricmc.resourcetracker.client.gui.UiTheme;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.model.CountFormatter;
import net.fabricmc.resourcetracker.model.HudSelection;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Shared card geometry for the game HUD, editor preview and positioning screen. */
public final class HudRenderCache {
    private static final int PADDING = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int COLUMN_GAP = 8;
    private static final int MAX_NAME_WIDTH = 180;
    private static final Map<TrackerConfig.TrackingList, Entry> CACHE = new IdentityHashMap<>();
    private HudRenderCache() {}

    public static HudRenderModel get(TrackerConfig.TrackingList list, Font font, int guiScaledHeight) {
        Entry entry = CACHE.get(list);
        if (entry != null && entry.font == font && entry.guiScaledHeight == guiScaledHeight) return entry.model;
        HudRenderModel model = preview(list, font, guiScaledHeight);
        CACHE.put(list, new Entry(font, guiScaledHeight, model));
        return model;
    }
    public static void clear() { CACHE.clear(); }

    /** Fresh geometry for settings being edited, using tick-cached item counts. */
    public static HudRenderModel preview(TrackerConfig.TrackingList list, Font font, int guiScaledHeight) {
        String needPrefix = Component.translatable("gui.resourcetracker.overlay.need").getString();
        String title = list.name == null ? "" : list.name;
        int rowHeight = list.showIcons ? 28 : 18;
        int iconOffset = list.showIcons ? 22 : 0;
        List<TrackerConfig.TrackedItem> items = new ArrayList<>();
        if (list.displayItems() != null) {
            for (TrackerConfig.TrackedItem item : list.displayItems()) if (item != null && item.isValid()) items.add(item);
        }
        HudSelection.Selection<TrackerConfig.TrackedItem> selection = HudSelection.select(items,
                list.hideCompleted, list.sortRemaining, list.maxVisibleRows);
        items = new ArrayList<>(selection.rows());
        int completed = selection.completed(), contentWidth = 72;
        List<ItemText> texts = new ArrayList<>(items.size());
        for (TrackerConfig.TrackedItem item : items) {
            boolean done = item.isCompleted();
            String name = RenderUtils.shortenText(font, item.getDisplayName(), MAX_NAME_WIDTH);
            String count = item.isManuallyCollected()
                    ? Component.translatable("gui.resourcetracker.collected").getString()
                    : list.showRemaining && !done ? needPrefix + formatCount(list, item, item.remaining())
                    : formatCount(list, item, item.cachedCount) + " / " + formatCount(list, item, item.targetCount);
            int countWidth = font.width(count) + (done ? PngIcons.SMALL_CHECK_ADVANCE : 0);
            int entryWidth = list.showIcons ? iconOffset + Math.max(font.width(name), countWidth)
                    : font.width(name) + 12 + countWidth;
            contentWidth = Math.max(contentWidth, entryWidth);
            texts.add(new ItemText(name, count, done));
        }
        String summary = selection.total() == 0 ? "" : completed + " / " + selection.total();
        if (selection.hiddenByLimit() > 0) summary += " · "
                + Component.translatable("gui.resourcetracker.hud.more", selection.hiddenByLimit()).getString();
        if (list.hudRaw && selection.total() == 0) summary = Component.translatable(list.rawPlanValid
                ? "gui.resourcetracker.overlay.done" : "gui.resourcetracker.raw.unavailable").getString();
        int[] layout = RenderUtils.calculateColumnLayout(list, items.size(), guiScaledHeight, HEADER_HEIGHT, PADDING, rowHeight);
        int columns = layout[0], itemsPerColumn = layout[1];
        int columnWidth = contentWidth + PADDING * 2;
        int totalWidth = Math.max(columnWidth * columns + COLUMN_GAP * (columns - 1),
                font.width(title) + (summary.isEmpty() ? 0 : font.width(summary) + 16) + PADDING * 2);
        if (columns == 1) columnWidth = totalWidth;
        int height = HEADER_HEIGHT + itemsPerColumn * rowHeight + PADDING;
        int left = -PADDING, top = -PADDING, right = totalWidth - PADDING, bottom = height - PADDING;
        List<HudRenderModel.Fill> fills = new ArrayList<>();
        List<HudRenderModel.Row> rows = new ArrayList<>(items.size());
        // Each background region is painted once so semi-transparent colors keep their alpha.
        fills.add(new HudRenderModel.Fill(left, top, right, HEADER_HEIGHT - 6,
                blend(list.backgroundColor, UiTheme.HEADER, 0.45f)));
        fills.add(new HudRenderModel.Fill(left, HEADER_HEIGHT - 6, right, HEADER_HEIGHT - 3, list.backgroundColor));
        for (int column = 0; column < columns; column++) {
            int x = column * (columnWidth + COLUMN_GAP) - PADDING;
            for (int row = 0; row < itemsPerColumn; row++) {
                int y = HEADER_HEIGHT + row * rowHeight - 3;
                boolean populated = column * itemsPerColumn + row < items.size();
                int color = populated && (row & 1) == 0
                        ? blend(list.backgroundColor, UiTheme.PANEL, 0.25f) : list.backgroundColor;
                fills.add(new HudRenderModel.Fill(x, y, x + columnWidth, y + rowHeight, color));
            }
            if (column < columns - 1) fills.add(new HudRenderModel.Fill(x + columnWidth, HEADER_HEIGHT - 3,
                    x + columnWidth + COLUMN_GAP, bottom, list.backgroundColor));
        }
        int columnsRight = columns * columnWidth + COLUMN_GAP * (columns - 1) - PADDING;
        if (columnsRight < right) fills.add(new HudRenderModel.Fill(columnsRight, HEADER_HEIGHT - 3,
                right, bottom, list.backgroundColor));
        fills.add(new HudRenderModel.Fill(left, HEADER_HEIGHT + itemsPerColumn * rowHeight - 3,
                right, bottom, list.backgroundColor));
        int borderColor = withAlpha(UiTheme.BORDER, list.backgroundColor >>> 24);
        border(fills, left, top, right, bottom, borderColor);
        fills.add(new HudRenderModel.Fill(left + 1, HEADER_HEIGHT - 6, right - 1, HEADER_HEIGHT - 5, borderColor));
        for (int i = 0; i < items.size(); i++) {
            TrackerConfig.TrackedItem item = items.get(i);
            ItemText text = texts.get(i);
            int column = i / itemsPerColumn, row = i % itemsPerColumn;
            int x = column * (columnWidth + COLUMN_GAP), y = HEADER_HEIGHT + row * rowHeight;
            int rowRight = x + columnWidth - PADDING * 2;
            int progressY = y + rowHeight - 6;
            int trackWidth = rowRight - x;
            fills.add(new HudRenderModel.Fill(x, progressY, rowRight, progressY + 2,
                    withAlpha(UiTheme.BORDER, (list.backgroundColor >>> 24) / 2)));
            double fraction = item.isManuallyCollected() ? 1
                    : Math.max(0, Math.min(1, (double) item.cachedCount / Math.max(1, item.targetCount)));
            int filledWidth = (int) Math.round(trackWidth * fraction);
            if (filledWidth > 0) fills.add(new HudRenderModel.Fill(x, progressY, x + filledWidth, progressY + 2,
                    withAlpha(UiTheme.ACCENT, list.textColor >>> 24)));
            int countColor = text.done ? withAlpha(UiTheme.ACCENT, list.textColor >>> 24)
                    : blend(list.textColor, UiTheme.MUTED, 0.45f);
            rows.add(new HudRenderModel.Row(list.showIcons ? item.getStack() : null, x, y + 1,
                    text.name, x + iconOffset, y, list.textColor, text.count,
                    list.showIcons ? x + iconOffset : rowRight - font.width(text.count)
                            - (text.done ? PngIcons.SMALL_CHECK_ADVANCE : 0),
                    list.showIcons ? y + 10 : y, countColor, text.done));
        }
        return new HudRenderModel(title, list.nameColor, list.backgroundColor, left, top, right, bottom,
                list.showIcons, columns, itemsPerColumn, rows, fills, summary,
                right - PADDING - font.width(summary),
                withAlpha(completed == selection.total() && selection.total() > 0 ? UiTheme.ACCENT : UiTheme.MUTED, list.nameColor >>> 24));
    }
    public static String formatCount(TrackerConfig.TrackingList list, TrackerConfig.TrackedItem item, long count) {
        return CountFormatter.format(count, item.getStack().getMaxStackSize(),
                item.getStack().getItem().canFitInsideContainerItems(),
                CountFormatter.Unit.valueOf(list.countUnit.name()),
                new CountFormatter.Labels(Component.translatable("gui.resourcetracker.unit.items").getString(),
                        Component.translatable("gui.resourcetracker.unit.stacks").getString(),
                        Component.translatable("gui.resourcetracker.unit.shulkers").getString()));
    }
    private static void border(List<HudRenderModel.Fill> fills, int x, int y, int right, int bottom, int color) {
        fills.add(new HudRenderModel.Fill(x, y, right, y + 1, color));
        fills.add(new HudRenderModel.Fill(x, bottom - 1, right, bottom, color));
        fills.add(new HudRenderModel.Fill(x, y + 1, x + 1, bottom - 1, color));
        fills.add(new HudRenderModel.Fill(right - 1, y + 1, right, bottom - 1, color));
    }
    private static int withAlpha(int color, int alpha) { return (alpha << 24) | (color & 0xFFFFFF); }
    /** Keep the configured alpha, including a fully transparent background. */
    private static int blend(int base, int tint, float amount) {
        int red = Math.round(((base >> 16) & 255) * (1 - amount) + ((tint >> 16) & 255) * amount);
        int green = Math.round(((base >> 8) & 255) * (1 - amount) + ((tint >> 8) & 255) * amount);
        int blue = Math.round((base & 255) * (1 - amount) + (tint & 255) * amount);
        return (base & 0xFF000000) | (red << 16) | (green << 8) | blue;
    }
    private record Entry(Font font, int guiScaledHeight, HudRenderModel model) {}
    private record ItemText(String name, String count, boolean done) {}
}
