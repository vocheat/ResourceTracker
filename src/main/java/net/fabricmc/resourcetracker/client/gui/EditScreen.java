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
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private TextFieldWidget textR, textG, textB, textA;
    private TextFieldWidget nameR, nameG, nameB, nameA;
    private TextFieldWidget bgR, bgG, bgB, bgA;
    private TextFieldWidget xField, yField, scaleField;

    // Lists
    private final List<Item> availableItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private TextFieldWidget searchField;

    // Cache for item count text fields
    private final Map<TrackerConfig.TrackedItem, TextFieldWidget> itemCountFields = new HashMap<>();

    private double scrollLeft = 0;
    private double scrollRight = 0;

    // Layout
    private int listAreaY;
    private int leftBoxX, rightBoxX, boxWidth, boxHeight;
    private final int searchHeight = 18;

    // Mouse State
    private boolean wasMouseDown = false;
    private boolean isDraggingScrollLeft = false;
    private boolean isDraggingScrollRight = false;

    // Labels
    private final List<LabelData> labels = new ArrayList<>();

    private record LabelData(Text text, int x, int y, int color) {}

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

        // Populate available items if empty
        if (availableItems.isEmpty()) {
            Registries.ITEM.stream().forEach(availableItems::add);
        }

        int w = this.width;
        int h = this.height;
        int centerX = w / 2;

        int row1Y = 30;
        int row1Width = 120 + 10 + 35 + 5 + 35 + 5 + 35 + 10 + 70 + 10 + 60;
        int startX = centerX - (row1Width / 2);

        // Name Field
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.list_name"), startX, 120, row1Y - 12);
        TextFieldWidget nameField = new TextFieldWidget(textRenderer, startX, row1Y, 120, 16, Text.translatable("gui.resourcetracker.edit.list_name"));
        nameField.setText(list.name);
        nameField.setChangedListener(s -> {
            list.name = s;
            TrackerConfig.save();
        });
        this.addDrawableChild(nameField);

        int curX = startX + 130;

        // Position X Field
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.pos_x"), curX, 30, row1Y - 12);
        xField = createSmallField(curX, row1Y, 30, list.x, s -> {
            try {
                list.x = Integer.parseInt(s);
                TrackerConfig.save();
            } catch (Exception e) {}
        });
        this.addDrawableChild(xField);
        curX += 35;

        // Position Y Field
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.pos_y"), curX, 30, row1Y - 12);
        yField = createSmallField(curX, row1Y, 30, list.y, s -> {
            try {
                list.y = Integer.parseInt(s);
                TrackerConfig.save();
            } catch (Exception e) {}
        });
        this.addDrawableChild(yField);
        curX += 35;

        // Scale Field
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.scale"), curX, 30, row1Y - 12);
        scaleField = createSmallField(curX, row1Y, 30, String.valueOf(list.scale), s -> {
            try {
                list.scale = Float.parseFloat(s);
                TrackerConfig.save();
            } catch (Exception e) {}
        });
        this.addDrawableChild(scaleField);
        curX += 40;

        // Mode Button (Count vs Needed)
        this.addDrawableChild(ButtonWidget.builder(Text.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"), b -> {
            list.showRemaining = !list.showRemaining;
            b.setMessage(Text.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"));
            TrackerConfig.save();
        }).dimensions(curX, row1Y, 70, 16).build());
        curX += 75;

        // Icons Toggle Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"), b -> {
            list.showIcons = !list.showIcons;
            b.setMessage(Text.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"));
            TrackerConfig.save();
        }).dimensions(curX, row1Y, 70, 16).build());
        curX += 75;

        // Reset Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.edit.reset"), b -> resetSettings())
                .dimensions(curX, row1Y, 60, 16).build());


        int row2Y = row1Y + 35;
        int groupWidth = 110;
        int row2Width = groupWidth + 20 + groupWidth + 20 + groupWidth;
        int colorStartX = centerX - (row2Width / 2);

        // Text Color Configuration
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.color_text"), colorStartX, groupWidth, row2Y - 12);
        textR = createColorField(colorStartX, row2Y, (list.textColor >> 16) & 0xFF);
        textG = createColorField(colorStartX + 28, row2Y, (list.textColor >> 8) & 0xFF);
        textB = createColorField(colorStartX + 56, row2Y, list.textColor & 0xFF);
        int ta = (list.textColor >> 24) & 0xFF;
        if (ta == 0 && list.textColor != 0) ta = 255;
        else if (list.textColor == 0) ta = 255;
        textA = createColorField(colorStartX + 84, row2Y, ta);
        this.addDrawableChild(textR);
        this.addDrawableChild(textG);
        this.addDrawableChild(textB);
        this.addDrawableChild(textA);

        int titleX = colorStartX + 130;

        // Title Color Configuration
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.color_title"), titleX, groupWidth, row2Y - 12);
        nameR = createColorField(titleX, row2Y, (list.nameColor >> 16) & 0xFF);
        nameG = createColorField(titleX + 28, row2Y, (list.nameColor >> 8) & 0xFF);
        nameB = createColorField(titleX + 56, row2Y, list.nameColor & 0xFF);
        int na = (list.nameColor >> 24) & 0xFF;
        if (na == 0 && list.nameColor != 0) na = 255;
        else if (list.nameColor == 0) na = 255;
        nameA = createColorField(titleX + 84, row2Y, na);
        this.addDrawableChild(nameR);
        this.addDrawableChild(nameG);
        this.addDrawableChild(nameB);
        this.addDrawableChild(nameA);

        int bgX = titleX + 130;

        // Background Color Configuration
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.color_bg"), bgX, groupWidth, row2Y - 12);
        bgR = createColorField(bgX, row2Y, (list.backgroundColor >> 16) & 0xFF);
        bgG = createColorField(bgX + 28, row2Y, (list.backgroundColor >> 8) & 0xFF);
        bgB = createColorField(bgX + 56, row2Y, list.backgroundColor & 0xFF);
        bgA = createColorField(bgX + 84, row2Y, (list.backgroundColor >> 24) & 0xFF);
        this.addDrawableChild(bgR);
        this.addDrawableChild(bgG);
        this.addDrawableChild(bgB);
        this.addDrawableChild(bgA);

        // List Areas Calculation
        listAreaY = row2Y + 45;
        int bottomGap = 35;
        boxWidth = 220;
        boxHeight = h - listAreaY - bottomGap - 5;
        int midGap = 15;
        int totalListsWidth = (boxWidth * 2) + midGap;
        leftBoxX = centerX - (totalListsWidth / 2);
        rightBoxX = leftBoxX + boxWidth + midGap;

        // Search Field
        searchField = new TextFieldWidget(textRenderer, leftBoxX + 2, listAreaY + 2, boxWidth - 18, 14, Text.translatable("gui.resourcetracker.edit.search"));
        searchField.setChangedListener(this::updateSearch);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(0xFFFFFFFF);
        this.addDrawableChild(searchField);

        // Headers
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.search_hint"), leftBoxX, boxWidth, listAreaY - 14);
        addCenteredLabel(Text.translatable("gui.resourcetracker.edit.tracked_items"), rightBoxX, boxWidth, listAreaY - 14);

        // Clear List Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.edit.clear"), b -> {
            list.items.clear();
            TrackerConfig.save();
            refreshCountWidgets();
        }).dimensions(rightBoxX + boxWidth - 60, listAreaY - 19, 60, 15).build());

        // Done Button
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.resourcetracker.done"), b -> client.setScreen(parent))
                .dimensions(centerX - 50, h - 25, 100, 20).build());

        refreshCountWidgets();
        updateSearch(searchField.getText());
        updateColors();
    }

    /**
     * Resets the current list configuration to defaults.
     */
    private void resetSettings() {
        // 1. Create a fresh list instance
        // This automatically picks up default values from TrackerConfig class
        TrackerConfig.TrackingList defaultList = new TrackerConfig.TrackingList();

        // 2. Copy settings from default list to current list
        list.x = defaultList.x;
        list.y = defaultList.y;
        list.scale = defaultList.scale;
        list.showRemaining = defaultList.showRemaining;
        list.showIcons = defaultList.showIcons;

        // Copy colors
        list.textColor = defaultList.textColor;
        list.nameColor = defaultList.nameColor;
        list.backgroundColor = defaultList.backgroundColor;

        // 3. Update UI text fields (coordinates, scale)
        xField.setText(String.valueOf(list.x));
        yField.setText(String.valueOf(list.y));
        scaleField.setText(String.valueOf(list.scale));

        // 4. Update color fields (extract ARGB components)
        updateColorFields(textR, textG, textB, textA, list.textColor);
        updateColorFields(nameR, nameG, nameB, nameA, list.nameColor);
        updateColorFields(bgR, bgG, bgB, bgA, list.backgroundColor);

        // 5. Save and reload the screen
        TrackerConfig.save();
        client.setScreen(new EditScreen(parent, list));
    }

    /**
     * Helper to populate 4 separate color fields from one integer value.
     */
    private void updateColorFields(TextFieldWidget r, TextFieldWidget g, TextFieldWidget b, TextFieldWidget a, int color) {
        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        // Fix for old color formats (if alpha is 0 but color is not transparent/black, assume full opacity)
        if (alpha == 0 && color != 0) alpha = 255;

        // If color is effectively transparent but has RGB values (e.g. 0xFFFFFF with 0 alpha), fix it
        // In TrackerConfig, textColor=0xFFFFFF (no alpha bits). We correct it here:
        if (alpha == 0 && (red != 0 || green != 0 || blue != 0)) alpha = 255;

        r.setText(String.valueOf(red));
        g.setText(String.valueOf(green));
        b.setText(String.valueOf(blue));
        a.setText(String.valueOf(alpha));
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
                    try {
                        item.targetCount = Integer.parseInt(val);
                        TrackerConfig.save();
                    } catch (NumberFormatException ignored) {}
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

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, width, height, 0xA0000000);

        handleInput(mouseX, mouseY);

        // Draw Left Box (Search Results)
        drawBox(context, leftBoxX, listAreaY, boxWidth, boxHeight);
        context.fill(leftBoxX, listAreaY + searchHeight, leftBoxX + boxWidth, listAreaY + searchHeight + 1, 0xFF555555);
        drawMagnifyingGlass(context, leftBoxX + boxWidth - 14, listAreaY + 4);
        renderItemList(context, mouseX, mouseY, leftBoxX, listAreaY, boxWidth, boxHeight, filteredItems, scrollLeft);

        // Draw Right Box (Added Items)
        drawBox(context, rightBoxX, listAreaY, boxWidth, boxHeight);
        renderAddedList(context, mouseX, mouseY, rightBoxX, listAreaY, boxWidth, boxHeight);

        super.render(context, mouseX, mouseY, delta);

        // Draw Labels
        for (LabelData l : labels) {
            int txtW = textRenderer.getWidth(l.text);
            context.fill(l.x - 2, l.y - 2, l.x + txtW + 2, l.y + 10, 0xFF222222);
            context.drawText(textRenderer, l.text, l.x, l.y, l.color, true);
        }
    }

    private void renderItemList(DrawContext context, int mx, int my, int x, int y, int w, int h, List<Item> items, double scroll) {
        int startDisplayY = y + searchHeight + 1;
        int displayHeight = h - searchHeight - 1;

        context.enableScissor(x, startDisplayY, x + w, y + h);
        int itemH = 20;
        int startY = (int) (startDisplayY - scroll + 2);

        // 1. Variable to store item under mouse
        Item hoveredItem = null;

        for (int i = 0; i < items.size(); i++) {
            int cy = startY + (i * itemH);
            if (cy + itemH < startDisplayY || cy > y + h) continue;

            Item item = items.get(i);
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + itemH;

            if (hover) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, 0x30FFFFFF);
                // 2. If hovered, store the item
                hoveredItem = item;
            }

            context.drawItem(new ItemStack(item), x + 4, cy + 2);

            // Shorten text using helper method
            String name = item.getName().getString();
            // Text width = list width - left padding (24) - right padding (4)
            name = shortenText(textRenderer, name, w - 28);

            context.drawText(textRenderer, Text.literal(name), x + 24, cy + 6, 0xFFFFFFFF, true);
        }

        // 3. Disable scissor BEFORE drawing tooltip
        context.disableScissor();

        drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, items.size() * itemH, scroll);

        // 4. Draw tooltip on top of everything
        if (hoveredItem != null) {
            context.drawTooltip(textRenderer, hoveredItem.getName(), mx, my);
        }
    }

    private void renderAddedList(DrawContext context, int mx, int my, int x, int y, int w, int h) {
        context.enableScissor(x, y, x + w, y + h);
        int itemH = 20;
        int startY = (int) (y - scrollRight + 2);

        // 1. Variable for item under cursor
        Item hoveredItem = null;

        for (int i = 0; i < list.items.size(); i++) {
            TrackerConfig.TrackedItem ti = list.items.get(i);
            int cy = startY + (i * itemH);

            TextFieldWidget widget = itemCountFields.get(ti);
            boolean isVisible = (cy + itemH >= y && cy <= y + h);

            if (widget != null) {
                if (isVisible) {
                    widget.setVisible(true);
                    widget.setX(x + w - 65);
                    widget.setY(cy + 3);
                } else {
                    widget.setVisible(false);
                }
            }

            if (!isVisible) continue;

            Item item = Registries.ITEM.get(Identifier.of(ti.itemId));
            // Hover zone (excluding buttons on the right)
            boolean hover = mx >= x && mx < x + w - 45 && my >= cy && my < cy + itemH;

            if (hover) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, 0x10FFFFFF);
                // 2. Store item if exists
                if (item != null) {
                    hoveredItem = item;
                }
            }

            if (item != null) {
                context.drawItem(new ItemStack(item), x + 4, cy + 2);

                // Shorten text
                String txt = item.getName().getString();
                txt = shortenText(textRenderer, txt, w - 85 - 24);
                context.drawText(textRenderer, Text.literal(txt), x + 24, cy + 6, 0xFFFFFFFF, true);

                // Delete Cross
                int crossX = x + w - 20;
                int crossY = cy + 6;
                boolean crossHover = mx >= crossX - 2 && mx <= crossX + 7 && my >= crossY - 2 && my <= crossY + 7;
                int crossColor = crossHover ? 0xFFFFFFFF : 0xFFA0A0A0;
                drawPixelCross(context, crossX, crossY, crossColor);
            }
        }

        // 3. Disable scissor
        context.disableScissor();

        drawStyledScrollbar(context, x + w - 6, y, h, list.items.size() * itemH, scrollRight);

        // 4. Draw tooltip on top
        if (hoveredItem != null) {
            context.drawTooltip(textRenderer, hoveredItem.getName(), mx, my);
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
            if (mx >= leftBoxX && mx < leftBoxX + boxWidth - 10 && my >= listContentY && my < listAreaY + boxHeight) {
                int idx = (int) ((my - listContentY + scrollLeft) / itemH);
                if (idx >= 0 && idx < filteredItems.size()) {
                    addItem(filteredItems.get(idx));
                }
            }
            if (mx >= rightBoxX && mx < rightBoxX + boxWidth && my >= listAreaY && my < listAreaY + boxHeight) {
                int idx = (int) ((my - listAreaY + scrollRight) / itemH);
                if (idx >= 0 && idx < list.items.size()) {
                    int itemY = listAreaY + (idx * itemH) - (int) scrollRight + 2;
                    // Click zone for the delete cross
                    int crossX = rightBoxX + boxWidth - 20;
                    int crossY = itemY + 6;

                    if (mx >= crossX - 3 && mx <= crossX + 8 && my >= crossY - 3 && my <= crossY + 8) {
                        list.items.remove(idx);
                        TrackerConfig.save();
                        refreshCountWidgets();
                    }
                }
            }
            if (mx > leftBoxX + boxWidth - 10 && mx < leftBoxX + boxWidth) isDraggingScrollLeft = true;
            if (mx > rightBoxX + boxWidth - 10 && mx < rightBoxX + boxWidth) isDraggingScrollRight = true;
        }

        if (down) {
            int listVisibleH = boxHeight - searchHeight;
            if (isDraggingScrollLeft) {
                double contentH = filteredItems.size() * itemH;
                if (contentH > listVisibleH) {
                    double pct = (my - (listAreaY + searchHeight)) / (double) listVisibleH;
                    scrollLeft = MathHelper.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
                }
            }
            if (isDraggingScrollRight) {
                double contentH = list.items.size() * itemH;
                if (contentH > boxHeight) {
                    double pct = (my - listAreaY) / (double) boxHeight;
                    scrollRight = MathHelper.clamp(pct * contentH - (boxHeight / 2.0), 0, contentH - boxHeight);
                }
            }
        } else {
            isDraggingScrollLeft = false;
            isDraggingScrollRight = false;
        }
        wasMouseDown = down;
    }

    private void drawStyledScrollbar(DrawContext context, int x, int y, int h, int contentH, double scroll) {
        if (contentH <= h) return;
        context.fill(x, y, x + 5, y + h, 0xFF000000);
        int barH = Math.max(20, (int) ((float) h / contentH * h));
        int barY = y + (int) ((scroll / (contentH - h)) * (h - barH));
        context.fill(x, barY, x + 5, barY + barH, 0xFF888888);
    }

    private void drawMagnifyingGlass(DrawContext context, int x, int y) {
        int color = 0xFFAAAAAA;
        context.fill(x + 2, y + 2, x + 7, y + 3, color);
        context.fill(x + 2, y + 6, x + 7, y + 7, color);
        context.fill(x + 2, y + 3, x + 3, y + 6, color);
        context.fill(x + 6, y + 3, x + 7, y + 6, color);
        context.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    private void drawBox(DrawContext context, int x, int y, int w, int h) {
        // 1. Semi-transparent black background
        context.fill(x, y, x + w, y + h, 0x80000000);

        // 2. Border color
        // Changed from dark gray to White (0xFFFFFFFF) for visibility
        int color = 0xFFFFFFFF;

        // 3. Draw border lines manually
        context.fill(x, y, x + w, y + 1, color);       // Top
        context.fill(x, y + h - 1, x + w, y + h, color); // Bottom
        context.fill(x, y, x + 1, y + h, color);       // Left
        context.fill(x + w - 1, y, x + w, y + h, color); // Right
    }

    private void addCenteredLabel(Text text, int x, int width, int y) {
        int textWidth = textRenderer.getWidth(text);
        int centeredX = x + (width - textWidth) / 2;
        labels.add(new LabelData(text, centeredX, y, 0xFFFFFFFF));
    }

    private TextFieldWidget createSmallField(int x, int y, int w, Object val, java.util.function.Consumer<String> onChange) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 16, Text.empty());
        f.setText(String.valueOf(val));
        f.setChangedListener(onChange);
        return f;
    }

    private TextFieldWidget createColorField(int x, int y, int value) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, 26, 16, Text.empty());
        f.setText(String.valueOf(value));
        f.setMaxLength(3);
        f.setChangedListener(s -> updateColors());
        return f;
    }

    private void updateColors() {
        try {
            list.textColor = packColor(textR, textG, textB, textA);
            list.nameColor = packColor(nameR, nameG, nameB, nameA);
            list.backgroundColor = packColor(bgR, bgG, bgB, bgA);
            TrackerConfig.save();
        } catch (Exception ignored) {}
    }

    private int packColor(TextFieldWidget r, TextFieldWidget g, TextFieldWidget b, TextFieldWidget a) {
        int rv = clamp(parse(r));
        int gv = clamp(parse(g));
        int bv = clamp(parse(b));
        int av = clamp(parse(a));
        return (av << 24) | (rv << 16) | (gv << 8) | bv;
    }

    private int parse(TextFieldWidget w) {
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount;
        int itemH = 20;
        int listContentY = listAreaY + searchHeight + 2;
        int listVisibleH = boxHeight - searchHeight - 2;

        if (mouseX >= leftBoxX && mouseX <= leftBoxX + boxWidth && mouseY >= listContentY && mouseY <= listAreaY + boxHeight) {
            double contentH = filteredItems.size() * itemH;
            if (contentH > listVisibleH) {
                scrollLeft = MathHelper.clamp(scrollLeft - (amount * itemH), 0, contentH - listVisibleH);
                return true;
            }
        }
        if (mouseX >= rightBoxX && mouseX <= rightBoxX + boxWidth && mouseY >= listAreaY && mouseY <= listAreaY + boxHeight) {
            double contentH = list.items.size() * itemH;
            if (contentH > boxHeight) {
                scrollRight = MathHelper.clamp(scrollRight - (amount * itemH), 0, contentH - boxHeight);
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
            TrackerConfig.save();
            refreshCountWidgets();
        }
    }

    /**
     * Trims text to fit within a specific width, appending "..." if truncated.
     */
    private String shortenText(net.minecraft.client.font.TextRenderer textRenderer, String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = textRenderer.getWidth(suffix);
        // trimToWidth trims text to (width - suffix_width)
        return textRenderer.trimToWidth(text, Math.max(0, maxWidth - suffixWidth)) + suffix;
    }
}