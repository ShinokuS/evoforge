package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class GeneratedRainfallRegimeScenarioTest {

    @Test
    void generatedCalibrationPathStartsDryThenProducesLiveRainAndSoilWater() {
        ScenarioSession session = new GeneratedRainfallRegimeScenario().create();

        assertEquals(0L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.CLEAR, session.weather().current().kind());
        assertEquals(0L, retainedWater(session));
        assertEquals(0L, freeWater(session));

        for (int tick = 1; tick < 10; tick++) {
            session.runtime().stepper().advance();
            assertEquals(WeatherPresentationKind.CLEAR, session.weather().current().kind());
        }

        session.runtime().stepper().advance();
        assertEquals(10L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.RAIN, session.weather().current().kind());
        assertTrue(retainedWater(session) > 0L);
    }

    private static long retainedWater(ScenarioSession session) {
        long total = 0L;
        for (int x = -7; x <= 7; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -4; z <= 4; z++) {
                    total += session.runtime().view().soilLiquids().amountOf(
                            WaterSystem.TYPE, x, y, z);
                }
            }
        }
        return total;
    }

    private static long freeWater(ScenarioSession session) {
        long total = 0L;
        for (int x = -7; x <= 7; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -4; z <= 4; z++) {
                    total += session.runtime().view().water().amount(x, y, z);
                }
            }
        }
        return total;
    }
}
