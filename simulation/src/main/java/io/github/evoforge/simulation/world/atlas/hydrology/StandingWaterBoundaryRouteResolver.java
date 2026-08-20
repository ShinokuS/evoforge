package io.github.evoforge.simulation.world.atlas.hydrology;

/** Replaceable resolver for minimum-barrier routes toward boundary-connected standing water. */
@FunctionalInterface
public interface StandingWaterBoundaryRouteResolver {
    StandingWaterBoundaryRouteTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterSpillTopology spills);
}
