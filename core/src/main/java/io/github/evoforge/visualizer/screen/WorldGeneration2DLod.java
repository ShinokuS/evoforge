package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/**
 * Pure presentation sampling policy for the V15 inspector.
 *
 * <p>Overview strides are nested powers of two. Cell-detail x1 is allowed across a genuinely useful
 * regional viewport because authoritative data is prepared by a bounded asynchronous tile cache,
 * not by synchronous per-cell Continuum reads. The two quality budgets are coupled deliberately:
 * raising cell detail may make the immediately adjacent parent levels somewhat denser, but it can
 * never make the renderer skip a nested level such as x4 -> x1. That adjacency is required for
 * visually seamless refinement.</p>
 */
final class WorldGeneration2DLod {
    /** Fast default: roughly a 95x95 square may enter x1 once tiles are ready. */
    static final long DEFAULT_MAX_DETAILED_CELLS = 9_000L;
    static final long DEFAULT_MAX_SAMPLES = 6_000L;
    static final long MIN_DETAILED_CELLS = 2_000L;
    /** High-quality inspection: up to roughly 500x500 visible cells at x1. */
    static final long MAX_DETAILED_CELLS = 250_000L;
    static final long MIN_OVERVIEW_SAMPLES = 1_500L;
    static final long MAX_OVERVIEW_SAMPLES = 24_000L;

    /** Leave a dead-band around every level boundary so wheel noise cannot flap LODs. */
    private static final int LOD_EXIT_PERCENT = 115;
    private static final int LOD_ENTER_PERCENT = 95;

    private static volatile long detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
    private static volatile long overviewSampleBudget = DEFAULT_MAX_SAMPLES;
    private static volatile int previousStride = 1;

    private WorldGeneration2DLod() {
    }

    /**
     * Chooses a nested presentation LOD with hysteresis on every boundary.
     *
     * <p>The old policy independently compared x1 with {@code detailedCellBudget} and overview levels
     * with {@code overviewSampleBudget}. At high Detailed range that could make one zoom step jump
     * directly from x4 (or worse) to x1. The new capacity ladder blends the two budgets: the near
     * budget decays gradually with stride while the far budget takes over naturally. Consecutive
     * zoom thresholds therefore always differ by exactly one power-of-two level.</p>
     */
    static synchronized int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) {
            previousStride = 1;
            return 1;
        }
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        int raw = rawStride(cells);

        if (raw > previousStride) {
            long exitCapacity = percent(levelCapacity(previousStride), LOD_EXIT_PERCENT);
            if (cells <= exitCapacity) raw = previousStride;
        } else if (raw < previousStride) {
            long enterCapacity = percent(levelCapacity(raw), LOD_ENTER_PERCENT);
            if (cells > enterCapacity) raw = previousStride;
        }

        previousStride = raw;
        return raw;
    }

    /**
     * Exact x1 tiles start warming throughout the adjacent x2 band, not only immediately before x1.
     * This gives the worker an entire visual LOD level to make the child frame resident before it can
     * become visible, while still bounding speculative work by the configured near-detail quality.
     */
    static boolean detailWarmupUseful(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return false;
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        return cells <= levelCapacity(2);
    }

    private static int rawStride(long cells) {
        if (cells <= detailedCellBudget) return 1;

        int stride = 2;
        while (cells > levelCapacity(stride)) {
            if (stride >= (1 << 29)) return 1 << 30;
            stride <<= 1;
        }
        return stride;
    }

    /**
     * Maximum world-cell area represented by one LOD before it must become coarser.
     *
     * <p>The near-detail contribution falls linearly with stride ({@code detail * stride}), while the
     * ordinary overview budget scales with the number of cells represented by one sample
     * ({@code far * stride^2}). The maximum of both gives a monotonic nested ladder. With the default
     * 9k/6k tuning this reproduces the historical x1/x2/x4/x8 thresholds; with a raised Detailed range
     * it prevents skipped levels instead of silently exploding Far detail.</p>
     */
    private static long levelCapacity(int stride) {
        if (stride <= 1) return detailedCellBudget;
        long nearCapacity = saturatingMultiply(detailedCellBudget, stride);
        long strideSquared = saturatingMultiply(stride, stride);
        long farCapacity = saturatingMultiply(overviewSampleBudget, strideSquared);
        return Math.max(nearCapacity, farCapacity);
    }

    /**
     * Expands a clipped camera range to whole world-anchored LOD blocks. The returned range never
     * leaves the world. Keeping the block origin tied to world bounds prevents a one-cell camera move
     * from shifting every overview sample and rectangle on screen.
     */
    static VisualizerCamera.VisibleRange alignVisibleRange(
            VisualizerCamera.VisibleRange visible,
            WorldBounds bounds,
            int stride) {
        if (visible == null || bounds == null) {
            throw new IllegalArgumentException("LOD alignment requires visible range and bounds");
        }
        if (stride <= 1) return visible;

        int minX = alignedBlockStart(bounds.minX(), visible.minX(), stride);
        int minY = alignedBlockStart(bounds.minY(), visible.minY(), stride);
        int maxX = alignedBlockEnd(bounds.minX(), bounds.maxX(), visible.maxX(), stride);
        int maxY = alignedBlockEnd(bounds.minY(), bounds.maxY(), visible.maxY(), stride);
        return new VisualizerCamera.VisibleRange(minX, maxX, minY, maxY);
    }

    static VisualizerCamera.VisibleRange expandVisibleRange(
            VisualizerCamera.VisibleRange visible,
            WorldBounds bounds,
            int marginCells) {
        if (visible == null || bounds == null || marginCells < 0) {
            throw new IllegalArgumentException("LOD expansion requires range/bounds and non-negative margin");
        }
        return new VisualizerCamera.VisibleRange(
                Math.max(bounds.minX(), visible.minX() - marginCells),
                Math.min(bounds.maxX(), visible.maxX() + marginCells),
                Math.max(bounds.minY(), visible.minY() - marginCells),
                Math.min(bounds.maxY(), visible.maxY() + marginCells));
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

    static synchronized void detailedCellBudget(long value) {
        detailedCellBudget = requireRange(
                value,
                MIN_DETAILED_CELLS,
                MAX_DETAILED_CELLS,
                "detailed cell budget");
        previousStride = 1;
    }

    static synchronized void overviewSampleBudget(long value) {
        overviewSampleBudget = requireRange(
                value,
                MIN_OVERVIEW_SAMPLES,
                MAX_OVERVIEW_SAMPLES,
                "overview sample budget");
        previousStride = 1;
    }

    static synchronized void resetTuning() {
        detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
        overviewSampleBudget = DEFAULT_MAX_SAMPLES;
        previousStride = 1;
    }

    private static int alignedBlockStart(int worldMinimum, int coordinate, int stride) {
        long block = Math.floorDiv((long) coordinate - worldMinimum, stride);
        return Math.toIntExact((long) worldMinimum + block * stride);
    }

    private static int alignedBlockEnd(
            int worldMinimum,
            int worldMaximum,
            int coordinate,
            int stride) {
        long blockStart = alignedBlockStart(worldMinimum, coordinate, stride);
        return Math.toIntExact(Math.min((long) worldMaximum, blockStart + stride - 1L));
    }

    private static long percent(long value, int percent) {
        if (value == Long.MAX_VALUE || value > Long.MAX_VALUE / percent) return Long.MAX_VALUE;
        return value * percent / 100L;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left == 0L || right == 0L) return 0L;
        if (left > Long.MAX_VALUE / right) return Long.MAX_VALUE;
        return left * right;
    }

    private static long requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }
}
