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

package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.PixelIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EditScreen extends Screen {
    private static final int SEARCH_HEIGHT = 26;
    private static final int ITEM_ROW_HEIGHT = 28;
    private static final int PIXEL_ICON_SIZE = 16;

    private final Screen parent;
    private final TrackerConfig.TrackingList list;

    private EditBox xField;
    private EditBox yField;
    private EditBox scaleField;
    private EditBox searchField;
    private EditBox trackedSearchField;

    private final List<Item> availableItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private final List<TrackerConfig.TrackedItem> filteredTrackedItems = new ArrayList<>();
    private final Map<TrackerConfig.TrackedItem, EditBox> itemCountFields = new HashMap<>();
    private final Map<Item, ItemStack> searchStackCache = new HashMap<>();
    private final Map<Item, String> itemDisplayNames = new HashMap<>();
    private final List<LabelData> labels = new ArrayList<>();

    private double scrollLeft = 0;
    private double scrollRight = 0;
    private int listAreaY;
    private int leftBoxX;
    private int rightBoxX;
    private int boxWidth;
    private int boxHeight;
    private Component hoveredTooltipText = null;
    private boolean isDraggingScrollLeft = false;
    private boolean isDraggingScrollRight = false;

    private EditBox[] textRgbA = new EditBox[4];
    private EditBox[] titleRgbA = new EditBox[4];
    private EditBox[] bgRgbA = new EditBox[4];

    private record LabelData(Component text, int x, int y, int color) {}

    public EditScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(Component.translatable("gui.resourcetracker.edit.title"));
        this.parent = parent;
        this.list = list;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.labels.clear();
        this.itemCountFields.clear();

        if (availableItems.isEmpty()) {
            BuiltInRegistries.ITEM.stream().forEach(item -> {
                availableItems.add(item);
                itemDisplayNames.put(item, VersionCompat.getItemName(item));
            });
            availableItems.sort(Comparator.comparing(itemDisplayNames::get));
        }

        int centerX = this.width / 2;

        int row1Y = 21;
        int nameW = 120;
        int fieldW = 30;
        int gap = 5;
        int row1Width = nameW + gap + fieldW + gap + fieldW + gap + fieldW;
        int startX = centerX - (row1Width / 2);

        addLabel(Component.translatable("gui.resourcetracker.edit.list_name"), startX, nameW, row1Y - 12, 0xFFFFFFFF);
        EditBox nameField = new EditBox(font, startX, row1Y, nameW, 14, Component.translatable("gui.resourcetracker.edit.list_name"));
        nameField.setValue(list.name);
        nameField.setResponder(s -> list.name = s);
        this.addRenderableWidget(nameField);

        int curX = startX + nameW + gap;

        addLabel(Component.translatable("gui.resourcetracker.edit.pos_x"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        xField = createSmallField(curX, row1Y, fieldW, list.x, s -> {
            try {
                list.x = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(xField);
        curX += fieldW + gap;

        addLabel(Component.translatable("gui.resourcetracker.edit.pos_y"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        yField = createSmallField(curX, row1Y, fieldW, list.y, s -> {
            try {
                list.y = Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(yField);
        curX += fieldW + gap;

        addLabel(Component.translatable("gui.resourcetracker.edit.scale"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        scaleField = createSmallField(curX, row1Y, fieldW, String.valueOf(list.scale), s -> {
            try {
                float v = Float.parseFloat(s);
                list.scale = Math.max(0.25f, Math.min(4.0f, v));
            } catch (NumberFormatException ignored) {
            }
        });
        this.addRenderableWidget(scaleField);

        int row2Y = row1Y + 21;
        int btnW = 80;
        int btnGap = 4;
        int resetW = 60;
        int row2Width = btnW + btnGap + btnW + btnGap + btnW + btnGap + resetW;
        int btnStartX = centerX - (row2Width / 2);

        this.addRenderableWidget(Button.builder(Component.translatable(list.showRemaining
                ? "gui.resourcetracker.edit.mode_need"
                : "gui.resourcetracker.edit.mode_count"), b -> {
            list.showRemaining = !list.showRemaining;
            b.setMessage(Component.translatable(list.showRemaining
                    ? "gui.resourcetracker.edit.mode_need"
                    : "gui.resourcetracker.edit.mode_count"));
        }).bounds(btnStartX, row2Y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable(list.showIcons
                ? "gui.resourcetracker.edit.icons_on"
                : "gui.resourcetracker.edit.icons_off"), b -> {
            list.showIcons = !list.showIcons;
            b.setMessage(Component.translatable(list.showIcons
                    ? "gui.resourcetracker.edit.icons_on"
                    : "gui.resourcetracker.edit.icons_off"));
        }).bounds(btnStartX + btnW + btnGap, row2Y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(getColumnsButtonText(list.columns), b -> {
            list.columns = (list.columns + 1) % 6;
            b.setMessage(getColumnsButtonText(list.columns));
        }).bounds(btnStartX + (btnW + btnGap) * 2, row2Y, btnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.edit.reset"), b -> resetSettings())
                .bounds(btnStartX + (btnW + btnGap) * 3, row2Y, resetW, 20).build());

        int row3Y = row2Y + 37;
        int groupW = 124;
        int groupGap = 20;
        int row3Width = (groupW * 3) + (groupGap * 2);
        int colorStartX = centerX - (row3Width / 2);

        Component[] headers = {
                Component.translatable("gui.resourcetracker.edit.color_text"),
                Component.translatable("gui.resourcetracker.edit.color_title"),
                Component.translatable("gui.resourcetracker.edit.color_bg")
        };
        String[] fLabels = {"R", "G", "B", "Alpha"};
        int[] fColors = {0xFFFF4444, 0xFF44FF44, 0xFF4488FF, 0xFFCCCCCC};
        int[] fWidths = {26, 26, 26, 34};

        for (int i = 0; i < 3; i++) {
            int cx = colorStartX + i * (groupW + groupGap);
            int labelW = font.width(headers[i]);
            labels.add(new LabelData(headers[i], cx + (groupW - labelW) / 2, row3Y - 12, 0xFFFFFFFF));

            int fStartX = cx;
            for (int j = 0; j < 4; j++) {
                int lw = font.width(fLabels[j]);
                labels.add(new LabelData(Component.literal(fLabels[j]), fStartX + (fWidths[j] - lw) / 2, row3Y, fColors[j]));
                fStartX += fWidths[j] + 4;
            }

            EditBox[] fields = new EditBox[4];
            int color = getColorForIndex(i);
            int[] vals = {(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, getAlphaForIndex(i)};

            fStartX = cx;
            for (int j = 0; j < 4; j++) {
                EditBox f = new EditBox(font, fStartX, row3Y + 12, fWidths[j], 14, Component.empty());
                f.setValue(String.valueOf(vals[j]));
                f.setMaxLength(3);
                f.setTextColor(0xFFFFFFFF);
                int colorGroup = i;
                f.setResponder(s -> applyInlineColor(colorGroup));
                this.addRenderableWidget(f);
                fields[j] = f;
                fStartX += fWidths[j] + 4;
            }
            if (i == 0) {
                textRgbA = fields;
            } else if (i == 1) {
                titleRgbA = fields;
            } else {
                bgRgbA = fields;
            }
        }

        listAreaY = row3Y + 40;
        int bottomGap = 44;
        boxWidth = 220;
        boxHeight = this.height - listAreaY - bottomGap;
        int midGap = 15;
        int totalListsWidth = (boxWidth * 2) + midGap;
        leftBoxX = centerX - (totalListsWidth / 2);
        rightBoxX = leftBoxX + boxWidth + midGap;

        searchField = new EditBox(font, leftBoxX + 6, listAreaY + 7, boxWidth - 38, 16,
                Component.translatable("gui.resourcetracker.edit.search"));
        searchField.setResponder(this::updateSearch);
        searchField.setBordered(false);
        searchField.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(searchField);

        trackedSearchField = new EditBox(font, rightBoxX + 6, listAreaY + 7, boxWidth - 38, 16,
                Component.translatable("gui.resourcetracker.edit.search"));
        trackedSearchField.setResponder(this::updateTrackedSearch);
        trackedSearchField.setBordered(false);
        trackedSearchField.setTextColor(0xFFFFFFFF);
        this.addRenderableWidget(trackedSearchField);

        addLabel(Component.translatable("gui.resourcetracker.edit.available_items"), leftBoxX, boxWidth, listAreaY - 9, 0xFFFFFFFF);
        addLabel(Component.translatable("gui.resourcetracker.edit.tracked_items"), rightBoxX, boxWidth, listAreaY - 9, 0xFFFFFFFF);

        int botBtnW = 100;
        int botBtnGap = 15;
        int botRowW = botBtnW + botBtnGap + botBtnW;
        int botStartX = centerX - (botRowW / 2);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.edit.clear"), b -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                list.items.clear();
                                refreshCountWidgets();
                                updateTrackedSearch(trackedSearchField.getValue());
                                TrackerConfig.save();
                            }
                            this.minecraft.setScreen(EditScreen.this);
                        },
                        Component.translatable("gui.resourcetracker.edit.clear"),
                        Component.translatable("gui.resourcetracker.edit.clear_confirm")
                ));
            }
        }).bounds(botStartX, this.height - 30, botBtnW, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(botStartX + botBtnW + botBtnGap, this.height - 30, botBtnW, 20).build());

        refreshCountWidgets();
        updateSearch(searchField.getValue());
        updateTrackedSearch("");
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);

        RenderUtils.drawBoxFill(context, leftBoxX, listAreaY, boxWidth, boxHeight);
        renderSearchBar(context, leftBoxX, listAreaY, boxWidth, searchField);
        renderItemList(context, mouseX, mouseY, leftBoxX, listAreaY, boxWidth, boxHeight, filteredItems, scrollLeft);

        RenderUtils.drawBoxFill(context, rightBoxX, listAreaY, boxWidth, boxHeight);
        renderSearchBar(context, rightBoxX, listAreaY, boxWidth, trackedSearchField);
        renderAddedList(context, mouseX, mouseY, rightBoxX, listAreaY, boxWidth, boxHeight);

        super.extractRenderState(context, mouseX, mouseY, delta);

        for (LabelData label : labels) {
            context.text(font, label.text, label.x, label.y, label.color, true);
        }

        RenderUtils.drawBoxOutline(context, leftBoxX, listAreaY, boxWidth, boxHeight);
        RenderUtils.drawBoxOutline(context, rightBoxX, listAreaY, boxWidth, boxHeight);

        if (hoveredTooltipText != null) {
            VersionCompat.setTooltip(context, font, hoveredTooltipText, mouseX, mouseY);
            hoveredTooltipText = null;
        }
    }

    private void resetSettings() {
        TrackerConfig.applyDefaults(list);
        minecraft.setScreen(new EditScreen(parent, list));
    }

    private Component getColumnsButtonText(int columns) {
        if (columns <= 0) {
            return Component.translatable("gui.resourcetracker.edit.columns_auto");
        }
        return Component.translatable("gui.resourcetracker.edit.columns", columns);
    }

    private void refreshCountWidgets() {
        for (EditBox widget : itemCountFields.values()) {
            this.removeWidget(widget);
        }

        Map<TrackerConfig.TrackedItem, EditBox> newMap = new LinkedHashMap<>();
        for (TrackerConfig.TrackedItem item : list.items) {
            EditBox widget = itemCountFields.get(item);
            if (widget == null) {
                widget = new EditBox(font, 0, 0, 35, 14, Component.translatable("gui.resourcetracker.edit.count_field"));
                widget.setValue(String.valueOf(item.targetCount));
                widget.setTextColor(0xFFFFFFFF);
                widget.setResponder(val -> {
                    try {
                        int v = Integer.parseInt(val);
                        item.targetCount = Math.max(1, Math.min(99999, v));
                    } catch (NumberFormatException ignored) {
                    }
                });
            }
            newMap.put(item, widget);
            this.addRenderableWidget(widget);
        }
        itemCountFields.clear();
        itemCountFields.putAll(newMap);
    }

    private void updateSearch(String query) {
        filteredItems.clear();
        String q = query.toLowerCase().trim();

        if (q.isEmpty()) {
            filteredItems.addAll(availableItems);
        } else {
            List<Item> startsWith = new ArrayList<>();
            List<Item> contains = new ArrayList<>();

            for (Item item : availableItems) {
                String name = itemDisplayNames.getOrDefault(item, "").toLowerCase();
                String id = VersionCompat.getItemId(item).toLowerCase();
                if (name.startsWith(q) || id.startsWith(q) || id.startsWith("minecraft:" + q)) {
                    startsWith.add(item);
                } else if (name.contains(q) || id.contains(q)) {
                    contains.add(item);
                }
            }
            filteredItems.addAll(startsWith);
            filteredItems.addAll(contains);
        }
        scrollLeft = 0;
    }

    private void updateTrackedSearch(String query) {
        filteredTrackedItems.clear();
        String q = query.toLowerCase().trim();

        if (q.isEmpty()) {
            filteredTrackedItems.addAll(list.items);
        } else {
            for (TrackerConfig.TrackedItem trackedItem : list.items) {
                if (!trackedItem.isValid()) continue;
                if (trackedItem.getDisplayName().toLowerCase().contains(q)) {
                    filteredTrackedItems.add(trackedItem);
                }
            }
        }
        scrollRight = 0;
    }

    private void renderItemList(GuiGraphicsExtractor context, int mx, int my, int x, int y, int w, int h,
            List<Item> items, double scroll) {
        int startDisplayY = y + SEARCH_HEIGHT + 1;
        int displayHeight = h - SEARCH_HEIGHT - 1;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = ITEM_ROW_HEIGHT;
        int startY = (int) (startDisplayY - scroll + 2);

        Item hoveredItem = null;

        for (int i = 0; i < items.size(); i++) {
            int cy = startY + (i * itemH);
            if (cy + itemH < startDisplayY || cy > y + h) continue;

            Item item = items.get(i);
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + itemH && my >= startDisplayY;

            if (hover) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, 0x30FFFFFF);
                hoveredItem = item;
            }

            context.item(searchStackCache.computeIfAbsent(item, ItemStack::new), x + 4, cy + 6);

            String name = RenderUtils.shortenText(font, itemDisplayNames.getOrDefault(item, VersionCompat.getItemName(item)), w - 28);
            context.text(font, Component.literal(name), x + 24, cy + 10, 0xFFFFFFFF, true);
        }

        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, items.size() * itemH + 6, scroll);

        if (hoveredItem != null) {
            hoveredTooltipText = searchStackCache.computeIfAbsent(hoveredItem, ItemStack::new).getHoverName();
        }
    }

    private void renderAddedList(GuiGraphicsExtractor context, int mx, int my, int x, int y, int w, int h) {
        int startDisplayY = y + SEARCH_HEIGHT + 1;
        int displayHeight = h - SEARCH_HEIGHT - 1;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = ITEM_ROW_HEIGHT;
        int startY = (int) (startDisplayY - scrollRight + 2);

        TrackerConfig.TrackedItem hoveredTracked = null;

        for (EditBox widget : itemCountFields.values()) {
            widget.setVisible(false);
        }

        for (int i = 0; i < filteredTrackedItems.size(); i++) {
            TrackerConfig.TrackedItem trackedItem = filteredTrackedItems.get(i);
            int cy = startY + (i * itemH);

            EditBox widget = itemCountFields.get(trackedItem);
            boolean isVisible = cy >= startDisplayY && cy + itemH <= y + h - 2;

            if (widget != null && isVisible) {
                widget.setVisible(true);
                widget.setX(x + w - 75);
                widget.setY(cy + 7);
            }

            if (!isVisible) continue;

            boolean valid = trackedItem.isValid();
            boolean hover = mx >= x && mx < x + w - 50 && my >= cy && my < cy + itemH && my >= startDisplayY;
            if (hover) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, valid ? 0x10FFFFFF : 0x20FF5555);
                hoveredTracked = trackedItem;
            }

            if (valid) {
                context.item(trackedItem.getStack(), x + 4, cy + 6);
            } else {
                context.text(font, Component.literal("!"), x + 9, cy + 10, 0xFFFF5555, true);
            }
            String display = valid ? trackedItem.getDisplayName() : "Invalid: " + trackedItem.itemId;
            String txt = RenderUtils.shortenText(font, display, w - 90 - 24);
            context.text(font, Component.literal(txt), x + 24, cy + 10, valid ? 0xFFFFFFFF : 0xFFFF7777, true);

            int crossX = x + w - 29;
            int crossY = cy + 2;
            boolean crossHover = mx >= crossX && mx < crossX + 24 && my >= crossY && my < crossY + 24;
            int crossColor = crossHover ? 0xFFFF5555 : 0xFF888888;
            drawPixelIcon24(context, crossX, crossY, crossColor, PixelIcons.CROSS);
        }

        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight,
                filteredTrackedItems.size() * itemH + 6, scrollRight);

        if (hoveredTracked != null) {
            hoveredTooltipText = hoveredTracked.isValid() ? hoveredTracked.getStack().getHoverName() : Component.literal(hoveredTracked.itemId);
        }
    }

    private void renderSearchBar(GuiGraphicsExtractor context, int x, int y, int w, EditBox field) {
        context.fill(x + 1, y + 1, x + w - 1, y + SEARCH_HEIGHT - 1, 0x40000000);
        context.fill(x, y + SEARCH_HEIGHT, x + w, y + SEARCH_HEIGHT + 1, 0xFF555555);
        drawPixelIcon24(context, x + w - 25, y + ((SEARCH_HEIGHT - PIXEL_ICON_SIZE) / 2), 0xFF888888, PixelIcons.SEARCH);

        if (field.getValue().isEmpty() && !field.isFocused()) {
            int textY = y + (SEARCH_HEIGHT - 8) / 2;
            context.text(font, Component.translatable("gui.resourcetracker.edit.search_hint"), x + 6, textY, 0xFF666666, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }

        double mx = event.x();
        double my = event.y();
        int listContentY = listAreaY + SEARCH_HEIGHT;

        if (mx > leftBoxX + boxWidth - 10 && mx < leftBoxX + boxWidth
                && my >= listContentY && my < listAreaY + boxHeight) {
            isDraggingScrollLeft = true;
            return true;
        }

        if (mx > rightBoxX + boxWidth - 10 && mx < rightBoxX + boxWidth
                && my >= listContentY && my < listAreaY + boxHeight) {
            isDraggingScrollRight = true;
            return true;
        }

        if (mx >= leftBoxX && mx < leftBoxX + boxWidth - 10 && my >= listContentY && my < listAreaY + boxHeight) {
            int idx = (int) ((my - listContentY + scrollLeft) / ITEM_ROW_HEIGHT);
            if (idx >= 0 && idx < filteredItems.size()) {
                addItem(filteredItems.get(idx));
                updateTrackedSearch(trackedSearchField.getValue());
                return true;
            }
        }

        if (mx >= rightBoxX && mx < rightBoxX + boxWidth - 10 && my >= listContentY && my < listAreaY + boxHeight) {
            int idx = (int) ((my - listContentY + scrollRight) / ITEM_ROW_HEIGHT);
            if (idx >= 0 && idx < filteredTrackedItems.size()) {
                int itemY = listContentY + (idx * ITEM_ROW_HEIGHT) - (int) scrollRight + 2;
                int crossX = rightBoxX + boxWidth - 29;
                int crossY = itemY + 2;

                if (mx >= crossX && mx < crossX + 24 && my >= crossY && my < crossY + 24) {
                    TrackerConfig.TrackedItem toRemove = filteredTrackedItems.get(idx);
                    list.items.remove(toRemove);
                    refreshCountWidgets();
                    updateTrackedSearch(trackedSearchField.getValue());
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (isDraggingScrollLeft || isDraggingScrollRight) {
            updateScrollbarDrag(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        isDraggingScrollLeft = false;
        isDraggingScrollRight = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount;
        int listContentY = listAreaY + SEARCH_HEIGHT + 2;
        int listVisibleH = boxHeight - SEARCH_HEIGHT - 2;

        if (mouseX >= leftBoxX && mouseX <= leftBoxX + boxWidth
                && mouseY >= listContentY && mouseY <= listAreaY + boxHeight) {
            double contentH = filteredItems.size() * ITEM_ROW_HEIGHT + 6;
            if (contentH > listVisibleH) {
                scrollLeft = Mth.clamp(scrollLeft - (amount * ITEM_ROW_HEIGHT), 0, contentH - listVisibleH);
                return true;
            }
        }

        if (mouseX >= rightBoxX && mouseX <= rightBoxX + boxWidth
                && mouseY >= listContentY && mouseY <= listAreaY + boxHeight) {
            double contentH = filteredTrackedItems.size() * ITEM_ROW_HEIGHT + 6;
            if (contentH > listVisibleH) {
                scrollRight = Mth.clamp(scrollRight - (amount * ITEM_ROW_HEIGHT), 0, contentH - listVisibleH);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void updateScrollbarDrag(double mouseY) {
        int listVisibleH = boxHeight - SEARCH_HEIGHT;
        if (isDraggingScrollLeft) {
            double contentH = filteredItems.size() * ITEM_ROW_HEIGHT + 6;
            if (contentH > listVisibleH) {
                double pct = (mouseY - (listAreaY + SEARCH_HEIGHT)) / (double) listVisibleH;
                scrollLeft = Mth.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
            }
        }
        if (isDraggingScrollRight) {
            double contentH = filteredTrackedItems.size() * ITEM_ROW_HEIGHT + 6;
            if (contentH > listVisibleH) {
                double pct = (mouseY - (listAreaY + SEARCH_HEIGHT)) / (double) listVisibleH;
                scrollRight = Mth.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
            }
        }
    }

    private void addLabel(Component text, int areaX, int areaW, int y, int color) {
        int textWidth = font.width(text);
        int centeredX = areaX + (areaW - textWidth) / 2;
        labels.add(new LabelData(text, centeredX, y, color));
    }

    private EditBox createSmallField(int x, int y, int w, Object val, Consumer<String> onChange) {
        EditBox field = new EditBox(font, x, y, w, 14, Component.empty());
        field.setValue(String.valueOf(val));
        field.setResponder(onChange);
        return field;
    }

    private int getAlphaForIndex(int index) {
        return switch (index) {
            case 0 -> (list.textColor >> 24) & 0xFF;
            case 1 -> (list.nameColor >> 24) & 0xFF;
            case 2 -> (list.backgroundColor >> 24) & 0xFF;
            default -> 255;
        };
    }

    private void setAlphaForIndex(int index, int alpha) {
        int rgb;
        switch (index) {
            case 0 -> {
                rgb = list.textColor & 0xFFFFFF;
                list.textColor = (alpha << 24) | rgb;
            }
            case 1 -> {
                rgb = list.nameColor & 0xFFFFFF;
                list.nameColor = (alpha << 24) | rgb;
            }
            case 2 -> {
                rgb = list.backgroundColor & 0xFFFFFF;
                list.backgroundColor = (alpha << 24) | rgb;
            }
        }
    }

    private int getColorForIndex(int index) {
        return switch (index) {
            case 0 -> list.textColor;
            case 1 -> list.nameColor;
            case 2 -> list.backgroundColor;
            default -> 0xFFFFFF;
        };
    }

    private void setColorForIndex(int index, int rgb) {
        int alpha;
        switch (index) {
            case 0 -> {
                alpha = (list.textColor >> 24) & 0xFF;
                list.textColor = (alpha << 24) | (rgb & 0xFFFFFF);
            }
            case 1 -> {
                alpha = (list.nameColor >> 24) & 0xFF;
                list.nameColor = (alpha << 24) | (rgb & 0xFFFFFF);
            }
            case 2 -> {
                alpha = (list.backgroundColor >> 24) & 0xFF;
                list.backgroundColor = (alpha << 24) | (rgb & 0xFFFFFF);
            }
        }
    }

    private void applyInlineColor(int index) {
        EditBox[] fields = index == 0 ? textRgbA : index == 1 ? titleRgbA : bgRgbA;
        if (fields == null || fields[0] == null) return;
        try {
            int r = clamp(Integer.parseInt(fields[0].getValue()));
            int g = clamp(Integer.parseInt(fields[1].getValue()));
            int b = clamp(Integer.parseInt(fields[2].getValue()));
            int a = clamp(Integer.parseInt(fields[3].getValue()));
            int rgb = (r << 16) | (g << 8) | b;
            setColorForIndex(index, rgb);
            setAlphaForIndex(index, a);
        } catch (NumberFormatException ignored) {
        }
    }

    private int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    @Override
    public void onClose() {
        TrackerConfig.save();
        this.minecraft.setScreen(parent);
    }

    private void addItem(Item item) {
        String id = VersionCompat.getItemId(item);
        for (TrackerConfig.TrackedItem trackedItem : list.items) {
            if (trackedItem.itemId.equals(id)) {
                return;
            }
        }

        list.items.add(new TrackerConfig.TrackedItem(id, 1));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
        }
        refreshCountWidgets();
    }

    private static void drawPixelIcon24(GuiGraphicsExtractor context, int x, int y, int color, int[] pixels) {
        if (pixels == null) return;
        for (int pixel : pixels) {
            int px = (pixel >> 8) & 0xFF;
            int py = pixel & 0xFF;
            if (px >= 0 && px < PIXEL_ICON_SIZE && py >= 0 && py < PIXEL_ICON_SIZE) {
                context.fill(x + px, y + py, x + px + 1, y + py + 1, color);
            }
        }
    }
}
