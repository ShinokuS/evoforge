package io.github.evoforge.simulation.world.terrain;

/**
 * Internal scale-aware projection of the canonical Terrain surface for derived map representations.
 *
 * <p>The canonical source remains {@link ContinuousTerrainSurface#surfaceZAt(long, long)}. This
 * projection may analytically suppress structure that is smaller than the requested map sampling
 * interval so a coarse tile represents the same world without aliasing unresolved detail into
 * pixel noise. Camera/LOD never changes authoritative Terrain truth.</p>
 */
interface TerrainSurfaceMapObservation {
    double surfaceZForMapAt(long x, long y, long sampleSpacing);
}
