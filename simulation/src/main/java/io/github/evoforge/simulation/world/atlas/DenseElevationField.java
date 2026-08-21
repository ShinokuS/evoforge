package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class DenseElevationField implements ElevationField {
    private final WorldBounds bounds;
    private final int width;
    private final long[] elevationSubunits;

    /** Copy-safe constructor for callers that retain ownership of their input buffer. */
    DenseElevationField(WorldBounds bounds, long[] elevationSubunits) {
        this(bounds, elevationSubunits, true);
    }

    /**
     * Transfers exclusive ownership of a freshly allocated generation buffer without cloning it.
     *
     * <p>The caller must never mutate the array again after this call.</p>
     */
    static DenseElevationField takeOwnership(WorldBounds bounds, long[] elevationSubunits) {
        return new DenseElevationField(bounds, elevationSubunits, false);
    }

    private DenseElevationField(
            WorldBounds bounds,
            long[] elevationSubunits,
            boolean copyArray) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (elevationSubunits == null) {
            throw new IllegalArgumentException("elevationSubunits must not be null");
        }
        int expected = cellCount(bounds);
        if (elevationSubunits.length != expected) {
            throw new IllegalArgumentException(
                    "elevation count must match horizontal world area: expected "
                            + expected + ", got " + elevationSubunits.length);
        }
        this.bounds = bounds;
        this.width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        this.elevationSubunits = copyArray ? elevationSubunits.clone() : elevationSubunits;
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

    /**
     * Internal zero-copy read path for package-local generation stages.
     *
     * <p>The returned storage remains owned by this immutable field and must never be mutated. It is
     * intentionally package-private so public consumers cannot bypass the field contract.</p>
     */
    long[] readOnlyStorage() {
        return elevationSubunits;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int elevationAt(int x, int y) {
        return Math.toIntExact(Math.floorDiv(
                elevationSubunitsAt(x, y),
                SUBUNITS_PER_CELL));
    }

    @Override
    public long elevationSubunitsAt(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "position outside elevation field: (" + x + ", " + y + ")");
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return elevationSubunits[localY * width + localX];
    }
}
