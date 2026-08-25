package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;

/** Read-only adapter from continuous Terrain Z to the normalized Continuum map scalar contract. */
public final class ContinuousTerrainSurfaceContinuumField implements ContinuumScalarField {
    private static final double DIAGNOSTIC_HALF_RANGE_Z = 4_096.0d;

    private final ContinuousTerrainSurface surface;

    public ContinuousTerrainSurfaceContinuumField(ContinuousTerrainSurface surface) {
        if (surface == null) throw new IllegalArgumentException("surface must not be null");
        this.surface = surface;
    }

    @Override
    public double sample(long x, long y) {
        double normalized = 0.5d + surface.surfaceZAt(x, y) / (2.0d * DIAGNOSTIC_HALF_RANGE_Z);
        return Math.max(0.0d, Math.min(1.0d, normalized));
    }
}
