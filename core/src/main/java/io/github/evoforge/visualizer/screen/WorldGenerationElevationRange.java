package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Actual/representative generated elevation ranges used only by preview color normalization. */
record WorldGenerationElevationRange(
        long minimumSubunits,
        long maximumSubunits,
        long minimumWaterSubunits) {
    private static final long MAX_EXACT_RANGE_CELLS = 512L * 512L;
    private static final int MAX_SAMPLED_AXIS = 160;

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
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long length = (long) bounds.maxY() - bounds.minY() + 1L;
        long area = Math.multiplyExact(width, length);
        if (area <= MAX_EXACT_RANGE_CELLS) {
            return exact(elevation, bounds);
        }
        return from(WorldGenerationElevationGrid.sample(elevation, MAX_SAMPLED_AXIS));
    }

    static WorldGenerationElevationRange from(WorldGenerationElevationGrid grid) {
        if (grid == null) throw new IllegalArgumentException("elevation grid must not be null");
        long minimumLand = Long.MAX_VALUE;
        long maximumLand = Long.MIN_VALUE;
        long minimumWater = 0L;
        for (int y = 0; y < grid.height(); y++) {
            for (int x = 0; x < grid.width(); x++) {
                long value = grid.elevationSubunitsAt(x, y);
                if (value < 0L) {
                    minimumWater = Math.min(minimumWater, value);
                    continue;
                }
                minimumLand = Math.min(minimumLand, value);
                maximumLand = Math.max(maximumLand, value);
            }
        }
        return finish(minimumLand, maximumLand, minimumWater);
    }

    private static WorldGenerationElevationRange exact(ElevationField elevation, WorldBounds bounds) {
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
        return finish(minimumLand, maximumLand, minimumWater);
    }

    private static WorldGenerationElevationRange finish(
            long minimumLand,
            long maximumLand,
            long minimumWater) {
        if (minimumLand == Long.MAX_VALUE) {
            minimumLand = 0L;
            maximumLand = 0L;
        }
        return new WorldGenerationElevationRange(minimumLand, maximumLand, minimumWater);
    }
}
