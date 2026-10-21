package net.fabricmc.resourcetracker.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Display-only ordering: never reorder the saved list or change its completion summary. */
public final class HudSelection {
    private HudSelection() { }
    public record Selection<T>(List<T> rows, int hiddenByLimit, int completed, int total) { }

    public static <T extends ResourceState> Selection<T> select(List<T> items, boolean hideCompleted,
                                                               boolean sortRemaining, int maxRows) {
        int completed = 0;
        List<T> visible = new ArrayList<>();
        for (T item : items) {
            if (item.isCompleted()) completed++;
            if (!hideCompleted || !item.isCompleted()) visible.add(item);
        }
        Comparator<T> comparator = Comparator.comparing((T item) -> !item.pinned);
        if (sortRemaining) comparator = comparator.thenComparing(
                Comparator.comparingInt((T item) -> item.remaining()).reversed());
        visible.sort(comparator);
        int shown = maxRows <= 0 ? visible.size() : Math.min(maxRows, visible.size());
        return new Selection<>(List.copyOf(visible.subList(0, shown)), visible.size() - shown,
                completed, items.size());
    }
}
