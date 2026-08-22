package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Bounded multi-Z Water stress: one deep pool plus one vertical falling shaft. */
public final class WaterZStackScenario implements VisualizerScenario {

    private static final int MIN_X = -9;
    private static final int MAX_X = 9;
    private static final int MIN_Y = -5;
    private static final int MAX_Y = 5;
    private static final int MIN_Z = -1;
    private static final int MAX_Z = 4;

    @Override public String id() { return "water-z-flow"; }
    @Override public String title() { return "Water Z Flow"; }
    @Override public String description() {
        return "Rain-free finite Water: deep stacked pool plus a vertical fall. PgUp/PgDn checks cutaway continuity.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, MIN_Z, MAX_Z);
        MaterialDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_z_stone");
        MaterialDefinitionId wall =
                assembly.landscapeDefinition("scenario:water_z_wall");

        // Left: a 2x2 deep pool. Four completely full lower cells plus a finite
        // upper volume force one real Water column to occupy more than one Z.
        WaterScenarioSupport.fillFloor(
                assembly, stone, -6, -5, -1, 0, -1);
        WaterScenarioSupport.ringWalls(
                assembly, wall, -7, -4, -2, 1, 0, 2);
        for (int x = -6; x <= -5; x++) {
            for (int y = -1; y <= 0; y++) {
                assembly.initialWater(x, y, 0, CellVolume.FULL);
            }
        }
        assembly.initialWater(-6, -1, 1, 600_000);

        // Right: one open vertical shaft. Side FullShape cells prevent horizontal
        // escape while Water descends one local edge per hydraulic update.
        assembly.placeTerrain(4, 0, -1, stone);
        for (int z = 0; z <= 3; z++) {
            for (int x = 3; x <= 5; x++) {
                for (int y = -1; y <= 1; y++) {
                    if (x == 4 && y == 0) {
                        continue;
                    }
                    assembly.placeTerrain(x, y, z, wall);
                }
            }
        }
        assembly.initialWater(4, 0, 3, 800_000);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 2);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, 0, MAX_Z);

        return WaterScenarioSupport.clearSession(
                runtime,
                new ScenarioView(2, 0f, 0f, 1f),
                diagnostics);
    }
}
