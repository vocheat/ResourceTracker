package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.util.PngIcons;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.model.WoodType;
import net.fabricmc.resourcetracker.crafting.CraftingRuntime;
import net.fabricmc.resourcetracker.crafting.RecipeIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/** Selects a valid wood family for interchangeable recipe inputs without changing source goals. */
public final class WoodChoiceScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList list;
    private final ClientLevel level;
    private final String contextKey;
    private String query = "";
    private EditBox searchField;
    private RecipeIndex displayedIndex;

    public WoodChoiceScreen(Screen parent, TrackerConfig.TrackingList list) {
        super(parent, Component.translatable("gui.resourcetracker.wood.title"));
        this.list = list;
        this.level = Minecraft.getInstance().level;
        this.contextKey = TrackerConfig.getActiveContextKey();
    }

    public static Component name(WoodType type) {
        return type == null || type == WoodType.AUTO
                ? Component.translatable("gui.resourcetracker.wood.auto")
                : Component.translatable("gui.resourcetracker.wood.species." + type.name().toLowerCase(Locale.ROOT));
    }

    @Override protected TrackerConfig.TrackingList undoList() { return list; }

    @Override protected void init() {
        boolean searching = searchField != null && searchField.isFocused();
        int cursor = searchField == null ? query.length() : searchField.getCursorPosition();
        displayedIndex = CraftingRuntime.index();
        layout(80);
        label(Component.translatable("gui.resourcetracker.wood.note"), left, 32, contentWidth)
                .setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.wood.hint")));
        searchField = new EditBox(font, left, 54, contentWidth, 20, Component.translatable("gui.resourcetracker.raw.wood_search"));
        searchField.setHint(Component.translatable("gui.resourcetracker.raw.wood_search"));
        searchField.setMaxLength(120);
        searchField.setValue(query);
        searchField.setCursorPosition(Math.min(cursor, query.length()));
        searchField.setResponder(value -> {
            query = value;
            page = 0;
            if (minecraft != null) minecraft.execute(() -> { init(); setFocused(searchField); });
        });
        addRenderableWidget(searchField);
        if (searching) setFocused(searchField);
        String filter = query.toLowerCase(Locale.ROOT).trim();
        List<WoodType> choices = Arrays.stream(WoodType.values())
                .filter(type -> type == WoodType.AUTO || (VersionCompat.isValidItemId(type.planksId())
                        && !displayedIndex.recipesFor(type.planksId()).isEmpty()))
                .filter(type -> name(type).getString().toLowerCase(Locale.ROOT).contains(filter)
                        || type.name().toLowerCase(Locale.ROOT).contains(filter)).toList();
        pagination(choices.size());
        for (int i = page * pageSize; i < Math.min(choices.size(), (page + 1) * pageSize); i++) {
            WoodType type = choices.get(i);
            boolean current = type == list.woodType;
            Component description = current ? Component.translatable("gui.resourcetracker.item_picker.current", name(type)) : name(type);
            addRenderableWidget(TrackerButton.builder(name(type), b -> {
                if (!ensureSession()) return;
                if (list.woodType != type) {
                    TrackerHistory.record(list);
                    list.woodType = type;
                    ResourceTrackerClient.invalidateTargetItemCache();
                }
                onClose();
            }).bounds(left, rowTop + (i % pageSize) * 24, contentWidth, 20)
                    .leadingIcon(current ? PngIcons.Icon.CHECK : null)
                    .createNarration(s -> description.copy()).tooltip(Tooltip.create(description)).build());
        }
        button(CommonComponents.GUI_CANCEL, left, height - 26, contentWidth, b -> onClose());
    }

    private boolean ensureSession() {
        if (minecraft != null && level != null && minecraft.level == level
                && minecraft.player != null && minecraft.player.isAlive()
                && Objects.equals(contextKey, TrackerConfig.getActiveContextKey())
                && TrackerConfig.INSTANCE.lists.contains(list)) return true;
        if (minecraft != null) VersionCompat.setScreen(minecraft, null);
        return false;
    }

    @Override public void tick() {
        if (!ensureSession()) return;
        super.tick();
        if (displayedIndex != CraftingRuntime.index()) init();
    }
    @Override public void onClose() { if (ensureSession()) super.onClose(); }
}
