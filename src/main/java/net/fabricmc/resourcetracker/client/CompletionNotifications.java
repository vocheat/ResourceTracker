package net.fabricmc.resourcetracker.client;

import net.fabricmc.resourcetracker.config.TrackerConfig;
import net.fabricmc.resourcetracker.model.CompletionTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Runs after the tick inventory update, including when the global HUD is hidden. */
public final class CompletionNotifications {
    private static final Map<TrackerConfig.TrackingList, CompletionTracker> TRACKERS = new IdentityHashMap<>();
    private CompletionNotifications() { }

    public static void reset() { TRACKERS.clear(); }

    public static void update(Minecraft client) {
        if (client.player == null) return;
        var config = TrackerConfig.INSTANCE;
        TRACKERS.keySet().removeIf(list -> !config.lists.contains(list));
        List<TrackerConfig.TrackingList> finishedLists = new ArrayList<>();
        List<Component> finishedResources = new ArrayList<>();
        for (var list : config.lists) {
            var tracker = TRACKERS.computeIfAbsent(list, ignored -> new CompletionTracker());
            var source = observe(tracker, "items", list.items);
            CompletionTracker.Changes raw;
            if (list.rawPlanValid) raw = observe(tracker, "raw", list.getRawItems());
            else { tracker.forget("raw"); raw = new CompletionTracker.Changes(false, List.of()); }
            var changes = list.hudRaw ? raw : source;
            if (!list.isVisible) continue;
            if (config.notifyLists && changes.listCompleted()) finishedLists.add(list);
            else if (config.notifyResources) {
                var items = list.displayItems();
                for (String key : changes.resources()) {
                    int index = Integer.parseInt(key.substring(key.lastIndexOf('#') + 1));
                    if (index < items.size()) finishedResources.add(Component.translatable(
                            "gui.resourcetracker.notifications.resource", items.get(index).getDisplayName(), list.name));
                }
            }
        }
        Component message;
        if (!finishedLists.isEmpty()) message = finishedLists.size() == 1
                ? Component.translatable("gui.resourcetracker.notifications.list", finishedLists.getFirst().name)
                : Component.translatable("gui.resourcetracker.notifications.lists", finishedLists.size());
        else if (!finishedResources.isEmpty()) message = finishedResources.size() == 1 ? finishedResources.getFirst()
                : Component.translatable("gui.resourcetracker.notifications.resources", finishedResources.size());
        else return;
        net.fabricmc.resourcetracker.compat.VersionCompat.showCompletionMessage(client, message);
        if (config.completionSound > 0 && config.completionVolume > 0) {
            client.player.playSound(config.completionSound == 2 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    config.completionVolume / 100.0f, config.completionSound == 2 ? 1.0f : 1.2f);
        }
    }

    private static CompletionTracker.Changes observe(CompletionTracker tracker, String mode,
                                                    List<TrackerConfig.TrackedItem> items) {
        List<CompletionTracker.Goal> goals = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (item.isValid()) goals.add(new CompletionTracker.Goal(item.itemId + '#' + i, item.isCompleted()));
        }
        return tracker.observe(mode, goals);
    }
}
