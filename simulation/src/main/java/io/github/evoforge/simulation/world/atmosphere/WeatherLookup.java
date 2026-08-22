package io.github.evoforge.simulation.world.atmosphere;

import io.github.evoforge.simulation.world.space.WorldBounds;

/** Read-only current-weather capability exposed outside the authoritative mutable state owner. */
public interface WeatherLookup {
    WorldBounds bounds();

    WeatherCellState at(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
