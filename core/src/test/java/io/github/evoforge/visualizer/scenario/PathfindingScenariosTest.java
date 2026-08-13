package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PathfindingScenariosTest {

    @Test
    void focusedScenariosExposeExpectedInitialStatus() {
        assertStatus(new PathfindingStraightScenario(), "FOUND");
        assertStatus(new PathfindingStructuralDetourScenario(), "FOUND");
        assertStatus(new PathfindingWeightedDetourScenario(), "FOUND");
        assertStatus(new PathfindingRampScenario(), "FOUND");
        assertStatus(new PathfindingMultiLevelClimbScenario(), "FOUND");
        assertStatus(new PathfindingZSwitchbackScenario(), "FOUND");
        assertStatus(new PathfindingVerticalOverpassScenario(), "FOUND");
        assertStatus(new PathfindingUnreachableScenario(), "NO_PATH");
        assertStatus(new PathfindingHierarchyScenario(), "FOUND");
        assertStatus(new PathfindingInvalidationScenario(), "RUNNING");
    }

    @Test
    void invalidationScenarioShowsStaleThenFreshDetourAcrossTicks() {
        ScenarioSession session = new PathfindingInvalidationScenario().create();

        for (int tick = 1; tick <= 3; tick++) {
            session.runtime().stepper().advance();
            session.update();
            assertSummaryStartsWith(session, "status=RUNNING");
        }

        session.runtime().stepper().advance();
        session.update();
        assertSummaryStartsWith(session, "status=STALE");

        session.runtime().stepper().advance();
        session.update();
        assertSummaryStartsWith(session, "status=FOUND");

        boolean leavesDirectLine = false;
        ScenarioDiagnostics diagnostics = session.diagnostics();
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.ROUTE
                    && marker.y() != 0) {
                leavesDirectLine = true;
                break;
            }
        }
        assertTrue(leavesDirectLine, "fresh route must detour around the new block");
    }

    @Test
    void verticalScenariosExposeRouteAcrossMultipleZSlices() {
        assertRouteZSlices(new PathfindingMultiLevelClimbScenario(), 4);
        assertRouteZSlices(new PathfindingZSwitchbackScenario(), 3);
        assertRouteZSlices(new PathfindingVerticalOverpassScenario(), 3);
    }

    @Test
    void repeatedCreationReproducesPathDiagnostics() {
        for (VisualizerScenario scenario : List.of(
                new PathfindingStraightScenario(),
                new PathfindingWeightedDetourScenario(),
                new PathfindingMultiLevelClimbScenario(),
                new PathfindingVerticalOverpassScenario(),
                new PathfindingHierarchyScenario(),
                new PathfindingInvalidationScenario())) {

            ScenarioSession first = scenario.create();
            ScenarioSession second = scenario.create();

            assertEquals(first.view(), second.view());
            assertEquals(
                    first.diagnostics().summary(),
                    second.diagnostics().summary());
            assertEquals(
                    first.diagnostics().cellCount(),
                    second.diagnostics().cellCount());
        }
    }

    private static void assertRouteZSlices(
            VisualizerScenario scenario,
            int minimumSlices) {

        ScenarioDiagnostics diagnostics = scenario.create().diagnostics();
        Set<Integer> routeZ = new HashSet<>();

        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.ROUTE) {
                routeZ.add(marker.z());
            }
        }

        assertTrue(
                routeZ.size() >= minimumSlices,
                scenario.id() + ": route Z slices=" + routeZ);
    }

    private static void assertStatus(
            VisualizerScenario scenario,
            String expectedStatus) {

        ScenarioSession session = scenario.create();
        assertSummaryStartsWith(session, "status=" + expectedStatus);
    }

    private static void assertSummaryStartsWith(
            ScenarioSession session,
            String expectedPrefix) {

        String summary = session.diagnostics().summary();
        assertTrue(
                summary.startsWith(expectedPrefix),
                expectedPrefix + " expected, got: " + summary);
    }
}
