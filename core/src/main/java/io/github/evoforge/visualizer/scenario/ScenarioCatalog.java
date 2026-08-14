package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.visualizer.scenario.geometry.CutawayScenario;
import io.github.evoforge.visualizer.scenario.geometry.RampNavigationScenario;
import io.github.evoforge.visualizer.scenario.movement.MoveToInteractiveScenario;
import io.github.evoforge.visualizer.scenario.movement.MoveToPatrolScenario;
import io.github.evoforge.visualizer.scenario.movement.TimedMovementScenario;
import io.github.evoforge.visualizer.scenario.occupancy.OccupancyContentionScenario;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed grouped catalog shown by the development visualizer browser. */
public final class ScenarioCatalog {
    private final List<ScenarioGroup> groups;
    private final List<VisualizerScenario> scenarios;

    public ScenarioCatalog(List<VisualizerScenario> scenarios) {
        this(List.of(new ScenarioGroup("scenarios", "Scenarios", requireScenarioList(scenarios))), true);
    }

    private ScenarioCatalog(List<ScenarioGroup> groups, boolean grouped) {
        if (groups == null || groups.isEmpty()) {
            throw new IllegalArgumentException("scenario groups must not be empty");
        }
        List<ScenarioGroup> groupCopy = List.copyOf(groups);
        Set<String> groupIds = new HashSet<>();
        Set<String> scenarioIds = new HashSet<>();
        List<VisualizerScenario> flattened = new ArrayList<>();
        for (ScenarioGroup group : groupCopy) {
            if (group == null) throw new IllegalArgumentException("scenario group must not be null");
            if (!groupIds.add(group.id())) {
                throw new IllegalArgumentException("duplicate scenario group id: " + group.id());
            }
            for (VisualizerScenario scenario : group.scenarios()) {
                validateScenario(scenario);
                if (!scenarioIds.add(scenario.id())) {
                    throw new IllegalArgumentException("duplicate scenario id: " + scenario.id());
                }
                flattened.add(scenario);
            }
        }
        this.groups = groupCopy;
        this.scenarios = List.copyOf(flattened);
    }

    public static ScenarioCatalog ofGroups(ScenarioGroup... groups) {
        if (groups == null) throw new IllegalArgumentException("groups must not be null");
        return new ScenarioCatalog(List.of(groups), true);
    }

    public static ScenarioCatalog standard() {
        return ofGroups(
                ScenarioGroup.of("geometry", "Geometry & Navigation",
                        new CutawayScenario(), new RampNavigationScenario()),
                ScenarioGroup.of("movement", "Movement",
                        new TimedMovementScenario(), new MoveToPatrolScenario(), new MoveToInteractiveScenario()),
                ScenarioGroup.of("occupancy", "Occupancy",
                        new OccupancyContentionScenario()),
                ScenarioGroup.of("pathfinding", "Pathfinding",
                        new PathfindingStraightScenario(),
                        new PathfindingStructuralDetourScenario(),
                        new PathfindingWeightedDetourScenario(),
                        new PathfindingRampScenario(),
                        new PathfindingMultiLevelClimbScenario(),
                        new PathfindingZSwitchbackScenario(),
                        new PathfindingVerticalOverpassScenario(),
                        new PathfindingUnreachableScenario(),
                        new PathfindingHierarchyScenario(),
                        new PathfindingInvalidationScenario()));
    }

    public int size() { return scenarios.size(); }
    public VisualizerScenario get(int index) { return scenarios.get(index); }
    public List<VisualizerScenario> scenarios() { return scenarios; }
    public List<ScenarioGroup> groups() { return groups; }

    private static List<VisualizerScenario> requireScenarioList(List<VisualizerScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) throw new IllegalArgumentException("scenarios must not be empty");
        return scenarios;
    }

    private static void validateScenario(VisualizerScenario scenario) {
        if (scenario == null) throw new IllegalArgumentException("scenario must not be null");
        requireText(scenario.id(), "scenario id");
        requireText(scenario.title(), "scenario title");
        requireText(scenario.description(), "scenario description");
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }
}
