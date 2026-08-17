package io.github.evoforge.visualizer.scenario.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class SoilHydraulicContrastScenarioTest {

    @Test
    void identicalRainfallProducesDifferentSoilAndSurfaceWaterResponseThenDries() {
        ScenarioSession session = new SoilHydraulicContrastScenario().create();

        assertEquals(0L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.CLEAR, session.weather().current().kind());
        assertEquals(0L, left(session).retained());
        assertEquals(0L, right(session).retained());
        assertEquals(0L, left(session).free());
        assertEquals(0L, right(session).free());

        for (int tick = 1; tick <= 4; tick++) {
            session.runtime().stepper().advance();
        }
        assertEquals(4L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.CLEAR, session.weather().current().kind());

        session.runtime().stepper().advance();
        assertEquals(5L, session.runtime().time().tick());
        assertEquals(WeatherPresentationKind.RAIN, session.weather().current().kind());

        SoilHydraulicContrastScenario.SideWater fast = left(session);
        SoilHydraulicContrastScenario.SideWater slow = right(session);
        assertTrue(
                fast.retained() > slow.retained(),
                "higher hydraulic conductivity must retain more of the same first rain pulse");
        assertEquals(
                0L,
                fast.free(),
                "fast soil should absorb the first deterministic pulse without surface water");
        assertTrue(
                slow.free() > 0L,
                "slow soil should leave surface water from that same rain pulse");

        boolean driedAfterRain = false;
        for (int step = 0; step < 200; step++) {
            session.runtime().stepper().advance();
            if (session.weather().current().kind() != WeatherPresentationKind.CLEAR) continue;
            SoilHydraulicContrastScenario.SideWater left = left(session);
            SoilHydraulicContrastScenario.SideWater right = right(session);
            if (left.retained() == 0L
                    && left.free() == 0L
                    && right.retained() == 0L
                    && right.free() == 0L) {
                driedAfterRain = true;
                break;
            }
        }
        assertTrue(
                driedAfterRain,
                "a sustained clear spell must eventually remove all exposed free and retained Water");
    }

    private static SoilHydraulicContrastScenario.SideWater left(ScenarioSession session) {
        return SoilHydraulicContrastScenario.sideWater(
                session.runtime(),
                SoilHydraulicContrastScenario.BOUNDS.minX(),
                -1);
    }

    private static SoilHydraulicContrastScenario.SideWater right(ScenarioSession session) {
        return SoilHydraulicContrastScenario.sideWater(
                session.runtime(),
                1,
                SoilHydraulicContrastScenario.BOUNDS.maxX());
    }
}
