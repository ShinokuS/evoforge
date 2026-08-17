package io.github.evoforge.simulation.world.weather;

/** Runtime process that advances current weather deterministically to a simulation tick. */
@FunctionalInterface
public interface WeatherDriver {
    void update(long tick);
}
