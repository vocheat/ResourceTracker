package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.fabricmc.resourcetracker.selection.AreaScan;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** An unsaved area capture: replacing/removing materials only changes this draft. */
public final class AreaListScreen extends PagedDataScreen {
    private final TrackerConfig.TrackingList draft;
    private final ClientLevel level;
    private final String contextKey;
    private final long scannedBlocks;
    private final long skippedBlocks;
    private final Map<String, Integer> unsupported;
    private String query = "";
    private boolean focusSearch;
    private EditBox searchField;
    private EditBox multiplierField;
    private EditBox help;
    private String helpDetails;
    private Button applyMultiplier;
    private Button undoButton;
    private Button accept;
    private int multiplier = 1;
    private String multiplierText = "1";
    private boolean undoShortcutDown;

    public AreaListScreen(AreaScan scan, ClientLevel level, String contextKey) {
        super(null, Component.translatable("gui.resourcetracker.area.title"));
        this.level = level;
        this.contextKey = contextKey;
        this.scannedBlocks = scan.totalBlocks();
        this.skippedBlocks = scan.skippedBlocks();
        this.unsupported = scan.unsupportedBlockIds();
        draft = TrackerConfig.createList(Component.translatable("gui.resourcetracker.area.default_name").getString());
        scan.materials().forEach((id, count) -> draft.items.add(new TrackerConfig.TrackedItem(id, count)));
        draft.items.sort(Comparator.comparing(TrackerConfig.TrackedItem::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        TrackerHistory.registerDraft(draft, () -> multiplier, value -> {
            multiplier = value;
            multiplierText = Integer.toString(value);
        });
    }

    @Override
    protected void init() {
        int cursor = searchField == null ? query.length() : searchField.getCursorPosition();
        layout(164);
        undoShortcutDown = minecraft != null && VersionCompat.isUndoPressed(minecraft);
        label(Component.translatable("gui.resourcetracker.edit.list_name"), left, 30, contentWidth);
        EditBox name = new EditBox(font, left, 48, contentWidth, 20,
                Component.translatable("gui.resourcetracker.edit.list_name"));
        name.setMaxLength(120);
        name.setValue(draft.name);
        name.setResponder(value -> { draft.name = value; updateControls(); });
        addRenderableWidget(name);
        label(Component.translatable("gui.resourcetracker.area.multiplier"), left, 79, 76);
        multiplierField = new EditBox(font, left + 80, 75, 54, 20,
                Component.translatable("gui.resourcetracker.area.multiplier"));
        multiplierField.setMaxLength(10);
        multiplierField.setValue(multiplierText);
        multiplierField.setResponder(value -> { multiplierText = value; updateControls(); });
        multiplierField.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.area.multiplier_hint")));
        addRenderableWidget(multiplierField);
        applyMultiplier = button(Component.translatable("gui.resourcetracker.area.apply_multiplier"),
                left + 140, 75, Math.max(1, contentWidth - 140), b -> applyMultiplier());
        EditBox search = new EditBox(font, left, 102, contentWidth, 20,
                Component.translatable("gui.resourcetracker.edit.search"));
        searchField = search;
        search.setHint(Component.translatable("gui.resourcetracker.edit.search_tracked"));
        search.setMaxLength(120);
        search.setValue(query);
        search.setCursorPosition(Math.min(cursor, query.length()));
        search.setResponder(value -> {
            query = value;
            page = 0;
            focusSearch = true;
            if (minecraft != null) minecraft.execute(this::init);
        });
        addRenderableWidget(search);
        label(Component.translatable("gui.resourcetracker.area.summary_copies", scannedBlocks,
                draft.items.size(), multiplier), left, 129, contentWidth);
        help = label(Component.empty(), left, 146, contentWidth);
        helpDetails = null;
        List<TrackerConfig.TrackedItem> rows = draft.items.stream().filter(this::matches).toList();
        pagination(rows.size());
        for (int index = page * pageSize; index < Math.min(rows.size(), (page + 1) * pageSize); index++) {
            TrackerConfig.TrackedItem item = rows.get(index);
            int y = rowTop + (index % pageSize) * 24;
            Button replace = button(Component.literal(item.getDisplayName() + " × " + scaledCount(item)), left, y, contentWidth - 30,
                    b -> {
                        if (validSession()) VersionCompat.setScreen(minecraft,
                                new ItemPickerScreen(this, draft, item, () -> { }, multiplier));
                    });
            replace.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.replace_item", item.getDisplayName())));
            Button remove = button(Component.literal("×"), left + contentWidth - 26, y, 26,
                    b -> {
                        if (!validSession()) return;
                        TrackerHistory.record(draft);
                        draft.items.remove(item);
                        init();
                    });
            remove.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.remove_item", item.getDisplayName())));
        }
        if (rows.isEmpty()) label(Component.translatable("gui.resourcetracker.edit.no_results"), left, rowTop + 4, contentWidth);
        int actionsWidth = Math.max(1, contentWidth - 138);
        int undoWidth = Math.max(1, Math.min(90, (actionsWidth - 6) / 3));
        undoButton = button(Component.translatable("gui.resourcetracker.undo"), left + 138, height - 52,
                undoWidth, b -> undo());
        undoButton.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.undo_hint")));
        button(Component.translatable("gui.resourcetracker.family.button"), left + 138 + undoWidth + 6,
                height - 52, actionsWidth - undoWidth - 6, b -> {
                    if (validSession()) VersionCompat.setScreen(minecraft,
                            new FamilyReplacementScreen(this, draft, multiplier));
                });
        int half = (contentWidth - 6) / 2;
        accept = button(Component.translatable("gui.resourcetracker.area.create"), left, height - 26, half, b -> create());
        button(Component.translatable("gui.cancel"), left + half + 6, height - 26, contentWidth - half - 6, b -> onClose());
        updateControls();
        if (focusSearch) setFocused(search);
    }

    private boolean matches(TrackerConfig.TrackedItem item) {
        String filter = query.toLowerCase(Locale.ROOT).trim();
        return item.itemId.toLowerCase(Locale.ROOT).contains(filter)
                || item.getDisplayName().toLowerCase(Locale.ROOT).contains(filter);
    }

    private boolean validSession() {
        return minecraft != null && minecraft.player != null && minecraft.player.isAlive() && minecraft.level == level
                && TrackerConfig.hasActiveContext() && Objects.equals(contextKey, TrackerConfig.getActiveContextKey());
    }

    private long scaledCount(TrackerConfig.TrackedItem item) {
        return (long) item.targetCount * multiplier;
    }

    private Integer requestedMultiplier() {
        try {
            int value = Integer.parseInt(multiplierText);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean fits(int value) {
        return draft.items.stream().allMatch(item -> item.targetCount > 0
                && item.targetCount <= TrackerConfig.MAX_TARGET_COUNT / value);
    }

    private void updateControls() {
        Integer requested = requestedMultiplier();
        boolean valid = requested != null && fits(requested);
        boolean pending = requested != null && requested != multiplier;
        if (applyMultiplier != null) applyMultiplier.active = validSession() && valid && pending;
        if (undoButton != null) undoButton.active = TrackerHistory.canUndo(draft);
        if (accept != null) accept.active = validSession() && !TrackerConfig.isGlobalWriteBlocked()
                && !draft.name.isBlank() && !draft.items.isEmpty() && valid && !pending;
        if (help == null) return;
        Component hint;
        if (requested == null) {
            hint = Component.translatable("gui.resourcetracker.area.invalid_multiplier");
        } else if (!valid) {
            hint = Component.translatable("gui.resourcetracker.edit.replace_overflow", TrackerConfig.MAX_TARGET_COUNT);
        } else if (pending) {
            hint = Component.translatable("gui.resourcetracker.area.multiplier_pending", requested);
        } else if (skippedBlocks > 0) {
            hint = Component.translatable("gui.resourcetracker.area.skipped", skippedBlocks);
        } else {
            hint = Component.translatable("gui.resourcetracker.area.edit_hint");
        }
        StringBuilder details = new StringBuilder(hint.getString());
        unsupported.forEach((id, count) -> details.append('\n').append(id).append(" × ").append(count));
        String text = details.toString();
        if (!text.equals(helpDetails)) {
            helpDetails = text;
            help.setValue(hint.getString());
            help.setCursorPosition(0);
            help.setHighlightPos(0);
            help.setTextColorUneditable(valid ? UiTheme.TEXT : 0xFFFF7777);
            help.setTooltip(Tooltip.create(Component.literal(text)));
        }
    }

    private void applyMultiplier() {
        Integer requested = requestedMultiplier();
        if (!validSession() || requested == null || requested == multiplier || !fits(requested)) return;
        TrackerHistory.record(draft);
        multiplier = requested;
        multiplierText = Integer.toString(multiplier);
        init();
    }

    private void undo() {
        if (validSession() && TrackerHistory.undo(draft)) init();
    }

    private void create() {
        updateControls();
        if (!accept.active) return;
        // Prevalidated targets are committed once, after all draft edits and multiplier changes.
        for (TrackerConfig.TrackedItem item : draft.items) item.setTargetCount((int) scaledCount(item));
        draft.name = draft.name.trim();
        TrackerHistory.discardDraft(draft);
        TrackerConfig.INSTANCE.lists.add(draft);
        TrackerConfig.saveList(draft);
        ResourceTrackerClient.invalidateTargetItemCache();
        onClose();
    }

    @Override
    public void tick() {
        if (!validSession()) {
            onClose();
            return;
        }
        boolean down = VersionCompat.isUndoPressed(minecraft);
        if (down && !undoShortcutDown) undo();
        undoShortcutDown = down;
        updateControls();
        super.tick();
    }

    @Override
    public void onClose() {
        TrackerHistory.discardDraft(draft);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
