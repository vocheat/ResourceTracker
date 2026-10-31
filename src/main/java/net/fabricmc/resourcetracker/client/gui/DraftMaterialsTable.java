package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Shared import/area table; the owner retains its own acceptance and mutation contract. */
final class DraftMaterialsTable {
    enum Capability { READ_ONLY, SELECT_RESOURCE }

    private DraftMaterialsTable() {}

    static EditBox search(PagedDataScreen owner, ResourceViewState state, int x, int y, int width, Runnable refresh) {
        EditBox search = new EditBox(owner.textFont(), x, y, width, 20,
                Component.translatable("gui.resourcetracker.edit.search_tracked"));
        search.setHint(Component.translatable("gui.resourcetracker.edit.search_tracked"));
        search.setMaxLength(120);
        search.setValue(state.query);
        search.setResponder(value -> {
            state.query = value;
            owner.page = state.page = 0;
            refresh.run();
        });
        owner.input(search);
        return search;
    }

    static void rows(PagedDataScreen owner, List<TrackerConfig.TrackedItem> source, ResourceViewState state,
                     int width, Capability capability, Function<TrackerConfig.TrackedItem, Component> text,
                     Consumer<TrackerConfig.TrackedItem> select) {
        String query = state.query.toLowerCase(Locale.ROOT).trim();
        List<TrackerConfig.TrackedItem> rows = source.stream().filter(item -> item.itemId.toLowerCase(Locale.ROOT).contains(query)
                || item.getDisplayName().toLowerCase(Locale.ROOT).contains(query)).toList();
        owner.pagination(rows.size());
        state.page = owner.page;
        for (int i = owner.page * owner.pageSize; i < Math.min(rows.size(), (owner.page + 1) * owner.pageSize); i++) {
            TrackerConfig.TrackedItem item = rows.get(i);
            Component caption = text.apply(item);
            int y = owner.rowTop + (i % owner.pageSize) * 24;
            if (capability == Capability.READ_ONLY) owner.label(caption, owner.left, y + 4, width);
            else owner.button(Component.literal(item.itemId.equals(state.selectedItemId) ? "▶ " : "").append(caption),
                    owner.left, y, width, b -> select.accept(item)).setTooltip(Tooltip.create(caption));
        }
        if (rows.isEmpty()) owner.label(Component.translatable("gui.resourcetracker.edit.no_results"), owner.left, owner.rowTop + 4, width);
    }
}
