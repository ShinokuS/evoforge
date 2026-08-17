package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import java.math.BigInteger;
import java.util.Optional;

/** Started runtime paired with the immutable Atlas and initialization result that produced it. */
public record GeneratedWorldRuntime(
        WorldAtlas atlas,
        TerrainMaterializationResult materialization,
        SimulationRuntime runtime,
        Optional<SimulationTimeScale> timeScale) {

    public GeneratedWorldRuntime(
            WorldAtlas atlas,
            TerrainMaterializationResult materialization,
            SimulationRuntime runtime) {
        this(atlas, materialization, runtime, Optional.empty());
    }

    public GeneratedWorldRuntime {
        if (atlas == null || materialization == null || runtime == null || timeScale == null) {
            throw new IllegalArgumentException(
                    "generated world runtime components must not be null");
        }
    }

    /** Exact elapsed physical nanoseconds when runtime composition supplied a time scale. */
    public Optional<BigInteger> elapsedPhysicalNanoseconds() {
        return timeScale.map(scale -> scale.elapsedNanoseconds(runtime.time().tick()));
    }
}
