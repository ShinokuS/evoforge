package io.github.evoforge.simulation.world.continuum.map;

/** Identifies one derived map tile. It is presentation/query data, never world truth. */
public record ContinuumMapTileKey(int level, long tileX, long tileY, long sourceRevision) {
    public ContinuumMapTileKey {
        if (level < 0) throw new IllegalArgumentException("level must be >= 0");
        if (tileX < 0L || tileY < 0L) throw new IllegalArgumentException("tile coordinates must be >= 0");
    }

    public ContinuumMapTileKey parent() {
        if (level == Integer.MAX_VALUE) throw new IllegalStateException("level overflow");
        return new ContinuumMapTileKey(level + 1, tileX >>> 1, tileY >>> 1, sourceRevision);
    }

    public ContinuumMapTileKey ancestorAt(int ancestorLevel) {
        if (ancestorLevel < level) throw new IllegalArgumentException("ancestorLevel must be >= level");
        int shift = ancestorLevel - level;
        if (shift >= Long.SIZE) return new ContinuumMapTileKey(ancestorLevel, 0L, 0L, sourceRevision);
        return new ContinuumMapTileKey(ancestorLevel, tileX >>> shift, tileY >>> shift, sourceRevision);
    }
}
