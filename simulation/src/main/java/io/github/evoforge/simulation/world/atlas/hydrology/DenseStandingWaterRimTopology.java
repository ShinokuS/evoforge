package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterRimTopology}. */
final class DenseStandingWaterRimTopology implements StandingWaterRimTopology {
    private final WorldBounds bounds;
    private final List<List<StandingWaterRimCell>> rimsByBody;

    DenseStandingWaterRimTopology(
            WorldBounds bounds,
            List<List<StandingWaterRimCell>> rimsByBody) {
        if (bounds == null || rimsByBody == null) {
            throw new IllegalArgumentException("standing-water rim topology inputs must not be null");
        }
        List<List<StandingWaterRimCell>> copied = new ArrayList<>(rimsByBody.size());
        for (int bodyId = 0; bodyId < rimsByBody.size(); bodyId++) {
            List<StandingWaterRimCell> rim = rimsByBody.get(bodyId);
            if (rim == null) throw new IllegalArgumentException("rim list must not be null");
            for (StandingWaterRimCell cell : rim) {
                if (cell == null || cell.bodyId() != bodyId) {
                    throw new IllegalArgumentException("rim cell must reference owning body id");
                }
            }
            copied.add(List.copyOf(rim));
        }
        this.bounds = bounds;
        this.rimsByBody = List.copyOf(copied);
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return rimsByBody.size();
    }

    @Override
    public List<StandingWaterRimCell> rimCells(int bodyId) {
        if (bodyId < 0 || bodyId >= rimsByBody.size()) {
            throw new IllegalArgumentException("unknown standing-water body id: " + bodyId);
        }
        return rimsByBody.get(bodyId);
    }
}
