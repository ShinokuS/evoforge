package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class DenseElevationField implements ElevationField {
    private final WorldBounds bounds;
    private final int width;
    private final int[] elevations;

    DenseElevationField(WorldBounds bounds, int[] elevations) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (elevations == null) {
            throw new IllegalArgumentException("elevations must not be null");
        }
        int expected = cellCount(bounds);
        if (elevations.length != expected) {
            throw new IllegalArgumentException(
                    "elevation count must match horizontal world area: expected "
                            + expected + ", got " + elevations.length);
        }
        this.bounds = bounds;
        this.width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        this.elevations = elevations.clone();
    }

    static int cellCount(WorldBounds bounds) {
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        long area;
        try {
            area = Math.multiplyExact(width, height);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "horizontal world area exceeds current elevation representation", exception);
        }
        if (area > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "horizontal world area exceeds current elevation representation: " + area);
        }
        return (int) area;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int elevationAt(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside elevation field: (" + x + ", " + y + ")");
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return elevations[localY * width + localX];
    }
}
