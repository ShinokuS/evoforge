package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;
import org.junit.jupiter.api.Test;

final class MoveToScenariosTest {

    @Test
    void patrolContinuesToTheNextWaypointOnAnotherZLevel() {
        ScenarioSession session = new MoveToPatrolScenario().create();
        SimulationRuntime runtime = session.runtime();
        ObjectId mover = runtime.view().cells().objectAt(-4, -2, 0, 0);

        assertTrue(runtime.view().moveTo().isActive(mover));
        assertTrue(hasRoute(session.diagnostics()));
        assertEquals(1, goalZ(session.diagnostics()));

        int ticks = 0;
        while (!session.diagnostics().summary().contains("waypoint=2/5")
                && ticks++ < 300) {
            advance(session);
        }

        assertTrue(session.diagnostics().summary().contains("waypoint=2/5"));
        assertTrue(runtime.view().moveTo().isActive(mover));
        assertEquals(2, runtime.view().transforms().x(mover));
        assertEquals(2, runtime.view().transforms().y(mover));
        assertEquals(1, runtime.view().transforms().z(mover));
        assertEquals(2, goalZ(session.diagnostics()));
    }

    @Test
    void interactiveScenarioMovesSelectedObjectUpAndDownAcrossZLevels() {
        ScenarioSession session = new MoveToInteractiveScenario().create();
        SimulationRuntime runtime = session.runtime();
        ObjectId mover = runtime.view().cells().objectAt(-4, -2, 0, 0);

        session.controller().primaryCellAction(-4, -2, 0);
        assertTrue(session.controller().secondaryCellAction(14, 0, 4));
        assertTrue(runtime.view().moveTo().isActive(mover));
        assertTrue(hasRoute(session.diagnostics()));
        assertEquals(14, goalX(session.diagnostics()));
        assertEquals(4, goalZ(session.diagnostics()));

        session.controller().secondaryCellAction(6, 0, 2);
        assertEquals(14, goalX(session.diagnostics()));
        assertEquals(4, goalZ(session.diagnostics()));

        advanceUntilIdle(session, mover);

        assertEquals(14, runtime.view().transforms().x(mover));
        assertEquals(0, runtime.view().transforms().y(mover));
        assertEquals(4, runtime.view().transforms().z(mover));
        assertNotNull(runtime.view().moveTo().lastCompletion(mover));
        assertTrue(runtime.view().moveTo().lastCompletion(mover).reachedGoal());

        assertTrue(session.controller().secondaryCellAction(-4, 0, 0));
        assertEquals(0, goalZ(session.diagnostics()));
        advanceUntilIdle(session, mover);

        assertEquals(-4, runtime.view().transforms().x(mover));
        assertEquals(0, runtime.view().transforms().y(mover));
        assertEquals(0, runtime.view().transforms().z(mover));
        assertTrue(runtime.view().moveTo().lastCompletion(mover).reachedGoal());
    }

    private static void advanceUntilIdle(
            ScenarioSession session,
            ObjectId mover) {

        int ticks = 0;
        while (session.runtime().view().moveTo().isActive(mover)
                && ticks++ < 300) {
            advance(session);
        }
        assertTrue(!session.runtime().view().moveTo().isActive(mover));
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

    private static int goalZ(ScenarioDiagnostics diagnostics) {
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.GOAL) {
                return marker.z();
            }
        }
        throw new AssertionError("goal marker missing");
    }
}
