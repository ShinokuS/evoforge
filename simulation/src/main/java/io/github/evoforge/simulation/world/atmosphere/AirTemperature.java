package io.github.evoforge.simulation.world.atmosphere;

/** Exact air temperature value measured in milli-degrees Celsius. */
public record AirTemperature(int milliCelsius) {
    public static final int ABSOLUTE_ZERO_MILLI_CELSIUS = -273_150;

    public AirTemperature {
        if (milliCelsius < ABSOLUTE_ZERO_MILLI_CELSIUS) {
            throw new IllegalArgumentException("temperature must not be below absolute zero");
        }
    }

    public static AirTemperature ofMilliCelsius(int milliCelsius) {
        return new AirTemperature(milliCelsius);
    }
}
