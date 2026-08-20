package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link DrainageBasinTopology}. */
final class DenseDrainageBasinTopology implements DrainageBasinTopology {
    private final WorldBounds bounds;
    private final int width;
    private final int[] basinIds;
    private final List<DrainageBasin> basins;

    DenseDrainageBasinTopology(
            WorldBounds bounds,
            int[] basinIds,
            List<DrainageBasin> basins) {
        if (bounds == null || basinIds == null || basins == null) {
            throw new IllegalArgumentException("drainage basin topology inputs must not be null");
        }
        long widthLong = (long) bounds.maxX() - bounds.minX() + 1L;
        long heightLong = (long) bounds.maxY() - bounds.minY() + 1L;
        this.width = Math.toIntExact(widthLong);
        int expectedArea = Math.multiplyExact(width, Math.toIntExact(heightLong));
        if (basinIds.length != expectedArea) {
            throw new IllegalArgumentException("drainage basin labels must cover world XY bounds");
        }

        this.basins = List.copyOf(basins);
        for (int id = 0; id < this.basins.size(); id++) {
            if (this.basins.get(id).id() != id) {
                throw new IllegalArgumentException("drainage basin ids must be dense and ordered");
            }
        }
        for (int basinId : basinIds) {
            if (basinId < NO_BASIN || basinId >= this.basins.size()) {
                throw new IllegalArgumentException("drainage basin label references unknown basin");
            }
        }
        this.bounds = bounds;
        this.basinIds = basinIds.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int basinCount() {
        return basins.size();
    }

    @Override
    public int basinIdAt(int x, int y) {
        return basinIds[indexOf(x, y)];
    }

    @Override
    public DrainageBasin basin(int id) {
        if (id < 0 || id >= basins.size()) {
            throw new IllegalArgumentException("unknown drainage basin id: " + id);
        }
        return basins.get(id);
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException("drainage basin coordinate outside world bounds");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }
}
