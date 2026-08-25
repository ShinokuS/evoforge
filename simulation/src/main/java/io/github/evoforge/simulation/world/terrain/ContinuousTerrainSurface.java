package io.github.evoforge.simulation.world.terrain;

/**
 * Canonical generated continuous Terrain surface before exact XYZ materialization.
 *
 * <p>The value is continuous Z in logical world-cell coordinates around the shared sea datum. The
 * surface is authoritative Genesis geometry for later drainage/refinement/materialization stages;
 * it is not the integer runtime {@link TerrainSurfaceLookup} projection.</p>
 */
@FunctionalInterface
public interface ContinuousTerrainSurface {
    double SEA_DATUM = 0.0d;

    /** Returns continuous Terrain-surface Z at the addressed XY world coordinate. */
    double surfaceZAt(long x, long y);

    /** Submergence is derived from the one surface rather than from an independent ocean painter. */
    default boolean isSubmergedAt(long x, long y) {
        return surfaceZAt(x, y) < SEA_DATUM;
    }
}
