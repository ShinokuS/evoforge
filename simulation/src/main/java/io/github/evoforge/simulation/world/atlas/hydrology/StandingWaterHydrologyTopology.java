package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable Stage 2B.1 analytical bundle for accepted standing-water topology. */
public record StandingWaterHydrologyTopology(
        StandingWaterTopology rawStandingWater,
        StandingWaterTopology standingWater,
        StandingWaterDomainTopology domains,
        StandingWaterRimTopology rims,
        StandingWaterSpillTopology spills,
        StandingWaterBoundaryRouteTopology boundaryRoutes) {

    public StandingWaterHydrologyTopology {
        if (rawStandingWater == null
                || standingWater == null
                || domains == null
                || rims == null
                || spills == null
                || boundaryRoutes == null) {
            throw new IllegalArgumentException("standing-water hydrology facts must not be null");
        }
        WorldBounds bounds = standingWater.bounds();
        int bodyCount = standingWater.bodyCount();
        if (!bounds.equals(rawStandingWater.bounds())
                || !bounds.equals(domains.bounds())
                || !bounds.equals(rims.bounds())
                || !bounds.equals(spills.bounds())
                || !bounds.equals(boundaryRoutes.bounds())) {
            throw new IllegalArgumentException("standing-water hydrology facts must share world bounds");
        }
        if (domains.bodyCount() != bodyCount
                || rims.bodyCount() != bodyCount
                || spills.bodyCount() != bodyCount
                || boundaryRoutes.bodyCount() != bodyCount) {
            throw new IllegalArgumentException("standing-water hydrology facts must share hydrologic body domain");
        }
    }

    public WorldBounds bounds() {
        return standingWater.bounds();
    }

    public int bodyCount() {
        return standingWater.bodyCount();
    }

    public int rawBodyCount() {
        return rawStandingWater.bodyCount();
    }
}
