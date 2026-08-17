package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.measurement.AirTemperature;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.weather.WeatherCellState;
import io.github.evoforge.simulation.world.weather.WeatherFootprint;
import io.github.evoforge.simulation.world.weather.WeatherHydroForcingView;
import io.github.evoforge.simulation.world.weather.WeatherState;
import io.github.evoforge.simulation.world.weather.model.AlternatingRainfallPulseDriver;
import io.github.evoforge.simulation.world.weather.model.RainfallPulseParameters;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class DynamicWeatherForcingIntegrationTest {

    @Test
    void weatherDriverAdvancesBeforeSameTickAtmosphericWaterForcing() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, 0, 2);
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(0, 0, 0, 0, 0, 2);
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:weather-ground");
        assembly.placeTerrain(0, 0, 0, ground);

        WeatherState weather = new WeatherState(
                bounds,
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(12_000)));
        SimulationTimeScale timeScale = SimulationTimeScale.of(Duration.ofSeconds(1L));
        PhysicalSpaceScale spaceScale = PhysicalSpaceScale.cubicMillimeters(1_000L);
        AlternatingRainfallPulseDriver driver = new AlternatingRainfallPulseDriver(
                weather,
                WeatherFootprint.whole(bounds),
                new RainfallPulseParameters(
                        Duration.ofSeconds(1L),
                        Duration.ofSeconds(1L),
                        WaterDepthRate.ofMillimeters(1L, Duration.ofSeconds(1L))),
                timeScale,
                42L);
        WeatherHydroForcingView forcing = new WeatherHydroForcingView(
                weather,
                spaceScale,
                timeScale,
                driver);
        assembly.generatedHydroClimate(forcing);
        SimulationRuntime runtime = assembly.start();

        long wetStart = driver.nextTransitionTick();
        for (long tick = 1L; tick < wetStart; tick++) {
            runtime.stepper().advance();
        }
        assertEquals(0L, totalWater(runtime));

        runtime.stepper().advance();

        assertTrue(driver.raining());
        assertTrue(forcing.precipitationDueAt(0, 0) > 0L);
        assertEquals(forcing.precipitationDueAt(0, 0), totalWater(runtime));
        assertEquals(wetStart, runtime.time().tick());
    }

    private static long totalWater(SimulationRuntime runtime) {
        long total = 0L;
        for (int z = 0; z <= 2; z++) {
            total += runtime.view().water().amount(0, 0, z);
        }
        return total;
    }
}
