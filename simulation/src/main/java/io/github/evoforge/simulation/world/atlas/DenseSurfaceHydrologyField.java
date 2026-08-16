package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable implementation of generated surface-hydrology facts. */
final class DenseSurfaceHydrologyField implements SurfaceHydrologyField {
    private final WorldBounds bounds;
    private final int width;
    private final int[] initialWater;
    private final boolean[] shoreline;

    DenseSurfaceHydrologyField(
            WorldBounds bounds,
            int[] initialWater,
            boolean[] shoreline) {
        if (bounds == null || initialWater == null || shoreline == null) {
            throw new IllegalArgumentException("surface hydrology fields must not be null");
        }
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int expected = Math.multiplyExact(width, height);
        if (initialWater.length != expected || shoreline.length != expected) {
            throw new IllegalArgumentException(
                    "surface hydrology arrays must match horizontal world area");
        }
        for (int amount : initialWater) CellVolume.requireValid(amount);
        for (int index = 0; index < expected; index++) {
            if (initialWater[index] > CellVolume.EMPTY && shoreline[index]) {
                throw new IllegalArgumentException(
                        "a generated surface-water column cannot also be shoreline");
            }
        }
        this.bounds = bounds;
        this.width = width;
        this.initialWater = initialWater.clone();
        this.shoreline = shoreline.clone();
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
        return shoreline[indexOf(x, y)];
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "surface hydrology position outside world bounds: (" + x + ", " + y + ")");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }
}
