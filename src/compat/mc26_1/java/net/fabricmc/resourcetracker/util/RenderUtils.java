package net.fabricmc.resourcetracker.util;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class RenderUtils {
    public static final int PIXEL_ICON_SIZE = 16;

    public static void drawPixelIcon24(GuiGraphicsExtractor context, int x, int y, int color, int[] pixels) {
        if (pixels == null) return;
        for (int pixel : pixels) {
            int px = (pixel >> 8) & 0xFF;
            int py = pixel & 0xFF;
            if (px >= 0 && px < PIXEL_ICON_SIZE && py >= 0 && py < PIXEL_ICON_SIZE) {
                context.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }

    public static void drawPixelIcon24InBox(GuiGraphicsExtractor context, int[] pixels, int boxX, int boxY, int boxW, int boxH, int color) {
        int iconX = boxX + (boxW - PIXEL_ICON_SIZE) / 2;
        int iconY = boxY + (boxH - PIXEL_ICON_SIZE) / 2;
        drawPixelIcon24(context, iconX, iconY, color, pixels);
    }

    public static void drawBox(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x80000000);
        int color = 0xFFFFFFFF;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawBoxFill(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x80000000);
    }

    public static void drawBoxOutline(GuiGraphicsExtractor context, int x, int y, int w, int h) {
        int color = 0xFF6B6B6B;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawStyledScrollbar(GuiGraphicsExtractor context, int x, int y, int h, int contentH, double scroll) {
        if (contentH <= h) return;
        context.fill(x, y, x + 5, y + h, 0xFF000000);
        int barH = Math.max(20, (int) ((float) h / contentH * h));
        int maxScroll = contentH - h;
        int barY = y + (int) ((scroll / maxScroll) * (h - barH));
        context.fill(x, barY, x + 5, barY + barH, 0xFF888888);
    }

    public static String shortenText(Font textRenderer, String text, int maxWidth) {
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.width(suffix);
        return textRenderer.plainSubstrByWidth(text, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }

    private static String cachedNeedPrefix = null;

    public static String getCountText(int current, int target, boolean showRemaining) {
        if (current >= target) {
            return "[\u2713] " + current + "/" + target;
        }
        if (showRemaining) {
            if (cachedNeedPrefix == null) {
                cachedNeedPrefix = Component.translatable("gui.resourcetracker.overlay.need").getString();
            }
            return cachedNeedPrefix + (target - current);
        }
        return current + " / " + target;
    }

    public static int[] calculateColumnLayout(TrackerConfig.TrackingList list, int itemCount,
                                              int screenHeight, int headerHeight, int padding, int itemRowHeight) {
        if (itemCount <= 0) {
            return new int[]{1, 0};
        }
        if (list.columns > 0) {
            int numColumns = list.columns;
            int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
            return new int[] { numColumns, itemsPerColumn };
        }
        int availH = (int) ((screenHeight - list.y) / list.scale) - headerHeight - padding;
        int maxPerCol = Math.max(1, availH / itemRowHeight);
        if (itemCount <= maxPerCol) {
            return new int[] { 1, itemCount };
        }
        int numColumns = (int) Math.ceil((double) itemCount / maxPerCol);
        int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
        return new int[] { numColumns, itemsPerColumn };
    }
}
