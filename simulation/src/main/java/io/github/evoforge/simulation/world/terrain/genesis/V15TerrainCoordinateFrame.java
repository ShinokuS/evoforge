package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/** Maps modern zero-based Continuum addresses to the centered XY coordinates used by the V15 oracle. */
public record V15TerrainCoordinateFrame(
        ContinuumWorldDomain domain,
        long legacyMinX,
        long legacyMinY) {

    public V15TerrainCoordinateFrame {
        if (domain == null) throw new IllegalArgumentException("domain must not be null");
    }

    /** Matches the old world-generation visualizer bounds: {@code min = -size / 2}. */
    public static V15TerrainCoordinateFrame centered(ContinuumWorldDomain domain) {
        if (domain == null) throw new IllegalArgumentException("domain must not be null");
        return new V15TerrainCoordinateFrame(
                domain,
                -domain.width() / 2L,
                -domain.height() / 2L);
    }

    public long legacyX(long continuumX) {
        requireCoordinate(continuumX, 0L, true);
        return Math.addExact(legacyMinX, continuumX);
    }

    public long legacyY(long continuumY) {
        requireCoordinate(0L, continuumY, false);
        return Math.addExact(legacyMinY, continuumY);
    }

    public long cellIndex(long continuumX, long continuumY) {
        if (!domain.contains(continuumX, continuumY)) {
            throw new IllegalArgumentException("coordinate lies outside the terrain frame");
        }
        return Math.addExact(Math.multiplyExact(continuumY, domain.width()), continuumX);
    }

    private void requireCoordinate(long x, long y, boolean checkX) {
        boolean valid = checkX ? x >= 0L && x < domain.width() : y >= 0L && y < domain.height();
        if (!valid) throw new IllegalArgumentException("coordinate lies outside the terrain frame");
    }
}
