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

package net.fabricmc.resourcetracker.util;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared rendering utilities used across GUI screens and HUD overlay.
 */
public class RenderUtils {

    /**
     * Draws a bordered box with a semi-transparent black background.
     */
    public static void drawBox(GuiGraphics context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x80000000);
        int color = 0xFFFFFFFF;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Draws only a semi-transparent dark background fill (no border).
     */
    public static void drawBoxFill(GuiGraphics context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x80000000);
    }

    /**
     * Draws a box outline only (no background fill) with a uniform gray border.
     */
    public static void drawBoxOutline(GuiGraphics context, int x, int y, int w, int h) {
        int color = 0xFF6B6B6B;
        // Top edge
        context.fill(x, y, x + w, y + 1, color);
        // Bottom edge
        context.fill(x, y + h - 1, x + w, y + h, color);
        // Left edge
        context.fill(x, y, x + 1, y + h, color);
        // Right edge
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Draws a styled scrollbar track and thumb.
     */
    public static void drawStyledScrollbar(GuiGraphics context, int x, int y, int h, int contentH, double scroll) {
        if (contentH <= h) return;
        context.fill(x, y, x + 5, y + h, 0xFF000000);
        int barH = Math.max(20, (int) ((float) h / contentH * h));
        int maxScroll = contentH - h;
        int barY = y + (int) ((scroll / maxScroll) * (h - barH));
        context.fill(x, barY, x + 5, barY + barH, 0xFF888888);
    }

    /**
     * Trims text to fit within a specific pixel width, appending "..." if truncated.
     */
    public static String shortenText(Font textRenderer, String text, int maxWidth) {
        if (textRenderer.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.width(suffix);
        return textRenderer.plainSubstrByWidth(text, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }

    /**
     * Formats the count text based on current progress and display mode.
     */
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

    /**
     * Calculates the number of columns and items-per-column for a tracking list.
     * Returns int[]{numColumns, itemsPerColumn}.
     */
    public static int[] calculateColumnLayout(TrackerConfig.TrackingList list, int itemCount,
            int screenHeight, int headerHeight, int padding, int itemRowHeight) {
        if (list.columns > 0) {
            int numColumns = list.columns;
            int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
            return new int[]{numColumns, itemsPerColumn};
        }
        int availH = (int) ((screenHeight - list.y) / list.scale) - headerHeight - padding;
        int maxPerCol = Math.max(1, availH / itemRowHeight);
        if (itemCount <= maxPerCol) {
            return new int[]{1, itemCount};
        }
        int numColumns = (int) Math.ceil((double) itemCount / maxPerCol);
        int itemsPerColumn = (int) Math.ceil((double) itemCount / numColumns);
        return new int[]{numColumns, itemsPerColumn};
    }
}
