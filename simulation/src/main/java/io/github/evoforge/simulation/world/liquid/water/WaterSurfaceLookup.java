package io.github.evoforge.simulation.world.liquid.water;

/** Read-only topmost positive-water cell in an XY column. */
public interface WaterSurfaceLookup {

    boolean hasColumn(int x, int y);

    int topZ(int x, int y);

    int columnCount();

    /** Iterates wet columns in deterministic X/Y order. */
    void forEach(WaterSurfaceConsumer consumer);
}
