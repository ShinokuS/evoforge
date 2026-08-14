package io.github.evoforge.simulation.world.environment.sky;

/** Read-only vertical sky exposure over state-bearing XY columns. */
public interface SkySurfaceLookup {

    SkySurface find(int x, int y);

    void forEach(SkySurfaceConsumer consumer);
}
