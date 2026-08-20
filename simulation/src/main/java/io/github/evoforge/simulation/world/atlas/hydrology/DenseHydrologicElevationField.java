package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable elevation produced by a hydrologic terrain morphology owner. */
final class DenseHydrologicElevationField implements ElevationField {
    private final WorldBounds bounds;
    private final int width;
    private final long[] elevations;

    DenseHydrologicElevationField(WorldBounds bounds, long[] elevations) {
        if (bounds == null || elevations == null) {
            throw new IllegalArgumentException("hydrologic elevation inputs must not be null");
        }
        long widthLong = (long) bounds.maxX() - bounds.minX() + 1L;
        long heightLong = (long) bounds.maxY() - bounds.minY() + 1L;
        this.width = Math.toIntExact(widthLong);
        int expected = Math.multiplyExact(width, Math.toIntExact(heightLong));
        if (elevations.length != expected) {
            throw new IllegalArgumentException("hydrologic elevation must cover world XY bounds");
        }
        this.bounds = bounds;
        this.elevations = elevations.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int elevationAt(int x, int y) {
        return Math.toIntExact(Math.floorDiv(elevationSubunitsAt(x, y), SUBUNITS_PER_CELL));
    }

    @Override
    public long elevationSubunitsAt(int x, int y) {
        if (x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY()) {
            throw new IllegalArgumentException("hydrologic elevation coordinate outside world bounds");
        }
        return elevations[(y - bounds.minY()) * width + (x - bounds.minX())];
    }
}
