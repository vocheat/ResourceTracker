package net.fabricmc.resourcetracker.config;

import java.util.ArrayList;

/** Exercises intent snapshots without loading Fabric's global configuration or a client. */
public final class TrackerHistoryRegression {
    public static void main(String[] args) {
        TrackerConfig.TrackingList list = new TrackerConfig.TrackingList();
        TrackerConfig.TrackedItem stone = new TrackerConfig.TrackedItem("minecraft:stone", 64);
        stone.manuallyCollectedTarget = 64;
        stone.pinned = true;
        stone.cachedCount = 17;
        list.items.add(stone);
        list.recipeChoices.put("minecraft:stone", "recipe-a");
        list.ingredientChoices.put("slot-a", "minecraft:cobblestone");
        list.terminalItems.add("minecraft:oak_log");
        TrackerConfig.TrackedItem raw = new TrackerConfig.TrackedItem("minecraft:cobblestone", 12);
        raw.manuallyCollectedTarget = 12;
        raw.pinned = true;
        list.rawItems.add(raw);

        TrackerHistory.Snapshot snapshot = TrackerHistory.Snapshot.capture(list);
        stone.setTargetCount(128);
        stone.pinned = false;
        list.items.clear();
        list.recipeChoices.clear();
        list.ingredientChoices.clear();
        list.terminalItems.clear();
        list.rawState.get(raw.itemId).collectedTarget = 0;
        raw.manuallyCollectedTarget = 0;
        raw.pinned = false;
        list.name = "Appearance stays independent";
        list.columns = 3;
        snapshot.restore(list);

        TrackerConfig.TrackedItem restored = list.items.getFirst();
        check(restored != stone && restored.targetCount == 64 && restored.isManuallyCollected() && restored.pinned,
                "deleted rows restore exact independent source intent");
        check(restored.cachedCount == 0, "inventory is derived, never restored from history");
        check(list.recipeChoices.get("minecraft:stone").equals("recipe-a")
                        && list.ingredientChoices.get("slot-a").equals("minecraft:cobblestone")
                        && list.terminalItems.contains("minecraft:oak_log"), "crafting decisions restore");
        check(list.rawItems.isEmpty() && !list.rawPlanValid, "stale derived rows are discarded");
        list.captureRawState();
        check(list.rawState.get(raw.itemId).collectedTarget == 12 && list.rawState.get(raw.itemId).pinned,
                "live raw annotations are captured before copying and survive the next capture");
        check(list.name.equals("Appearance stays independent") && list.columns == 3,
                "resource undo preserves unrelated name and display edits");

        // Restoring more than once must not share mutable row or raw-state instances with the snapshot.
        restored.targetCount = 2;
        list.rawState.get(raw.itemId).pinned = false;
        list.items = new ArrayList<>();
        snapshot.restore(list);
        check(list.items.getFirst().targetCount == 64 && list.rawState.get(raw.itemId).pinned,
                "snapshot restoration does not alias subsequent edits");
        System.out.println("History snapshot regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
