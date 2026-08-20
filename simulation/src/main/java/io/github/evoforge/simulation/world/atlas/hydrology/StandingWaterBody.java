package io.github.evoforge.simulation.world.atlas.hydrology;

/** Immutable geometric summary of one accepted standing-water component. */
public record StandingWaterBody(
        int id,
        long cellCount,
        long shorelineEdgeCount,
        boolean touchesWorldBoundary,
        int minX,
        int maxX,
        int minY,
        int maxY) {

    public StandingWaterBody {
        if (id < 0) throw new IllegalArgumentException("standing-water body id must be non-negative");
        if (cellCount <= 0L) throw new IllegalArgumentException("standing-water body must contain cells");
        if (shorelineEdgeCount < 0L) {
            throw new IllegalArgumentException("shoreline edge count must be non-negative");
        }
        if (maxX < minX || maxY < minY) {
            throw new IllegalArgumentException("standing-water body bounds must be ordered");
        }
    }
}
