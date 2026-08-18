package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Actual generated land-height range used only by preview color normalization. */
record WorldGenerationElevationRange(long minimumSubunits, long maximumSubunits) {
    WorldGenerationElevationRange {
        if (maximumSubunits < minimumSubunits) {
            throw new IllegalArgumentException("maximum elevation must be >= minimum elevation");
        }
    }

    static WorldGenerationElevationRange from(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
        WorldBounds bounds = elevation.bounds();
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                long value = elevation.elevationSubunitsAt(x, y);
                if (value < 0L) continue;
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
            }
        }
        if (minimum == Long.MAX_VALUE) return new WorldGenerationElevationRange(0L, 0L);
        return new WorldGenerationElevationRange(minimum, maximum);
    }
}
