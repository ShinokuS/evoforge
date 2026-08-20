package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterTopology}. */
final class DenseStandingWaterTopology implements StandingWaterTopology {
    private final WorldBounds bounds;
    private final int width;
    private final int[] bodyIds;
    private final List<StandingWaterBody> bodies;

    DenseStandingWaterTopology(
            WorldBounds bounds,
            int[] bodyIds,
            List<StandingWaterBody> bodies) {
        if (bounds == null || bodyIds == null || bodies == null) {
            throw new IllegalArgumentException("standing-water topology inputs must not be null");
        }
        long widthLong = (long) bounds.maxX() - bounds.minX() + 1L;
        long heightLong = (long) bounds.maxY() - bounds.minY() + 1L;
        this.width = Math.toIntExact(widthLong);
        int expectedArea = Math.multiplyExact(width, Math.toIntExact(heightLong));
        if (bodyIds.length != expectedArea) {
            throw new IllegalArgumentException("standing-water labels must cover world XY bounds");
        }

        this.bodies = List.copyOf(bodies);
        for (int id = 0; id < this.bodies.size(); id++) {
            if (this.bodies.get(id).id() != id) {
                throw new IllegalArgumentException("standing-water body ids must be dense and ordered");
            }
        }
        for (int bodyId : bodyIds) {
            if (bodyId < NO_BODY || bodyId >= this.bodies.size()) {
                throw new IllegalArgumentException("standing-water label references unknown body");
            }
        }
        this.bounds = bounds;
        this.bodyIds = bodyIds.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return bodies.size();
    }

    @Override
    public int bodyIdAt(int x, int y) {
        return bodyIds[indexOf(x, y)];
    }

    @Override
    public StandingWaterBody body(int id) {
        if (id < 0 || id >= bodies.size()) {
            throw new IllegalArgumentException("unknown standing-water body id: " + id);
        }
        return bodies.get(id);
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException("standing-water coordinate outside world bounds");
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return localY * width + localX;
    }
}
