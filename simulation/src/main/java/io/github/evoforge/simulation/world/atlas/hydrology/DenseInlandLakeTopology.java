package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link InlandLakeTopology}. */
final class DenseInlandLakeTopology implements InlandLakeTopology {
    private final WorldBounds bounds;
    private final int width;
    private final int[] lakeIds;
    private final List<InlandLake> lakes;

    DenseInlandLakeTopology(
            WorldBounds bounds,
            int[] lakeIds,
            List<InlandLake> lakes) {
        if (bounds == null || lakeIds == null || lakes == null) {
            throw new IllegalArgumentException("inland lake topology inputs must not be null");
        }
        long widthLong = (long) bounds.maxX() - bounds.minX() + 1L;
        long heightLong = (long) bounds.maxY() - bounds.minY() + 1L;
        this.width = Math.toIntExact(widthLong);
        int expectedArea = Math.multiplyExact(width, Math.toIntExact(heightLong));
        if (lakeIds.length != expectedArea) {
            throw new IllegalArgumentException("inland lake labels must cover world XY bounds");
        }

        this.lakes = List.copyOf(lakes);
        for (int id = 0; id < this.lakes.size(); id++) {
            if (this.lakes.get(id).id() != id) {
                throw new IllegalArgumentException("inland lake ids must be dense and ordered");
            }
        }
        for (int lakeId : lakeIds) {
            if (lakeId < NO_LAKE || lakeId >= this.lakes.size()) {
                throw new IllegalArgumentException("inland lake label references unknown lake");
            }
        }
        this.bounds = bounds;
        this.lakeIds = lakeIds.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int lakeCount() {
        return lakes.size();
    }

    @Override
    public int lakeIdAt(int x, int y) {
        return lakeIds[indexOf(x, y)];
    }

    @Override
    public InlandLake lake(int id) {
        if (id < 0 || id >= lakes.size()) {
            throw new IllegalArgumentException("unknown inland lake id: " + id);
        }
        return lakes.get(id);
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException("inland lake coordinate outside world bounds");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }
}
