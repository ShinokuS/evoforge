package io.github.evoforge.simulation.world.atlas.hydrology;

/** Replaceable derivation of oceanic/inland water-domain roles from standing-water topology. */
@FunctionalInterface
public interface StandingWaterDomainAnalyzer {
    StandingWaterDomainTopology analyze(StandingWaterTopology standingWater);

    static StandingWaterDomainAnalyzer standard() {
        return BoundaryStandingWaterDomainAnalyzer.INSTANCE;
    }
}
