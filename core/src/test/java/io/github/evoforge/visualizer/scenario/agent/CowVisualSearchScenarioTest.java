package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class CowVisualSearchScenarioTest {
    @Test
    void scenarioExposesSearchBeforeConcreteFoodSelection() {
        ScenarioSession session = new CowVisualSearchScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        session.runtime().stepper().advance();
        session.update();
        assertNull(session.runtime().view().agents().currentTarget(cow));
        assertNotNull(session.runtime().view().searches().currentSearch(cow));
        assertTrue(session.diagnostics().summary().contains("search=SWEEPING:core:hunger"));

        session.runtime().stepper().advance();
        session.runtime().stepper().advance();
        session.update();
        assertNotNull(session.runtime().view().agents().currentTarget(cow));
        assertTrue(session.diagnostics().summary().contains("grassVisible=true"));
    }
}
