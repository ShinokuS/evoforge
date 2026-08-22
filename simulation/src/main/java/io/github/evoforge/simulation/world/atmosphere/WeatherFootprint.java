package io.github.evoforge.simulation.world.atmosphere;

import io.github.evoforge.simulation.world.space.WorldBounds;

/**
 * Rectangular XY footprint controlled by one coherent weather process.
 *
 * <p>This is a runtime geometry primitive, not the project's semantic macro Region type.</p>
 */
public record WeatherFootprint(int minX, int maxX, int minY, int maxY) {

    public WeatherFootprint {
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("weather footprint minimums must not exceed maximums");
        }
    }

    public static WeatherFootprint whole(WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("world bounds must not be null");
        }
        return new WeatherFootprint(bounds.minX(), bounds.maxX(), bounds.minY(), bounds.maxY());
    }

    public boolean contains(int x, int y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    public void requireInside(WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("world bounds must not be null");
        }
        if (minX < bounds.minX()
                || maxX > bounds.maxX()
                || minY < bounds.minY()
                || maxY > bounds.maxY()) {
            throw new IllegalArgumentException("weather footprint must lie inside weather-state bounds");
        }
    }
}
