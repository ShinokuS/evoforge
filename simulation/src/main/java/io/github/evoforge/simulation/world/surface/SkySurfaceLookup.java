package io.github.evoforge.simulation.world.surface;

/** Read-only vertical sky exposure over state-bearing XY columns. */
public interface SkySurfaceLookup {

    SkySurface find(int x, int y);

    void forEach(SkySurfaceConsumer consumer);
}
