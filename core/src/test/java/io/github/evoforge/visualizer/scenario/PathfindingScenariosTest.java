package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class PathfindingScenariosTest {

    @Test
    void focusedScenariosExposeExpectedTerminalStatus() {
        assertStatus(new PathfindingStraightScenario(), "FOUND");
        assertStatus(new PathfindingStructuralDetourScenario(), "FOUND");
        assertStatus(new PathfindingWeightedDetourScenario(), "FOUND");
        assertStatus(new PathfindingRampScenario(), "FOUND");
        assertStatus(new PathfindingUnreachableScenario(), "NO_PATH");
        assertStatus(new PathfindingHierarchyScenario(), "FOUND");
        assertStatus(new PathfindingInvalidationScenario(), "STALE");
    }

    @Test
    void repeatedCreationReproducesPathDiagnostics() {
        for (VisualizerScenario scenario : List.of(
                new PathfindingStraightScenario(),
                new PathfindingWeightedDetourScenario(),
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
