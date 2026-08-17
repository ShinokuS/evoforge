package io.github.evoforge.simulation.world.calibration.rainfall;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Spatial preparation-time source of algorithm-independent rainfall occurrence statistics. */
public interface RainfallOccurrenceField {
    WorldBounds bounds();
    RainfallOccurrenceNormal at(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }

    static RainfallOccurrenceField uniform(
            WorldBounds bounds,
            RainfallOccurrenceNormal normal) {
        if (bounds == null || normal == null) {
            throw new IllegalArgumentException("uniform rainfall occurrence inputs must not be null");
        }
        return new RainfallOccurrenceField() {
            @Override public WorldBounds bounds() { return bounds; }

            @Override
            public RainfallOccurrenceNormal at(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException(
                            "position outside rainfall occurrence field: (" + x + ", " + y + ")");
                }
                return normal;
            }
        };
    }
}
