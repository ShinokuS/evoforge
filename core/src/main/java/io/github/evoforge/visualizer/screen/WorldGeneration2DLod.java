package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/**
 * Pure presentation sampling policy for the V15 inspector.
 *
 * <p>Overview strides are nested powers of two. Cell-detail x1 is allowed across a genuinely useful
 * regional viewport because authoritative data is now prepared by a bounded asynchronous tile cache,
 * not by synchronous per-cell Continuum reads. The detail budget is therefore a live presentation
 * quality knob rather than a safety switch for simulation work.</p>
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
    private static final int DETAIL_EXIT_PERCENT = 125;

    private static volatile long detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
    private static volatile long overviewSampleBudget = DEFAULT_MAX_SAMPLES;
    private static volatile int previousStride = 1;

    private WorldGeneration2DLod() {
    }

    /**
     * Chooses a nested presentation LOD with x1 hysteresis. A small zoom oscillation around the
     * detail threshold cannot repeatedly switch the same world area between x1 and x2.
     */
    static synchronized int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) {
            previousStride = 1;
            return 1;
        }
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        int raw = rawStride(cells);
        if (previousStride == 1 && raw > 1) {
            long exitBudget = Math.multiplyExact(detailedCellBudget, DETAIL_EXIT_PERCENT) / 100L;
            if (cells <= exitBudget) raw = 1;
        }
        previousStride = raw;
        return raw;
    }

    static boolean detailWarmupUseful(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return false;
        long cells = Math.multiplyExact((long) widthCells, (long) lengthCells);
        return cells <= Math.multiplyExact(detailedCellBudget, 3L) / 2L;
    }

    private static int rawStride(long cells) {
        if (cells <= detailedCellBudget) return 1;
        int requiredStride = Math.max(
                2,
                (int) Math.ceil(Math.sqrt(cells / (double) overviewSampleBudget)));
        return nextPowerOfTwo(requiredStride);
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
    }

    static synchronized void resetTuning() {
        detailedCellBudget = DEFAULT_MAX_DETAILED_CELLS;
        overviewSampleBudget = DEFAULT_MAX_SAMPLES;
        previousStride = 1;
    }

    private static int nextPowerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            if (result > (1 << 29)) return 1 << 30;
            result <<= 1;
        }
        return result;
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

    private static long requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }
}
