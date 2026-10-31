package net.fabricmc.resourcetracker.model;

import java.util.ArrayList;
import java.util.List;

/** Item quantities in exact units or decimal stacks rounded up to hundredths. */
public final class CountFormatter {
    public enum Unit { ITEMS, STACKS, DECIMAL_STACKS, SHULKERS }
    public record Labels(String items, String stacks, String shulkers) { }
    private CountFormatter() { }

    public static String format(long count, int stackSize, boolean fitsShulker, Unit unit, Labels labels) {
        if (count < 0 || stackSize < 1) throw new IllegalArgumentException("Invalid item quantity");
        if (unit == Unit.ITEMS) return Long.toString(count);
        if (unit == Unit.DECIMAL_STACKS) return decimalStacks(count, stackSize) + " " + labels.stacks();
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

    private static String decimalStacks(long count, int stackSize) {
        long whole = count / stackSize;
        // Split before multiplying to avoid overflow even for Long.MAX_VALUE.
        long hundredths = ((count % stackSize) * 100 + stackSize - 1) / stackSize;
        if (hundredths == 100) {
            whole++;
            hundredths = 0;
        }
        if (hundredths == 0) return Long.toString(whole);
        String fraction = hundredths % 10 == 0 ? Long.toString(hundredths / 10)
                : (hundredths < 10 ? "0" : "") + hundredths;
        return whole + "." + fraction;
    }
}
