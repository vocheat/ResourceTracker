package net.fabricmc.resourcetracker.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Regression checks for exact and decimal stack quantity formatting. */
public final class CountFormatterRegression {
    private static final CountFormatter.Labels LABELS = new CountFormatter.Labels("i", "s", "b");
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    public static void main(String[] args) {
        exactExamples();
        roundingProperties();
        invalidInputs();
        legacyUnits();
        System.out.println("Count formatter regression checks passed");
    }

    private static void exactExamples() {
        expect(272, 64, "4.25 s");
        expect(273, 64, "4.27 s");
        expect(1, 64, "0.02 s");
        expect(64, 64, "1 s");
        expect(96, 64, "1.5 s");
        expect(199, 200, "1 s");
        expect(0, 64, "0 s");
        expect(1, 16, "0.07 s");
        expect(1, 1, "1 s");
        expect(1, Integer.MAX_VALUE, "0.01 s");
        expect(Long.MAX_VALUE, Integer.MAX_VALUE, expected(Long.MAX_VALUE, Integer.MAX_VALUE) + " s");
    }

    private static void roundingProperties() {
        long[] counts = {0, 1, 2, 15, 16, 63, 64, 65, 271, 272, 273,
                1_000_003, Integer.MAX_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE};
        int[] stackSizes = {1, 2, 16, 32, 64, 127, 1_000_003, Integer.MAX_VALUE};
        for (long count : counts) {
            for (int stackSize : stackSizes) {
                String result = CountFormatter.format(count, stackSize, true,
                        CountFormatter.Unit.DECIMAL_STACKS, LABELS);
                String expected = expected(count, stackSize) + " s";
                check(result.equals(expected), "decimal stack output for " + count + "/" + stackSize
                        + ": expected " + expected + ", got " + result);

                BigDecimal shown = new BigDecimal(result.substring(0, result.length() - 2));
                BigDecimal exact = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(stackSize), 20,
                        RoundingMode.DOWN);
                check(shown.compareTo(exact) >= 0, "displayed stacks must not understate " + count + "/" + stackSize);
                check(shown.subtract(exact).compareTo(new BigDecimal("0.01")) < 0,
                        "rounding excess must be below 0.01 for " + count + "/" + stackSize);
            }
        }
    }

    private static String expected(long count, int stackSize) {
        BigInteger[] qr = BigInteger.valueOf(count).multiply(HUNDRED)
                .divideAndRemainder(BigInteger.valueOf(stackSize));
        BigInteger hundredths = qr[0].add(qr[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        return new BigDecimal(hundredths, 2).stripTrailingZeros().toPlainString();
    }

    private static void invalidInputs() {
        throwsIllegalArgument(-1, 64);
        throwsIllegalArgument(0, 0);
        throwsIllegalArgument(0, -1);
    }

    private static void throwsIllegalArgument(long count, int stackSize) {
        try {
            CountFormatter.format(count, stackSize, true, CountFormatter.Unit.DECIMAL_STACKS, LABELS);
            throw new AssertionError("expected IllegalArgumentException for " + count + "/" + stackSize);
        } catch (IllegalArgumentException expected) {
            // Expected validation behavior.
        }
    }

    private static void legacyUnits() {
        check(CountFormatter.format(17, 16, true, CountFormatter.Unit.STACKS, LABELS).equals("1 s 1 i"),
                "stack formatting remains unchanged");
        check(CountFormatter.format(1729, 64, true, CountFormatter.Unit.SHULKERS, LABELS).equals("1 b 1 i"),
                "shulker formatting remains unchanged");
        check(CountFormatter.format(27, 1, false, CountFormatter.Unit.SHULKERS, LABELS).equals("27 i"),
                "non-nestable shulkers remain stacks");
        check(CountFormatter.format(Long.MAX_VALUE, 64, true, CountFormatter.Unit.ITEMS, LABELS)
                .equals(Long.toString(Long.MAX_VALUE)), "item formatting remains unchanged");
    }

    private static void expect(long count, int stackSize, String expected) {
        String actual = CountFormatter.format(count, stackSize, true, CountFormatter.Unit.DECIMAL_STACKS, LABELS);
        check(actual.equals(expected), "expected " + expected + " for " + count + "/" + stackSize + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
