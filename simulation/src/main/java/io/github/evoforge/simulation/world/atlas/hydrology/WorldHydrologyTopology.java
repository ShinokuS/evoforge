package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Stage 2B aggregate over final authoritative terrain. Terrain morphology is upstream; this bundle
 * contains only standing-water analysis, closed drainage basins and generated inland lakes.
 */
public record WorldHydrologyTopology(
        StandingWaterHydrologyTopology standingWaterTopology,
        DrainageBasinTopology drainageBasins,
        InlandLakeTopology inlandLakes) {

    public WorldHydrologyTopology {
        if (standingWaterTopology == null || drainageBasins == null || inlandLakes == null) {
            throw new IllegalArgumentException("world hydrology topology facts must not be null");
        }
        WorldBounds bounds = standingWaterTopology.bounds();
        if (!bounds.equals(drainageBasins.bounds()) || !bounds.equals(inlandLakes.bounds())) {
            throw new IllegalArgumentException("world hydrology topology facts must share world bounds");
        }
    }

    public WorldBounds bounds() {
        return standingWaterTopology.bounds();
    }
}
