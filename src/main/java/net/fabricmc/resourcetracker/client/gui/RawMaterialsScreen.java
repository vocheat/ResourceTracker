package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.model.CountFormatter;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** The derived view belongs to the original editor session and never saves independently. */
public final class RawMaterialsScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private String query = "";
    private EditBox searchField;
    private List<TrackerConfig.TrackedItem> displayedItems;

    public RawMaterialsScreen(EditScreen parent, TrackerConfig.TrackingList list) {
        super(parent, Component.translatable("gui.resourcetracker.edit.raw_title", list.name));
        this.list = list;
    }

    @Override
    protected void init() {
        boolean searching = searchField != null && searchField.isFocused();
        layout(110);
        int tabWidth = (contentWidth - 6) / 2;
        button(Component.translatable("gui.resourcetracker.edit.items"), left, 30, tabWidth, b -> onClose());
        button(Component.translatable("gui.resourcetracker.edit.raw"), left + tabWidth + 6, 30, tabWidth, b -> {}).active = false;
        label(Component.translatable("gui.resourcetracker.edit.raw_note"), left, 58, contentWidth);
        EditBox search = new EditBox(font, left, 80, contentWidth, 18, Component.translatable("gui.resourcetracker.edit.search_tracked"));
        searchField = search;
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            if (minecraft != null) minecraft.execute(() -> { init(); focusSearch(); });
        });
        addRenderableWidget(search);
        if (searching) setFocused(search);
        String filter = query.toLowerCase(Locale.ROOT).trim();
        displayedItems = list.getRawItems();
        List<TrackerConfig.TrackedItem> items = displayedItems.stream().filter(item ->
                item.itemId.toLowerCase(Locale.ROOT).contains(filter) || item.getDisplayName().toLowerCase(Locale.ROOT).contains(filter)).toList();
        pagination(items.size());
        int actionWidth = Math.min(42, Math.max(20, (contentWidth - 72) / 4));
        for (int i = page * pageSize; i < Math.min(items.size(), (page + 1) * pageSize); i++) {
            TrackerConfig.TrackedItem item = items.get(i);
            int y = rowTop + (i % pageSize) * 24;
            int actionsX = left + contentWidth - actionWidth * 3 - 12;
            String status = item.isManuallyCollected() ? Component.translatable("gui.resourcetracker.edit.collected").getString()
                    : format(item, Math.min(item.cachedCount, item.targetCount)) + " / " + format(item, item.targetCount);
            label(Component.literal(item.getDisplayName() + " — " + status), left, y + 4, actionsX - left - 6);
            button(Component.literal(item.isManuallyCollected() ? "✓" : "○"), actionsX, y, actionWidth, b -> {
                TrackerHistory.record(list);
                item.toggleCollected(); changed(); init();
            }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.resourcetracker.edit.mark_collected")));
            button(Component.literal(item.pinned ? "★" : "☆"), actionsX + actionWidth + 4, y, actionWidth, b -> {
                TrackerHistory.record(list);
                item.pinned = !item.pinned; changed(); init();
            }).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.resourcetracker.edit.pin")));
            button(Component.literal("…"), actionsX + (actionWidth + 4) * 2, y, actionWidth,
                    b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new RecipeOptionsScreen(this, list, item.itemId)); })
                    .setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.resourcetracker.recipe.title")));
        }
        if (!list.rawDiagnostics.isEmpty()) {
            button(Component.translatable("gui.resourcetracker.recipe.diagnostics", list.rawDiagnostics.size()), left + 138, height - 52,
                    contentWidth - 138, b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new RecipeOptionsScreen(this, list, null)); });
        }
        int footerWidth = Math.max(1, (contentWidth - 12) / 3);
        button(Component.translatable("gui.resourcetracker.recipe.options"), left, height - 26, footerWidth,
                b -> { if (minecraft != null) VersionCompat.setScreen(minecraft, new RecipeOptionsScreen(this, list, null)); });
        button(Component.translatable("gui.resourcetracker.undo"), left + footerWidth + 6, height - 26, footerWidth,
                b -> { if (TrackerHistory.undo(list)) init(); }).active = TrackerHistory.canUndo(list);
        button(Component.translatable("gui.resourcetracker.done"), left + contentWidth - footerWidth, height - 26, footerWidth, b -> onClose());
    }

    private void focusSearch() {
        for (var child : children()) if (child instanceof EditBox box && box.active) { setFocused(box); break; }
    }

    private void changed() {
        ResourceTrackerClient.invalidateTargetItemCache();
    }

    private String format(TrackerConfig.TrackedItem item, int count) {
        if (!item.isValid()) return Integer.toString(count);
        return CountFormatter.format(count, item.getStack().getMaxStackSize(), item.getStack().getItem().canFitInsideContainerItems(),
                CountFormatter.Unit.valueOf(list.countUnit.name()), new CountFormatter.Labels(
                        Component.translatable("gui.resourcetracker.unit.items").getString(),
                        Component.translatable("gui.resourcetracker.unit.stacks").getString(),
                        Component.translatable("gui.resourcetracker.unit.shulkers").getString()));
    }

    @Override
    public void tick() {
        super.tick();
        if (displayedItems != list.getRawItems()) init();
    }
}
