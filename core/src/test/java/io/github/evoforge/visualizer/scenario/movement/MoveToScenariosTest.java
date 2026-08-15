package io.github.evoforge.visualizer.scenario.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
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
        while (!session.diagnostics().summary().contains("waypoint=2/5") && ticks++ < 300) advance(session);
        assertTrue(session.diagnostics().summary().contains("waypoint=2/5"));
        assertTrue(runtime.view().moveTo().isActive(mover));
        assertEquals(2, runtime.view().transforms().x(mover));
        assertEquals(2, runtime.view().transforms().y(mover));
        assertEquals(1, runtime.view().transforms().z(mover));
        assertEquals(2, goalZ(session.diagnostics()));
    }

    private static void advance(ScenarioSession session) {
        session.runtime().stepper().advance();
        session.update();
    }

    private static boolean hasRoute(ScenarioDiagnostics diagnostics) {
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            if (diagnostics.cell(index).style() == ScenarioCellMarkerStyle.ROUTE) return true;
        }
        return false;
    }

    private static int goalZ(ScenarioDiagnostics diagnostics) {
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.GOAL) return marker.z();
        }
        throw new AssertionError("goal marker missing");
    }
}
