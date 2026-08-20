package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Derives closed terrain depressions without changing terrain or deciding which become lakes. */
@FunctionalInterface
public interface DrainageBasinTopologyAnalyzer {

    DrainageBasinTopology analyze(
            ElevationField elevation,
            StandingWaterTopology drainageOutlets);

    static DrainageBasinTopologyAnalyzer standard() {
        return new PriorityFloodDrainageBasinTopologyAnalyzer();
    }
}
