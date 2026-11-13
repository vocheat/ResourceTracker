package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.model.CountFormatter;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** A derived view in the original editor session; opening it never changes the HUD source. */
public final class RawMaterialsScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final ResourceViewState view = new ResourceViewState();
    private EditBox searchField;
    private List<TrackerConfig.TrackedItem> displayedItems;

    public RawMaterialsScreen(EditScreen parent, TrackerConfig.TrackingList list) {
        super(parent, Component.translatable("gui.resourcetracker.edit.raw_title", list.name));
        this.list = list;
    }

    @Override protected TrackerConfig.TrackingList undoList() { return list; }

    @Override protected void init() {
        boolean searching = searchField != null && searchField.isFocused();
        int cursor = searchField == null ? view.query.length() : searchField.getCursorPosition();
        layout(104);
        showPanel();
        int tabWidth = (contentWidth - 8) / 3;
        button(Component.translatable("gui.resourcetracker.ui.resources"), left, 30, tabWidth,
                b -> ((EditScreen) parent).openTab("resources"));
        button(Component.translatable("gui.resourcetracker.ui.raw"), left + tabWidth + 4, 30, tabWidth, b -> {});
        button(Component.translatable("gui.resourcetracker.ui.hud"), left + (tabWidth + 4) * 2, 30, tabWidth,
                b -> ((EditScreen) parent).openTab("hud"));
        Component summary = Component.translatable("gui.resourcetracker.raw.rules_summary", WoodChoiceScreen.name(list.woodType),
                Component.translatable(list.includeSmelting ? "gui.resourcetracker.raw.smelting_on" : "gui.resourcetracker.raw.smelting_off"),
                Component.translatable(RawRulesScreen.stoneExpanded(list) ? "gui.resourcetracker.raw.stone_smelt" : "gui.resourcetracker.raw.stone_ready"));
        List<String> summaryLines = RawTextScreen.wrap(font, summary.getString(), contentWidth);
        for (int i = 0; i < Math.min(2, summaryLines.size()); i++)
            label(Component.literal(summaryLines.get(i)), left, 54 + i * 11, contentWidth).setTooltip(Tooltip.create(summary));
        button(Component.translatable("gui.resourcetracker.raw.rules"), left, 80, 100,
                b -> open(new RawRulesScreen(this, list)));
        searchField = new EditBox(font, left + 106, 80, contentWidth - 106, 20,
                Component.translatable("gui.resourcetracker.raw.search"));
        searchField.setHint(Component.translatable("gui.resourcetracker.raw.search"));
        searchField.setMaxLength(120);
        searchField.setValue(view.query);
        searchField.setCursorPosition(Math.min(cursor, view.query.length()));
        searchField.setResponder(value -> {
            view.query = value; view.page = 0; page = 0;
            if (minecraft != null) minecraft.execute(() -> { init(); setFocused(searchField); });
        });
        addRenderableWidget(searchField);
        if (searching) setFocused(searchField);
        displayedItems = list.getRawItems();
        String filter = view.query.toLowerCase(Locale.ROOT).trim();
        List<TrackerConfig.TrackedItem> items = displayedItems.stream().filter(item ->
                item.itemId.toLowerCase(Locale.ROOT).contains(filter) || item.getDisplayName().toLowerCase(Locale.ROOT).contains(filter)).toList();
        boolean wide = width >= 620 && height >= 300;
        int tableWidth = wide ? contentWidth - 220 : contentWidth;
        pageSize = Math.max(1, (height - 82 - rowTop) / 24);
        pagination(items.size(), height - 76);
        view.page = page;
        for (int i = page * pageSize; i < Math.min(items.size(), (page + 1) * pageSize); i++) {
            TrackerConfig.TrackedItem item = items.get(i);
            int y = rowTop + (i % pageSize) * 24;
            String status = quantity(item);
            String full = item.getDisplayName() + " — " + status + ", "
                    + Component.translatable("gui.resourcetracker.raw.manual", state(item.isManuallyCollected())).getString() + ", "
                    + Component.translatable("gui.resourcetracker.raw.pinned", state(item.pinned)).getString();
            String indicators = (item.itemId.equals(view.selectedItemId) ? "▶ " : "")
                    + (item.pinned ? "★ " : "");
            TrackerButton.Builder row = TrackerButton.builder(Component.empty(), b -> {
                view.selectedItemId = item.itemId;
                view.page = page;
                if (wide) init(); else open(new DetailScreen(this, list, item.itemId));
            }).bounds(left, y, tableWidth, 20)
                    .leadingIcon(item.isManuallyCollected() ? PngIcons.Icon.CHECK_SMALL : null)
                    .createNarration(s -> Component.literal(full))
                    .tooltip(Tooltip.create(Component.literal(full + "\n" + item.itemId)));
            if (item.itemId.equals(view.selectedItemId)) row.primary();
            addRenderableWidget(row.build());
            int countWidth = Math.min(tableWidth / 2, font.width(status) + 8);
            int checkWidth = item.isManuallyCollected() ? PngIcons.SMALL_CHECK_ADVANCE : 0;
            label(Component.literal(indicators + item.getDisplayName()), left + 4 + checkWidth, y + 5,
                    tableWidth - countWidth - 8 - checkWidth);
            label(Component.literal(status), left + tableWidth - countWidth, y + 5, countWidth - 4);
        }
        if (items.isEmpty()) label(Component.translatable("gui.resourcetracker.raw.empty"), left, rowTop + 5, tableWidth);
        if (wide) {
            TrackerConfig.TrackedItem selected = selectedItem(list, view.selectedItemId);
            int detailX = left + tableWidth + 8;
            if (selected == null) wrappedLabel(Component.translatable("gui.resourcetracker.raw.select"), detailX, rowTop + 4, 212, UiTheme.MUTED);
            else selectedControls(selected, detailX, rowTop, 212);
        }
        int half = (contentWidth - 6) / 2;
        button(Component.translatable("gui.resourcetracker.recipe.title"), left, height - 50, half,
                b -> open(new RecipeOptionsScreen(this, list, null)));
        button(Component.translatable("gui.resourcetracker.recipe.diagnostics", list.rawDiagnostics.size()), left + half + 6, height - 50, half,
                b -> open(new RawTextScreen(this, Component.translatable("gui.resourcetracker.recipe.diagnostics", list.rawDiagnostics.size()),
                        list.rawDiagnostics.stream().map(DiagnosticText::of).toList())));
        button(Component.translatable("gui.resourcetracker.undo"), left, height - 26, half,
                b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
        button(Component.translatable("gui.resourcetracker.done"), left + half + 6, height - 26, half, b -> onClose());
    }

    @Override
    protected boolean drawEditorBackground(BackgroundFill fill) {
        if (!super.drawEditorBackground(fill)) return false;
        int tabWidth = (contentWidth - 8) / 3;
        int x = left + tabWidth + 4;
        fill.draw(x, 51, x + tabWidth, 53, UiTheme.ACCENT);
        return true;
    }

    private void selectedControls(TrackerConfig.TrackedItem item, int x, int y, int w) {
        label(Component.literal(item.getDisplayName()), x, y, w);
        label(Component.literal(quantity(item)), x, y + 18, w);
        ((TrackerButton) button(Component.translatable("gui.resourcetracker.raw.manual", state(item.isManuallyCollected())), x, y + 40, w,
                b -> { TrackerHistory.record(list); item.toggleCollected(); changed(); init(); }))
                .setLeadingIcon(item.isManuallyCollected() ? PngIcons.Icon.CHECK : null)
                .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.raw.manual_hint")));
        button(Component.translatable("gui.resourcetracker.raw.pinned", state(item.pinned)), x, y + 64, w,
                b -> { TrackerHistory.record(list); item.pinned = !item.pinned; changed(); init(); });
        button(Component.translatable("gui.resourcetracker.raw.recipe_details"), x, y + 88, w,
                b -> open(new RecipeOptionsScreen(this, list, item.itemId)));
    }
    private void open(Screen screen) { if (minecraft != null) VersionCompat.setScreen(minecraft, screen); }
    static Component state(boolean enabled) { return Component.translatable(enabled ? "gui.resourcetracker.raw.on" : "gui.resourcetracker.raw.off"); }
    static void changed() { ResourceTrackerClient.invalidateTargetItemCache(); }
    private static TrackerConfig.TrackedItem selectedItem(TrackerConfig.TrackingList list, String id) {
        return list.getRawItems().stream().filter(item -> item.itemId.equals(id)).findFirst().orElse(null);
    }
    private String quantity(TrackerConfig.TrackedItem item) {
        return format(item, Math.min(item.cachedCount, item.targetCount)) + " / " + format(item, item.targetCount);
    }
    private String format(TrackerConfig.TrackedItem item, int count) {
        if (!item.isValid()) return Integer.toString(count);
        return CountFormatter.format(count, item.getStack().getMaxStackSize(), item.getStack().getItem().canFitInsideContainerItems(),
                CountFormatter.Unit.valueOf(list.countUnit.name()), new CountFormatter.Labels(
                        Component.translatable("gui.resourcetracker.unit.items").getString(),
                        Component.translatable("gui.resourcetracker.unit.stacks").getString(),
                        Component.translatable("gui.resourcetracker.unit.shulkers").getString()));
    }
    @Override public void tick() { super.tick(); if (displayedItems != list.getRawItems()) { view.page = page; init(); } }

    private static final class DetailScreen extends PagedDataScreen {
        private final TrackerConfig.TrackingList list;
        private final String id;
        private List<TrackerConfig.TrackedItem> displayed;
        private DetailScreen(RawMaterialsScreen parent, TrackerConfig.TrackingList list, String id) {
            super(parent, Component.literal(new TrackerConfig.TrackedItem(id, 1).getDisplayName())); this.list = list; this.id = id;
        }
        @Override protected TrackerConfig.TrackingList undoList() { return list; }
        @Override protected void init() {
            layout(38);
            displayed = list.getRawItems();
            TrackerConfig.TrackedItem item = selectedItem(list, id);
            if (item == null) label(Component.translatable("gui.resourcetracker.raw.absent"), left, 40, contentWidth);
            else {
                RawMaterialsScreen owner = (RawMaterialsScreen) parent;
                label(Component.literal(owner.quantity(item)), left, 38, contentWidth);
                ((TrackerButton) button(Component.translatable("gui.resourcetracker.raw.manual", state(item.isManuallyCollected())), left, 62, contentWidth,
                        b -> { TrackerHistory.record(list); item.toggleCollected(); changed(); init(); }))
                        .setLeadingIcon(item.isManuallyCollected() ? PngIcons.Icon.CHECK : null)
                        .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.raw.manual_hint")));
                button(Component.translatable("gui.resourcetracker.raw.pinned", state(item.pinned)), left, 86, contentWidth,
                        b -> { TrackerHistory.record(list); item.pinned = !item.pinned; changed(); init(); });
                button(Component.translatable("gui.resourcetracker.raw.recipe_details"), left, 110, contentWidth,
                        b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new RecipeOptionsScreen(this, list, id)); });
            }
            int half = (contentWidth - 6) / 2;
            button(Component.translatable("gui.resourcetracker.undo"), left, height - 26, half,
                    b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
            button(Component.translatable("gui.resourcetracker.raw.back"), left + half + 6, height - 26, half, b -> onClose());
        }
        @Override public void tick() { super.tick(); if (displayed != list.getRawItems()) init(); }
    }
}
