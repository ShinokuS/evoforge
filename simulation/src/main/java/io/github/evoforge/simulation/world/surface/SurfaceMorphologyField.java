package io.github.evoforge.simulation.world.surface;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Immutable local topographic facts derived from generated elevation.
 *
 * <p>Values use the same precise elevation subunits as the source elevation field. They describe
 * geometry only; deposition, Soil, erosion and material algorithms remain free to interpret these
 * facts through their own model parameters.</p>
 */
public interface SurfaceMorphologyField {
    WorldBounds bounds();

    /** Largest absolute elevation difference to an in-bounds neighboring column. */
    long maximumNeighborSlopeSubunitsAt(int x, int y);

    /** Positive difference between mean neighbor elevation and this column, otherwise zero. */
    long concavitySubunitsAt(int x, int y);
}
