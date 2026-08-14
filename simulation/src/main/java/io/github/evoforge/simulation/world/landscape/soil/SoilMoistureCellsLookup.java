package io.github.evoforge.simulation.world.landscape.soil;

/** Read-only deterministic iteration over terrain cells retaining positive moisture. */
public interface SoilMoistureCellsLookup {

    int wetCellCount();

    void forEach(SoilMoistureCellConsumer consumer);
}
