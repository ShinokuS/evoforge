package io.github.evoforge.simulation.world.surface;

@FunctionalInterface
public interface SkySurfaceConsumer {

    void accept(SkySurface surface);
}
