package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;

/** Started runtime paired with the immutable Atlas and initialization result that produced it. */
public record GeneratedWorldRuntime(
        WorldAtlas atlas,
        TerrainMaterializationResult materialization,
        SimulationRuntime runtime) {

    public GeneratedWorldRuntime {
        if (atlas == null || materialization == null || runtime == null) {
            throw new IllegalArgumentException(
                    "generated world runtime components must not be null");
        }
    }
}
