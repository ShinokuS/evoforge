package io.github.evoforge.simulation.world.weather.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.mechanics.measurement.AirTemperature;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.weather.WeatherCellState;
import io.github.evoforge.simulation.world.weather.WeatherFootprint;
import io.github.evoforge.simulation.world.weather.WeatherState;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class AlternatingRainfallPulseDriverTest {

    @Test
    void alternatesCoherentDryAndWetSpellsInsideItsFootprint() {
        WeatherState weather = calmWeather();
        AlternatingRainfallPulseDriver driver = new AlternatingRainfallPulseDriver(
                weather,
                new WeatherFootprint(1, 2, 1, 2),
                parameters(),
                SimulationTimeScale.of(Duration.ofSeconds(1L)),
                77L);

        long wetStart = driver.nextTransitionTick();
        driver.update(wetStart - 1L);
        assertFalse(driver.raining());

        driver.update(wetStart);
        assertTrue(driver.raining());
        assertTrue(weather.at(1, 1).precipitationRate().depthNanometersNumerator().signum() > 0);
        assertEquals(WaterDepthRate.ZERO, weather.at(0, 0).precipitationRate());
        assertEquals(WaterDepthRate.ZERO, weather.at(3, 3).precipitationRate());

        long dryStart = driver.nextTransitionTick();
        driver.update(dryStart);
        assertFalse(driver.raining());
        assertEquals(WaterDepthRate.ZERO, weather.at(1, 1).precipitationRate());
        assertEquals(WaterDepthRate.ZERO, weather.at(2, 2).precipitationRate());
    }

    @Test
    void sameSeedAndParametersReplayExactlyWithoutPerTickRandomness() {
        WeatherState firstWeather = calmWeather();
        WeatherState secondWeather = calmWeather();
        AlternatingRainfallPulseDriver first = new AlternatingRainfallPulseDriver(
                firstWeather,
                WeatherFootprint.whole(firstWeather.bounds()),
                parameters(),
                SimulationTimeScale.of(Duration.ofMillis(250L)),
                991L);
        AlternatingRainfallPulseDriver second = new AlternatingRainfallPulseDriver(
                secondWeather,
                WeatherFootprint.whole(secondWeather.bounds()),
                parameters(),
                SimulationTimeScale.of(Duration.ofMillis(250L)),
                991L);

        long initialTransition = first.nextTransitionTick();
        for (long tick = 0L; tick <= 200L; tick++) {
            first.update(tick);
            second.update(tick);
            assertEquals(first.raining(), second.raining());
            assertEquals(first.nextTransitionTick(), second.nextTransitionTick());
            assertEquals(firstWeather.at(2, 1), secondWeather.at(2, 1));
        }
        assertTrue(initialTransition > 0L);
    }

    @Test
    void precipitationDriverPreservesOtherWeatherDimensions() {
        WaterDepthRate evaporation = WaterDepthRate.ofMillimeters(4L, Duration.ofDays(1L));
        WeatherCellState initial = new WeatherCellState(
                AirTemperature.ofMilliCelsius(18_250),
                WaterDepthRate.ZERO,
                evaporation);
        WeatherState weather = new WeatherState(bounds(), initial);
        AlternatingRainfallPulseDriver driver = new AlternatingRainfallPulseDriver(
                weather,
                WeatherFootprint.whole(weather.bounds()),
                parameters(),
                SimulationTimeScale.of(Duration.ofSeconds(1L)),
                42L);

        driver.update(driver.nextTransitionTick());

        assertEquals(AirTemperature.ofMilliCelsius(18_250), weather.at(0, 0).airTemperature());
        assertEquals(evaporation, weather.at(0, 0).evaporativeDemandRate());
        assertTrue(weather.at(0, 0).precipitationRate().depthNanometersNumerator().signum() > 0);
    }

    private static RainfallPulseParameters parameters() {
        return new RainfallPulseParameters(
                Duration.ofSeconds(5L),
                Duration.ofSeconds(2L),
                WaterDepthRate.ofMillimeters(3L, Duration.ofHours(1L)));
    }

    private static WeatherState calmWeather() {
        return new WeatherState(
                bounds(),
                WeatherCellState.calm(AirTemperature.ofMilliCelsius(12_000)));
    }

    private static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -2, 2);
    }
}
