package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class CowVisualSearchScenarioTest {
    @Test
    void scenarioShowsExplorationBeforeConcreteFoodSelection() {
        ScenarioSession session = new CowVisualSearchScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        session.runtime().stepper().advance();
        session.update();
        assertNull(session.runtime().view().agents().currentTarget(cow));
        assertNotNull(session.runtime().view().searches().currentSearch(cow));
        assertTrue(session.diagnostics().summary().contains("search=SWEEPING:core:hunger"));
        assertTrue(session.diagnostics().summary().contains("grassVisible=false"));

        boolean sawExploring = false;
        for (int tick = 0; tick < 80 && session.runtime().view().agents().currentTarget(cow) == null; tick++) {
            session.runtime().stepper().advance();
            session.update();
            sawExploring |= session.diagnostics().summary().contains("search=EXPLORING:core:hunger");
        }

        assertTrue(sawExploring);
        assertNotNull(session.runtime().view().agents().currentTarget(cow));
        assertTrue(session.diagnostics().summary().contains("grassVisible=true"));
    }
}
