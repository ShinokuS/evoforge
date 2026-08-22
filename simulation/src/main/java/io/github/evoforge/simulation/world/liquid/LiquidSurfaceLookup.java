package io.github.evoforge.simulation.world.liquid;

/** Read-only topmost positive-liquid cells in deterministic XY columns. */
public interface LiquidSurfaceLookup {

    boolean hasColumn(int x, int y);

    int topZ(int x, int y);

    LiquidTypeId topType(int x, int y);

    int columnCount();

    /** Iterates all wet columns in deterministic X/Y order. */
    void forEach(LiquidSurfaceConsumer consumer);

    boolean hasColumn(LiquidTypeId type, int x, int y);

    int topZ(LiquidTypeId type, int x, int y);

    int columnCount(LiquidTypeId type);

    /** Iterates columns containing the requested liquid in deterministic X/Y order. */
    void forEach(LiquidTypeId type, LiquidSurfaceConsumer consumer);
}
