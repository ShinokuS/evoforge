package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Symmetric source that stresses deterministic bounded multi-edge outflow. */
public final class WaterSymmetricSplitScenario implements VisualizerScenario {

    @Override public String id() { return "water-symmetric-split"; }
    @Override public String title() { return "Water Symmetric Split"; }
    @Override public String description() {
        return "One central finite source relaxes into four equivalent directions; symmetry exposes ordering or outgoing-budget bias.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_split_stone");
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_split_absorbent");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(50_000, 1L);

        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                assembly.placeTerrain(
                        x,
                        y,
                        -1,
                        x == 0 && y == 0 ? stone : absorbent);
            }
        }
        WaterScenarioSupport.ringWalls(
                assembly, absorbent, -5, 5, -5, 5, 0, 0);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 10);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -5, 5, -5, 5, 0, 1);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                0.50f);
    }
}
