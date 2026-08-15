package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Two catchments connected by one physical opening for head equalization. */
public final class WaterEqualizationScenario implements VisualizerScenario {

    @Override public String id() { return "water-equalization"; }
    @Override public String title() { return "Water Equalization"; }
    @Override public String description() {
        return "Wet stone chamber feeds a dry absorbent chamber through one gate; heads should relax locally and conservatively.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_equal_stone");
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_equal_absorbent");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(40_000, 1L);

        for (int x = -5; x <= 5; x++) {
            LandscapeDefinitionId floor = x < 0 ? stone : absorbent;
            WaterScenarioSupport.fillFloor(
                    assembly, floor, x, x, -3, 3, -1);
        }
        WaterScenarioSupport.ringWalls(
                assembly, absorbent, -6, 6, -4, 4, 0, 0);

        // One-cell gate at (0,0,0). Every other crossing between chambers is
        // a real FullShape barrier, so any Water appearing on the right must
        // either come through this opening or from its own later saturation.
        for (int y = -3; y <= 3; y++) {
            if (y != 0) {
                assembly.placeTerrain(0, y, 0, absorbent);
            }
        }

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 16);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -6, 6, -4, 4, 0, 1);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                0.55f);
    }
}
