package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.agents.decision.AgentIntentPhase;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LivingCowHerdScenarioTest {

    @Test
    void largerScenarioExercisesMultipleExclusiveCowsPlantsAndInteriorLake() {
        ScenarioSession session = new LivingCowHerdScenario().create();
        Set<ObjectId> cows = new HashSet<>();
        for (int[] start : LivingCowHerdScenario.COW_STARTS) {
            ObjectId cow = session.runtime().view().cells().objectAt(
                    start[0], start[1], LivingCowHerdScenario.STANDING_Z, 0);
            assertNotNull(cow);
            cows.add(cow);
        }
        assertEquals(6, cows.size());

        for (int[] site : LivingCowHerdScenario.PLANT_SITES) {
            assertNotNull(session.runtime().view().cells().objectAt(
                    site[0], site[1], LivingCowHerdScenario.STANDING_Z, 0));
        }
        assertTrue(LivingCowHerdScenario.insideLake(
                LivingCowHerdScenario.LAKE_CENTER_X,
                LivingCowHerdScenario.LAKE_CENTER_Y));
        assertTrue(session.runtime().view().water().amount(
                LivingCowHerdScenario.LAKE_CENTER_X,
                LivingCowHerdScenario.LAKE_CENTER_Y,
                LivingCowHerdScenario.LAKE_WATER_Z) > 0);

        Set<ObjectId> drinkers = new HashSet<>();
        Set<ObjectId> grazers = new HashSet<>();
        boolean sawLakeDrink = false;
        boolean sawDryWeather = false;
        boolean sawSecondRainWindow = false;

        for (int tick = 0; tick < LivingCowHerdScenario.CLIMATE_CYCLE_TICKS + 80; tick++) {
            session.runtime().stepper().advance();
            session.update();
            assertExclusivePositions(session, cows);

            for (ObjectId cow : cows) {
                AgentIntentTrace intent = session.runtime().view().agents().currentIntent(cow);
                if (intent == null || intent.phase() != AgentIntentPhase.USING_OPPORTUNITY) continue;
                if ("needs:satisfaction".equals(intent.providerId())) grazers.add(cow);
                if ("needs:liquid_drink".equals(intent.providerId())) {
                    drinkers.add(cow);
                    sawLakeDrink |= liquidTargetZ(intent.targetKey()) == LivingCowHerdScenario.LAKE_WATER_Z;
                }
            }

            long currentTick = session.runtime().time().tick();
            if (currentTick == LivingCowHerdScenario.RAIN_ACTIVE_TICKS + 1L) {
                sawDryWeather = session.weather().current().kind() == WeatherPresentationKind.CLEAR;
            }
            if (currentTick == LivingCowHerdScenario.CLIMATE_CYCLE_TICKS + 1L) {
                sawSecondRainWindow = session.weather().current().kind() == WeatherPresentationKind.RAIN;
            }
        }

        assertTrue(drinkers.size() >= 2,
                "several independent Cows must successfully complete liquid opportunities");
        assertTrue(grazers.size() >= 2,
                "several independent Cows must successfully complete forage opportunities");
        assertTrue(sawLakeDrink,
                "the broad interior lake must participate in normal drinking opportunities");
        assertTrue(sawDryWeather);
        assertTrue(sawSecondRainWindow);
        assertTrue(session.runtime().view().water().amount(
                LivingCowHerdScenario.LAKE_CENTER_X,
                LivingCowHerdScenario.LAKE_CENTER_Y,
                LivingCowHerdScenario.LAKE_WATER_Z) > 0,
                "the finite lake must survive the acceptance window");
    }

    private static void assertExclusivePositions(
            ScenarioSession session,
            Set<ObjectId> cows) {
        Set<Cell> occupied = new HashSet<>();
        for (ObjectId cow : cows) {
            Cell cell = new Cell(
                    session.runtime().view().transforms().x(cow),
                    session.runtime().view().transforms().y(cow),
                    session.runtime().view().transforms().z(cow));
            assertTrue(occupied.add(cell), "exclusive Cows must never overlap: " + cell);
        }
    }

    private static int liquidTargetZ(String targetKey) {
        if (targetKey == null || !targetKey.startsWith("liquid:")) return Integer.MIN_VALUE;
        int hash = targetKey.lastIndexOf('#');
        int comma = targetKey.lastIndexOf(',', hash);
        if (comma < 0 || hash <= comma) return Integer.MIN_VALUE;
        return Integer.parseInt(targetKey.substring(comma + 1, hash));
    }

    private record Cell(int x, int y, int z) { }
}
