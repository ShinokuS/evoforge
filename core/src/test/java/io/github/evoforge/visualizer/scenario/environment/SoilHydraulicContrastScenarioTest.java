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

        long previousWater = total(fast) + total(slow);
        int clearWeatherDecreases = 0;
        for (int step = 0; step < 200 && clearWeatherDecreases < 2; step++) {
            session.runtime().stepper().advance();
            SoilHydraulicContrastScenario.SideWater currentLeft = left(session);
            SoilHydraulicContrastScenario.SideWater currentRight = right(session);
            long currentWater = total(currentLeft) + total(currentRight);

            if (session.weather().current().kind() == WeatherPresentationKind.CLEAR
                    && currentWater < previousWater) {
                clearWeatherDecreases++;
            }
            previousWater = currentWater;
        }

        assertTrue(
                clearWeatherDecreases >= 2,
                "repeated clear-weather evaporation must keep reducing exposed Water after rainfall");
    }

    private static long total(SoilHydraulicContrastScenario.SideWater water) {
        return water.retained() + water.free();
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
