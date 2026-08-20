package io.github.evoforge.simulation.world.atlas.hydrology;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies boundary-connected standing water as oceanic and every other body as inland.
 *
 * <p>For standard V14 worlds the complete water boundary is guaranteed upstream, so every oceanic
 * inlet, bay and sea belongs to the same four-connected boundary component without any size-based
 * heuristic here.</p>
 */
public final class BoundaryStandingWaterDomainAnalyzer implements StandingWaterDomainAnalyzer {
    static final BoundaryStandingWaterDomainAnalyzer INSTANCE = new BoundaryStandingWaterDomainAnalyzer();

    public BoundaryStandingWaterDomainAnalyzer() {
    }

    @Override
    public StandingWaterDomainTopology analyze(StandingWaterTopology standingWater) {
        if (standingWater == null) {
            throw new IllegalArgumentException("standing-water topology must not be null");
        }
        List<StandingWaterDomainRole> roles = new ArrayList<>(standingWater.bodyCount());
        for (int bodyId = 0; bodyId < standingWater.bodyCount(); bodyId++) {
            roles.add(standingWater.body(bodyId).touchesWorldBoundary()
                    ? StandingWaterDomainRole.OCEANIC
                    : StandingWaterDomainRole.INLAND);
        }
        return new DenseStandingWaterDomainTopology(standingWater.bounds(), roles);
    }
}
