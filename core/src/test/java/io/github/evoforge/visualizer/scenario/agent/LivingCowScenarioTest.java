package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.search.AgentSearchStatus;
import io.github.evoforge.simulation.world.agent.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class LivingCowScenarioTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");

    @Test
    void scenarioRequiresRealExplorationBeforeClosingLivingCowCycle() {
        ScenarioSession session = new LivingCowScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        assertEquals(0, session.runtime().view().needs().level(cow, HUNGER));
        assertEquals(
                ObjectVisualFamily.CREATURE,
                session.objectPresentations().get(
                        session.runtime().view().objects().get(cow).definitionId()).family());
        assertTrue(session.runtime().view().vision().snapshot(cow).objects().isEmpty());

        boolean explored = false;
        int maxDistanceFromStart = 0;
        AgentIntentTrace use = null;
        for (int tick = 0; tick < 360; tick++) {
            session.runtime().stepper().advance();
            session.update();

            AgentSearchTrace search = session.runtime().view().searches().currentSearch(cow);
            if (search != null && search.status() == AgentSearchStatus.EXPLORING) {
                explored = true;
            }

            int x = session.runtime().view().transforms().x(cow);
            int y = session.runtime().view().transforms().y(cow);
            maxDistanceFromStart = Math.max(
                    maxDistanceFromStart,
                    Math.max(Math.abs(x), Math.abs(y)));

            AgentIntentTrace intent = session.runtime().view().agents().currentIntent(cow);
            if (intent != null && intent.phase() == AgentIntentPhase.USING_OPPORTUNITY) {
                use = intent;
                break;
            }
        }

        assertTrue(explored, "Living Cow must enter unguided exploration before finding food");
        assertTrue(maxDistanceFromStart >= 3, "Living Cow must physically expand search away from the start");
        assertNotNull(use);
        assertNotNull(use.targetId());
        assertTrue(use.expectedCompletionTick() > use.startedTick());
        assertTrue(session.runtime().view().consumableStocks().has(use.targetId()));
        assertTrue(session.runtime().view().growth().has(use.targetId()));
        assertEquals(
                ObjectVisualFamily.VEGETATION,
                session.objectPresentations().get(
                        session.runtime().view().objects().get(use.targetId()).definitionId()).family());

        long hungerBeforeCompletion = session.runtime().view().needs().level(cow, HUNGER);
        long stockBeforeCompletion = session.runtime().view().consumableStocks().quantity(use.targetId());
        while (session.runtime().time().tick() < use.expectedCompletionTick()) {
            session.runtime().stepper().advance();
            session.update();
        }

        assertTrue(session.runtime().view().needs().level(cow, HUNGER) < hungerBeforeCompletion);
        assertTrue(session.runtime().view().consumableStocks().quantity(use.targetId()) < stockBeforeCompletion);
        assertTrue(session.diagnostics().summary().contains("Hunger"));
    }
}
