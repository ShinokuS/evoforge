package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable per-body potential routing toward boundary-connected standing water. */
public interface StandingWaterBoundaryRouteTopology {
    WorldBounds bounds();

    int bodyCount();

    StandingWaterBoundaryRoute route(int bodyId);
}
