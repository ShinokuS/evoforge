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
        assertStatus(new PathfindingInvalidationScenario(), "FOUND");
    }

    @Test
    void invalidationScenarioKeepsVisibleInitialRouteThenReplans() {
        ScenarioSession session = new PathfindingInvalidationScenario().create();

        assertRouteStaysOnCenter(session.diagnostics());

        for (int tick = 1; tick <= 3; tick++) {
            session.runtime().stepper().advance();
            session.update();
            assertSummaryStartsWith(session, "status=FOUND");
            assertRouteStaysOnCenter(session.diagnostics());
        }

        session.runtime().stepper().advance();
        session.update();
        assertSummaryStartsWith(session, "status=STALE");
        assertRouteStaysOnCenter(session.diagnostics());

        session.runtime().stepper().advance();
        session.update();
        assertSummaryStartsWith(session, "status=FOUND");

        boolean usesSideLane = false;
        boolean crossesSolid = false;
        boolean crossesSlow = false;
        ScenarioDiagnostics diagnostics = session.diagnostics();
        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() != ScenarioCellMarkerStyle.ROUTE) {
                continue;
            }
            if (marker.y() != 0) {
                usesSideLane = true;
            }
            if (marker.y() == 0 && marker.x() == 8) {
                crossesSolid = true;
            }
            if (marker.y() == 0 && marker.x() == 14) {
                crossesSlow = true;
            }
        }

        assertTrue(usesSideLane, "fresh route must use one side lane");
        assertTrue(!crossesSolid, "fresh route must avoid the new solid block");
        assertTrue(!crossesSlow, "fresh route must avoid the expensive slow cell");
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

    private static void assertRouteStaysOnCenter(
            ScenarioDiagnostics diagnostics) {

        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            if (marker.style() == ScenarioCellMarkerStyle.ROUTE) {
                assertEquals(
                        0,
                        marker.y(),
                        "visible pre-change route must stay on the center lane");
            }
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
