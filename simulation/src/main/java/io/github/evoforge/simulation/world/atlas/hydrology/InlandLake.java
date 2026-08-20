package io.github.evoforge.simulation.world.atlas.hydrology;

/** One generated inland standing-water body with an explicit local water-surface elevation. */
public record InlandLake(
        int id,
        int sourceBasinId,
        long cellCount,
        long surfaceElevationSubunits,
        long maximumDepthSubunits,
        int minX,
        int maxX,
        int minY,
        int maxY) {

    public InlandLake {
        if (id < 0 || sourceBasinId < 0) {
            throw new IllegalArgumentException("inland lake ids must be non-negative");
        }
        if (cellCount <= 0L) throw new IllegalArgumentException("inland lake must contain cells");
        if (surfaceElevationSubunits < 0L) {
            throw new IllegalArgumentException("inland lake surface must be at or above sea level");
        }
        if (maximumDepthSubunits <= 0L) {
            throw new IllegalArgumentException("inland lake depth must be positive");
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("inland lake bounds must be ordered");
        }
    }
}
