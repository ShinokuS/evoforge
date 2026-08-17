package io.github.evoforge.simulation.world.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.mechanics.measurement.AirTemperature;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class WeatherHydroForcingViewTest {

    @Test
    void currentWeatherIsCompiledAtRuntimeBoundaryAndCanChangeBetweenTicks() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -2, 2);
        WeatherCellState calm = WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000));
        WeatherState weather = new WeatherState(bounds, calm);
        WeatherHydroForcingView view = new WeatherHydroForcingView(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        assertEquals(0L, view.precipitationRateAt(1, 0).volumeDueAtTick(1L));

        weather.setAt(
                1,
                0,
                new WeatherCellState(
                        AirTemperature.ofMilliCelsius(8_000),
                        WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                        WaterDepthRate.ZERO));

        assertEquals(0L, view.precipitationRateAt(0, 0).volumeDueAtTick(1L));
        assertEquals(1_000L, view.precipitationRateAt(1, 0).volumeDueAtTick(1L));
    }

    @Test
    void dynamicIntegrationUsesOnlyCurrentIntervalsAndPreservesFractionalCarry() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -2, 2);
        WeatherState weather = new WeatherState(
                bounds,
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000)));
        WeatherHydroForcingView view = new WeatherHydroForcingView(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        view.advanceToTick(1L);
        assertEquals(0L, view.precipitationDueAt(0, 0));

        weather.setPrecipitationRateAt(
                0,
                0,
                WaterDepthRate.ofNanometers(500L, Duration.ofSeconds(1L)));
        view.advanceToTick(2L);
        assertEquals(0L, view.precipitationDueAt(0, 0));

        weather.setPrecipitationRateAt(0, 0, WaterDepthRate.ZERO);
        view.advanceToTick(3L);
        assertEquals(0L, view.precipitationDueAt(0, 0));

        weather.setPrecipitationRateAt(
                0,
                0,
                WaterDepthRate.ofNanometers(500L, Duration.ofSeconds(1L)));
        view.advanceToTick(4L);
        assertEquals(1L, view.precipitationDueAt(0, 0));
    }

    @Test
    void dynamicIntegrationRejectsSkippedOrRepeatedTicks() {
        WeatherState weather = new WeatherState(
                new WorldBounds(0, 0, 0, 0, -1, 1),
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(10_000)));
        WeatherHydroForcingView view = new WeatherHydroForcingView(
                weather,
                PhysicalSpaceScale.cubicMillimeters(1_000L),
                SimulationTimeScale.of(Duration.ofSeconds(1L)));

        assertThrows(IllegalStateException.class, () -> view.advanceToTick(2L));
        view.advanceToTick(1L);
        assertThrows(IllegalStateException.class, () -> view.advanceToTick(1L));
    }
}
