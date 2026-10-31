package net.fabricmc.resourcetracker.client.gui;

import net.fabricmc.resourcetracker.client.ResourceTrackerClient;
import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.config.TrackerHistory;

/** Changes resource intent without saving, including lists still being drafted. */
public final class ItemReplacement {
    public enum Result { REPLACED, UNCHANGED, OVERFLOW, MISSING }

    private ItemReplacement() { }

    public static Result replace(TrackerConfig.TrackingList list, TrackerConfig.TrackedItem source, String itemId) {
        return replace(list, source, itemId, 1);
    }

    /** Draft counts stay at one copy; validate the visible combined target before mutating. */
    public static Result replace(TrackerConfig.TrackingList list, TrackerConfig.TrackedItem source, String itemId,
                                 int displayMultiplier) {
        int index = list.items.indexOf(source);
        if (index < 0) return Result.MISSING;
        if (itemId.equals(source.itemId)) return Result.UNCHANGED;
        TrackerConfig.TrackedItem existing = list.items.stream()
                .filter(item -> item != source && itemId.equals(item.itemId)).findFirst().orElse(null);
        long target = (long) source.targetCount + (existing == null ? 0 : existing.targetCount);
        if (displayMultiplier < 1 || target < 1 || target > TrackerConfig.MAX_TARGET_COUNT / displayMultiplier)
            return Result.OVERFLOW;

        TrackerHistory.record(list);
        if (existing == null) {
            // A different item needs fresh runtime caches and collected acknowledgement.
            TrackerConfig.TrackedItem replacement = new TrackerConfig.TrackedItem(itemId, (int) target);
            replacement.pinned = source.pinned;
            list.items.set(index, replacement);
        } else {
            existing.setTargetCount((int) target);
            existing.pinned |= source.pinned;
            list.items.remove(source);
        }
        ResourceTrackerClient.invalidateTargetItemCache();
        return Result.REPLACED;
    }
}
