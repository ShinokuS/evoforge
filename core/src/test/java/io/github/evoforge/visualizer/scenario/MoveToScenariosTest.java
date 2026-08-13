package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

final class MoveToScenariosTest {

    @Test
    void patrolContinuesToTheNextWaypoint() {
        ScenarioSession session = new MoveToPatrolScenario().create();
        SimulationRuntime runtime = session.runtime();
        ObjectId mover = runtime.view().cells().objectAt(-4, -3, 1, 0);

        assertTrue(runtime.view().moveTo().isActive(mover));
        assertTrue(hasRoute(session.diagnostics()));

        int ticks = 0;
        while (!session.diagnostics().summary().contains("waypoint=2/4")
                && ticks++ < 100) {
            advance(session);
        }

        assertTrue(session.diagnostics().summary().contains("waypoint=2/4"));
        assertTrue(runtime.view().moveTo().isActive(mover));
        assertEquals(4, runtime.view().transforms().x(mover));
        assertEquals(-3, runtime.view().transforms().y(mover));
    }

    @Test
    void interactiveScenarioMovesSelectedObjectAndKeepsAcceptedRouteVisible() {
        ScenarioSession session = new MoveToInteractiveScenario().create();
        SimulationRuntime runtime = session.runtime();
        ObjectId mover = runtime.view().cells().objectAt(-4, 0, 1, 0);

        session.controller().primaryCellAction(-4, 0, 1);
        assertTrue(session.controller().secondaryCellAction(4, 0, 1));
        assertTrue(runtime.view().moveTo().isActive(mover));
        assertTrue(hasRoute(session.diagnostics()));
        assertEquals(4, goalX(session.diagnostics()));

        session.controller().secondaryCellAction(0, 4, 1);
        assertEquals(4, goalX(session.diagnostics()));

        int ticks = 0;
        while (runtime.view().moveTo().isActive(mover) && ticks++ < 100) {
            advance(session);
        }

        assertTrue(!runtime.view().moveTo().isActive(mover));
        assertEquals(4, runtime.view().transforms().x(mover));
        assertEquals(0, runtime.view().transforms().y(mover));
        assertNotNull(runtime.view().moveTo().lastCompletion(mover));
        assertTrue(runtime.view().moveTo().lastCompletion(mover).reachedGoal());
    }

    private static void advance(ScenarioSession session) {
        session.runtime().stepper().advance();
        session.update();
    }

    private static boolean hasRoute(ScenarioDiagnostics diagnostics) {
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            if (diagnostics.cell(index).style() == ScenarioCellMarkerStyle.ROUTE) {
                return true;
            }
        }
        return false;
    }

    private static int goalX(ScenarioDiagnostics diagnostics) {
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.GOAL) {
                return marker.x();
            }
        }
        throw new AssertionError("goal marker missing");
    }
}
