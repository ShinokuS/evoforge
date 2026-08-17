package io.github.evoforge.simulation.world.genesis;

/**
 * Requested thermal baseline used to author durable climate normals.
 *
 * <p>Temperature is expressed in milli-degrees Celsius. Elevation cooling is intentionally
 * expressed per generated elevation cell because world cells do not yet declare a real-world
 * vertical length. The conversion therefore remains explicit instead of pretending cells are
 * metres.</p>
 */
public record ClimateNormalsSpec(
        int datumMeanTemperatureMilliCelsius,
        int coolingMilliCelsiusPerElevationCell) {

    public static final int ABSOLUTE_ZERO_MILLI_CELSIUS = -273_150;

    /** Current neutral temperate baseline at generated elevation datum Z=0. */
    public static final ClimateNormalsSpec STANDARD = new ClimateNormalsSpec(12_000, 250);

    public ClimateNormalsSpec {
        if (datumMeanTemperatureMilliCelsius < ABSOLUTE_ZERO_MILLI_CELSIUS) {
            throw new IllegalArgumentException("datum mean temperature must not be below absolute zero");
        }
        if (coolingMilliCelsiusPerElevationCell < 0) {
            throw new IllegalArgumentException("elevation cooling must not be negative");
        }
    }

    public static ClimateNormalsSpec of(
            int datumMeanTemperatureMilliCelsius,
            int coolingMilliCelsiusPerElevationCell) {
        return new ClimateNormalsSpec(
                datumMeanTemperatureMilliCelsius,
                coolingMilliCelsiusPerElevationCell);
    }
}
