package io.github.evoforge.visualizer.scenario.pathfinding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.visualizer.presentation.route.RoutePresentation;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
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
        RoutePresentation route = session.diagnostics().route();
        for (int index = 0; index < route.size(); index++) {
            if (route.y(index) != 0) usesSideLane = true;
            if (route.y(index) == 0 && route.x(index) == 8) crossesSolid = true;
            if (route.y(index) == 0 && route.x(index) == 14) crossesSlow = true;
        }
        assertTrue(usesSideLane, "fresh route must use one side lane");
        assertTrue(!crossesSolid, "fresh route must avoid the new solid block");
        assertTrue(!crossesSlow, "fresh route must avoid the expensive slow cell");
    }

    @Test
    void verticalScenariosExposeOneOrderedRouteAcrossMultipleZSlices() {
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
            assertEquals(first.diagnostics().summary(), second.diagnostics().summary());
            assertRouteEquals(first.diagnostics().route(), second.diagnostics().route());
        }
    }

    private static void assertRouteStaysOnCenter(ScenarioDiagnostics diagnostics) {
        RoutePresentation route = diagnostics.route();
        assertTrue(!route.empty(), "expected visible route presentation");
        for (int index = 0; index < route.size(); index++) {
            assertEquals(0, route.y(index), "visible pre-change route must stay on the center lane");
        }
    }

    private static void assertRouteZSlices(VisualizerScenario scenario, int minimumSlices) {
        RoutePresentation route = scenario.create().diagnostics().route();
        Set<Integer> routeZ = new HashSet<>();
        for (int index = 0; index < route.size(); index++) routeZ.add(route.z(index));
        assertTrue(routeZ.size() >= minimumSlices, scenario.id() + ": route Z slices=" + routeZ);
    }

    private static void assertRouteEquals(RoutePresentation first, RoutePresentation second) {
        assertEquals(first.size(), second.size());
        for (int index = 0; index < first.size(); index++) {
            assertEquals(first.x(index), second.x(index));
            assertEquals(first.y(index), second.y(index));
            assertEquals(first.z(index), second.z(index));
        }
    }

    private static void assertStatus(VisualizerScenario scenario, String expectedStatus) {
        assertSummaryStartsWith(scenario.create(), "status=" + expectedStatus);
    }

    private static void assertSummaryStartsWith(ScenarioSession session, String expectedPrefix) {
        String summary = session.diagnostics().summary();
        assertTrue(summary.startsWith(expectedPrefix), expectedPrefix + " expected, got: " + summary);
    }
}
