package io.github.evoforge.simulation.world.mechanics.occupancy;

import io.github.evoforge.simulation.world.space.occupancy.OccupancyState;

/** Read-only present-tense occupancy projection for one discrete object cell. */
public interface OccupancyLookup {

    OccupancyState state(
            int x,
            int y,
            int z);
}
