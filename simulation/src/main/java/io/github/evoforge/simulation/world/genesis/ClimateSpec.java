package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;

/**
 * Requested long-term climate intent before world generation begins.
 *
 * <p>This remains the single authored climate source. V1-V7 retain the historical cell-relative
 * atmospheric-water normal so those worlds remain reproducible. V8 introduces physical water-depth
 * normals that are independent from cell area and tick duration. Runtime atmosphere activation is
 * still a separate composition decision.</p>
 */
public record ClimateSpec(
        ClimateTemperature datumMeanTemperature,
        int coolingMilliCelsiusPerElevationCell,
        ClimateWaterNormal precipitationWaterNormal,
        ClimateWaterNormal evaporativeDemandWaterNormal) {

    /**
     * Legacy neutral baseline for the current V7 world revision.
     *
     * <p>The equal 1:1 rates carry only a dimensionless moisture balance for V7 initial-water
     * generation. They are not an Earth calibration and must not be interpreted as physical rain
     * or evaporation. V8 worlds should be authored through {@link #physical}.</p>
     */
    public static final ClimateSpec STANDARD = new ClimateSpec(
            ClimateTemperature.ofMilliCelsius(12_000),
            250,
            ClimateWaterNormal.legacy(CellVolumeRate.of(1L, 1L)),
            ClimateWaterNormal.legacy(CellVolumeRate.of(1L, 1L)));

    /** @deprecated Runtime forcing is not a property of climate intent. Use {@link #STANDARD}. */
    @Deprecated(forRemoval = true)
    public static final ClimateSpec STANDARD_UNFORCED = STANDARD;

    /** Source-compatible constructor for V1-V7 cell-relative climate specifications. */
    public ClimateSpec(
            ClimateTemperature datumMeanTemperature,
            int coolingMilliCelsiusPerElevationCell,
            CellVolumeRate precipitationNormal,
            CellVolumeRate evaporativeDemandNormal) {
        this(
                datumMeanTemperature,
                coolingMilliCelsiusPerElevationCell,
                ClimateWaterNormal.legacy(precipitationNormal),
                ClimateWaterNormal.legacy(evaporativeDemandNormal));
    }

    public ClimateSpec {
        if (datumMeanTemperature == null) {
            throw new IllegalArgumentException("datum mean temperature must not be null");
        }
        if (coolingMilliCelsiusPerElevationCell < 0) {
            throw new IllegalArgumentException("elevation cooling must not be negative");
        }
        if (precipitationWaterNormal == null || evaporativeDemandWaterNormal == null) {
            throw new IllegalArgumentException("climate water normals must not be null");
        }
        if (!precipitationWaterNormal.kind().equals(evaporativeDemandWaterNormal.kind())) {
            throw new IllegalArgumentException(
                    "precipitation and evaporative-demand normals must use the same dimension");
        }
    }

    /** Compatibility factory for V1-V7 cell-relative climate specifications. */
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

    /** Physical V8+ climate-water intent expressed as water depth per physical time. */
    public static ClimateSpec physical(
            ClimateTemperature datumMeanTemperature,
            int coolingMilliCelsiusPerElevationCell,
            WaterDepthRate precipitationNormal,
            WaterDepthRate evaporativeDemandNormal) {
        return new ClimateSpec(
                datumMeanTemperature,
                coolingMilliCelsiusPerElevationCell,
                ClimateWaterNormal.physical(precipitationNormal),
                ClimateWaterNormal.physical(evaporativeDemandNormal));
    }

    /** Legacy V1-V7 accessor retained while historical revisions are supported. */
    public CellVolumeRate precipitationNormal() {
        return requireLegacy(precipitationWaterNormal, "precipitation");
    }

    /** Legacy V1-V7 accessor retained while historical revisions are supported. */
    public CellVolumeRate evaporativeDemandNormal() {
        return requireLegacy(evaporativeDemandWaterNormal, "evaporative demand");
    }

    public WaterDepthRate precipitationDepthNormal() {
        return requirePhysical(precipitationWaterNormal, "precipitation");
    }

    public WaterDepthRate evaporativeDemandDepthNormal() {
        return requirePhysical(evaporativeDemandWaterNormal, "evaporative demand");
    }

    /** @deprecated Use {@link #precipitationNormal()} only for legacy V1-V7 compatibility. */
    @Deprecated(forRemoval = true)
    public CellVolumeRate precipitationSupply() {
        return precipitationNormal();
    }

    /** @deprecated Use {@link #evaporativeDemandNormal()} only for legacy V1-V7 compatibility. */
    @Deprecated(forRemoval = true)
    public CellVolumeRate evaporativeDemand() {
        return evaporativeDemandNormal();
    }

    private static CellVolumeRate requireLegacy(ClimateWaterNormal normal, String name) {
        if (normal instanceof ClimateWaterNormal.LegacyCellVolume legacy) {
            return legacy.rate();
        }
        throw new IllegalStateException(name + " normal is physical water depth, not CellVolume/tick");
    }

    private static WaterDepthRate requirePhysical(ClimateWaterNormal normal, String name) {
        if (normal instanceof ClimateWaterNormal.PhysicalDepth physical) {
            return physical.rate();
        }
        throw new IllegalStateException(name + " normal is legacy CellVolume/tick, not physical depth");
    }
}
