package net.fabricmc.resourcetracker.model;

import java.util.ArrayList;
import java.util.List;

/** Exact quantities, expressed as full containers/stacks plus a remainder. */
public final class CountFormatter {
    public enum Unit { ITEMS, STACKS, SHULKERS }
    public record Labels(String items, String stacks, String shulkers) { }
    private CountFormatter() { }

    public static String format(long count, int stackSize, boolean fitsShulker, Unit unit, Labels labels) {
        if (count < 0 || stackSize < 1) throw new IllegalArgumentException("Invalid item quantity");
        if (unit == Unit.ITEMS) return Long.toString(count);
        if (!fitsShulker && unit == Unit.SHULKERS) unit = Unit.STACKS;
        List<String> parts = new ArrayList<>(3);
        if (unit == Unit.SHULKERS) {
            long capacity = 27L * stackSize;
            long boxes = count / capacity;
            if (boxes > 0) parts.add(boxes + " " + labels.shulkers());
            count %= capacity;
        }
        if (stackSize > 1) {
            long stacks = count / stackSize;
            if (stacks > 0) parts.add(stacks + " " + labels.stacks());
            count %= stackSize;
        }
        if (count > 0 || parts.isEmpty()) parts.add(count + " " + labels.items());
        return String.join(" ", parts);
    }
}
