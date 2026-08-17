package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;

/**
 * Requested long-term climate intent before world generation begins.
 *
 * <p>This is the single authored climate source. Generation turns it into spatial
 * {@code ClimateNormalsField} facts. Whether those durable normals are projected into active
 * runtime rain/evaporation is a runtime-composition decision and is deliberately not encoded in
 * this specification.</p>
 *
 * <p>The water-normal dimensions still use {@link CellVolumeRate} as a transitional simulation
 * unit. That preserves existing generated facts until world distance and simulation time have a
 * complete physical scale contract; callers must not interpret these rates as real-world
 * millimetres per day/year.</p>
 */
public record ClimateSpec(
        ClimateTemperature datumMeanTemperature,
        int coolingMilliCelsiusPerElevationCell,
        CellVolumeRate precipitationNormal,
        CellVolumeRate evaporativeDemandNormal) {

    /**
     * Compatibility baseline preserving the generated climate facts of revisions V1-V6.
     *
     * <p>Its zero water normals are climate facts, not a switch that disables runtime atmosphere.
     * The future semantic climate compiler may replace this baseline only under a new generation
     * revision so old worlds remain reproducible.</p>
     */
    public static final ClimateSpec STANDARD = new ClimateSpec(
            ClimateTemperature.ofMilliCelsius(12_000),
            250,
            CellVolumeRate.ZERO,
            CellVolumeRate.ZERO);

    /** @deprecated Runtime forcing is no longer a property of climate intent. Use {@link #STANDARD}. */
    @Deprecated(forRemoval = true)
    public static final ClimateSpec STANDARD_UNFORCED = STANDARD;

    public ClimateSpec {
        if (datumMeanTemperature == null) {
            throw new IllegalArgumentException("datum mean temperature must not be null");
        }
        if (coolingMilliCelsiusPerElevationCell < 0) {
            throw new IllegalArgumentException("elevation cooling must not be negative");
        }
        if (precipitationNormal == null) {
            throw new IllegalArgumentException("precipitation normal must not be null");
        }
        if (evaporativeDemandNormal == null) {
            throw new IllegalArgumentException("evaporative-demand normal must not be null");
        }
    }

    public static ClimateSpec of(
            ClimateTemperature datumMeanTemperature,
            int coolingMilliCelsiusPerElevationCell,
            CellVolumeRate precipitationNormal,
            CellVolumeRate evaporativeDemandNormal) {
        return new ClimateSpec(
                datumMeanTemperature,
                coolingMilliCelsiusPerElevationCell,
                precipitationNormal,
                evaporativeDemandNormal);
    }

    /** @deprecated Use {@link #precipitationNormal()}; supply is a runtime-forcing concept. */
    @Deprecated(forRemoval = true)
    public CellVolumeRate precipitationSupply() {
        return precipitationNormal;
    }

    /** @deprecated Use {@link #evaporativeDemandNormal()}; runtime demand belongs to forcing. */
    @Deprecated(forRemoval = true)
    public CellVolumeRate evaporativeDemand() {
        return evaporativeDemandNormal;
    }
}
