package io.github.evoforge.simulation.world.atlas;

/**
 * Exact constant-memory order-statistic selection for positive IEEE-754 doubles.
 *
 * <p>For finite positive doubles (and positive infinity), unsigned IEEE-754 bit patterns increase in
 * exactly the same order as the numeric values. Selection can therefore resolve the requested raw
 * bit pattern one byte at a time. Each byte requires one linear scan of the source and a 256-entry
 * histogram. No copy of the selected population and no comparison sort are required.</p>
 */
final class PositiveDoubleRadixSelector {
    private static final int RADIX = 256;
    private static final int BYTE_MASK = RADIX - 1;
    private static final int BYTES = Long.BYTES;

    private PositiveDoubleRadixSelector() {
    }

    /**
     * Returns the zero-based {@code rank}-th value among all values strictly greater than zero, in
     * ascending numeric order. NaN, negative values and both signed zeroes are excluded.
     */
    static double select(double[] values, int positiveCount, int rank) {
        if (values == null) throw new IllegalArgumentException("values must not be null");
        if (positiveCount <= 0 || rank < 0 || rank >= positiveCount) {
            throw new IllegalArgumentException("positive selection count/rank are invalid");
        }

        long selectedPrefix = 0L;
        long prefixMask = 0L;
        int remainingRank = rank;
        int[] counts = new int[RADIX];

        for (int byteIndex = 0; byteIndex < BYTES; byteIndex++) {
            int shift = (BYTES - 1 - byteIndex) * Byte.SIZE;
            java.util.Arrays.fill(counts, 0);
            int matched = 0;

            for (double value : values) {
                if (!(value > 0d)) continue;
                long bits = Double.doubleToRawLongBits(value);
                if ((bits & prefixMask) != selectedPrefix) continue;
                counts[(int) ((bits >>> shift) & BYTE_MASK)]++;
                matched++;
            }
            if (matched <= remainingRank) {
                throw new IllegalArgumentException(
                        "positiveCount does not match the selectable source population");
            }

            int selectedByte = 0;
            while (remainingRank >= counts[selectedByte]) {
                remainingRank -= counts[selectedByte];
                selectedByte++;
            }
            long byteMask = (long) BYTE_MASK << shift;
            selectedPrefix |= (long) selectedByte << shift;
            prefixMask |= byteMask;
        }

        return Double.longBitsToDouble(selectedPrefix);
    }
}
