package net.fabricmc.resourcetracker.client.render;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Immutable, precomputed draw data for one HUD tracking list.
 */
public final class HudRenderModel {
    public final String title;
    public final int titleColor;
    public final int backgroundColor;
    public final int backgroundMinX;
    public final int backgroundMinY;
    public final int backgroundMaxX;
    public final int backgroundMaxY;
    public final boolean showIcons;
    public final int columns;
    public final int itemsPerColumn;
    public final List<Row> rows;

    HudRenderModel(
            String title,
            int titleColor,
            int backgroundColor,
            int backgroundMinX,
            int backgroundMinY,
            int backgroundMaxX,
            int backgroundMaxY,
            boolean showIcons,
            int columns,
            int itemsPerColumn,
            List<Row> rows
    ) {
        this.title = title;
        this.titleColor = titleColor;
        this.backgroundColor = backgroundColor;
        this.backgroundMinX = backgroundMinX;
        this.backgroundMinY = backgroundMinY;
        this.backgroundMaxX = backgroundMaxX;
        this.backgroundMaxY = backgroundMaxY;
        this.showIcons = showIcons;
        this.columns = columns;
        this.itemsPerColumn = itemsPerColumn;
        this.rows = List.copyOf(rows);
    }

    public static final class Row {
        public final ItemStack stack;
        public final int itemX;
        public final int itemY;
        public final String nameText;
        public final int nameX;
        public final int nameY;
        public final int nameColor;
        public final String countText;
        public final int countX;
        public final int countY;
        public final int countColor;

        Row(
                ItemStack stack,
                int itemX,
                int itemY,
                String nameText,
                int nameX,
                int nameY,
                int nameColor,
                String countText,
                int countX,
                int countY,
                int countColor
        ) {
            this.stack = stack;
            this.itemX = itemX;
            this.itemY = itemY;
            this.nameText = nameText;
            this.nameX = nameX;
            this.nameY = nameY;
            this.nameColor = nameColor;
            this.countText = countText;
            this.countX = countX;
            this.countY = countY;
            this.countColor = countColor;
        }
    }
}
