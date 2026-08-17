package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/**
 * Requested long-term climate intent before world generation begins.
 *
 * <p>This is the single authored climate source. Generation turns it into spatial
 * {@code ClimateNormalsField} facts. Whether runtime atmospheric processes consume those facts is
 * a simulation-composition decision and is deliberately not encoded in this world specification.</p>
 */
public record ClimateSpec(
        ClimateTemperature datumMeanTemperature,
        int coolingMilliCelsiusPerElevationCell,
        CellVolumeRate precipitationSupply,
        CellVolumeRate evaporativeDemand) {

    /**
     * Neutral baseline climate used when callers do not author one explicitly.
     *
     * <p>The equal non-zero hydrologic rates deliberately define a finite moisture ratio without
     * pretending that one simulation tick has a calibrated real-world duration. Runtime atmosphere
     * activation remains a separate composition decision.</p>
     */
    public static final ClimateSpec STANDARD_BASELINE = new ClimateSpec(
            ClimateTemperature.ofMilliCelsius(12_000),
            250,
            CellVolumeRate.of(1L, 1L),
            CellVolumeRate.of(1L, 1L));

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
