package io.github.evoforge.simulation.world.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(0L, view.precipitationSupplyAt(1, 0).volumeDueAtTick(1L));

        weather.setAt(
                1,
                0,
                new WeatherCellState(
                        AirTemperature.ofMilliCelsius(8_000),
                        WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L)),
                        WaterDepthRate.ZERO));

        assertEquals(0L, view.precipitationSupplyAt(0, 0).volumeDueAtTick(1L));
        assertEquals(1_000L, view.precipitationSupplyAt(1, 0).volumeDueAtTick(1L));
    }
}
