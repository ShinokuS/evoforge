package io.github.evoforge.simulation.world.climate;

/** Immutable long-term mean temperature measured in milli-degrees Celsius. */
public record ClimateTemperature(int milliCelsius) {
    public static final int ABSOLUTE_ZERO_MILLI_CELSIUS = -273_150;

    public ClimateTemperature {
        if (milliCelsius < ABSOLUTE_ZERO_MILLI_CELSIUS) {
            throw new IllegalArgumentException("temperature must not be below absolute zero");
        }
    }

    public static ClimateTemperature ofMilliCelsius(int milliCelsius) {
        return new ClimateTemperature(milliCelsius);
    }
}
