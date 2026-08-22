package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.geometry.RampShape;

/** Small repeated terrain helpers shared by visualizer-only scenario domains. */
public final class ScenarioTerrain {

    private ScenarioTerrain() {
    }

    public static void fill(
            SimulationAssembly assembly,
            MaterialDefinitionId terrain,
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
            MaterialDefinitionId terrain,
            int x,
            int y,
            int z,
            RampShape ramp) {

        assembly.placeTerrain(x, y, z, terrain);
        assembly.setShape(x, y, z, ramp);
    }
}
