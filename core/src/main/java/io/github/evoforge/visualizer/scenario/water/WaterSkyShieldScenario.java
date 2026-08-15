package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Exposed and roofed chambers for vertical sky-surface targeting. */
public final class WaterSkyShieldScenario implements VisualizerScenario {

    @Override public String id() { return "water-sky-shield"; }
    @Override public String title() { return "Water Sky Shield"; }
    @Override public String description() {
        return "Exposed stone receives rain while the matching chamber under an absorbent roof stays physically dry below.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_sky_stone");
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_sky_absorbent");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(80_000, 1L);

        WaterScenarioSupport.fillFloor(
                assembly, stone, -5, -1, -3, 3, -1);
        WaterScenarioSupport.fillFloor(
                assembly, stone, 1, 5, -3, 3, -1);
        WaterScenarioSupport.ringWalls(
                assembly, absorbent, -6, 6, -4, 4, 0, 0);

        // Divider prevents exposed left-hand runoff from entering the sheltered
        // chamber horizontally during the short observation window.
        for (int y = -3; y <= 3; y++) {
            assembly.placeTerrain(0, y, 0, absorbent);
        }

        // Right chamber roof is the highest sky surface. Rain therefore enters
        // this terrain's moisture store instead of magically reaching the floor.
        WaterScenarioSupport.fillFloor(
                assembly, absorbent, 1, 5, -3, 3, 1);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 8);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -6, 6, -4, 4, -1, 2);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                0.66f);
    }
}
