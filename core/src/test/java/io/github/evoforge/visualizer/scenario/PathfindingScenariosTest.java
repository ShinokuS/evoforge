package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PathfindingScenariosTest {

    @Test
    void focusedScenariosExposeExpectedTerminalStatus() {
        assertStatus(new PathfindingStraightScenario(), "FOUND");
        assertStatus(new PathfindingStructuralDetourScenario(), "FOUND");
        assertStatus(new PathfindingWeightedDetourScenario(), "FOUND");
        assertStatus(new PathfindingRampScenario(), "FOUND");
        assertStatus(new PathfindingMultiLevelClimbScenario(), "FOUND");
        assertStatus(new PathfindingZSwitchbackScenario(), "FOUND");
        assertStatus(new PathfindingVerticalOverpassScenario(), "FOUND");
        assertStatus(new PathfindingUnreachableScenario(), "NO_PATH");
        assertStatus(new PathfindingHierarchyScenario(), "FOUND");
        assertStatus(new PathfindingInvalidationScenario(), "STALE");
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
                new PathfindingHierarchyScenario())) {

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

        String summary = scenario.create()
                .diagnostics()
                .summary();

        assertTrue(
                summary.startsWith(
                        "status=" + expectedStatus),
                scenario.id() + ": " + summary);
    }
}
