package io.github.evoforge.simulation.world.mechanics.occupancy;

/** Read-only present-tense occupancy projection for one discrete object cell. */
public interface OccupancyLookup {

    OccupancyState state(
            int x,
            int y,
            int z);
}
