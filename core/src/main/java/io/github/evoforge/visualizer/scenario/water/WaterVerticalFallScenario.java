package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Elevated rain collector with open edges above a dry lower catchment. */
public final class WaterVerticalFallScenario implements VisualizerScenario {

    @Override public String id() { return "water-vertical-fall"; }
    @Override public String title() { return "Water Vertical Fall"; }
    @Override public String description() {
        return "Elevated finite runoff falls through open Z cells one solver step at a time into the lower basin.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_fall_absorbent");
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_fall_stone");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(80_000, 1L);

        WaterScenarioSupport.fillFloor(
                assembly, absorbent, -5, 5, -4, 4, -1);
        WaterScenarioSupport.ringWalls(
                assembly, absorbent, -6, 6, -5, 5, 0, 1);

        // Only this high platform immediately creates free Water. The lower
        // catchment still absorbs the first rain pulses, so falling Water is
        // visually attributable to the elevated source rather than local rain.
        WaterScenarioSupport.fillFloor(
                assembly, stone, -4, -2, -1, 1, 2);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 6);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -6, 6, -5, 5, 0, 4);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(3, -1f, 0f, 1f),
                diagnostics,
                0.72f);
    }
}
