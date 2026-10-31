package net.fabricmc.resourcetracker.client.gui;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.util.PngIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** Searchable single-choice page, including a nullable "automatic" choice. */
public class ChoiceScreen<T> extends PagedDataScreen {
    private final List<T> values;
    private final T selected;
    private final Function<T, Component> displayName;
    private final Consumer<T> onSelect;
    private String query = "";
    private EditBox search;

    public ChoiceScreen(Screen parent, Component title, List<T> values, T selected,
                        Function<T, Component> displayName, Consumer<T> onSelect) {
        super(parent, title);
        this.values = new ArrayList<>(values);
        this.selected = selected;
        this.displayName = displayName;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        boolean focusSearch = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursorPosition();
        layout(60);
        search = new EditBox(font, left, 32, contentWidth, 20,
                Component.translatable("gui.resourcetracker.edit.search"));
        search.setMaxLength(120);
        search.setHint(Component.translatable("gui.resourcetracker.edit.search"));
        search.setValue(query);
        search.setCursorPosition(Math.min(cursor, query.length()));
        search.setResponder(value -> {
            query = value;
            page = 0;
            if (minecraft != null) minecraft.execute(this::init);
        });
        addRenderableWidget(search);
        if (focusSearch) setFocused(search);
        String filter = query.trim().toLowerCase(Locale.ROOT);
        List<T> filtered = values.stream()
                .filter(value -> displayName.apply(value).getString().toLowerCase(Locale.ROOT).contains(filter)).toList();
        boolean multiline = filtered.stream().anyMatch(value -> font.width(displayName.apply(value)) > contentWidth - 28);
        int rowHeight = multiline ? 40 : 24;
        pageSize = Math.max(1, (height - rowTop - 62) / rowHeight);
        pagination(filtered.size());
        for (int i = page * pageSize; i < Math.min(filtered.size(), (page + 1) * pageSize); i++) {
            T value = filtered.get(i);
            Component name = displayName.apply(value);
            String valueText = name.getString();
            if (multiline && font.width(valueText) > contentWidth - 28) {
                String first = font.plainSubstrByWidth(valueText, contentWidth - 28);
                int space = first.lastIndexOf(' ');
                if (space > 0) first = first.substring(0, space);
                if (!first.isEmpty()) valueText = first + "\n" + valueText.substring(first.length()).stripLeading();
            }
            boolean current = Objects.equals(selected, value);
            Component description = current ? Component.translatable("gui.resourcetracker.item_picker.current", name) : name;
            Component message = Component.literal(valueText);
            var choice = TrackerButton.builder(message, b -> {
                onSelect.accept(value);
                if (minecraft != null && VersionCompat.currentScreen(minecraft) == this) onClose();
            }).bounds(left, rowTop + (i % pageSize) * rowHeight, contentWidth, rowHeight - 4)
                    .leadingIcon(current ? PngIcons.Icon.CHECK : null)
                    .createNarration(s -> description.copy()).build();
            choice.setTooltip(Tooltip.create(description));
            addRenderableWidget(choice);
        }
        if (filtered.isEmpty()) label(Component.translatable("gui.resourcetracker.edit.no_results"), left, rowTop + 4, contentWidth);
        button(Component.translatable("gui.back"), left, height - 26, contentWidth, b -> onClose());
    }
}
