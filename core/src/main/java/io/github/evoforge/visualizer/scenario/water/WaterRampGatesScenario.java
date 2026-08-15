package io.github.evoforge.visualizer.scenario.water;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Bounded horizontal Water stress combining symmetry, barriers and Ramp faces. */
public final class WaterRampGatesScenario implements VisualizerScenario {

    private static final int MIN_X = -10;
    private static final int MAX_X = 10;
    private static final int MIN_Y = -6;
    private static final int MAX_Y = 6;

    @Override public String id() { return "water-geometry-stress"; }
    @Override public String title() { return "Water Geometry Stress"; }
    @Override public String description() {
        return "Rain-free stress map: symmetric split, long barrier detour/equalization and opposite Ramp faces.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, -1, 1);
        LandscapeDefinitionId stone =
                assembly.landscapeDefinition("scenario:water_geometry_stone");
        LandscapeDefinitionId wall =
                assembly.landscapeDefinition("scenario:water_geometry_wall");

        // One finite floor makes the map a closed shallow hydraulic domain. The
        // explicit world bounds replace the previous accidental infinite runoff.
        WaterScenarioSupport.fillFloor(
                assembly, stone, MIN_X, MAX_X, MIN_Y, MAX_Y, -1);

        // Upper-left chamber: a symmetric source. It is intentionally isolated
        // so any directional bias remains visible instead of diffusing into the map.
        WaterScenarioSupport.ringWalls(
                assembly, wall, -9, -3, 1, 5, 0, 0);
        assembly.initialWater(-6, 3, 0, CellVolume.FULL);

        // Lower-left/centre: a long barrier with one open end. Water seeded on the
        // left must travel around the end before the right side can equalize.
        for (int y = -6; y <= -2; y++) {
            assembly.placeTerrain(-1, y, 0, wall);
        }
        assembly.initialWater(-5, -4, 0, 900_000);
        assembly.initialWater(-4, -4, 0, 700_000);

        // Right: two narrow channels containing POSITIVE_X ramps. The upper lane
        // approaches the low/west face and may enter the partial anchor cell. The
        // lower lane approaches the high/east face and must remain blocked.
        for (int x = 4; x <= 9; x++) {
            assembly.placeTerrain(x, 1, 0, wall);
            assembly.placeTerrain(x, 3, 0, wall);
            assembly.placeTerrain(x, -3, 0, wall);
            assembly.placeTerrain(x, -1, 0, wall);
        }
        for (int y : new int[] {-2, 2}) {
            assembly.placeTerrain(4, y, 0, wall);
            assembly.placeTerrain(9, y, 0, wall);
            assembly.placeTerrain(6, y, 0, stone);
            assembly.setShape(6, y, 0, RampShape.POSITIVE_X);
        }
        assembly.initialWater(5, 2, 0, 700_000);
        assembly.initialWater(7, -2, 0, 700_000);

        SimulationRuntime runtime =
                WaterScenarioSupport.startAndAdvance(assembly, 10);
        WaterScenarioDiagnostics diagnostics = new WaterScenarioDiagnostics(
                runtime, MIN_X, MAX_X, MIN_Y, MAX_Y, 0, 1);

        return WaterScenarioSupport.clearSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 1f),
                diagnostics);
    }
}
