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

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.InventoryUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Handles the rendering of the Resource Tracker overlay on the in-game HUD.
 * <p>
 * This class is responsible for drawing the tracking lists, items, counts,
 * and handling layout logic (such as multi-column rendering if the list is too long).
 * </p>
 *
 * @author vocheat
 */
public class HudOverlay implements HudRenderCallback {

    private static final int ITEM_ROW_HEIGHT = 24; // Fixed height per row, matches EditScreen layout
    private static final boolean USE_COMPACT_DONE = true; // Compact mode for completed items
    private static final int MAX_TEXT_WIDTH = 200; // Maximum text width before truncation

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;

        for (TrackerConfig.TrackingList list : TrackerConfig.INSTANCE.lists) {
            if (!list.isVisible) continue;

            // Save the current matrix state
            context.getMatrices().pushMatrix();
            // Apply translation and scaling based on config
            context.getMatrices().translate((float) list.x, (float) list.y);
            context.getMatrices().scale(list.scale, list.scale);

            renderListContent(context, client, list);

            // Restore the matrix state
            context.getMatrices().popMatrix();
        }
    }

    /**
     * Calculates dimensions and renders the content of a single tracking list.
     */
    private void renderListContent(DrawContext context, MinecraftClient client, TrackerConfig.TrackingList list) {
        int padding = 4;
        int headerHeight = 14;
        int itemCount = list.items.size();

        // Handle empty list case - just draw the header
        if (itemCount == 0) {
            int boxWidth = client.textRenderer.getWidth(list.name) + (padding * 2);
            int boxHeight = headerHeight + padding;

            context.fill(-padding, -padding, boxWidth - padding, boxHeight - padding, list.backgroundColor);
            context.drawTextWithShadow(client.textRenderer, list.name, 0, 0, list.nameColor | 0xFF000000);
            return;
        }

        // --- Step 1: Pre-calculate layout and maximum width ---
        int maxTextWidth = client.textRenderer.getWidth(list.name);

        // Determine offset: 20 pixels if icons are shown, 2 pixels if not
        int iconOffset = list.showIcons ? 20 : 2;

        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            Item item = Registries.ITEM.get(Identifier.of(trackedItem.itemId));
            if (item == null) continue;

            // Update cache if necessary for width calculation
            if (trackedItem.cachedCount == -1) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, item);
            }
            int currentCount = trackedItem.cachedCount;

            String itemName = item.getName().getString();
            String countText = getCountText(currentCount, trackedItem.targetCount, list.showRemaining);

            int nameWidth = client.textRenderer.getWidth(itemName);
            int countWidth = client.textRenderer.getWidth(countText);

            // Calculate total width for this entry
            int entryWidth = iconOffset + Math.max(nameWidth, countWidth);

            // Cap the width to the maximum allowed
            if (entryWidth > MAX_TEXT_WIDTH + iconOffset) {
                entryWidth = MAX_TEXT_WIDTH + iconOffset;
            }

            if (entryWidth > maxTextWidth) {
                maxTextWidth = entryWidth;
            }
        }

        // Calculate single column dimensions
        int columnWidth = maxTextWidth + (padding * 2);
        int singleColumnHeight = headerHeight + (itemCount * ITEM_ROW_HEIGHT) + padding;

        // --- Step 2: Check if the list fits on the screen vertically ---
        int screenHeight = client.getWindow().getScaledHeight();
        int scaledHeight = (int) (singleColumnHeight * list.scale);
        int actualBottomY = (int) (list.y + scaledHeight);

        // Use double columns if the list extends beyond the screen height
        boolean useDoubleColumn = actualBottomY > screenHeight;

        int itemsPerColumn;
        int totalWidth;
        int boxHeight;

        if (useDoubleColumn) {
            itemsPerColumn = (itemCount + 1) / 2;
            totalWidth = (columnWidth * 2) + padding;
            boxHeight = headerHeight + (itemsPerColumn * ITEM_ROW_HEIGHT) + padding;
        } else {
            itemsPerColumn = itemCount;
            totalWidth = columnWidth;
            boxHeight = singleColumnHeight;
        }

        // Draw background panel
        context.fill(-padding, -padding, totalWidth - padding, boxHeight - padding, list.backgroundColor);

        // Draw header text
        context.drawTextWithShadow(client.textRenderer, list.name, 0, 0, list.nameColor | 0xFF000000);

        // --- Step 3: Render Items ---
        int currentY = headerHeight;
        int currentColumn = 0;
        int columnOffsetX = 0;

        for (int i = 0; i < list.items.size(); i++) {
            TrackerConfig.TrackedItem trackedItem = list.items.get(i);
            Item item = Registries.ITEM.get(Identifier.of(trackedItem.itemId));
            if (item == null) continue;

            // Use cached count (likely calculated in Step 1)
            if (trackedItem.cachedCount == -1) {
                trackedItem.cachedCount = InventoryUtils.countItems(client.player, item);
            }
            int currentCount = trackedItem.cachedCount;

            int target = trackedItem.targetCount;
            boolean isDone = currentCount >= target;

            // Handle column switching
            if (useDoubleColumn && i >= itemsPerColumn) {
                if (currentColumn == 0) {
                    columnOffsetX = columnWidth + padding;
                    currentY = headerHeight;
                    currentColumn = 1;
                }
            }

            // Draw Item Icon (only if enabled)
            if (list.showIcons) {
                int iconY = currentY + 1;
                context.drawItem(new ItemStack(item), columnOffsetX, iconY);
            }

            // Prepare Item Name
            String itemName = item.getName().getString();

            // Calculate available width for text (total width - icon offset)
            int availableWidth = maxTextWidth - iconOffset;

            // Smartly trim text if it's too long
            itemName = shortenText(client.textRenderer, itemName, availableWidth);

            // Draw Item Name
            int itemColor = list.textColor | 0xFF000000;
            context.drawTextWithShadow(client.textRenderer, itemName, columnOffsetX + iconOffset, currentY, itemColor);

            // Draw Item Count
            String countLine = getCountText(currentCount, target, list.showRemaining);
            int countColor = isDone ? 0xFF55FF55 : (itemColor & 0xAAFFFFFF); // Green if done, else slightly transparent
            context.drawTextWithShadow(client.textRenderer, countLine, columnOffsetX + iconOffset, currentY + 10, countColor);

            currentY += ITEM_ROW_HEIGHT;
        }
    }

    /**
     * Formats the count text based on current progress and settings.
     */
    private String getCountText(int current, int target, boolean showRemaining) {
        boolean isDone = current >= target;

        if (USE_COMPACT_DONE && isDone) {
            return "[✓] " + current + "/" + target;
        }

        if (isDone) {
            return Text.translatable("gui.resourcetracker.overlay.done").getString() + " (" + current + "/" + target + ")";
        }

        return showRemaining ?
                Text.translatable("gui.resourcetracker.overlay.need").getString() + (target - current) :
                current + " / " + target;
    }

    /**
     * Trims text to fit within a specific width, appending "..." if truncated.
     *
     * @param textRenderer The text renderer instance.
     * @param text         The string to potentially shorten.
     * @param maxWidth     The maximum allowed width in pixels.
     * @return The original text if it fits, or a shortened version ending in "..."
     */
    private String shortenText(net.minecraft.client.font.TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.getWidth(suffix);
        // trimToWidth cuts text to (width - suffix_width) to ensure the suffix fits
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }
}