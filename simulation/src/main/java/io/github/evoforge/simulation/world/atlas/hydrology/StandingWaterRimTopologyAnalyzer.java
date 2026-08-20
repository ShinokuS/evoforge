package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable analysis boundary for dry rim geometry around standing-water components. */
@FunctionalInterface
public interface StandingWaterRimTopologyAnalyzer {
    StandingWaterRimTopology analyze(
            ElevationField elevation,
            StandingWaterTopology standingWater);
}
