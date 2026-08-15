package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class RainHydrologyScenarioTest {

    @Test
    void sceneStartsDryWithSeparateFiniteLake() {
        ScenarioSession session = new RainHydrologyScenario().create();

        assertEquals(0L, session.runtime().time().tick());
        assertEquals(
                WeatherPresentationKind.CLEAR,
                session.weather().current().kind());

        assertEquals(
                0,
                session.runtime().view().soilMoisture().amount(0, 0, -1));
        assertEquals(
                0,
                session.runtime().view().water().amount(0, 0, 0));
        assertTrue(
                session.runtime().view().water().amount(-5, 0, -1) > 0,
                "the separate depression lake must exist at setup without prewarming the ground");
        assertEquals(
                0,
                session.runtime().view().soilMoisture().amount(4, 0, -1),
                "ground under the elevated roof must start just as dry as exposed ground");
    }
}
