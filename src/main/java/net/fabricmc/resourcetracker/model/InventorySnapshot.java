package net.fabricmc.resourcetracker.model;

import java.util.Map;

/** Immutable stock observed during the client update, including nested portable containers. */
public record InventorySnapshot(Map<String, Long> counts) {
    public InventorySnapshot { counts = Map.copyOf(counts); }
}
