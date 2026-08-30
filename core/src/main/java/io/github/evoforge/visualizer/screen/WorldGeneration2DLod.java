package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/**
 * Pure presentation sampling policy for the V15 inspector.
 *
 * <p>The user-facing controls are deliberately axis distances, not opaque area budgets. Detailed
 * range is the visible world span at which exact x1 becomes eligible. Far detail is the target number
 * of overview samples along the longest visible axis. Overview strides remain nested powers of two.
 * Normal camera zooms move by at most one LOD level per rendered frame; changing a performance slider
 * resets hysteresis so the new setting takes effect immediately on the current viewport.</p>
 */
final class WorldGeneration2DLod {
    static final int DEFAULT_DETAILED_RANGE_CELLS = 96;
    static final int MIN_DETAILED_RANGE_CELLS = 32;
    static final int MAX_DETAILED_RANGE_CELLS = 512;

    static final int DEFAULT_OVERVIEW_SAMPLES_PER_AXIS = 80;
    static final int MIN_OVERVIEW_SAMPLES_PER_AXIS = 32;
    static final int MAX_OVERVIEW_SAMPLES_PER_AXIS = 256;

    /**
     * Existing settings-panel plumbing still speaks long "budget" values. Keep that API temporarily,
     * but make it a linear fixed-point representation instead of the old area budget: 100 slider
     * units equal one visible cell/sample along an axis. The UI's existing 500-unit step therefore
     * changes the actual distance by exactly five cells everywhere on the slider.
     */
    private static final long LEGACY_SLIDER_UNITS_PER_AXIS = 100L;
    static final long DEFAULT_MAX_DETAILED_CELLS =
            DEFAULT_DETAILED_RANGE_CELLS * LEGACY_SLIDER_UNITS_PER_AXIS;
    static final long DEFAULT_MAX_SAMPLES =
            DEFAULT_OVERVIEW_SAMPLES_PER_AXIS * LEGACY_SLIDER_UNITS_PER_AXIS;
    static final long MIN_DETAILED_CELLS =
            MIN_DETAILED_RANGE_CELLS * LEGACY_SLIDER_UNITS_PER_AXIS;
    static final long MAX_DETAILED_CELLS =
            MAX_DETAILED_RANGE_CELLS * LEGACY_SLIDER_UNITS_PER_AXIS;
    static final long MIN_OVERVIEW_SAMPLES =
            MIN_OVERVIEW_SAMPLES_PER_AXIS * LEGACY_SLIDER_UNITS_PER_AXIS;
    static final long MAX_OVERVIEW_SAMPLES =
            MAX_OVERVIEW_SAMPLES_PER_AXIS * LEGACY_SLIDER_UNITS_PER_AXIS;

    /** Small dead-band for wheel noise; deliberately much smaller than the old 15% area band. */
    private static final int LOD_EXIT_PERCENT = 108;
    private static final int LOD_ENTER_PERCENT = 92;

    private static volatile int detailedRangeCells = DEFAULT_DETAILED_RANGE_CELLS;
    private static volatile int overviewSamplesPerAxis = DEFAULT_OVERVIEW_SAMPLES_PER_AXIS;
    /** Zero means the next call must apply tuning without hysteresis. */
    private static volatile int previousStride;

    private WorldGeneration2DLod() {
    }

    static synchronized int stride(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) {
            previousStride = 0;
            return 1;
        }
        int span = Math.max(widthCells, lengthCells);
        int raw = rawStride(span);

        if (previousStride == 0) {
            previousStride = raw;
            return raw;
        }

        if (raw > previousStride) {
            long exitSpan = percent(levelMaximumSpan(previousStride), LOD_EXIT_PERCENT);
            if (span <= exitSpan) return previousStride;
            raw = nextCoarser(previousStride, raw);
        } else if (raw < previousStride) {
            int child = nextFiner(previousStride, raw);
            long enterSpan = percent(levelMaximumSpan(child), LOD_ENTER_PERCENT);
            if (span > enterSpan) return previousStride;
            raw = child;
        }

