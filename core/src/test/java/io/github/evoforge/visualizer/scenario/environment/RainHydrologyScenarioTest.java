package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

final class RainHydrologyScenarioTest {

    @Test
    void prewarmedSceneStartsAtBoundedRainPulseWithShallowSurfaceWater() {
        ScenarioSession session = new RainHydrologyScenario().create();

        assertEquals(240L, session.runtime().time().tick());
        assertEquals(
                WeatherPresentationKind.RAIN,
                session.weather().current().kind());
        assertTrue(session.runtime().view().waterSurfaces().columnCount() > 0);

        assertTrue(
                session.runtime().view().water().amount(-4, 0, 0) > 0,
                "impermeable surface should expose a shallow finite puddle");
        assertEquals(
                0,
                session.runtime().view().water().amount(4, 3, 0),
                "loam should absorb its direct 3 mm rain event before free Water forms");
        assertEquals(
                0,
                session.runtime().view().soilMoisture().amount(3, 0, -1),
                "covered ground must remain shielded by the roof");
        assertTrue(
                session.runtime().view().soilMoisture().amount(3, 0, 1) > 0,
                "the exposed roof must receive precipitation instead");
    }
}
