package io.github.evoforge.simulation.world.geophysics;

/**
 * Authoritative continuous macro-geophysical skeleton of the world.
 *
 * <p>Elevation is a dimensionless signed macro value around a fixed sea datum of zero. It is not
 * exact XYZ terrain height. Ocean/land is derived from this same elevation fact rather than from an
 * independent painter.
 */
public interface MacroGeophysicalField {
    double SEA_DATUM = 0.0d;

    /** Returns signed macro elevation in the closed range [-1, 1]. */
    double elevationAt(long x, long y);

    /** Ocean is a consequence of macro elevation falling below the shared sea datum. */
    default boolean isOceanAt(long x, long y) {
        return elevationAt(x, y) < SEA_DATUM;
    }
}
