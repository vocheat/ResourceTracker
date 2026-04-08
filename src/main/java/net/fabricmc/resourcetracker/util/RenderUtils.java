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

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Shared rendering utilities used across GUI screens and HUD overlay.
 */
public class RenderUtils {

    /**
     * Draws a bordered box with a semi-transparent black background.
     */
    public static void drawBox(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x80000000);
        int color = 0xFFFFFFFF;
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * Draws a styled scrollbar track and thumb.
     */
    public static void drawStyledScrollbar(DrawContext context, int x, int y, int h, int contentH, double scroll) {
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
    public static String shortenText(TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.getWidth(suffix);
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }

    /**
     * Formats the count text based on current progress and display mode.
     */
    public static String getCountText(int current, int target, boolean showRemaining) {
        if (current >= target) {
            return "[\u2713] " + current + "/" + target;
        }
        return showRemaining
                ? Text.translatable("gui.resourcetracker.overlay.need").getString() + (target - current)
                : current + " / " + target;
    }
}
