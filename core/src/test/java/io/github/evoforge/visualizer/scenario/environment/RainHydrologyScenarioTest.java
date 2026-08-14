package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class RainHydrologyScenarioTest {

    @Test
    void prewarmedSceneContainsSurfaceWaterInsideDryAbsorbentWalls() {
        ScenarioSession session = new RainHydrologyScenario().create();

        assertEquals(400L, session.runtime().time().tick());
        assertEquals(
                WeatherPresentationKind.RAIN,
                session.weather().current().kind());
        assertTrue(session.runtime().view().waterSurfaces().columnCount() > 0);

        assertTrue(
                session.runtime().view().water().amount(-4, 0, 0) > 0,
                "stone catchment should expose finite surface water");

        assertEquals(
                0,
                session.runtime().view().water().amount(-6, 0, 1),
                "left absorbent wall must still have no surface overflow");
        assertEquals(
                0,
                session.runtime().view().water().amount(6, 0, 1),
                "right absorbent wall must still have no surface overflow");
    }
}
