package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import io.github.evoforge.visualizer.scenario.ScenarioSession;

/** Deep contained basin that deliberately creates Water across several Z cells. */
public final class WaterZStackScenario implements VisualizerScenario {

    @Override public String id() { return "water-z-stack"; }
    @Override public String title() { return "Water Z Stack"; }
    @Override public String description() {
        return "Deep finite basin spanning multiple Z cells. Use PgUp/PgDn to verify cutaway Water continuity.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_stack_stone");
        LandscapeDefinitionId wall =
                assembly.landscapeDefinition("scenario:water_stack_wall");
        assembly.soilHydrology(wall, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(50_000, 1L);

        WaterScenarioSupport.fillFloor(
                assembly, stone, -2, 2, -2, 2, -1);
        WaterScenarioSupport.ringWalls(
                assembly, wall, -3, 3, -3, 3, 0, 3);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 46);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -3, 3, -3, 3, 0, 4);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(2, 0f, 0f, 1f),
                diagnostics,
                0.65f);
    }
}
