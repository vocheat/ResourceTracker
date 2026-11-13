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
    private final ResourceViewState view = new ResourceViewState();
    private boolean detail;
    private boolean focusSearch;
    private EditBox searchField;
    private EditBox nameField;
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
        boolean naming = nameField != null && nameField.isFocused();
        int nameCursor = nameField == null ? draft.name.length() : nameField.getCursorPosition();
        boolean searching = searchField != null && searchField.isFocused();
        int cursor = searchField == null ? view.query.length() : searchField.getCursorPosition();
        layout(108, Component.translatable("gui.resourcetracker.draft.title"));
        showPanel();
        undoShortcutDown = minecraft != null && VersionCompat.isUndoPressed(minecraft);
        multiplierField = null;
        applyMultiplier = null;
        EditBox name = new EditBox(font, left, 30, contentWidth, 20,
                Component.translatable("gui.resourcetracker.edit.list_name"));
        name.setMaxLength(120);
        name.setValue(draft.name);
        name.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.edit.list_name")));
        name.setResponder(value -> { draft.name = value; updateControls(); });
        addRenderableWidget(name);
        nameField = name;
        name.setCursorPosition(Math.min(nameCursor, draft.name.length()));
        help = label(Component.empty(), left, 58, contentWidth - 76);
        helpDetails = null;
        button(Component.translatable("gui.resourcetracker.draft.details"), left + contentWidth - 70, 54, 70,
                b -> showNotes());
        TrackerConfig.TrackedItem selected = draft.items.stream()
                .filter(item -> item.itemId.equals(view.selectedItemId)).findFirst().orElse(null);
        boolean wide = contentWidth >= 540;
        if (detail && !wide && selected != null) {
            button(Component.translatable("gui.resourcetracker.compact.back_resources"), left, 80, contentWidth,
                    b -> { detail = false; init(); });
            selectedPanel(selected, left, rowTop, contentWidth);
        } else {
            detail = false;
            Component actions = Component.translatable("gui.resourcetracker.compact.list_actions");
            int actionWidth = font.width(actions) + 12;
            boolean separateActions = contentWidth - actionWidth - 6 < 92;
            if (separateActions) {
                rowTop += 24;
                pageSize = Math.max(1, (height - rowTop - 62) / 24);
            }
            searchField = DraftMaterialsTable.search(this, view, left, 80,
                    separateActions ? contentWidth : contentWidth - actionWidth - 6, () -> {
                focusSearch = true;
                if (minecraft != null) minecraft.execute(this::init);
            });
            searchField.setCursorPosition(Math.min(cursor, view.query.length()));
            if (searching || focusSearch) setFocused(searchField);
            button(actions, separateActions ? left : left + contentWidth - actionWidth,
                    separateActions ? 104 : 80, separateActions ? contentWidth : actionWidth, b -> showActions());
            int tableWidth = wide ? contentWidth - 192 : contentWidth;
            DraftMaterialsTable.rows(this, draft.items, view, tableWidth,
                    DraftMaterialsTable.Capability.SELECT_RESOURCE,
                    item -> Component.literal(item.getDisplayName() + " × " + scaledCount(item)), item -> {
                        view.selectedItemId = item.itemId;
                        detail = !wide;
                        focusSearch = false;
                        init();
                    });
            if (wide && selected != null) selectedPanel(selected, left + tableWidth + 8, rowTop, 184);
        }
        undoButton = button(Component.translatable("gui.resourcetracker.undo"), left + 138, height - 52,
                contentWidth - 138, b -> undo());
        undoButton.setTooltip(Tooltip.create(Component.translatable("gui.resourcetracker.undo_hint")));
        int half = (contentWidth - 6) / 2;
        accept = button(Component.translatable("gui.resourcetracker.area.create"), left, height - 26, half, b -> create());
        button(Component.translatable("gui.cancel"), left + half + 6, height - 26, contentWidth - half - 6, b -> onClose());
        updateControls();
        if (naming) setFocused(nameField);
    }

    private void selectedPanel(TrackerConfig.TrackedItem selected, int x, int y, int w) {
        label(Component.literal(selected.getDisplayName()), x, y, w);
        label(Component.literal("× " + scaledCount(selected)), x, y + 14, w);
        int next = y + 24;
        button(Component.translatable("gui.resourcetracker.compact.replace"), x, next + 8, w, b -> {
            if (validSession()) VersionCompat.setScreen(minecraft, new ItemPickerScreen(this, draft, selected,
                    () -> { view.selectedItemId = null; detail = false; }, multiplier));
        });
        button(Component.translatable("gui.resourcetracker.compact.remove_resource"), x, next + 34, w, b -> {
            if (!validSession()) return;
            TrackerHistory.record(draft);
            draft.items.remove(selected);
            view.selectedItemId = null;
            detail = false;
            init();
        });
    }

    private void showActions() {
        if (!validSession()) return;
        VersionCompat.setScreen(minecraft, new ChoiceScreen<>(this,
                Component.translatable("gui.resourcetracker.compact.list_actions"), List.of("copies", "family"), null,
                value -> Component.translatable(value.equals("copies") ? "gui.resourcetracker.area.multiplier" : "gui.resourcetracker.family.button"),
                value -> {
                    if (!validSession()) return;
                    VersionCompat.setScreen(minecraft, value.equals("copies") ? new MultiplierScreen()
                            : new FamilyReplacementScreen(this, draft, multiplier));
                }));
    }

    private void showNotes() {
        List<Component> notes = new java.util.ArrayList<>();
        notes.add(Component.translatable("gui.resourcetracker.area.summary_copies", scannedBlocks, draft.items.size(), multiplier));
        notes.add(Component.translatable("gui.resourcetracker.area.edit_hint"));
        if (skippedBlocks > 0) notes.add(Component.translatable("gui.resourcetracker.area.skipped", skippedBlocks));
        unsupported.forEach((id, count) -> notes.add(Component.literal(id + " × " + count)));
        if (validSession()) VersionCompat.setScreen(minecraft, new NotesScreen(this,
                Component.translatable("gui.resourcetracker.draft.details"), notes));
    }

    private final class MultiplierScreen extends PagedDataScreen {
        MultiplierScreen() { super(AreaListScreen.this, Component.translatable("gui.resourcetracker.area.multiplier")); }
        @Override protected void init() {
            layout(120);
            wrappedLabel(Component.translatable("gui.resourcetracker.area.multiplier_hint"), left, 32, contentWidth, UiTheme.MUTED);
            multiplierField = new EditBox(font, left, 74, contentWidth, 20, title);
            multiplierField.setMaxLength(10);
            multiplierField.setValue(multiplierText);
            addRenderableWidget(multiplierField);
            EditBox status = label(Component.empty(), left, 104, contentWidth);
            applyMultiplier = button(Component.translatable("gui.resourcetracker.area.apply_multiplier"), left, height - 52,
                    contentWidth, b -> { AreaListScreen.this.applyMultiplier(); onClose(); });
            Runnable update = () -> {
                Integer requested = requestedMultiplier();
                applyMultiplier.active = validSession() && requested != null && fits(requested) && requested != multiplier;
                Component message = requested == null ? Component.translatable("gui.resourcetracker.area.invalid_multiplier")
                        : !fits(requested) ? Component.translatable("gui.resourcetracker.edit.replace_overflow", TrackerConfig.MAX_TARGET_COUNT)
                        : Component.translatable("gui.resourcetracker.draft.multiplier_status", multiplier, multiplierText);
                status.setValue(message.getString());
                status.setCursorPosition(0);
                status.setHighlightPos(0);
                status.setTooltip(Tooltip.create(message));
                status.setTextColorUneditable(requested == null || !fits(requested) ? UiTheme.DANGER : UiTheme.TEXT);
            };
            multiplierField.setResponder(value -> { multiplierText = value; update.run(); });
            button(Component.translatable("gui.back"), left, height - 26, contentWidth, b -> onClose());
            update.run();
            setFocused(multiplierField);
        }
        @Override public void tick() {
            if (!validSession()) VersionCompat.setScreen(minecraft, null);
            else super.tick();
        }
        @Override protected boolean editorKeyPressed(int keyCode) {
            if ((keyCode == 257 || keyCode == 335) && getFocused() == multiplierField) {
                if (applyMultiplier.active) { AreaListScreen.this.applyMultiplier(); onClose(); }
                return true;
            }
            return false;
        }
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
            hint = Component.translatable("gui.resourcetracker.draft.multiplier_status", multiplier, requested);
        } else if (skippedBlocks > 0) {
            hint = Component.translatable("gui.resourcetracker.area.skipped", skippedBlocks);
        } else {
            hint = Component.translatable("gui.resourcetracker.area.summary_copies", scannedBlocks, draft.items.size(), multiplier);
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
    protected boolean editorKeyPressed(int keyCode) {
        if ((keyCode == 257 || keyCode == 335) && getFocused() == nameField) {
            create();
            return true;
        }
        return false;
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
