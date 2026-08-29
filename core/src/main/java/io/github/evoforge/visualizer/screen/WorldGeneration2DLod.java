package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/**
 * Pure sampling policy that caps 2D preview work while keeping the closest inspection exact.
 *
 * <p>Overview strides are powers of two. Combined with world-anchored sampling this makes adjacent
 * LODs nested instead of rebuilding the whole visible lattice at every integer stride. The budgets
 * affect presentation only and never world generation or provenance.</p>
 */
final class WorldGeneration2DLod {
    static final long DEFAULT_MAX_DETAILED_CELLS = 128L;
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
