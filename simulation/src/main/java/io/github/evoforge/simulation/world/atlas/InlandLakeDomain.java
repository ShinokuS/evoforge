package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable pre-relief inland-water membership authored independently from continent geometry. */
public final class InlandLakeDomain {
    private final WorldBounds bounds;
    private final int width;
    private final int cellCount;
    private final long[] lakeWords;
    private final int lakeCellCount;

    /**
     * Compacts one boolean per cell into immutable packed membership.
     *
     * <p>The source mask is not retained, so later caller mutation cannot affect this domain.</p>
     */
    InlandLakeDomain(WorldBounds bounds, boolean[] lake, int lakeCellCount) {
        if (bounds == null || lake == null) {
            throw new IllegalArgumentException("inland lake domain facts must not be null");
        }
        int expected = DenseElevationField.cellCount(bounds);
        if (lake.length != expected) {
            throw new IllegalArgumentException("inland lake domain must match world bounds");
        }
        if (lakeCellCount < 0 || lakeCellCount > expected) {
            throw new IllegalArgumentException("inland lake cell count must fit world bounds");
        }
        long[] words = new long[wordCount(expected)];
        int counted = 0;
        for (int index = 0; index < lake.length; index++) {
            if (!lake[index]) continue;
            words[index >>> 6] |= 1L << (index & 63);
            counted++;
        }
        if (counted != lakeCellCount) {
            throw new IllegalArgumentException("inland lake count must match membership mask");
        }
        this.bounds = bounds;
        this.width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        this.cellCount = expected;
        this.lakeWords = words;
        this.lakeCellCount = lakeCellCount;
    }

    private InlandLakeDomain(WorldBounds bounds, int width, int cellCount) {
        this.bounds = bounds;
        this.width = width;
        this.cellCount = cellCount;
        this.lakeWords = new long[wordCount(cellCount)];
        this.lakeCellCount = 0;
    }

    public static InlandLakeDomain empty(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int cellCount = DenseElevationField.cellCount(bounds);
        return new InlandLakeDomain(bounds, width, cellCount);
    }

    public WorldBounds bounds() {
        return bounds;
    }

    public int lakeCellCount() {
        return lakeCellCount;
    }

    public boolean isLakeAt(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException("coordinate outside inland lake domain");
        }
        int index = (y - bounds.minY()) * width + (x - bounds.minX());
        return isSet(index);
    }

    boolean isLakeIndex(int index) {
        if (index < 0 || index >= cellCount) {
            throw new IllegalArgumentException("inland lake index outside world domain");
        }
        return isSet(index);
    }

    private boolean isSet(int index) {
        return (lakeWords[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private boolean contains(int x, int y) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }

    private static int wordCount(int cellCount) {
        return Math.toIntExact(((long) cellCount + 63L) >>> 6);
    }
}
