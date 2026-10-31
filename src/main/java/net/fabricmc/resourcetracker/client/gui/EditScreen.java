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
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.client.render.HudRenderer;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private EditBox xField, yField, scaleField;

    // Lists
    private final List<Item> availableItems = new ArrayList<>();
    private final List<Item> filteredItems = new ArrayList<>();
    private final List<TrackerConfig.TrackedItem> filteredTrackedItems = new ArrayList<>();
    private EditBox searchField;
    private EditBox trackedSearchField;

    // Cache for item count text fields
    private final Map<TrackerConfig.TrackedItem, EditBox> itemCountFields = new HashMap<>();

    // Cache for ItemStacks used in search list rendering (avoids per-frame allocations)
    private final Map<Item, ItemStack> searchStackCache = new HashMap<>();

    // Pre-computed display names for all items (populated once at init, avoids per-keystroke getString())
    private final Map<Item, String> itemDisplayNames = new HashMap<>();
    private final Map<Item, String> itemIds = new HashMap<>();
    private final Map<Item, String> itemDisplayNamesLower = new HashMap<>();
    private final Map<Item, String> itemIdsLower = new HashMap<>();
    private final Map<EditBox, Component> invalidFields = new IdentityHashMap<>();

    private double scrollLeft = 0;
    private double scrollRight = 0;
    private boolean narrowLayout;
    private boolean showTrackedPane;
    private boolean showPreview;
    private final List<Button> itemTabButtons = new ArrayList<>();
    private final List<Button> availableActionButtons = new ArrayList<>();
    private final List<Button> removeActionButtons = new ArrayList<>();
    private final List<Button> replaceActionButtons = new ArrayList<>();
    private final List<Button> statusActionButtons = new ArrayList<>();
    private final List<Integer> availableProxyIndices = new ArrayList<>();
    private final List<Integer> removeProxyIndices = new ArrayList<>();
    private int availableFocusIndex = -1;
    private int trackedFocusIndex = -1;

    // Layout
    private int listAreaY;
    private int leftBoxY, rightBoxY;
    private int leftBoxX, rightBoxX, boxWidth, boxHeight;
    private static final int SEARCH_HEIGHT = 26;
    private static final int ITEM_ROW_HEIGHT = 28;

    private Component hoveredTooltipText = null;

    // Mouse State
    private boolean wasMouseDown = false;
    private boolean isDraggingScrollLeft = false;
    private boolean isDraggingScrollRight = false;

    // Labels
    private final List<LabelData> labels = new ArrayList<>();

    private record LabelData(Component text, int x, int y, int color) {}

    private int windowX, windowTop, windowWidth;
    private int styleX, styleTop, styleWidth, styleHeight, styleScroll;
    private int previewX, previewY, previewWidth, previewHeight;
    private int previewZoom = 2;
    private static final int STYLE_CONTENT_HEIGHT = 404;
    private final List<Button> previewZoomButtons = new ArrayList<>();
    private final Map<net.minecraft.client.gui.components.AbstractWidget, Integer> styleWidgetY = new IdentityHashMap<>();
    private final List<LabelData> styleLabels = new ArrayList<>();
    private final List<Button> colorButtons = new ArrayList<>();
    private final Set<String> trackedItemIds = new HashSet<>();
    private Button undoButton;
    private boolean undoShortcutDown;

    public EditScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(Component.translatable("gui.resourcetracker.edit.title"));
        this.parent = parent;
        this.list = list;
    }

    @Override
    protected void init() {
        TrackerHistory.endCountChange(list);
        String availableQuery = searchField == null ? "" : searchField.getValue();
        String trackedQuery = trackedSearchField == null ? "" : trackedSearchField.getValue();
        double previousLeft = scrollLeft, previousRight = scrollRight;
        clearWidgets();
        labels.clear();
        itemCountFields.clear();
        invalidFields.clear();
        itemTabButtons.clear();
        availableActionButtons.clear();
        removeActionButtons.clear();
        replaceActionButtons.clear();
        statusActionButtons.clear();
        styleWidgetY.clear();
        styleLabels.clear();
        previewZoomButtons.clear();
        trackedItemIds.clear();
        for (TrackerConfig.TrackedItem item : list.items) trackedItemIds.add(item.itemId);
        wasMouseDown = minecraft != null && GLFW.glfwGetMouseButton(VersionCompat.getWindowHandle(minecraft.getWindow()), 0) == GLFW.GLFW_PRESS;

        if (availableItems.isEmpty()) {
            BuiltInRegistries.ITEM.stream().forEach(item -> {
                availableItems.add(item);
                cacheItemText(item);
            });
            availableItems.sort(Comparator.comparing(itemDisplayNames::get));
        }

        windowWidth = Math.min(640, Math.max(220, width - 24));
        windowX = (width - windowWidth) / 2;
        windowTop = 12;
        int innerX = windowX + 12;
        int innerWidth = windowWidth - 24;
        int footerY = height - 34;
        narrowLayout = innerWidth < 420;
        listAreaY = windowTop + 112;
        leftBoxY = rightBoxY = listAreaY;
        boxWidth = narrowLayout ? innerWidth : (innerWidth - 12) / 2;
        leftBoxX = innerX;
        rightBoxX = narrowLayout ? innerX : leftBoxX + boxWidth + 12;
        boxHeight = Math.max(28, footerY - 8 - listAreaY);

        labels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.list_name"), innerX, windowTop + 34, UiTheme.MUTED));
        EditBox nameField = new EditBox(font, innerX + 58, windowTop + 29, innerWidth - 58, 18,
                Component.translatable("gui.resourcetracker.edit.list_name"));
        nameField.setValue(list.name);
        nameField.setResponder(value -> list.name = value);
        addRenderableWidget(nameField);

        int tabWidth = Math.min(150, (innerWidth - 12) / 3);
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.items"), b -> switchTab(false))
                .bounds(innerX, windowTop + 54, tabWidth, 20).build());
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.raw"), b -> {
            if (minecraft != null) minecraft.setScreen(new RawMaterialsScreen(this, list));
        }).bounds(innerX + tabWidth + 6, windowTop + 54, tabWidth, 20).build());
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.appearance"), b -> switchTab(true))
                .bounds(innerX + (tabWidth + 6) * 2, windowTop + 54, tabWidth, 20).build());
        Button hudModeButton = TrackerButton.builder(Component.translatable(list.hudRaw ? "gui.resourcetracker.edit.hud_raw" : "gui.resourcetracker.edit.hud_items"), b -> {
            list.hudRaw = !list.hudRaw;
            b.setMessage(Component.translatable(list.hudRaw ? "gui.resourcetracker.edit.hud_raw" : "gui.resourcetracker.edit.hud_items"));
            net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
        }).bounds(innerX, windowTop + 78, (innerWidth - 6) / 2, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.hud_mode_hint"))).build();
        addRenderableWidget(hudModeButton);
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.family.button"), b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new FamilyReplacementScreen(this, list));
        }).bounds(innerX + (innerWidth - 6) / 2 + 6, windowTop + 78, (innerWidth - 6) / 2, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.resourcetracker.family.hint"))).build());

        searchField = new EditBox(font, leftBoxX + 6, leftBoxY + 7, boxWidth - 38, 16,
                Component.translatable("gui.resourcetracker.edit.search"));
        searchField.setBordered(false);
        searchField.setValue(availableQuery);
        searchField.setResponder(this::updateSearch);
        searchField.setVisible(!showPreview && (!narrowLayout || !showTrackedPane));
        addRenderableWidget(searchField);
        trackedSearchField = new EditBox(font, rightBoxX + 6, rightBoxY + 7, boxWidth - 38, 16,
                Component.translatable("gui.resourcetracker.edit.search_tracked"));
        trackedSearchField.setBordered(false);
        trackedSearchField.setValue(trackedQuery);
        trackedSearchField.setResponder(this::updateTrackedSearch);
        trackedSearchField.setVisible(!showPreview && (!narrowLayout || showTrackedPane));
        addRenderableWidget(trackedSearchField);

        if (!showPreview) {
            if (narrowLayout) {
                int paneTabWidth = (innerWidth - 6) / 2;
                leftBoxY = rightBoxY = listAreaY + 10;
                boxHeight = Math.max(28, boxHeight - 10);
                searchField.setY(leftBoxY + 7);
                trackedSearchField.setY(rightBoxY + 7);
                Button available = TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.available_items"), b -> {
                    showTrackedPane = false; init();
                }).bounds(innerX, listAreaY - 10, paneTabWidth, 20).build();
                Button tracked = TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.tracked_items"), b -> {
                    showTrackedPane = true; init();
                }).bounds(innerX + paneTabWidth + 6, listAreaY - 10, paneTabWidth, 20).build();
                itemTabButtons.add(available); itemTabButtons.add(tracked);
                addRenderableWidget(available); addRenderableWidget(tracked);
            } else {
                labels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.available_items"), leftBoxX, listAreaY - 12, UiTheme.MUTED));
                labels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.tracked_items"), rightBoxX, listAreaY - 12, UiTheme.MUTED));
                labels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.target_header"), rightBoxX + boxWidth - 94, listAreaY - 12, UiTheme.MUTED));
            }
            addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.clear"), b -> confirmClear())
                    .bounds(innerX, footerY, Math.max(1, (innerWidth - 12) / 3), 20).build());
        } else {
            buildStyleControls(innerX, innerWidth);
        }

        int footerButtonWidth = Math.max(1, (innerWidth - 12) / 3);
        undoButton = addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.undo"), b -> undo())
                .bounds(innerX + footerButtonWidth + 6, footerY, footerButtonWidth, 20).build());
        undoButton.active = TrackerHistory.canUndo(list);
        undoButton.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.undo_hint")));
        addRenderableWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.done"), b -> onClose())
                .bounds(innerX + innerWidth - footerButtonWidth, footerY, footerButtonWidth, 20).primary().build());
        refreshCountWidgets();
        updateSearch(availableQuery);
        updateTrackedSearch(trackedQuery);
        scrollLeft = previousLeft;
        scrollRight = previousRight;
        rebuildItemActionButtons();
        updateItemActionButtons();
        scrollLeft = previousLeft;
        scrollRight = previousRight;
        updateStyleWidgets();
    }

    private void undo() {
        if (TrackerHistory.undo(list)) init();
    }

    @Override
    public void tick() {
        super.tick();
        if (undoButton != null) undoButton.active = TrackerHistory.canUndo(list);
        if (minecraft == null) return;
        // Polling keeps this shortcut shared across the supported keyboard-event APIs.
        boolean down = VersionCompat.isUndoPressed(minecraft);
        if (down && !undoShortcutDown) undo();
        undoShortcutDown = down;
    }

    private void switchTab(boolean appearance) {
        if (showPreview == appearance) return;
        showPreview = appearance;
        hoveredTooltipText = null;
        isDraggingScrollLeft = isDraggingScrollRight = false;
        init();
    }

    private void confirmClear() {
        if (minecraft == null) return;
        minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                TrackerHistory.record(list);
                list.items.clear();
                trackedItemIds.clear();
                scrollRight = 0;
                net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
            }
            minecraft.setScreen(this);
        }, Component.translatable("gui.resourcetracker.edit.clear"),
                Component.translatable("gui.resourcetracker.edit.clear_confirm")));
    }

    private void buildStyleControls(int innerX, int innerWidth) {
        styleX = innerX;
        styleWidth = Math.min(204, Math.max(128, (innerWidth - 12) / 2));
        styleTop = listAreaY;
        styleHeight = Math.max(36, height - 52 - styleTop);
        previewX = styleX + styleWidth + 12;
        previewY = styleTop;
        previewWidth = Math.max(36, innerWidth - styleWidth - 12);
        previewHeight = styleHeight;
        int zoomWidth = Math.min(54, Math.max(1, (previewWidth - 24) / 3));
        int zoomX = previewX + (previewWidth - (zoomWidth * 3 + 8)) / 2;
        for (int zoom = 1; zoom <= 3; zoom++) {
            final int value = zoom;
            Button button = TrackerButton.builder(Component.literal((zoom * 100) + "%"), b -> previewZoom = value)
                    .bounds(zoomX + (zoom - 1) * (zoomWidth + 4), previewY + 18, zoomWidth, 20)
                    .tooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.preview_zoom", zoom * 100)))
                    .createNarration(supplier -> Component.translatable("gui.resourcetracker.edit.preview_zoom", value * 100))
                    .build();
            addRenderableWidget(button);
            previewZoomButtons.add(button);
        }
        int x = styleX + 8, controlWidth = styleWidth - 16;


        addStyleWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.units." + list.countUnit.name().toLowerCase(java.util.Locale.ROOT)), b -> {
            TrackerConfig.CountUnit[] units = TrackerConfig.CountUnit.values();
            list.countUnit = units[(list.countUnit.ordinal() + 1) % units.length];
            b.setMessage(Component.translatable("gui.resourcetracker.edit.units." + list.countUnit.name().toLowerCase(java.util.Locale.ROOT)));
        }).bounds(x, styleTop + 300, controlWidth, 20).build());
        addStyleWidget(TrackerButton.builder(Component.translatable(list.hideCompleted ? "gui.resourcetracker.edit.hide_completed" : "gui.resourcetracker.edit.show_completed"), b -> {
            list.hideCompleted = !list.hideCompleted;
            b.setMessage(Component.translatable(list.hideCompleted ? "gui.resourcetracker.edit.hide_completed" : "gui.resourcetracker.edit.show_completed"));
        }).bounds(x, styleTop + 324, controlWidth, 20).build());
        addStyleWidget(TrackerButton.builder(Component.translatable(list.sortRemaining ? "gui.resourcetracker.edit.sort_remaining" : "gui.resourcetracker.edit.sort_original"), b -> {
            list.sortRemaining = !list.sortRemaining;
            b.setMessage(Component.translatable(list.sortRemaining ? "gui.resourcetracker.edit.sort_remaining" : "gui.resourcetracker.edit.sort_original"));
        }).bounds(x, styleTop + 348, controlWidth, 20).build());
        styleLabels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.max_rows"), x, styleTop + 376, UiTheme.MUTED));
        addStyleWidget(createIntField(x + controlWidth - 44, styleTop + 374, 44, list.maxVisibleRows, 0, 100000,
                Component.translatable("gui.resourcetracker.validation.max_rows"), value -> list.maxVisibleRows = value));

        styleLabels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.group_position"), x, styleTop + 8, UiTheme.TEXT));
        styleLabels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.group_display"), x, styleTop + 60, UiTheme.TEXT));
        styleLabels.add(new LabelData(Component.translatable("gui.resourcetracker.edit.group_colors"), x, styleTop + 154, UiTheme.TEXT));
        int fieldWidth = (controlWidth - 12) / 3;
        String[] keys = {"gui.resourcetracker.edit.pos_x", "gui.resourcetracker.edit.pos_y", "gui.resourcetracker.edit.scale"};
        for (int i = 0; i < 3; i++) {
            styleLabels.add(new LabelData(Component.translatable(keys[i]), x + i * (fieldWidth + 6), styleTop + 22, UiTheme.MUTED));
        }
        xField = createIntField(x, styleTop + 34, fieldWidth, list.x, null, null,
                Component.translatable("gui.resourcetracker.validation.integer"), value -> list.x = value);
        yField = createIntField(x + fieldWidth + 6, styleTop + 34, fieldWidth, list.y, null, null,
                Component.translatable("gui.resourcetracker.validation.integer"), value -> list.y = value);
        scaleField = createFloatField(x + (fieldWidth + 6) * 2, styleTop + 34, fieldWidth, list.scale,
                TrackerConfig.MIN_SCALE, TrackerConfig.MAX_SCALE,
                Component.translatable("gui.resourcetracker.validation.scale"), value -> list.scale = value);
        addStyleWidget(xField); addStyleWidget(yField); addStyleWidget(scaleField);
        addStyleWidget(TrackerButton.builder(Component.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"), b -> {
            list.showRemaining = !list.showRemaining;
            b.setMessage(Component.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"));
        }).bounds(x, styleTop + 74, controlWidth, 20).build());
        addStyleWidget(TrackerButton.builder(Component.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"), b -> {
            list.showIcons = !list.showIcons;
            b.setMessage(Component.translatable(list.showIcons ? "gui.resourcetracker.edit.icons_on" : "gui.resourcetracker.edit.icons_off"));
        }).bounds(x, styleTop + 98, controlWidth, 20).build());
        addStyleWidget(TrackerButton.builder(getColumnsButtonText(list.columns), b -> {
            list.columns = (list.columns + 1) % (TrackerConfig.MAX_COLUMNS + 1);
            b.setMessage(getColumnsButtonText(list.columns));
        }).bounds(x, styleTop + 122, controlWidth, 20).build());
        String[] colorKeys = {"gui.resourcetracker.color.text", "gui.resourcetracker.color.title", "gui.resourcetracker.color.background"};
        colorButtons.clear();
        for (int index = 0; index < 3; index++) {
            final int colorIndex = index;
            Button colorButton = TrackerButton.builder(Component.translatable(colorKeys[index]), b -> {
                if (minecraft != null) minecraft.setScreen(new ColorPickerScreen(this,
                        Component.translatable(colorKeys[colorIndex]), getColorForIndex(colorIndex), color -> setColorForIndex(colorIndex, color)));
            }).bounds(x, styleTop + 168 + index * 24, controlWidth, 20).build();
            addStyleWidget(colorButton);
            colorButtons.add(colorButton);
        }
        addStyleWidget(TrackerButton.builder(Component.translatable("gui.resourcetracker.edit.reset"), b -> resetSettings())
                .bounds(x, styleTop + 248, controlWidth, 20).build());
    }

    private void addStyleWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
        styleWidgetY.put(widget, widget.getY());
        addRenderableWidget(widget);
        addWidget(new StyleFocusTarget(widget));
    }

    /** Offers off-screen controls to native Tab navigation without mouse hit areas. */
    private static final class StyleFocusTarget implements net.minecraft.client.gui.components.events.GuiEventListener,
            net.minecraft.client.gui.narration.NarratableEntry {
        private final net.minecraft.client.gui.components.AbstractWidget widget;

        private StyleFocusTarget(net.minecraft.client.gui.components.AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return event instanceof FocusNavigationEvent.TabNavigation && widget.active && !widget.visible && !widget.isFocused()
                    ? ComponentPath.leaf(widget) : null;
        }

        @Override public void setFocused(boolean focused) {}
        @Override public boolean isFocused() { return false; }
        @Override public boolean isActive() { return false; }
        @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    private void clearHiddenStyleFocus() {
        if (getFocused() instanceof net.minecraft.client.gui.components.AbstractWidget widget
                && styleWidgetY.containsKey(widget) && !widget.visible) super.setFocused(null);
    }

    private void updateStyleWidgets() {
        if (!showPreview) return;
        int maxScroll = Math.max(0, STYLE_CONTENT_HEIGHT - styleHeight);
        styleScroll = Mth.clamp(styleScroll, 0, maxScroll);
        for (Map.Entry<net.minecraft.client.gui.components.AbstractWidget, Integer> entry : styleWidgetY.entrySet()) {
            net.minecraft.client.gui.components.AbstractWidget widget = entry.getKey();
            widget.setY(entry.getValue() - styleScroll);
            widget.visible = widget.getY() >= styleTop + 1 && widget.getY() + widget.getHeight() <= styleTop + styleHeight - 1;
        }
        clearHiddenStyleFocus();
    }

    /**
     * Resets the current list configuration to defaults.
     */
    private void resetSettings() {
        TrackerConfig.applyDefaults(list);
        styleScroll = 0;
        init();
    }

    private Component getColumnsButtonText(int columns) {
        if (columns <= 0) {
            return Component.translatable("gui.resourcetracker.edit.columns_auto");
        }
        return Component.translatable("gui.resourcetracker.edit.columns", columns);
    }

    private void refreshCountWidgets() {
        for (EditBox w : itemCountFields.values()) this.removeWidget(w);
        Map<TrackerConfig.TrackedItem, EditBox> newMap = new LinkedHashMap<>();
        for (TrackerConfig.TrackedItem item : list.items) {
            EditBox w;
            if (itemCountFields.containsKey(item)) w = itemCountFields.get(item);
            else {
                w = new EditBox(font, 0, 0, 50, 16, Component.translatable("gui.resourcetracker.edit.count_field"));
                w.setValue(String.valueOf(item.targetCount));
                w.setTextColor(0xFFFFFFFF);
                EditBox countField = w;
                w.setResponder(val -> {
                    Integer v = parseInteger(val);
                    boolean valid = v != null && v >= 1 && v <= TrackerConfig.MAX_TARGET_COUNT;
                    markFieldValidity(countField, valid, Component.translatable("gui.resourcetracker.validation.target_count"));
                    if (valid) {
                        if (item.targetCount != v) TrackerHistory.recordCountChange(list, item);
                        item.setTargetCount(v);
                        net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
                    }
                });
            }
            w.setVisible(!showPreview && (!narrowLayout || showTrackedPane));
            newMap.put(item, w);
            this.addRenderableWidget(w);
        }
        itemCountFields.clear();
        itemCountFields.putAll(newMap);
    }

    private void updateSearch(String query) {
        filteredItems.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();

        if (q.isEmpty()) {
            filteredItems.addAll(availableItems);
        } else {
            List<Item> startsWith = new ArrayList<>();
            List<Item> contains = new ArrayList<>();

            for (Item item : availableItems) {
                String name = itemDisplayNamesLower.get(item);
                String id = itemIdsLower.get(item);
                if (name == null || id == null) {
                    cacheItemText(item);
                    name = itemDisplayNamesLower.getOrDefault(item, "");
                    id = itemIdsLower.getOrDefault(item, "");
                }
                boolean queryWithoutNamespace = !q.contains(":");
                if (name.startsWith(q) || id.startsWith(q) || (queryWithoutNamespace && id.startsWith("minecraft:" + q))) {
                    startsWith.add(item);
                } else if (name.contains(q) || id.contains(q)) {
                    contains.add(item);
                }
            }
            // availableItems is pre-sorted alphabetically, so sublists are already in order
            filteredItems.addAll(startsWith);
            filteredItems.addAll(contains);
        }
        scrollLeft = 0;
        if (searchField != null) resetAvailableFocus();
    }

    private void updateTrackedSearch(String query) {
        filteredTrackedItems.clear();
        String q = query.toLowerCase(Locale.ROOT).trim();

        if (q.isEmpty()) {
            filteredTrackedItems.addAll(list.items);
        } else {
            for (TrackerConfig.TrackedItem ti : list.items) {
                if (!ti.isValid()) continue;
                String name = ti.getDisplayName().toLowerCase(Locale.ROOT);
                String id = ti.itemId == null ? "" : ti.itemId.toLowerCase(Locale.ROOT);
                if (name.contains(q) || id.contains(q) || (!q.contains(":") && id.startsWith("minecraft:" + q))) {
                    filteredTrackedItems.add(ti);
                }
            }
        }
        scrollRight = 0;
        if (trackedSearchField != null) resetTrackedFocus();
    }

    private void rebuildItemActionButtons() {
        for (Button button : availableActionButtons) removeWidget(button);
        for (Button button : removeActionButtons) removeWidget(button);
        for (Button button : replaceActionButtons) removeWidget(button);
        for (Button button : statusActionButtons) removeWidget(button);
        availableActionButtons.clear();
        removeActionButtons.clear();
        replaceActionButtons.clear();
        statusActionButtons.clear();
        availableProxyIndices.clear();
        removeProxyIndices.clear();

        int proxyCount = visibleItemRowCapacity();
        for (int slot = 0; slot < proxyCount; slot++) {
            final int proxySlot = slot;
            Button button = TrackerButton.builder(Component.empty(), b -> activateAvailableProxy(proxySlot))
                    .bounds(leftBoxX, leftBoxY + SEARCH_HEIGHT, Math.max(1, boxWidth - 10), ITEM_ROW_HEIGHT).build();
            button.setAlpha(0.0F);
            addRenderableWidget(button);
            availableActionButtons.add(button);
            availableProxyIndices.add(-1);
        }
        for (int slot = 0; slot < proxyCount; slot++) {
            final int proxySlot = slot;
            Button button = TrackerButton.builder(Component.empty(), b -> activateRemoveProxy(proxySlot))
                    .bounds(rightBoxX + boxWidth - 36, rightBoxY + SEARCH_HEIGHT, 28, ITEM_ROW_HEIGHT).build();
            button.setAlpha(0.0F);
            addRenderableWidget(button);
            removeActionButtons.add(button);
            removeProxyIndices.add(-1);
            Button replace = TrackerButton.builder(Component.empty(), b -> {
                int index = removeProxyIndices.get(proxySlot);
                if (index >= 0 && index < filteredTrackedItems.size()) openReplacement(filteredTrackedItems.get(index));
            }).bounds(rightBoxX + 1, rightBoxY, Math.max(1, boxWidth - 137), ITEM_ROW_HEIGHT).build();
            replace.setAlpha(0.0F);
            addRenderableWidget(replace);
            replaceActionButtons.add(replace);
            for (int action = 0; action < 2; action++) {
                final boolean pin = action == 1;
                Button status = TrackerButton.builder(Component.empty(), b -> {
                    int index = removeProxyIndices.get(proxySlot);
                    if (index < 0 || index >= filteredTrackedItems.size()) return;
                    TrackerConfig.TrackedItem item = filteredTrackedItems.get(index);
                    TrackerHistory.record(list);
                    if (pin) item.pinned = !item.pinned; else item.toggleCollected();
                    net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
                }).bounds(rightBoxX, rightBoxY, 18, 20).build();
                status.setAlpha(0.0F);
                addRenderableWidget(status);
                statusActionButtons.add(status);
            }
        }
        resetAvailableFocus();
        resetTrackedFocus();
    }

    private int visibleItemRowCapacity() {
        return Math.max(1, (Math.max(1, boxHeight - SEARCH_HEIGHT - 4) + ITEM_ROW_HEIGHT - 1) / ITEM_ROW_HEIGHT + 1);
    }

    private void resetAvailableFocus() {
        availableFocusIndex = filteredItems.isEmpty() ? -1 : 0;
        scrollLeft = 0;
    }

    private void resetTrackedFocus() {
        trackedFocusIndex = filteredTrackedItems.isEmpty() ? -1 : 0;
        scrollRight = 0;
    }

    private void activateAvailableProxy(int slot) {
        if (slot >= 0 && slot < availableProxyIndices.size()) {
            int index = availableProxyIndices.get(slot);
            if (index >= 0 && index < filteredItems.size()) {
                addItem(filteredItems.get(index));
                if (trackedSearchField != null) updateTrackedSearch(trackedSearchField.getValue());
            }
        }
    }

    private void activateRemoveProxy(int slot) {
        if (slot >= 0 && slot < removeProxyIndices.size()) {
            int index = removeProxyIndices.get(slot);
            if (index >= 0 && index < filteredTrackedItems.size()) confirmRemove(filteredTrackedItems.get(index));
        }
    }

    private int focusedAvailableIndex() {
        for (int i = 0; i < availableActionButtons.size(); i++) {
            if (availableActionButtons.get(i).isFocused()) return availableProxyIndices.get(i);
        }
        return -1;
    }

    private int focusedTrackedIndex() {
        for (int i = 0; i < removeActionButtons.size(); i++) {
            if (removeActionButtons.get(i).isFocused() || replaceActionButtons.get(i).isFocused()
                    || statusActionButtons.get(i * 2).isFocused() || statusActionButtons.get(i * 2 + 1).isFocused())
                return removeProxyIndices.get(i);
        }
        return -1;
    }

    private void updateItemActionButtons() {
        if (showPreview) {
            for (Button button : availableActionButtons) button.visible = false;
            for (Button button : removeActionButtons) button.visible = false;
            for (Button button : replaceActionButtons) button.visible = false;
            for (Button button : statusActionButtons) button.visible = false;
            return;
        }
        int leftStart = leftBoxY + SEARCH_HEIGHT + 2;
        int rightStart = rightBoxY + SEARCH_HEIGHT + 2;
        int visibleHeight = Math.max(1, boxHeight - SEARCH_HEIGHT - 4);
        int leftMaxScroll = Math.max(0, filteredItems.size() * ITEM_ROW_HEIGHT - visibleHeight);
        int rightMaxScroll = Math.max(0, filteredTrackedItems.size() * ITEM_ROW_HEIGHT - visibleHeight);
        scrollLeft = Mth.clamp(scrollLeft, 0, leftMaxScroll);
        scrollRight = Mth.clamp(scrollRight, 0, rightMaxScroll);

        int focusedLeft = focusedAvailableIndex();
        int focusedRight = focusedTrackedIndex();
        if (focusedLeft >= 0) availableFocusIndex = focusedLeft;
        if (focusedRight >= 0) trackedFocusIndex = focusedRight;

        int leftFirst = Math.max(0, (int) Math.floor(scrollLeft / ITEM_ROW_HEIGHT));
        int rightFirst = Math.max(0, (int) Math.floor(scrollRight / ITEM_ROW_HEIGHT));
        for (int slot = 0; slot < availableActionButtons.size(); slot++) {
            int index = leftFirst + slot;
            Button button = availableActionButtons.get(slot);
            availableProxyIndices.set(slot, index < filteredItems.size() ? index : -1);
            button.visible = !narrowLayout || !showTrackedPane;
            if (index >= 0 && index < filteredItems.size()) {
                int y = (int) (leftStart + index * ITEM_ROW_HEIGHT - scrollLeft);
                boolean inViewport = y < leftStart + visibleHeight && y + ITEM_ROW_HEIGHT > leftStart;
                button.visible = button.visible && inViewport;
                if (inViewport) {
                    int clippedY = Math.max(leftStart, y);
                    int clippedBottom = Math.min(leftStart + visibleHeight, y + ITEM_ROW_HEIGHT);
                    button.setX(leftBoxX + 1);
                    button.setY(clippedY);
                    button.setHeight(Math.max(1, clippedBottom - clippedY));
                    button.setWidth(Math.max(1, boxWidth - 11));
                    button.setMessage(Component.translatable("gui.resourcetracker.edit.add_item", getCachedItemName(filteredItems.get(index))));
                }
            } else {
                button.visible = false;
            }
        }
        for (int slot = 0; slot < removeActionButtons.size(); slot++) {
            int index = rightFirst + slot;
            Button button = removeActionButtons.get(slot);
            removeProxyIndices.set(slot, index < filteredTrackedItems.size() ? index : -1);
            button.visible = !narrowLayout || showTrackedPane;
            if (index >= 0 && index < filteredTrackedItems.size()) {
                int y = (int) (rightStart + index * ITEM_ROW_HEIGHT - scrollRight);
                boolean inViewport = y >= rightStart && y + ITEM_ROW_HEIGHT <= rightStart + visibleHeight;
                button.visible = button.visible && inViewport;
                if (inViewport) {
                    int clippedY = Math.max(rightStart, y);
                    int clippedBottom = Math.min(rightStart + visibleHeight, y + ITEM_ROW_HEIGHT);
                    button.setX(rightBoxX + boxWidth - 36);
                    button.setY(clippedY);
                    button.setHeight(Math.max(1, clippedBottom - clippedY));
                    button.setWidth(24);
                    button.setMessage(Component.translatable("gui.resourcetracker.edit.remove_item", filteredTrackedItems.get(index).getDisplayName()));
                }
            } else {
                button.visible = false;
            }
        }
        for (int slot = 0; slot < removeActionButtons.size(); slot++) {
            Button remove = removeActionButtons.get(slot);
            int index = removeProxyIndices.get(slot);
            Button replace = replaceActionButtons.get(slot);
            replace.visible = remove.visible;
            replace.setX(rightBoxX + 1);
            replace.setY(remove.getY());
            replace.setWidth(Math.max(1, boxWidth - 137));
            replace.setHeight(remove.getHeight());
            if (index >= 0 && index < filteredTrackedItems.size()) {
                replace.setMessage(Component.translatable("gui.resourcetracker.edit.replace_item", filteredTrackedItems.get(index).getDisplayName()));
                replace.setTooltip(Tooltip.create(replace.getMessage()));
            }
            for (int action = 0; action < 2; action++) {
                Button status = statusActionButtons.get(slot * 2 + action);
                status.visible = remove.visible;
                status.setX(rightBoxX + boxWidth - 136 + action * 20);
                status.setY(remove.getY() + 4);
                if (index >= 0 && index < filteredTrackedItems.size()) {
                    status.setMessage(Component.translatable(action == 0 ? "gui.resourcetracker.edit.mark_collected" : "gui.resourcetracker.edit.pin"));
                    status.setTooltip(Tooltip.create(status.getMessage()));
                }
            }
        }
    }

    private void focusLogicalItem(boolean tracked, int index) {
        List<?> items = tracked ? filteredTrackedItems : filteredItems;
        if (items.isEmpty()) return;
        index = Mth.clamp(index, 0, items.size() - 1);
        int visibleHeight = Math.max(1, boxHeight - SEARCH_HEIGHT - 4);
        int maxScroll = Math.max(0, items.size() * ITEM_ROW_HEIGHT - visibleHeight);
        int start = tracked ? rightBoxY + SEARCH_HEIGHT + 2 : leftBoxY + SEARCH_HEIGHT + 2;
        double scroll = tracked ? scrollRight : scrollLeft;
        int rowY = start + index * ITEM_ROW_HEIGHT - (int) scroll;
        if (rowY < start) scroll = index * ITEM_ROW_HEIGHT;
        else if (rowY + ITEM_ROW_HEIGHT > start + visibleHeight) scroll = (index + 1) * ITEM_ROW_HEIGHT - visibleHeight;
        scroll = Mth.clamp(scroll, 0, maxScroll);
        if (tracked) { trackedFocusIndex = index; scrollRight = scroll; }
        else { availableFocusIndex = index; scrollLeft = scroll; }
        updateItemActionButtons();
        List<Button> buttons = tracked ? removeActionButtons : availableActionButtons;
        List<Integer> indices = tracked ? removeProxyIndices : availableProxyIndices;
        for (int i = 0; i < buttons.size(); i++) {
            if (indices.get(i) == index && buttons.get(i).visible) {
                buttons.get(i).setFocused(true);
                break;
            }
        }
    }

    private Button[] trackedRowActions(int slot) {
        return new Button[] { removeActionButtons.get(slot), replaceActionButtons.get(slot),
                statusActionButtons.get(slot * 2), statusActionButtons.get(slot * 2 + 1) };
    }

    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        if (showPreview) return super.nextFocusPath(event);
        boolean forward;
        if (event instanceof FocusNavigationEvent.TabNavigation tab) {
            forward = tab.forward();
            for (int slot = 0; slot < removeActionButtons.size(); slot++) {
                Button[] actions = trackedRowActions(slot);
                for (int action = 0; action < actions.length; action++) {
                    if (!actions[action].isFocused()) continue;
                    int nextAction = action + (forward ? 1 : -1);
                    if (nextAction >= 0 && nextAction < actions.length) return ComponentPath.leaf(actions[nextAction]);
                    int index = removeProxyIndices.get(slot) + (forward ? 1 : -1);
                    if (index < 0 || index >= filteredTrackedItems.size()) return super.nextFocusPath(event);
                    focusLogicalItem(true, index);
                    for (int nextSlot = 0; nextSlot < removeProxyIndices.size(); nextSlot++) {
                        if (removeProxyIndices.get(nextSlot) == index && removeActionButtons.get(nextSlot).visible) {
                            Button[] nextActions = trackedRowActions(nextSlot);
                            return ComponentPath.leaf(nextActions[forward ? 0 : nextActions.length - 1]);
                        }
                    }
                    return super.nextFocusPath(event);
                }
            }
        } else if (event instanceof FocusNavigationEvent.ArrowNavigation arrow
                && (arrow.direction() == ScreenDirection.UP || arrow.direction() == ScreenDirection.DOWN)) {
            forward = arrow.direction() == ScreenDirection.DOWN;
        } else {
            return super.nextFocusPath(event);
        }

        int currentAvailable = focusedAvailableIndex();
        if (currentAvailable >= 0) {
            int next = currentAvailable + (forward ? 1 : -1);
            if (next >= 0 && next < filteredItems.size()) {
                return focusLogicalPath(false, next);
            }
        }
        int currentTracked = focusedTrackedIndex();
        if (currentTracked >= 0) {
            int next = currentTracked + (forward ? 1 : -1);
            if (next >= 0 && next < filteredTrackedItems.size()) {
                return focusLogicalPath(true, next);
            }
        }
        return super.nextFocusPath(event);
    }

    @Override
    public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
        if (getFocused() != focused) TrackerHistory.endCountChange(list);
        if (showPreview && focused instanceof net.minecraft.client.gui.components.AbstractWidget widget && styleWidgetY.containsKey(widget)) {
            int baseY = styleWidgetY.get(widget);
            if (baseY - styleScroll < styleTop + 2) styleScroll = baseY - styleTop - 2;
            if (baseY - styleScroll + widget.getHeight() > styleTop + styleHeight - 2)
                styleScroll = baseY + widget.getHeight() - styleTop - styleHeight + 2;
            updateStyleWidgets();
        }
        super.setFocused(focused);
    }

    private ComponentPath focusLogicalPath(boolean tracked, int index) {
        focusLogicalItem(tracked, index);
        List<Button> buttons = tracked ? removeActionButtons : availableActionButtons;
        List<Integer> indices = tracked ? removeProxyIndices : availableProxyIndices;
        for (int i = 0; i < buttons.size(); i++) {
            if (indices.get(i) == index && buttons.get(i).visible) return ComponentPath.leaf(buttons.get(i));
        }
        return null;
    }

    private void openReplacement(TrackerConfig.TrackedItem item) {
        if (minecraft == null) return;
        TrackerHistory.endCountChange(list);
        VersionCompat.setScreen(minecraft, new ItemPickerScreen(this, list, item, () -> { }));
    }

    private void confirmRemove(TrackerConfig.TrackedItem item) {
        if (this.minecraft == null) return;
        this.minecraft.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                TrackerHistory.record(list);
                list.items.remove(item);
                trackedItemIds.remove(item.itemId);
                refreshCountWidgets();
                updateTrackedSearch(trackedSearchField.getValue());
                net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
            }
            if (this.minecraft != null) this.minecraft.setScreen(this);
        }, Component.translatable("gui.resourcetracker.delete_confirm_title"),
                Component.translatable("gui.resourcetracker.edit.remove_confirm", item.getDisplayName())));
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // No-op: we draw our own semi-transparent background in render()
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        context.fill(windowX, windowTop, windowX + windowWidth, height - 12, UiTheme.WINDOW);
        RenderUtils.drawBoxOutline(context, windowX, windowTop, windowWidth, height - windowTop - 12);
        context.drawString(font, title, windowX + 12, windowTop + 10, UiTheme.TEXT, true);
        int tabWidth = Math.min(150, (windowWidth - 36) / 3);
        int activeX = windowX + 12 + (showPreview ? (tabWidth + 6) * 2 : 0);
        context.fill(activeX, windowTop + 75, activeX + tabWidth, windowTop + 77, UiTheme.ACCENT);

        if (!showPreview) handleInput(mouseX, mouseY);
        updateItemActionButtons();
        if (showPreview) {
            for (EditBox widget : itemCountFields.values()) widget.setVisible(false);
            updateStyleWidgets();
            context.fill(styleX, styleTop, styleX + styleWidth, styleTop + styleHeight, UiTheme.PANEL);
            RenderUtils.drawBoxOutline(context, styleX, styleTop, styleWidth, styleHeight);
            renderPreview(context);
        } else {
            if (!narrowLayout || !showTrackedPane) {
                RenderUtils.drawBoxFill(context, leftBoxX, leftBoxY, boxWidth, boxHeight);
                renderSearchBar(context, leftBoxX, leftBoxY, boxWidth, searchField);
                renderItemList(context, mouseX, mouseY, leftBoxX, leftBoxY, boxWidth, boxHeight, filteredItems, scrollLeft);
            }
            if (!narrowLayout || showTrackedPane) {
                RenderUtils.drawBoxFill(context, rightBoxX, rightBoxY, boxWidth, boxHeight);
                renderSearchBar(context, rightBoxX, rightBoxY, boxWidth, trackedSearchField);
                renderAddedList(context, mouseX, mouseY, rightBoxX, rightBoxY, boxWidth, boxHeight);
            }
        }
        super.render(context, mouseX, mouseY, delta);
        for (LabelData label : labels) context.drawString(font, label.text, label.x, label.y, label.color, true);
        if (showPreview) {
            if (previewZoomButtons.size() == 3) {
                Button selectedZoom = previewZoomButtons.get(previewZoom - 1);
                context.fill(selectedZoom.getX(), selectedZoom.getY() + 21,
                        selectedZoom.getX() + selectedZoom.getWidth(), selectedZoom.getY() + 23, UiTheme.ACCENT);
            }
            context.enableScissor(styleX + 1, styleTop + 1, styleX + styleWidth - 1, styleTop + styleHeight - 1);
            for (LabelData label : styleLabels) context.drawString(font, label.text, label.x, label.y - styleScroll, label.color, true);
            for (int index = 0; index < colorButtons.size(); index++) {
                Button button = colorButtons.get(index);
                if (!button.visible) continue;
                int x = styleX + styleWidth - 27, y = button.getY() + 2;
                ColorPickerScreen.drawSwatch(context, x, y, 16, 16, getColorForIndex(index));
            }
            context.disableScissor();
            RenderUtils.drawStyledScrollbar(context, styleX + styleWidth - 6, styleTop + 2, styleHeight - 4, STYLE_CONTENT_HEIGHT, styleScroll);
        } else {
            if (!narrowLayout || !showTrackedPane) RenderUtils.drawBoxOutline(context, leftBoxX, leftBoxY, boxWidth, boxHeight);
            if (!narrowLayout || showTrackedPane) RenderUtils.drawBoxOutline(context, rightBoxX, rightBoxY, boxWidth, boxHeight);
        }
        if (showPreview) context.drawString(font, Component.translatable("gui.resourcetracker.edit.save_on_exit"),
                windowX + 12, height - 32, UiTheme.MUTED, false);
        if (hoveredTooltipText != null) {
            VersionCompat.setTooltip(context, font, hoveredTooltipText, mouseX, mouseY);
            hoveredTooltipText = null;
        }
        showInvalidFieldTooltip(context, mouseX, mouseY);
    }

    private void renderPreview(GuiGraphics context) {
        int panelX = previewX, panelY = previewY;
        int panelWidth = previewWidth, panelHeight = previewHeight;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, UiTheme.PANEL);
        RenderUtils.drawBoxOutline(context, panelX, panelY, panelWidth, panelHeight);
        context.drawCenteredString(font, Component.translatable("gui.resourcetracker.edit.preview_title"),
                panelX + panelWidth / 2, panelY + 5, UiTheme.TEXT);

        HudRenderModel model = HudRenderCache.preview(list, font, minecraft.getWindow().getGuiScaledHeight());
        int modelWidth = Math.max(1, model.backgroundMaxX - model.backgroundMinX);
        int modelHeight = Math.max(1, model.backgroundMaxY - model.backgroundMinY);
        int availableWidth = Math.max(1, panelWidth - 16);
        int availableHeight = Math.max(1, panelHeight - 55);
        float hudScale = TrackerConfig.clampScale(list.scale);
        float desiredScale = hudScale * previewZoom;
        float fitScale = Math.min((float) availableWidth / modelWidth, (float) availableHeight / modelHeight);
        boolean fitted = desiredScale > fitScale;
        if (fitted) {
            availableHeight = Math.max(1, availableHeight - 12);
            fitScale = Math.min((float) availableWidth / modelWidth, (float) availableHeight / modelHeight);
        }
        float scale = Math.min(desiredScale, fitScale);
        int contentTop = panelY + 47;
        float originX = panelX + 8 + (availableWidth - modelWidth * scale) / 2 - model.backgroundMinX * scale;
        float originY = contentTop + (availableHeight - modelHeight * scale) / 2 - model.backgroundMinY * scale;

        context.enableScissor(panelX + 1, contentTop, panelX + panelWidth - 1, contentTop + availableHeight);
        VersionCompat.push(context);
        VersionCompat.translate(context, originX, originY);
        VersionCompat.scale(context, scale, scale);
        HudRenderer.render(context, font, model);
        VersionCompat.pop(context);
        context.disableScissor();
        if (fitted) {
            String message = Component.translatable("gui.resourcetracker.edit.preview_fitted",
                    Math.round(scale / hudScale * 100)).getString();
            context.drawString(font, RenderUtils.shortenText(font, message, panelWidth - 16),
                    panelX + 8, panelY + panelHeight - 12, UiTheme.MUTED, false);
        }
    }

    private void renderItemList(GuiGraphics context, int mx, int my, int x, int y, int w, int h, List<Item> items, double scroll) {
        int startDisplayY = y + SEARCH_HEIGHT + 2;
        int displayHeight = h - SEARCH_HEIGHT - 4;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = ITEM_ROW_HEIGHT;
        int startY = (int) (startDisplayY - scroll);

        Item hoveredItem = null;

        int firstVisibleIndex = Math.max(0, (int) Math.floor(scroll / itemH));
        int lastVisibleIndex = Math.min(items.size() - 1, (int) Math.ceil((scroll + displayHeight) / itemH));
        for (int i = firstVisibleIndex; i <= lastVisibleIndex; i++) {
            int cy = startY + (i * itemH);
            if (cy + itemH < startDisplayY || cy > y + h) continue;

            Item item = items.get(i);
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + itemH && my >= startDisplayY;

            if (hover || focusedAvailableIndex() == i) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, UiTheme.HOVER);
                hoveredItem = item;
            }

            context.renderItem(searchStackCache.computeIfAbsent(item, ItemStack::new), x + 4, cy + 6);

            String name = getCachedItemName(item);
            name = RenderUtils.shortenText(font, name, w - 56);

            context.drawString(font, Component.literal(name), x + 24, cy + 10, 0xFFFFFFFF, true);
            boolean added = trackedItemIds.contains(itemIds.get(item));
            if (added) {
                RenderUtils.drawPngIconInBox(context, PngIcons.Icon.CHECK,
                        x + w - 36, cy + 2, 24, 24, UiTheme.ACCENT);
            } else {
                RenderUtils.drawPngIconInBox(context, PngIcons.Icon.PLUS,
                        x + w - 36, cy + 2, 24, 24, UiTheme.MUTED);
            }
        }

        if (items.isEmpty()) context.drawString(font, RenderUtils.shortenText(font,
                Component.translatable("gui.resourcetracker.edit.no_results").getString(), w - 16),
                x + 8, startDisplayY + 8, UiTheme.MUTED, false);
        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, items.size() * itemH, scroll);

        if (hoveredItem != null) {
            hoveredTooltipText = Component.literal(getCachedItemName(hoveredItem));
        }
    }

    private void renderAddedList(GuiGraphics context, int mx, int my, int x, int y, int w, int h) {
        int startDisplayY = y + SEARCH_HEIGHT + 2;
        int displayHeight = h - SEARCH_HEIGHT - 4;

        context.enableScissor(x + 1, startDisplayY, x + w - 1, y + h - 2);
        int itemH = ITEM_ROW_HEIGHT;
        int startY = (int) (startDisplayY - scrollRight);

        TrackerConfig.TrackedItem hoveredTracked = null;

        for (EditBox widget : itemCountFields.values()) {
            widget.setVisible(false);
        }

        int firstVisibleIndex = Math.max(0, (int) Math.floor(scrollRight / itemH));
        int lastVisibleIndex = Math.min(filteredTrackedItems.size() - 1, (int) Math.ceil((scrollRight + displayHeight) / itemH));
        for (int i = firstVisibleIndex; i <= lastVisibleIndex; i++) {
            TrackerConfig.TrackedItem ti = filteredTrackedItems.get(i);
            int cy = startY + (i * itemH);

            EditBox widget = itemCountFields.get(ti);
            boolean isVisible = (cy >= startDisplayY && cy + itemH <= y + h - 2);

            if (widget != null && isVisible) {
                widget.setVisible(true);
                widget.setX(x + w - 94);
                widget.setY(cy + 7);
            }

            if (!isVisible) continue;

            boolean valid = ti.isValid();
            boolean hover = mx >= x && mx < x + w - 50 && my >= cy && my < cy + itemH && my >= startDisplayY;
            if (hover || focusedTrackedIndex() == i) {
                context.fill(x + 1, cy, x + w - 1, cy + itemH, valid ? UiTheme.HOVER : 0x20FF5555);
                hoveredTracked = ti;
            }

            if (valid) {
                context.renderItem(ti.getStack(), x + 4, cy + 6);
            } else {
                context.drawString(font, Component.literal("!"), x + 9, cy + 10, 0xFFFF5555, true);
            }
            String display = valid ? ti.getDisplayName() : Component.translatable("gui.resourcetracker.edit.invalid_item", ti.itemId).getString();
            String txt = RenderUtils.shortenText(font, display, Math.max(0, w - 164));
            context.drawString(font, Component.literal(txt), x + 24, cy + 10, valid ? 0xFFFFFFFF : 0xFFFF7777, true);

            context.drawString(font, itemStatus(ti, false), x + w - 132, cy + 10, ti.isManuallyCollected() ? UiTheme.ACCENT : UiTheme.MUTED, false);
            context.drawString(font, itemStatus(ti, true), x + w - 112, cy + 10, ti.pinned ? UiTheme.ACCENT : UiTheme.MUTED, false);

            int crossX = x + w - 36;
            int crossY = cy + 2;
            boolean crossHover = mx >= crossX && mx < crossX + 24 && my >= crossY && my < crossY + 24;
            int crossColor = crossHover ? 0xFFFF5555 : 0xFF888888;
            if (crossHover) {
                hoveredTooltipText = Component.translatable("gui.resourcetracker.delete");
            }
            RenderUtils.drawPngIconInBox(context, PngIcons.Icon.CROSS, crossX, crossY, 24, 24, crossColor);
        }

        if (filteredTrackedItems.isEmpty()) context.drawString(font, RenderUtils.shortenText(font,
                Component.translatable(list.items.isEmpty() ? "gui.resourcetracker.edit.empty_list" : "gui.resourcetracker.edit.no_results").getString(), w - 16),
                x + 8, startDisplayY + 8, UiTheme.MUTED, false);
        context.disableScissor();

        RenderUtils.drawStyledScrollbar(context, x + w - 6, startDisplayY, displayHeight, filteredTrackedItems.size() * itemH, scrollRight);

        if (hoveredTracked != null) {
            hoveredTooltipText = mx < x + w - 136
                    ? Component.translatable("gui.resourcetracker.edit.replace_item", hoveredTracked.getDisplayName())
                    : (hoveredTracked.isValid() ? Component.literal(hoveredTracked.getDisplayName()) : Component.literal(hoveredTracked.itemId));
        }
    }

    private void handleInput(int mx, int my) {
        boolean down = GLFW.glfwGetMouseButton(VersionCompat.getWindowHandle(minecraft.getWindow()), 0) == GLFW.GLFW_PRESS;
        int itemH = ITEM_ROW_HEIGHT;

        if (down && !wasMouseDown) {
            int listContentY = leftBoxY + SEARCH_HEIGHT + 2;
            // Left list click -> add item
            if ((!narrowLayout || !showTrackedPane) && mx >= leftBoxX && mx < leftBoxX + boxWidth - 10 && my >= listContentY && my < leftBoxY + boxHeight) {
                int idx = (int) ((my - listContentY + scrollLeft) / itemH);
                if (idx >= 0 && idx < filteredItems.size()) {
                    addItem(filteredItems.get(idx));
                    updateTrackedSearch(trackedSearchField.getValue());
                }
            }
            // Right list click -> delete
            int rightContentY = rightBoxY + SEARCH_HEIGHT + 2;
            if ((!narrowLayout || showTrackedPane) && mx >= rightBoxX && mx < rightBoxX + boxWidth - 10 && my >= rightContentY && my < rightBoxY + boxHeight) {
                int idx = (int) ((my - rightContentY + scrollRight) / itemH);
                if (idx >= 0 && idx < filteredTrackedItems.size()) {
                    int itemY = rightContentY + (idx * itemH) - (int) scrollRight;
                    int crossX = rightBoxX + boxWidth - 36;
                    int crossY = itemY + 2;

                    if (mx >= crossX && mx < crossX + 24 && my >= crossY && my < crossY + 24) {
                        TrackerConfig.TrackedItem toRemove = filteredTrackedItems.get(idx);
                        confirmRemove(toRemove);
                    }
                }
            }
            if ((!narrowLayout || !showTrackedPane) && mx > leftBoxX + boxWidth - 10 && mx < leftBoxX + boxWidth && my >= leftBoxY + SEARCH_HEIGHT && my < leftBoxY + boxHeight) isDraggingScrollLeft = true;
            if ((!narrowLayout || showTrackedPane) && mx > rightBoxX + boxWidth - 10 && mx < rightBoxX + boxWidth && my >= rightBoxY + SEARCH_HEIGHT && my < rightBoxY + boxHeight) isDraggingScrollRight = true;
        }

        if (down) {
            int listVisibleH = boxHeight - SEARCH_HEIGHT - 4;
            if (isDraggingScrollLeft) {
                double contentH = filteredItems.size() * itemH;
                if (contentH > listVisibleH) {
                    double pct = (my - (leftBoxY + SEARCH_HEIGHT)) / (double) listVisibleH;
                    scrollLeft = Mth.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
                }
            }
            if (isDraggingScrollRight) {
                double contentH = filteredTrackedItems.size() * itemH;
                if (contentH > listVisibleH) {
                    double pct = (my - (rightBoxY + SEARCH_HEIGHT)) / (double) listVisibleH;
                    scrollRight = Mth.clamp(pct * contentH - (listVisibleH / 2.0), 0, contentH - listVisibleH);
                }
            }
        } else {
            isDraggingScrollLeft = false;
            isDraggingScrollRight = false;
        }
        wasMouseDown = down;
    }

    private void renderSearchBar(GuiGraphics context, int x, int y, int w, EditBox field) {
        // Subtle darker background for search area
        context.fill(x + 1, y + 1, x + w - 1, y + SEARCH_HEIGHT - 1, UiTheme.HEADER);

        // Separator line
        context.fill(x, y + SEARCH_HEIGHT, x + w, y + SEARCH_HEIGHT + 1, 0xFF555555);

        RenderUtils.drawPngIcon(context, PngIcons.Icon.SEARCH, x + w - 25, y + ((SEARCH_HEIGHT - PngIcons.ICON_SIZE) / 2), 0xFF888888);

        // Placeholder text "Search..." when field is empty
        if (field.getValue().isEmpty() && !field.isFocused()) {
            int textY = y + (SEARCH_HEIGHT - 8) / 2;
            context.drawString(font, Component.translatable(field == trackedSearchField ? "gui.resourcetracker.edit.search_tracked" : "gui.resourcetracker.edit.search_hint"), x + 6, textY, UiTheme.MUTED, false);
        }
    }

    private EditBox createIntField(int x, int y, int w, int value, Integer min, Integer max, Component tooltip, java.util.function.IntConsumer onChange) {
        EditBox field = new EditBox(font, x, y, w, 14, Component.empty());
        field.setValue(String.valueOf(value));
        field.setResponder(text -> {
            Integer parsed = parseInteger(text);
            boolean valid = parsed != null && (min == null || parsed >= min) && (max == null || parsed <= max);
            markFieldValidity(field, valid, tooltip);
            if (valid) onChange.accept(parsed);
        });
        return field;
    }

    private EditBox createFloatField(int x, int y, int w, float value, float min, float max, Component tooltip, FloatSetter onChange) {
        EditBox field = new EditBox(font, x, y, w, 14, Component.empty());
        field.setValue(String.valueOf(value));
        field.setResponder(text -> {
            Float parsed = parseFloat(text);
            boolean valid = parsed != null && Float.isFinite(parsed) && parsed >= min && parsed <= max;
            markFieldValidity(field, valid, tooltip);
            if (valid) onChange.set(parsed);
        });
        return field;
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Float parseFloat(String text) {
        try {
            return Float.parseFloat(text.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void markFieldValidity(EditBox field, boolean valid, Component tooltip) {
        field.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF5555);
        if (valid) invalidFields.remove(field);
        else invalidFields.put(field, tooltip);
    }

    private void showInvalidFieldTooltip(GuiGraphics context, int mouseX, int mouseY) {
        for (Map.Entry<EditBox, Component> entry : invalidFields.entrySet()) {
            EditBox field = entry.getKey();
            if (field.visible && (field.isFocused() || isFieldHovered(field, mouseX, mouseY))) {
                VersionCompat.setTooltip(context, font, entry.getValue(), mouseX, mouseY);
                return;
            }
        }
    }

    private boolean isFieldHovered(EditBox field, int mouseX, int mouseY) {
        return field.visible && mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
                && mouseY >= field.getY() && mouseY < field.getY() + field.getHeight();
    }

    private void cacheItemText(Item item) {
        String displayName = item.getName().getString();
        String itemId = VersionCompat.getItemId(item);
        itemDisplayNames.put(item, displayName);
        itemIds.put(item, itemId);
        itemDisplayNamesLower.put(item, displayName.toLowerCase(Locale.ROOT));
        itemIdsLower.put(item, itemId.toLowerCase(Locale.ROOT));
    }

    private String getCachedItemName(Item item) {
        String name = itemDisplayNames.get(item);
        if (name == null) {
            cacheItemText(item);
            name = itemDisplayNames.get(item);
        }
        return name == null ? "" : name;
    }

    private int getColorForIndex(int index) {
        return switch (index) {
            case 0 -> list.textColor;
            case 1 -> list.nameColor;
            case 2 -> list.backgroundColor;
            default -> 0xFFFFFF;
        };
    }

    private void setColorForIndex(int index, int color) {
        switch (index) {
            case 0 -> list.textColor = color;
            case 1 -> list.nameColor = color;
            case 2 -> list.backgroundColor = color;
            default -> throw new IllegalArgumentException("Unknown color index: " + index);
        }
    }

    @Override
    public void onClose() {
        TrackerHistory.endCountChange(list);
        TrackerConfig.saveList(list);
        net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
        if (parent instanceof MainScreen main) main.init();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showPreview) {
            if (mouseX >= styleX && mouseX <= styleX + styleWidth && mouseY >= styleTop && mouseY <= styleTop + styleHeight) {
                styleScroll = Mth.clamp(styleScroll - (int) (verticalAmount * 24), 0, Math.max(0, STYLE_CONTENT_HEIGHT - styleHeight));
                updateStyleWidgets();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        double amount = verticalAmount;
        int itemH = ITEM_ROW_HEIGHT;
        int leftContentY = leftBoxY + SEARCH_HEIGHT + 2;
        int rightContentY = rightBoxY + SEARCH_HEIGHT + 2;
        int listVisibleH = boxHeight - SEARCH_HEIGHT - 4;

        if ((!narrowLayout || !showTrackedPane) && mouseX >= leftBoxX && mouseX <= leftBoxX + boxWidth && mouseY >= leftContentY && mouseY <= leftBoxY + boxHeight) {
            double contentH = filteredItems.size() * itemH;
            if (contentH > listVisibleH) {
                scrollLeft = Mth.clamp(scrollLeft - (amount * itemH), 0, contentH - listVisibleH);
                return true;
            }
        }
        if ((!narrowLayout || showTrackedPane) && mouseX >= rightBoxX && mouseX <= rightBoxX + boxWidth && mouseY >= rightContentY && mouseY <= rightBoxY + boxHeight) {
            double contentH = filteredTrackedItems.size() * itemH;
            if (contentH > listVisibleH) {
                scrollRight = Mth.clamp(scrollRight - (amount * itemH), 0, contentH - listVisibleH);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private String itemStatus(TrackerConfig.TrackedItem item, boolean pin) {
        return pin ? (item.pinned ? "\u2605" : "\u2606") : (item.isManuallyCollected() ? "\u2713" : "\u25cb");
    }

    private void addItem(Item item) {
        String id = VersionCompat.getItemId(item);
        boolean found = false;
        for (TrackerConfig.TrackedItem ti : list.items) {
            if (ti.itemId.equals(id)) {
                found = true;
                break;
            }
        }
        if (!found) {
            TrackerHistory.record(list);
            list.items.add(new TrackerConfig.TrackedItem(id, 1));
            trackedItemIds.add(id);
            net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache();
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f);
            }
            refreshCountWidgets();
        }
    }

    private interface FloatSetter {
        void set(float value);
    }
}
