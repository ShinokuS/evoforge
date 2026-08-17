package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/**
 * Requested long-term climate intent before world generation begins.
 *
 * <p>This is the single authored climate source. Generation turns it into spatial
 * {@code ClimateNormalsField} facts; runtime rain/evaporation consume only a derived hydrologic
 * projection of those facts.</p>
 */
public record ClimateSpec(
        ClimateTemperature datumMeanTemperature,
        int coolingMilliCelsiusPerElevationCell,
        CellVolumeRate precipitationSupply,
        CellVolumeRate evaporativeDemand) {

    public static final ClimateSpec STANDARD_UNFORCED = new ClimateSpec(
            ClimateTemperature.ofMilliCelsius(12_000),
            250,
            CellVolumeRate.ZERO,
            CellVolumeRate.ZERO);

    public ClimateSpec {
        if (datumMeanTemperature == null) {
            throw new IllegalArgumentException("datum mean temperature must not be null");
        }
        if (coolingMilliCelsiusPerElevationCell < 0) {
            throw new IllegalArgumentException("elevation cooling must not be negative");
        }
        if (precipitationSupply == null) {
            throw new IllegalArgumentException("precipitation supply must not be null");
        }
        if (evaporativeDemand == null) {
            throw new IllegalArgumentException("evaporative demand must not be null");
        }
    }

    public static ClimateSpec of(
            ClimateTemperature datumMeanTemperature,
            int coolingMilliCelsiusPerElevationCell,
            CellVolumeRate precipitationSupply,
            CellVolumeRate evaporativeDemand) {
        return new ClimateSpec(
                datumMeanTemperature,
                coolingMilliCelsiusPerElevationCell,
                precipitationSupply,
                evaporativeDemand);
    }
}
