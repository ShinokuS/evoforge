package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable read contract for generated surface elevation by global world column. */
public interface ElevationField {
    long SUBUNITS_PER_CELL = 1_000_000L;

    WorldBounds bounds();

    /** Discrete surface-cell Z used by terrain materialization. */
    int elevationAt(int x, int y);

    /**
     * Precise generated elevation used by macro world facts such as drainage.
     *
     * <p>The default preserves compatibility for substitute algorithms that only
     * author discrete cell elevation. Precision-aware algorithms override it.
     */
    default long elevationSubunitsAt(int x, int y) {
        return Math.multiplyExact((long) elevationAt(x, y), SUBUNITS_PER_CELL);
    }

    /**
     * Fills a regular Cartesian sample grid in row-major order.
     *
     * <p>The default deliberately delegates to the point contract, preserving compatibility for
     * simple fields. Page-backed Continuum fields override this method so a bounded preview grid can
     * be materialized as one terrain request instead of thousands of unrelated page lookups.</p>
     */
    default void fillElevationSubunits(
            int minX,
            int minY,
            int sampleWidth,
            int sampleHeight,
            long step,
            long[] target) {
        if (sampleWidth <= 0 || sampleHeight <= 0 || step <= 0L || target == null
                || target.length < Math.multiplyExact(sampleWidth, sampleHeight)) {
            throw new IllegalArgumentException("elevation sample grid dimensions/output are invalid");
        }
        long maxX = Math.addExact((long) minX, Math.multiplyExact((long) sampleWidth - 1L, step));
        long maxY = Math.addExact((long) minY, Math.multiplyExact((long) sampleHeight - 1L, step));
        if (maxX < Integer.MIN_VALUE || maxX > Integer.MAX_VALUE
                || maxY < Integer.MIN_VALUE || maxY > Integer.MAX_VALUE
                || !contains(minX, minY)
                || !contains((int) maxX, (int) maxY)) {
            throw new IllegalArgumentException("elevation sample grid lies outside field bounds");
        }

        int cursor = 0;
        for (int sampleY = 0; sampleY < sampleHeight; sampleY++) {
            int y = Math.toIntExact((long) minY + sampleY * step);
            for (int sampleX = 0; sampleX < sampleWidth; sampleX++, cursor++) {
                int x = Math.toIntExact((long) minX + sampleX * step);
                target[cursor] = elevationSubunitsAt(x, y);
            }
        }
    }

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
