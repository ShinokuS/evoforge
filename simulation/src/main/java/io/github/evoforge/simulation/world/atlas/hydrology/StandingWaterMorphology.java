package io.github.evoforge.simulation.world.atlas.hydrology;

/** Broad geometric facts for one hydrologically significant standing-water body. */
public record StandingWaterMorphology(
        int bodyId,
        int maximumInteriorClearanceCells,
        long worldBoundaryEdgeCount) {

    public StandingWaterMorphology {
        if (bodyId < 0) throw new IllegalArgumentException("morphology body id must be non-negative");
        if (maximumInteriorClearanceCells <= 0) {
            throw new IllegalArgumentException("standing-water clearance must be positive");
        }
        if (worldBoundaryEdgeCount < 0L) {
            throw new IllegalArgumentException("world-boundary edge count must be non-negative");
        }
    }
}
