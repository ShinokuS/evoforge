package io.github.evoforge.visualizer.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ScenarioCatalogTest {
    @Test
    void standardCatalogKeepsDomainGroupsAndFocusedScenarioOrder() {
        ScenarioCatalog catalog = ScenarioCatalog.standard();
        assertEquals(List.of(
                        "geometry", "movement", "occupancy", "water", "agents", "pathfinding"),
                catalog.groups().stream().map(ScenarioGroup::id).toList());
        assertEquals(List.of(
                        "cutaway", "ramp-navigation", "timed-movement",
                        "movement-patrol", "movement-click-to-move",
                        "occupancy-contention",
                        "rain-hydrology", "generated-rainfall-regime",
                        "water-z-flow", "water-geometry-stress",
                        "agent-living-cow", "agent-living-cow-herd",
                        "agent-cow-foraging", "agent-cow-visual-search",
                        "pathfinding-straight", "pathfinding-structural-detour",
                        "pathfinding-weighted-detour", "pathfinding-ramp-3d",
                        "pathfinding-multi-level-climb", "pathfinding-z-switchback",
                        "pathfinding-vertical-overpass", "pathfinding-unreachable",
                        "pathfinding-hierarchy", "pathfinding-invalidation"),
                catalog.scenarios().stream().map(VisualizerScenario::id).toList());
    }

    @Test
    void creatingTheSameScenarioTwiceProducesFreshRuntime() {
        VisualizerScenario scenario = ScenarioCatalog.standard().get(2);
        ScenarioSession first = scenario.create();
        ScenarioSession second = scenario.create();
        assertNotSame(first.runtime(), second.runtime());
        assertEquals(0, first.runtime().time().tick());
        assertEquals(0, second.runtime().time().tick());
        assertEquals(first.view(), second.view());
    }

    @Test
    void duplicateIdsAcrossGroupsAreRejected() {
        VisualizerScenario duplicate = stub("same");
        assertThrows(IllegalArgumentException.class, () -> ScenarioCatalog.ofGroups(
                ScenarioGroup.of("first", "First", duplicate),
                ScenarioGroup.of("second", "Second", duplicate)));
    }

    @Test
    void duplicateGroupIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioCatalog.ofGroups(
                ScenarioGroup.of("same", "First", stub("one")),
                ScenarioGroup.of("same", "Second", stub("two"))));
    }

    private static VisualizerScenario stub(String id) {
        return new VisualizerScenario() {
            @Override public String id() { return id; }
            @Override public String title() { return "Scenario " + id; }
            @Override public String description() { return "Description " + id; }
            @Override public ScenarioSession create() { throw new UnsupportedOperationException(); }
        };
    }
}
