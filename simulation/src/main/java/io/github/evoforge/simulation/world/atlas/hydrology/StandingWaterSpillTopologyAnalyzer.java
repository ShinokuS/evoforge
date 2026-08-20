package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Replaceable analysis boundary for minimum-barrier connectivity between water bodies. */
@FunctionalInterface
public interface StandingWaterSpillTopologyAnalyzer {
    StandingWaterSpillTopology analyze(
            ElevationField elevation,
            StandingWaterTopology standingWater);
}
