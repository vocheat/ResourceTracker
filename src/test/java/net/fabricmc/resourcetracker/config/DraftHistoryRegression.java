package net.fabricmc.resourcetracker.config;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.resourcetracker.client.gui.ItemReplacement;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;

/** Exercises unsaved draft history through the same entry points as the area editor. */
public final class DraftHistoryRegression {
    private DraftHistoryRegression() { }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var loader = FabricLoader.getInstance();
        var directory = loader.getClass().getDeclaredField("configDir");
        directory.setAccessible(true);
        if (directory.get(loader) == null) {
            Path scratch = Path.of("Workflow", "draft-history-regression-config").toAbsolutePath();
            Files.createDirectories(scratch);
            directory.set(loader, scratch);
        }
        TrackerHistory.clear();
        try {
            removalAndReplacement();
            multipleStepsAndMultiplier();
            storedListIsolation();
            discardAndClear();
            contextChange();
        } finally {
            TrackerHistory.clear();
        }
        System.out.println("Draft history regression checks passed");
    }

    private static void removalAndReplacement() {
        TrackerConfig.TrackingList draft = draft();
        int[] multiplier = {1};
        TrackerHistory.registerDraft(draft, () -> multiplier[0], value -> multiplier[0] = value);
        TrackerConfig.TrackedItem source = draft.items.getFirst();
        source.pinned = true;
        source.manuallyCollectedTarget = source.targetCount;
        TrackerHistory.record(draft);
        draft.items.remove(source);
        check(TrackerHistory.canUndo(draft) && TrackerHistory.undo(draft), "draft removal can be undone");
        check(draft.items.size() == 2 && draft.items.getFirst().itemId.equals(source.itemId)
                && draft.items.getFirst().pinned && draft.items.getFirst().isManuallyCollected(),
                "removal restores original row position and intent");
        check(!TrackerHistory.canUndo(draft), "one deletion is one undo step");
        check(ItemReplacement.replace(draft, draft.items.getFirst(), "minecraft:diamond")
                == ItemReplacement.Result.REPLACED, "actual replacement accepts registered draft");
        check(TrackerHistory.undo(draft) && draft.items.getFirst().itemId.equals(source.itemId)
                && !TrackerHistory.canUndo(draft), "replacement restores in one undo step");
        TrackerHistory.recordDeletion(draft, 0);
        check(!TrackerHistory.canUndoDeletion(), "a draft cannot enter the persisted-list deletion stack");
        check(!TrackerConfig.INSTANCE.lists.contains(draft) && draft.storageFileName == null,
                "draft operations never register or persist the list");
        TrackerHistory.discardDraft(draft);
    }

    private static void multipleStepsAndMultiplier() {
        TrackerConfig.TrackingList draft = draft();
        int[] multiplier = {1};
        int[] callbacks = {0};
        TrackerHistory.registerDraft(draft, () -> multiplier[0], value -> {
            multiplier[0] = value;
            callbacks[0]++;
        });
        TrackerHistory.record(draft);
        draft.items.forEach(item -> item.setTargetCount(item.targetCount * 3));
        multiplier[0] = 3;
        // Reinitializing a screen must neither replace callbacks nor discard existing entries.
        TrackerHistory.registerDraft(draft, () -> 99, value -> { throw new AssertionError("replaced draft session"); });
        TrackerHistory.record(draft);
        draft.items.removeFirst();
        check(ItemReplacement.replace(draft, draft.items.getFirst(), "minecraft:gold_ingot")
                == ItemReplacement.Result.REPLACED, "replacement after removal accepted");
        check(TrackerHistory.undo(draft) && multiplier[0] == 3 && draft.items.size() == 1
                && draft.items.getFirst().itemId.equals("minecraft:oak_planks")
                && draft.items.getFirst().targetCount == 24, "replacement undo restores its multiplier and rows");
        check(TrackerHistory.undo(draft) && multiplier[0] == 3 && draft.items.size() == 2
                && draft.items.getFirst().targetCount == 12, "removal undo keeps scaled quantities");
        check(TrackerHistory.undo(draft) && multiplier[0] == 1 && draft.items.get(0).targetCount == 4
                && draft.items.get(1).targetCount == 8 && callbacks[0] == 3
                && !TrackerHistory.canUndo(draft), "multiplier undo restores both quantities and applied multiplier once");
        TrackerHistory.discardDraft(draft);
    }

    private static void storedListIsolation() {
        TrackerConfig.TrackingList stored = draft();
        TrackerConfig.TrackingList draft = draft();
        TrackerConfig.INSTANCE.lists.add(stored);
        try {
            TrackerHistory.registerDraft(draft, () -> 2, value -> { });
            TrackerHistory.registerDraft(stored, () -> { throw new AssertionError("stored list read draft state"); },
                    value -> { throw new AssertionError("stored list restored draft state"); });
            TrackerHistory.record(draft);
            draft.items.clear();
            TrackerConfig.TrackedItem row = stored.items.getFirst();
            TrackerHistory.recordCountChange(stored, row);
            row.setTargetCount(5);
            TrackerHistory.recordCountChange(stored, row);
            row.setTargetCount(6);
            TrackerHistory.discardDraft(stored);
            check(TrackerHistory.undo(stored) && stored.items.getFirst().targetCount == 4
                    && !TrackerHistory.canUndo(stored), "ordinary count edits still coalesce into one history entry");
            check(TrackerHistory.canUndo(draft) && TrackerHistory.undo(draft) && draft.items.size() == 2,
                    "stored and draft histories are independent");
        } finally {
            TrackerHistory.discardDraft(draft);
            TrackerConfig.INSTANCE.lists.remove(stored);
            TrackerHistory.clear();
        }
    }

    private static void discardAndClear() {
        TrackerConfig.TrackingList draft = draft();
        TrackerHistory.record(draft);
        check(!TrackerHistory.canUndo(draft), "unregistered drafts have no history");
        TrackerHistory.registerDraft(draft, () -> 1, value -> { throw new AssertionError("discarded callback"); });
        TrackerHistory.record(draft);
        draft.items.clear();
        TrackerHistory.discardDraft(draft);
        TrackerHistory.record(draft);
        check(!TrackerHistory.canUndo(draft) && !TrackerHistory.undo(draft), "discard removes session and snapshots");
        TrackerHistory.registerDraft(draft, () -> 1, value -> { });
        TrackerHistory.record(draft);
        TrackerHistory.clear();
        TrackerHistory.record(draft);
        check(!TrackerHistory.canUndo(draft), "clear releases draft registration as well as history");
    }

    private static void contextChange() throws Exception {
        TrackerConfig.TrackingList draft = draft();
        TrackerHistory.registerDraft(draft, () -> 1, value -> { throw new AssertionError("stale-context callback"); });
        TrackerHistory.record(draft);
        // Change only context identity to exercise history's guard without writing configuration files.
        var contextField = TrackerConfig.class.getDeclaredField("activeContext");
        contextField.setAccessible(true);
        Object original = contextField.get(null);
        try {
            contextField.set(null, TrackerConfig.makeServerContext("draft-history-regression.invalid"));
            check(!TrackerHistory.canUndo(draft), "context change discards old snapshots");
            TrackerHistory.record(draft);
            check(!TrackerHistory.canUndo(draft), "context change releases the old draft session");
        } finally {
            contextField.set(null, original);
            TrackerHistory.clear();
        }
    }

    private static TrackerConfig.TrackingList draft() {
        TrackerConfig.TrackingList list = new TrackerConfig.TrackingList();
        list.items.add(new TrackerConfig.TrackedItem("minecraft:stone", 4));
        list.items.add(new TrackerConfig.TrackedItem("minecraft:oak_planks", 8));
        return list;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
