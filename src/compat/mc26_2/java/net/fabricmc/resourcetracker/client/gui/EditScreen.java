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
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.client.render.HudRenderCache;
import net.fabricmc.resourcetracker.client.render.HudRenderModel;
import net.fabricmc.resourcetracker.client.render.HudRenderer;
import net.fabricmc.resourcetracker.util.RenderUtils;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Editor state stays here while pickers and the raw-material screen are open. */
public class EditScreen extends EditorInputScreen {
    private final Screen parent;
    private final TrackerConfig.TrackingList list;
    private final ResourceViewState resources = new ResourceViewState();
    private final ResourceViewState catalogState = new ResourceViewState();
    private final List<Item> catalogItems = new ArrayList<>();
    private final Map<Item, String> catalogNames = new HashMap<>();
    private final Map<Item, String> catalogSearch = new HashMap<>();
    private final Map<Item, net.minecraft.world.item.ItemStack> catalogStacks = new HashMap<>();
    private final Map<String, AbstractWidget> controls = new LinkedHashMap<>();
    private final Map<AbstractWidget, Integer> scrollWidgets = new IdentityHashMap<>();
    private final Map<EditBox, Component> invalidFields = new IdentityHashMap<>();
    private final Map<String, String> countDrafts = new HashMap<>();
    private final Map<String, String> invalidDrafts = new HashMap<>();
    private final Map<String, Integer> scrollPositions = new HashMap<>();
    private String activeScrollContext;
    private boolean discardInvalidDrafts;
    private final List<LabelData> labels = new ArrayList<>();
    private final List<LabelData> scrollingLabels = new ArrayList<>();
    private final List<RowData> rows = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private final Map<Button, RowStatus> rowStatuses = new IdentityHashMap<>();
    private List<String> tableIds = List.of();
    private record LabelData(Component text, int x, int y, int color) {}
    private record RowData(TrackerConfig.TrackedItem tracked, Item catalogItem, int y) {}
    private record RowStatus(String name, int target, int stock, boolean collected, boolean pinned) {}
    private String tab = "resources", hudGroup = "content", focusKey, lastFocusKey;
    private boolean catalog, detail, fullPreview, advancedPosition, undoShortcutDown;
    private boolean wide;
    private int left, contentWidth, contentTop, footerY, tableX, tableWidth, rowTop, pageSize;
    private int styleX, styleTop, styleWidth, styleHeight, styleScroll, styleContentHeight;
    private int previewX, previewY, previewWidth, previewHeight, previewZoom = 2;
    private EditBox countField;
    private Button undoButton;
    private LabelData stockLabel;
    private int displayedStockCount;
    private RawMaterialsScreen rawScreen;

