package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable external-drainage role keyed by hydrologic standing-water body id. */
public interface StandingWaterExternalSinkTopology {
    WorldBounds bounds();

    int bodyCount();

    boolean isExternalSink(int bodyId);

    int externalSinkCount();
}
