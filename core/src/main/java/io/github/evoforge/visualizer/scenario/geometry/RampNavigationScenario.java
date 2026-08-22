package io.github.evoforge.visualizer.scenario.geometry;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.geometry.RampShape;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Shape/navigation scene with joined ramp banks and a three-level ramp chain. */
public final class RampNavigationScenario implements VisualizerScenario {

    @Override public String id() { return "ramp-navigation"; }
    @Override public String title() { return "Ramp Navigation"; }
    @Override public String description() {
        return "Joined ramp banks, lateral traversal and a successive vertical chain. Use F2/F3.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition(
                "scenario:ramp_ground");

        ScenarioTerrain.fill(assembly, ground, -10, 10, -8, 8, -1);
        ScenarioTerrain.fill(assembly, ground, -7, 7, -5, 5, 0);

        // Parallel ramps make the generic side-join behavior visually obvious.
        for (int x = -2; x <= 2; x++) {
            ScenarioTerrain.placeRamp(
                    assembly, ground, x, -6, 0, RampShape.POSITIVE_Y);
            ScenarioTerrain.placeRamp(
                    assembly, ground, x, 6, 0, RampShape.NEGATIVE_Y);
        }
        ScenarioTerrain.placeRamp(assembly, ground, -8, 0, 0, RampShape.POSITIVE_X);
        ScenarioTerrain.placeRamp(assembly, ground, 8, 0, 0, RampShape.NEGATIVE_X);

        ScenarioTerrain.fill(assembly, ground, 1, 6, -4, 4, 1);
        assembly.setShape(1, -4, 1, RampShape.POSITIVE_X);
        ScenarioTerrain.fill(assembly, ground, 3, 6, 2, 4, 2);
        ScenarioTerrain.placeRamp(assembly, ground, 2, 3, 2, RampShape.POSITIVE_X);
        ScenarioTerrain.fill(assembly, ground, 5, 6, 2, 4, 3);
        ScenarioTerrain.placeRamp(assembly, ground, 4, 3, 3, RampShape.POSITIVE_X);

        return new ScenarioSession(
                assembly.start(),
                new ScenarioView(1, 0f, 0f, 1f));
    }
}
