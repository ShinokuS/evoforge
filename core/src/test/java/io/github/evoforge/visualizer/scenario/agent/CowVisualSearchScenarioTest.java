package io.github.evoforge.visualizer.scenario.agent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import org.junit.jupiter.api.Test;

final class CowVisualSearchScenarioTest {
    @Test
    void scenarioShowsMultiCellExplorationBeforeConcreteFoodSelection() {
        ScenarioSession session = new CowVisualSearchScenario().create();
        ObjectId cow = session.runtime().view().cells().objectAt(0, 0, 0, 0);

        session.runtime().stepper().advance();
        session.update();
        assertNull(session.runtime().view().agents().currentTargetKey(cow));
        assertNotNull(session.runtime().view().searches().currentSearch(cow));
        assertTrue(session.diagnostics().summary().contains("search=SWEEPING:core:hunger"));
        assertTrue(session.diagnostics().summary().contains("grassVisible=false"));

        for (int tick = 0; tick < 3; tick++) session.runtime().stepper().advance();
        session.update();
        PathRoute explorationRoute = session.runtime().view().moveTo().activeRoute(cow);
        assertNotNull(explorationRoute);
        assertTrue(explorationRoute.size() >= 3);

        boolean sawExploring = session.diagnostics().summary().contains("search=EXPLORING:core:hunger");
        // Horizon-oriented unguided search is deliberately not biased toward the hidden Grass position.
        // Keep this as a liveness budget, not an exact-path/timing contract.
        for (int tick = 0; tick < 240 && session.runtime().view().agents().currentTargetKey(cow) == null; tick++) {
            session.runtime().stepper().advance();
            session.update();
            sawExploring |= session.diagnostics().summary().contains("search=EXPLORING:core:hunger");
        }

        assertTrue(sawExploring);
        assertNotNull(session.runtime().view().agents().currentTargetKey(cow));
        assertTrue(session.diagnostics().summary().contains("grassVisible=true"));
    }
}
