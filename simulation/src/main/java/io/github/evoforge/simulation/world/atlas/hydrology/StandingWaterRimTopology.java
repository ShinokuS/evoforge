package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Immutable dry-rim geometry keyed by standing-water body id. */
public interface StandingWaterRimTopology {
    WorldBounds bounds();

    int bodyCount();

    List<StandingWaterRimCell> rimCells(int bodyId);
}
