package net.fabricmc.resourcetracker.config;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Session-only undo of resource intent. All callers run on the client thread. */
public final class TrackerHistory {
    private static final int LIMIT = 30;
    private static final Map<TrackerConfig.TrackingList, History> HISTORIES = new IdentityHashMap<>();
    private static final Map<TrackerConfig.TrackingList, Draft> DRAFTS = new IdentityHashMap<>();
    private static final ArrayDeque<Deletion> DELETIONS = new ArrayDeque<>();
    private static String contextKey;

    private TrackerHistory() { }

    /** Opt an unsaved draft into session history without adding it to the configuration. */
    public static void registerDraft(TrackerConfig.TrackingList list, IntSupplier multiplierGetter,
                                     IntConsumer multiplierRestorer) {
        checkContext();
        Objects.requireNonNull(list);
        Objects.requireNonNull(multiplierGetter);
        Objects.requireNonNull(multiplierRestorer);
        if (!TrackerConfig.INSTANCE.lists.contains(list)) {
            DRAFTS.putIfAbsent(list, new Draft(multiplierGetter, multiplierRestorer));
        }
    }

    /** End a draft session and release its history and callbacks. Does not save. */
    public static void discardDraft(TrackerConfig.TrackingList list) {
        checkContext();
        if (DRAFTS.remove(list) != null) HISTORIES.remove(list);
    }

    /** Capture immediately before a discrete resource change. Does not save. */
    public static void record(TrackerConfig.TrackingList list) {
        if (!current(list)) return;
        History history = HISTORIES.computeIfAbsent(list, ignored -> new History());
        history.countItem = null;
        push(history.snapshots, capture(list));
    }

    /** One step per continuous edit of a row, until focus changes or another action occurs. */
    public static void recordCountChange(TrackerConfig.TrackingList list, TrackerConfig.TrackedItem item) {
        if (!current(list)) return;
        History history = HISTORIES.computeIfAbsent(list, ignored -> new History());
        if (history.countItem == item) return;
        push(history.snapshots, capture(list));
        history.countItem = item;
    }

    public static void endCountChange(TrackerConfig.TrackingList list) {
        checkContext();
        History history = HISTORIES.get(list);
        if (history != null) history.countItem = null;
    }

    public static boolean canUndo(TrackerConfig.TrackingList list) {
        if (!current(list)) return false;
        History history = HISTORIES.get(list);
        return history != null && !history.snapshots.isEmpty();
    }

    /** Restores source intent and invalidates derived state; the editor remains the save point. */
    public static boolean undo(TrackerConfig.TrackingList list) {
        if (!canUndo(list)) return false;
        History history = HISTORIES.get(list);
        history.countItem = null;
        Entry entry = history.snapshots.removeLast();
        entry.snapshot().restore(list);
        Draft draft = DRAFTS.get(list);
        if (draft != null && entry.multiplier() != null) {
            // The callback restores editor state only; rows already have their captured quantities.
            draft.multiplierRestorer().accept(entry.multiplier());
        }
        ResourceTrackerClient.invalidateTargetItemCache();
        return true;
    }

    /** Call before deleting a list; deletion undo has its own overview-screen stack. */
    public static void recordDeletion(TrackerConfig.TrackingList list, int index) {
        if (!current(list) || !TrackerConfig.INSTANCE.lists.contains(list)
                || TrackerConfig.isGlobalWriteBlocked()) return;
        list.captureRawState();
        push(DELETIONS, new Deletion(list, Math.max(0, index)));
        endCountChange(list);
    }

    public static boolean canUndoDeletion() {
        checkContext();
        return !TrackerConfig.isGlobalWriteBlocked() && !DELETIONS.isEmpty();
    }

