package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Immutable minimum-barrier graph between accepted standing-water bodies. */
public interface StandingWaterSpillTopology {
    WorldBounds bounds();

    int bodyCount();

    List<StandingWaterSpillConnection> connections();

    List<StandingWaterSpillConnection> connectionsForBody(int bodyId);
}
