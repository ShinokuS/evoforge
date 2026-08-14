package io.github.evoforge.simulation.world.environment.sky;

@FunctionalInterface
public interface SkySurfaceConsumer {

    void accept(SkySurface surface);
}
