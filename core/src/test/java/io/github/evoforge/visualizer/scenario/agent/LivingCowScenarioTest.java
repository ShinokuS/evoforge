package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class LivingCowScenarioTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");

    @Test
    void scenarioClosesPhysiologyDecisionTimedUseAndRegrowthIntoOneProductionRuntime() {
        ScenarioSession session = new LivingCowScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        assertEquals(0, session.runtime().view().needs().level(cow, HUNGER));
        assertEquals(
                ObjectVisualFamily.CREATURE,
                session.objectPresentations().get(
                        session.runtime().view().objects().get(cow).definitionId()).family());

        AgentIntentTrace use = null;
        for (int tick = 0; tick < 180; tick++) {
            session.runtime().stepper().advance();
            session.update();
            AgentIntentTrace intent = session.runtime().view().agents().currentIntent(cow);
            if (intent != null && intent.phase() == AgentIntentPhase.USING_OPPORTUNITY) {
                use = intent;
                break;
            }
        }

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
        assertTrue(session.runtime().view().consumableStocks().quantity(use.targetId()) <= stockBeforeCompletion);
        assertTrue(session.diagnostics().summary().contains("Hunger"));
    }
}