    /** Restores the original object/id/index and uses saveList's filename ownership checks. */
    public static boolean undoDeletion() {
        if (!canUndoDeletion()) return false;
        Deletion deletion = DELETIONS.removeLast();
        TrackerConfig.TrackingList list = deletion.list();
        if (TrackerConfig.INSTANCE.lists.stream().anyMatch(existing -> Objects.equals(existing.id, list.id))) return false;
        TrackerConfig.INSTANCE.lists.add(Math.min(deletion.index(), TrackerConfig.INSTANCE.lists.size()), list);
        TrackerConfig.saveList(list);
        ResourceTrackerClient.invalidateTargetItemCache();
        return true;
    }

    /** Also call on config reload: a reload can replace objects without changing context. */
    public static void clear() {
        HISTORIES.clear();
        DRAFTS.clear();
        DELETIONS.clear();
        contextKey = TrackerConfig.getActiveContextKey();
    }

    private static void checkContext() {
        if (!Objects.equals(contextKey, TrackerConfig.getActiveContextKey())) clear();
    }

    private static boolean current(TrackerConfig.TrackingList list) {
        checkContext();
        return list != null && (TrackerConfig.INSTANCE.lists.contains(list) || DRAFTS.containsKey(list));
    }

    private static Entry capture(TrackerConfig.TrackingList list) {
        Draft draft = DRAFTS.get(list);
        return new Entry(Snapshot.capture(list), draft == null ? null : draft.multiplierGetter().getAsInt());
    }

    private static <T> void push(ArrayDeque<T> stack, T value) {
        if (stack.size() == LIMIT) stack.removeFirst();
        stack.addLast(value);
    }

    private static final class History {
        final ArrayDeque<Entry> snapshots = new ArrayDeque<>();
        TrackerConfig.TrackedItem countItem;
    }

    private record Draft(IntSupplier multiplierGetter, IntConsumer multiplierRestorer) { }
    private record Entry(Snapshot snapshot, Integer multiplier) { }
    private record Deletion(TrackerConfig.TrackingList list, int index) { }

    private record ItemState(String id, int target, int collected, boolean pinned) {
        static ItemState capture(TrackerConfig.TrackedItem item) {
            return new ItemState(item.itemId, item.targetCount, item.manuallyCollectedTarget, item.pinned);
        }

        TrackerConfig.TrackedItem restore() {
            TrackerConfig.TrackedItem item = new TrackerConfig.TrackedItem(id, target);
            item.manuallyCollectedTarget = collected;
            item.pinned = pinned;
            return item;
        }
    }

    private record RawIntent(int collected, boolean pinned) { }

    // Appearance/name are deliberately outside resource history. They remain independently editable.
    record Snapshot(List<ItemState> items, Map<String, String> recipes,
                    Map<String, String> ingredients, Set<String> terminals,
                    Map<String, RawIntent> raw) {
        static Snapshot capture(TrackerConfig.TrackingList list) {
            list.captureRawState();
            Map<String, RawIntent> raw = new LinkedHashMap<>();
            list.rawState.forEach((id, state) -> raw.put(id, new RawIntent(state.collectedTarget, state.pinned)));
            return new Snapshot(list.items.stream().map(ItemState::capture).toList(),
                    new LinkedHashMap<>(list.recipeChoices), new LinkedHashMap<>(list.ingredientChoices),
                    new LinkedHashSet<>(list.terminalItems), raw);
        }

        void restore(TrackerConfig.TrackingList list) {
            list.items = new ArrayList<>(items.stream().map(ItemState::restore).toList());
            list.recipeChoices = new LinkedHashMap<>(recipes);
            list.ingredientChoices = new LinkedHashMap<>(ingredients);
            list.terminalItems = new LinkedHashSet<>(terminals);
            list.rawState = new LinkedHashMap<>();
            raw.forEach((id, state) -> list.rawState.put(id, new TrackerConfig.RawState(state.collected(), state.pinned())));
            // Otherwise the next crafting update would capture stale derived marks over restored intent.
            list.rawItems = new ArrayList<>();
            list.rawDiagnostics = new ArrayList<>();
            list.rawPlanValid = false;
        }
    }
}
