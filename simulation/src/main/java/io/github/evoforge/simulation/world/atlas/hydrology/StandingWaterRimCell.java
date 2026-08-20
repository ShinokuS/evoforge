package io.github.evoforge.simulation.world.atlas.hydrology;

/** One dry rim-cell relationship around a standing-water body. */
public record StandingWaterRimCell(
        int bodyId,
        int x,
        int y,
        long elevationSubunits,
        int adjacentWaterEdgeCount,
        int dryNeighborCount,
        boolean touchesWorldBoundary) {

    public StandingWaterRimCell {
        if (bodyId < 0) throw new IllegalArgumentException("rim body id must be non-negative");
        if (elevationSubunits < 0L) {
            throw new IllegalArgumentException("standing-water rim cell must be dry/non-negative");
        }
        if (adjacentWaterEdgeCount <= 0 || adjacentWaterEdgeCount > 4) {
            throw new IllegalArgumentException("rim cell must touch its water body by cardinal edge");
        }
        if (dryNeighborCount < 0 || dryNeighborCount > 4) {
            throw new IllegalArgumentException("dry-neighbor count must fit cardinal neighborhood");
        }
    }

    /** True when the rim cell has at least one in-world dry continuation beyond the water edge. */
    public boolean hasDryContinuation() {
        return dryNeighborCount > 0;
    }
}
