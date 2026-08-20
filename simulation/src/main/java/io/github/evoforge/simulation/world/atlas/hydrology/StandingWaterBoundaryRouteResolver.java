package io.github.evoforge.simulation.world.atlas.hydrology;

/** Replaceable resolver for minimum-barrier routes toward oceanic standing water. */
@FunctionalInterface
public interface StandingWaterBoundaryRouteResolver {
    StandingWaterBoundaryRouteTopology resolve(
            StandingWaterTopology standingWater,
            StandingWaterSpillTopology spills,
            StandingWaterDomainTopology domains);
}
