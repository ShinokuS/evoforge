package io.github.evoforge.simulation.world.terrain.field;

/** Precise generated terrain elevation in legacy one-millionth-of-a-cell subunits. */
@FunctionalInterface
public interface TerrainElevationField {
    long SUBUNITS_PER_CELL = 1_000_000L;

    long elevationSubunitsAt(long x, long y);
}
