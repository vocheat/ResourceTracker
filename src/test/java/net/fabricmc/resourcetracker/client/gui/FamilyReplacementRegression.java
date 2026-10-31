package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Exercises real registry counterparts, atomic merges, draft scaling, and the undo boundary. */
public final class FamilyReplacementRegression {
    private FamilyReplacementRegression() { }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var loader = FabricLoader.getInstance();
        var directory = loader.getClass().getDeclaredField("configDir");
        directory.setAccessible(true);
        if (directory.get(loader) == null) {
            Path scratch = Path.of("Workflow", "replacement-regression-config").toAbsolutePath();
            Files.createDirectories(scratch);
            directory.set(loader, scratch);
        }
        shapesAndUndo();
        missingCounterparts();
        overflowAndDraftScale();
        System.out.println("Family replacement regression checks passed");
    }

    private static void shapesAndUndo() {
        var list = new TrackerConfig.TrackingList();
        List<String> shapes = List.of("planks", "stairs", "slab", "door", "trapdoor", "fence", "fence_gate",
                "pressure_plate", "button", "sign", "hanging_sign", "log", "wood", "leaves", "sapling",
                "boat", "chest_boat");
        for (int i = 0; i < shapes.size(); i++) {
            var item = add(list, "oak_" + shapes.get(i), i + 1);
            item.pinned = true;
            item.toggleCollected();
            item.cachedCount = 99;
            item.getItem();
        }
        add(list, "stripped_oak_log", 22);
        add(list, "stripped_oak_wood", 23);
        var destination = add(list, "spruce_planks", 30);
        destination.toggleCollected();
        destination.cachedCount = 44;
        var unchanged = add(list, "stone", 64);
        unchanged.toggleCollected();
        unchanged.cachedCount = 70;
        var custom = new TrackerConfig.TrackedItem("example:oak_planks", 8);
        list.items.add(custom);
        TrackerConfig.INSTANCE.lists.add(list);
        TrackerHistory.clear();
        try {
            var preview = FamilyReplacement.preview(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE);
            check(preview.matched() == shapes.size() + 2 && preview.unmatched() == 0 && !preview.overflow(),
                    "all exact oak shapes have real spruce counterparts");
            check(FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE)
                    == FamilyReplacement.Result.REPLACED, "family replacement accepted");
            check(list.items.getFirst().getItem() == Items.SPRUCE_PLANKS
                    && list.items.getFirst().targetCount == 31 && list.items.getFirst().pinned,
                    "duplicate destination sums quantities and preserves pins");
            for (var item : list.items) {
                if (item.itemId.startsWith("minecraft:spruce_") || item.itemId.startsWith("minecraft:stripped_spruce_")) {
                    check(!item.isManuallyCollected() && item.cachedCount == 0, "all changed requirements reset state");
                }
            }
            check(list.items.contains(unchanged) && unchanged.isManuallyCollected() && unchanged.cachedCount == 70
                    && list.items.contains(custom), "unrelated objects and custom namespace remain untouched");
            check(TrackerHistory.undo(list) && list.items.size() == shapes.size() + 5
                    && list.items.getFirst().itemId.equals("minecraft:oak_planks")
                    && list.items.getFirst().isManuallyCollected(), "one undo restores all family source rows and marks");
            check(!TrackerHistory.canUndo(list), "family replacement records exactly one snapshot");
            check(FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.OAK)
                    == FamilyReplacement.Result.UNCHANGED && !TrackerHistory.canUndo(list), "same family makes no history entry");
        } finally {
            TrackerConfig.INSTANCE.lists.remove(list);
            TrackerHistory.clear();
        }
    }

    private static void missingCounterparts() {
        var list = new TrackerConfig.TrackingList();
        add(list, "oak_planks", 10);
        add(list, "oak_door", 3);
        var boat = add(list, "oak_boat", 2);
        var leaves = add(list, "oak_leaves", 40);
        var preview = FamilyReplacement.preview(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.CRIMSON);
        check(preview.matched() == 2 && preview.unmatched() == 2, "nether wood lacks boats and leaves explicitly");
        check(FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.CRIMSON)
                == FamilyReplacement.Result.REPLACED && list.items.contains(boat) && list.items.contains(leaves),
                "unsupported shapes stay unchanged during partial replacement");
        var oldRegistry = new TrackerConfig.TrackingList();
        add(oldRegistry, "oak_planks", 1);
        var unavailable = FamilyReplacement.preview(oldRegistry, FamilyReplacement.Family.OAK,
                FamilyReplacement.Family.PALE_OAK, id -> false);
        check(unavailable.matched() == 0 && unavailable.unmatched() == 1,
                "profile registry absence never resolves to another item");
        var bamboo = new TrackerConfig.TrackingList();
        add(bamboo, "bamboo_raft", 2);
        add(bamboo, "bamboo_mosaic_stairs", 5);
        var bambooPreview = FamilyReplacement.preview(bamboo, FamilyReplacement.Family.BAMBOO, FamilyReplacement.Family.OAK);
        check(bambooPreview.matched() == 1 && bambooPreview.unmatched() == 1
                && bambooPreview.rows().getFirst().targetId().equals("minecraft:oak_boat"),
                "explicit raft counterpart preserves boat intent while mosaic remains unmatched");
    }

    private static void overflowAndDraftScale() {
        var list = new TrackerConfig.TrackingList();
        var source = add(list, "oak_planks", 2);
        var door = add(list, "oak_door", 3);
        var destination = add(list, "spruce_planks", TrackerConfig.MAX_TARGET_COUNT);
        destination.toggleCollected();
        TrackerConfig.INSTANCE.lists.add(list);
        TrackerHistory.clear();
        try {
            check(FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE)
                    == FamilyReplacement.Result.OVERFLOW && list.items.equals(List.of(source, door, destination))
                    && destination.isManuallyCollected() && !TrackerHistory.canUndo(list),
                    "late merge overflow rejects every row atomically without history");
            destination.targetCount = TrackerConfig.MAX_TARGET_COUNT / 2;
            check(FamilyReplacement.preview(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE, 2).overflow()
                    && FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE, 2)
                    == FamilyReplacement.Result.OVERFLOW && list.items.getFirst() == source,
                    "draft multiplier validates merged actual quantity before mutation");
            destination.targetCount = 8;
            check(FamilyReplacement.replace(list, FamilyReplacement.Family.OAK, FamilyReplacement.Family.SPRUCE, 3)
                    == FamilyReplacement.Result.REPLACED && list.items.getFirst().targetCount == 10,
                    "valid draft replacement keeps base quantities unscaled");
        } finally {
            TrackerConfig.INSTANCE.lists.remove(list);
            TrackerHistory.clear();
        }
    }

    private static TrackerConfig.TrackedItem add(TrackerConfig.TrackingList list, String path, int count) {
        var item = new TrackerConfig.TrackedItem("minecraft:" + path, count);
        list.items.add(item);
        return item;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
