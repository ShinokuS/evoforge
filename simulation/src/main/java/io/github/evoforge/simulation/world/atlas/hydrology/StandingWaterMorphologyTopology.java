package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable broad morphology keyed by hydrologic standing-water body id. */
public interface StandingWaterMorphologyTopology {
    WorldBounds bounds();

    int bodyCount();

    StandingWaterMorphology morphology(int bodyId);
}
