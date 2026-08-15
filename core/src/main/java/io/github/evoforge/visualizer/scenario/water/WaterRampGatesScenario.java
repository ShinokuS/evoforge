package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Mirrored channels that exercise the low/high physical faces of one Ramp orientation. */
public final class WaterRampGatesScenario implements VisualizerScenario {

    @Override public String id() { return "water-ramp-gates"; }
    @Override public String title() { return "Water Ramp Gates"; }
    @Override public String description() {
        return "Two channels approach POSITIVE_X ramps from opposite faces: low face admits flow, high face blocks it.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_ramp_stone");
        LandscapeDefinitionId absorbent =
                assembly.landscapeDefinition("scenario:water_ramp_absorbent");
        assembly.soilHydrology(absorbent, 1_000_000, 1_000_000);
        assembly.periodicPrecipitation(50_000, 1L);

        for (int x = -4; x <= 4; x++) {
            LandscapeDefinitionId lowerFloor = x >= -4 && x <= -2
                    ? stone : absorbent;
            LandscapeDefinitionId upperFloor = x >= 2 && x <= 4
                    ? stone : absorbent;
            assembly.placeTerrain(x, -2, -1, lowerFloor);
            assembly.placeTerrain(x, 2, -1, upperFloor);
        }

        // Channel walls remain absorbent throughout the short acceptance window,
        // so only the stone source pads and the Ramp anchors create free Water.
        for (int x = -5; x <= 5; x++) {
            assembly.placeTerrain(x, -3, 0, absorbent);
            assembly.placeTerrain(x, -1, 0, absorbent);
            assembly.placeTerrain(x, 1, 0, absorbent);
            assembly.placeTerrain(x, 3, 0, absorbent);
        }
        for (int y : new int[] {-2, 2}) {
            assembly.placeTerrain(-5, y, 0, absorbent);
            assembly.placeTerrain(5, y, 0, absorbent);
            assembly.placeTerrain(0, y, 0, stone);
            assembly.setShape(0, y, 0, RampShape.POSITIVE_X);
        }

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 12);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, -5, 5, -3, 3, 0, 1);

        return WaterScenarioSupport.rainySession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics,
                0.58f);
    }
}
