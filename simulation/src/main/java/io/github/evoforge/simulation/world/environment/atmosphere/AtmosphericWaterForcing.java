package io.github.evoforge.simulation.world.environment.atmosphere;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Runtime contract for atmospheric Water source/sink amounts over one simulation interval.
 *
 * <p>The producer owns whatever state and physical integration are required to obtain the current
 * interval amounts. Consumers do not inspect whether those amounts came from frozen climate
 * compatibility, current WeatherState, or another atmospheric model.</p>
 */
public interface AtmosphericWaterForcing {
    WorldBounds bounds();

    /** Advances authoritative forcing state exactly to the addressed positive simulation tick. */
    void advanceToTick(long tick);

    /** Whole CellVolume units of precipitation due for the most recently advanced interval. */
    long precipitationDueAt(int x, int y);

    /** Whole CellVolume units of evaporative demand due for the most recently advanced interval. */
    long evaporativeDemandDueAt(int x, int y);

    default boolean contains(int x, int y) {
        WorldBounds bounds = bounds();
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY();
    }
}
