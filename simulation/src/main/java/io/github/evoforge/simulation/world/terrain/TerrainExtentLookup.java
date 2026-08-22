package io.github.evoforge.simulation.world.terrain;

/** Read-only vertical extent of currently present terrain. */
public interface TerrainExtentLookup {

    boolean empty();

    int minZ();

    int maxZ();
}
