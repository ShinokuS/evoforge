package io.github.evoforge.simulation.mechanics.hydrology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.kernel.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atmosphere.AirTemperature;
import io.github.evoforge.simulation.world.atmosphere.WaterDepthRate;
import io.github.evoforge.simulation.world.space.measurement.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.space.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import io.github.evoforge.simulation.world.atmosphere.WeatherCellState;
import io.github.evoforge.simulation.world.atmosphere.WeatherState;

final class WeatherWaterForcingTest {

    @Test
    void currentWeatherIsCompiledAtRuntimeBoundaryAndCanChangeBetweenTicks() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -2, 2);
        WeatherCellState calm = WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000));
        WeatherState weather = new WeatherState(bounds, calm);
        WeatherWaterForcing forcing = new WeatherWaterForcing(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        assertEquals(0L, forcing.precipitationRateAt(1, 0).volumeDueAtTick(1L));
        weather.setAt(
                1,
                0,
                new WeatherCellState(
                        AirTemperature.ofMilliCelsius(8_000),
                        WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                        WaterDepthRate.ZERO));
        assertEquals(0L, forcing.precipitationRateAt(0, 0).volumeDueAtTick(1L));
        assertEquals(1_000L, forcing.precipitationRateAt(1, 0).volumeDueAtTick(1L));
    }

    @Test
    void dynamicIntegrationUsesOnlyCurrentIntervalsAndPreservesFractionalCarry() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -2, 2);
        WeatherState weather = new WeatherState(
                bounds,
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000)));
        WeatherWaterForcing forcing = new WeatherWaterForcing(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        forcing.advanceToTick(1L);
        assertEquals(0L, forcing.precipitationDueAt(0, 0));
        weather.setPrecipitationRateAt(
                0, 0, WaterDepthRate.ofNanometers(500L, Duration.ofSeconds(1L)));
        forcing.advanceToTick(2L);
        assertEquals(0L, forcing.precipitationDueAt(0, 0));
        weather.setPrecipitationRateAt(0, 0, WaterDepthRate.ZERO);
        forcing.advanceToTick(3L);
        assertEquals(0L, forcing.precipitationDueAt(0, 0));
        weather.setPrecipitationRateAt(
                0, 0, WaterDepthRate.ofNanometers(500L, Duration.ofSeconds(1L)));
        forcing.advanceToTick(4L);
        assertEquals(1L, forcing.precipitationDueAt(0, 0));
    }

    @Test
    void dynamicIntegrationRejectsSkippedOrRepeatedTicks() {
        WeatherState weather = new WeatherState(
                new WorldBounds(0, 0, 0, 0, -1, 1),
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000)));
        WeatherWaterForcing forcing = new WeatherWaterForcing(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));
        assertThrows(IllegalStateException.class, () -> forcing.advanceToTick(2L));
        forcing.advanceToTick(1L);
        assertThrows(IllegalStateException.class, () -> forcing.advanceToTick(1L));
    }
}
