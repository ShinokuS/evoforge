package io.github.evoforge.simulation.world.atlas.hydrology;

/** Replaceable resolver for minimum-barrier routes toward selected external standing-water sinks. */
@FunctionalInterface
public interface StandingWaterBoundaryRouteResolver {
    StandingWaterBoundaryRouteTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterSpillTopology spills,
            StandingWaterExternalSinkTopology externalSinks);
}
