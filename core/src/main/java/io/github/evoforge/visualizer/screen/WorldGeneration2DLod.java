package io.github.evoforge.visualizer.screen;

/**
 * Pure sampling policy that caps 2D preview work while keeping very close inspection exact.
 *
 * <p>The budgets are intentionally live-tunable from the development preview. They affect only
 * presentation work and never world generation or provenance. Production V15 pages and lazy terrain
 * shape decisions remain materially more expensive than the old dense snapshot, so the synchronous
 * exact renderer is reserved for a tiny viewport; ordinary zoom stays on the asynchronous overview
 * refinement path.</p>
 */
final class WorldGeneration2DLod {
    static final long DEFAULT_MAX_DETAILED_CELLS = 64L;
    static final long DEFAULT_MAX_SAMPLES = 6_000L;
    static final long MIN_DETAILED_CELLS = 32L;
    static final long MAX_DETAILED_CELLS = 2_000L;
    static final long MIN_OVERVIEW_SAMPLES = 1_500L;
    static final long MAX_OVERVIEW_SAMPLES = 24_000L;

    private static volatile long detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
    private static volatile long overviewSampleBudget = DEFAULT_MAX_SAMPLES;

    private WorldGeneration2DLod() {
    }

    static int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return 1;
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        if (cells <= detailedCellBudget) return 1;

        int overviewStride = Math.max(
                1,
                (int) Math.ceil(Math.sqrt(cells / (double) overviewSampleBudget)));
        return Math.max(2, overviewStride);
    }

    static long sampledCells(int widthCells, int lengthCells, int stride) {
        if (widthCells <= 0 || lengthCells <= 0) return 0L;
        int safeStride = Math.max(1, stride);
        long x = (widthCells + (long) safeStride - 1L) / safeStride;
        long y = (lengthCells + (long) safeStride - 1L) / safeStride;
        return Math.multiplyExact(x, y);
    }

    static long detailedCellBudget() {
        return detailedCellBudget;
    }

    static long overviewSampleBudget() {
        return overviewSampleBudget;
    }

    static void detailedCellBudget(long value) {
        detailedCellBudget = requireRange(
                value,
                MIN_DETAILED_CELLS,
                MAX_DETAILED_CELLS,
                "detailed cell budget");
    }

    static void overviewSampleBudget(long value) {
        overviewSampleBudget = requireRange(
                value,
                MIN_OVERVIEW_SAMPLES,
                MAX_OVERVIEW_SAMPLES,
                "overview sample budget");
    }

    static void resetTuning() {
        detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
        overviewSampleBudget = DEFAULT_MAX_SAMPLES;
    }

    private static long requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }
}
