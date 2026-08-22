package io.github.evoforge.simulation.world.terrain;

/** Read-only topmost terrain anchor for each occupied XY column. */
public interface TerrainSurfaceLookup {

    boolean hasColumn(int x, int y);

    int topZ(int x, int y);

    int columnCount();

    /** Iterates occupied columns in deterministic X/Y order. */
    void forEach(TerrainSurfaceConsumer consumer);
}
