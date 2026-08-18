package io.github.evoforge.simulation.world.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.climate.ClimateNormalsField;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.mechanics.measurement.AirTemperature;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WeatherStateTest {

    @Test
    void stateIsSpatialMutableAndKeepsPhysicalRates() {
        WorldBounds bounds = new WorldBounds(-1, 1, -1, 1, -2, 2);
        WeatherCellState calm = WeatherCellState.calm(AirTemperature.ofMilliCelsius(12_000));
        WeatherState state = new WeatherState(bounds, calm);
        WeatherCellState storm = new WeatherCellState(
                AirTemperature.ofMilliCelsius(9_500),
                WaterDepthRate.ofMillimeters(12L, Duration.ofHours(1L)),
                WaterDepthRate.ZERO);

        state.setAt(1, -1, storm);

        assertEquals(storm, state.at(1, -1));
        assertEquals(calm, state.at(0, 0));
        assertThrows(IllegalArgumentException.class, () -> state.at(2, 0));
        assertThrows(IllegalArgumentException.class, () -> state.setAt(0, 2, storm));
    }

    @Test
    void calmPhysicalWeatherKeepsPotentialEvaporativeDemandFromClimate() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        WaterDepthRate evaporation =
                WaterDepthRate.ofMillimeters(2_400L, Duration.ofDays(365L));
        ClimateNormalsField climate = new ClimateNormalsField() {
            @Override public WorldBounds bounds() { return bounds; }
            @Override public ClimateTemperature meanTemperatureAt(int x, int y) {
                return ClimateTemperature.ofMilliCelsius(18_000);
            }
            @Override public ClimateWaterNormal.Kind waterNormalKind() {
                return ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME;
            }
            @Override public ClimateWaterNormal precipitationWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.physical(
                        WaterDepthRate.ofMillimeters(1_200L, Duration.ofDays(365L)));
            }
            @Override public ClimateWaterNormal evaporativeDemandWaterNormalAt(int x, int y) {
                return ClimateWaterNormal.physical(evaporation);
            }
        };

        WeatherCellState current = WeatherState.calmFromClimateNormals(climate).at(0, 0);

        assertEquals(AirTemperature.ofMilliCelsius(18_000), current.airTemperature());
        assertEquals(WaterDepthRate.ZERO, current.precipitationRate());
        assertEquals(evaporation, current.evaporativeDemandRate());
    }
}