        previousStride = raw;
        return raw;
    }

    /**
     * Starts exact-tile residency throughout a deterministic band around the x1 threshold. This is
     * based solely on Detailed range, so changing Far detail cannot accidentally disable x1 prewarm.
     */
    static boolean detailWarmupUseful(int widthCells, int lengthCells) {
        if (widthCells <= 0 || lengthCells <= 0) return false;
        int span = Math.max(widthCells, lengthCells);
        return span <= Math.multiplyExact(detailedRangeCells, 2);
    }

    private static int rawStride(int spanCells) {
        if (spanCells <= detailedRangeCells) return 1;
        long required = divideCeil(spanCells, overviewSamplesPerAxis);
        int stride = 2;
        while (stride < required) {
            if (stride >= (1 << 29)) return 1 << 30;
            stride <<= 1;
        }
        return stride;
    }

    private static long levelMaximumSpan(int stride) {
        if (stride <= 1) return detailedRangeCells;
        return Math.multiplyExact((long) overviewSamplesPerAxis, stride);
    }

    private static int nextCoarser(int previous, int target) {
        if (previous <= 0 || previous >= target) return target;
        if (previous >= (1 << 29)) return target;
        return Math.min(target, previous << 1);
    }

    private static int nextFiner(int previous, int target) {
        if (previous <= target) return target;
        return Math.max(target, previous >> 1);
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

    static int detailedRangeCells() {
        return detailedRangeCells;
    }

    static int overviewSamplesPerAxis() {
        return overviewSamplesPerAxis;
    }

    static synchronized void detailedRangeCells(int value) {
        detailedRangeCells = requireRange(
                value,
                MIN_DETAILED_RANGE_CELLS,
                MAX_DETAILED_RANGE_CELLS,
                "detailed range");
        previousStride = 0;
    }

    static synchronized void overviewSamplesPerAxis(int value) {
        overviewSamplesPerAxis = requireRange(
                value,
                MIN_OVERVIEW_SAMPLES_PER_AXIS,
                MAX_OVERVIEW_SAMPLES_PER_AXIS,
                "overview samples per axis");
        previousStride = 0;
    }

    static long detailedCellBudget() {
        return detailedRangeCells * LEGACY_SLIDER_UNITS_PER_AXIS;
    }

    static long overviewSampleBudget() {
        return overviewSamplesPerAxis * LEGACY_SLIDER_UNITS_PER_AXIS;
    }

    static synchronized void detailedCellBudget(long value) {
        long checked = requireRange(value, MIN_DETAILED_CELLS, MAX_DETAILED_CELLS, "detailed slider value");
        detailedRangeCells(axisFromLegacySlider(checked));
    }

    static synchronized void overviewSampleBudget(long value) {
        long checked = requireRange(value, MIN_OVERVIEW_SAMPLES, MAX_OVERVIEW_SAMPLES, "overview slider value");
        overviewSamplesPerAxis(axisFromLegacySlider(checked));
    }

    static synchronized void resetTuning() {
        detailedRangeCells = DEFAULT_DETAILED_RANGE_CELLS;
        overviewSamplesPerAxis = DEFAULT_OVERVIEW_SAMPLES_PER_AXIS;
        previousStride = 0;
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
        if (value > Long.MAX_VALUE / percent) return Long.MAX_VALUE;
        return value * percent / 100L;
    }

    private static long divideCeil(long value, long divisor) {
        return Math.floorDiv(value - 1L, divisor) + 1L;
    }

    private static int axisFromLegacySlider(long value) {
        return Math.toIntExact(Math.round(value / (double) LEGACY_SLIDER_UNITS_PER_AXIS));
    }

    private static int requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }

    private static long requireRange(long value, long minimum, long maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }
}
