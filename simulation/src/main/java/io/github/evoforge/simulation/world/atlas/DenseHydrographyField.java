package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable generated channel-network facts over one finite world XY area. */
final class DenseHydrographyField implements HydrographyField {
    private final WorldBounds bounds;
    private final int width;
    private final boolean[] channels;

    DenseHydrographyField(WorldBounds bounds, boolean[] channels) {
        if (bounds == null || channels == null) {
            throw new IllegalArgumentException("hydrography field inputs must not be null");
        }
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int expected = Math.multiplyExact(width, height);
        if (channels.length != expected) {
            throw new IllegalArgumentException("hydrography array must match horizontal world area");
        }
        this.bounds = bounds;
        this.width = width;
        this.channels = channels.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public boolean isChannelAt(int x, int y) {
        return channels[indexOf(x, y)];
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "hydrography position outside world bounds: (" + x + ", " + y + ")");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }
}
