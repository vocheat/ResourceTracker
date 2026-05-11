package net.fabricmc.resourcetracker.util;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class RenderUtils {
    public static void drawPngIconInBox(GuiGraphicsExtractor context, PngIcons.Icon icon, int boxX, int boxY, int boxW, int boxH, int color) {
        PngIcons.Mask mask = icon.mask();
        int iconX = boxX + (boxW - mask.width) / 2;
        int iconY = boxY + (boxH - mask.height) / 2;
        drawPngIcon(context, icon, iconX, iconY, color);
    }

    public static void drawPngIcon(GuiGraphicsExtractor context, PngIcons.Icon icon, int x, int y, int color) {
        PngIcons.Mask mask = icon.mask();
        int colorAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        for (int pixel : mask.pixels) {
            int alpha = ((pixel & 0xFF) * colorAlpha + 127) / 255;
            if (alpha == 0) {
                continue;
            }
            int px = (pixel >>> 16) & 0xFF;
            int py = (pixel >>> 8) & 0xFF;
            context.fill(x + px, y + py, x + px + 1, y + py + 1, (alpha << 24) | rgb);
        }
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
        int configuredColumns = TrackerConfig.clampColumns(list.columns);
        if (configuredColumns > 0) {
            int numColumns = configuredColumns;
            int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
            return new int[] { numColumns, itemsPerColumn };
        }
        float scale = TrackerConfig.clampScale(list.scale);
        int availH = (int) ((screenHeight - list.y) / scale) - headerHeight - padding;
        int maxPerCol = Math.max(1, availH / itemRowHeight);
        if (itemCount <= maxPerCol) {
            return new int[] { 1, itemCount };
        }
        int numColumns = (int) Math.ceil((double) itemCount / maxPerCol);
        int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
        return new int[] { numColumns, itemsPerColumn };
    }
}
