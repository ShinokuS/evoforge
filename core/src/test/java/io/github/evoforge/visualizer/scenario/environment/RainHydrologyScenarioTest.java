package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class RainHydrologyScenarioTest {

    @Test
    void sceneStartsDryWithSeparateFiniteLakeAndWeatherMatchesForcingWindow() {
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
        assertEquals(
                0,
                session.runtime().view().soilMoisture().amount(4, 0, -1),
                "all exposed soil must start uniformly dry");
        assertTrue(
                session.runtime().view().water().amount(-5, 0, -1) > 0,
                "the separate depression lake must exist at setup without prewarming the ground");

        session.runtime().stepper().advance();
        assertEquals(
                WeatherPresentationKind.RAIN,
                session.weather().current().kind(),
                "visual rain must begin on the same tick as the first physical precipitation pulse");
        assertTrue(
                session.runtime().view().soilMoisture().amount(0, 0, -1) > 0,
                "physical soil moisture must already change while rain is visible");
        assertTrue(
                session.runtime().view().soilMoisture().amount(4, 0, -1) > 0,
                "ordinary exposed soil elsewhere in the scene must receive the same live rain forcing");

        for (int tick = 1; tick < 120; tick++) {
            session.runtime().stepper().advance();
        }
        assertEquals(120L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.RAIN, session.weather().current().kind());

        session.runtime().stepper().advance();
        assertEquals(121L, session.runtime().time().tick());
        assertEquals(
                WeatherPresentationKind.CLEAR,
                session.weather().current().kind(),
                "rain visuals must stop when the physical forcing window closes");
    }
}
