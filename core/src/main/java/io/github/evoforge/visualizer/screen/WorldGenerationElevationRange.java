package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Actual generated land-height and submerged-depth ranges used only by preview color normalization. */
record WorldGenerationElevationRange(
        long minimumSubunits,
        long maximumSubunits,
        long minimumWaterSubunits) {
    WorldGenerationElevationRange {
        if (maximumSubunits < minimumSubunits) {
            throw new IllegalArgumentException("maximum land elevation must be >= minimum land elevation");
        }
        if (minimumWaterSubunits > 0L) {
            throw new IllegalArgumentException("minimum water elevation must be <= sea level");
        }
    }

    WorldGenerationElevationRange(long minimumSubunits, long maximumSubunits) {
        this(minimumSubunits, maximumSubunits, 0L);
    }

    static WorldGenerationElevationRange from(ElevationField elevation) {
        if (elevation == null) throw new IllegalArgumentException("elevation must not be null");
        WorldBounds bounds = elevation.bounds();
        long minimumLand = Long.MAX_VALUE;
        long maximumLand = Long.MIN_VALUE;
        long minimumWater = 0L;
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                long value = elevation.elevationSubunitsAt(x, y);
                if (value < 0L) {
                    minimumWater = Math.min(minimumWater, value);
                    continue;
                }
                minimumLand = Math.min(minimumLand, value);
                maximumLand = Math.max(maximumLand, value);
            }
        }
        if (minimumLand == Long.MAX_VALUE) {
            minimumLand = 0L;
            maximumLand = 0L;
        }
        return new WorldGenerationElevationRange(minimumLand, maximumLand, minimumWater);
    }
}
