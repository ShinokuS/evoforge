package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Long local detour around a real FullShape barrier. */
public final class WaterBarrierDetourScenario implements VisualizerScenario {

    @Override public String id() { return "water-barrier-detour"; }
    @Override public String title() { return "Water Barrier Detour"; }
    @Override public String description() {
        return "Localized runoff must route around a long solid wall through the only open end; no wall crossing or basin teleportation.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_detour_stone");
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_detour_absorbent");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(40_000, 1L);

        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                boolean source = x >= -5 && x <= -3
                        && y >= -1 && y <= 1;
                assembly.placeTerrain(
                        x,
                        y,
                        -1,
                        source ? stone : absorbent);
            }
        }
        WaterScenarioSupport.ringWalls(
                assembly, absorbent, -6, 6, -4, 4, 0, 0);

        // The wall touches the lower side of the enclosure and stops before y=3.
        // Therefore Water can reach the right chamber only by travelling around
        // the upper end at (0,3,0).
        for (int y = -3; y <= 2; y++) {
            assembly.placeTerrain(0, y, 0, absorbent);
        }

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 18);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -6, 6, -4, 4, 0, 1);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                0.52f);
    }
}
