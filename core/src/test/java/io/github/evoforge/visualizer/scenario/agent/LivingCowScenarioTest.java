package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class LivingCowScenarioTest {

    @Test
    void scenarioCombinesSparseRainPuddlesEdgeLakeAndAutonomousNeeds() {
        ScenarioSession session = new LivingCowScenario().create();
        ObjectId meadowCow = session.runtime().view().cells().objectAt(0, 0, LivingCowScenario.STANDING_Z, 0);
        ObjectId lakeCow = session.runtime().view().cells().objectAt(
                LivingCowScenario.LAKE_COW_START_X, 0, LivingCowScenario.STANDING_Z, 0);
        assertNotNull(meadowCow);
        assertNotNull(lakeCow);
        assertEquals(
                ObjectVisualFamily.CREATURE,
                session.objectPresentations().get(
                        session.runtime().view().objects().get(meadowCow).definitionId()).family());
        assertEquals(0, session.runtime().view().needs().level(meadowCow, LivingCowScenario.HUNGER));
        assertEquals(0, session.runtime().view().needs().level(meadowCow, LivingCowScenario.THIRST));
        assertEquals(WeatherPresentationKind.CLEAR, session.weather().current().kind());
        int initialLakeWater = session.runtime().view().water().amount(
                LivingCowScenario.LAKE_MIN_X, 0, LivingCowScenario.LAKE_WATER_Z);
        assertTrue(initialLakeWater > 0);

        boolean sawFirstRainWindow = false;
        boolean sawSameLevelPuddleDrink = false;
        boolean sawLowerLakeDrink = false;
        boolean sawGrazing = false;
        boolean sawDryWeather = false;
        boolean sawSecondRainWindow = false;
        int maxPuddleCells = 0;

        for (int tick = 0; tick < LivingCowScenario.CLIMATE_CYCLE_TICKS + 12; tick++) {
            session.runtime().stepper().advance();
            session.update();

            assertDifferentCells(session, meadowCow, lakeCow);
            maxPuddleCells = Math.max(maxPuddleCells, countMeadowPuddles(session));
            for (ObjectId cow : new ObjectId[] {meadowCow, lakeCow}) {
                AgentIntentTrace intent = session.runtime().view().agents().currentIntent(cow);
                if (intent == null || intent.phase() != AgentIntentPhase.USING_OPPORTUNITY) continue;
                if ("needs:satisfaction".equals(intent.providerId())) sawGrazing = true;
                if ("needs:liquid_drink".equals(intent.providerId())) {
                    int targetZ = liquidTargetZ(intent.targetKey());
                    sawSameLevelPuddleDrink |= targetZ == LivingCowScenario.STANDING_Z;
                    sawLowerLakeDrink |= targetZ == LivingCowScenario.LAKE_WATER_Z;
                }
            }

            long currentTick = session.runtime().time().tick();
            if (currentTick == 1L) {
                sawFirstRainWindow = session.weather().current().kind() == WeatherPresentationKind.RAIN;
            }
            if (currentTick == LivingCowScenario.RAIN_ACTIVE_TICKS + 1L) {
                sawDryWeather = session.weather().current().kind() == WeatherPresentationKind.CLEAR;
            }
            if (currentTick == LivingCowScenario.CLIMATE_CYCLE_TICKS + 1L) {
                sawSecondRainWindow = session.weather().current().kind() == WeatherPresentationKind.RAIN;
            }
        }

        assertTrue(maxPuddleCells > 0, "rain must create temporary free-Water puddles");
        assertTrue(maxPuddleCells <= 25, "puddles must remain sparse rather than cover the meadow");
        assertTrue(sawSameLevelPuddleDrink, "a Cow must use a rain-created same-level puddle");
        assertTrue(sawLowerLakeDrink, "a Cow must drink the lower edge lake from shoreline level");
        assertTrue(sawGrazing, "the same autonomous layer must also keep using plant opportunities");
        assertTrue(sawFirstRainWindow, "the first climate window must visibly rain");
        assertTrue(sawDryWeather, "rain must end after the active window");
        assertTrue(sawSecondRainWindow, "the climate must return to rain on the next cycle");
        assertTrue(session.runtime().view().water().amount(
                LivingCowScenario.LAKE_MIN_X, 0, LivingCowScenario.LAKE_WATER_Z) > 0,
                "the finite edge lake should survive one climate cycle");
        assertTrue(session.diagnostics().summary().contains(" H "));
        assertTrue(session.diagnostics().summary().contains(" T "));
    }

    private static int countMeadowPuddles(ScenarioSession session) {
        int count = 0;
        for (int x = LivingCowScenario.MIN_X; x <= LivingCowScenario.MAX_X; x++) {
            for (int y = LivingCowScenario.MIN_Y; y <= LivingCowScenario.MAX_Y; y++) {
                if (LivingCowScenario.insideLake(x, y)) continue;
                if (session.runtime().view().water().amount(x, y, LivingCowScenario.STANDING_Z) > 0) count++;
            }
        }
        return count;
    }

    private static void assertDifferentCells(ScenarioSession session, ObjectId first, ObjectId second) {
        int firstX = session.runtime().view().transforms().x(first);
        int firstY = session.runtime().view().transforms().y(first);
        int firstZ = session.runtime().view().transforms().z(first);
        int secondX = session.runtime().view().transforms().x(second);
        int secondY = session.runtime().view().transforms().y(second);
        int secondZ = session.runtime().view().transforms().z(second);
        assertTrue(firstX != secondX || firstY != secondY || firstZ != secondZ,
                "exclusive Cows must never overlap");
    }

    private static int liquidTargetZ(String targetKey) {
        if (targetKey == null || !targetKey.startsWith("liquid:")) return Integer.MIN_VALUE;
        int hash = targetKey.lastIndexOf('#');
        int comma = targetKey.lastIndexOf(',', hash);
        if (comma < 0 || hash <= comma) return Integer.MIN_VALUE;
        return Integer.parseInt(targetKey.substring(comma + 1, hash));
    }
}
