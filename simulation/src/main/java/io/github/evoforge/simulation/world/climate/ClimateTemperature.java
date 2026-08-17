package io.github.evoforge.simulation.world.climate;

import io.github.evoforge.simulation.world.mechanics.measurement.AirTemperature;

/** Immutable long-term mean temperature measured in milli-degrees Celsius. */
public record ClimateTemperature(int milliCelsius) {
    public static final int ABSOLUTE_ZERO_MILLI_CELSIUS = AirTemperature.ABSOLUTE_ZERO_MILLI_CELSIUS;

    public ClimateTemperature {
        AirTemperature.ofMilliCelsius(milliCelsius);
    }

    public static ClimateTemperature ofMilliCelsius(int milliCelsius) {
        return new ClimateTemperature(milliCelsius);
    }

    public AirTemperature asAirTemperature() {
        return AirTemperature.ofMilliCelsius(milliCelsius);
    }
}
