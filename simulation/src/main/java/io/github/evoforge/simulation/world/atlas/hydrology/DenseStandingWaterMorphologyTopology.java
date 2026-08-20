package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterMorphologyTopology}. */
final class DenseStandingWaterMorphologyTopology implements StandingWaterMorphologyTopology {
    private final WorldBounds bounds;
    private final List<StandingWaterMorphology> morphology;

    DenseStandingWaterMorphologyTopology(
            WorldBounds bounds,
            List<StandingWaterMorphology> morphology) {
        if (bounds == null || morphology == null) {
            throw new IllegalArgumentException("standing-water morphology inputs must not be null");
        }
        this.bounds = bounds;
        this.morphology = List.copyOf(morphology);
        for (int bodyId = 0; bodyId < this.morphology.size(); bodyId++) {
            if (this.morphology.get(bodyId).bodyId() != bodyId) {
                throw new IllegalArgumentException("standing-water morphology ids must be dense and ordered");
            }
        }
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return morphology.size();
    }

    @Override
    public StandingWaterMorphology morphology(int bodyId) {
        if (bodyId < 0 || bodyId >= morphology.size()) {
            throw new IllegalArgumentException("unknown standing-water morphology body id: " + bodyId);
        }
        return morphology.get(bodyId);
    }
}
