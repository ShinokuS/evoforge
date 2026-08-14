package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class CowForagingScenarioTest {

    @Test
    void scenarioExposesAuthoritativeDecisionDiagnostics() {
        ScenarioSession session = new CowForagingScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        session.runtime().stepper().advance();
        session.update();

        var trace = session.runtime().view().agents().lastDecision(cow);
        assertNotNull(trace);
        assertNotNull(trace.selected());
        assertEquals(2, trace.candidates().size());
        assertTrue(session.diagnostics().summary().contains("winner=hay"));
        assertEquals(2, session.diagnostics().cellCount());
    }
}
