package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldSpec;

/** Current uniform hydrologic-climate authoring stage. */
public final class HydroClimateGenerationStage implements HydroClimateGenerator {

    @Override
    public HydroClimateField generate(WorldSpec spec) {
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        HydroClimateSpec climate = spec.hydroClimate();
        return new UniformHydroClimateField(
                spec.bounds(),
                climate.precipitationSupply(),
                climate.evaporativeDemand());
    }
}
