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

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/**
 * The configuration screen for a specific tracking list.
 * <p>
 * This screen allows the user to:
 * <ul>
 * <li>Change list settings (name, position, scale).</li>
 * <li>Customize colors (Text, Header, Background).</li>
 * <li>Search for items in the registry.</li>
 * <li>Add or remove items from the tracking list.</li>
 * <li>Reset settings to default values.</li>
 * </ul>
 * </p>
 *
 * @author vocheat
 */
public class EditScreen extends Screen {
    private final Screen parent;
    private final TrackerConfig.TrackingList list;

    // Settings Fields
    private TextFieldWidget xField, yField, scaleField;

    // Lists
    private final List<Item> availableItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private final List<TrackerConfig.TrackedItem> filteredTrackedItems = new ArrayList<>();
    private TextFieldWidget searchField;
    private TextFieldWidget trackedSearchField;

    // Cache for item count text fields
    private final Map<TrackerConfig.TrackedItem, TextFieldWidget> itemCountFields = new HashMap<>();

    // Cache for ItemStacks used in search list rendering (avoids per-frame allocations)
    private final Map<Item, ItemStack> searchStackCache = new HashMap<>();

    private double scrollLeft = 0;
    private double scrollRight = 0;

    // Layout
    private int listAreaY;
    private int leftBoxX, rightBoxX, boxWidth, boxHeight;
    private final int searchHeight = 18;
    
    private Text hoveredTooltipText = null;

    // Mouse State
    private boolean wasMouseDown = false;
    private boolean isDraggingScrollLeft = false;
    private boolean isDraggingScrollRight = false;

    // Labels
    private final List<LabelData> labels = new ArrayList<>();

    private record LabelData(Text text, int x, int y, int color) {}

    // Color Fields
    private TextFieldWidget[] textRgbA = new TextFieldWidget[4];
    private TextFieldWidget[] titleRgbA = new TextFieldWidget[4];
    private TextFieldWidget[] bgRgbA = new TextFieldWidget[4];

