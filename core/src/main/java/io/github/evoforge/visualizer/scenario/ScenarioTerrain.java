package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

/** Small repeated terrain helpers shared by visualizer-only scenario domains. */
public final class ScenarioTerrain {

    private ScenarioTerrain() {
    }

    public static void fill(
            SimulationAssembly assembly,
            LandscapeDefinitionId terrain,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int z) {

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                assembly.placeTerrain(x, y, z, terrain);
            }
        }
    }

    public static void placeRamp(
            SimulationAssembly assembly,
            LandscapeDefinitionId terrain,
            int x,
            int y,
            int z,
            RampShape ramp) {

        assembly.placeTerrain(x, y, z, terrain);
        assembly.setShape(x, y, z, ramp);
    }
}
