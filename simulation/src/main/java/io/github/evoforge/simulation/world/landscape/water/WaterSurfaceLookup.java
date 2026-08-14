package io.github.evoforge.simulation.world.landscape.water;

/** Read-only topmost positive-water cell in an XY column. */
public interface WaterSurfaceLookup {

    boolean hasColumn(int x, int y);

    int topZ(int x, int y);

    int columnCount();
}