    public EditScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(Text.translatable("gui.resourcetracker.edit.title"));
        this.parent = parent;
        this.list = list;
    }

    @Override
    protected void init() {
        this.clearChildren();
        this.labels.clear();
        this.itemCountFields.clear();

        if (availableItems.isEmpty()) {
            Registries.ITEM.stream().forEach(availableItems::add);
        }

        int w = this.width;
        int h = this.height;
        int centerX = w / 2;

        // === Row 1: Name, PosX, PosY, Scale ===
        int row1Y = 21; // Increased Y margin
        int nameW = 120, fieldW = 30, gap = 5;
        int row1Width = nameW + gap + fieldW + gap + fieldW + gap + fieldW;
        int startX = centerX - (row1Width / 2);

        addLabel(Text.translatable("gui.resourcetracker.edit.list_name"), startX, nameW, row1Y - 12, 0xFFFFFFFF);
        TextFieldWidget nameField = new TextFieldWidget(textRenderer, startX, row1Y, nameW, 14, Text.translatable("gui.resourcetracker.edit.list_name"));
        nameField.setText(list.name);
        nameField.setChangedListener(s -> list.name = s);
        this.addDrawableChild(nameField);

        int curX = startX + nameW + gap;

        addLabel(Text.translatable("gui.resourcetracker.edit.pos_x"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        xField = createSmallField(curX, row1Y, fieldW, list.x, s -> {
            try { list.x = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(xField);
        curX += fieldW + gap;

        addLabel(Text.translatable("gui.resourcetracker.edit.pos_y"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        yField = createSmallField(curX, row1Y, fieldW, list.y, s -> {
            try { list.y = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(yField);
        curX += fieldW + gap;

        addLabel(Text.translatable("gui.resourcetracker.edit.scale"), curX, fieldW, row1Y - 12, 0xFFAAAAAA);
        scaleField = createSmallField(curX, row1Y, fieldW, String.valueOf(list.scale), s -> {
            try { list.scale = Float.parseFloat(s); } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(scaleField);

        // === Row 2: Buttons ===
        int row2Y = row1Y + 21;
        int btnW = 80, btnGap = 4, resetW = 60;
        int row2Width = btnW + btnGap + btnW + btnGap + btnW + btnGap + resetW;
        int btnStartX = centerX - (row2Width / 2);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"), b -> {
            list.showRemaining = !list.showRemaining;
            b.setMessage(Text.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"));
        }).dimensions(btnStartX, row2Y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"), b -> {
            list.showIcons = !list.showIcons;
            b.setMessage(Text.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"));
        }).dimensions(btnStartX + btnW + btnGap, row2Y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(getColumnsButtonText(list.columns), b -> {
            list.columns = (list.columns + 1) % 6;
            b.setMessage(getColumnsButtonText(list.columns));
        }).dimensions(btnStartX + (btnW + btnGap) * 2, row2Y, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.edit.reset"), b -> resetSettings())
                .dimensions(btnStartX + (btnW + btnGap) * 3, row2Y, resetW, 20).build());

        // === Row 3: Colors ===
        int row3Y = row2Y + 37;
        int groupW = 124; // 4 boxes + gaps
        int groupGap = 20;
        int row3Width = (groupW * 3) + (groupGap * 2);
        int colorStartX = centerX - (row3Width / 2);

        String[] headers = {"Text Color", "Title color", "Background color"};
        String[] fLabels = {"R", "G", "B", "Alpha"};
        int[] fColors = {0xFFFF4444, 0xFF44FF44, 0xFF4488FF, 0xFFCCCCCC};
        int[] fWidths = {26, 26, 26, 34};
        
        for (int i = 0; i < 3; i++) {
            int cx = colorStartX + i * (groupW + groupGap);
            int labelW = textRenderer.getWidth(headers[i]);
            labels.add(new LabelData(Text.literal(headers[i]), cx + (groupW - labelW) / 2, row3Y - 12, 0xFFFFFFFF));
            
            // Labels for R G B Alpha
            int fStartX = cx;
            for (int j = 0; j < 4; j++) {
                int lw = textRenderer.getWidth(fLabels[j]);
                labels.add(new LabelData(Text.literal(fLabels[j]), fStartX + (fWidths[j] - lw) / 2, row3Y, fColors[j]));
                fStartX += fWidths[j] + 4;
            }

            // Fields
            TextFieldWidget[] fields = new TextFieldWidget[4];
            int color = getColorForIndex(i);
            int[] vals = { (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, getAlphaForIndex(i) };
            
            fStartX = cx;
            for (int j = 0; j < 4; j++) {
                TextFieldWidget f = new TextFieldWidget(textRenderer, fStartX, row3Y + 12, fWidths[j], 14, Text.empty());
                f.setText(String.valueOf(vals[j]));
                f.setMaxLength(3);
                f.setEditableColor(0xFFFFFFFF);
                int finalI = i; // capture group index
                f.setChangedListener(s -> applyInlineColor(finalI));
                this.addDrawableChild(f);
                fields[j] = f;
                fStartX += fWidths[j] + 4;
            }
            if (i == 0) textRgbA = fields;
            else if (i == 1) titleRgbA = fields;
            else bgRgbA = fields;
        }

        // === List Areas ===
        listAreaY = row3Y + 40;
        int bottomGap = 44;
        boxWidth = 220;
        boxHeight = h - listAreaY - bottomGap;
        int midGap = 15;
        int totalListsWidth = (boxWidth * 2) + midGap;
        leftBoxX = centerX - (totalListsWidth / 2);
        rightBoxX = leftBoxX + boxWidth + midGap;

        // Left Search Field
        searchField = new TextFieldWidget(textRenderer, leftBoxX + 4, listAreaY + 5, boxWidth - 22, 16, Text.translatable("gui.resourcetracker.edit.search"));
        searchField.setChangedListener(this::updateSearch);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(0xFFFFFFFF);
        this.addDrawableChild(searchField);

        // Right Search Field (tracked items)
        trackedSearchField = new TextFieldWidget(textRenderer, rightBoxX + 4, listAreaY + 5, boxWidth - 22, 16, Text.translatable("gui.resourcetracker.edit.search"));
        trackedSearchField.setChangedListener(this::updateTrackedSearch);
        trackedSearchField.setDrawsBackground(false);
        trackedSearchField.setEditableColor(0xFFFFFFFF);
        this.addDrawableChild(trackedSearchField);

        // Headers
        addLabel(Text.translatable("gui.resourcetracker.edit.search_hint"), leftBoxX, boxWidth, listAreaY - 9, 0xFFFFFFFF);
        addLabel(Text.translatable("gui.resourcetracker.edit.tracked_items"), rightBoxX, boxWidth, listAreaY - 9, 0xFFFFFFFF);

        // Bottom Buttons (Clear & Done)
        int botBtnW = 100, botBtnGap = 15;
        int botRowW = botBtnW + botBtnGap + botBtnW;
        int botStartX = centerX - (botRowW / 2);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.edit.clear"), b -> {
            list.items.clear();
            refreshCountWidgets();
            updateTrackedSearch(trackedSearchField.getText());
        }).dimensions(botStartX, h - 30, botBtnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.done"), b -> client.setScreen(parent))
                .dimensions(botStartX + botBtnW + botBtnGap, h - 30, botBtnW, 20).build());

        refreshCountWidgets();
        updateSearch(searchField.getText());
        updateTrackedSearch("");
    }

    /**
     * Resets the current list configuration to defaults.
     */
    private void resetSettings() {
        TrackerConfig.TrackingList defaultList = new TrackerConfig.TrackingList();

        list.x = defaultList.x;
        list.y = defaultList.y;
        list.scale = defaultList.scale;
        list.showRemaining = defaultList.showRemaining;
        list.showIcons = defaultList.showIcons;
        list.columns = defaultList.columns;

        list.textColor = defaultList.textColor;
        list.nameColor = defaultList.nameColor;
        list.backgroundColor = defaultList.backgroundColor;

        client.setScreen(new EditScreen(parent, list));
    }

    private Text getColumnsButtonText(int columns) {
        if (columns <= 0) {
            return Text.translatable("gui.resourcetracker.edit.columns_auto");
        }
        return Text.translatable("gui.resourcetracker.edit.columns", columns);
    }

    private void refreshCountWidgets() {
        for (TextFieldWidget w : itemCountFields.values()) this.remove(w);
        Map<TrackerConfig.TrackedItem, TextFieldWidget> newMap = new LinkedHashMap<>();
        for (TrackerConfig.TrackedItem item : list.items) {
            TextFieldWidget w;
            if (itemCountFields.containsKey(item)) w = itemCountFields.get(item);
            else {
                w = new TextFieldWidget(textRenderer, 0, 0, 35, 14, Text.translatable("gui.resourcetracker.edit.count_field"));
                w.setText(String.valueOf(item.targetCount));
                w.setEditableColor(0xFFFFFFFF);
                w.setChangedListener(val -> {
                    try { item.targetCount = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
                });
            }
            newMap.put(item, w);
            this.addDrawableChild(w);
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
                String name = item.getName().getString().toLowerCase();
                if (name.startsWith(q)) {
                    startsWith.add(item);
                } else if (name.contains(q)) {
                    contains.add(item);
                }
            }
            startsWith.sort(Comparator.comparing(i -> i.getName().getString()));
            contains.sort(Comparator.comparing(i -> i.getName().getString()));

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
            for (TrackerConfig.TrackedItem ti : list.items) {
                if (!ti.isValid()) continue;
                if (ti.getDisplayName().toLowerCase().contains(q)) {
                    filteredTrackedItems.add(ti);
                }
            }
        }
        scrollRight = 0;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No-op: we draw our own semi-transparent background in render()
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, width, height, 0xA0000000);

        handleInput(mouseX, mouseY);

        // Left box background
        RenderUtils.drawBoxFill(context, leftBoxX, listAreaY, boxWidth, boxHeight);
        renderSearchBar(context, leftBoxX, listAreaY, boxWidth, searchField);
        renderItemList(context, mouseX, mouseY, leftBoxX, listAreaY, boxWidth, boxHeight, filteredItems, scrollLeft);

        // Right box background
        RenderUtils.drawBoxFill(context, rightBoxX, listAreaY, boxWidth, boxHeight);
        renderSearchBar(context, rightBoxX, listAreaY, boxWidth, trackedSearchField);
        renderAddedList(context, mouseX, mouseY, rightBoxX, listAreaY, boxWidth, boxHeight);

        super.render(context, mouseX, mouseY, delta);

        // Draw Labels
        for (LabelData l : labels) {
            context.drawText(textRenderer, l.text, l.x, l.y, l.color, true);
        }

        // Draw box outlines on top of everything (only tooltips and picker render above)
        RenderUtils.drawBoxOutline(context, leftBoxX, listAreaY, boxWidth, boxHeight);
        RenderUtils.drawBoxOutline(context, rightBoxX, listAreaY, boxWidth, boxHeight);

        // Inline fields render automatically

        if (hoveredTooltipText != null) {
            context.drawTooltip(textRenderer, hoveredTooltipText, mouseX, mouseY);
            hoveredTooltipText = null;
        }
    }

    private void renderItemList(DrawContext context, int mx, int my, int x, int y, int w, int h, List<Item> items, double scroll) {
        int startDisplayY = y + searchHeight + 1;
        int displayHeight = h - searchHeight - 1;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = 20;
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

            context.drawItem(searchStackCache.computeIfAbsent(item, ItemStack::new), x + 4, cy + 2);

            String name = item.getName().getString();
            name = RenderUtils.shortenText(textRenderer, name, w - 28);

            context.drawText(textRenderer, Text.literal(name), x + 24, cy + 6, 0xFFFFFFFF, true);
        }

        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, items.size() * itemH + 6, scroll);

        if (hoveredItem != null) {
            hoveredTooltipText = hoveredItem.getName();
        }
    }

    private void renderAddedList(DrawContext context, int mx, int my, int x, int y, int w, int h) {
        int startDisplayY = y + searchHeight + 1;
        int displayHeight = h - searchHeight - 1;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = 20;
        int startY = (int) (startDisplayY - scrollRight + 2);

        TrackerConfig.TrackedItem hoveredTracked = null;

        // Hide all widgets first, then show visible ones
        for (TextFieldWidget widget : itemCountFields.values()) {
            widget.setVisible(false);
        }

        for (int i = 0; i < filteredTrackedItems.size(); i++) {
            TrackerConfig.TrackedItem ti = filteredTrackedItems.get(i);
            int cy = startY + (i * itemH);

            TextFieldWidget widget = itemCountFields.get(ti);
            boolean isVisible = (cy >= startDisplayY && cy + itemH <= y + h - 2);

            if (widget != null) {
                if (isVisible) {
                    widget.setVisible(true);
                    widget.setX(x + w - 75);
                    widget.setY(cy + 3);
                }
            }

            if (!isVisible) continue;
            if (!ti.isValid()) continue;

            // Hover zone (excluding buttons on the right)
            boolean hover = mx >= x && mx < x + w - 50 && my >= cy && my < cy + itemH && my >= startDisplayY;

            if (hover) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, 0x10FFFFFF);
                hoveredTracked = ti;
            }

            context.drawItem(ti.getStack(), x + 4, cy + 2);

            // Shorten text
            String txt = RenderUtils.shortenText(textRenderer, ti.getDisplayName(), w - 90 - 24);
            context.drawText(textRenderer, Text.literal(txt), x + 24, cy + 6, 0xFFFFFFFF, true);

            // Delete button — larger hitbox, red on hover
            int crossX = x + w - 25;
            int crossY = cy + 4;
            boolean crossHover = mx >= crossX - 4 && mx <= crossX + 9 && my >= crossY - 2 && my <= crossY + 9;
            int crossColor = crossHover ? 0xFFFF5555 : 0xFF888888;
            drawPixelCross(context, crossX, crossY + 2, crossColor);
        }

        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, filteredTrackedItems.size() * itemH + 6, scrollRight);

        if (hoveredTracked != null) {
            hoveredTooltipText = hoveredTracked.getItem().getName();
        }
    }

    private void drawPixelCross(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 1, y + 1, color);
        context.fill(x + 4, y, x + 5, y + 1, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + 3, y + 1, x + 4, y + 2, color);
        context.fill(x + 2, y + 2, x + 3, y + 3, color);
        context.fill(x + 1, y + 3, x + 2, y + 4, color);
        context.fill(x + 3, y + 3, x + 4, y + 4, color);
        context.fill(x, y + 4, x + 1, y + 5, color);
        context.fill(x + 4, y + 4, x + 5, y + 5, color);
    }

    private void handleInput(int mx, int my) {
        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), 0) == GLFW.GLFW_PRESS;
        int itemH = 20;

        if (down && !wasMouseDown) {
            int listContentY = listAreaY + searchHeight;
            // Left list click -> add item
            if (mx >= leftBoxX && mx < leftBoxX + boxWidth - 10 && my >= listContentY && my < listAreaY + boxHeight) {
                int idx = (int) ((my - listContentY + scrollLeft) / itemH);
                if (idx >= 0 && idx < filteredItems.size()) {
                    addItem(filteredItems.get(idx));
                    updateTrackedSearch(trackedSearchField.getText());
                }
            }
            // Right list click -> delete
            int rightContentY = listAreaY + searchHeight;
            if (mx >= rightBoxX && mx < rightBoxX + boxWidth && my >= rightContentY && my < listAreaY + boxHeight) {
                int idx = (int) ((my - rightContentY + scrollRight) / itemH);
                if (idx >= 0 && idx < filteredTrackedItems.size()) {
                    int itemY = rightContentY + (idx * itemH) - (int) scrollRight + 2;
                    int crossX = rightBoxX + boxWidth - 25;
                    int crossY = itemY + 4;

                    if (mx >= crossX - 4 && mx <= crossX + 9 && my >= crossY - 2 && my <= crossY + 9) {
                        TrackerConfig.TrackedItem toRemove = filteredTrackedItems.get(idx);
                        list.items.remove(toRemove);
                        refreshCountWidgets();
                        updateTrackedSearch(trackedSearchField.getText());
                    }
                }
            }
            if (mx > leftBoxX + boxWidth - 10 && mx < leftBoxX + boxWidth) isDraggingScrollLeft = true;
            if (mx > rightBoxX + boxWidth - 10 && mx < rightBoxX + boxWidth) isDraggingScrollRight = true;
        }

        if (down) {
            int listVisibleH = boxHeight - searchHeight;
            if (isDraggingScrollLeft) {
                double contentH = filteredItems.size() * itemH + 6;
                if (contentH > listVisibleH) {
                    double pct = (my - (listAreaY + searchHeight)) / (double) listVisibleH;
                    scrollLeft = MathHelper.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
                }
            }
            if (isDraggingScrollRight) {
                double contentH = filteredTrackedItems.size() * itemH + 6;
                if (contentH > listVisibleH) {
                    double pct = (my - (listAreaY + searchHeight)) / (double) listVisibleH;
                    scrollRight = MathHelper.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
                }
            }
        } else {
            isDraggingScrollLeft = false;
            isDraggingScrollRight = false;
        }
        wasMouseDown = down;
    }

    private void renderSearchBar(DrawContext context, int x, int y, int w, TextFieldWidget field) {
        // Subtle darker background for search area
        context.fill(x + 1, y + 1, x + w - 1, y + searchHeight - 1, 0x40000000);

        // Separator line
        context.fill(x, y + searchHeight, x + w, y + searchHeight + 1, 0xFF555555);

        // Magnifying glass icon
        int iconX = x + w - 14;
        int iconY = y + (searchHeight - 9) / 2;
        int ic = 0xFF888888;
        // Circle
        context.fill(iconX + 1, iconY,     iconX + 5, iconY + 1, ic);
        context.fill(iconX,     iconY + 1, iconX + 1, iconY + 5, ic);
        context.fill(iconX + 5, iconY + 1, iconX + 6, iconY + 5, ic);
        context.fill(iconX + 1, iconY + 5, iconX + 5, iconY + 6, ic);
        // Handle
        context.fill(iconX + 5, iconY + 5, iconX + 7, iconY + 6, ic);
        context.fill(iconX + 6, iconY + 6, iconX + 8, iconY + 7, ic);

        // Placeholder text "Search..." when field is empty
        if (field.getText().isEmpty() && !field.isFocused()) {
            int textY = y + (searchHeight - 8) / 2;
            context.drawText(textRenderer, Text.literal("Search..."), x + 6, textY, 0xFF666666, false);
        }
    }

    private void addLabel(Text text, int areaX, int areaW, int y, int color) {
        int textWidth = textRenderer.getWidth(text);
        int centeredX = areaX + (areaW - textWidth) / 2;
        labels.add(new LabelData(text, centeredX, y, color));
    }

    private TextFieldWidget createSmallField(int x, int y, int w, Object val, java.util.function.Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 14, Text.empty());
        f.setText(String.valueOf(val));
        f.setChangedListener(onChange);
        return f;
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
            case 0 -> { rgb = list.textColor & 0xFFFFFF; list.textColor = (alpha << 24) | rgb; }
            case 1 -> { rgb = list.nameColor & 0xFFFFFF; list.nameColor = (alpha << 24) | rgb; }
            case 2 -> { rgb = list.backgroundColor & 0xFFFFFF; list.backgroundColor = (alpha << 24) | rgb; }
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
            case 0 -> { alpha = (list.textColor >> 24) & 0xFF; list.textColor = (alpha << 24) | (rgb & 0xFFFFFF); }
            case 1 -> { alpha = (list.nameColor >> 24) & 0xFF; list.nameColor = (alpha << 24) | (rgb & 0xFFFFFF); }
            case 2 -> { alpha = (list.backgroundColor >> 24) & 0xFF; list.backgroundColor = (alpha << 24) | (rgb & 0xFFFFFF); }
        }
    }

    private void applyInlineColor(int index) {
        TextFieldWidget[] fields = index == 0 ? textRgbA : index == 1 ? titleRgbA : bgRgbA;
        if (fields == null || fields[0] == null) return;
        try {
            int r = clamp(Integer.parseInt(fields[0].getText()));
            int g = clamp(Integer.parseInt(fields[1].getText()));
            int b = clamp(Integer.parseInt(fields[2].getText()));
            int a = clamp(Integer.parseInt(fields[3].getText()));
            int rgb = (r << 16) | (g << 8) | b;
            setColorForIndex(index, rgb);
            setAlphaForIndex(index, a);
        } catch (NumberFormatException ignored) {}
    }

    private int parseNum(TextFieldWidget w) {
        try {
            return Integer.parseInt(w.getText());
        } catch (Exception e) {
            return 0;
        }
    }

    private int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    @Override
    public void close() {
        TrackerConfig.save();
        super.close();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount;
        int itemH = 20;
        int listContentY = listAreaY + searchHeight + 2;
        int listVisibleH = boxHeight - searchHeight - 2;

        if (mouseX >= leftBoxX && mouseX <= leftBoxX + boxWidth && mouseY >= listContentY && mouseY <= listAreaY + boxHeight) {
            double contentH = filteredItems.size() * itemH + 6;
            if (contentH > listVisibleH) {
                scrollLeft = MathHelper.clamp(scrollLeft - (amount * itemH), 0, contentH - listVisibleH);
                return true;
            }
        }
        if (mouseX >= rightBoxX && mouseX <= rightBoxX + boxWidth && mouseY >= listContentY && mouseY <= listAreaY + boxHeight) {
            double contentH = filteredTrackedItems.size() * itemH + 6;
            if (contentH > listVisibleH) {
                scrollRight = MathHelper.clamp(scrollRight - (amount * itemH), 0, contentH - listVisibleH);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    private void addItem(Item item) {
        String id = Registries.ITEM.getId(item).toString();
        boolean found = false;
        for (TrackerConfig.TrackedItem ti : list.items) {
            if (ti.itemId.equals(id)) {
                found = true;
                break;
            }
        }
        if (!found) {
            list.items.add(new TrackerConfig.TrackedItem(id, 0));
            refreshCountWidgets();
        }
    }
}