package io.github.evoforge.simulation.world.atlas.hydrology;

/** Produces broad geometric scale facts for hydrologic standing-water bodies. */
@FunctionalInterface
public interface StandingWaterMorphologyAnalyzer {
    StandingWaterMorphologyTopology analyze(StandingWaterTopology standingWater);

    static StandingWaterMorphologyAnalyzer standard() {
        return CardinalStandingWaterMorphologyAnalyzer.INSTANCE;
    }
}
