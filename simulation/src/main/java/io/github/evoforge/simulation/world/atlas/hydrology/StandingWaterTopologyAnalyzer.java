package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable analysis boundary that derives standing-water component topology from elevation. */
@FunctionalInterface
public interface StandingWaterTopologyAnalyzer {
    StandingWaterTopology analyze(ElevationField elevation);
}
