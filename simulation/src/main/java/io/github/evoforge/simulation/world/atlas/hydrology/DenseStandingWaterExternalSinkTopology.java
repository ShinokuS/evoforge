package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable implementation of {@link StandingWaterExternalSinkTopology}. */
final class DenseStandingWaterExternalSinkTopology implements StandingWaterExternalSinkTopology {
    private final WorldBounds bounds;
    private final boolean[] externalSinks;
    private final int externalSinkCount;

    DenseStandingWaterExternalSinkTopology(WorldBounds bounds, boolean[] externalSinks) {
        if (bounds == null || externalSinks == null) {
            throw new IllegalArgumentException("external-sink topology inputs must not be null");
        }
        this.bounds = bounds;
        this.externalSinks = externalSinks.clone();
        int count = 0;
        for (boolean externalSink : this.externalSinks) {
            if (externalSink) count++;
        }
        this.externalSinkCount = count;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return externalSinks.length;
    }

    @Override
    public boolean isExternalSink(int bodyId) {
        if (bodyId < 0 || bodyId >= externalSinks.length) {
            throw new IllegalArgumentException("unknown standing-water external-sink body id: " + bodyId);
        }
        return externalSinks[bodyId];
    }

    @Override
    public int externalSinkCount() {
        return externalSinkCount;
    }
}
