package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/** Immutable pre-relief inland-water membership authored independently from continent geometry. */
public final class InlandLakeDomain {
    private final WorldBounds bounds;
    private final boolean[] lake;
    private final int lakeCellCount;

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
        int counted = 0;
        for (boolean cell : lake) {
            if (cell) counted++;
        }
        if (counted != lakeCellCount) {
            throw new IllegalArgumentException("inland lake count must match membership mask");
        }
        this.bounds = bounds;
        this.lake = Arrays.copyOf(lake, lake.length);
        this.lakeCellCount = lakeCellCount;
    }

    public static InlandLakeDomain empty(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        return new InlandLakeDomain(bounds, new boolean[DenseElevationField.cellCount(bounds)], 0);
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
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int index = (y - bounds.minY()) * width + (x - bounds.minX());
        return lake[index];
    }

    boolean isLakeIndex(int index) {
        if (index < 0 || index >= lake.length) {
            throw new IllegalArgumentException("inland lake index outside world domain");
        }
        return lake[index];
    }

    private boolean contains(int x, int y) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
