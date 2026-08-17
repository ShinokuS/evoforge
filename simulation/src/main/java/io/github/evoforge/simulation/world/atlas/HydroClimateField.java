package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.environment.atmosphere.AtmosphericWaterForcing;

/**
 * @deprecated Runtime atmospheric forcing is not an Atlas fact. Use
 * {@link AtmosphericWaterForcing}; this alias exists only for one source-transition step.
 */
@Deprecated(forRemoval = true)
public interface HydroClimateField extends AtmosphericWaterForcing { }
