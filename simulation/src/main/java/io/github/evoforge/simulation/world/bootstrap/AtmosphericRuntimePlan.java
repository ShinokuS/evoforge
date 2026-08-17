package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import java.util.Optional;

/**
 * Replaceable runtime-composition strategy for atmosphere.
 *
 * <p>The plan consumes already prepared immutable world facts and optional runtime time scale exactly
 * once before simulation starts. It is not a generator or calibrator and is never called by the
 * running simulation.</p>
 */
@FunctionalInterface
public interface AtmosphericRuntimePlan {
    AtmosphericRuntimeComposition compose(
            WorldAtlas atlas,
            Optional<SimulationTimeScale> timeScale);
}
