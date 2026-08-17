package io.github.evoforge.simulation.world.weather;

/** Runtime process that advances current weather deterministically to a simulation tick. */
@FunctionalInterface
public interface WeatherDriver {
    void update(long tick);

    /** Driver that intentionally leaves the current weather state unchanged. */
    static WeatherDriver stationary() {
        return tick -> {
            if (tick < 0L) {
                throw new IllegalArgumentException("weather tick must be non-negative");
            }
        };
    }
}
