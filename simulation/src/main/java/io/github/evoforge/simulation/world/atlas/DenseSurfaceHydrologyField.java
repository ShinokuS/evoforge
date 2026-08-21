package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable implementation of generated surface-hydrology facts. */
final class DenseSurfaceHydrologyField implements SurfaceHydrologyField {
    private final WorldBounds bounds;
    private final int width;
    private final int cellCount;
    private final int[] initialWater;
    private final long[] shorelineWords;

    /** Copy-safe compatibility constructor for callers that retain their source arrays. */
    DenseSurfaceHydrologyField(
            WorldBounds bounds,
            int[] initialWater,
            boolean[] shoreline) {
        if (bounds == null || initialWater == null || shoreline == null) {
            throw new IllegalArgumentException("surface hydrology fields must not be null");
        }
        int width = width(bounds);
        int expected = cellCount(bounds, width);
        if (initialWater.length != expected || shoreline.length != expected) {
            throw new IllegalArgumentException(
                    "surface hydrology arrays must match horizontal world area");
        }
        long[] words = new long[wordCount(expected)];
        for (int index = 0; index < expected; index++) {
            if (shoreline[index]) setPacked(words, index);
        }
        validate(initialWater, words, expected);
        this.bounds = bounds;
        this.width = width;
        this.cellCount = expected;
        this.initialWater = initialWater.clone();
        this.shorelineWords = words;
    }

    /** Transfers exclusive ownership of freshly generated water and packed-shoreline buffers. */
    static DenseSurfaceHydrologyField takePackedOwnership(
            WorldBounds bounds,
            int[] initialWater,
            long[] shorelineWords) {
        if (bounds == null || initialWater == null || shorelineWords == null) {
            throw new IllegalArgumentException("surface hydrology fields must not be null");
        }
        int width = width(bounds);
        int expected = cellCount(bounds, width);
        if (initialWater.length != expected || shorelineWords.length != wordCount(expected)) {
            throw new IllegalArgumentException(
                    "surface hydrology arrays must match horizontal world area");
        }
        validate(initialWater, shorelineWords, expected);
        return new DenseSurfaceHydrologyField(
                bounds, width, expected, initialWater, shorelineWords);
    }

    static long[] newPackedShoreline(int cellCount) {
        if (cellCount < 0) throw new IllegalArgumentException("cellCount must be >= 0");
        return new long[wordCount(cellCount)];
    }

    static void setPacked(long[] words, int index) {
        words[index >>> 6] |= 1L << (index & 63);
    }

    private DenseSurfaceHydrologyField(
            WorldBounds bounds,
            int width,
            int cellCount,
            int[] initialWater,
            long[] shorelineWords) {
        this.bounds = bounds;
        this.width = width;
        this.cellCount = cellCount;
        this.initialWater = initialWater;
        this.shorelineWords = shorelineWords;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int initialWaterVolumeAt(int x, int y) {
        return initialWater[indexOf(x, y)];
    }

    @Override
    public boolean isShoreline(int x, int y) {
        int index = indexOf(x, y);
        return (shorelineWords[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "surface hydrology position outside world bounds: (" + x + ", " + y + ")");
        }
        int index = (y - bounds.minY()) * width + (x - bounds.minX());
        if (index < 0 || index >= cellCount) {
            throw new IllegalStateException("surface hydrology index escaped compact field bounds");
        }
        return index;
    }

    private static void validate(int[] initialWater, long[] shorelineWords, int cellCount) {
        for (int index = 0; index < cellCount; index++) {
            int amount = CellVolume.requireValid(initialWater[index]);
            boolean shoreline = (shorelineWords[index >>> 6] & (1L << (index & 63))) != 0L;
            if (amount > CellVolume.EMPTY && shoreline) {
                throw new IllegalArgumentException(
                        "a generated surface-water column cannot also be shoreline");
            }
        }
    }

    private static int width(WorldBounds bounds) {
        return Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
    }

    private static int cellCount(WorldBounds bounds, int width) {
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        return Math.multiplyExact(width, height);
    }

    private static int wordCount(int cellCount) {
        return Math.toIntExact(((long) cellCount + 63L) >>> 6);
    }
}
