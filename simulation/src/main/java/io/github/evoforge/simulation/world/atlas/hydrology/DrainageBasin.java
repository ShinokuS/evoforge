package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * One closed terrain depression and the minimum elevation at which it spills into a downstream
 * drainage path.
 *
 * <p>The basin is a terrain/topology fact only. It does not imply that water is actually present.
 */
public record DrainageBasin(
        int id,
        long cellCount,
        long spillElevationSubunits,
        long maximumDepthSubunits,
        int minX,
        int maxX,
        int minY,
        int maxY) {

    public DrainageBasin {
        if (id < 0) throw new IllegalArgumentException("drainage basin id must be non-negative");
        if (cellCount <= 0L) throw new IllegalArgumentException("drainage basin must contain cells");
        if (spillElevationSubunits < 0L) {
            throw new IllegalArgumentException("drainage basin spill must be at or above sea level");
        }
        if (maximumDepthSubunits <= 0L) {
            throw new IllegalArgumentException("drainage basin depth must be positive");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("drainage basin bounds must be ordered");
        }
    }
}
