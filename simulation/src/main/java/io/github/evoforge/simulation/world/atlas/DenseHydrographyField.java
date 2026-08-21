package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable generated channel-network facts over one finite world XY area. */
final class DenseHydrographyField implements HydrographyField {
    private final WorldBounds bounds;
    private final int width;
    private final int cellCount;
    private final long[] channelWords;

    /**
     * Copy-safe compatibility constructor for callers that provide one boolean per cell.
     *
     * <p>The mask is compacted immediately, so later caller mutation cannot affect this field.</p>
     */
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
        long[] words = new long[wordCount(expected)];
        for (int index = 0; index < channels.length; index++) {
            if (channels[index]) {
                words[index >>> 6] |= 1L << (index & 63);
            }
        }
        this.bounds = bounds;
        this.width = width;
        this.cellCount = expected;
        this.channelWords = words;
    }

    /** Transfers exclusive ownership of a freshly produced packed channel mask. */
    static DenseHydrographyField takePackedOwnership(WorldBounds bounds, long[] channelWords) {
        if (bounds == null || channelWords == null) {
            throw new IllegalArgumentException("hydrography field inputs must not be null");
        }
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int cellCount = Math.multiplyExact(width, height);
        if (channelWords.length != wordCount(cellCount)) {
            throw new IllegalArgumentException("packed hydrography array must match horizontal world area");
        }
        return new DenseHydrographyField(bounds, width, cellCount, channelWords);
    }

    static long[] newPackedMask(int cellCount) {
        if (cellCount < 0) throw new IllegalArgumentException("cellCount must be >= 0");
        return new long[wordCount(cellCount)];
    }

    static void setPacked(long[] words, int index) {
        words[index >>> 6] |= 1L << (index & 63);
    }

    private DenseHydrographyField(
            WorldBounds bounds,
            int width,
            int cellCount,
            long[] channelWords) {
        this.bounds = bounds;
        this.width = width;
        this.cellCount = cellCount;
        this.channelWords = channelWords;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public boolean isChannelAt(int x, int y) {
        int index = indexOf(x, y);
        return (channelWords[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    private int indexOf(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException(
                    "hydrography position outside world bounds: (" + x + ", " + y + ")");
        }
        int index = (y - bounds.minY()) * width + (x - bounds.minX());
        if (index < 0 || index >= cellCount) {
            throw new IllegalStateException("hydrography index escaped compact field bounds");
        }
        return index;
    }

    private static int wordCount(int cellCount) {
        return Math.toIntExact(((long) cellCount + 63L) >>> 6);
    }
}
