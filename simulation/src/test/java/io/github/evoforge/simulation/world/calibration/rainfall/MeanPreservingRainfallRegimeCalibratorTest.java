package io.github.evoforge.simulation.world.calibration.rainfall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class MeanPreservingRainfallRegimeCalibratorTest {

    @Test
    void preservesPhysicalPrecipitationAndOccurrenceStatisticsByColumn() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -1, 1);
        RainfallOccurrenceNormal occurrenceNormal = new RainfallOccurrenceNormal(
                Duration.ofHours(18),
                Duration.ofHours(6));
        RainfallOccurrenceField occurrence = RainfallOccurrenceField.uniform(bounds, occurrenceNormal);

        RainfallRegimeField regimes = new MeanPreservingRainfallRegimeCalibrator().calibrate(
                physicalClimate(bounds), occurrence);

        assertEquals(
                WaterDepthRate.ofMillimeters(600L, Duration.ofDays(365L)),
                regimes.at(0, 0).longTermMeanPrecipitation());
        assertEquals(
                WaterDepthRate.ofMillimeters(1_200L, Duration.ofDays(365L)),
                regimes.at(1, 0).longTermMeanPrecipitation());
        assertEquals(occurrenceNormal, regimes.at(0, 0).occurrence());
        assertEquals(occurrenceNormal, regimes.at(1, 0).occurrence());
    }

    @Test
    void rejectsLegacyClimateAndMismatchedBounds() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        RainfallOccurrenceNormal normal = new RainfallOccurrenceNormal(
                Duration.ofHours(1), Duration.ofHours(1));
        MeanPreservingRainfallRegimeCalibrator calibrator =
                new MeanPreservingRainfallRegimeCalibrator();

        assertThrows(
                IllegalArgumentException.class,
                () -> calibrator.calibrate(
                        legacyClimate(bounds),
                        RainfallOccurrenceField.uniform(bounds, normal)));
        assertThrows(
                IllegalArgumentException.class,
                () -> calibrator.calibrate(
                        physicalClimate(bounds),
                        RainfallOccurrenceField.uniform(
                                new WorldBounds(0, 1, 0, 0, -1, 1), normal)));
    }

    private static ClimateNormalsField physicalClimate(WorldBounds bounds) {
        return new ClimateNormalsField() {
            @Override public WorldBounds bounds() { return bounds; }
            @Override public ClimateTemperature meanTemperatureAt(int x, int y) {
                return ClimateTemperature.ofMilliCelsius(12_000);
            }
            @Override public ClimateWaterNormal.Kind waterNormalKind() {
                return ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME;
            }
            @Override public ClimateWaterNormal precipitationWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.physical(WaterDepthRate.ofMillimeters(
                        x == 0 ? 600L : 1_200L,
                        Duration.ofDays(365L)));
            }
            @Override public ClimateWaterNormal evaporativeDemandWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.physical(WaterDepthRate.ZERO);
            }
        };
    }

    private static ClimateNormalsField legacyClimate(WorldBounds bounds) {
        return new ClimateNormalsField() {
            @Override public WorldBounds bounds() { return bounds; }
            @Override public ClimateTemperature meanTemperatureAt(int x, int y) {
                return ClimateTemperature.ofMilliCelsius(12_000);
            }
            @Override public ClimateWaterNormal.Kind waterNormalKind() {
                return ClimateWaterNormal.Kind.LEGACY_CELL_VOLUME_PER_TICK;
            }
            @Override public ClimateWaterNormal precipitationWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.legacy(CellVolumeRate.ZERO);
            }
            @Override public ClimateWaterNormal evaporativeDemandWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.legacy(CellVolumeRate.ZERO);
            }
        };
    }
}
