package net.fabricmc.resourcetracker.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import net.fabricmc.resourcetracker.model.ResourceState;

/** Optional TXT metadata is placed before [items], readable by earlier releases too. */
public final class ListMetadata {
    public final Map<String, Map<String, String>> sections = new LinkedHashMap<>();

    public void put(String section, String key, String value) {
        sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>()).put(key, value);
    }

    public Map<String, String> section(String name) {
        return sections.getOrDefault(name, Map.of());
    }

    public void putItemStates(List<? extends ResourceState> items) {
        for (int index = 0; index < items.size(); index++) {
            ResourceState item = items.get(index);
            if (item.manuallyCollectedTarget > 0 || item.pinned) put("item-state", Integer.toString(index),
                    item.itemId + "|" + item.manuallyCollectedTarget + "|" + item.pinned);
        }
    }

    public void applyItemStates(List<? extends ResourceState> items) {
        section("item-state").forEach((key, value) -> {
            try {
                int index = Integer.parseInt(key);
                String[] parts = value.split("\\|", -1);
                if (index < 0 || index >= items.size() || parts.length != 3) return;
                ResourceState item = items.get(index);
                // External text edits/reordering cannot assign a different item's annotation.
                if (!item.itemId.equals(parts[0])) return;
                item.manuallyCollectedTarget = Math.max(0, Integer.parseInt(parts[1]));
                item.pinned = Boolean.parseBoolean(parts[2]);
                item.validateCollected();
            } catch (NumberFormatException ignored) {
                // Invalid optional annotations do not discard otherwise valid legacy goals.
            }
        });
    }

    public static ListMetadata decode(String text) {
        ListMetadata result = new ListMetadata();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1);
                if (section.equalsIgnoreCase("items")) break;
                continue;
            }
            int separator = line.indexOf('=');
            if (!section.isEmpty() && separator > 0) result.put(section,
                    line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return result;
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        sections.forEach((name, entries) -> {
            if (entries.isEmpty()) return;
            result.append('\n').append('[').append(clean(name)).append("]\n");
            entries.forEach((key, value) -> result.append(clean(key)).append('=').append(clean(value)).append('\n'));
        });
        return result.toString();
    }

    public static String clean(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }
}
