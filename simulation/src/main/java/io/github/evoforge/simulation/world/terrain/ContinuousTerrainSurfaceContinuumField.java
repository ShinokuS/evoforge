package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;

/** Read-only adapter from continuous Terrain Z to the normalized Continuum map scalar contract. */
public final class ContinuousTerrainSurfaceContinuumField implements ContinuumScalarField {
    private static final double DIAGNOSTIC_HALF_RANGE_Z = 4_096.0d;
    private static final double DIAGNOSTIC_GAMMA = 0.62d;

    private final ContinuousTerrainSurface surface;

    public ContinuousTerrainSurfaceContinuumField(ContinuousTerrainSurface surface) {
        if (surface == null) throw new IllegalArgumentException("surface must not be null");
        this.surface = surface;
    }

    @Override
    public double sample(long x, long y) {
        double z = surface.surfaceZAt(x, y);
        if (!Double.isFinite(z)) return 0.5d;

        // The map is diagnostic presentation, not Terrain truth. A signed gamma transfer preserves
        // the sea datum at exactly 0.5 while giving ordinary 50..1000 Z relief substantially more
        // contrast than a linear mapping across the full +/-4096 safety range. This makes finer
        // deterministic structure visibly emerge as the map requests finer Continuum levels.
        double magnitude = Math.min(1.0d, Math.abs(z) / DIAGNOSTIC_HALF_RANGE_Z);
        double contrast = Math.pow(magnitude, DIAGNOSTIC_GAMMA);
        double normalized = 0.5d + Math.copySign(0.5d * contrast, z);
        return Math.max(0.0d, Math.min(1.0d, normalized));
    }
}
