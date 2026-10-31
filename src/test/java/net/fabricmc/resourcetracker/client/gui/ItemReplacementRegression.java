package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks replacement intent, real registry caches, and the actual undo boundary. */
public final class ItemReplacementRegression {
    private ItemReplacementRegression() { }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // The standalone JVM has no launched Fabric game provider or config directory.
        var loader = FabricLoader.getInstance();
        var directory = loader.getClass().getDeclaredField("configDir");
        directory.setAccessible(true);
        if (directory.get(loader) == null) {
            Path scratch = Path.of("Workflow", "replacement-regression-config").toAbsolutePath();
            Files.createDirectories(scratch);
            directory.set(loader, scratch);
        }
        replacementAndUndo();
        mergeAndRejection();
        scaledDraftMerge();
        draft();
        System.out.println("Item replacement regression checks passed");
    }

    private static void replacementAndUndo() {
        TrackerConfig.TrackingList list = new TrackerConfig.TrackingList();
        TrackerConfig.TrackedItem source = new TrackerConfig.TrackedItem("minecraft:stone", 130);
        source.pinned = true;
        source.toggleCollected();
        source.cachedCount = 98;
        check(source.getItem() == Items.STONE, "prime original registry cache");
        list.items.add(source);
        list.recipeChoices.put("minecraft:stone", "minecraft:stone_from_smelting_cobblestone");
        list.ingredientChoices.put("recipe-slot", "minecraft:oak_planks");
        list.terminalItems.add("minecraft:stone");
        list.rawState.put("minecraft:cobblestone", new TrackerConfig.RawState(130, true));
        TrackerConfig.INSTANCE.lists.add(list);
        TrackerHistory.clear();
        try {
            check(ItemReplacement.replace(list, source, "minecraft:diamond") == ItemReplacement.Result.REPLACED,
                    "replacement accepted");
            TrackerConfig.TrackedItem replacement = list.items.getFirst();
            check(replacement.targetCount == 130 && replacement.pinned, "target and pin preserved");
            check(replacement.getItem() == Items.DIAMOND && replacement.cachedCount == 0
                    && !replacement.isManuallyCollected(), "new item does not inherit registry, inventory, or collected caches");
            check(list.recipeChoices.size() == 1 && !list.recipeChoices.containsKey("minecraft:diamond")
                    && list.ingredientChoices.get("recipe-slot").equals("minecraft:oak_planks")
                    && list.terminalItems.contains("minecraft:stone"), "item-specific recipe preferences are not copied to new material");
            check(TrackerHistory.canUndo(list) && TrackerHistory.undo(list), "replacement is one undo step");
            TrackerConfig.TrackedItem restored = list.items.getFirst();
            check(restored.itemId.equals("minecraft:stone") && restored.targetCount == 130
                    && restored.pinned && restored.isManuallyCollected(), "undo restores exact source intent");
            check(list.rawState.get("minecraft:cobblestone").collectedTarget == 130
                    && list.rawState.get("minecraft:cobblestone").pinned, "undo preserves raw metadata");
            check(!TrackerHistory.canUndo(list), "one replacement creates exactly one snapshot");
            check(ItemReplacement.replace(list, restored, restored.itemId) == ItemReplacement.Result.UNCHANGED
                    && !TrackerHistory.canUndo(list), "same item makes no undo entry");
            check(ItemReplacement.replace(list, source, "minecraft:gold_ingot") == ItemReplacement.Result.MISSING
                    && !TrackerHistory.canUndo(list), "stale source cannot replace an unrelated row");
        } finally {
            TrackerConfig.INSTANCE.lists.remove(list);
            TrackerHistory.clear();
        }
    }

    private static void mergeAndRejection() {
        TrackerConfig.TrackingList list = new TrackerConfig.TrackingList();
        TrackerConfig.TrackedItem source = new TrackerConfig.TrackedItem("minecraft:stone", 45);
        source.pinned = true;
        TrackerConfig.TrackedItem existing = new TrackerConfig.TrackedItem("minecraft:diamond", 85);
        existing.toggleCollected();
        list.items.add(source);
        list.items.add(existing);
        TrackerConfig.INSTANCE.lists.add(list);
        TrackerHistory.clear();
        try {
            check(ItemReplacement.replace(list, source, existing.itemId) == ItemReplacement.Result.REPLACED,
                    "duplicate target accepted");
            check(list.items.size() == 1 && list.items.getFirst() == existing && existing.targetCount == 130
                    && existing.pinned && !existing.isManuallyCollected(), "merge sums quantities and reopens increased requirement");
            check(TrackerHistory.undo(list) && list.items.size() == 2
                    && list.items.get(0).targetCount == 45 && list.items.get(1).targetCount == 85
                    && list.items.get(1).isManuallyCollected(), "undo restores both premerge rows and marks");
            source = list.items.get(0);
            existing = list.items.get(1);
            existing.targetCount = TrackerConfig.MAX_TARGET_COUNT;
            existing.toggleCollected();
            TrackerHistory.clear();
            check(ItemReplacement.replace(list, source, existing.itemId) == ItemReplacement.Result.OVERFLOW,
                    "overflow refused");
            check(list.items.size() == 2 && list.items.get(0) == source && list.items.get(1) == existing
                    && source.targetCount == 45 && existing.targetCount == TrackerConfig.MAX_TARGET_COUNT
                    && existing.isManuallyCollected() && !TrackerHistory.canUndo(list), "overflow is atomic and creates no undo entry");
        } finally {
            TrackerConfig.INSTANCE.lists.remove(list);
            TrackerHistory.clear();
        }
    }

    private static void scaledDraftMerge() {
        var draft = new TrackerConfig.TrackingList();
        int edge = TrackerConfig.MAX_TARGET_COUNT / 4 + 1;
        var source = new TrackerConfig.TrackedItem("minecraft:stone", edge);
        var destination = new TrackerConfig.TrackedItem("minecraft:diamond", edge);
        draft.items.add(source);
        draft.items.add(destination);
        TrackerHistory.registerDraft(draft, () -> 2, value -> { });
        try {
            check(ItemReplacement.replace(draft, source, destination.itemId, 2) == ItemReplacement.Result.OVERFLOW
                    && draft.items.size() == 2 && source.targetCount == edge && destination.targetCount == edge
                    && !TrackerHistory.canUndo(draft), "scaled merge overflow is atomic and records no draft history");
            source.targetCount = 3;
            destination.targetCount = 5;
            check(ItemReplacement.replace(draft, source, destination.itemId, 2) == ItemReplacement.Result.REPLACED
                    && draft.items.size() == 1 && draft.items.getFirst().targetCount == 8,
                    "scaled replacement retains the sum for one copy");
            check(TrackerHistory.undo(draft) && draft.items.size() == 2
                    && draft.items.get(0).targetCount == 3 && draft.items.get(1).targetCount == 5,
                    "scaled merge restores both base targets in one draft undo");
        } finally {
            TrackerHistory.discardDraft(draft);
        }
    }

    private static void draft() {
        TrackerConfig.TrackingList draft = new TrackerConfig.TrackingList();
        TrackerConfig.TrackedItem source = new TrackerConfig.TrackedItem("minecraft:stone", 64);
        draft.items.add(source);
        check(ItemReplacement.replace(draft, source, "minecraft:oak_planks") == ItemReplacement.Result.REPLACED
                && draft.items.getFirst().targetCount == 64 && !TrackerConfig.INSTANCE.lists.contains(draft),
                "draft replacement does not register or save the list");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
