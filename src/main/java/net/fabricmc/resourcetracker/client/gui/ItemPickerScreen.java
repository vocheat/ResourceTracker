package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Searchable replacement picker shared by the normal editor and area-list drafts. */
public final class ItemPickerScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final TrackerConfig.TrackedItem source;
    private final Runnable afterReplace;
    private final int displayMultiplier;
    private final List<Choice> choices;
    private final ClientLevel level;
    private final String contextKey;
    private EditBox searchField;
    private String query = "";
    private Component error;

    private record Choice(String id, String name, String search) { }

    public ItemPickerScreen(Screen parent, TrackerConfig.TrackingList list,
                            TrackerConfig.TrackedItem source, Runnable afterReplace) {
        this(parent, list, source, afterReplace, 1);
    }

    public ItemPickerScreen(Screen parent, TrackerConfig.TrackingList list,
                            TrackerConfig.TrackedItem source, Runnable afterReplace, int displayMultiplier) {
        super(parent, Component.translatable("gui.resourcetracker.edit.replace_title",
                source.getDisplayName(), (long) source.targetCount * displayMultiplier));
        this.list = list;
        this.source = source;
        this.afterReplace = afterReplace;
        this.displayMultiplier = displayMultiplier;
        this.level = Minecraft.getInstance().level;
        this.contextKey = TrackerConfig.getActiveContextKey();
        this.choices = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).map(item -> {
            String id = VersionCompat.getItemId(item);
            String name = VersionCompat.getItemName(item);
            return new Choice(id, name, (name + " " + id).toLowerCase(Locale.ROOT));
        }).sorted(Comparator.comparing(Choice::name)).toList();
    }

    @Override
    protected void init() {
        int cursor = searchField == null ? query.length() : searchField.getCursorPosition();
        boolean searching = searchField == null || searchField.isFocused();
        layout(94);
        label(Component.translatable("gui.resourcetracker.edit.replace_hint"), left, 30, contentWidth);
        searchField = new EditBox(font, left, 50, contentWidth, 18,
                Component.translatable("gui.resourcetracker.edit.search"));
        searchField.setValue(query);
        searchField.setCursorPosition(Math.min(cursor, query.length()));
        searchField.setHighlightPos(Math.min(cursor, query.length()));
        searchField.setResponder(value -> {
            query = value;
            page = 0;
            error = null;
            if (minecraft != null) minecraft.execute(this::init);
        });
        searchField.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.search_hint")));
        addRenderableWidget(searchField);
        if (searching) setFocused(searchField);
        if (error != null) {
            EditBox message = label(error, left, 74, contentWidth);
            message.setTextColorUneditable(0xFFFF7777);
        }
        String filter = query.toLowerCase(Locale.ROOT).trim();
        List<Choice> filtered = choices.stream().filter(choice -> choice.search().contains(filter)).toList();
        pagination(filtered.size());
        for (int i = page * pageSize; i < Math.min(filtered.size(), (page + 1) * pageSize); i++) {
            Choice choice = filtered.get(i);
            button(Component.literal(choice.name()), left, rowTop + (i % pageSize) * 24, contentWidth,
                    b -> select(choice)).setTooltip(Tooltip.create(Component.translatable(
                            "gui.resourcetracker.edit.select_item", choice.name()).append("\n").append(choice.id())));
        }
        if (filtered.isEmpty()) label(Component.translatable("gui.resourcetracker.edit.no_results"), left, rowTop + 4, contentWidth);
        button(CommonComponents.GUI_CANCEL, left, height - 26, contentWidth, b -> onClose());
    }

    private void select(Choice choice) {
        if (!ensureSession()) return;
        ItemReplacement.Result result = ItemReplacement.replace(list, source, choice.id(), displayMultiplier);
        if (result == ItemReplacement.Result.OVERFLOW) {
            error = Component.translatable("gui.resourcetracker.edit.replace_overflow", TrackerConfig.MAX_TARGET_COUNT);
            init();
        } else if (result == ItemReplacement.Result.MISSING) {
            error = Component.translatable("gui.resourcetracker.edit.replace_missing");
            init();
        } else {
            afterReplace.run();
            onClose();
        }
    }

    private boolean ensureSession() {
        if (minecraft != null && level != null && minecraft.level == level
                && minecraft.player != null && minecraft.player.isAlive()
                && Objects.equals(contextKey, TrackerConfig.getActiveContextKey())) return true;
        if (minecraft != null) VersionCompat.setScreen(minecraft, null);
        return false;
    }

    @Override
    public void tick() {
        if (ensureSession()) super.tick();
    }

    @Override
    public void onClose() {
        if (ensureSession()) super.onClose();
    }
}