    public EditScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(Component.translatable("gui.resourcetracker.edit.title"));
        this.parent = parent;
        this.list = list;
    }

    private Component text(String key, Object... args) {
        return Component.translatable("gui.resourcetracker.compact." + key, args);
    }

    @Override
    protected void init() {
        if (activeScrollContext != null) scrollPositions.put(activeScrollContext, styleScroll);
        activeScrollContext = null;
        String restore = focusKey == null ? lastFocusKey : focusKey;
        int cursor = -1;
        if (discardInvalidDrafts) invalidDrafts.clear();
        for (var entry : controls.entrySet()) {
            if (!discardInvalidDrafts && entry.getValue() instanceof EditBox field && !entry.getKey().startsWith("count.")) {
                if (invalidFields.containsKey(field)) invalidDrafts.put(entry.getKey(), field.getValue());
                else invalidDrafts.remove(entry.getKey());
            }
            if (entry.getValue() == getFocused() && (restore == null || restore.equals(entry.getKey()))) {
                restore = entry.getKey();
                if (entry.getValue() instanceof EditBox field) cursor = field.getCursorPosition();
                break;
            }
        }
        discardInvalidDrafts = false;
        setFocused(null);
        clearWidgets(); controls.clear(); labels.clear(); rows.clear(); rowButtons.clear(); rowStatuses.clear();
        scrollWidgets.clear(); scrollingLabels.clear(); stockLabel = null; invalidFields.clear(); countField = null; tableIds = List.of();
        contentWidth = Math.max(80, Math.min(900, width - 24));
        left = (width - contentWidth) / 2;
        footerY = height - 28;
        wide = width >= 620 && height >= 300;
        contentTop = 62;
        if (selected() == null) detail = false;
        if (fullPreview) {
            label(text("preview"), left, 12, UiTheme.TEXT);
            previewX = left; previewY = 32; previewWidth = contentWidth; previewHeight = Math.max(50, footerY - 38);
            zoomButton(previewX + 8, previewY + 18, previewWidth - 16);
            button("back.preview", text("back_hud"), left, footerY, contentWidth, b -> { fullPreview = false; init(); });
        } else if (catalog) {
            buildCatalog();
        } else if (detail && !wide) {
            label(text("resource_detail"), left, 12, UiTheme.TEXT);
            button("back.detail", text("back_resources"), left, 30, contentWidth, b -> { detail = false; focusKey = "row." + resources.selectedItemId; init(); });
            styleX = left; styleTop = 56; styleWidth = contentWidth; styleHeight = Math.max(24, footerY - 64);
            beginScroll("detail." + resources.selectedItemId);
            buildDetail(left + 8, styleTop + 4, contentWidth - 16, true);
            finishScroll(); footer();
        } else {
            buildHeader();
            if (tab.equals("hud")) buildHud(); else buildResources();
            footer();
        }
        if (restore != null && controls.containsKey(restore)) {
            AbstractWidget target = controls.get(restore);
            if (target.active) {
                setFocused(target);
                if (cursor >= 0 && target instanceof EditBox field) {
                    field.setCursorPosition(Math.min(cursor, field.getValue().length()));
                    field.setHighlightPos(Math.min(cursor, field.getValue().length()));
                }
            }
        }
        focusKey = null;
    }

    private void buildHeader() {
        int titleWidth = Math.min(150, Math.max(60, contentWidth / 3));
        label(Component.literal(RenderUtils.shortenText(font, title.getString(), titleWidth - 6)), left, 14, UiTheme.TEXT);
        EditBox name = new EditBox(font, left + titleWidth, 8, contentWidth - titleWidth, 20, Component.translatable("gui.resourcetracker.edit.list_name"));
        name.setMaxLength(32767); name.setValue(list.name); name.setResponder(value -> list.name = value);
        register("name", name);
        int w = (contentWidth - 8) / 3;
        String[] ids = {"resources", "raw", "hud"};
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            button("tab." + id, Component.translatable("gui.resourcetracker.ui." + id), left + i * (w + 4), 34, w, b -> openTab(id));
        }
    }

    public void openTab(String next) {
        TrackerHistory.endCountChange(list);
        catalog = false; detail = false; fullPreview = false;
        if (minecraft == null) return;
        if ("raw".equals(next)) {
            if (rawScreen == null) rawScreen = new RawMaterialsScreen(this, list);
            VersionCompat.setScreen(minecraft, rawScreen);
        } else {
            tab = "hud".equals(next) ? "hud" : "resources";
            focusKey = "tab." + tab;
            if (VersionCompat.currentScreen(minecraft) == this) init(); else VersionCompat.setScreen(minecraft, this);
        }
    }

    private void footer() {
        int w = (contentWidth - 6) / 2;
        undoButton = button("undo", Component.translatable("gui.resourcetracker.undo"), left, footerY, w, b -> undo());
        undoButton.active = TrackerHistory.canUndo(list);
        undoButton.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.undo_hint")));
        button("done", Component.translatable("gui.resourcetracker.done"), left + w + 6, footerY, w, b -> { detail = false; onClose(); })
                .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.save_on_exit")));
    }

    private void buildResources() {
        tableX = left;
        tableWidth = wide ? contentWidth - 216 : contentWidth;
        int toolY = contentTop;
        int searchWidth = wide ? Math.max(60, tableWidth - 214) : tableWidth;
        search("resources.search", resources, left, toolY, searchWidth);
        int actionY = wide ? toolY : toolY + 24;
        int actionX = wide ? left + searchWidth + 6 : left;
        int addWidth = wide ? 70 : (tableWidth - 6) / 3;
        button("catalog.open", text("add"), actionX, actionY, addWidth, b -> { catalog = true; focusKey = "catalog.search"; init(); });
        button("list.actions", text("list_actions"), actionX + addWidth + 6, actionY,
                tableX + tableWidth - actionX - addWidth - 6, b -> openListActions());
        int headerY = actionY + 26;
        label(text("target_items"), tableX + tableWidth - 146, headerY, UiTheme.MUTED);
        label(text("stock"), tableX + tableWidth - 68, headerY, UiTheme.MUTED);
        rowTop = headerY + 14;
        String filter = resources.query.trim().toLowerCase(Locale.ROOT);
        List<TrackerConfig.TrackedItem> filtered = list.items.stream().filter(item ->
                (item.getDisplayName() + " " + item.itemId).toLowerCase(Locale.ROOT).contains(filter)).toList();
        buildRows(filtered, null, resources);
        if (wide) {
            beginScroll("detail." + resources.selectedItemId);
            styleX = left + tableWidth + 8; styleTop = contentTop; styleWidth = 208; styleHeight = Math.max(24, footerY - 8 - styleTop);
            if (selected() == null) {
                scrollingLabels.add(new LabelData(text("select_resource"), styleX + 8, styleTop + 8, UiTheme.MUTED));
                styleContentHeight = 32;
            } else buildDetail(styleX + 8, styleTop + 8, styleWidth - 16, true);
            finishScroll();
        }
    }

    private void search(String id, ResourceViewState state, int x, int y, int w) {
        EditBox field = new EditBox(font, x, y, Math.max(1, w), 20, text(id.startsWith("catalog") ? "catalog_search" : "list_search"));
        field.setMaxLength(32767); field.setValue(state.query);
        field.setHint(text(id.startsWith("catalog") ? "catalog_search" : "list_search"));
        field.setResponder(value -> { state.query = value; state.page = 0; focusKey = id; refreshSoon(); });
        register(id, field);
    }

    private void buildRows(List<TrackerConfig.TrackedItem> tracked, List<Item> available, ResourceViewState state) {
        int count = tracked == null ? available.size() : tracked.size();
        tableIds = tracked == null ? available.stream().map(VersionCompat::getItemId).toList()
                : tracked.stream().map(item -> item.itemId).toList();
        pageSize = Math.max(1, (footerY - 30 - rowTop) / 28);
        int pages = Math.max(1, (count + pageSize - 1) / pageSize);
        state.page = Mth.clamp(state.page, 0, pages - 1);
        for (int i = state.page * pageSize; i < Math.min(count, (state.page + 1) * pageSize); i++) {
            int y = rowTop + (i % pageSize) * 28;
            TrackerConfig.TrackedItem item = tracked == null ? null : tracked.get(i);
            Item choice = available == null ? null : available.get(i);
            String id = item == null ? VersionCompat.getItemId(choice) : item.itemId;
            Component narration = item == null ? Component.literal(catalogNames.get(choice))
                    : text("row", item.getDisplayName(), item.targetCount, item.cachedCount).copy()
                        .append(". ").append(text("manual_collected", status(item.isManuallyCollected())))
                        .append(". ").append(text("pinned", status(item.pinned)));
            Button row = button("row." + id, narration, tableX, y, tableWidth, b -> {
                if (choice != null) addCatalogItem(choice); else {
                    resources.selectedItemId = item.itemId;
                    detail = !wide; styleScroll = 0;
                    focusKey = "count." + item.itemId; init();
                }
            });
            row.setAlpha(0); row.setTooltip(Tooltip.create(narration.copy().append("\n").append(id)));
            rowButtons.add(row); rows.add(new RowData(item, choice, y));
        }
        if (count == 0) label(Component.translatable(list.items.isEmpty() && !catalog
                ? "gui.resourcetracker.edit.empty_list" : "gui.resourcetracker.edit.no_results"), tableX + 4, rowTop + 8, UiTheme.MUTED);
        int pagerY = footerY - 26;
        Button previous = button("page.previous", Component.literal("<"), tableX, pagerY, 28,
                b -> { state.page--; init(); });
        previous.active = state.page > 0;
        label(Component.literal((state.page + 1) + " / " + pages), tableX + 36, pagerY + 6, UiTheme.MUTED);
        Button next = button("page.next", Component.literal(">"), tableX + tableWidth - 28, pagerY, 28,
                b -> { state.page++; init(); });
        next.active = state.page + 1 < pages;
    }

    private void buildCatalog() {
        if (catalogItems.isEmpty()) {
            BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).forEach(item -> {
                String name = VersionCompat.getItemName(item);
                catalogItems.add(item); catalogNames.put(item, name);
                catalogStacks.put(item, new net.minecraft.world.item.ItemStack(item));
                catalogSearch.put(item, (name + " " + VersionCompat.getItemId(item)).toLowerCase(Locale.ROOT));
            });
            catalogItems.sort(Comparator.comparing(catalogNames::get));
        }
        label(text("catalog_title"), left, 12, UiTheme.TEXT);
        search("catalog.search", catalogState, left, 30, contentWidth);
        TrackerConfig.TrackedItem selected = selected();
        if (selected != null) {
            label(text("last_target", RenderUtils.shortenText(font, selected.getDisplayName(), Math.max(20, contentWidth - 170))), left, 58, UiTheme.MUTED);
            addCountField(selected, left + contentWidth - 78, 54, 78, false);
        } else label(text("catalog_hint"), left, 58, UiTheme.MUTED);
        tableX = left; tableWidth = contentWidth; rowTop = 80;
        String filter = catalogState.query.trim().toLowerCase(Locale.ROOT);
        buildRows(null, catalogItems.stream().filter(item -> catalogSearch.get(item).contains(filter)).toList(), catalogState);
        button("catalog.done", text("done_adding"), left, footerY, contentWidth, b -> { catalog = false; focusKey = "catalog.open"; init(); });
    }

    private void addCatalogItem(Item item) {
        boolean keepSearchFocus = controls.get("catalog.search") == getFocused();
        TrackerHistory.endCountChange(list);
        String id = VersionCompat.getItemId(item);
        TrackerConfig.TrackedItem target = list.items.stream().filter(existing -> existing.itemId.equals(id)).findFirst().orElse(null);
        if (target == null) {
            TrackerHistory.record(list);
            target = new TrackerConfig.TrackedItem(id, 1); list.items.add(target); invalidate();
        }
        resources.selectedItemId = id; catalogState.selectedItemId = id;
        focusKey = keepSearchFocus ? "catalog.search" : "count." + id; init();
    }

    private TrackerConfig.TrackedItem selected() {
        return list.items.stream().filter(item -> item.itemId.equals(resources.selectedItemId)).findFirst().orElse(null);
    }

    private void buildDetail(int x, int y, int w, boolean scrolling) {
        TrackerConfig.TrackedItem item = selected();
        if (item == null) return;
        scrollingLabels.add(new LabelData(Component.literal(item.getDisplayName()), x, y, UiTheme.TEXT));
        scrollingLabels.add(new LabelData(text("target_items"), x, y + 22, UiTheme.MUTED));
        addCountField(item, x + w - 76, y + 18, 76, scrolling);
        displayedStockCount = item.cachedCount;
        stockLabel = new LabelData(text("stock_count", displayedStockCount), x, y + 48, UiTheme.MUTED);
        scrollingLabels.add(stockLabel);
        styleButton("collected", text("manual_collected", status(item.isManuallyCollected())), x, y + 66, w, b -> {
            TrackerHistory.record(list); item.toggleCollected(); invalidate(); init();
        }).setLeadingIcon(item.isManuallyCollected() ? PngIcons.Icon.CHECK : null)
                .setTooltip(Tooltip.create(text("manual_hint")));
        styleButton("pinned", text("pinned", status(item.pinned)), x, y + 90, w, b -> {
            TrackerHistory.record(list); item.pinned = !item.pinned; invalidate(); init();
        });
        styleButton("replace", text("replace"), x, y + 114, w, b -> {
            TrackerHistory.endCountChange(list);
            Map<String, Integer> before = new HashMap<>();
            for (TrackerConfig.TrackedItem previous : list.items) before.put(previous.itemId, previous.targetCount);
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ItemPickerScreen(this, list, item, () -> {
                countDrafts.clear();
                if (!list.items.contains(item)) resources.selectedItemId = list.items.stream().filter(current ->
                        !Objects.equals(before.get(current.itemId), current.targetCount)).map(current -> current.itemId).findFirst().orElse(null);
            }));
        });
        styleButton("remove", text("remove_resource"), x, y + 138, w, b -> {
            TrackerHistory.record(list); list.items.remove(item); countDrafts.remove(item.itemId);
            resources.selectedItemId = null; detail = false; invalidate(); init();
        });
        styleContentHeight = y + 164 - styleTop;
    }

    private void addCountField(TrackerConfig.TrackedItem item, int x, int y, int w, boolean scrolling) {
        EditBox field = new EditBox(font, x, y, w, 20, text("target_items").copy().append(": ").append(item.getDisplayName()));
        field.setMaxLength(16); field.setValue(countDrafts.getOrDefault(item.itemId, String.valueOf(item.targetCount)));
        Component validation = Component.translatable("gui.resourcetracker.validation.target_count");
        field.setTooltip(Tooltip.create(validation.copy().append("\n").append(item.getDisplayName()).append("\n").append(item.itemId)));
        field.setResponder(value -> {
            Integer parsed = parseInteger(value);
            boolean valid = parsed != null && parsed >= 1 && parsed <= TrackerConfig.MAX_TARGET_COUNT;
            // Valid input is already in the model; only preserve unfinished/invalid input.
            if (valid) countDrafts.remove(item.itemId); else countDrafts.put(item.itemId, value);
            markFieldValidity(field, valid, validation);
            if (valid && item.targetCount != parsed) {
                TrackerHistory.recordCountChange(list, item); item.setTargetCount(parsed); invalidate();
                if (controls.get("collected") instanceof TrackerButton collected) {
                    collected.setMessage(text("manual_collected", status(item.isManuallyCollected())));
                    collected.setLeadingIcon(item.isManuallyCollected() ? PngIcons.Icon.CHECK : null);
                }
            }
        });
        Integer parsed = parseInteger(field.getValue());
        markFieldValidity(field, parsed != null && parsed >= 1 && parsed <= TrackerConfig.MAX_TARGET_COUNT, validation);
        register("count." + item.itemId, field); countField = field;
        if (scrolling) addScrolling(field);
    }

    private void openListActions() {
        if (minecraft != null) VersionCompat.setScreen(minecraft, new EditorActionsScreen());
    }

    private final class EditorActionsScreen extends PagedDataScreen {
        private EditorActionsScreen() { super(EditScreen.this, EditScreen.this.text("list_actions")); }
        @Override protected void init() {
            layout(40);
            button(Component.translatable("gui.resourcetracker.family.button"), left, rowTop, contentWidth, b -> {
                if (minecraft != null) VersionCompat.setScreen(minecraft, new FamilyReplacementScreen(EditScreen.this, list));
            });
            button(Component.translatable("gui.resourcetracker.edit.clear"), left, rowTop + 26, contentWidth, b -> confirmClear());
            button(Component.translatable("gui.back"), left, height - 26, contentWidth, b -> onClose());
        }
    }

    private void confirmClear() {
        if (minecraft == null) return;
        VersionCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) { TrackerHistory.record(list); list.items.clear(); resources.selectedItemId = null;
                resources.page = 0; countDrafts.clear(); invalidate(); }
            VersionCompat.setScreen(minecraft, this);
        }, Component.translatable("gui.resourcetracker.edit.clear"), Component.translatable("gui.resourcetracker.edit.clear_confirm")));
    }

    private Component status(boolean enabled) { return text(enabled ? "on" : "off"); }

    private void buildHud() {
        beginScroll("hud." + hudGroup);
        styleX = left; styleTop = contentTop;
        styleWidth = wide ? Math.min(290, contentWidth / 2) : contentWidth;
        styleHeight = Math.max(24, footerY - 8 - styleTop);
        if (!wide) {
            button("preview.open", text("preview"), left, contentTop, contentWidth, b -> { fullPreview = true; init(); });
            styleTop += 26; styleHeight -= 26;
        } else {
            previewX = styleX + styleWidth + 10; previewY = styleTop;
            previewWidth = contentWidth - styleWidth - 10; previewHeight = styleHeight;
            zoomButton(previewX + 8, previewY + 18, previewWidth - 16);
        }
        int x = styleX + 8, w = styleWidth - 16, y = styleTop + 4;
        for (String group : List.of("content", "appearance", "position")) {
            styleButton("group." + group, text("group." + group).copy().append(group.equals(hudGroup) ? " ▾" : " ▸"), x, y, w,
                    b -> { hudGroup = group; styleScroll = 0; focusKey = "group." + group; init(); });
            y += 24;
            if (!hudGroup.equals(group)) continue;
            if (group.equals("content")) {
                choice("source", text("source", text(list.hudRaw ? "source.raw" : "source.targets")), x, y, w,
                        List.of(false, true), list.hudRaw, v -> text(v ? "source.raw" : "source.targets"), v -> { list.hudRaw = v; invalidate(); }); y += 24;
                choice("mode", text("mode", Component.translatable(list.showRemaining ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count")), x, y, w,
                        List.of(false, true), list.showRemaining, v -> Component.translatable(v ? "gui.resourcetracker.edit.mode_need" : "gui.resourcetracker.edit.mode_count"), v -> list.showRemaining = v); y += 24;
                choice("units", Component.translatable("gui.resourcetracker.edit.units." + list.countUnit.name().toLowerCase(Locale.ROOT)), x, y, w,
                        Arrays.asList(TrackerConfig.CountUnit.values()), list.countUnit,
                        v -> Component.translatable("gui.resourcetracker.edit.units." + v.name().toLowerCase(Locale.ROOT)).append(" · ").append(text("unit_example." + v.name().toLowerCase(Locale.ROOT))), v -> list.countUnit = v); y += 24;
                styleButton("hide", text("hide_completed", status(list.hideCompleted)), x, y, w,
                        b -> { list.hideCompleted = !list.hideCompleted; init(); }); y += 24;
                choice("sort", Component.translatable(list.sortRemaining ? "gui.resourcetracker.edit.sort_remaining" : "gui.resourcetracker.edit.sort_original"), x, y, w,
                        List.of(false, true), list.sortRemaining, v -> Component.translatable(v ? "gui.resourcetracker.edit.sort_remaining" : "gui.resourcetracker.edit.sort_original"), v -> list.sortRemaining = v); y += 24;
                scrollingLabels.add(new LabelData(text("row_limit"), x, y + 5, UiTheme.MUTED));
                EditBox limit = createIntField(x + w - 56, y, 56, list.maxVisibleRows, 0, 100000,
                        Component.translatable("gui.resourcetracker.validation.max_rows"), v -> list.maxVisibleRows = v);
                register("row_limit", limit); addScrolling(limit); y += 24;
            } else if (group.equals("appearance")) {
                styleButton("icons", text("icons", status(list.showIcons)), x, y, w, b -> { list.showIcons = !list.showIcons; init(); }); y += 24;
                choice("columns", columnsText(list.columns), x, y, w, List.of(0,1,2,3,4,5), list.columns, this::columnsText, v -> list.columns = v); y += 24;
                scrollingLabels.add(new LabelData(text("hud_scale"), x, y + 5, UiTheme.MUTED));
                EditBox scale = createFloatField(x + w - 56, y, 56, list.scale, TrackerConfig.MIN_SCALE, TrackerConfig.MAX_SCALE,
                        Component.translatable("gui.resourcetracker.validation.scale"), v -> list.scale = v);
                register("scale", scale); addScrolling(scale); y += 24;
                String[] keys = {"gui.resourcetracker.color.text", "gui.resourcetracker.color.title", "gui.resourcetracker.color.background"};
                for (int i = 0; i < keys.length; i++) {
                    int index = i; String key = keys[i];
                    styleButton("color." + i, Component.translatable(key), x, y, w, b -> {
                        if (minecraft != null) VersionCompat.setScreen(minecraft, new ColorPickerScreen(this,
                                Component.translatable(key), getColorForIndex(index), color -> setColorForIndex(index, color)));
                    }); y += 24;
                }
                styleButton("reset", Component.translatable("gui.resourcetracker.edit.reset"), x, y, w, b -> confirmReset()); y += 24;
            } else {
                styleButton("move", text("move_hud"), x, y, w, b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new HudMoveScreen(this)); }); y += 24;
                styleButton("advanced", text("exact_position").copy().append(advancedPosition ? " ▾" : " ▸"), x, y, w,
                        b -> { advancedPosition = !advancedPosition; init(); }); y += 24;
                if (advancedPosition) {
                    scrollingLabels.add(new LabelData(Component.literal("X"), x, y + 4, UiTheme.MUTED));
                    EditBox xf = createIntField(x + 18, y, w / 2 - 24, list.x, null, null, Component.translatable("gui.resourcetracker.validation.integer"), v -> list.x = v);
                    register("x", xf); addScrolling(xf);
                    scrollingLabels.add(new LabelData(Component.literal("Y"), x + w / 2, y + 4, UiTheme.MUTED));
                    EditBox yf = createIntField(x + w / 2 + 18, y, w / 2 - 18, list.y, null, null, Component.translatable("gui.resourcetracker.validation.integer"), v -> list.y = v);
                    register("y", yf); addScrolling(yf); y += 24;
                }
            }
        }
        styleContentHeight = y - styleTop + 4;
        finishScroll();
    }

    private Component columnsText(Integer columns) {
        return Component.translatable(columns == 0 ? "gui.resourcetracker.edit.columns_auto" : "gui.resourcetracker.edit.columns", columns);
    }

    private void confirmReset() {
        if (minecraft != null) VersionCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
            if (confirmed) { TrackerConfig.applyDefaults(list); styleScroll = 0; discardInvalidDrafts = true; }
            VersionCompat.setScreen(minecraft, this);
        }, Component.translatable("gui.resourcetracker.edit.reset"), text("reset_scope")));
    }

    private <T> void choice(String id, Component label, int x, int y, int w, List<T> values, T selected,
                            Function<T, Component> display, Consumer<T> onSelect) {
        styleButton(id, label.copy().append(" ▾"), x, y, w, b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this, label, values, selected, display, onSelect));
        });
    }

    private void zoomButton(int x, int y, int w) {
        button("preview.zoom", Component.translatable("gui.resourcetracker.edit.preview_zoom", previewZoom * 100).append(" ▾"), x, y, w, b -> {
            if (minecraft != null) VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this, text("preview_zoom_title"),
                    List.of(1,2,3), previewZoom, v -> Component.literal(v * 100 + "%"), v -> previewZoom = v));
        });
    }

    private TrackerButton button(String id, Component label, int x, int y, int w, Button.OnPress onPress) {
        TrackerButton result = TrackerButton.builder(label, b -> { focusKey = id; onPress.onPress(b); }).bounds(x, y, Math.max(1, w), 20).tooltip(Tooltip.create(label)).build();
        register(id, result); return result;
    }
    private TrackerButton styleButton(String id, Component label, int x, int y, int w, Button.OnPress onPress) {
        TrackerButton result = button(id, label, x, y, w, onPress); addScrolling(result); return result;
    }
    private void register(String id, AbstractWidget widget) {
        if (widget instanceof EditBox field && invalidDrafts.containsKey(id)) field.setValue(invalidDrafts.get(id));
        controls.put(id, widget); addRenderableWidget(widget);
    }
    private void label(Component label, int x, int y, int color) { labels.add(new LabelData(label, x, y, color)); }
    private void addScrolling(AbstractWidget widget) { scrollWidgets.put(widget, widget.getY()); addWidget(new StyleFocusTarget(widget)); }

    private void finishScroll() {
        styleScroll = Mth.clamp(styleScroll, 0, Math.max(0, styleContentHeight - styleHeight));
        for (var entry : scrollWidgets.entrySet()) {
            AbstractWidget widget = entry.getKey(); widget.setY(entry.getValue() - styleScroll);
            widget.visible = widget.getY() >= styleTop && widget.getY() + widget.getHeight() <= styleTop + styleHeight;
        }
    }
    private void beginScroll(String context) {
        activeScrollContext = context; styleScroll = scrollPositions.getOrDefault(context, 0);
    }

    private static final class StyleFocusTarget implements net.minecraft.client.gui.components.events.GuiEventListener,
            net.minecraft.client.gui.narration.NarratableEntry {
        private final AbstractWidget widget;
        private StyleFocusTarget(AbstractWidget widget) { this.widget = widget; }
        @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) {
            return event instanceof FocusNavigationEvent.TabNavigation && widget.active && !widget.visible && !widget.isFocused()
                    ? ComponentPath.leaf(widget) : null;
        }
        @Override public void setFocused(boolean focused) {}
        @Override public boolean isFocused() { return false; }
        @Override public boolean isActive() { return false; }
        @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {}
    }

    @Override public void setFocused(net.minecraft.client.gui.components.events.GuiEventListener focused) {
        // Native mouse dispatch can focus the old button after its callback rebuilt this screen.
        if (focused instanceof AbstractWidget widget && !controls.containsValue(widget)) return;
        if (getFocused() != focused) TrackerHistory.endCountChange(list);
        for (var entry : controls.entrySet()) if (entry.getValue() == focused) lastFocusKey = entry.getKey();
        if (focused instanceof AbstractWidget widget && scrollWidgets.containsKey(widget)) {
            int y = scrollWidgets.get(widget);
            if (y - styleScroll < styleTop) styleScroll = y - styleTop;
            else if (y + widget.getHeight() - styleScroll > styleTop + styleHeight)
                styleScroll = y + widget.getHeight() - styleTop - styleHeight;
            finishScroll();
        }
        super.setFocused(focused);
    }

    @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        int focusedIndex = -1;
        for (int i = 0; i < rowButtons.size(); i++) if (rowButtons.get(i).isFocused()) focusedIndex = i;
        if (focusedIndex >= 0) {
            if (event instanceof FocusNavigationEvent.TabNavigation navigation && navigation.forward()) {
                if (countField != null) { setFocused(countField); return ComponentPath.leaf(countField); }
                return ComponentPath.leaf(controls.get("page.next").active ? controls.get("page.next") : controls.get(catalog ? "catalog.done" : "done"));
            }
            if (event instanceof FocusNavigationEvent.ArrowNavigation arrow
                    && (arrow.direction() == ScreenDirection.UP || arrow.direction() == ScreenDirection.DOWN)) {
                ResourceViewState state = catalog ? catalogState : resources;
                int next = state.page * pageSize + focusedIndex + (arrow.direction() == ScreenDirection.DOWN ? 1 : -1);
                if (next >= 0 && next < tableIds.size()) {
                    String id = tableIds.get(next); state.page = next / pageSize; focusKey = "row." + id; init();
                    return ComponentPath.leaf(controls.get("row." + id));
                }
            }
        }
        return super.nextFocusPath(event);
    }

    @Override protected boolean editorKeyPressed(int keyCode) {
        if (keyCode == 32 && !catalog) {
            for (int i = 0; i < rowButtons.size(); i++) if (rowButtons.get(i).isFocused()) {
                TrackerConfig.TrackedItem item = rows.get(i).tracked;
                if (item != null) { TrackerHistory.record(list); item.toggleCollected(); invalidate(); init(); return true; }
            }
        }
        if (keyCode == 257 || keyCode == 335) {
            if (countField != null && countField.isFocused()) {
                if (!invalidFields.containsKey(countField)) TrackerHistory.endCountChange(list);
                return true;
            }
            if (catalog && controls.get("catalog.search") == getFocused()) {
                if (!rows.isEmpty()) addCatalogItem(rows.getFirst().catalogItem);
                return true;
            }
        }
        return false;
    }

    private void refreshSoon() { if (minecraft != null) minecraft.execute(this::init); }
    private void invalidate() { net.fabricmc.resourcetracker.client.ResourceTrackerClient.invalidateTargetItemCache(); }
    private void undo() {
        if (TrackerHistory.undo(list)) { countDrafts.clear(); init(); }
    }
    @Override public void tick() {
        super.tick();
        if (undoButton != null) undoButton.active = TrackerHistory.canUndo(list);
        if (minecraft != null) {
            boolean down = VersionCompat.isUndoPressed(minecraft);
            if (down && !undoShortcutDown) undo();
            undoShortcutDown = down;
        }
        refreshStockLabel();
        refreshRowMessages();
    }

    /** Keep keyboard narration and hover help in sync with the same cached data as the table. */
    private void refreshRowMessages() {
        for (int i = 0; i < rows.size(); i++) {
            TrackerConfig.TrackedItem item = rows.get(i).tracked;
            if (item == null) continue;
            Button button = rowButtons.get(i);
            String name = item.getDisplayName();
            boolean collected = item.isManuallyCollected();
            RowStatus old = rowStatuses.get(button);
            if (old != null && old.target == item.targetCount && old.stock == item.cachedCount
                    && old.collected == collected && old.pinned == item.pinned && old.name.equals(name)) continue;
            Component narration = text("row", name, item.targetCount, item.cachedCount).copy()
                    .append(". ").append(text("manual_collected", status(collected)))
                    .append(". ").append(text("pinned", status(item.pinned)));
            button.setMessage(narration);
            button.setTooltip(Tooltip.create(narration.copy().append("\n").append(item.itemId)));
            rowStatuses.put(button, new RowStatus(name, item.targetCount, item.cachedCount, collected, item.pinned));
        }
    }

    /** Read the tick-cached stock without rebuilding focused inputs or recounting inventory. */
    private void refreshStockLabel() {
        if (stockLabel == null) return;
        TrackerConfig.TrackedItem item = selected();
        if (item == null || displayedStockCount == item.cachedCount) return;
        int index = scrollingLabels.indexOf(stockLabel);
        if (index < 0) return;
        displayedStockCount = item.cachedCount;
        stockLabel = new LabelData(text("stock_count", displayedStockCount), stockLabel.x, stockLabel.y, stockLabel.color);
        scrollingLabels.set(index, stockLabel);
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {}
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xA0000000);
        context.fill(left - 8, 4, left + contentWidth + 8, height - 4, UiTheme.WINDOW);
        if (fullPreview || (!catalog && !detail && tab.equals("hud") && wide)) renderPreview(context);
        if (!scrollWidgets.isEmpty() || !scrollingLabels.isEmpty()) {
            context.fill(styleX, styleTop, styleX + styleWidth, styleTop + styleHeight, UiTheme.PANEL);
        }
        for (int i = 0; i < rows.size(); i++) {
            RowData row = rows.get(i); TrackerConfig.TrackedItem tracked = row.tracked;
            String id = tracked == null ? VersionCompat.getItemId(row.catalogItem) : tracked.itemId;
            boolean selected = id.equals(resources.selectedItemId);
            context.fill(tableX, row.y, tableX + tableWidth, row.y + 26,
                    selected || rowButtons.get(i).isHoveredOrFocused() ? UiTheme.HOVER : UiTheme.PANEL);
            if (selected) context.fill(tableX, row.y, tableX + 2, row.y + 26, UiTheme.ACCENT);
            if (tracked == null) context.item(catalogStacks.get(row.catalogItem), tableX + 4, row.y + 5);
            else if (tracked.isValid()) context.item(tracked.getStack(), tableX + 4, row.y + 5);
            String name = tracked == null ? catalogNames.get(row.catalogItem) : tracked.getDisplayName();
            int nameWidth = tableWidth - (tracked == null ? 54 : 178);
            context.text(font, RenderUtils.shortenText(font, name, Math.max(20, nameWidth)), tableX + 24, row.y + 8, UiTheme.TEXT, false);
            if (tracked != null) {
                context.text(font, String.valueOf(tracked.targetCount), tableX + tableWidth - 146, row.y + 8, UiTheme.TEXT, false);
                int stockX = tableX + tableWidth - 68;
                int stockColor = tracked.isCompleted() ? UiTheme.ACCENT : UiTheme.MUTED;
                int checkWidth = tracked.isManuallyCollected() ? PngIcons.SMALL_CHECK_ADVANCE : 0;
                int pinWidth = tracked.pinned ? font.width(" ★") : 0;
                String stock = RenderUtils.shortenText(font, String.valueOf(tracked.cachedCount), 64 - checkWidth - pinWidth);
                context.text(font, stock, stockX, row.y + 8, stockColor, false);
                int indicatorX = stockX + font.width(stock);
                if (tracked.isManuallyCollected()) RenderUtils.drawPngIconInBox(context, PngIcons.Icon.CHECK_SMALL,
                        indicatorX, row.y + 8, checkWidth, font.lineHeight, stockColor);
                if (tracked.pinned) context.text(font, " ★", indicatorX + checkWidth, row.y + 8, stockColor, false);
            } else {
                boolean existing = list.items.stream().anyMatch(item -> item.itemId.equals(id));
                if (existing) RenderUtils.drawPngIconInBox(context, PngIcons.Icon.CHECK,
                        tableX + tableWidth - 24, row.y + 3, 20, 20, UiTheme.ACCENT);
                else context.text(font, "+", tableX + tableWidth - 20, row.y + 8, UiTheme.MUTED, false);
            }
        }
        super.extractRenderState(context, mouseX, mouseY, delta);
        for (int i = 0; i < 3; i++) {
            AbstractWidget color = controls.get("color." + i);
            if (color != null && color.visible) ColorPickerScreen.drawSwatch(context,
                    color.getX() + color.getWidth() - 22, color.getY() + 2, 16, 16, getColorForIndex(i));
        }
        for (LabelData label : labels) {
            boolean invalidCount = catalog && countField != null && invalidFields.containsKey(countField) && label.y == 58;
            Component message = invalidCount ? text("invalid_count") : label.text;
            int maxWidth = invalidCount ? countField.getX() - label.x - 6 : left + contentWidth - label.x;
            context.text(font, RenderUtils.shortenText(font, message.getString(), Math.max(1, maxWidth)),
                    label.x, label.y, invalidCount ? 0xFFFF7777 : label.color, false);
        }
        if (!scrollingLabels.isEmpty()) {
            context.enableScissor(styleX, styleTop, styleX + styleWidth, styleTop + styleHeight);
            for (LabelData label : scrollingLabels) {
                boolean invalidCount = countField != null && invalidFields.containsKey(countField) && scrollWidgets.containsKey(countField)
                        && label.y == scrollWidgets.get(countField) + 30;
                context.text(font, RenderUtils.shortenText(font, (invalidCount ? text("invalid_count") : label.text).getString(),
                    Math.max(1, styleX + styleWidth - label.x - 6)), label.x, label.y - styleScroll, invalidCount ? 0xFFFF7777 : label.color, false);
            }
            context.disableScissor();
        }
        if (styleContentHeight > styleHeight && !scrollWidgets.isEmpty()) RenderUtils.drawStyledScrollbar(context,
                styleX + styleWidth - 5, styleTop, styleHeight, styleContentHeight, styleScroll);
        if (!catalog && !detail && !fullPreview) {
            int w = (contentWidth - 8) / 3;
            int x = left + (tab.equals("hud") ? 2 * (w + 4) : 0);
            context.fill(x, 55, x + w, 57, UiTheme.ACCENT);
        }
        showInvalidFieldTooltip(context, mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!scrollWidgets.isEmpty() && x >= styleX && x < styleX + styleWidth && y >= styleTop && y < styleTop + styleHeight) {
            styleScroll = Mth.clamp(styleScroll - (int)(vertical * 24), 0, Math.max(0, styleContentHeight - styleHeight));
            finishScroll();
            // Wheel scrolling can hide the active input. Tab navigation uses setFocused()
            // to reveal its destination and must keep that separate auto-scroll behavior.
            if (getFocused() instanceof AbstractWidget widget && scrollWidgets.containsKey(widget) && !widget.visible) {
                setFocused(null);
                lastFocusKey = null;
                focusKey = null;
            }
            return true;
        }
        if (!rows.isEmpty() && x >= tableX && x < tableX + tableWidth && y >= rowTop && y < footerY - 28 && vertical != 0) {
            ResourceViewState state = catalog ? catalogState : resources;
            state.page = Math.max(0, state.page + (vertical < 0 ? 1 : -1)); init(); return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void onClose() {
        if (fullPreview) { fullPreview = false; focusKey = "preview.open"; init(); return; }
        if (catalog) { catalog = false; focusKey = "catalog.open"; init(); return; }
        if (detail && !wide) { detail = false; focusKey = "row." + resources.selectedItemId; init(); return; }
        TrackerHistory.endCountChange(list); TrackerConfig.saveList(list); invalidate();
        if (parent instanceof MainScreen main) main.init();
        if (minecraft != null) VersionCompat.setScreen(minecraft, parent);
    }

    private interface FloatSetter { void set(float value); }
    private void renderPreview(GuiGraphicsExtractor context) {
        int panelX = previewX, panelY = previewY;
        int panelWidth = previewWidth, panelHeight = previewHeight;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, UiTheme.PANEL);
        RenderUtils.drawBoxOutline(context, panelX, panelY, panelWidth, panelHeight);
        context.centeredText(font, Component.translatable("gui.resourcetracker.edit.preview_title"),
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
            context.text(font, RenderUtils.shortenText(font, message, panelWidth - 16),
                    panelX + 8, panelY + panelHeight - 12, UiTheme.MUTED, false);
        }
    }

    private EditBox createIntField(int x, int y, int w, int value, Integer min, Integer max, Component tooltip, java.util.function.IntConsumer onChange) {
        EditBox field = new EditBox(font, x, y, w, 20, Component.empty());
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
        EditBox field = new EditBox(font, x, y, w, 20, Component.empty());
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

    private void showInvalidFieldTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY) {
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

}
