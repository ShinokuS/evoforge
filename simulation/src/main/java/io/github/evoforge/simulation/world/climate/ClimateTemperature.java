package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.genesis.ClimateNormalsSpec;

/** Immutable long-term mean temperature measured in milli-degrees Celsius. */
public record ClimateTemperature(int milliCelsius) {

    public ClimateTemperature {
        if (milliCelsius < ClimateNormalsSpec.ABSOLUTE_ZERO_MILLI_CELSIUS) {
            throw new IllegalArgumentException("temperature must not be below absolute zero");
        }
    }

    public static ClimateTemperature ofMilliCelsius(int milliCelsius) {
        return new ClimateTemperature(milliCelsius);
    }
}
