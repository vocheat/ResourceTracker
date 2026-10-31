package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.compat.VersionCompat;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Explicit vanilla wood counterparts: never substitutes a substring in an arbitrary item id. */
public final class FamilyReplacement {
    public enum Family {
        OAK("oak"), SPRUCE("spruce"), BIRCH("birch"), JUNGLE("jungle"), ACACIA("acacia"),
        DARK_OAK("dark_oak"), MANGROVE("mangrove"), CHERRY("cherry"), PALE_OAK("pale_oak"),
        CRIMSON("crimson"), WARPED("warped"), BAMBOO("bamboo");

        private final String key;
        private final Map<String, String> shapes = new LinkedHashMap<>();

        Family(String key) {
            this.key = key;
            for (String shape : List.of("planks", "stairs", "slab", "fence", "fence_gate", "door",
                    "trapdoor", "pressure_plate", "button", "sign", "hanging_sign")) {
                shapes.put(shape, "minecraft:" + key + "_" + shape);
            }
            boolean nether = key.equals("crimson") || key.equals("warped");
            if (key.equals("bamboo")) {
                shapes.put("log", "minecraft:bamboo_block");
                shapes.put("stripped_log", "minecraft:stripped_bamboo_block");
                shapes.put("boat", "minecraft:bamboo_raft");
                shapes.put("chest_boat", "minecraft:bamboo_chest_raft");
                shapes.put("mosaic", "minecraft:bamboo_mosaic");
                shapes.put("mosaic_stairs", "minecraft:bamboo_mosaic_stairs");
                shapes.put("mosaic_slab", "minecraft:bamboo_mosaic_slab");
            } else {
                String log = nether ? "stem" : "log";
                String wood = nether ? "hyphae" : "wood";
                shapes.put("log", "minecraft:" + key + "_" + log);
                shapes.put("wood", "minecraft:" + key + "_" + wood);
                shapes.put("stripped_log", "minecraft:stripped_" + key + "_" + log);
                shapes.put("stripped_wood", "minecraft:stripped_" + key + "_" + wood);
                if (!nether) {
                    shapes.put("boat", "minecraft:" + key + "_boat");
                    shapes.put("chest_boat", "minecraft:" + key + "_chest_boat");
                    shapes.put("leaves", "minecraft:" + key + "_leaves");
                    shapes.put("sapling", "minecraft:" + key + (key.equals("mangrove") ? "_propagule" : "_sapling"));
                }
            }
        }

        public String planksId() { return "minecraft:" + key + "_planks"; }
        private String shape(String id) {
            return shapes.entrySet().stream().filter(entry -> entry.getValue().equals(id))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
    }

    public record Row(String sourceId, String targetId, int count) {
        public boolean matched() { return targetId != null; }
    }
    public record Preview(List<Row> rows, int matched, int unmatched, boolean overflow) { }
    public enum Result { REPLACED, UNCHANGED, OVERFLOW }

    private FamilyReplacement() { }

    public static boolean exists(String id) {
        var item = VersionCompat.getItem(id);
        return item != null && item != Items.AIR;
    }

    public static List<Family> sourceFamilies(TrackerConfig.TrackingList list) {
        return java.util.Arrays.stream(Family.values()).filter(family -> list.items.stream()
                .anyMatch(item -> family.shape(item.itemId) != null)).toList();
    }

    public static Preview preview(TrackerConfig.TrackingList list, Family source, Family target) {
        return preview(list, source, target, 1);
    }

    public static Preview preview(TrackerConfig.TrackingList list, Family source, Family target, int displayMultiplier) {
        return preview(list, source, target, FamilyReplacement::exists, displayMultiplier);
    }

    // Injectable registry membership also verifies missing counterparts in older profiles.
    static Preview preview(TrackerConfig.TrackingList list, Family source, Family target, Predicate<String> exists) {
        return preview(list, source, target, exists, 1);
    }

    private static Preview preview(TrackerConfig.TrackingList list, Family source, Family target,
                                   Predicate<String> exists, int displayMultiplier) {
        if (source == null || target == null || source == target) return new Preview(List.of(), 0, 0, false);
        List<Row> rows = new ArrayList<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        int matched = 0;
        for (var item : list.items) {
            String shape = source.shape(item.itemId);
            if (shape == null) continue;
            String counterpart = target.shapes.get(shape);
            if (counterpart != null && !exists.test(counterpart)) counterpart = null;
            rows.add(new Row(item.itemId, counterpart, item.targetCount));
            if (counterpart != null) {
                matched++;
                totals.merge(counterpart, (long) item.targetCount, Long::sum);
            }
        }
        for (var item : list.items) {
            if (totals.containsKey(item.itemId)) totals.merge(item.itemId, (long) item.targetCount, Long::sum);
        }
        boolean overflow = displayMultiplier < 1 || totals.values().stream().anyMatch(value -> value < 1
                || value > TrackerConfig.MAX_TARGET_COUNT / displayMultiplier);
        return new Preview(List.copyOf(rows), matched, rows.size() - matched, overflow);
    }

    public static Result replace(TrackerConfig.TrackingList list, Family source, Family target) {
        return replace(list, source, target, 1);
    }

    /** The multiplier validates draft display quantities; stored draft counts remain unscaled. */
    public static Result replace(TrackerConfig.TrackingList list, Family source, Family target, int displayMultiplier) {
        Preview preview = preview(list, source, target, displayMultiplier);
        if (preview.overflow()) return Result.OVERFLOW;
        if (preview.matched() == 0) return Result.UNCHANGED;
        Map<String, String> replacements = new LinkedHashMap<>();
        for (Row row : preview.rows()) if (row.matched()) replacements.put(row.sourceId(), row.targetId());
        Set<String> affected = new LinkedHashSet<>(replacements.values());
        Map<String, TrackerConfig.TrackedItem> merged = new LinkedHashMap<>();
        for (var item : list.items) {
            String id = replacements.getOrDefault(item.itemId, item.itemId);
            if (!affected.contains(id)) continue;
            var result = merged.computeIfAbsent(id, ignored -> new TrackerConfig.TrackedItem(id, 0));
            result.targetCount += item.targetCount;
            result.pinned |= item.pinned;
        }
        List<TrackerConfig.TrackedItem> result = new ArrayList<>();
        Set<String> inserted = new LinkedHashSet<>();
        for (var item : list.items) {
            String id = replacements.getOrDefault(item.itemId, item.itemId);
            if (!affected.contains(id)) result.add(item);
            else if (inserted.add(id)) result.add(merged.get(id));
        }
        TrackerHistory.record(list);
        list.items = result;
        ResourceTrackerClient.invalidateTargetItemCache();
        return Result.REPLACED;
    }
}
