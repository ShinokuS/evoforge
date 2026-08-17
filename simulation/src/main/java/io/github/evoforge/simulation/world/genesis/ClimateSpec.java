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
     * Minimal baseline climate used when callers do not author one explicitly.
     *
     * <p>The zero water rates are climate values, not a switch that disables runtime atmosphere.
     * Atmospheric activation belongs to runtime composition.</p>
     */
    public static final ClimateSpec STANDARD_BASELINE = new ClimateSpec(
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
