package net.fabricmc.resourcetracker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Completion edges only: loading a completed list or observing it repeatedly stays quiet. */
public final class CompletionTracker {
    public record Goal(String key, boolean completed) { }
    public record Changes(boolean listCompleted, List<String> resources) {
        public Changes { resources = List.copyOf(resources); }
    }
    private final Map<String, Map<String, Boolean>> previous = new HashMap<>();

    public Changes observe(String dataset, List<Goal> goals) {
        Map<String, Boolean> current = new LinkedHashMap<>();
        for (Goal goal : goals) current.put(goal.key(), goal.completed());
        Map<String, Boolean> old = previous.put(dataset, current);
        if (old == null) return new Changes(false, List.of());
        List<String> completed = new ArrayList<>();
        current.forEach((key, done) -> {
            if (done && Boolean.FALSE.equals(old.get(key))) completed.add(key);
        });
        boolean allDone = !current.isEmpty() && current.values().stream().allMatch(Boolean::booleanValue);
        boolean wasDone = !old.isEmpty() && old.values().stream().allMatch(Boolean::booleanValue);
        return new Changes(allDone && !wasDone, completed);
    }

    public void forget(String dataset) { previous.remove(dataset); }
}
